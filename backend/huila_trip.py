"""Countdown to the trip to Huila (11 December 2026). No model, no persistence.

Android plays the scene (HuilaArt) at 10:00, 12:00, 17:00 and 20:00 and asks for the notice;
this only writes the sentence she says. Must match HuilaMode.daysUntilTrip.
"""
from datetime import date, datetime, timedelta, timezone

BOGOTA = timezone(timedelta(hours=-5))
TRIP = date(2026, 12, 11)
QUESTION = '¿Cuántos días faltan para el viaje al Huila?'


def days_to_trip(today):
    return (TRIP - today).days


def notice(today=None):
    """The sentence for today, or None once the trip has passed."""
    today = today if today is not None else datetime.now(BOGOTA).date()
    days = days_to_trip(today)
    if days < 0: return None
    if days == 0: return '¡Hoy es el viaje al Huila!'
    if days == 1: return 'Falta un día para el viaje al Huila.'
    return f'Faltan {days} días para el viaje al Huila.'
