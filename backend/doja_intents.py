"""Small deterministic Spanish timer grammar. Never runs an LLM or shell."""
import re
import unicodedata
from assistant_config import SETTINGS

ONES = {"cero": 0, "un": 1, "uno": 1, "una": 1, "dos": 2, "tres": 3,
        "cuatro": 4, "cinco": 5, "seis": 6, "siete": 7, "ocho": 8, "nueve": 9,
        "diez": 10, "once": 11, "doce": 12, "trece": 13, "catorce": 14,
        "quince": 15, "dieciseis": 16, "diecisiete": 17, "dieciocho": 18,
        "diecinueve": 19, "veinte": 20, "veintiuno": 21, "veintiun": 21,
        "veintidos": 22, "veintitres": 23, "veinticuatro": 24, "veinticinco": 25,
        "veintiseis": 26, "veintisiete": 27, "veintiocho": 28, "veintinueve": 29}
TENS = {"treinta": 30, "cuarenta": 40, "cincuenta": 50, "sesenta": 60,
        "setenta": 70, "ochenta": 80, "noventa": 90}
FIRST_WORDS = "|".join(sorted(set(ONES) | set(TENS), key=len, reverse=True))
WORDS = FIRST_WORDS + "|y"
NUMBER = rf"(?:\d+|{FIRST_WORDS})(?:\s+(?:{WORDS})){{0,3}}"
DURATION = re.compile(rf"\b({NUMBER})\s+(segundos?|minutos?|horas?)\b")


def normalized(text):
    return " ".join("".join(c for c in unicodedata.normalize("NFD", text.lower())
                            if unicodedata.category(c) != "Mn").split())


def number(text):
    if text.isdigit():
        return int(text)
    if text in ONES:
        return ONES[text]
    if text in TENS:
        return TENS[text]
    parts = text.split()
    if len(parts) == 3 and parts[0] in TENS and parts[1] == "y" and ONES.get(parts[2], 0) in range(1, 10):
        return TENS[parts[0]] + ONES[parts[2]]
    raise ValueError("Numero ambiguo")


def parse_command(text):
    text = normalized(text)
    timer = bool(re.search(r"\b(temporizador(?:es)?|timer(?:s)?|cuenta atras)\b", text))
    if re.search(r"\b(no|nunca|menos|entre|aproximadamente|quizas)\b|-\s*\d", text):
        return {"type": "say", "text": "No haré cambios. Indica una orden y duración exactas."}
    if timer and re.search(r"\b(ayer|dije|dijiste|ejemplo|hipoteticamente)\b", text):
        return {"type": "say", "text": "Para cambiar un temporizador necesito una orden directa, no un ejemplo o relato."}
    if timer and re.search(r"\b(manana|lunes|martes|miercoles|jueves|viernes|sabado|domingo|cuando)\b|\ba las\b", text):
        return {"type": "say", "text": "Este piloto solo cambia cuentas regresivas ahora. Aún no programa fechas ni horarios."}
    if re.search(r"\b(cancela|cancele|cancelar|elimina|elimine|borra|borre)\b", text) and timer:
        cancellation = re.sub(r"^" + re.escape(normalized(SETTINGS["wake_word"])) + r"[, ]+", "", text)
        allowed = {"cancela", "cancele", "cancelar", "elimina", "elimine", "borra", "borre", "el", "los", "un", "todos", "por", "favor",
                   "temporizador", "temporizadores", "timer", "timers"}
        if any(token not in allowed for token in re.findall(r"\w+", cancellation)):
            return {"type": "say", "text": "Aún no selecciono temporizadores por nombre, número o duración. Di cancela el temporizador, o cancela todos los temporizadores."}
        return {"type": "cancel_timer", "all": bool(re.search(r"\btodos\b", text))}
    if re.search(r"\b(silencia|calla)\b|deten (?:la |el )?(?:alarma|sonido)", text):
        return {"type": "stop_alarm"}
    if (timer and re.search(r"\b(lista|listar|muestra|cuantos|que)\b", text)) or "cuanto falta" in text:
        return {"type": "list_timers"}
    if not timer:
        return {"type": "say", "text": "Esta prueba solo gestiona temporizadores. Puedes decir: temporizador de diez segundos."}
    without_wake = re.sub(r"^" + re.escape(normalized(SETTINGS["wake_word"])) + r"[, ]+", "", text)
    if not re.search(r"\b(pon|pone|ponme|poneme|poner|crea|crear|inicia|iniciar|activa|activar|programa|programar)\b", text) and not re.match(r"^(?:un )?(temporizador|timer|cuenta atras)\b", without_wake):
        return {"type": "say", "text": "Para crearlo, di: pon un temporizador y su duración."}
    if re.search(r"\bo\b|\d[.,]\d|\bmedio\b|\bmedia\b", text):
        return {"type": "say", "text": "Indica una sola duración en horas, minutos o segundos enteros."}
    found = list(DURATION.finditer(text))
    seconds = 0
    seen = set()
    try:
        for match in found:
            unit = match[2][0]
            if unit in seen:
                raise ValueError("Unidad repetida")
            seen.add(unit)
            seconds += number(match[1]) * {"s": 1, "m": 60, "h": 3600}[unit]
    except ValueError:
        return {"type": "say", "text": "No entendí la duración. Dila de nuevo de forma sencilla."}
    if not found:
        return {"type": "say", "text": "Necesito una duración de un segundo a veinticuatro horas.", "needs_duration": True}
    if not 1 <= seconds <= 86400:
        return {"type": "say", "text": "Necesito una duración de un segundo a veinticuatro horas."}
    # Do not silently ignore an extra number or unrecognised duration unit.
    remainder = text
    for match in reversed(found):
        remainder = remainder[:match.start()] + " " + remainder[match.end():]
    if re.search(rf"\b(?:\d+|{WORDS})\b", remainder.replace(" y ", " ")):
        # Articles 'un/una' are allowed before the word temporizador.
        remainder = re.sub(r"\b(un|una)\s+(?=temporizador|timer|cuenta)", "", remainder)
        remainder = re.sub(r"\by\b", "", remainder)
        if re.search(rf"\b(?:\d+|{WORDS})\b", remainder):
            return {"type": "say", "text": "La duración es ambigua. Indica un único temporizador."}
    return {"type": "timer", "seconds": seconds}
