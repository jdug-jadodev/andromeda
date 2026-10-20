"""Post-native-call literal validation. No executor, persistence, network or routing."""
from datetime import datetime, timedelta, timezone
import re

from doja_intents import normalized, number, ONES, TENS
from timer_proposals import literal_seconds

BOGOTA = timezone(timedelta(hours=-5))
MAX_AHEAD_MS = 366 * 86400000
MONTHS = 'enero febrero marzo abril mayo junio julio agosto septiembre octubre noviembre diciembre'.split()


class AgendaValidationError(ValueError):
    pass


def notation(text):
    """Only word/digit notation, never convert units, dates or remove modifiers."""
    tokens = normalized(text).split(); result = []; index = 0
    while index < len(tokens):
        if index + 2 < len(tokens) and tokens[index] in TENS and tokens[index + 1] == 'y' and 1 <= ONES.get(tokens[index + 2], 0) <= 9:
            result.append(str(number(' '.join(tokens[index:index + 3])))); index += 3
        elif tokens[index] in ONES or tokens[index] in TENS:
            result.append(str(number(tokens[index]))); index += 1
        elif re.fullmatch('[0-9]+', tokens[index]): result.append(str(int(tokens[index]))); index += 1
        else: result.append(tokens[index]); index += 1
    return ' '.join(result)


def clean(text):
    if (not isinstance(text, str) or not 0 < len(text) <= 250 or '<|' in text
            or re.search(r'["\'«»<>\x00-\x1f]', text)):
        raise AgendaValidationError('invalid_request')
    if any(char.isdecimal() and char not in '0123456789' for char in text): raise AgendaValidationError('invalid_request')
    value = ' '.join(text.split()).strip(' .,!¡?¿')
    value = re.sub(r'^andr[oó]meda[, ]+', '', value, flags=re.I)
    return re.sub(r'^por favor ', '', value, flags=re.I)


def calendar_due(when, anchor_ms):
    """Explicit date + unambiguous time, or literal elapsed duration. Never roll past dates forward."""
    if type(anchor_ms) is not int or anchor_ms <= 0: raise AgendaValidationError('invalid_clock')
    plain = normalized(when)
    if plain.startswith('en '):
        try: seconds = literal_seconds('pon un temporizador de ' + plain[3:])
        except ValueError: raise AgendaValidationError('invalid_time') from None
        return anchor_ms + seconds * 1000, seconds * 1000
    match = re.fullmatch(r'(.+?) a (?:las? )?(.+)', plain)
    if not match: raise AgendaValidationError('missing_date_time')
    date, clock = match.groups(); today = datetime.fromtimestamp(anchor_ms / 1000, BOGOTA).date()
    if date in {'hoy', 'manana', 'pasado manana'}:
        day = today + timedelta(days={'hoy': 0, 'manana': 1, 'pasado manana': 2}[date])
    else:
        date = re.sub(r'^el ', '', date)
        try:
            if re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', date): day = datetime.strptime(date, '%Y-%m-%d').date()
            elif re.fullmatch(r'[0-9]{2}/[0-9]{2}/[0-9]{4}', date): day = datetime.strptime(date, '%d/%m/%Y').date()
            else:
                parts = re.fullmatch(r'(.+?) de (' + '|'.join(MONTHS) + r') de ([0-9]{4})', date)
                if not parts: raise ValueError()
                day = datetime(int(parts[3]), MONTHS.index(parts[2]) + 1, number(parts[1])).date()
        except (ValueError, OverflowError): raise AgendaValidationError('invalid_date') from None
    if clock in {'medianoche', 'mediodia'}: hour, minute = (0 if clock == 'medianoche' else 12), 0
    else:
        suffix = re.search(r' (de la manana|de la tarde|de la noche|del mediodia)$', clock)
        period = suffix[1] if suffix else ''
        clock = clock[:suffix.start()] if suffix else clock
        digital = re.fullmatch(r'([0-9]{1,2}):([0-9]{2})', clock)
        try:
            if digital: hour, minute = int(digital[1]), int(digital[2])
            else:
                parts = clock.split(' y ', 1)
                hour = number(parts[0]); minute = 0
                if len(parts) == 2: minute = {'media': 30, 'cuarto': 15}.get(parts[1], -1)
                if minute == -1: minute = number(parts[1])
            if period:
                if not 1 <= hour <= 12: raise ValueError()
                if period == 'de la manana' and hour == 12: raise ValueError()
                if period == 'del mediodia' and hour != 12: raise ValueError()
                if period == 'de la noche' and hour == 12: hour = 0
                elif period in {'de la tarde', 'de la noche'} and hour < 12: hour += 12
            elif not digital and hour < 13: raise AgendaValidationError('ambiguous_time')
            if not 0 <= hour <= 23 or not 0 <= minute <= 59: raise ValueError()
        except (ValueError, OverflowError) as error:
            if isinstance(error, AgendaValidationError): raise
            raise AgendaValidationError('invalid_time') from None
    due = int(datetime(day.year, day.month, day.day, hour, minute, tzinfo=BOGOTA).timestamp() * 1000)
    if not 0 < due - anchor_ms <= MAX_AHEAD_MS: raise AgendaValidationError('past_or_range')
    return due, 0


