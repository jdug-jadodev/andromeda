"""Cliente HTTP tipado de la API local de la TV. Sin shell, sin texto libre."""
import json
import os
import re
import time
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
    except TVError:
        raise
    except Exception as e:
        # RemoteDisconnected, timeouts, JSON roto: nunca debe tumbar al residente.
        raise TVError('network', str(e))


def status(timeout=10):
    return _call('GET', '/status', timeout=timeout)


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
    # Igual que power_on: timeout corto. Si la TV tarda en responder, bloquear el
    # loop del backend más de 5 s dispara 'stale_monitor' y mata al residente. La
    # orden sale igual; si la TV no responde contestamos error sin caernos.
    return _call('POST', '/power/off', {}, timeout=3)


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


def toast(message, timeout=5):
    """Notificación en pantalla (toast LG WebOS)."""
    return _call('POST', '/toast', {'message': message}, timeout=timeout)


def toast_if_on(message, timeout=2):
    """Muestra un aviso en la TV solo si está encendida. Nunca lanza: devuelve
    False si la TV está apagada, sin red o el puente no responde, para poder
    llamarla en un turno programado sin tumbar al residente."""
    try:
        if not status(timeout=timeout).get('reachable'):
            return False
        toast(message, timeout=timeout)
        return True
    except TVError:
        return False


def announce(message, timeout=4):
    """Muestra el aviso completo (animación) en la TV abriendo el navegador a la
    página servida por el teléfono, y vuelve a la app/entrada anterior al acabar.
    Nunca lanza; el sleep total ronda los 20 s, así que debe llamarse en un hilo."""
    try:
        st = status(timeout=timeout)
        if not st.get('reachable'):
            return False
        previous = st.get('app')
        host = st.get('host') or '192.168.1.86'
        url = 'http://%s:8770/huila' % host
        # Abre la animación en el navegador de la TV (a pantalla completa).
        open_app(app_id='browser', url=url)
        # La animación dura ~16 s; espera a que termine y vuelve a lo anterior.
        time.sleep(19.0)
        _restore(previous)
        time.sleep(1.0)
        return True
    except TVError:
        return False


def _restore(app_id):
    """Devuelve la TV a la entrada/app anterior. Una entrada HDMI se restaura con
    switchInput (no como app), y el resto de apps se relanza."""
    m = re.match(r'com\.webos\.app\.hdmi(\d)', app_id or '')
    if m:
        switch_input('HDMI_' + m.group(1))
    elif app_id in ('com.webos.app.livetv', 'com.webos.app.liveplayback'):
        switch_input('TV')
    elif app_id:
        open_app(app_id=app_id)
