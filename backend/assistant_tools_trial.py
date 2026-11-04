"""Authenticated turn-bound agenda grants and read-only web results. No event executor."""
from datetime import datetime
import re
import uuid

from agenda_requests import BOGOTA, request
from timer_trial import TimerTrial
from conversation_flow import ConversationFlow
from phone_volume import VolumeTrial
from light_trial import LightTrial

REJECTION = 'No pude confirmar esa petición. Dime claramente qué quieres hacer.'


DAYS = 'lunes martes miércoles jueves viernes sábado domingo'.split()


def _short_clock(hour, minute):
    return str(hour) if minute == 0 else f'{hour}:{minute:02d}'


def _short_day(due):
    return DAYS[datetime.fromtimestamp(due / 1000, BOGOTA).weekday()]


def result_text(status, kind, due, reminder_text='', delay_ms=0, detailed=False, weekday=0, minute_of_day=-1):
    """Confirmación corta: sin año, sin segundos y sin repetir la zona horaria.

    Debe coincidir carácter por carácter con AgendaSpeechPolicy.java: Android
    compara el texto exacto antes de aceptar el recibo.
    """
    if status == 'CREATED':
        if weekday:
            return f'Listo, alarma los {DAYS[weekday - 1]} a las {_short_clock(minute_of_day // 60, minute_of_day % 60)}.'
        if minute_of_day >= 0:
            return f'Listo, alarma todos los días a las {_short_clock(minute_of_day // 60, minute_of_day % 60)}.'
        if delay_ms:
            scale, unit = (3600000, 'hora') if delay_ms % 3600000 == 0 else (60000, 'minuto') if delay_ms % 60000 == 0 else (1000, 'segundo')
            count = delay_ms // scale
            return f'Listo, te aviso en {count} {unit}{"s" if count != 1 else ""}.'
        moment = datetime.fromtimestamp(due / 1000, BOGOTA)
        what = 'alarma' if kind == 'ALARM' else 'recordatorio'
        return f'Listo, {what} para el {_short_day(due)} a las {_short_clock(moment.hour, moment.minute)}.'
    return {'DUPLICATE': 'Esa orden ya tenía un registro. No la repetí; revisa la agenda manual.',
            'REJECTED': 'Android rechazó la programación. No he confirmado un aviso nuevo.',
            'FAILED': 'La programación falló y se retiró ese intento. Revisa la agenda manual.',
            'UNKNOWN': 'No pude confirmar el resultado. Revisa Alarmas o Recordatorios antes de pedirlo otra vez.'}[status]


