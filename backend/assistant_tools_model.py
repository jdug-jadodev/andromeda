"""Native tools only, with literal guards; Android remains the sole event executor."""
import copy
import json
import time
import re
import threading

from agenda_requests import current_event, AgendaValidationError
from conversation_voice import ConversationModel, conversational_reply
from doja_intents import normalized
from timer_proposals import selected_timer_seconds, TimerValidationError
from web_search import WEB_QUERY_REASONS, WebQueryValidationError, current_query
from web_answers import contract as web_contract
from internet_search import buscar_en_internet, InternetSearchError
from worker_answers import answer as worker_answer, followup, latest_web
from phone_volume import current_volume


def tool(name, description, *arguments):
    return {'type': 'function', 'function': {'name': name, 'description': description, 'parameters': {
        'type': 'object', 'properties': {name: {'type': 'string'} for name in arguments},
        'required': list(arguments)}}}


TOOLS = [tool('create_timer', 'Duración literal.', 'duration_text'),
         tool('create_alarm', 'Programa la alarma Android solicitada.'),
         tool('create_reminder', 'Programa el recordatorio solicitado, también «recuérdame».'),
         tool('search_web', 'Internet.'),
         tool('set_phone_volume', 'Baja, sube o pon el volumen del teléfono, también «a la mitad». Usa esta herramienta sin argumentos.')]
SYSTEM = ('Andrómeda. Sí puedes programar con herramientas. Alarmas/recordatorios usan el pedido actual, sin argumentos. '
          'Copia literal en español; no citas/negaciones.')


def native_call(message, finish):
    if finish not in {'stop', 'tool_calls'} or not isinstance(message, dict): raise ValueError('Incomplete native response')
    calls = message.get('tool_calls')
    if not isinstance(calls, list) or len(calls) != 1 or not isinstance(calls[0], dict) or calls[0].get('type') != 'function': raise ValueError('One native call required')
    function = calls[0].get('function')
    if not isinstance(function, dict) or set(function) != {'name', 'arguments'}: raise ValueError('Invalid native function')
    name, args = function['name'], function['arguments']
    spec = next((item['function'] for item in TOOLS if item['function']['name'] == name), None)
    if spec is None: raise ValueError('Unknown native tool')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result: raise ValueError('Duplicate argument')
            result[key] = value
        return result
    if isinstance(args, str):
        if len(args) > 1024: raise ValueError('Oversized native arguments')
        args = json.loads(args, object_pairs_hook=unique)
    if not isinstance(args, dict) or set(args) != set(spec['parameters']['properties']) or any(
            not isinstance(value, str) or len(value) > (250 if name in {'create_alarm', 'create_reminder'} else 160) for value in args.values()): raise ValueError('Invalid native arguments')
    if any(not value for value in args.values()): raise ValueError('Empty argument')
    return name, args


