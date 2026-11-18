"""Estado pendiente de una acción conversacional. Sin E/S, sin modelo, sin ejecutar.

Un solo dueño del estado: este módulo guarda lo que el humano ya dijo y devuelve
la siguiente pregunta o la propuesta final. La app solo habla y ejecuta; no
mantiene una copia del formulario ni compara textos exactos.

Nada aquí autoriza ni ejecuta: una propuesta lista todavía no toca Android. El
llamador sigue pasando por las guardas de siempre (nonce, permiso, idempotencia).

Reglas que este módulo garantiza:

- Una pregunta por turno, y solo por lo que falta o es ambiguo.
- Cada respuesta fusiona: «el martes» no borra «reunión para algo», «mejor a
  las 4» no borra la fecha.
- Lo ambiguo se pregunta («a las 3» → mañana o tarde); no se inventa.
- Recordatorio y alarma se confirman con un resumen de una línea antes de
  guardar. El temporizador se propone directo por ser inmediato y reversible.
- Un cambio de tema no secuestra el aviso: se responde al tema nuevo y el
  pendiente sigue esperando.
"""
from datetime import date, datetime
import re

from agenda_requests import (BOGOTA, AgendaValidationError, calendar_clock, calendar_day,
    calendar_due, clean, day_due, parts)
from alarm_schedule import daily, weekly
from affirmation import is_affirmative, is_negative
from cancel_phrases import is_cancel
from doja_intents import normalized, number
from timer_proposals import TimerValidationError, literal_seconds

SCHEMA = 1
TTL_MS = 10 * 60 * 1000
CANCELLED = 'He descartado el aviso. No he guardado nada.'
STALE = 'Ese aviso ya caducó. Dime otra vez qué quieres recordar.'
MISMATCH = 'Esa fecha no coincide con el día que dijiste. ¿Qué día quieres?'
NO_SUCH_DAY = 'Esa fecha no existe o ya pasó. ¿Qué día quieres?'
PAST = 'Esa fecha u hora ya pasó. ¿Cuál quieres?'

WEEKDAYS = ('lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo')
MONTHS = ('enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
          'septiembre', 'octubre', 'noviembre', 'diciembre')

# Una entrada por herramienta. `slots` es lo que hace falta para proponer.
TOOLS = {
    'REMINDER': dict(slots=('what', 'when'), questions={
        'what': '¿Qué te recuerdo?',
        'when': '¿Para cuándo te lo recuerdo?',
        'hour': '¿A qué hora te lo recuerdo?',
        'day': '¿Qué día te lo recuerdo?'}),
    'ALARM': dict(slots=('when',), questions={
        'when': '¿Para qué día y a qué hora pongo la alarma?',
        'hour': '¿A qué hora pongo la alarma?',
        'day': '¿Para qué día pongo la alarma?'}),
    'TIMER': dict(slots=('duration',), questions={
        'duration': '¿De cuánto tiempo quieres el temporizador?'}),
}

# Verbos de otra petición: durante un aviso pendiente se responde al tema nuevo
# en vez de tratarlo como un dato del formulario.
_OTHER_TOPIC = re.compile(r'\b(?:busca|buscar|consulta|consultar|investiga|chiste|cuentame|'
    r'clima|noticias|musica|pon|ponme|crea|programa|enciende|apaga|elimina|volumen|luces?)\b')
_CORRECTION = re.compile(r'^(?:no|mejor|perdon|era|digamos)\b[, ]*', re.I)
_CONFIRMATION = re.compile(r'^(?:si|claro|dale|confirmo|guardalo|guardala|listo|ok|vale)\b')


class AmbiguousClock(ValueError):
    """Hora sin mañana/tarde: se pregunta, no se asume."""

    def __init__(self, hour, day=None):
        super().__init__('ambiguous_time')
        self.hour = hour
        self.day = day


class NotATime(ValueError):
    """La frase no contiene ninguna expresión de tiempo reconocible."""


class PastTime(ValueError):
    """La fecha u hora entendida ya pasó."""


