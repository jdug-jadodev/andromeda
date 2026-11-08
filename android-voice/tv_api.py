#!/usr/bin/env python3
"""API HTTP local para controlar la LG UR7300PSA rooteada.

Uso:
  python tools/tv_api.py pair     # empareja con permisos de control (una sola vez)
  python tools/tv_api.py serve    # levanta la API en 127.0.0.1:8770

Endpoints:
  GET  /                 indice de endpoints
  GET  /openapi.json     especificacion (para que un agente/IA sepa que puede llamar)
  GET  /status           encendida/apagada, app en pantalla, volumen
  GET  /apps             apps instaladas
  POST /power/on         enciende (Wake-on-LAN)
  POST /power/off        apaga (standby)
  POST /volume           {"level": 0-100} o {"delta": -5}
  POST /app              {"id": "com.webos.app.browser"} o {"url": "https://..."}
  POST /toast            {"message": "hola"} -> notificacion en la TV
  POST /scene            {"name": "cine"|"noche"|"manana"}

Variables de entorno:
  TV_IP (def. 192.168.1.10), TV_MAC (def. F8:01:B4:11:D6:32)
  TV_API_HOST (def. 127.0.0.1), TV_API_PORT (def. 8770)
  TV_API_TOKEN (opcional; si existe hay que mandar "Authorization: Bearer <token>")
"""

import json
import os
import re
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

try:
    import slopbro  # noqa: E402
except ImportError:
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "external" / "slopbro"))
    import slopbro  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass

TV_IP = os.environ.get("TV_IP", "192.168.1.9")
TV_MAC = os.environ.get("TV_MAC", "8C:83:94:0D:F9:B4")
API_HOST = os.environ.get("TV_API_HOST", "0.0.0.0")
API_PORT = int(os.environ.get("TV_API_PORT", "8770"))
TOKEN_FILE = Path(__file__).resolve().parent / "api_token.txt"


def _load_token():
    token = os.environ.get("TV_API_TOKEN", "")
    if token:
        return token
    if API_HOST not in ("127.0.0.1", "localhost"):
        # escucha en red: token obligatorio, se genera una vez
        if TOKEN_FILE.exists():
            return TOKEN_FILE.read_text().strip()
        import secrets

        token = secrets.token_urlsafe(24)
        TOKEN_FILE.write_text(token)
        return token
    return ""


API_TOKEN = _load_token()
KEY_FILE = Path(__file__).resolve().parent / "tvapi.key"

# Salida de audio deseada: por defecto la barra por optica (LG Sound Sync).
# TV_ENFORCE_OUTPUT=0 desactiva la correccion automatica.
DEFAULT_OUTPUT = os.environ.get("TV_DEFAULT_OUTPUT", "external_optical")
ENFORCE_OUTPUT = os.environ.get("TV_ENFORCE_OUTPUT", "1") != "0"
# Cada cuantos segundos se comprueba que siga en la barra (0 = desactivado)
WATCHDOG_SECONDS = int(os.environ.get("TV_WATCHDOG_SECONDS", "0"))

PERMISSIONS = [
    "LAUNCH",
    "CLOSE",
    "READ_APP_STATUS",
    "READ_INSTALLED_APPS",
    "READ_RUNNING_APPS",
    "READ_TV_INFO",
    "READ_UPDATE_INFO",
    "CONTROL_POWER",
    "CONTROL_AUDIO",
    "CONTROL_INPUT_TV",
    "CONTROL_INPUT_JOYSTICK",
    "CONTROL_MOUSE_AND_KEYBOARD",
    "CONTROL_TV_SCREEN",
]

MANIFEST = dict(slopbro.DEFAULT_MANIFEST)
MANIFEST["appId"] = "tv.local.api"
MANIFEST["localizedAppNames"] = {"": "TV Local API"}
MANIFEST["permissions"] = PERMISSIONS


