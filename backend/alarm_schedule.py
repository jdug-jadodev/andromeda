"""Literal weekly alarm schedules; opt-in after native create_alarm selection."""
from datetime import datetime, timedelta
import re
from agenda_requests import BOGOTA, AgendaValidationError, calendar_due, clean
from doja_intents import normalized

POLICY = 'weekly_alarm_lifecycle_v1'
PROTOCOL = 13
DAYS = 'lunes martes miércoles jueves viernes sábado domingo'.split()
PLAIN_DAYS = [normalized(day) for day in DAYS]
DAY_PATTERN = '|'.join(day+'s?' if day in {'sabado','domingo'} else day for day in PLAIN_DAYS)


def weekly(when, anchor):
    if type(anchor) is not int or anchor<=0: raise AgendaValidationError('invalid_clock')
    plain = normalized(clean(when))
    match = re.fullmatch(r'(?:el |los |cada |todos los )?(' + DAY_PATTERN + r') a (?:las? )?(.+)', plain)
    if match: day_text,clock=match[1],match[2]
    else:
        match=re.fullmatch(r'a (?:las? )?(.+?) (?:el |los |cada |todos los )('+DAY_PATTERN+r')',plain)
        if not match: return None
        clock,day_text=match[1],match[2]
    weekday = PLAIN_DAYS.index(day_text[:-1] if day_text in {'sabados','domingos'} else day_text) + 1
    period = re.search(r'\s*(a\.?\s*m\.?|p\.?\s*m\.?|a eme|pe eme)$', clock)
    if period:
        morning = period[1][0] == 'a'; clock = clock[:period.start()].strip()
        if morning and re.fullmatch(r'(?:12|doce)(?::[0-9]{2})?', clock):
            clock = '00' + clock[clock.index(':'):] if ':' in clock else '00:00'
        else: clock += ' de la manana' if morning else ' de la tarde'
    today = datetime.fromtimestamp(anchor / 1000, BOGOTA).date()
    day = today + timedelta(days=(weekday - today.isoweekday()) % 7)
    reference = day if day > today else day + timedelta(days=7)
    due, _ = calendar_due(f'{reference.isoformat()} a las {clock}', anchor)
    instant = datetime.fromtimestamp(due / 1000, BOGOTA)
    candidate = int(datetime(day.year, day.month, day.day, instant.hour, instant.minute, tzinfo=BOGOTA).timestamp() * 1000)
    if candidate <= anchor: candidate += 7 * 86400000
    return dict(due_ms=candidate, delay_ms=0, weekday=weekday, minute_of_day=instant.hour * 60 + instant.minute)


def daily(when, anchor):
    if type(anchor) is not int or anchor <= 0: raise AgendaValidationError('invalid_clock')
    plain = normalized(clean(when))
    match = re.fullmatch(r'(?:todos los dias|cada dia) a (?:las? )?(.+)', plain)
    if not match: return None
    clock = match[1]
    period = re.search(r'\s*(a\.?\s*m\.?|p\.?\s*m\.?|a eme|pe eme)$', clock)
    if period:
        morning = period[1][0] == 'a'; clock = clock[:period.start()].strip()
        if morning and re.fullmatch(r'(?:12|doce)(?::[0-9]{2})?', clock):
            clock = '00' + clock[clock.index(':'):] if ':' in clock else '00:00'
        else: clock += ' de la manana' if morning else ' de la tarde'
    today = datetime.fromtimestamp(anchor / 1000, BOGOTA).date()
    reference = today + timedelta(days=1)  # Always future: parse the clock without hitting the past guard.
    due, _ = calendar_due(f'{reference.isoformat()} a las {clock}', anchor)
    instant = datetime.fromtimestamp(due / 1000, BOGOTA)
    minute = instant.hour * 60 + instant.minute
    candidate = int(datetime(today.year, today.month, today.day, instant.hour, instant.minute, tzinfo=BOGOTA).timestamp() * 1000)
    if candidate <= anchor: candidate += 86400000
    return dict(due_ms=candidate, delay_ms=0, weekday=0, minute_of_day=minute)


def schedule(kind, when, anchor):
    value = weekly(when, anchor) if kind == 'ALARM' else None
    if value is None: value = daily(when, anchor)
    if value is not None: return value
    due, delay = calendar_due(when, anchor)
    return dict(due_ms=due, delay_ms=delay, weekday=0, minute_of_day=-1)


def label(weekday, minute):
    if weekday == 0:
        if type(minute) is not int or not 0 <= minute < 1440: raise AgendaValidationError('invalid_daily_schedule')
        return f'cada día a las {minute//60:02d}:{minute%60:02d}, hora de Bogotá'
    if type(weekday) is not int or not 1 <= weekday <= 7 or type(minute) is not int or not 0 <= minute < 1440:
        raise AgendaValidationError('invalid_weekly_schedule')
    return f'cada {DAYS[weekday-1]} a las {minute//60:02d}:{minute%60:02d}, hora de Bogotá'


def deletion_target(human):
    match = re.fullmatch(r'(?:elimina|borra|cancela) la alarma (?:del |de los |de cada |de todos los |el |los |cada )?(.+)', normalized(clean(human)))
    if not match: raise AgendaValidationError('explicit_alarm_deletion_required')
    value = weekly(match[1], 1792501200000)  # Only weekday/clock are retained, never this synthetic due.
    if value is None: raise AgendaValidationError('weekly_alarm_target_required')
    return {key: value[key] for key in ('weekday', 'minute_of_day')}