def new(tool, turn_id, now_ms, nonce=''):
    """Abre un aviso pendiente vacío. No decide todavía qué preguntar."""
    if tool not in TOOLS:
        raise ValueError('Herramienta sin diálogo pendiente')
    if not isinstance(turn_id, str) or type(now_ms) is not int or now_ms <= 0:
        raise ValueError('Aviso pendiente inválido')
    return {'schema': SCHEMA, 'tool': tool, 'slots': {}, 'origin_turn': turn_id,
            'nonce': str(nonce), 'created_ms': now_ms, 'updated_ms': now_ms, 'asked': ''}


def load(value):
    """Valida un aviso guardado (SQLite, RAM o reconexión)."""
    if not isinstance(value, dict) or value.get('schema') != SCHEMA:
        raise ValueError('Aviso pendiente de otro esquema')
    if value.get('tool') not in TOOLS or not isinstance(value.get('slots'), dict):
        raise ValueError('Aviso pendiente inválido')
    for key in ('origin_turn', 'nonce', 'asked'):
        if not isinstance(value.get(key), str):
            raise ValueError('Aviso pendiente inválido')
    for key in ('created_ms', 'updated_ms'):
        if type(value.get(key)) is not int or value[key] <= 0:
            raise ValueError('Aviso pendiente inválido')
    return value


def expired(pending, now_ms):
    if type(now_ms) is not int:
        raise ValueError('Reloj inválido')
    return now_ms - pending['created_ms'] >= TTL_MS


def missing(pending):
    """Primer dato que falta, en orden de conversación. None si ya está listo."""
    slots = pending['slots']
    if pending['tool'] == 'TIMER':
        return None if slots.get('duration_ms') else 'duration'
    if pending['tool'] == 'REMINDER' and not slots.get('what'):
        return 'what'
    if slots.get('delay_ms') or slots.get('weekly'):
        return None
    if not slots.get('day'):
        return 'day' if slots.get('hour') is not None else 'when'
    return None if slots.get('hour') is not None else 'hour'


def _spoken_hour(text):
    """Hora hablada de la parte del reloj («el 24 a las 8» → 8)."""
    plain = re.sub(r'^(?:para )?', '', normalized(clean(text)))
    split = re.search(r'(?:^| )a (?:las? )?(.+)$', plain)
    clock = re.sub(r'^(?:las? )', '', split[1] if split else plain)
    token = clock.split(' ')[0] if clock else ''
    try:
        return number(token)
    except ValueError:
        return None


_WEEKDAY_WORD = re.compile(r'\b(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)\b')
_DATED = re.compile(r'\b(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)\b|'
    r'\b(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\b|'
    r'\bel [0-9]|[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4}')

_PERIOD = re.compile(r'\b(?:(?:de|en|por) la (manana|tarde|noche)|(a\.?\s*m\.?|p\.?\s*m\.?|a eme|pe eme))\b')


def _time_slots(kind, text, anchor_ms):
    """Traduce una frase a datos de tiempo, o falla sin inventar."""
    plain = normalized(clean(text))
    if not plain:
        raise NotATime()
    repeat = bool(re.search(r'\b(?:todos los|todas las|cada)\b', plain))
    if kind == 'ALARM' or repeat:
        for parse in (weekly, daily):
            try:
                value = parse(plain, anchor_ms)
            except AgendaValidationError:
                value = None
            if value is not None:
                return {'weekly': {'weekday': int(value['weekday']),
                    'minute_of_day': int(value['minute_of_day'])}, 'due_ms': int(value['due_ms'])}
    try:
        due, delay = calendar_due(plain, anchor_ms)
    except AgendaValidationError as error:
        reason = str(error)
        if reason == 'missing_time':
            return {'day': calendar_day(plain, anchor_ms).isoformat()}
        if reason == 'ambiguous_time':
            # Conserva el día ya dicho; solo falta mañana/tarde.
            day_text = re.sub(r' a (?:las? )?.+$', '', plain)
            day = None
            if day_text != plain:
                try:
                    day = calendar_day(day_text, anchor_ms).isoformat()
                except AgendaValidationError:
                    day = None
            raise AmbiguousClock(_spoken_hour(plain), day) from None
        if reason == 'past_or_range':
            raise PastTime() from None
        # Puede ser solo la hora («a las 4 de la tarde») sin día.
        try:
            hour, minute = calendar_clock(plain)
        except AgendaValidationError as clock_error:
            if str(clock_error) == 'ambiguous_time':
                raise AmbiguousClock(_spoken_hour(plain)) from None
            # Una fecha que se entendió mal se dice; no se disfraza de tema.
            if _DATED.search(plain):
                raise AgendaValidationError(
                    'invalid_date' if re.search(_WEEKDAY_WORD, plain) else 'invalid_day') from None
            raise NotATime() from None
        return {'hour': hour, 'minute': minute}
    if delay and delay > 0:
        return {'delay_ms': int(delay), 'due_ms': int(due)}
    instant = datetime.fromtimestamp(due / 1000, BOGOTA)
    return {'day': instant.date().isoformat(), 'hour': instant.hour, 'minute': instant.minute}


