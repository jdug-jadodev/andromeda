"""Cliente HTTP tipado de la API local de la TV. Sin shell, sin texto libre."""
import json
import os
import urllib.request
import urllib.error

BASE = os.environ.get('TV_API_BASE', 'http://127.0.0.1:8770')
TOKEN = os.environ.get('TV_API_TOKEN', '')


class TVError(Exception):
    def __init__(self, kind, detail=''):
        super().__init__(kind)
        self.kind = kind      # 'auth' | 'param' | 'tv' | 'network'
        self.detail = detail


def _call(method, path, body=None, timeout=10):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Authorization', 'Bearer ' + TOKEN)
    if data:
        req.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        if e.code == 401:
            raise TVError('auth', 'token')
        if e.code == 400:
            raise TVError('param', e.read().decode())
        raise TVError('tv', e.read().decode())
    except (urllib.error.URLError, TimeoutError) as e:
        raise TVError('network', str(e))


def status():
    return _call('GET', '/status')


def apps():
    return _call('GET', '/apps')


def inputs():
    return _call('GET', '/inputs')


def volume_state():
    return _call('GET', '/volume')


def power_on():
    # El WoL se envía en cuanto la API recibe la petición; la TV tarda 10-30s en
    # despertar. Bloquear el loop del backend esperando la confirmación deja
    # metrics.json viejo (el guard del worker mata llama-server a los 30s, y el
    # Monitor marca 'stale_monitor' a los 5s). Timeout corto: el WoL sale igual y
    # contestamos enseguida; la TV termina de encender sola.
    return _call('POST', '/power/on', {}, timeout=3)


def power_off():
    return _call('POST', '/power/off', {})


def set_volume(level):
    return _call('POST', '/volume', {'level': level})


def bump_volume(delta):
    return _call('POST', '/volume', {'delta': delta})


def set_mute(mute):
    return _call('POST', '/volume', {'mute': bool(mute)})


def set_output(output):
    return _call('POST', '/audio', {'output': output})


def enforce_output():
    return _call('POST', '/audio', {'enforce': True})


def switch_input(input_id):
    return _call('POST', '/input', {'id': input_id})


def open_app(name=None, app_id=None, url=None):
    body = {k: v for k, v in (('name', name), ('id', app_id), ('url', url)) if v}
    return _call('POST', '/app', body)


def scene(name):
    return _call('POST', '/scene', {'name': name})
