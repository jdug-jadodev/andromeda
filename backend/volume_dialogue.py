"""Native-selected volume form: human numbers + heard, fresh human confirmation.

No model arguments, executor, history, network or storage. Free phrasing opens a
proposal, NEVER an execution permit. Python and Android reconstruct independently.
"""
import re
from dataclasses import dataclass
from doja_intents import normalized, number, ONES, TENS

ASK = '¿A qué porcentaje, de cero a cien, quieres dejar el volumen multimedia del teléfono?'
CANCELLED = 'He descartado el ajuste pendiente. No he cambiado el volumen.'
MAX_AGE_MS = 300000
YES = {'si', 'si por favor', 'si gracias', 'si hazlo', 'si adelante', 'si correcto', 'correcto', 'exacto', 'adelante', 'hazlo', 'de acuerdo', 'confirmo', 'dale'}
NO = {'no', 'no gracias', 'cancela', 'cancelar', 'cancela el ajuste', 'cancela el volumen', 'dejalo', 'olvidalo'}
NAMES = '|'.join(sorted((set(ONES) - {'un', 'una'}) | set(TENS) | {'cien', 'ciento'}, key=lambda item: (-len(item), item)))
NUMERIC = re.compile(r'\b(?:[0-9]+|(?:' + NAMES + r')(?: y (?:uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve))?)\b')


def plain(text):
    if (not isinstance(text, str) or not 0 < len(text.encode('utf-16-le')) // 2 <= 250
            or re.search(r'[\x00-\x1f\x7f"\'«»<>]', text)):
        raise ValueError('invalid_volume_text')
    return normalized(text).strip(' .,!¡?¿')


def candidate(text):
    """Extract ONE human value, not intent/authority. A subsequent yes is REQUIRED."""
    value = plain(text)
    if (re.search(r'\b(?:no|nunca|jamas|ni|sin|si|ayer|dije|ejemplo|imagina|supongamos|manana|cuando|'
                  r'tv|televisor|television|alarma|alarmas|llamada|llamadas|timbre|notificaciones|busca|internet|'
                  r'recordatorio|temporizador|enciende|apaga|elimina|borra)\b', value)
            or re.search(r'[0-9][.,][0-9]|-\s*[0-9]', value)
            or any(char.isdecimal() and char not in '0123456789' for char in value)):
        raise ValueError('volume_scope_not_authorized')
    # Only spelling of the unit is equivalent. Never repair a human number.
    value = re.sub(r'\bun(?=\s*(?:por (?:ciento|cien)\b|porciento\b|%))', 'uno', value)
    if value in {'un','una'}: value = 'uno'
    value = re.sub(r'\bpor (?:ciento|cien)\b|\bporciento\b|%', ' ', value)
    found = [match.group() for match in NUMERIC.finditer(value)]
    halves = re.findall(r'\bmitad\b', value)
    if halves:
        return 50 if len(halves) == 1 and not found and not re.search(r'\bactual\b', value) else None
    if len(found) != 1: return None
    try: percent = 100 if found[0] in {'cien', 'ciento'} else number(found[0])
    except ValueError: return None
    return percent if 0 <= percent <= 100 else None


def confirm(percent):
    return f'¿Quieres que deje el volumen multimedia del teléfono al {percent} por ciento?'


@dataclass(frozen=True)
class Human:
    id: str
    seq: int
    text: str
    speech_end: int

    @classmethod
    def from_turn(cls, turn, now, maximum_sequence):
        value = cls(turn['id'], turn['seq'], turn['text'], turn['speech_end'])
        if (not isinstance(value.id, str) or not re.fullmatch('[0-9a-f]{32}', value.id)
                or type(value.seq) is not int or not 1 <= value.seq <= maximum_sequence
                or type(value.speech_end) is not int or not 0 < value.speech_end <= now + 100
                or now - value.speech_end >= 60000 or not isinstance(value.text, str)
                or not 0 < len(value.text.encode('utf-16-le')) // 2 <= 250):
            raise ValueError('invalid_volume_human_turn')
        return value


class VolumeDialogue:
    def __init__(self, maximum_sequence=24):
        if maximum_sequence not in (24, 64): raise ValueError('invalid_volume_sequence_scope')
        self.maximum_sequence = maximum_sequence; self.clear()

    def clear(self):
        self.active = self.armed = False
        self.origin = self.question_turn = ''
        self.created = self.last_seq = self.answers = self.heard_at = 0
        self.percent = self.prepared = None

    def cancelled(self):
        self.clear(); return {'kind': 'volume_cancelled', 'text': CANCELLED}

    def question(self, human):
        self.question_turn = human.id; self.armed = False; self.prepared = None
        return {'kind': 'volume_question', 'text': ASK if self.percent is None else confirm(self.percent)}

    def begin(self, turn, now):
        if self.active: raise ValueError('volume_form_already_active')
        human = Human.from_turn(turn, now, self.maximum_sequence)
        try: percent = candidate(human.text)
        except ValueError: return self.cancelled()
        self.active = True; self.created = human.speech_end; self.last_seq = human.seq
        self.percent = percent; self.origin = human.id if percent is not None else ''
        return self.question(human)

    def spoken(self, turn_id, completed, now):
        if not self.active or self.question_turn != turn_id: return
        if completed: self.armed = True; self.heard_at = now
        else: self.clear()

    def answer(self, turn, now):
        if not self.active: return None
        human = Human.from_turn(turn, now, self.maximum_sequence)
        if (not self.armed or human.seq <= self.last_seq or human.id == self.question_turn
                or human.speech_end < self.heard_at or now - self.created >= MAX_AGE_MS):
            return self.cancelled()
        self.last_seq = human.seq; self.armed = False; self.answers += 1
        if self.answers > 6: return self.cancelled()
        try: text = re.sub(r'\s*,\s*', ' ', plain(human.text))
        except ValueError: return self.cancelled()
        if text in NO: return self.cancelled()
        if self.percent is not None and text in YES:
            self.prepared = dict(kind='confirmed_volume_proposal', percent=self.percent,
                volume_origin_turn_id=self.origin, volume_question_turn_id=self.question_turn,
                request_turn_id=human.id, request_sequence=human.seq)
            return dict(self.prepared)
        if text in YES: return self.question(human)  # A yes cannot invent a missing number.
        # A new tool request revokes this form and goes to native selection; it
        # cannot become a number, confirmation or a synthetic volume command.
        if re.search(r'\b(?:busca|consulta|investiga|recordatorio|recuerdame|alarma|temporizador|enciende|apaga|elimina|borra)\b', text):
            self.clear(); return None
        try: percent = candidate(human.text)
        except ValueError: return self.cancelled()
        if percent is None:
            if self.percent is not None: self.clear(); return None
            return self.question(human)
        self.percent = percent; self.origin = human.id
        return self.question(human)  # Corrections always require a NEW heard question.

    def claim(self, result, turn, now):
        human = Human.from_turn(turn, now, self.maximum_sequence); expected = self.prepared
        if (expected is None or now - self.created >= MAX_AGE_MS
                or expected['request_turn_id'] != human.id or expected['request_sequence'] != human.seq
                or set(result) != set(expected) or any(type(result[key]) is not type(value) or result[key] != value for key, value in expected.items())):
            raise ValueError('unrelated_volume_confirmation')
        value = dict(expected); self.clear(); return value