def _finalize(slots, anchor_ms):
    """Calcula el instante absoluto (y lo valida futuro) cuando ya hay día y hora."""
    if slots.get('day') and slots.get('hour') is not None:
        clock = f'{int(slots["hour"])}:{int(slots.get("minute") or 0):02d}'
        try:
            slots['due_ms'], _ = day_due(date.fromisoformat(slots['day']), clock, anchor_ms)
        except AgendaValidationError:
            raise PastTime() from None
    elif not slots.get('weekly') and not slots.get('delay_ms'):
        slots.pop('due_ms', None)
    return slots


def _merge_time(pending, text, anchor_ms):
    """Fusiona los datos de tiempo nuevos sobre los que ya había."""
    incoming = _time_slots(pending['tool'], text, anchor_ms)
    slots = dict(pending['slots'])
    # Una forma nueva reemplaza a la anterior, pero solo en lo que se dijo:
    # «mejor a las 4» conserva el día; «no, el 24» conserva la hora.
    if 'weekly' in incoming or 'delay_ms' in incoming:
        for stale in ('day', 'hour', 'minute', 'delay_ms', 'weekly'):
            slots.pop(stale, None)
        slots.update(incoming)
        return slots
    for key in ('day', 'hour', 'minute'):
        if key in incoming:
            slots[key] = incoming[key]
    slots.pop('weekly', None)
    slots.pop('delay_ms', None)
    return _finalize(slots, anchor_ms)


def _date_text(day):
    value = date.fromisoformat(day)
    # El año siempre explícito: si la fecha se rodó al mes o año siguiente, el
    # resumen lo dice en vez de asumirlo en silencio.
    return f'{WEEKDAYS[value.weekday()]} {value.day} de {MONTHS[value.month - 1]} de {value.year}'


def _clock_text(hour, minute):
    return f'{int(hour):02d}:{int(minute or 0):02d}'


def _duration_text(milliseconds):
    seconds = int(milliseconds) // 1000
    if seconds >= 3600 and seconds % 3600 == 0:
        hours = seconds // 3600
        return f'{hours} hora' + ('' if hours == 1 else 's')
    if seconds % 60 == 0:
        minutes = seconds // 60
        return f'{minutes} minuto' + ('' if minutes == 1 else 's')
    return f'{seconds} segundos'