# --------------------------------------------------------------------------
# Wake-on-LAN
# --------------------------------------------------------------------------
def wake_on_lan(mac=TV_MAC, broadcast="255.255.255.255", port=9):
    mac_clean = mac.replace(":", "").replace("-", "").replace(".", "")
    if len(mac_clean) != 12:
        raise ValueError("MAC invalida: %s" % mac)
    payload = b"\xff" * 6 + bytes.fromhex(mac_clean) * 16

    targets = [(broadcast, port), ("192.168.1.255", port), (TV_IP, port)]
    sent = []
    for host, prt in targets:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                s.sendto(payload, (host, prt))
            sent.append(host)
        except OSError:
            pass
    return sent


# --------------------------------------------------------------------------
# Cliente SSAP con reconexion
# --------------------------------------------------------------------------
class TV:
    def __init__(self, ip=TV_IP):
        self.ip = ip
        self.client = None
        self.lock = threading.Lock()

    def _key(self):
        try:
            return KEY_FILE.read_text().strip()
        except OSError:
            return ""

    def connect(self, interactive=False):
        ws = slopbro.WebSocket.connect(self.ip, slopbro.PORT_TLS, secure=True, timeout=10)
        client = slopbro.SSAPClient(ws)
        new_key = client.register(self._key(), MANIFEST, timeout=60 if interactive else 20)
        if new_key:
            KEY_FILE.write_text(new_key)
        self.client = client
        return client

    def request(self, uri, payload=None, timeout=10.0):
        with self.lock:
            if self.client is None:
                self.connect()
            try:
                return self.client.request(uri, payload or {}, timeout=timeout)
            except (OSError, slopbro.WebSocketError, slopbro.SSAPError):
                self.client = None
                self.connect()
                return self.client.request(uri, payload or {}, timeout=timeout)

    def close(self):
        with self.lock:
            if self.client is not None:
                self.client.close()
                self.client = None


tv = TV()


def is_reachable(timeout=1.5):
    try:
        with socket.create_connection((TV_IP, slopbro.PORT_TLS), timeout=timeout):
            return True
    except OSError:
        return False


