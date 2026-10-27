"""Deterministic local time/date/weather answers. No model, no persistence.

Time and date come from the phone clock in Bogotá (UTC-5, no DST). Weather is a
bounded Open-Meteo fetch with a short in-process cache. Unrecognized or failed
questions return None so ordinary conversation keeps using the model.
"""
import json
import re
import threading
import time
import urllib.request
from datetime import datetime, timezone, timedelta

from doja_intents import normalized

BOGOTA = timezone(timedelta(hours=-5))
WEATHER_URL = ("https://api.open-meteo.com/v1/forecast?latitude=4.711&longitude=-74.0721"
               "&current=temperature_2m,weather_code&temperature_unit=celsius&timeformat=unixtime&timezone=America%2FBogota")
WEATHER_MAX_AGE = 10 * 60  # seconds

_weather_lock = threading.Lock()
_weather = {"at": 0.0, "celsius": None, "code": None}

_MONTHS = ("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
           "septiembre", "octubre", "noviembre", "diciembre")
_WEEKDAYS = ("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")

_TIME = re.compile(r"\b(?:que hora|la hora|hora es|horas son|dame la hora|dime la hora|me das la hora)\b")
_DATE = re.compile(r"\b(?:que fecha|la fecha|que dia es|dame la fecha|dime la fecha|a que fecha)\b")
_WEATHER = re.compile(r"\b(?:clima|temperatura|que tiempo hace|como esta el clima|como esta el tiempo|"
                      r"hace frio|hace calor|va a llover|llovera|esta lloviendo|esta nevando)\b")


def time_text(now):
    hour, minute = now.hour, now.minute
    period = "de la mañana" if hour < 12 else "de la tarde" if hour < 18 else "de la noche"
    h12 = hour % 12 or 12
    if minute == 0: return f"Son las {h12} {period}."
    if minute == 15: return f"Son las {h12} y cuarto {period}."
    if minute == 30: return f"Son las {h12} y media {period}."
    return f"Son las {h12}:{minute:02d} {period}."


def date_text(now):
    return f"Hoy es {_WEEKDAYS[now.weekday()]} {now.day} de {_MONTHS[now.month - 1]} de {now.year}."


def _condition(code):
    if code == 0: return "despejado"
    if code in (1, 2): return "mayormente despejado"
    if code == 3: return "nublado"
    if code in (45, 48): return "con niebla"
    if code in (51, 53, 55, 56, 57): return "con llovizna"
    if code in (61, 63, 65, 66, 67): return "con lluvia"
    if code in (71, 73, 75, 77, 85, 86): return "con nieve"
    if code in (80, 81, 82): return "con chubascos"
    if code in (95, 96, 99): return "con tormenta"
    return ""


def _fetch_weather(monotonic=time.monotonic):
    with _weather_lock:
        if _weather["celsius"] is not None and monotonic() - _weather["at"] < WEATHER_MAX_AGE:
            return _weather["celsius"], _weather["code"]
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        # Keep the fetch short so a slow network never blocks the continuous-voice
        # loop long enough to look like a lost audio stream (>5s).
        with opener.open(WEATHER_URL, timeout=3) as response:
            data = json.loads(response.read().decode("utf-8"))
        celsius = float(data["current"]["temperature_2m"])
        code = int(data["current"]["weather_code"])
        if not -50 <= celsius <= 60: raise ValueError("temperature outside range")
    except Exception:
        return None, None
    with _weather_lock:
        _weather["at"] = monotonic(); _weather["celsius"] = celsius; _weather["code"] = code
    return celsius, code


def weather_text(celsius, code):
    condition = _condition(code)
    if condition: return f"En Bogotá hace {celsius:.0f} grados, {condition}."
    return f"En Bogotá hace {celsius:.0f} grados."


def answer(human, now=None, fetcher=_fetch_weather):
    if not isinstance(human, str) or not 0 < len(human) <= 250: return None
    value = normalized(human)
    now = now if now is not None else datetime.now(BOGOTA)
    if _WEATHER.search(value):
        celsius, code = fetcher()
        if celsius is None: return "No pude consultar el clima ahora. Inténtalo en un momento."
        return weather_text(celsius, code)
    if _TIME.search(value): return time_text(now)
    if _DATE.search(value): return date_text(now)
    return None