def summary(pending):
    """Resumen de una línea para confirmar antes de guardar."""
    slots = pending['slots']
    if pending['tool'] == 'TIMER':
        return f'Temporizador de {_duration_text(slots["duration_ms"])}.'
    if slots.get('weekly'):
        minute_of_day = int(slots['weekly']['minute_of_day'])
        clock = _clock_text(minute_of_day // 60, minute_of_day % 60)
        weekday = int(slots['weekly']['weekday'])
        when = (f'todos los días a las {clock}' if weekday == 0
                else f'todos los {WEEKDAYS[weekday - 1]} a las {clock}')
    elif slots.get('delay_ms'):
        when = f'en {_duration_text(slots["delay_ms"])}'
    else:
        when = f'el {_date_text(slots["day"])} a las {_clock_text(slots["hour"], slots["minute"])}'
    if pending['tool'] == 'ALARM':
        return f'Alarma {when}. ¿La guardo?'
    return f'“{slots["what"]}”, {when}. ¿Lo guardo?'


def _ready(pending):
    slots = pending['slots']
    value = {'tool': pending['tool'], 'slots': dict(slots)}
    if pending['tool'] == 'TIMER':
        value['duration_ms'] = int(slots['duration_ms'])
    elif slots.get('weekly'):
        value['weekly'] = dict(slots['weekly'])
        value['due_ms'] = int(slots['due_ms'])
    elif slots.get('delay_ms'):
        value['delay_ms'] = int(slots['delay_ms'])
        value['due_ms'] = int(slots['due_ms'])
    else:
        value['due_ms'] = int(slots['due_ms'])
    return {'kind': 'pending_ready', 'tool': pending['tool'], 'text': summary(pending),
            'confirm': pending['tool'] in ('REMINDER', 'ALARM'), 'value': value,
            'pending': pending}


def _question(pending, slot):
    pending['asked'] = slot
    return {'kind': 'pending_question', 'tool': pending['tool'], 'slot': slot,
            'text': TOOLS[pending['tool']]['questions'][slot], 'pending': pending}


def _invalid(error):
    """Mensaje según el fallo: día que no existe o fecha que no cuadra."""
    return NO_SUCH_DAY if str(error) == 'invalid_day' else MISMATCH


def _subject(text):
    value = clean(text)
    value = re.sub(r'^(?:que |que te recuerde |para |de )', '', value, flags=re.I)
    return value.strip(' .,!¡?¿')


def _opening(kind, text, anchor_ms):
    """Datos completos dentro de la misma orden («recuérdame … el martes a las 3»)."""
    for when, body in parts(kind, clean(text)):
        try:
            slots = _time_slots(kind, when, anchor_ms)
        except (AgendaValidationError, AmbiguousClock, NotATime, PastTime):
            continue
        return (_subject(body) if kind == 'REMINDER' else ''), slots
    return None


def _subject_and_time(text, anchor_ms):
    """«reunión para algo el martes a las 3»: tema y cuándo en la misma frase.

    Reutiliza el mismo parser de orden completa añadiendo solo el prefijo que
    exige; el tema y el tiempo salen literales del texto humano, sin repararlo.
    """
    for when, body in parts('REMINDER', 'recuerdame ' + clean(text)):
        if not body:
            continue
        try:
            slots = _time_slots('REMINDER', when, anchor_ms)
        except (AgendaValidationError, AmbiguousClock, NotATime, PastTime):
            continue
        return _subject(body), slots
    return None


def _propose(pending, anchor_ms):
    slot = missing(pending)
    if slot:
        return _question(pending, slot)
    return _ready(pending)


def merge(pending, text, anchor_ms, now_ms=None):
    """Fusiona un turno humano. Devuelve el resultado con el aviso actualizado."""
    pending = dict(load(pending), slots=dict(load(pending)['slots']))
    if type(now_ms) is not int or type(anchor_ms) is not int or anchor_ms <= 0:
        raise ValueError('Reloj inválido')
    if expired(pending, now_ms):
        return {'kind': 'pending_stale', 'text': STALE, 'pending': None}
    try:
        plain = normalized(clean(text))
    except AgendaValidationError:
        # Texto que la casa no acepta (comillas, longitud, dígitos raros): se
        # vuelve a preguntar lo que falta, sin abrir ni cerrar nada.
        return _question(pending, missing(pending) or TOOLS[pending['tool']]['slots'][0])
    if is_cancel(text) or plain in {'no', 'nada', 'dejalo', 'olvidalo', 'asi no', 'mejor no'}:
        return {'kind': 'pending_cancelled', 'text': CANCELLED, 'pending': None}
    if _OTHER_TOPIC.search(plain):
        return {'kind': 'pending_other_topic', 'text': None, 'pending': pending}
    pending['updated_ms'] = now_ms

    # Respuesta a «¿3 de la mañana o de la tarde?»: solo el período.
    if pending.get('asked') == 'period' and pending.get('asked_hour'):
        period = _PERIOD.search(plain)
        if period:
            morning = (period.group(1) or '').startswith(('manana', 'manan')) or \
                (period.group(2) or '').startswith('a')
            hour = int(pending['asked_hour']) % 12 + (0 if morning else 12)
            pending['slots']['hour'] = hour
            pending['slots'].setdefault('minute', 0)
            pending['asked'] = ''
            pending.pop('asked_hour', None)
            try:
                pending['slots'] = _finalize(pending['slots'], anchor_ms)
            except PastTime:
                return {'kind': 'pending_invalid', 'text': PAST, 'pending': pending}
            return _propose(pending, anchor_ms)

    if pending['tool'] == 'TIMER':
        try:
            pending['slots']['duration_ms'] = int(literal_seconds(text)) * 1000
        except (TimerValidationError, ValueError):
            return _question(pending, 'duration')
        return _ready(pending)

    opening = _opening(pending['tool'], text, anchor_ms)
    if opening is not None:
        subject, slots = opening
        if subject and pending['tool'] == 'REMINDER':
            pending['slots']['what'] = subject
        pending['slots'].update(slots)
        try:
            pending['slots'] = _finalize(pending['slots'], anchor_ms)
        except PastTime:
            return {'kind': 'pending_invalid', 'text': PAST, 'pending': pending}
        return _propose(pending, anchor_ms)

    if pending['tool'] == 'REMINDER' and not pending['slots'].get('what'):
        combined = _subject_and_time(text, anchor_ms)
        if combined is not None:
            pending['slots']['what'] = combined[0]
            pending['slots'].update(combined[1])
            try:
                pending['slots'] = _finalize(pending['slots'], anchor_ms)
            except PastTime:
                return {'kind': 'pending_invalid', 'text': PAST, 'pending': pending}
            return _propose(pending, anchor_ms)

    correction = _CORRECTION.match(plain)
    if correction:
        remainder = text[correction.end():]
        try:
            pending['slots'] = _merge_time(pending, remainder, anchor_ms)
            return _propose(pending, anchor_ms)
        except AmbiguousClock as ambiguous:
            return _ambiguous(pending, ambiguous)
        except PastTime:
            return {'kind': 'pending_invalid', 'text': PAST, 'pending': pending}
        except AgendaValidationError as error:
            return {'kind': 'pending_invalid', 'text': _invalid(error), 'pending': pending}
        except NotATime:
            if pending['tool'] == 'REMINDER' and _subject(remainder):
                pending['slots']['what'] = _subject(remainder)
                return _propose(pending, anchor_ms)
            return _question(pending, missing(pending) or 'when')

    try:
        pending['slots'] = _merge_time(pending, text, anchor_ms)
        return _propose(pending, anchor_ms)
    except AmbiguousClock as ambiguous:
        return _ambiguous(pending, ambiguous)
    except PastTime:
        return {'kind': 'pending_invalid', 'text': PAST, 'pending': pending}
    except AgendaValidationError as error:
        return {'kind': 'pending_invalid', 'text': _invalid(error), 'pending': pending}
    except NotATime:
        # Si ya estaba completo, «sí» no lo degrada a pregunta: se repite el
        # resumen. La confirmación real la decide resolve_confirmation().
        if missing(pending) is None:
            return _ready(pending)
        if pending['tool'] == 'REMINDER':
            subject = _subject(text)
            if subject and not pending['slots'].get('what'):
                pending['slots']['what'] = subject
                return _propose(pending, anchor_ms)
        return _question(pending, missing(pending) or 'when')


def _ambiguous(pending, ambiguous):
    """Pregunta solo mañana/tarde, sin perder el día ya dicho."""
    hour = ambiguous.hour
    if ambiguous.day and not pending['slots'].get('day'):
        pending['slots']['day'] = ambiguous.day
    if hour is None:
        return _question(pending, 'hour')
    if hour == 12:
        text = '¿Mediodía o medianoche?'
    elif 1 <= hour <= 11:
        text = f'¿{hour} de la mañana o de la tarde?'
    else:
        return _question(pending, 'hour')
    pending['asked'] = 'period'
    pending['asked_hour'] = int(hour)
    return {'kind': 'pending_question', 'tool': pending['tool'], 'slot': 'period',
            'text': text, 'pending': pending}


def resolve_confirmation(pending, text):
    """Interpreta la respuesta al resumen. No guarda nada por sí mismo."""
    pending = load(pending)
    if is_cancel(text) or is_negative(text):
        return 'cancelled'
    if is_affirmative(text) or _CONFIRMATION.match(normalized(clean(text))):
        return 'confirmed'
    return 'unclear'
