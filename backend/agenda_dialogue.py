"""Bounded, typed agenda form. Native selection opens it; human turns fill it.

No executor/model/history/network/storage. Questions must finish TTS before a
following human turn can supply a field. Never synthesize a complete request.
"""
from dataclasses import dataclass
import re

from agenda_requests import calendar_due, clean, parts
from doja_intents import normalized

SUBJECT = '¿Para qué es el recordatorio?'
WHEN = '¿Cuándo quieres que te lo recuerde?'
ALARM_WHEN = '¿Para qué día y a qué hora quieres la alarma?'
INVALID_TIME = 'Necesito un momento inequívoco y futuro. Puedes decir una fecha y hora, o en cuántos minutos.'
OTHER_REQUEST = 'Hay un aviso pendiente de completar. Di «cancela el aviso» para cambiar de petición.'
CANCELLED = 'He descartado el aviso pendiente. No he guardado ningún evento.'
MAX_AGE_MS = 300000


def subject(text):
    value = clean(text)
    plain = normalized(value)
    if (not 0 < len(value.encode('utf-16-le')) // 2 <= 160 or re.search(r'[;.!?]|\b(?:no|hoy|manana|pasado manana)\b|\ben .+ (?:segundos?|minutos?|horas?)\b', plain)
            or re.search(r'\b(?:pon|ponme|crea|programa|inicia|recuerdame|busca|consulta|investiga|cancela|elimina|enciende|apaga|volumen)\b', plain)
            or plain in {'si', 'no', 'hazlo', 'eso', 'lo mismo'}):
        raise ValueError('Invalid agenda subject')
    return value


def opening(kind, human):
    """Validate only AFTER this agenda kind was selected natively."""
    raw = clean(human)
    if kind == 'REMINDER':
        match = re.fullmatch(r'(?:(?:pon|ponme|crea|programa) (?:un |el )?recordatorio|recu[eé]rdame)(?: (.+))?', raw, re.I)
    elif kind == 'ALARM':
        match = re.fullmatch(r'(?:(?:pon|ponme|crea|programa) (?:una |la )?alarma|alarma)(?: (.+))?', raw, re.I)
    else: raise ValueError('Invalid agenda kind')
    if not match: raise ValueError('Explicit agenda opening required')
    tail = match[1] or ''
    if not tail: return ''
    if kind == 'ALARM': raise ValueError('Incomplete/ambiguous alarm time')
    # A full but ambiguous time is not silently kept as reminder content.
    candidates = parts(kind, raw)
    if candidates: raise ValueError('Incomplete/ambiguous reminder time')
    return subject(re.sub(r'^para ', '', tail, flags=re.I))


@dataclass(frozen=True)
class Human:
    id: str
    seq: int
    text: str
    speech_end: int
    wall: int
    boot: int

    @classmethod
    def from_turn(cls, turn, now, maximum_age=1500, maximum_sequence=24):
        value = cls(turn['id'], turn['seq'], turn['text'], turn['speech_end'], turn['calendar_anchor_ms'], turn['calendar_boot_ms'])
        if (not isinstance(value.id, str) or not re.fullmatch('[0-9a-f]{32}', value.id)
                or type(value.seq) is not int or not 1 <= value.seq <= maximum_sequence or not isinstance(value.text, str)
                or type(value.speech_end) is not int or not 0 < value.speech_end <= now + 100 or now-value.speech_end >= 60000
                or type(value.wall) is not int or value.wall <= 0 or type(value.boot) is not int
                or not 0 < value.boot <= now + 100 or now-value.boot > maximum_age
                or len(value.text.encode('utf-16-le')) // 2 > 250): raise ValueError('Invalid agenda human turn')
        clean(value.text)
        return value


class AgendaDialogue:
    def __init__(self, maximum_sequence=24, alarm_lifecycle=False):
        if maximum_sequence not in (24,64): raise ValueError('Invalid agenda epoch size')
        self.maximum_sequence=maximum_sequence; self.alarm_lifecycle=alarm_lifecycle; self.clear()

    def clear(self):
        self.kind = self.body = self.origin = self.question_turn = ''
        self.created = self.last_seq = self.answers = 0
        self.armed = False
        self.prepared = None

    @property
    def active(self): return bool(self.kind)

    def question(self, human, text=None):
        self.question_turn = human.id; self.armed = False; self.prepared = None
        return {'kind': 'agenda_question', 'event_kind': self.kind,
                'text': text or (ALARM_WHEN if self.kind == 'ALARM' else WHEN if self.body else SUBJECT)}

    def begin(self, kind, turn, now):
        if self.active: raise ValueError('Agenda form already active')
        human = Human.from_turn(turn, now, maximum_age=60000, maximum_sequence=self.maximum_sequence); body = opening(kind, human.text)
        self.kind, self.body, self.origin = kind, body, human.id
        self.created, self.last_seq = human.boot, human.seq
        return self.question(human)

    def spoken(self, turn_id, completed):
        if self.question_turn != turn_id: return
        if completed: self.armed = True
        else: self.clear()

    def answer(self, turn, now):
        if not self.active: return None
        human = Human.from_turn(turn, now, maximum_sequence=self.maximum_sequence)
        if not self.armed or human.seq <= self.last_seq or now-self.created >= MAX_AGE_MS:
            self.clear(); raise ValueError('Agenda question not heard/stale')
        self.last_seq = human.seq; self.armed = False; self.prepared = None
        self.answers += 1
        if self.answers > 8:
            self.clear(); return {'kind': 'agenda_cancelled', 'text': CANCELLED}
        plain = normalized(clean(human.text))
        if plain in {'cancela', 'cancelar', 'cancela el aviso', 'cancela el recordatorio', 'cancela la alarma', 'no', 'dejalo', 'olvidalo'}:
            self.clear(); return {'kind': 'agenda_cancelled', 'text': CANCELLED}
        if re.search(r'\b(?:busca|consulta|investiga|pon|ponme|crea|programa|recuerdame|enciende|apaga|elimina|volumen)\b', plain):
            return self.question(human, OTHER_REQUEST)
        if self.kind == 'REMINDER' and not self.body:
            try: self.body = subject(human.text)
            except ValueError: return self.question(human, SUBJECT)
            return self.question(human)
        try:
            if self.alarm_lifecycle:
                from alarm_schedule import schedule
                time = schedule(self.kind, clean(human.text), human.wall)
            else:
                due, delay = calendar_due(clean(human.text), human.wall); time=dict(due_ms=due,delay_ms=delay)
        except (ValueError, OverflowError): return self.question(human, INVALID_TIME)
        self.prepared = dict(event_kind=self.kind, text=self.body, when_text=clean(human.text), **time,
            zone='America/Bogota', dialogue_origin_turn_id=self.origin,
            dialogue_final_turn_id=human.id, dialogue_final_sequence=human.seq)
        return dict(self.prepared, kind='guided_agenda_proposal')

    def claim(self, result, turn, now):
        human = Human.from_turn(turn, now, maximum_sequence=self.maximum_sequence)
        expected = self.prepared
        if (expected is None or now-self.created >= MAX_AGE_MS
                or expected['dialogue_final_turn_id'] != human.id or expected['dialogue_final_sequence'] != human.seq
                or any(type(result.get(key)) is not type(value) or result[key] != value for key, value in expected.items())):
            raise ValueError('Unrelated/stale guided agenda proposal')
        value = dict(expected); self.clear(); return value
