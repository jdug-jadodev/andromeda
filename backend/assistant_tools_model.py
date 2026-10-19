"""Native tools only, with literal guards; Android remains the sole event executor."""
import copy
import json
import time
import re

from agenda_requests import current_event, AgendaValidationError
from conversation_voice import ConversationModel, conversational_reply
from doja_intents import normalized
from timer_proposals import selected_timer_seconds, TimerValidationError
from web_search import authorized_query, search, speech


def tool(name, description, *arguments):
    return {'type': 'function', 'function': {'name': name, 'description': description, 'parameters': {
        'type': 'object', 'properties': {name: {'type': 'string'} for name in arguments},
        'required': list(arguments)}}}


TOOLS = [tool('create_timer', 'Duración literal.', 'duration_text'),
         tool('create_alarm', 'Programa la alarma Android solicitada.'),
         tool('create_reminder', 'Programa el recordatorio solicitado, también «recuérdame».'),
         tool('search_web', 'Internet.', 'query')]
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
    anchor_wall = anchor_boot = 0
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

    def start(self, text, speech_end, session_end):
        self.work_deadline = min(self.clock() + 22000, speech_end + 26500, session_end - 1500)
        return super().start(text, speech_end, session_end)

    def interpret_result(self, message, finish_reason, human):
        if message.get('tool_calls'):
            try:
                name, args = native_call(message, finish_reason)
                if name == 'create_timer': return {'kind': 'timer_proposal', 'seconds': selected_timer_seconds(message, finish_reason, human)}
                if name in {'create_alarm', 'create_reminder'}:
                    # The strict zero-argument schema rejects (never repairs)
                    # any model-supplied date, duration, text or copied request.
                    value = current_event('ALARM' if name == 'create_alarm' else 'REMINDER', human, self.anchor_wall)
                    return dict(value, kind='agenda_proposal')
                query = authorized_query(human, args['query'])
                try:
                    remaining = (self.work_deadline - self.clock()) / 1000
                    rows = search(query, time.monotonic() + max(0, remaining), self.cancelled)
                    return {'kind': 'web_result', 'text': speech(rows), 'sources': rows}
                except (OSError, ValueError, TimeoutError):
                    return {'kind': 'web_result', 'text': 'La búsqueda web no estuvo disponible. No consulté una fuente utilizable ni puedo confirmar esos datos.', 'sources': []}
            except (ValueError, KeyError, TypeError, AgendaValidationError, TimerValidationError):
                return {'kind': 'tools_rejected'}
        plain = normalized(human)
        if re.match(r'^(?:andromeda[, ]+)?(?:por favor )?(?:recuerdame\b|(?:pon|ponme|crea|programa|inicia)\b|(?:alarma|recordatorio|temporizador|timer|cuenta atras)\b|(?:busca|investiga|consulta) (?:en|por) (?:internet|la web))', plain):
            return {'kind': 'tools_rejected'}
        try: return {'kind': 'reply', 'text': conversational_reply(message, finish_reason)}
        except ValueError: return {'kind': 'tools_rejected'} # Reject unsupported claims, including misleading negative prose, without execution.
