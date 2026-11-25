"""Countdown to the trip to Huila (11 December 2026). No model, no persistence.

Android plays the scene (HuilaArt) at 10:00, 12:00, 17:00 and 20:00 and asks for the notice;
this only writes the sentence she says. Must match HuilaMode.daysUntilTrip.
"""
import re
from datetime import date, datetime, timedelta, timezone

from doja_intents import normalized

BOGOTA = timezone(timedelta(hours=-5))
TRIP = date(2026, 12, 11)
QUESTION = '¿Cuántos días faltan para el viaje al Huila?'

_TRIP = re.compile(r'\b(?:huila|el viaje|nuestro viaje)\b')
_ASKS = re.compile(r'\b(?:cuantos|cuantas|cuanto|falta|faltan|queda|quedan|cuando)\b')
# Not the countdown: prices, weather, distances, or an order (a reminder, a search...).
_OTHER = re.compile(r'\b(?:cuesta|vale|precio|clima|temperatura|lejos|distancia|horas|kilometros|demora|tarda|'
                    r'recuerdame|recordatorio|alarma|temporizador|pon|ponme|programa|crea|busca)\b')


def days_to_trip(today):
    return (TRIP - today).days


def answer(human, today=None):
    """'¿Cuántos días faltan para el viaje al Huila?' gives the scene result; anything else gives None."""
    if not isinstance(human, str) or not 0 < len(human) <= 250: return None
    value = normalized(human)
    if not _TRIP.search(value) or not _ASKS.search(value) or _OTHER.search(value): return None
    text = notice(today)
    return None if text is None else {'kind': 'santa', 'scene': 'HUILA', 'text': text}


def notice(today=None):
    """The sentence for today, or None once the trip has passed."""
    today = today if today is not None else datetime.now(BOGOTA).date()
    days = days_to_trip(today)
    if days < 0: return None
    if days == 0: return '¡Hoy es el viaje al Huila!'
    if days == 1: return 'Falta un día para el viaje al Huila.'
    return f'Faltan {days} días para el viaje al Huila.'
