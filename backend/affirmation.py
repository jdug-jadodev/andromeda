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