def is_awake(timeout=6.0):
    """True solo si la TV responde de verdad por SSAP (no vale solo el puerto:
    en standby el stack de red sigue aceptando TCP)."""
    try:
        with socket.create_connection((TV_IP, slopbro.PORT_TLS), timeout=timeout):
            pass
    except OSError:
        return False
    try:
        tv.request("ssap://com.webos.applicationManager/getForegroundAppInfo", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def wait_until_awake(timeout_s=45):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if is_awake(timeout=5):
            return True
        time.sleep(2)
    return False


def normalize_output(value):
    raw = str(value or "").strip().lower().replace(" ", "").replace("_", "")
    if raw in ("barra", "soundbar", "optica", "optical", "externo", "externooptical", "externaloptical", "lgss", "lgsoundsync", "soundsync"):
        return "external_optical"
    if raw in ("tv", "speaker", "altavoz", "internos", "tvspeaker"):
        return "tv_speaker"
    if raw in ("bluetooth", "bt", "externobluetooth", "externalbluetooth"):
        return "external_bluetooth"
    return value


def status():
    info = {"reachable": False, "ip": TV_IP, "mac": TV_MAC}
    if not is_reachable():
        info["power"] = "off/standby"
        return info
    try:
        fg = tv.request("ssap://com.webos.applicationManager/getForegroundAppInfo", timeout=6)
        info["app"] = (fg.get("payload") or {}).get("appId")
        info["reachable"] = True
    except Exception:  # noqa: BLE001
        info["power"] = "standby (puerto abierto, SSAP no responde)"
        return info
    try:
        vol = tv.request("ssap://audio/getVolume")
        payload = vol.get("payload") or {}
        nested = payload.get("volumeStatus") or {}
        info["volume"] = nested.get("volume", payload.get("volume"))
        info["muted"] = nested.get("muteStatus", payload.get("muted"))
        info["output"] = nested.get("soundOutput")
    except Exception:  # noqa: BLE001
        pass
    return info


# --------------------------------------------------------------------------
# Acciones
# --------------------------------------------------------------------------
def do_power_off():
    # Si ni siquiera se puede conectar no se envio nada: el backend no debe
    # decir que la TV quedo apagada. Si la conexion estaba vieja, se reintenta
    # una vez; la TV suele cerrar el socket al apagarse.
    try:
        if tv.client is None:
            tv.connect()
    except Exception as error:
        return {"sent": False, "error": str(error)[:80]}
    try:
        resp = tv.client.request("ssap://system/turnOff", {}, timeout=1.0)
        return {"sent": True, "confirmed": True, "response": resp.get("payload") or {}}
    except Exception:
        tv.client = None
    try:
        tv.connect()
        resp = tv.client.request("ssap://system/turnOff", {}, timeout=1.0)
        return {"sent": True, "confirmed": True, "response": resp.get("payload") or {}}
    except Exception:
        tv.client = None
        return {"sent": True, "confirmed": False}


def audio_info():
    out = (tv.request("ssap://audio/getSoundOutput").get("payload") or {}).get("soundOutput")
    payload = tv.request("ssap://audio/getVolume").get("payload") or {}
    nested = payload.get("volumeStatus") or {}
    return {
        "output": out,
        "desired_output": DEFAULT_OUTPUT,
        "output_ok": out == DEFAULT_OUTPUT,
        "volume": nested.get("volume", payload.get("volume")),
        "muted": nested.get("muteStatus", payload.get("muted")),
        "external_device_control": nested.get("externalDeviceControl"),
        "volume_syncable": nested.get("volumeSyncable"),
        "dedicated_speaker": nested.get("dedicatedSpeakerConnected"),
    }


def set_output(output):
    resp = tv.request("ssap://audio/changeSoundOutput", {"output": output}, timeout=15)
    return {"requested": output, "response": resp.get("payload")}


def ensure_output():
    """Si la salida no es la barra, la devuelve a la barra. Devuelve None si no
    hubo que corregir nada."""
    if not ENFORCE_OUTPUT:
        return None
    try:
        current = (tv.request("ssap://audio/getSoundOutput").get("payload") or {}).get("soundOutput")
    except Exception:  # noqa: BLE001
        return None
    if not current or current == DEFAULT_OUTPUT:
        return None
    try:
        set_output(DEFAULT_OUTPUT)
        return {"restored": True, "from": current, "to": DEFAULT_OUTPUT}
    except Exception:  # noqa: BLE001
        return None


def current_volume():
    payload = tv.request("ssap://audio/getVolume").get("payload") or {}
    nested = payload.get("volumeStatus") or {}
    return nested.get("volume", payload.get("volume", 0))


def volume_info():
    payload = tv.request("ssap://audio/getVolume").get("payload") or {}
    nested = payload.get("volumeStatus") or {}
    return {
        "volume": nested.get("volume", payload.get("volume")),
        "muted": nested.get("muteStatus", payload.get("muted")),
        "min": 0,
        "max": nested.get("maxVolume", 100),
        "output": nested.get("soundOutput"),
        "external_control": nested.get("externalDeviceControl"),
    }


def do_volume(level=None, delta=None, mute=None):
    restored = ensure_output()
    if mute is not None:
        resp = tv.request("ssap://audio/setMute", {"mute": bool(mute)})
        out = {"muted": bool(mute), "response": resp.get("payload")}
    else:
        if level is None:
            level = int(current_volume() or 0) + int(delta or 0)
        level = max(0, min(100, int(level)))
        resp = tv.request("ssap://audio/setVolume", {"volume": level})
        out = {"level": level, "response": resp.get("payload")}
    if restored:
        out["output_restored"] = restored
    return out


def do_launch(app_id=None, url=None, name=None):
    resolved = None
    if not app_id and name:
        resolved = find_app(name)
        if not resolved:
            raise ValueError("no encontre app para '%s'" % name)
        app_id = resolved["id"]
    if app_id == "browser" and url:
        app_id = "com.webos.app.browser"
    resp = tv.request(
        "ssap://system.launcher/launch",
        {"id": app_id, "params": {"target": url} if url else {}},
        timeout=20,
    )
    out = {"id": app_id, "response": resp.get("payload") or resp}
    if resolved:
        out["matched_title"] = resolved.get("title")
    return out


def list_apps():
    resp = tv.request("ssap://com.webos.applicationManager/listApps")
    apps = (resp.get("payload") or {}).get("apps") or []
    return [{"id": a.get("id"), "title": a.get("title")} for a in apps]


def find_app(query):
    q = (query or "").strip().lower()
    if not q:
        return None
    apps = list_apps()
    # 1) id exacto  2) titulo exacto  3) id contiene  4) titulo contiene
    for a in apps:
        if (a["id"] or "").lower() == q:
            return a
    for a in apps:
        if (a["title"] or "").lower() == q:
            return a
    for a in apps:
        if q in (a["id"] or "").lower():
            return a
    for a in apps:
        if q in (a["title"] or "").lower():
            return a
    return None


def list_inputs():
    # 1) intento oficial (en esta TV da 401)
    try:
        resp = tv.request("ssap://tv/getExternalInputList", timeout=8)
        devices = (resp.get("payload") or {}).get("devices") or []
        if devices:
            return [
                {
                    "id": d.get("id"),
                    "label": d.get("label"),
                    "connected": d.get("connected"),
                    "appId": d.get("appId"),
                }
                for d in devices
            ]
    except Exception:  # noqa: BLE001
        pass

    # 2) respaldo: deducir de las apps de entrada instaladas
    out = []
    for a in list_apps():
        app_id = a.get("id") or ""
        match = re.match(r"com\.webos\.app\.hdmi(\d)$", app_id)
        if match:
            out.append({
                "id": "HDMI_%s" % match.group(1),
                "label": a.get("title") or ("HDMI %s" % match.group(1)),
                "connected": None,
                "appId": app_id,
            })
        elif app_id in ("com.webos.app.livetv", "com.webos.app.liveplayback"):
            out.append({
                "id": "TV",
                "label": a.get("title") or "TV / antena",
                "connected": True,
                "appId": app_id,
            })
    out.sort(key=lambda d: d["id"])
    return out


def normalize_input(value):
    raw = str(value or "").strip().upper().replace(" ", "").replace("_", "")
    if raw in ("TV", "ANTENA", "LIVETV", "ANTENNA"):
        return "TV"
    if raw.startswith("HDMI"):
        raw = raw[4:]
    if raw.isdigit():
        return "HDMI_%s" % raw.lstrip("0")
    return str(value).strip().upper()


def do_input(input_id):
    target = normalize_input(input_id)
    if target == "TV":
        resp = tv.request("ssap://system.launcher/launch", {"id": "com.webos.app.livetv"}, timeout=20)
        return {"ok": True, "input": "TV", "via": "com.webos.app.livetv", "response": resp.get("payload") or resp}

    try:
        resp = tv.request("ssap://tv/switchInput", {"inputId": target}, timeout=15)
        payload = resp.get("payload") or {}
        if payload.get("returnValue") is True:
            return {"ok": True, "input": target, "via": "switchInput"}
    except Exception:  # noqa: BLE001
        pass
    # Respaldo: la app de la entrada (com.webos.app.hdmi1, ...)
    suffix = target.split("_")[-1]
    fallback = "com.webos.app.hdmi%s" % suffix
    resp = tv.request("ssap://system.launcher/launch", {"id": fallback}, timeout=20)
    return {"ok": True, "input": target, "via": fallback, "response": resp.get("payload") or resp}


def do_toast(message):
    resp = tv.request("ssap://system.notifications/createToast", {"message": message})
    return resp.get("payload") or resp


SCENES = {
    "cine": [("app", {"id": "com.webos.app.browser"}), ("volume", {"level": 25})],
    "noche": [("volume", {"level": 5}), ("off", {})],
    "manana": [("on", {}), ("volume", {"level": 15})],
}


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------
ROUTES = {
    "/": "indice",
    "/openapi.json": "spec",
    "/health": "salud (sin token)",
    "/status": "status",
    "/apps": "apps",
    "/volume": "volumen (GET leer / POST cambiar)",
    "/audio": "salida de audio (GET) / forzar barra (POST)",
    "/inputs": "entradas HDMI (GET)",
    "/input": "cambiar a HDMI (POST)",
    "/power/on": "power_on",
    "/wake": "power_on (alias)",
    "/power/off": "power_off",
    "/app": "app",
    "/toast": "toast",
    "/scene": "scene",
}

SPEC = {
    "openapi": "3.0.0",
    "info": {"title": "LG webOS TV API", "version": "1.0", "description": "Control local de la TV rooteada"},
    "paths": {
        "/status": {"get": {"summary": "Estado: encendida/apagada, app, volumen"}},
        "/apps": {"get": {"summary": "Lista de apps instaladas"}},
        "/volume": {
            "get": {"summary": "Volumen actual, mute, salida de audio"},
            "post": {
                "summary": "Cambiar volumen o silenciar",
                "requestBody": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "properties": {
                        "level": {"type": "integer", "minimum": 0, "maximum": 100},
                        "delta": {"type": "integer"},
                        "mute": {"type": "boolean"},
                    },
                }}}},
            },
        },
        "/inputs": {"get": {"summary": "Entradas disponibles (HDMI, TV)"}},
        "/input": {
            "post": {
                "summary": "Cambiar de entrada",
                "requestBody": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "properties": {"id": {"type": "string", "example": "HDMI_1"}},
                }}}},
            }
        },
        "/power/on": {"post": {"summary": "Encender via Wake-on-LAN"}},
        "/power/off": {"post": {"summary": "Apagar (standby)"}},
        "/volume": {
            "post": {
                "summary": "Cambiar volumen",
                "requestBody": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "properties": {"level": {"type": "integer"}, "delta": {"type": "integer"}},
                }}}},
            }
        },
        "/app": {
            "post": {
                "summary": "Abrir app o URL",
                "requestBody": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "properties": {"id": {"type": "string"}, "url": {"type": "string"}},
                }}}},
            }
        },
        "/toast": {
            "post": {
                "summary": "Mostrar notificacion en la TV",
                "requestBody": {"content": {"application/json": {"schema": {
                    "type": "object", "properties": {"message": {"type": "string"}},
                }}}},
            }
        },
        "/scene": {
            "post": {
                "summary": "Escena predefinida (cine, noche, manana)",
                "requestBody": {"content": {"application/json": {"schema": {
                    "type": "object", "properties": {"name": {"type": "string"}},
                }}}},
            }
        },
    },
}


