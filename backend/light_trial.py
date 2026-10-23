"""RAM-only voice light confirmation and receipt bridge.

The backend selects the capability, but never receives or invents a light
target, color, frame, address, or retry. Android reconstructs the command from
the current human phrase with LightCommandParser and owns the BLE write.
"""
import uuid

from doja_intents import normalized


ASK = '¿Quieres que ejecute esa orden de luces? Di sí para confirmar o no para descartarla.'
CANCELLED = 'He descartado la orden de luces. No cambié ninguna luz.'
YES = {'si', 'si por favor', 'si gracias', 'si hazlo', 'si adelante', 'si correcto',
       'correcto', 'exacto', 'adelante', 'hazlo', 'de acuerdo', 'confirmo', 'dale'}
NO = {'no', 'no gracias', 'cancela', 'cancelar', 'dejalo', 'olvidalo'}
STATUSES = {'COMPLETE_UNCONFIRMED', 'AUTHORIZATION_REQUIRED', 'UNSUPPORTED_ORDER',
            'DEVICE_NOT_FOUND', 'TRANSPORT_ERROR', 'PARTIAL_OR_UNCERTAIN', 'BUSY', 'CLOSED'}


def _text(value):
    return normalized(value).strip(' .,!¡?¿')


class LightTrial:
    def __init__(self, clock, maximum_sequence=24):
        if maximum_sequence not in (24, 64):
            raise ValueError('invalid_light_sequence_scope')
        self.clock = clock
        self.maximum_sequence = maximum_sequence
        self.clear()

    def clear(self):
        self.active = self.armed = False
        self.origin = self.question = ''
        self.origin_sequence = self.question_sequence = 0
        self.created = self.heard_at = 0
        self.inflight = None

    def begin(self, turn):
        if self.active or self.inflight is not None:
            raise ValueError('light_confirmation_already_active')
        self.active = True
        self.armed = False
        self.origin = turn['id']
        self.origin_sequence = turn['seq']
        self.question = turn['id']
        self.question_sequence = turn['seq']
        self.created = turn['speech_end']
        return {'text': ASK, 'timer_control': 'light_question', 'offered_more': False}

    def spoken(self, turn_id, completed):
        if not self.active or self.question != turn_id:
            return
        if completed:
            self.armed = True
            self.heard_at = self.clock()
        else:
            self.clear()

    def human(self, turn, session, context, session_end):
        if not self.active:
            return None
        now = self.clock()
        if (not self.armed or turn['id'] == self.question or turn['seq'] <= self.question_sequence
                or turn['seq'] > self.maximum_sequence or turn['speech_end'] < self.heard_at
                or now - self.created >= 300000):
            self.clear()
            return {'text': CANCELLED, 'timer_control': 'light_cancelled', 'offered_more': True}
        self.armed = False
        try:
            answer = _text(turn['text'])
        except (TypeError, ValueError):
            answer = ''
        if answer in NO:
            self.clear()
            return {'text': CANCELLED, 'timer_control': 'light_cancelled', 'offered_more': True}
        if answer not in YES:
            self.clear()
            return {'text': CANCELLED, 'timer_control': 'light_cancelled', 'offered_more': True}
        before = now + 60000
        if session_end:
            before = min(before, session_end - 5000)
        if before - now < 500:
            self.clear()
            return {'text': 'La confirmación de luces venció. No cambié ninguna luz.',
                    'timer_control': 'light_cancelled', 'offered_more': True}
        self.inflight = {
            'type': 'light_execute', 'session_id': session, 'context_id': context,
            'nonce': uuid.uuid4().hex, 'origin_turn_id': self.origin,
            'origin_sequence': self.origin_sequence, 'request_turn_id': turn['id'],
            'request_sequence': turn['seq'], 'speech_end_ms': turn['speech_end'],
            'execute_before_ms': before,
        }
        return {'event': self.inflight, 'executing': True}

    def result(self, packet, session, context):
        grant = self.inflight
        if grant is None:
            return None
        keys = ('session_id', 'context_id', 'nonce', 'origin_turn_id', 'origin_sequence',
                'request_turn_id', 'request_sequence')
        if session != grant['session_id'] or context != grant['context_id'] or any(
                type(packet.get(key)) is not type(grant[key]) or packet[key] != grant[key] for key in keys):
            raise ValueError('unrelated_light_result')
        status = packet.get('status')
        target_count = packet.get('target_count')
        completed = packet.get('completed_target_count')
        accepted = packet.get('accepted_frame_count')
        detail = packet.get('detail')
        if (status not in STATUSES or type(target_count) is not int or target_count not in (1, 2)
                or type(completed) is not int or not 0 <= completed <= target_count
                or type(accepted) is not int or not 0 <= accepted <= 16
                or not isinstance(detail, str) or len(detail) > 120):
            raise ValueError('invalid_light_receipt')
        self.inflight = None
        if status == 'COMPLETE_UNCONFIRMED':
            text = 'Orden de luces enviada al Bluetooth. No tengo confirmación visual; revisa las luces.'
            actions = 1
        elif status == 'PARTIAL_OR_UNCERTAIN':
            text = 'La orden de luces quedó parcial o incierta. No la repetiré; revisa las luces antes de pedir otra.'
            actions = 0
        elif status == 'TRANSPORT_ERROR':
            text = 'No pude conectar con las luces. No se confirmó ningún cambio.'
            actions = 0
        elif status == 'UNSUPPORTED_ORDER':
            text = 'Esa orden de luces todavía no está validada. No cambié ninguna luz.'
            actions = 0
        else:
            text = 'No pude confirmar el resultado de las luces. Revísalas antes de pedir otra orden.'
            actions = 0
        return {'text': text, 'timer_control': 'light_result', 'actions_executed': actions,
                'close_reason': 'action_result', 'offered_more': False}

    def timed_out(self):
        if self.inflight is None or self.clock() < self.inflight['execute_before_ms'] + 500:
            return None
        self.inflight = None
        return {'text': 'No llegó la confirmación de las luces. El resultado es incierto; revísalas antes de pedir otra.',
                'timer_control': 'light_result', 'actions_executed': 0,
                'close_reason': 'action_result', 'offered_more': False}

    def cancel(self):
        self.clear()

    def close(self):
        self.clear()
