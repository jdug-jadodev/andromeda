"""Bounded, typed agenda form. Native selection opens it; human turns fill it.

No executor/model/history/network/storage. Questions must finish TTS before a
following human turn can supply a field. Never synthesize a complete request.
"""
from dataclasses import dataclass
import re

from agenda_requests import AgendaValidationError, calendar_day, calendar_due, day_due, clean, parts
from doja_intents import normalized

SUBJECT = '¿Para qué es el recordatorio?'
WHEN = '¿Cuándo quieres que te lo recuerde?'
ALARM_WHEN = '¿Para qué día y a qué hora quieres la alarma?'
INVALID_TIME = 'Necesito un momento inequívoco y futuro. Puedes decir una fecha y hora, o en cuántos minutos.'
OTHER_REQUEST = 'Hay un aviso pendiente de completar. Di «cancela el aviso» para cambiar de petición.'
CANCELLED = 'He descartado el aviso pendiente. No he guardado ningún evento.'
REMINDER_POLICY = 'confirmed_reminder_intent_v1'
REMINDER_INTENT = '¿Quieres crear un recordatorio?'
REMINDER_YES = {'si', 'si por favor', 'si gracias', 'si quiero', 'si quiero hacerlo',
    'si quiero un recordatorio', 'si quiero crear un recordatorio', 'quiero un recordatorio',
    'quiero crear un recordatorio', 'correcto', 'de acuerdo', 'claro', 'claro que si', 'vale', 'confirmo'}
MAX_AGE_MS = 300000


def subject(text):
    value = clean(text)
    plain = normalized(value)
    if (not 0 < len(value.encode('utf-16-le')) // 2 <= 160 or re.search(r'[;.!?]|\b(?:no|hoy|manana|pasado manana)\b|\b(?:en|dentro de) .+ (?:segundos?|minutos?|horas?|dias?)\b', plain)
            or re.search(r'\b(?:pon|ponme|crea|programa|inicia|recuerdame|busca|consulta|investiga|cancela|elimina|enciende|apaga|volumen)\b', plain)
            or plain in {'si', 'no', 'hazlo', 'eso', 'lo mismo'}):
        raise ValueError('Invalid agenda subject')
    return value


def _literal_opening(kind, human):
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


def may_clarify_reminder(human):
    """Only permits a QUESTION after native selection, never an action or fields.

    No fuzzy substitution of ASR words, repaired command, history or model data.
    A fresh affirmative and fresh answers to the fields are still required.
    """
    raw = clean(human); plain = normalized(raw)
    return (not re.search(r'[`;]|\b(?:no|nunca|jamas|ni|si|ayer|dije|dijiste|dijo|dijeron|ejemplo|imagina|supongamos|'
        r'busca|buscar|consulta|internet|web|google|whatsapp|alarma|alarmas|temporizador|volumen|'
        r'cancela|cancelar|borra|elimina|apaga|enciende|lista|listar|listame|muestra|muestrame|lee|leeme|leer)\b', plain)
        and not re.match(r'^(?:que|cuales|cuantos)\b', plain)
        and bool(re.search(r'\b(?:recordatorios?|recordar(?:me|lo)?|recuerdame|recuerdes|avisame|avisarme|avises)\b',plain)))


def dated_opening(kind, human, anchor_ms):
    """One literal subject and date, still missing an hour. No action or ASR repair."""
    if kind != 'REMINDER': return None
    matches = []
    for when, body in parts(kind, clean(human)):
        try: matches.append((subject(body), calendar_day(when, anchor_ms)))
        except (ValueError, OverflowError): continue
    return matches[0] if len(matches) == 1 else None


def hour_question(day):
    return f'¿A qué hora quieres el recordatorio para el {day:%d/%m/%Y}? Indica mañana o tarde, o usa formato de 24 horas.'


