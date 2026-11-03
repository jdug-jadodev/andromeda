"""DeepSeek V4 Flash (nube) como fallback del router determinista. Sin memoria.

Solo se invoca cuando el router determinista no reconoce la frase. Manda
[sistema + frase actual] (nunca historial) y devuelve texto libre o una
herramienta a despachar. Sin `DEEPSEEK_API_KEY` es un no-op (`enabled()` = False)
y el router responde el FALLBACK de siempre.

La API key se lee de la variable de entorno `DEEPSEEK_API_KEY` (el supervisor la
inyecta desde un archivo privado), el modelo de `DEEPSEEK_MODEL` (por defecto
`deepseek-flash`, DeepSeek-V4.1-Flash) y el host de `DEEPSEEK_API_BASE`.
"""
import copy
import http.client
import json
import os
import re
import ssl

from assistant_tools_model import SYSTEM, TOOLS, TV_TOOLS, READ_TOOL, DELETE_REMINDER_TOOL

API_BASE = os.environ.get('DEEPSEEK_API_BASE', 'api.deepseek.com')
# DeepSeek-V4.1-Flash-1017: el alias actual del modelo "Flash". El nombre
# histórico `deepseek-chat` ya no es la vía correcta.
MODEL = os.environ.get('DEEPSEEK_MODEL', 'deepseek-flash')
NETWORK_SECONDS = 20.0
MAX_BYTES = 32768
MAX_CONTENT = 2000

CLOUD_SYSTEM = (SYSTEM + ' No tienes search_web: responde tú con tu conocimiento los datos y las preguntas. '
                'Si la frase pide una acción (alarma, recordatorio, temporizador, luces, volumen, TV, '
                'autodestrucción), selecciona la herramienta correspondiente; si no, responde con texto en español, '
                'breve y natural.')


def api_key():
    return os.environ.get('DEEPSEEK_API_KEY', '').strip()


def enabled():
    return bool(api_key())


def _tools():
    """Catálogo de tools ofrecido a DeepSeek: los mismos del sistema, sin search_web
    (DeepSeek responde con su propio conocimiento). Los parsers deterministas
    re-leen la frase original, así que se omiten los campos `required`."""
    catalog = []
    for item in TOOLS + TV_TOOLS + [READ_TOOL, DELETE_REMINDER_TOOL]:
        if item['function']['name'] == 'search_web':
            continue
        clone = copy.deepcopy(item)
        clone['function']['parameters'].pop('required', None)
        catalog.append(clone)
    return catalog


def _content(value):
    if not isinstance(value, str) or not value.strip():
        return None
    if len(value) > MAX_CONTENT:
        value = value[:MAX_CONTENT]
    if re.search(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', value):
        return None
    return ' '.join(value.split())


def _parse(data):
    if not isinstance(data, dict):
        return None
    choices = data.get('choices')
    if not isinstance(choices, list) or not choices:
        return None
    message = choices[0].get('message')
    if not isinstance(message, dict):
        return None
    calls = message.get('tool_calls')
    if isinstance(calls, list) and calls:
        function = calls[0].get('function') if isinstance(calls[0], dict) else None
        if isinstance(function, dict) and isinstance(function.get('name'), str) and function['name']:
            return {'kind': 'tool', 'name': function['name']}
    text = _content(message.get('content'))
    if text is not None:
        return {'kind': 'text', 'text': text}
    return None


def reason(text, model):
    """Devuelve {'kind':'text',...}, {'kind':'tool',...} o None (sin key/fallo)."""
    if not enabled():
        return None
    deadline = model.work_deadline
    def now():
        return model.clock()
    # Termina al menos ~6s antes del deadline, para no chocar con el handler de
    # "tardé demasiado" (que se dispara a deadline-5s) y su cancelación bloqueada.
    remaining = (deadline - now()) / 1000 - 6.0
    timeout = min(NETWORK_SECONDS, max(0.5, remaining))
    if timeout <= 0 or model.cancelled.is_set():
        return None
    messages = [{'role': 'system', 'content': CLOUD_SYSTEM},
                {'role': 'user', 'content': text}]
    body = {'model': MODEL, 'messages': messages, 'stream': False, 'temperature': 0.2,
            'tools': _tools(), 'tool_choice': 'auto'}
    payload = json.dumps(body, ensure_ascii=False).encode('utf-8')
    connection = http.client.HTTPSConnection(API_BASE, 443, timeout=timeout, context=ssl.create_default_context())
    try:
        connection.request('POST', '/chat/completions', body=payload,
                           headers={'Authorization': 'Bearer ' + api_key(), 'Content-Type': 'application/json',
                                    'Accept': 'application/json', 'Accept-Encoding': 'identity'})
        response = connection.getresponse()
        if response.status != 200:
            return None
        raw = response.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES or not raw.strip():
            return None
        if model.cancelled.is_set() or now() >= deadline - 1500:
            return None
        return _parse(json.loads(raw.decode('utf-8')))
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError):
        return None
    finally:
        connection.close()
