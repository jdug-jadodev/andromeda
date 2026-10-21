"""Post-native whole-current-request validation and bounded Android grants only.

No volume executor, network, shell, audio or persistence. 'A la mitad' means
50 percent of the maximum MEDIA index, not half the current level.
"""
import re
import uuid
from doja_intents import normalized, number


def current_volume(human):
    if (not isinstance(human, str) or not 0 < len(human) <= 250 or
            re.search(r'[\x00-\x1f\x7f"\'«»<>]|\d[.,]\d', human)):
        raise ValueError('invalid_volume_request')
    plain = normalized(human).strip(' .,!¡?¿')
    plain = re.sub(r'^andromeda[, ]+', '', plain)
    plain = re.sub(r'^por favor ', '', plain)
    match = re.fullmatch(r'(?:baja|baje|bajale|sube|suba|subele|pon|ponme|ponle|ajusta|establece) el volumen'
        r'(?: del telefono| multimedia)? (?:a|al) (.+?)(?: por favor)?', plain)
    if not match: raise ValueError('explicit_volume_required')
    value = match[1]
    if value == 'la mitad': return 50
    value = re.sub(r'(?: por ciento|\s*%)$', '', value)
    if value == 'cien': percent = 100
    else:
        if any(c.isdecimal() and c not in '0123456789' for c in value): raise ValueError('invalid_volume_number')
        percent = number(value)
    if not 0 <= percent <= 100: raise ValueError('volume_out_of_range')
    return percent


def result_text(status, requested, actual=-1):
    if status in {'APPLIED', 'UNCHANGED'}:
        return (f'Volumen multimedia al {actual} %.' if actual == requested else
            f'Volumen multimedia aproximadamente al {actual} % (pedido: {requested} %).')
    if status == 'LIMITED': return f'Android dejó el volumen multimedia al {actual} %; no confirmé el {requested} % solicitado.'
    if status == 'REJECTED': return 'Android rechazó el ajuste de volumen. No he confirmado un cambio.'
    return 'No pude confirmar el volumen. Revisa el teléfono antes de pedir otro ajuste.'


class VolumeTrial:
    def __init__(self, clock, response_ms=30000, confirmed=False, maximum_sequence=24):
        if response_ms not in (30000,60000): raise ValueError('invalid_volume_window')
        self.maximum_age_ms=response_ms-3500
        self.clock, self.inflight, self.closed = clock, None, False
        self.consumed = set()
        from volume_dialogue import VolumeDialogue
        self.confirmed = confirmed; self.dialogue = VolumeDialogue(maximum_sequence)

    def authorize(self, result, turn, session, context, session_end):
        if self.confirmed: raise ValueError('volume_confirmation_required')
        return self._authorize(result, turn, session, context, session_end, current_volume(turn['text']), {})

    def authorize_confirmed(self, result, turn, session, context, session_end):
        if not self.confirmed: raise ValueError('volume_form_not_negotiated')
        value = self.dialogue.claim(result, turn, self.clock())
        return self._authorize(value, turn, session, context, session_end, value['percent'],
            {key: value[key] for key in ('volume_origin_turn_id', 'volume_question_turn_id')})

    def _authorize(self, result, turn, session, context, session_end, percent, provenance):
        now = self.clock()
        if (self.closed or self.inflight is not None or len(self.consumed) >= 24 or
                not all(isinstance(v, str) and re.fullmatch('[0-9a-f]{32}', v) for v in (session, context, turn['id'])) or
                turn['id'] in self.consumed or type(turn['seq']) is not int or turn['seq'] < 1 or
                type(turn['speech_end']) is not int or not 0 < turn['speech_end'] <= now + 100 or
                now - turn['speech_end'] >= self.maximum_age_ms or type(session_end) is not int or not now < session_end <= now + 300000):
            raise ValueError('invalid_volume_grant')
        if type(result.get('percent')) is not int or result['percent'] != percent: raise ValueError('volume_mismatch')
        before = min(now + 4000, turn['speech_end'] + self.maximum_age_ms, session_end - 5000)
        if before - now < 500: raise ValueError('insufficient_volume_budget')
        self.consumed.add(turn['id'])
        self.inflight = dict(type='volume_execute', session_id=session, context_id=context, nonce=uuid.uuid4().hex,
            request_turn_id=turn['id'], request_sequence=turn['seq'], speech_end_ms=turn['speech_end'],
            execute_before_ms=before, percent=percent, stream='media', **provenance)
        return {'event': self.inflight, 'executing': True}

    def result(self, packet, session, context):
        grant = self.inflight
        if grant is None: return None
        keys = ('session_id', 'context_id', 'nonce', 'request_turn_id', 'request_sequence', 'percent', 'stream')
        if session != grant['session_id'] or context != grant['context_id'] or any(
                type(packet.get(k)) is not type(grant[k]) or packet[k] != grant[k] for k in keys):
            raise ValueError('unrelated_volume_result')
        status, actual = packet.get('status'), packet.get('actual_percent')
        if status not in {'APPLIED', 'UNCHANGED', 'LIMITED', 'REJECTED', 'UNKNOWN'} or type(actual) is not int or (
                not 0 <= actual <= 100 if status in {'APPLIED', 'UNCHANGED', 'LIMITED'} else actual != -1):
            raise ValueError('invalid_volume_receipt')
        self.inflight = None
        return self.response(status, grant['percent'], actual)

    @staticmethod
    def response(status, requested, actual=-1):
        return dict(text=result_text(status, requested, actual), timer_control='volume_result',
            actions_executed=int(status in {'APPLIED', 'LIMITED'}), close_reason='action_result', offered_more=False)

    def timed_out(self):
        if self.inflight is None or self.clock() < self.inflight['execute_before_ms'] + 500: return None
        grant, self.inflight = self.inflight, None
        return self.response('UNKNOWN', grant['percent'])

    def cancel(self): self.inflight = None; self.dialogue.clear()
    def close(self): self.closed = True; self.cancel()
