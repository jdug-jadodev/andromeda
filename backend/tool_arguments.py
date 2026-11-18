"""Datos de herramienta propuestos por el modelo, validados como datos.

El modelo (DeepSeek) deja de ser solo un selector de nombres: devuelve los datos
del recordatorio o la alarma. Para que eso sea seguro sin volver al texto exacto:

1. **Prueba de origen.** `text` y `when_text` deben ser fragmentos literales de
   lo que dijo el humano (comparados sin tildes ni mayúsculas). Si el modelo
   inventa un tema o una fecha que nadie dijo, se rechaza.
2. **El parser manda cuando puede.** Si el fragmento de tiempo se entiende con el
   parser actual (que ya conoce «el martes», «el 24», «mañana»), el instante se
   calcula ahí. El modelo solo separa el tema del tiempo.
3. **La fecha del modelo se valida como dato.** Cuando el fragmento no se puede
   parsear, se usan `date`/`time` del modelo, pero se exige fecha real, futura,
   dentro de un año, con hora válida y coherente con el día de la semana que se
   nombró.
4. **Nada se ejecuta aquí.** Se devuelve una propuesta con el instante resuelto.
   La confirmación («¿Lo guardo?») y las guardas de siempre siguen mandando.

Todo está apagado por defecto (`ANDROMEDA_TOOL_ARGUMENTS=1` lo enciende): mientras
la app no sepa ejecutar el valor autorizado en vez de re-parsear la frase, esto no
debe usarse en el teléfono.
"""
from datetime import date, datetime, timedelta
import os
import re

from agenda_requests import (BOGOTA, MAX_AHEAD_MS, AgendaValidationError, calendar_clock,
    calendar_due, clean)
from alarm_schedule import weekly
from doja_intents import normalized, number

FLAG = 'ANDROMEDA_TOOL_ARGUMENTS'
SCHEMA_VERSION = 1

WEEKDAYS = ('lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo')

# Repetición explícita: sin esto, un día de la semana en un recordatorio es una
# sola vez (la app actual no sabe guardar series).
_REPEAT_WORDS = re.compile(r'\b(?:todos los|todas las|cada|los)\b')
_DAILY = re.compile(r'^(?:todos los dias(?: de la semana)?|cada dia|de lunes a domingo)'
                    r'(?: a (?:las? )?(.+))$')
_WEEKDAY_IN_TEXT = re.compile(r'\b(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)\b')
# Un fragmento que ya trae su propia fecha («el 24 de noviembre», «2026-11-24»):
# si el parser lo rechaza, no se rescata con la fecha del modelo; se dice.
_SPAN_HAS_DATE = re.compile(r'\b(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|'
    r'octubre|noviembre|diciembre)\b|[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4}|'
    r'\bel [0-9]{1,2}\b')

MESSAGES = {
    'no_evidence': 'No encontré esas palabras en lo que dijiste. ¿Me lo repites?',
    'bad_date': 'No entendí bien la fecha. ¿Me la dices otra vez?',
    'bad_time': 'No entendí bien la hora. ¿Me la dices otra vez?',
    'past': 'Esa fecha u hora ya pasó. ¿Cuál quieres?',
    'weekday_mismatch': 'El día que dijiste no coincide con esa fecha. ¿Cuál quieres?',
    'bad_repeat': 'No entendí si es una vez o todos los días. ¿Cuál es?',
    'series_unsupported': 'Puedo guardarlo una sola vez. Las series llegan con la próxima actualización del teléfono.',
    'ambiguous': '¿{hour} de la mañana o de la tarde?',
}


def enabled():
    """Interruptor único. Apagado = comportamiento actual, sin cambios."""
    return os.environ.get(FLAG, '').strip() in {'1', 'true', 'yes'}


