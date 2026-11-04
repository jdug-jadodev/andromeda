"""Frase de cancelación compartida por todos los diálogos.

Una sola lista cerrada para que "cancélalo" signifique lo mismo en volumen,
agenda, alarmas, recordatorios, luces y temporizadores. Sin modelo, sin
backend: solo texto normalizado.
"""
import re

from doja_intents import normalized

# Frases explícitas. No se incluye "no" a secas: en un diálogo de sí/no se
# interpreta aparte, y en una orden puede ser parte de otra frase
# ("no, ponlo a las ocho").
PHRASES = frozenset({
    'cancela',
    'cancelar',
    'cancelalo',
    'cancelala',
    'cancelame',
    'cancela eso',
    'cancela esto',
    'cancela lo',
    'cancela el aviso',
    'cancela el recordatorio',
    'cancela la alarma',
    'cancela el temporizador',
    'cancela el ajuste',
    'cancela el volumen',
    'cancela la orden',
    'dejalo',
    'dejala',
    'dejalo asi',
    'olvidalo',
    'olvidala',
    'olvidalo ya',
    'detente',
    'deten',
    'detente ya',
    'parate',
    'para ya',
    'no sigas',
    'aborta',
})

_PREFIX = re.compile(r'^(?:andromeda[ ,]+)?(?:por favor[ ,]+)?')
_SUFFIX = re.compile(r'\s+(?:ya|por favor)$')
_EDGES = ' .,!¡¿?'


def is_cancel(text):
    """True solo para una frase de cancelación completa, no para una que la contenga."""
    if not isinstance(text, str):
        return False
    plain = normalized(text)
    plain = _PREFIX.sub('', plain).strip(_EDGES)
    if plain in PHRASES:
        return True
    # "cancélalo ya" / "cancelar por favor": quita el énfasis final. Se hace
    # después de comprobar la lista para no romper "para ya".
    plain = _SUFFIX.sub('', plain)
    return plain in PHRASES
