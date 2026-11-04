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
_EDGES = ' .,!¡¿?'


def is_cancel(text):
    """True solo para una frase de cancelación completa, no para una que la contenga."""
    if not isinstance(text, str):
        return False
    plain = normalized(text)
    plain = _PREFIX.sub('', plain).strip(_EDGES)
    return plain in PHRASES