def schemas():
    """Esquema con datos para las herramientas que ya los aceptan.

    `text` y `when_text` son obligatorios: son la prueba de origen. `date`,
    `time` y `repeat` son opcionales y solo se usan si el fragmento no se puede
    parsear o si la repetición no está clara en el texto.
    """
    def function(name, description, properties, required):
        return {'type': 'function', 'function': {'name': name, 'description': description,
            'parameters': {'type': 'object', 'properties': properties, 'required': required}}}
    string = lambda description: {'type': 'string', 'description': description}
    return {
        'create_reminder': function('create_reminder',
            'Crea un recordatorio. Copia el tema y el momento con las palabras del usuario; no inventes nada.',
            {'text': string('Qué recordar, fragmento literal del usuario'),
             'when_text': string('Fragmento literal del usuario que dice cuándo'),
             'date': string('AAAA-MM-DD si el fragmento necesita fecha resuelta'),
             'time': string('HH:MM en 24 horas si el fragmento necesita hora resuelta'),
             'repeat': {'type': 'string', 'enum': ['once', 'daily', 'weekly'],
                        'description': 'once por defecto; daily si dijo todos los días'}},
            ['text', 'when_text']),
        'create_alarm': function('create_alarm',
            'Programa una alarma. Copia el momento con las palabras del usuario; no inventes nada.',
            {'text': string('Etiqueta breve, o vacío si no la dijo'),
             'when_text': string('Fragmento literal del usuario que dice cuándo'),
             'date': string('AAAA-MM-DD si el fragmento necesita fecha resuelta'),
             'time': string('HH:MM en 24 horas si el fragmento necesita hora resuelta'),
             'repeat': {'type': 'string', 'enum': ['once', 'daily', 'weekly'],
                        'description': 'once por defecto; weekly si dijo todos los martes'}},
            ['when_text']),
    }


def extend(tools):
    """Cambia solo las herramientas con esquema nuevo; el resto queda igual."""
    replacements = schemas()
    return [replacements.get(item['function']['name'], item) for item in tools]


def _plain(value):
    return normalized(clean(value))


def _quoted(fragment, transcript):
    """True si el fragmento aparece literalmente (sin tildes ni mayúsculas)."""
    if not isinstance(fragment, str) or not fragment.strip():
        return False
    return _plain(fragment) in _plain(transcript)


def _spoken_hour(clock):
    plain = re.sub(r'^(?:a )?(?:las? )?', '', normalized(clock))
    token = plain.split(' ')[0] if plain else ''
    try:
        return number(token)
    except ValueError:
        return None


def _day_at(hour, minute, anchor_ms):
    """Hoy si aún no pasó; mañana si ya pasó. Nunca el pasado."""
    today = datetime.fromtimestamp(anchor_ms / 1000, BOGOTA)
    candidate = today.replace(hour=int(hour), minute=int(minute), second=0, microsecond=0)
    if candidate <= today:
        candidate += timedelta(days=1)
    return int(candidate.timestamp() * 1000)


def _parse_span(kind, span, anchor_ms):
    """Instante y repetición a partir del fragmento literal, sin inventar.

    Devuelve dict con repeat/due_ms y, si aplica, weekday y minute_of_day.
    """
    plain = _plain(span)
    daily = _DAILY.match(plain)
    if daily:
        clock = daily[1]
        if not clock:
            raise AgendaValidationError('bad_time')
        try:
            hour, minute = calendar_clock(clock)
        except AgendaValidationError as error:
            if str(error) == 'ambiguous_time':
                raise _Ambiguous(_spoken_hour(clock)) from None
            raise
        return {'repeat': 'daily', 'due_ms': _day_at(hour, minute, anchor_ms),
                'minute_of_day': hour * 60 + minute}
    if kind == 'ALARM' or _REPEAT_WORDS.search(plain):
        try:
            value = weekly(plain, anchor_ms)
        except AgendaValidationError:
            value = None
        if value is not None:
            return {'repeat': 'weekly', 'due_ms': int(value['due_ms']),
                    'weekday': int(value['weekday']),
                    'minute_of_day': int(value['minute_of_day'])}
    due, delay = calendar_due(span, anchor_ms)
    if delay and delay > 0:
        return {'repeat': 'once', 'due_ms': int(due), 'delay_ms': int(delay)}
    instant = datetime.fromtimestamp(due / 1000, BOGOTA)
    return {'repeat': 'once', 'due_ms': int(due),
            'minute_of_day': instant.hour * 60 + instant.minute}


class _Ambiguous(ValueError):
    def __init__(self, hour):
        super().__init__('ambiguous_time')
        self.hour = hour