class AssistantToolsModel(ConversationModel):
    guided_agenda = False
    anchor_wall = anchor_boot = 0
    response_start_ms = 60000
    compute_ms = 55000
    @staticmethod
    def payload(messages, maximum=96):
        messages = copy.deepcopy(messages); messages[0]['content'] = SYSTEM
        result = ConversationModel.payload(messages, maximum)
        catalog = copy.deepcopy(TOOLS)
        for item in catalog: item['function']['parameters'].pop('required', None)
        # Only redundant generation metadata is omitted. native_call() still
        # requires every field and rejects extra, duplicate, empty or mistyped values.
        result.update(tools=catalog, tool_choice='auto', parallel_tool_calls=False)
        return result

    @staticmethod
    def dialogue_payload(messages, maximum=96):
        result = AssistantToolsModel.payload(messages, maximum)
        result['messages'][0]['content'] += ' Si piden alarma o recordatorio incompletos, selecciona su herramienta para preguntar lo que falta.'
        for item in result['tools']:
            if item['function']['name'] == 'create_reminder':
                item['function']['description'] = 'Crea o inicia un recordatorio. Selecciona también si falta contenido o momento; se preguntará. Sin argumentos.'
            elif item['function']['name'] == 'create_alarm':
                item['function']['description'] = 'Crea o inicia una alarma. Selecciona también si falta fecha y hora; se preguntará. Sin argumentos.'
        return result

    def enable_agenda_dialogue(self):
        self.guided_agenda = True
        self.payload = self.dialogue_payload

    def start(self, text, speech_end, session_end):
        if self.thread is not None and self.thread.is_alive(): raise ValueError('Concurrent turn')
        self.work_deadline = self.turn_deadline(speech_end, session_end)
        web = latest_web(self.memory)
        if web and not self.requires_native_selection(text):
            # A read-only continuation must not revisit the tool selector. This
            # gate grants NOTHING: any possible new command keeps the original
            # native selection + whole-current-request validators/executor.
            self.cancelled.clear(); self.done.clear(); self.result = self.error = None
            self.memory.messages(text, activity_ms=speech_end)
            history = [{'user': group[0]['content'], 'assistant': group[1]['content'] if len(group) == 2 else ''}
                       for group in self.memory.pairs[-4:] if 'web_answer' not in group[0]]
            self.context_metrics = {'tools_model_choice': 'text', 'tools_selected_tool': 'none',
                'tools_model_kind': 'reply', 'tools_validation': 'local_web_followup',
                'tools_web_attempted': False, 'input_token_budget': 1536}
            deadline = self.work_deadline
            def work():
                try:
                    result = followup(text, web, history, self.post, self.clock, deadline - 1500, self.cancelled)
                    if not self.cancelled.is_set(): self.result = result
                except Exception as error: self.error = type(error).__name__
                finally: self.done.set()
            self.thread = threading.Thread(target=work, daemon=True, name='read-only-web-followup')
            self.thread.start()
            return deadline
        return super().start(text, speech_end, session_end)

    @staticmethod
    def requires_native_selection(text):
        # Conservative exclusion from the read-only path, NOT a tool router,
        # data parser or execution authorization. Quotes/negations still go to
        # the unchanged native/current-request guards, never an inferred action.
        return bool(re.search(r'\b(?:busca|busques|buscar|consulta|consultes|investiga|investigues|'
            r'pon|ponme|pongas|crea|crees|crear|programa|programes|inicia|inicies|'
            r'recuerdame|recuerdes|alarma|alarmas|recordatorio|recordatorios|temporizador|temporizadores|'
            r'timer|cuenta atras|cancela|canceles|apaga|enciende|volumen|baja|bajes|sube|subas|ajusta)\b', normalized(text)))

    def interpret_result(self, message, finish_reason, human):
        selected, validation, web_attempted = 'none', 'invalid_native_call', False
        def finish(result, reason):
            metrics = getattr(self, 'context_metrics', None)
            if isinstance(metrics, dict):
                metrics.update(tools_model_choice='native' if message.get('tool_calls') else 'text',
                    tools_selected_tool=selected, tools_model_kind=result['kind'],
                    tools_validation=reason, tools_web_attempted=web_attempted)
            return result  # Fixed enums/boolean only, as in TimerTrialModel; never log human/model text.
        if message.get('tool_calls'):
            selected = 'unvalidated'
            try:
                name, args = native_call(message, finish_reason)
                selected = name
                if name == 'create_timer':
                    validation = 'timer_request_rejected'
                    return finish({'kind': 'timer_proposal', 'seconds': selected_timer_seconds(message, finish_reason, human)}, 'accepted')
                if name in {'create_alarm', 'create_reminder'}:
                    # The strict zero-argument schema rejects (never repairs)
                    # any model-supplied date, duration, text or copied request.
                    validation = 'agenda_request_rejected'
                    kind = 'ALARM' if name == 'create_alarm' else 'REMINDER'
                    try: value = current_event(kind, human, self.anchor_wall)
                    except AgendaValidationError:
                        if not self.guided_agenda: raise
                        from agenda_dialogue import opening
                        opening(kind, human)  # No execution; controller independently checks again.
                        return finish({'kind': 'agenda_dialogue_start', 'event_kind': kind}, 'agenda_question')
                    return finish(dict(value, kind='agenda_proposal'), 'accepted')
                if name == 'set_phone_volume':
                    validation = 'volume_request_rejected'
                    return finish({'kind': 'volume_proposal', 'percent': current_volume(human)}, 'accepted')
                validation = 'web_query_rejected'
                # Like agenda calls, this schema accepts NO echoed human data.
                # An added query argument is rejected by native_call(), never
                # ignored/repaired. Only the exact native selection reaches here.
                query = current_query(human)
                try:
                    remaining = (self.work_deadline - self.clock()) / 1000
                    web_attempted = True  # Search attempted, not proof that a provider received a request.
                    result = buscar_en_internet(query, time.monotonic() + max(0, remaining - 1.5), self.cancelled)
                    rows = result['fuentes']
                    # Worker-first: no second LFM pass, no context read aloud,
                    # no truncation disguised as the service's full answer.
                    response = worker_answer(result, self.clock, self.work_deadline - 1500, self.cancelled)
                    self.context_metrics.update(web_answer_status=response['status'], web_answer_source_index=response['source_index'],
                                                web_answer_input_tokens=response['input_tokens'])
                    return finish({'kind': 'web_result', 'text': response['text'], 'sources': rows,
                                   'web_answer': web_contract(response)}, 'web_results' if rows else 'web_no_results')
                except (OSError, ValueError, TimeoutError) as error:
                    if isinstance(error, InternetSearchError): self.context_metrics['web_provider_status'] = error.code
                    return finish({'kind': 'web_result', 'text': 'La búsqueda web no estuvo disponible. No consulté una fuente utilizable ni puedo confirmar esos datos.', 'sources': [],
                                   'web_answer': web_contract({'status': 'provider_unavailable'})}, 'web_provider_unavailable')
            except WebQueryValidationError as invalid:
                reason = invalid.reason if invalid.reason in WEB_QUERY_REASONS else 'invalid_request'
                return finish({'kind': 'tools_rejected'}, 'web_' + reason)
            except (ValueError, KeyError, TypeError, AgendaValidationError, TimerValidationError):
                return finish({'kind': 'tools_rejected'}, validation)
        plain = normalized(human)
        if re.match(r'^(?:andromeda[, ]+)?(?:por favor )?(?:recuerdame\b|(?:pon|ponme|crea|programa|inicia)\b|(?:baja|sube|ajusta|establece) el volumen\b|(?:alarma|recordatorio|temporizador|timer|cuenta atras)\b|(?:busca|investiga|consulta) (?:en|por) (?:internet|la web))', plain):
            return finish({'kind': 'tools_rejected'}, 'missing_native_call')
        try:
            plain_reply = conversational_reply(message, finish_reason)
            memory = getattr(self, 'memory', None)
            web = latest_web(memory) if memory is not None else None
            if web:
                history = [{'user': group[0]['content'], 'assistant': group[1]['content'] if len(group) == 2 else ''}
                           for group in memory.pairs[-4:] if 'web_answer' not in group[0]]
                return finish(followup(human, web, history, self.post, self.clock,
                    self.work_deadline - 1500, self.cancelled), 'local_web_followup')
            return finish({'kind': 'reply', 'text': plain_reply}, 'reply')
        except ValueError: return finish({'kind': 'tools_rejected'}, 'unsupported_reply') # Reject unsupported claims, including misleading negative prose, without execution.