def opening(kind, human, reminder_clarification=False, anchor_ms=None):
    if anchor_ms is not None:
        partial = dated_opening(kind, human, anchor_ms)
        if partial is not None: return partial[0]
    try: return _literal_opening(kind, human)
    except ValueError:
        if reminder_clarification and kind == 'REMINDER' and may_clarify_reminder(human): return None
        raise


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
    def __init__(self, maximum_sequence=24, alarm_lifecycle=False, reminder_clarification=False):
        if maximum_sequence not in (24,64): raise ValueError('Invalid agenda epoch size')
        if type(reminder_clarification) is not bool: raise ValueError('Invalid reminder clarification mode')
        self.maximum_sequence=maximum_sequence; self.alarm_lifecycle=alarm_lifecycle
        self.reminder_clarification=reminder_clarification; self.clear()

    def clear(self):
        self.kind = self.body = self.origin = self.question_turn = ''
        self.created = self.last_seq = self.answers = 0
        self.armed = False
        self.prepared = None
        self.day = None  # Numeric local date only; frozen at the human turn that supplied it.
        self.needs_intent = False; self.heard_at = self.question_speech_end = 0

    @property
    def active(self): return bool(self.kind)

    def question(self, human, text=None):
        self.question_turn = human.id; self.armed = False; self.prepared = None
        self.heard_at = 0; self.question_speech_end = human.speech_end
        return {'kind': 'agenda_question', 'event_kind': self.kind,
                'text': text or (REMINDER_INTENT if self.needs_intent else ALARM_WHEN if self.kind == 'ALARM'
                    else hour_question(self.day) if self.day is not None else WHEN if self.body else SUBJECT)}

    def begin(self, kind, turn, now):
        if self.active: raise ValueError('Agenda form already active')
        human = Human.from_turn(turn, now, maximum_age=60000, maximum_sequence=self.maximum_sequence)
        body = opening(kind, human.text, self.reminder_clarification, human.wall)
        self.kind, self.body, self.origin = kind, body or '', human.id; self.needs_intent = body is None
        partial = dated_opening(kind, human.text, human.wall)
        if partial is not None: self.day = partial[1]
        self.created, self.last_seq = human.boot, human.seq
        return self.question(human)

    def spoken(self, turn_id, completed, now=None):
        if self.question_turn != turn_id: return
        if completed and (not self.reminder_clarification or self.kind != 'REMINDER'
                or type(now) is int and now >= self.question_speech_end):
            self.armed = True; self.heard_at = now or 0
        else: self.clear()

    def answer(self, turn, now):
        if not self.active: return None
        human = Human.from_turn(turn, now, maximum_sequence=self.maximum_sequence)
        if (not self.armed or human.seq <= self.last_seq or now-self.created >= MAX_AGE_MS
                or self.reminder_clarification and self.kind == 'REMINDER'
                and (human.id == self.question_turn or human.speech_end < self.heard_at)):
            self.clear(); raise ValueError('Agenda question not heard/stale')
        self.last_seq = human.seq; self.armed = False; self.prepared = None
        self.answers += 1
        if self.answers > 8:
            self.clear(); return {'kind': 'agenda_cancelled', 'text': CANCELLED}
        plain = normalized(clean(human.text))
        if plain in {'cancela', 'cancelar', 'cancela el aviso', 'cancela el recordatorio', 'cancela la alarma', 'no', 'dejalo', 'olvidalo'}:
            self.clear(); return {'kind': 'agenda_cancelled', 'text': CANCELLED}
        if self.needs_intent:
            confirmation = re.sub(r'\s*,\s*',' ',plain).strip(' .,!¡?¿')
            if confirmation in {'no gracias', 'no quiero', 'no quiero un recordatorio'}:
                self.clear(); return {'kind':'agenda_cancelled','text':CANCELLED}
            if confirmation not in REMINDER_YES: return self.question(human)
            self.needs_intent = False; self.origin = human.id
            return self.question(human)  # Affirmation supplies intent ONLY, never content/time.
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
        except (ValueError, OverflowError):
            if self.kind != 'REMINDER': return self.question(human, INVALID_TIME)
            try: day = calendar_day(human.text, human.wall)
            except (ValueError, OverflowError): pass
            else:
                self.day = day
                return self.question(human)  # Date understood; never supply an hour from history/current time.
            if self.day is None: return self.question(human, INVALID_TIME)
            try:
                due, delay = day_due(self.day, human.text, human.wall)
                time = dict(due_ms=due, delay_ms=delay)
                if self.alarm_lifecycle: time.update(weekday=0, minute_of_day=-1)
            except (ValueError, OverflowError) as error:
                if isinstance(error, AgendaValidationError) and str(error) == 'ambiguous_time':
                    return self.question(human)  # Keep only the understood date, not the ambiguous clock.
                self.day = None
                return self.question(human, INVALID_TIME)
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