class AssistantToolsTrial:
    def __init__(self, clock, guided_agenda=False, maximum_sequence=24, alarm_lifecycle=False, confirmed_volume=False, reminder_read=False, reminder_clarification=False, reminder_deletion=False):
        self.clock, self.timer, self.inflight = clock, TimerTrial(clock), None
        self.consumed = set()
        if confirmed_volume and not guided_agenda: raise ValueError('Confirmed volume requires guided tools')
        self.volume = VolumeTrial(clock,60000 if guided_agenda else 30000,confirmed_volume,maximum_sequence)
        self.lights = LightTrial(clock, maximum_sequence)
        self.guided_agenda = guided_agenda
        from reminder_read import ReminderRead
        if reminder_read and not guided_agenda: raise ValueError('Reminder reading requires guided tools')
        self.read_enabled = reminder_read; self.read = ReminderRead(clock)
        from agenda_dialogue import AgendaDialogue
        self.alarm_lifecycle=alarm_lifecycle
        if reminder_clarification and not guided_agenda: raise ValueError('Reminder clarification requires guided tools')
        self.dialogue = AgendaDialogue(maximum_sequence,alarm_lifecycle,reminder_clarification)
        from alarm_deletion import AlarmDeletion
        self.deletion=AlarmDeletion(clock,maximum_sequence)
        from reminder_deletion import ReminderDeletion
        if reminder_deletion and not (guided_agenda and reminder_read and reminder_clarification and alarm_lifecycle):
            raise ValueError('Reminder deletion requires coordinated continuous reminders')
        self.reminder_delete_enabled=reminder_deletion; self.reminder_delete=ReminderDeletion(clock,maximum_sequence)

    @property
    def pending_dialogue(self): return self.dialogue.active or self.deletion.active or self.volume.dialogue.active or self.reminder_delete.active or self.lights.active or self.lights.inflight is not None

    @staticmethod
    def deletion_response(response):
        if response is not None and response.get('offered_more'):
            return dict(response,text=ConversationFlow.with_offer(response['text']))
        return response

    def dialogue_response(self, result):
        if result['kind'] == 'agenda_question':
            return {'text': result['text'], 'timer_control': 'agenda_question', 'agenda_kind': result['event_kind'], 'offered_more': False}
        if result['kind'] == 'agenda_cancelled':
            return {'text': ConversationFlow.with_offer(result['text']), 'timer_control': 'agenda_cancelled', 'offered_more': True}
        raise ValueError('Unknown agenda dialogue response')

    def human(self, turn, session, context, session_end):
        if not self.guided_agenda: return None
        if self.lights.active:
            response = self.lights.human(turn, session, context, session_end)
            if response and response.get('executing'):
                if turn['id'] in self.consumed or len(self.consumed) >= 24: raise ValueError('Second mutation')
                self.consumed.add(turn['id'])
            return response
        if self.reminder_delete.active:
            response=self.reminder_delete.human(turn,session,context,session_end)
            if response and response.get('event',{}).get('type')=='reminder_delete_execute':
                if turn['id'] in self.consumed or len(self.consumed)>=24: raise ValueError('Second mutation')
                self.consumed.add(turn['id'])
            return self.deletion_response(response)
        if self.volume.dialogue.active:
            result = self.volume.dialogue.answer(turn,self.clock())
            if result is None: return None
            if result['kind']=='confirmed_volume_proposal': return self.model_result(result,turn,session,context,session_end)
            return self.volume_dialogue_response(result)
        if self.alarm_lifecycle and self.deletion.active:
            response=self.deletion.human(turn,session,context,session_end)
            if response and response.get('executing'):
                if turn['id'] in self.consumed or len(self.consumed)>=24: raise ValueError('Second mutation')
                self.consumed.add(turn['id'])
            return self.deletion_response(response)
        result = self.dialogue.answer(turn, self.clock())
        if result is None: return None
        if result['kind'] == 'guided_agenda_proposal': return self.model_result(result, turn, session, context, session_end)
        return self.dialogue_response(result)

    def spoken(self, turn_id, completed):
        self.dialogue.spoken(turn_id, completed, self.clock()); self.deletion.spoken(turn_id,completed)
        self.volume.dialogue.spoken(turn_id,completed,self.clock())
        self.reminder_delete.spoken(turn_id,completed)
        self.lights.spoken(turn_id, completed)

    @staticmethod
    def volume_dialogue_response(result):
        if result['kind'] not in {'volume_question','volume_cancelled'}: raise ValueError('Unknown volume dialogue response')
        return {'text':result['text'],'timer_control':result['kind'],'offered_more':False}

    def completion(self, response):
        if self.guided_agenda and response is not None and response.get('close_reason') == 'action_result':
            response = dict(response, text=ConversationFlow.with_offer(response['text']), close_reason='', offered_more=True)
        return response

    def _tv_result(self, result):
        import tv_control
        kind = result['kind']
        try:
            if kind == 'tv_status':
                data = tv_control.status()
                if not data.get('reachable'):
                    return {'text': 'La TV está apagada. ¿La enciendo?', 'offered_more': True}
                return {'text': f'La TV está encendida. Volumen {data.get("volume", "?")}.'}
            if kind == 'tv_inputs':
                data = tv_control.inputs()
                names = [i.get('label') or i.get('id', '?') for i in data.get('inputs', [])]
                if not names:
                    return {'text': 'No pude leer las entradas de la TV.'}
                return {'text': 'Entradas de la TV: ' + ', '.join(names) + '.'}
            if kind == 'tv_power_on':
                try:
                    data = tv_control.power_on()
                except tv_control.TVError:
                    # El WoL ya se envió; la TV termina de encender sola.
                    return {'text': 'La tele se está encendiendo, dale unos segundos.'}
                return {'text': 'Encendí la TV.' if data.get('ok') else 'La tele se está encendiendo, dale unos segundos.'}
            if kind == 'tv_power_off':
                tv_control.power_off()
                return {'text': 'Listo, la TV quedó apagada.'}
            if kind == 'tv_volume':
                if 'level' in result:
                    tv_control.set_volume(result['level'])
                    return {'text': f'Volumen en {result["level"]}.'}
                if 'delta' in result:
                    tv_control.bump_volume(result['delta'])
                    st = tv_control.volume_state()
                    return {'text': f'Volumen en {st.get("volume", "?")}.'}
                if 'mute' in result:
                    tv_control.set_mute(result['mute'])
                    return {'text': 'TV silenciada.' if result['mute'] else 'Quité el silencio.'}
                return {'text': 'No entendí el cambio de volumen.'}
            if kind == 'tv_audio':
                if 'output' in result:
                    tv_control.set_output(result['output'])
                    label = 'la barra' if result['output'] == 'external_optical' else 'los altavoces'
                    return {'text': f'Puse el audio por {label}.'}
                if 'enforce' in result:
                    tv_control.enforce_output()
                    return {'text': 'Restauré la salida de audio.'}
                return {'text': 'No entendí el cambio de audio.'}
            if kind == 'tv_input':
                tv_control.switch_input(result['id'])
                label = 'TV' if result['id'] == 'TV' else result['id'].replace('_', ' ')
                return {'text': f'Puse {label}.'}
            if kind == 'tv_open_app':
                data = tv_control.open_app(name=result.get('name'))
                title = data.get('matched_title') or data.get('id') or 'la aplicación'
                return {'text': f'Abrí {title}.'}
            if kind == 'tv_scene':
                tv_control.scene(result['name'])
                return {'text': f'Apliqué la escena {result["name"]}.'}
            if kind == 'tv_home':
                tv_control.open_app(app_id='com.webos.app.home')
                return {'text': 'Volví al inicio de la TV.'}
        except tv_control.TVError:
            return {'text': 'No pude hablar con la TV ahora mismo.'}
        return {'text': REJECTION, 'timer_control': 'rejected'}

    def model_result(self, result, turn, session, context, session_end):
        if result['kind'] == 'self_destruct':
            # The model chose the tool; its scene is shown by Android while this line is spoken.
            # No timer_control: the app's TimerActionChannel rejects unknown values as
            # "Confirmación de acción no permitida". self_destruct is display+TTS only.
            # explicit_close ends the chat after the line like "no necesito nada más", so the clock falls in.
            return {'text': result['text'], 'face_scene': result['scene'], 'offered_more': False,
                    'close_reason': 'explicit_close'}
        if result['kind'].startswith('tv_'):
            return self._tv_result(result)
        if self.read.inflight is not None: raise ValueError('A read result is already pending')
        if self.reminder_delete.active or self.reminder_delete.grant is not None:
            raise ValueError('A reminder deletion is already pending')
        if result['kind']=='reminder_delete_request':
            if (not self.reminder_delete_enabled or self.pending_dialogue or self.inflight or self.timer.inflight
                    or self.volume.inflight or self.deletion.grant or turn['id'] in self.consumed or len(self.consumed)>=24):
                raise ValueError('Reminder deletion unavailable')
            return self.reminder_delete.start(turn,session,context,session_end)
        if result['kind']=='reminder_list_request':
            if (not self.read_enabled or self.pending_dialogue or self.inflight or self.timer.inflight
                    or self.volume.inflight or self.deletion.grant or turn['id'] in self.consumed):
                raise ValueError('Reminder read unavailable')
            return self.read.authorize(turn,session,context,session_end)
        if result['kind']=='volume_dialogue_start':
            if (not self.volume.confirmed or self.pending_dialogue or self.inflight or self.timer.inflight
                    or self.volume.inflight or self.deletion.grant or turn['id'] in self.consumed):
                raise ValueError('Volume form unavailable')
            return self.volume_dialogue_response(self.volume.dialogue.begin(turn,self.clock()))
        if result['kind'] == 'light_proposal':
            if (self.pending_dialogue or self.inflight or self.timer.inflight or self.volume.inflight
                    or self.deletion.grant or turn['id'] in self.consumed or len(self.consumed) >= 24):
                raise ValueError('Light form unavailable')
            response = self.lights.begin(turn)
            response['timer_control'] = 'light_question'
            return response
        if result['kind']=='confirmed_volume_proposal':
            if (self.inflight or self.timer.inflight or self.volume.inflight or self.deletion.grant
                    or turn['id'] in self.consumed or len(self.consumed)>=24): raise ValueError('Second mutation')
            response=self.volume.authorize_confirmed(result,turn,session,context,session_end)
            self.consumed.add(turn['id']); return response
        if result['kind']=='alarm_delete_request':
            if not self.alarm_lifecycle or self.pending_dialogue or self.deletion.grant or self.inflight or self.timer.inflight or self.volume.inflight or turn['id'] in self.consumed or len(self.consumed)>=24:
                raise ValueError('Alarm deletion unavailable/current turn consumed')
            response=self.deletion.start(result,turn,session,context); self.consumed.add(turn['id']); return response
        if result['kind'] == 'agenda_dialogue_start':
            if not self.guided_agenda: raise ValueError('Agenda dialogue not negotiated')
            if self.inflight is not None or self.timer.inflight is not None or self.volume.inflight is not None or turn['id'] in self.consumed:
                raise ValueError('Agenda dialogue cannot replace an executing turn')
            return self.dialogue_response(self.dialogue.begin(result['event_kind'], turn, self.clock()))
        mutation = result['kind'] in {'timer_proposal', 'agenda_proposal', 'guided_agenda_proposal', 'volume_proposal'}
        if mutation and (self.inflight is not None or self.timer.inflight is not None or self.volume.inflight is not None or self.deletion.grant is not None
                or turn['id'] in self.consumed or len(self.consumed) >= 24):
            raise ValueError('One mutation per current turn')
        if result['kind'] == 'volume_proposal':
            response = self.volume.authorize(result, turn, session, context, session_end)
            self.consumed.add(turn['id']); return response
        if result['kind'] == 'reply' and result.get('web_followup') is True:
            return {'text': ConversationFlow.with_offer(result['text']), 'timer_control': 'web_followup', 'offered_more': True}
        if result['kind'] in {'timer_proposal', 'timer_rejected', 'reply'}:
            response = self.timer.model_result(result, turn, session, context, session_end)
            if mutation: self.consumed.add(turn['id'])
            return response
        if result['kind'] == 'tools_rejected': return {'text': REJECTION, 'timer_control': 'rejected'}
        if result['kind'] == 'web_result':
            direct = result.get('web_answer', {}).get('mode') in {'service_answer', 'service_translation'}
            return {'text': ConversationFlow.with_offer(result['text'], maximum=2100 if direct else 700), 'sources': result['sources'], 'timer_control': 'web', 'offered_more': True,
                    **({'web_answer': result['web_answer']} if 'web_answer' in result else {})}
        if result['kind'] not in {'agenda_proposal', 'guided_agenda_proposal'} or self.inflight is not None or turn['id'] in self.consumed or len(self.consumed) >= 24:
            raise ValueError('Invalid agenda proposal state')
        if not all(isinstance(value, str) and re.fullmatch('[0-9a-f]{32}', value) for value in (turn['id'], session, context)):
            raise ValueError('Invalid grant identity')
        guided = result['kind'] == 'guided_agenda_proposal'
        if guided:
            if not self.guided_agenda: raise ValueError('Agenda dialogue not negotiated')
            prepared = self.dialogue.claim(result, turn, self.clock())
            value = {key: prepared[key] for key in ('event_kind', 'text', 'due_ms', 'delay_ms', 'zone') + (('weekday','minute_of_day') if self.alarm_lifecycle else ())}
        else: value = request(result['event_kind'], turn['text'], result['when_text'], result['text'], turn['calendar_anchor_ms'],self.alarm_lifecycle)
        if any(type(result.get(key)) is not type(item) or result[key] != item for key, item in value.items()):
            raise ValueError('Agenda payload does not match current request')
        now = self.clock(); before = min(now + 4000, turn['speech_end'] + 26500, session_end - 5000)
        if (type(turn['seq']) is not int or turn['seq'] < 1 or type(turn['speech_end']) is not int
                or not 0 < turn['speech_end'] <= now + 100 or now - turn['speech_end'] >= 26500
                or type(session_end) is not int or not now < session_end <= now + 300000
                or type(turn['calendar_anchor_ms']) is not int or turn['calendar_anchor_ms'] <= 0
                or type(turn['calendar_boot_ms']) is not int or not 0 < turn['calendar_boot_ms'] <= now + 100
                or now - turn['calendar_boot_ms'] >= 26500): raise ValueError('Stale/invalid agenda turn')
        if before - now < 500: raise ValueError('Insufficient execution budget')
        self.consumed.add(turn['id'])
        self.inflight = dict(value, type='agenda_execute', event_id=str(uuid.uuid4()), nonce=uuid.uuid4().hex,
            request_turn_id=turn['id'], request_sequence=turn['seq'], speech_end_ms=turn['speech_end'],
            execute_before_ms=before, when_text=result['when_text'], calendar_anchor_ms=turn['calendar_anchor_ms'],
            calendar_boot_ms=turn['calendar_boot_ms'],
            **({'dialogue_origin_turn_id': prepared['dialogue_origin_turn_id']} if guided else {}))
        return {'event': self.inflight, 'executing': True}

    def result(self, packet, session, context):
        if packet.get('action')=='reminder_delete_selection': return self.deletion_response(self.reminder_delete.selection(packet,session,context))
        if packet.get('action')=='reminder_delete_result': return self.deletion_response(self.reminder_delete.result(packet,session,context))
        if packet.get('action')=='reminder_read_result': return self.read.result(packet,session,context)
        if packet.get('action')=='alarm_selection': return self.deletion_response(self.deletion.selection(packet,session,context))
        if packet.get('action')=='alarm_delete_result': return self.deletion_response(self.deletion.result(packet,session,context))
        if packet.get('action') == 'volume_result': return self.completion(self.volume.result(packet, session, context))
        if packet.get('action') == 'light_result': return self.completion(self.lights.result(packet, session, context))
        if packet.get('action') == 'timer_result': return self.completion(self.timer.result(packet, session, context))
        grant = self.inflight
        if grant is None: return None
        expected = dict(session_id=session, context_id=context, **{key: grant[key] for key in
            ('event_id', 'nonce', 'event_kind', 'due_ms', 'request_turn_id', 'request_sequence')+(('weekday','minute_of_day') if self.alarm_lifecycle else ())})
        if any(type(packet.get(key)) is not type(value) or packet[key] != value for key, value in expected.items()) or packet.get('status') not in {'CREATED', 'DUPLICATE', 'REJECTED', 'FAILED', 'UNKNOWN'}:
            raise ValueError('Unrelated agenda result')
        self.inflight = None
        return self.completion({'text': result_text(packet['status'], grant['event_kind'], grant['due_ms'], grant['text'], grant['delay_ms'], self.guided_agenda,grant.get('weekday',0),grant.get('minute_of_day',-1)), 'timer_control': 'agenda_result',
                'actions_executed': int(packet['status'] == 'CREATED'), 'close_reason': 'action_result', 'offered_more': False})

    def timed_out(self):
        reminder_delete=self.reminder_delete.timed_out()
        if reminder_delete is not None: return self.deletion_response(reminder_delete)
        read = self.read.timed_out()
        if read is not None: return read
        deletion=self.deletion.timed_out()
        if deletion is not None: return self.deletion_response(deletion)
        volume = self.volume.timed_out()
        if volume is not None: return self.completion(volume)
        light = self.lights.timed_out()
        if light is not None: return self.completion(light)
        if self.inflight is not None and self.clock() >= self.inflight['execute_before_ms'] + 500:
            grant, self.inflight = self.inflight, None
            return self.completion({'text': result_text('UNKNOWN', grant['event_kind'], grant['due_ms']), 'timer_control': 'agenda_result',
                    'actions_executed': 0, 'close_reason': 'action_result', 'offered_more': False})
        return self.completion(self.timer.timed_out())

    def cancel(self): self.timer.cancel(); self.volume.cancel(); self.lights.cancel(); self.dialogue.clear(); self.deletion.clear(); self.read.cancel(); self.reminder_delete.clear()
    def close(self): self.timer.close(); self.volume.close(); self.lights.close(); self.dialogue.clear(); self.deletion.clear(); self.read.close(); self.reminder_delete.clear(); self.inflight = None