def _model_instant(arguments, span, anchor_ms):
    """Fecha y hora del modelo, validadas y coherentes con el fragmento."""
    text_date, text_time = arguments.get('date'), arguments.get('time')
    if not isinstance(text_date, str) or not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', text_date):
        raise AgendaValidationError('bad_date')
    if not isinstance(text_time, str) or not re.fullmatch(r'[0-9]{1,2}:[0-9]{2}', text_time):
        raise AgendaValidationError('bad_time')
    try:
        day = date.fromisoformat(text_date)
        hour, minute = (int(part) for part in text_time.split(':'))
        instant = datetime(day.year, day.month, day.day, hour, minute, tzinfo=BOGOTA)
    except (ValueError, OverflowError):
        raise AgendaValidationError('bad_date') from None
    if not 0 <= hour <= 23 or not 0 <= minute <= 59:
        raise AgendaValidationError('bad_time')
    due = int(instant.timestamp() * 1000)
    if not 0 < due - anchor_ms <= MAX_AHEAD_MS:
        raise AgendaValidationError('past')
    named = _WEEKDAY_IN_TEXT.search(_plain(span))
    if named and WEEKDAYS.index(named[0]) != instant.weekday():
        raise AgendaValidationError('weekday_mismatch')
    return due, instant


def propose(kind, arguments, transcript, anchor_ms, lifecycle=False, series=False):
    """Valida los datos del modelo y devuelve la propuesta o un rechazo hablado.

    `ok=False` nunca ejecuta ni guarda: trae `reason` y `message` para que el
    llamador pregunte lo que falta en vez de rendirse.
    """
    if kind not in ('REMINDER', 'ALARM'):
        raise ValueError('Herramienta sin validación de datos')
    if type(anchor_ms) is not int or anchor_ms <= 0:
        raise ValueError('Reloj inválido')
    if not isinstance(arguments, dict):
        raise ValueError('Datos de herramienta inválidos')
    reject = lambda reason, **extra: dict({'ok': False, 'reason': reason,
        'message': MESSAGES[reason].format(**extra) if '{' in MESSAGES[reason] else MESSAGES[reason]})

    subject = arguments.get('text', '') if isinstance(arguments.get('text'), str) else ''
    span = arguments.get('when_text', '') if isinstance(arguments.get('when_text'), str) else ''
    if kind == 'REMINDER':
        if not subject.strip() or not _quoted(subject, transcript):
            return reject('no_evidence')
    elif subject and not _quoted(subject, transcript):
        return reject('no_evidence')
    if not _quoted(span, transcript):
        return reject('no_evidence')

    wanted = arguments.get('repeat') or None
    if wanted not in (None, 'once', 'daily', 'weekly'):
        return reject('bad_repeat')
    try:
        parsed = _parse_span(kind, span, anchor_ms)
    except _Ambiguous as ambiguous:
        return reject('ambiguous', hour=ambiguous.hour)
    except AgendaValidationError as error:
        reason = str(error)
        if reason in {'past_or_range', 'past'}:
            return reject('past')
        if reason == 'invalid_date' and _SPAN_HAS_DATE.search(_plain(span)):
            return reject('weekday_mismatch' if _WEEKDAY_IN_TEXT.search(_plain(span)) else 'bad_date')
        try:
            due, instant = _model_instant(arguments, span, anchor_ms)
            parsed = {'repeat': wanted or 'once', 'due_ms': due,
                      'minute_of_day': instant.hour * 60 + instant.minute,
                      'source': 'model'}
        except AgendaValidationError as invalid:
            return reject(str(invalid) if str(invalid) in MESSAGES else 'bad_date')
    else:
        parsed['source'] = 'span'

    repeat = parsed.get('repeat', 'once')
    if wanted and wanted != repeat:
        # El fragmento y la repetición pedida se contradicen; se pregunta.
        return reject('bad_repeat')

    if repeat != 'once' and kind == 'REMINDER' and not series:
        return reject('series_unsupported')
    if repeat == 'weekly' and kind == 'ALARM' and not lifecycle:
        return reject('series_unsupported')
    if repeat == 'daily' and kind == 'ALARM' and not lifecycle:
        return reject('series_unsupported')

    value = {'text': subject.strip(), 'when_text': span.strip(), 'due_ms': int(parsed['due_ms']),
             'delay_ms': int(parsed.get('delay_ms', 0)), 'zone': 'America/Bogota',
             'repeat': repeat, 'source': parsed.get('source', 'span'), 'schema': SCHEMA_VERSION}
    if repeat in ('weekly', 'daily'):
        value['weekday'] = int(parsed.get('weekday', 0))
        value['minute_of_day'] = int(parsed['minute_of_day'])
    return {'ok': True, 'value': value}
