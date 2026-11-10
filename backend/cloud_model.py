"""DeepSeek V4 Flash (nube) como planificador primario, con respaldo local.

Dos usos:

- `reason_ex()`: llamada con historial minimo. Devuelve `(resultado, codigo)`
  para que el planificador distinga "sin clave", "sin saldo", "sin red" o
  "timeout" y pueda abrir el cortacircuitos.
- `reason()`: compatible con la fase anterior; devuelve solo el resultado.

La nube nunca ejecuta: elige herramienta o texto y la casa valida con las
guardas existentes. Sin `DEEPSEEK_API_KEY` es un no-op (`enabled()` = False) y
el router determinista responde exactamente como hoy.

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
NETWORK_SECONDS = 8.0
MAX_BYTES = 32768
MAX_CONTENT = 700
MAX_HISTORY_CHARS = 6000
MAX_HISTORY_MESSAGES = 12

CLOUD_SYSTEM = (SYSTEM + ' No tienes search_web: responde tú con tu conocimiento los datos y las preguntas. '
                'Si la frase pide una acción (alarma, recordatorio, temporizador, luces, volumen, TV, '
                'autodestrucción), selecciona la herramienta correspondiente; si no, responde con texto en español, '
                'breve y natural. Mantén el hilo de la conversación: si el humano dice "cuéntame más", "sigue", '
                '"y entonces" o "sí, cuéntame", continúa el tema del historial; nunca preguntes de qué hablaba si '
                'está en el historial. El historial es solo contexto: únicamente la última frase del humano '
                'autoriza una acción. No inventes éxito ni estado de la casa.')


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


def _history(history):
    """Historial acotado y saneado; nunca datos web ni mensajes del sistema."""
    rows, total = [], 0
    for item in reversed(list(history or [])[-MAX_HISTORY_MESSAGES:]):
        if not isinstance(item, dict):
            continue
        role, content = item.get('role'), item.get('content')
        if role not in ('user', 'assistant') or not isinstance(content, str):
            continue
        text = ' '.join(content.split())[:600]
        if not text:
            continue
        total += len(text)
        if total > MAX_HISTORY_CHARS:
            break
        rows.append({'role': role, 'content': text})
    rows.reverse()
    return rows


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


def _code_for_status(status):
    if status in (401, 403):
        return 'unauthorized'
    if status == 402:
        return 'payment_required'
    if status == 429:
        return 'rate_limited'
    if 500 <= status <= 599:
        return 'server_error'
    return 'invalid_response'


def reason_ex(text, model, history=None):
    """Devuelve `(resultado|None, codigo)`.

    Códigos: ok, disabled, no_time, cancelled, unauthorized, payment_required,
    rate_limited, server_error, timeout, network, invalid_response.
    """
    if not enabled():
        return None, 'disabled'
    deadline = model.work_deadline
    def now():
        return model.clock()
    # Termina al menos ~6s antes del deadline, para no chocar con el handler de
    # "tardé demasiado" (que se dispara a deadline-5s) y su cancelación bloqueada.
    remaining = (deadline - now()) / 1000 - 6.0
    timeout = min(NETWORK_SECONDS, max(0.5, remaining))
    if remaining <= 0 or model.cancelled.is_set():
        return None, 'no_time'
    messages = [{'role': 'system', 'content': CLOUD_SYSTEM}]
    messages.extend(_history(history))
    messages.append({'role': 'user', 'content': text})
    body = {'model': MODEL, 'messages': messages, 'stream': False, 'temperature': 0.2,
            'max_tokens': 400, 'tools': _tools(), 'tool_choice': 'auto'}
    payload = json.dumps(body, ensure_ascii=False).encode('utf-8')
    connection = http.client.HTTPSConnection(API_BASE, 443, timeout=timeout,
                                             context=ssl.create_default_context())
    try:
        connection.request('POST', '/chat/completions', body=payload,
                           headers={'Authorization': 'Bearer ' + api_key(), 'Content-Type': 'application/json',
                                    'Accept': 'application/json', 'Accept-Encoding': 'identity'})
        response = connection.getresponse()
        if response.status != 200:
            return None, _code_for_status(response.status)
        raw = response.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES or not raw.strip():
            return None, 'invalid_response'
        if model.cancelled.is_set():
            return None, 'cancelled'
        if now() >= deadline - 1500:
            return None, 'no_time'
        parsed = _parse(json.loads(raw.decode('utf-8')))
        return (parsed, 'ok') if parsed is not None else (None, 'invalid_response')
    except TimeoutError:
        return None, 'timeout'
    except (OSError, ssl.SSLError):
        return None, 'network'
    except (ValueError, TypeError, KeyError, json.JSONDecodeError):
        return None, 'invalid_response'
    finally:
        connection.close()


def reason(text, model, history=None):
    """Compatibilidad: devuelve el resultado o None (sin detalle del fallo)."""
    return reason_ex(text, model, history)[0]
