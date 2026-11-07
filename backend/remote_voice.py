"""Politica de ordenes remotas de la app acompanante.

Modulo puro: no abre red, microfono, herramientas ni sesiones. Solo valida el
contrato que el gateway y VoiceService usaran cuando se active la fase remota.

La cadena `POLICY` y los limites deben coincidir con `contract/home-api-v1/`.
El test `tests/test_remote_voice.py` lo comprueba.
"""
import json
import re

POLICY = 'remote_voice_v1'
FRAME = 6
ORIGIN_LOCAL = 'local'
ORIGIN_REMOTE = 'remote'

MAX_TEXT = 500
MAX_QUEUE = 2
WAIT_MS = 40000
REPLY_CACHE_MS = 300000
WAV_MAX_MS = 30000

ID_RE = re.compile(r'[A-Za-z0-9_-]{8,64}\Z')
MAX_TIMESTAMP = 9_999_999_999_999

ALLOWED_TOOLS = frozenset({
    'create_timer',
    'create_alarm',
    'create_reminder',
    'search_web',
    'set_phone_volume',
    'control_lights',
    'list_reminders',
    'delete_reminder',
})


class RemoteCommandFault(ValueError):
    """Orden remota que no cumple el contrato."""


def _identifier(value):
    if not isinstance(value, str) or ID_RE.fullmatch(value) is None:
        raise RemoteCommandFault('Identificador remoto invalido')
    return value


def _text(value):
    if not isinstance(value, str):
        raise RemoteCommandFault('Texto remoto invalido')
    text = value.strip()
    if not 1 <= len(text) <= MAX_TEXT:
        raise RemoteCommandFault('Longitud remota invalida')
    if any(ord(char) < 32 or ord(char) == 127 for char in text):
        raise RemoteCommandFault('Caracteres de control remotos')
    return text


def _timestamp(value):
    if type(value) is not int or not 0 < value <= MAX_TIMESTAMP:
        raise RemoteCommandFault('Marca de tiempo remota invalida')
    return value


def command(value):
    """Valida una orden `{id,text,client_ts_ms,want_wav}` y la normaliza."""
    if not isinstance(value, dict):
        raise RemoteCommandFault('Orden remota invalida')
    extra = set(value) - {'id', 'text', 'client_ts_ms', 'want_wav'}
    if extra:
        raise RemoteCommandFault('Campo remoto desconocido')
    want_wav = value.get('want_wav', True)
    if type(want_wav) is not bool:
        raise RemoteCommandFault('Preferencia de audio remota invalida')
    return {
        'id': _identifier(value.get('id')),
        'text': _text(value.get('text')),
        'client_ts_ms': _timestamp(value.get('client_ts_ms')),
        'want_wav': want_wav,
        'origin': ORIGIN_REMOTE,
    }


def frame(value):
    """Valida el frame 6 que viaja dentro de la sesion del residente."""
    if not isinstance(value, dict):
        raise RemoteCommandFault('Frame remoto invalido')
    if value.get('action') != 'remote_command':
        raise RemoteCommandFault('Accion remota invalida')
    extra = set(value) - {'action', 'id', 'text', 'client_ts_ms', 'want_wav'}
    if extra:
        raise RemoteCommandFault('Campo de frame remoto desconocido')
    return command({key: item for key, item in value.items() if key != 'action'})


def may_confirm(proposal_origin, confirmation_origin):
    """Un 'si' remoto no confirma una propuesta local, ni al reves."""
    return (proposal_origin in {ORIGIN_LOCAL, ORIGIN_REMOTE}
            and confirmation_origin == proposal_origin)


def tool_allowed(name):
    return name in ALLOWED_TOOLS


def deadline(now):
    if type(now) is not int or now <= 0:
        raise RemoteCommandFault('Reloj remoto invalido')
    return now + WAIT_MS


def encode(value):
    """Serializa una orden ya validada para el frame 6."""
    parsed = command(value)
    return json.dumps({
        'action': 'remote_command',
        'id': parsed['id'],
        'text': parsed['text'],
        'client_ts_ms': parsed['client_ts_ms'],
        'want_wav': parsed['want_wav'],
    }, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
