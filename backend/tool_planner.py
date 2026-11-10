"""Planificador de herramientas: DeepSeek primero, determinista siempre.

Dos motores intercambiables:

- `deepseek`: el modelo elige herramienta o respuesta usando el historial
  minimo de la conversacion. No ejecuta nada: la casa valida y ejecuta.
- `deterministic`: el router actual, sin cambios de comportamiento.

Regla de oro: si no hay clave, no hay red, no hay saldo, hay timeout o el
modelo falla, la misma frase se resuelve exactamente como hoy. Este modulo no
toca las guardas ni autoriza acciones: solo decide motor y clasifica el fallo.

El cortacircuitos es local al proceso: tras varios fallos seguidos se deja de
llamar a la nube durante unos minutos y se responde con el determinista.
"""
import os
import threading
import time

POLICIES = ('deepseek_first', 'deepseek_fallback', 'deterministic')
DEFAULT_POLICY = 'deepseek_first'

FAILURE_THRESHOLD = 3
OPEN_SECONDS = 300
OPEN_SECONDS_CREDENTIALS = 3600

# Fallos que no se arreglan reintentando en el mismo minuto.
CREDENTIAL_CODES = frozenset({'unauthorized', 'payment_required'})
TRANSIENT_CODES = frozenset({'rate_limited', 'server_error', 'timeout', 'network',
                             'invalid_response', 'no_time', 'exception',
                             'invalid_text', 'tool_rejected', 'unknown_tool'})

_lock = threading.Lock()
_failures = 0
_open_until = 0.0
_last_code = 'ok'
_last_latency_ms = 0
_notice = ''


def policy():
    """Motor pedido por configuracion; por defecto DeepSeek primero."""
    value = os.environ.get('ANDROMEDA_PLANNER', DEFAULT_POLICY).strip()
    return value if value in POLICIES else DEFAULT_POLICY


def breaker_seconds(code):
    return OPEN_SECONDS_CREDENTIALS if code in CREDENTIAL_CODES else OPEN_SECONDS


def available(now=None):
    current = time.monotonic() if now is None else now
    with _lock:
        return _open_until <= current


def status(now=None):
    current = time.monotonic() if now is None else now
    with _lock:
        return {'open': _open_until > current, 'failures': _failures,
                'last_code': _last_code, 'latency_ms': _last_latency_ms,
                'retry_in_ms': max(0, int((_open_until - current) * 1000))}


def success(latency_ms=0):
    global _failures, _open_until, _last_code, _last_latency_ms
    with _lock:
        _failures = 0
        _open_until = 0.0
        _last_code = 'ok'
        _last_latency_ms = max(0, int(latency_ms))


def failure(code, latency_ms=0):
    """Cuenta un fallo; abre el cortacircuitos al llegar al umbral.

    Un fallo de credenciales o saldo abre de inmediato y deja un aviso de una
    sola vez para que la casa pueda decir la verdad sin repetirlo cada turno.
    """
    global _failures, _open_until, _last_code, _last_latency_ms, _notice
    code = code if isinstance(code, str) and code in (CREDENTIAL_CODES | TRANSIENT_CODES) else 'exception'
    with _lock:
        _failures += 1
        _last_code = code
        _last_latency_ms = max(0, int(latency_ms))
        if code == 'payment_required':
            _failures = max(_failures, FAILURE_THRESHOLD)
            _notice = 'La nube no tiene saldo; sigo funcionando con lo de casa.'
        elif code == 'unauthorized':
            _failures = max(_failures, FAILURE_THRESHOLD)
            _notice = 'La clave de la nube no es valida; sigo funcionando con lo de casa.'
        if _failures >= FAILURE_THRESHOLD:
            _open_until = time.monotonic() + breaker_seconds(code)


def take_notice():
    """Aviso pendiente, una sola vez, nunca credenciales ni texto del modelo."""
    global _notice
    with _lock:
        value, _notice = _notice, ''
        return value


def reset():
    """Solo pruebas: deja el planificador como recien arrancado."""
    global _failures, _open_until, _last_code, _last_latency_ms, _notice
    with _lock:
        _failures = 0
        _open_until = 0.0
        _last_code = 'ok'
        _last_latency_ms = 0
        _notice = ''