def parts(kind, raw):
    candidates = []
    if kind == 'ALARM':
        match = re.fullmatch(r'(?:(?:pon|ponme|crea|programa) (?:una |la )?alarma|alarma) (?:para )?(.+)', raw, re.I)
        if match: candidates.append((match[1], ''))
    else:
        prefix = re.fullmatch(r'(?:recu[eé]rdame|(?:pon|ponme|crea|programa) (?:un |el )?recordatorio) (.+)', raw, re.I)
        if prefix:
            tail = prefix[1]
            front = re.fullmatch(r'(.+?) (?:que|para) (.+)', tail, re.I)
            if front: candidates.append((re.sub(r'^para ', '', front[1], flags=re.I), front[2]))
            for start in re.finditer(r' (?=en |hoy |ma[ñn]ana |pasado ma[ñn]ana |el [0-9]|[0-9]{4}-|[0-9]{2}/)', tail, re.I):
                candidates.append((tail[start.end():], tail[:start.start()]))
    return candidates


def request(kind, human, when_text, reminder_text, anchor_ms, alarm_lifecycle=False):
    """Validate the WHOLE current request and arguments; retain only extracted human payload."""
    raw = clean(human)
    if kind not in {'ALARM', 'REMINDER'} or not isinstance(when_text, str) or not isinstance(reminder_text, str):
        raise AgendaValidationError('invalid_call')
    if re.search(r'\bno (?:lo hagas|la pongas|lo pongas|la crees|lo crees|programes)\b', normalized(raw)):
        raise AgendaValidationError('invalid_request')
    candidates = parts(kind, raw)
    matches = []
    for when, body in candidates:
        if notation(when) != notation(when_text) or normalized(body) != normalized(reminder_text): continue
        if kind == 'REMINDER' and (not 0 < len(body) <= 160 or re.search(
                r'[;.!?]|(?:\b(?:y|ademas|tambien|despues|luego)\b|,)\s+(?:andromeda[, ]+)?(?:por favor )?'
                r'(?:pon|ponme|pone|crea|programa|inicia|recuerdame|busca|investiga|consulta|dime|cuenta|cancela|no)\b'
                r'|\ben .+ (?:segundos?|minutos?|horas?)\b', normalized(body))):
            continue
        if alarm_lifecycle:
            from alarm_schedule import schedule
            time = schedule(kind, when, anchor_ms)
        else:
            due, delay = calendar_due(when, anchor_ms); time = dict(due_ms=due, delay_ms=delay)
        matches.append(dict(event_kind=kind, text=body, zone='America/Bogota', **time))
    if len(matches) != 1: raise AgendaValidationError('invalid_request')
    return matches[0]


def current_event(kind, human, anchor_ms, alarm_lifecycle=False):
    """Validate literal data only AFTER the model selects this exact calendar kind.

    Native calendar tools have no data arguments: neither the model's history
    nor rewritten dates/content can supply authorization or event fields.
    """
    if kind not in {'ALARM', 'REMINDER'}: raise AgendaValidationError('invalid_call')
    matches = []
    for when, body in parts(kind, clean(human)):
        try: value = request(kind, human, when, body, anchor_ms, alarm_lifecycle)
        except (ValueError, OverflowError): continue
        matches.append(dict(value, when_text=when))
    if len(matches) != 1: raise AgendaValidationError('invalid_request')
    return matches[0]


def selected_event(human, proposed_request, anchor_ms):
    """Historical full-request-echo guard, retained for frozen controls/regressions.

    Current zero-argument calendar calls use current_event AFTER native selection.
    This helper never repairs a differing proposed request.
    """
    def header(value):
        value = normalized(clean(value))
        # Only optional positive command/article headers. Never strip a negation,
        # reported prefix, time modifier, note content or a second request.
        value = re.sub(r'^(?:(?:pon|ponme|crea|programa) (?:una |la )?alarma|alarma) ', 'alarma ', value)
        return re.sub(r'^(?:recuerdame|(?:pon|ponme|crea|programa) (?:un |el )?recordatorio|recordatorio) ', 'recordatorio ', value)
    if header(human) != header(proposed_request):
        raise AgendaValidationError('request_mismatch')
    matches = []
    for kind in ('ALARM', 'REMINDER'):
        try: matches.append(current_event(kind, human, anchor_ms))
        except (ValueError, OverflowError): continue
    if len(matches) != 1: raise AgendaValidationError('invalid_request')
    return matches[0]
