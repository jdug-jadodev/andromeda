"""Read-only Cloudflare search adapter. No Tavily key, history, retries or storage.

The fixed Python subprocess bounds DNS as well as TLS/HTTP. Credentials cross a
private pipe, never argv; neither HTTP errors nor response bodies are logged.
"""
import http.client
import json
import os
from pathlib import Path
import re
import ssl
import subprocess
import sys
import time
from urllib.parse import urlsplit

if __name__ == '__main__': sys.path.insert(0, str(Path(__file__).resolve().parent))
from web_search import bounded, source_url

MAX_BYTES = 65536
NETWORK_SECONDS = 4
ERRORS = frozenset({'configuration_missing', 'configuration_invalid', 'cancelled', 'timeout',
    'connection_failed', 'unauthorized', 'rate_limited', 'http_error', 'invalid_response',
    'empty_response', 'response_too_large', 'service_error', 'invalid_query'})


class InternetSearchError(ValueError):
    def __init__(self, code):
        self.code = code if isinstance(code, str) and code in ERRORS else 'invalid_response'
        super().__init__(self.code)


def configuration(environ):
    url, token = environ.get('WEB_SEARCH_URL', ''), environ.get('WEB_SEARCH_TOKEN', '')
    if not url or not token: raise InternetSearchError('configuration_missing')
    try:
        parts = urlsplit(url)
        host = parts.hostname or ''
        if (parts.scheme != 'https' or not source_url(url) or parts.query or parts.fragment
                or parts.netloc != host or not re.fullmatch(r'[a-zA-Z0-9.-]+', host)
                or any(piece in {'.', '..'} for piece in parts.path.split('/'))
                or '%' in parts.path or parts.path.rstrip('/').endswith('/preguntar')
                or not re.fullmatch(r'[A-Za-z0-9._~+/=-]{1,4096}', token)):
            raise ValueError()
    except (ValueError, TypeError): raise InternetSearchError('configuration_invalid') from None
    return host, parts.path.rstrip('/') + '/preguntar', token


def unique(pairs):
    value = {}
    for key, item in pairs:
        if key in value: raise InternetSearchError('invalid_response')
        value[key] = item
    return value


