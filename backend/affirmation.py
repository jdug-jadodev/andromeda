"""Reconocimiento compartido de sí/no para todos los diálogos.

Sustituye los conjuntos exactos que cada servicio repetía ("no", "no gracias")
y que no reconocían variantes naturales ("no no", "no nada más", "dale",
"de una", "listo"). Un "no" que inicia una petición nueva ("no, quiero X")
NO es un rechazo: sigue siendo conversación normal y cae en el router.
"""
from doja_intents import normalized


def plain(text):
    """Normaliza acentos, mayúsculas, comas y signos para comparar de forma estable."""
    value = normalized(text)
    value = value.replace(',', ' ').replace(';', ' ')
    return ' '.join(value.split()).strip(' .!¡?¿')


NEGATIVE = {
    'no', 'no no', 'no nada', 'no nada mas', 'no gracias', 'no gracias nada mas',
    'nada', 'nada mas', 'ya esta', 'ya', 'asi esta bien', 'asi esta perfecto',
    'eso es todo', 'no necesito nada', 'no necesito nada mas', 'no quiero nada',
    'no quiero nada mas', 'no mas', 'no gracias ya', 'estamos bien', 'tranquilo',
}

AFFIRMATIVE = {
    'si', 'si si', 'si por favor', 'si gracias', 'si quiero', 'si hazlo',
    'si adelante', 'si correcto', 'si quiero hacerlo', 'si quiero un recordatorio',
    'si quiero crear un recordatorio', 'correcto', 'exacto', 'adelante', 'hazlo',
    'de acuerdo', 'confirmo', 'dale', 'de una', 'de una vez', 'listo', 'claro',
    'claro que si', 'vale', 'ok', 'okey', 'por supuesto', 'quiero un recordatorio',
    'quiero crear un recordatorio', 'bueno', 'perfecto',
}


def is_negative(text):
    return plain(text) in NEGATIVE


def is_affirmative(text):
    return plain(text) in AFFIRMATIVE


CONFIRM_ACTIONS = {'ejecuta', 'ejecutala', 'ejecutalo', 'hazlo', 'hazla', 'adelante',
                   'procede', 'orden', 'luces', 'enciende', 'apaga', 'dale', 'confirma', 'confirmo'}
REQUEST_WORDS = {'temporizador', 'alarma', 'recordatorio', 'pon', 'crea', 'programa', 'otra', 'otro'}
NEGATION_WORDS = {'no', 'nunca', 'cancela', 'cancelalo', 'dejalo', 'olvidalo', 'espera', 'detente'}


def is_affirmative_phrase(text):
    """A natural yes that repeats the action: 'si, ejecuta esa orden de luces'."""
    if is_affirmative(text):
        return True
    words = plain(text).split()
    if not words or any(word in NEGATION_WORDS for word in words):
        return False
    if any(word in REQUEST_WORDS for word in words):
        return False
    if words[0] not in {'si', 'claro', 'dale', 'confirmo', 'vale', 'ok', 'correcto', 'exacto'}:
        return False
    if len(words) == 1:
        return True
    return any(word in CONFIRM_ACTIONS for word in words[1:])
