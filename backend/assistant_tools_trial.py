"""Authenticated turn-bound agenda grants and read-only web results. No event executor."""
from datetime import datetime
import re
import uuid

from agenda_requests import BOGOTA, request
from timer_trial import TimerTrial
from conversation_flow import ConversationFlow

REJECTION = ('No he programado ningún aviso ni completado una búsqueda con esa petición. '
             'Indica una sola petición completa: fecha y hora inequívocas para la alarma, '
             'contenido y momento para el recordatorio, o «busca en Internet» y la consulta.')


def result_text(status, kind, due):
    name = 'Alarma' if kind == 'ALARM' else 'Recordatorio'
    when = datetime.fromtimestamp(due / 1000, BOGOTA).strftime('%d/%m/%Y a las %H:%M')
    if status == 'CREATED': return f'{name} programado para el {when}, hora de Bogotá.' if kind == 'REMINDER' else f'Alarma programada para el {when}, hora de Bogotá.'
    return {'DUPLICATE': 'Esa orden ya tenía un registro. No la repetí; revisa la agenda manual.',
            'REJECTED': 'Android rechazó la programación. No he confirmado un aviso nuevo.',
            'FAILED': 'La programación falló y se retiró ese intento. Revisa la agenda manual.',
            'UNKNOWN': 'No pude confirmar el resultado. Revisa Alarmas o Recordatorios antes de pedirlo otra vez.'}[status]


class AssistantToolsTrial:
    def __init__(self, clock):
        self.clock, self.timer, self.inflight = clock, TimerTrial(clock), None
        self.consumed = set()

    def model_result(self, result, turn, session, context, session_end):
        if result['kind'] == 'reply' and result.get('web_followup') is True:
            return {'text': ConversationFlow.with_offer(result['text']), 'timer_control': 'web_followup', 'offered_more': True}
        if result['kind'] in {'timer_proposal', 'timer_rejected', 'reply'}:
            return self.timer.model_result(result, turn, session, context, session_end)
        if result['kind'] == 'tools_rejected': return {'text': REJECTION, 'timer_control': 'rejected'}
        if result['kind'] == 'web_result':
            direct = result.get('web_answer', {}).get('mode') == 'service_answer'
            return {'text': ConversationFlow.with_offer(result['text'], maximum=2100 if direct else 700), 'sources': result['sources'], 'timer_control': 'web', 'offered_more': True,
                    **({'web_answer': result['web_answer']} if 'web_answer' in result else {})}
        if result['kind'] != 'agenda_proposal' or self.inflight is not None or turn['id'] in self.consumed or len(self.consumed) >= 24:
            raise ValueError('Invalid agenda proposal state')
        if not all(isinstance(value, str) and re.fullmatch('[0-9a-f]{32}', value) for value in (turn['id'], session, context)):
            raise ValueError('Invalid grant identity')
        value = request(result['event_kind'], turn['text'], result['when_text'], result['text'], turn['calendar_anchor_ms'])
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
            calendar_boot_ms=turn['calendar_boot_ms'])
        return {'event': self.inflight, 'executing': True}

    def result(self, packet, session, context):
        if packet.get('action') == 'timer_result': return self.timer.result(packet, session, context)
        grant = self.inflight
        if grant is None: return None
        expected = dict(session_id=session, context_id=context, **{key: grant[key] for key in
            ('event_id', 'nonce', 'event_kind', 'due_ms', 'request_turn_id', 'request_sequence')})
        if any(type(packet.get(key)) is not type(value) or packet[key] != value for key, value in expected.items()) or packet.get('status') not in {'CREATED', 'DUPLICATE', 'REJECTED', 'FAILED', 'UNKNOWN'}:
            raise ValueError('Unrelated agenda result')
        self.inflight = None
        return {'text': result_text(packet['status'], grant['event_kind'], grant['due_ms']), 'timer_control': 'agenda_result',
                'actions_executed': int(packet['status'] == 'CREATED'), 'close_reason': 'action_result', 'offered_more': False}

    def timed_out(self):
        if self.inflight is not None and self.clock() >= self.inflight['execute_before_ms'] + 500:
            grant, self.inflight = self.inflight, None
            return {'text': result_text('UNKNOWN', grant['event_kind'], grant['due_ms']), 'timer_control': 'agenda_result',
                    'actions_executed': 0, 'close_reason': 'action_result', 'offered_more': False}
        return self.timer.timed_out()

    def cancel(self): self.timer.cancel()
    def close(self): self.timer.close(); self.inflight = None
