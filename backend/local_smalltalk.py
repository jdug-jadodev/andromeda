"""Deterministic local social replies: compliments, affection, achievements, birthdays,
joys and greetings. No model, no tools.

Fixed warm replies so common pleasantries and good news never reach the model, where a
small model can misroute them to a tool (e.g. a compliment becoming a reminder) or reply
flatly. Unrecognized or negated language returns None so ordinary conversation keeps
using the model.
"""
import re
from doja_intents import normalized

_GREETING = re.compile(
    r'^(?:hola|buenos dias|buenas tardes|buenas noches|buenas|que tal|como estas|como te va)$')

_BIRTHDAY = re.compile(r'\b(?:cumpleanos|cumple|hoy cumplo)\b')

# First-person success or milestone the user is sharing.
_ACHIEVEMENT = re.compile(
    r'(?:me (?:gane|ascendieron|gradue|case|sali)|'
    r'\b(?:gane|logre|consegui|aprobe|gradue|case|ascendi)\b|'
    r'lo (?:logre|consegui))')

# Positive mood or good news, without a specific win.
_JOY = re.compile(
    r'(?:estoy|me siento) (?:muy )?(?:feliz|contento|contenta|genial|orgulloso|orgullosa)|'
    r'(?:tengo|traigo) (?:una )?buena noticia')

# Strong affection toward the assistant, handled with a warm but non-romantic reply.
_LOVE = re.compile(r'(?:te quiero|te amo|me gustas)')

# Praise addressed to the assistant in the tú form, so third-person praise of someone
# else ("ella es bonita") never matches.
_COMPLIMENT = re.compile(
    r'(?:te admiro|me caes bien|me encantas|'
    r'eres (?:muy )?(?:bonita|linda|hermosa|preciosa|inteligente|genial|increible|maravillosa|'
    r'divertida|simpatica|amable|tierna|un sol|la mejor)|'
    r'que (?:bonita|linda|hermosa|preciosa|amable|inteligente|genial) eres)')

# A negated phrase is not a pleasantry; leave it to the model instead of a wrong reply.
_NEGATED = re.compile(
    r'\bno\s+(?:eres|te|me|que|soy|es|estoy|tengo|he|fui|'
    r'gane|logre|consegui|aprobe|gradue|case)\b')

_GREETING_REPLY = '¡Hola! ¿En qué puedo ayudarte?'
_BIRTHDAY_REPLY = '¡Feliz cumpleaños! Que la pases increíble.'
_ACHIEVEMENT_REPLY = '¡Felicidades! Me alegra muchísimo por ti, te lo mereces.'
_JOY_REPLY = '¡Qué alegría! Me encanta escuchar eso.'
_LOVE_REPLY = 'Yo también te tengo mucho cariño.'
_COMPLIMENT_REPLY = 'Gracias, qué lindo de tu parte.'


def answer(human):
    if not isinstance(human, str) or not 0 < len(human) <= 250: return None
    value = normalized(human).strip(' .,!¡?¿')
    if not value: return None
    if _NEGATED.search(value): return None
    if _GREETING.match(value): return _GREETING_REPLY
    if _BIRTHDAY.search(value): return _BIRTHDAY_REPLY
    if _ACHIEVEMENT.search(value): return _ACHIEVEMENT_REPLY
    if _JOY.search(value): return _JOY_REPLY
    if _LOVE.search(value): return _LOVE_REPLY
    if _COMPLIMENT.search(value): return _COMPLIMENT_REPLY
    return None
