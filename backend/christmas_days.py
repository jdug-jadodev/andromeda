"""Deterministic answer to "how many days until Christmas". No model, no persistence.

The count runs to 24 December in Bogotá and is worked out from today's date on every
question, so it goes down by itself. Android plays the scene; this only finds the
question and writes the sentence she says.
"""
import re
from datetime import date, datetime, timedelta, timezone

from doja_intents import normalized

BOGOTA = timezone(timedelta(hours=-5))

_ASKS = re.compile(r'\b(?:cuantos|cuantas|cuanto|falta|faltan|quedan?|cuando|que dia|ya es|hoy es|es hoy)\b')
_ORDER = re.compile(r'\b(?:recuerdame|recordatorio|alarma|temporizador|pon|ponme|programa|crea|busca)\b')
_ALREADY = re.compile(r'\b(?:ya es|hoy es|es hoy)\b')


def days_to_christmas(today):
    target = date(today.year, 12, 24)
    if today > target: target = date(today.year + 1, 12, 24)
    return (target - today).days


def sentence(days, already):
    if days == 0: return '¡Es hoy! ¡Feliz Navidad!'
    count = 'Falta un día para Navidad.' if days == 1 else f'Faltan {days} días para Navidad.'
    return 'Todavía no. ' + count if already else count


def answer(human, today=None):
    """A question about the days left gives the scene result; anything else gives None."""
    if not isinstance(human, str) or not 0 < len(human) <= 250: return None
    value = normalized(human)
    if not re.search(r'\bnavidad\b', value) or not _ASKS.search(value) or _ORDER.search(value): return None
    today = today if today is not None else datetime.now(BOGOTA).date()
    return {'kind': 'santa', 'text': sentence(days_to_christmas(today), bool(_ALREADY.search(value)))}