def _watchdog():
    while True:
        time.sleep(WATCHDOG_SECONDS)
        try:
            if is_awake(timeout=5):
                changed = ensure_output()
                if changed:
                    print("[watchdog] salida restaurada: %s" % changed, flush=True)
        except Exception:  # noqa: BLE001
            pass


class Handler(BaseHTTPRequestHandler):
    server_version = "TVAPI/1.0"

    def _send(self, code, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _authed(self):
        if not API_TOKEN:
            return True
        return self.headers.get("Authorization", "") == "Bearer " + API_TOKEN

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return {}

    def do_GET(self):  # noqa: N802
        path = urlparse(self.path).path.rstrip("/") or "/"
        if path == "/health":
            return self._send(200, {"ok": True, "tv": TV_IP, "reachable": is_reachable()})
        if not self._authed():
            return self._send(401, {"error": "token requerido"})
        try:
            if path == "/":
                return self._send(200, {"endpoints": ROUTES, "spec": "/openapi.json"})
            if path == "/openapi.json":
                return self._send(200, SPEC)
            if path == "/status":
                return self._send(200, status())
            if path == "/volume":
                return self._send(200, volume_info())
            if path == "/audio":
                return self._send(200, audio_info())
            if path == "/inputs":
                return self._send(200, {"inputs": list_inputs()})
            if path == "/apps":
                apps = list_apps()
                return self._send(200, {"count": len(apps), "apps": apps})
            return self._send(404, {"error": "ruta desconocida", "endpoints": ROUTES})
        except Exception as exc:  # noqa: BLE001
            return self._send(500, {"error": str(exc)})

    def do_POST(self):  # noqa: N802
        path = urlparse(self.path).path.rstrip("/") or "/"
        if not self._authed():
            return self._send(401, {"error": "token requerido"})
        body = self._body()
        try:
            if path in ("/power/on", "/wake"):
                sent = wake_on_lan()
                # No bloquear el loop del backend esperando el despertar: el WoL ya salio y la TV enciende sola.
                return self._send(200, {"ok": True, "state": "wol_enviado", "wol_sent": sent})
            if path == "/power/off":
                return self._send(200, {"ok": True, "response": do_power_off()})
            if path == "/volume":
                return self._send(200, do_volume(body.get("level"), body.get("delta"), body.get("mute")))
            if path == "/audio":
                if body.get("enforce"):
                    return self._send(200, {"enforced": True, "changed": ensure_output(), "state": audio_info()})
                if not body.get("output"):
                    return self._send(400, {"error": "falta 'output' (external_optical, tv_speaker) o 'enforce':true"})
                result = set_output(normalize_output(body.get("output")))
                result["state"] = audio_info()
                return self._send(200, result)
            if path == "/input":
                if not body.get("id") and not body.get("hdmi"):
                    return self._send(400, {"error": "falta 'id' (ej. HDMI_1, 1, hdmi2)"})
                return self._send(200, do_input(body.get("id") or body.get("hdmi")))
            if path == "/app":
                return self._send(200, do_launch(body.get("id"), body.get("url"), body.get("name")))
            if path == "/toast":
                return self._send(200, {"response": do_toast(body.get("message", "hola"))})
            if path == "/scene":
                name = body.get("name", "")
                steps = SCENES.get(name)
                if not steps:
                    return self._send(400, {"error": "escena desconocida", "disponibles": list(SCENES)})
                out = []
                for action, args in steps:
                    if action == "on":
                        out.append({"on": wake_on_lan()})
                    elif action == "off":
                        out.append({"off": do_power_off()})
                    elif action == "volume":
                        out.append({"volume": do_volume(args.get("level"))})
                    elif action == "app":
                        out.append({"app": do_launch(args.get("id"))})
                return self._send(200, {"scene": name, "result": out})
            return self._send(404, {"error": "ruta desconocida", "endpoints": ROUTES})
        except Exception as exc:  # noqa: BLE001
            return self._send(500, {"error": str(exc)})

    def log_message(self, fmt, *args):
        sys.stderr.write("[api %s] %s\n" % (time.strftime("%H:%M:%S"), fmt % args))


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "serve"

    if cmd == "pair":
        print("Emparejando con %s..." % TV_IP)
        print(">>> ACEPTA EL AVISO EN LA TV (boton 'Si'/'Aceptar') <<<")
        t = TV()
        t.connect(interactive=True)
        print("Emparejado. Clave guardada en %s" % KEY_FILE)
        print("Volumen:", t.request("ssap://audio/getVolume").get("payload"))
        t.close()
        return 0

    if cmd != "serve":
        print(__doc__)
        return 1

    print("API escuchando en http://%s:%d" % (API_HOST, API_PORT))
    if API_HOST not in ("127.0.0.1", "localhost"):
        print("Token: %s" % API_TOKEN)
        print("Ejemplo: curl -H \"Authorization: Bearer %s\" http://%s:%d/status"
              % (API_TOKEN, "<ip-de-esta-maquina>", API_PORT))
    if not is_reachable():
        print("AVISO: la TV no responde ahora mismo (apagada o en standby).")
    if ENFORCE_OUTPUT:
        print("Salida de audio forzada a: %s" % DEFAULT_OUTPUT)
    if WATCHDOG_SECONDS > 0:
        threading.Thread(target=_watchdog, daemon=True).start()
        print("Vigilante de salida de audio cada %ds" % WATCHDOG_SECONDS)
    httpd = ThreadingHTTPServer((API_HOST, API_PORT), Handler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\ncerrando")
    return 0


if __name__ == "__main__":
    sys.exit(main())