def text(value, limit):
    if not isinstance(value, str) or len(value) > limit: raise InternetSearchError('invalid_response')
    # Reject chat-template delimiters/control characters, not interpret markup.
    if '<|' in value or re.search(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', value):
        raise InternetSearchError('invalid_response')
    return ' '.join(value.split())


def normalize(value, pregunta):
    if not isinstance(value, dict): raise InternetSearchError('invalid_response')
    estado = value.get('estado')
    if not isinstance(estado, str): raise InternetSearchError('invalid_response')
    if estado.casefold() not in {'ok', 'success', 'exito', 'éxito', 'completado'}:
        raise InternetSearchError('service_error')
    # The echoed question is optional, but if supplied it must be the actual one.
    if 'pregunta' in value and value['pregunta'] != pregunta: raise InternetSearchError('invalid_response')
    respuesta = text(value.get('respuesta', ''), 12000)
    contexto = text(value.get('contexto_para_ia', ''), 16000)
    if not respuesta and not contexto: raise InternetSearchError('empty_response')
    fuentes = value.get('fuentes', [])
    if not isinstance(fuentes, list) or len(fuentes) > 50: raise InternetSearchError('invalid_response')
    rows, seen = [], set()
    for item in fuentes:
        if isinstance(item, str): item = {'url': item}
        if not isinstance(item, dict): continue
        url = item.get('url', '')
        try: valid = source_url(url) if isinstance(url, str) else None
        except ValueError: valid = None
        if not valid or url in seen: continue
        title = text(item.get('title', item.get('titulo', urlsplit(url).hostname)), 2000)
        excerpt = text(item.get('excerpt', item.get('contenido', item.get('content', item.get('extracto', '')))), 16000)
        rows.append({'url': url, 'title': bounded(title, 120), 'excerpt': bounded(excerpt, 350)})
        seen.add(url)
        if len(rows) == 3: break
    return {'respuesta': bounded(respuesta, 1000), 'contexto_para_ia': bounded(contexto, 1600), 'fuentes': rows}


def _http(host, path, token, pregunta):
    connection = http.client.HTTPSConnection(host, timeout=NETWORK_SECONDS, context=ssl.create_default_context())
    try:
        body = json.dumps({'pregunta': pregunta, 'tipo': 'general'}, ensure_ascii=False).encode('utf-8')
        connection.request('POST', path, body=body, headers={'Authorization': 'Bearer ' + token,
            'Content-Type': 'application/json', 'Accept': 'application/json', 'Accept-Encoding': 'identity'})
        response = connection.getresponse()
        if response.status in {401, 403}: raise InternetSearchError('unauthorized')
        if response.status == 429: raise InternetSearchError('rate_limited')
        if response.status != 200: raise InternetSearchError('http_error')  # No redirects or provider fallback.
        if response.getheader('Content-Type', '').split(';')[0].strip().lower() != 'application/json':
            raise InternetSearchError('invalid_response')
        if response.getheader('Content-Encoding', 'identity') not in {'identity', ''}:
            raise InternetSearchError('invalid_response')
        raw = response.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES: raise InternetSearchError('response_too_large')
        if not raw.strip(): raise InternetSearchError('empty_response')
        return normalize(json.loads(raw.decode('utf-8'), object_pairs_hook=unique), pregunta)
    finally:
        connection.close()


def buscar_en_internet(pregunta, deadline, cancelled=None, *, environ=None):
    """Only the caller-authorized, standalone question; deadline is monotonic seconds.

    Returns bounded respuesta/contexto_para_ia/fuentes; never fetches source URLs.
    Configuration is read at call time, so importing never opens a connection.
    """
    if not isinstance(pregunta, str) or not pregunta.strip() or len(pregunta) > 160:
        raise InternetSearchError('invalid_query')
    if re.search(r'[\x00-\x1f\x7f]|<\|', pregunta): raise InternetSearchError('invalid_query')
    if cancelled is not None and cancelled.is_set(): raise InternetSearchError('cancelled')
    end = min(deadline, time.monotonic() + NETWORK_SECONDS)
    if end - time.monotonic() < .25: raise InternetSearchError('timeout')
    host, path, token = configuration(os.environ if environ is None else environ)
    # No shell, proxy environment, token in process args, or inherited app secrets.
    child_env = {key: os.environ[key] for key in ('SYSTEMROOT', 'WINDIR') if key in os.environ}
    request = json.dumps({'host': host, 'path': path, 'token': token, 'pregunta': pregunta}).encode('utf-8')
    process = None
    try:
        process = subprocess.Popen([sys.executable, '-I', '-B', '-X', 'utf8', str(Path(__file__).resolve()), '--worker'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=child_env)
        pending = request
        while True:
            if cancelled is not None and cancelled.is_set(): raise InternetSearchError('cancelled')
            remaining = end - time.monotonic()
            if remaining <= 0: raise InternetSearchError('timeout')
            try:
                output, _ = process.communicate(input=pending, timeout=min(.05, remaining))
                break
            except subprocess.TimeoutExpired:
                pending = None
        if cancelled is not None and cancelled.is_set(): raise InternetSearchError('cancelled')
        if time.monotonic() >= end: raise InternetSearchError('timeout')
        if process.returncode != 0 or len(output) > MAX_BYTES: raise InternetSearchError('connection_failed')
        value = json.loads(output.decode('utf-8'), object_pairs_hook=unique)
        if not isinstance(value, dict): raise InternetSearchError('invalid_response')
        if 'error' in value: raise InternetSearchError(value['error'])
        # Verify pipe shape without persisting or exporting the raw response.
        return normalize(dict(value, estado='ok'), pregunta)
    except InternetSearchError:
        raise
    except (OSError, ValueError, TypeError, RecursionError):
        raise InternetSearchError('connection_failed') from None
    finally:
        if process is not None:
            if process.poll() is None: process.kill()
            process.communicate()  # Reap the owned child, including blocked DNS workers.


def _worker():
    try:
        row = json.loads(sys.stdin.buffer.read(16384).decode('utf-8'))
        value = _http(row['host'], row['path'], row['token'], row['pregunta'])
    except InternetSearchError as error: value = {'error': error.code}
    except TimeoutError: value = {'error': 'timeout'}
    except (OSError, http.client.HTTPException): value = {'error': 'connection_failed'}
    except Exception: value = {'error': 'invalid_response'}
    sys.stdout.buffer.write(json.dumps(value, ensure_ascii=True).encode('ascii'))


if __name__ == '__main__':
    if sys.argv[1:] != ['--worker']: raise SystemExit(2)
    _worker()
