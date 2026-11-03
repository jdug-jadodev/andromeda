"""Deterministic text→tool router. Fase 1: sin inferencia local.

Reemplaza la selección de herramienta del modelo local: empareja la frase actual
contra los gates `TOOL_INTENT` y despacha con los parsers deterministas ya
existentes. Devuelve las mismas formas de resultado que
`AssistantToolsModel.interpret_result`, de modo que el resto del backend (canales
Android, `say`) no cambia.
"""
import re

from doja_intents import normalized
from assistant_tools_model import TOOL_INTENT, FALLBACK, TV_UNCLEAR
from timer_proposals import literal_seconds, TimerValidationError
from agenda_requests import current_event, AgendaValidationError
from phone_volume import current_volume
from tv_intents import parse_volume, parse_input, parse_scene, parse_audio, parse_app
from self_destruct import choose


# Orden de prioridad para el emparejado determinista. Los gates de TV ya exigen
# "tele/tv" por lookahead, así que solo emparejan frases de TV; el orden entre
# ellos y el resto evita choques (p. ej. "enciende la tele" → tv_power_on, no
# control_lights).
TOOL_ORDER = (
    'self_destruct',
    'tv_power_off',
    'tv_power_on',
    'tv_volume',
    'tv_audio',
    'tv_open_app',
    'tv_scene',
    'tv_input',
    'tv_inputs',
    'tv_status',
    'delete_reminder',
    'create_reminder',
    'create_alarm',
    'create_timer',
    'list_reminders',
    'set_phone_volume',
    'control_lights',
)


def _matches(name, plain):
    pattern = TOOL_INTENT.get(name)
    return bool(pattern) and bool(re.search(pattern, plain))


def _dispatch(name, text, model):
    """Espejo de los cuerpos de rama de interpret_result, sin el modelo."""
    if name == 'delete_reminder':
        if not model.reminder_deletion:
            raise ValueError('Reminder deletion not negotiated')
        from reminder_deletion import requested
        requested(text)
        return {'kind': 'reminder_delete_request'}
    if name == 'list_reminders':
        if not model.basic_assistant:
            raise ValueError('Reminder reading not negotiated')
        from reminder_read import current_read
        current_read(text)
        return {'kind': 'reminder_list_request'}
    if name == 'create_timer':
        try:
            seconds = literal_seconds(text)
        except TimerValidationError as invalid:
            return {'kind': 'timer_rejected', 'reason': invalid.reason}
        return {'kind': 'timer_proposal', 'seconds': seconds}
    if name in {'create_alarm', 'create_reminder'}:
        kind = 'ALARM' if name == 'create_alarm' else 'REMINDER'
        if model.alarm_lifecycle and kind == 'ALARM':
            from alarm_schedule import deletion_target
            try:
                target = deletion_target(text)
            except ValueError:
                pass
            else:
                return dict(kind='alarm_delete_request', **target)
        try:
            value = current_event(kind, text, model.anchor_wall, model.alarm_lifecycle)
        except AgendaValidationError:
            if not model.guided_agenda:
                raise
            from agenda_dialogue import opening
            opening(kind, text, model.reminder_clarification, model.anchor_wall)
            return {'kind': 'agenda_dialogue_start', 'event_kind': kind}
        return dict(value, kind='agenda_proposal')
    if name == 'self_destruct':
        return choose()
    if name == 'set_phone_volume':
        if model.confirmed_volume:
            return {'kind': 'volume_dialogue_start'}
        return {'kind': 'volume_proposal', 'percent': current_volume(text)}
    if name == 'control_lights':
        return {'kind': 'light_proposal'}
    if name in {'tv_status', 'tv_inputs'}:
        if not model.tv_enabled:
            raise ValueError('TV control not enabled')
        if re.search(r'\b(?:entradas?|hdmi|fuentes?)\b', normalized(text)):
            return {'kind': 'tv_inputs'}
        return {'kind': 'tv_status'}
    if name in {'tv_power_on', 'tv_power_off'}:
        if not model.tv_enabled:
            raise ValueError('TV control not enabled')
        return {'kind': name}
    if name == 'tv_volume':
        if not model.tv_enabled:
            raise ValueError('TV control not enabled')
        params = parse_volume(text)
        if params is None:
            return {'kind': 'reply', 'text': TV_UNCLEAR}
        return dict(kind='tv_volume', **params)
    if name == 'tv_input':
        if not model.tv_enabled:
            raise ValueError('TV control not enabled')
        params = parse_input(text)
        if params is None:
            return {'kind': 'reply', 'text': TV_UNCLEAR}
        return dict(kind='tv_input', **params)
    if name == 'tv_audio':
        if not model.tv_enabled:
            raise ValueError('TV control not enabled')
        params = parse_audio(text)
        if params is None:
            return {'kind': 'reply', 'text': TV_UNCLEAR}
        return dict(kind='tv_audio', **params)
    if name == 'tv_scene':
        if not model.tv_enabled:
            raise ValueError('TV control not enabled')
        params = parse_scene(text)
        if params is None:
            return {'kind': 'reply', 'text': TV_UNCLEAR}
        return dict(kind='tv_scene', **params)
    if name == 'tv_open_app':
        if not model.tv_enabled:
            raise ValueError('TV control not enabled')
        params = parse_app(text)
        if params is None:
            return {'kind': 'reply', 'text': TV_UNCLEAR}
        return dict(kind='tv_open_app', **params)
    raise ValueError('Unhandled tool: ' + name)


def route(text, model):
    """Devuelve un dict de resultado, o `{'kind': 'reply', 'text': FALLBACK}`.

    Lo que no reconoce devuelve el FALLBACK ("No te entendí"). El fallback a
    DeepSeek (Fase 2) lo dispara `AssistantToolsModel.start()` a partir de ese
    FALLBACK, vía `cloud_enabled()`/`route_cloud()`.
    """
    plain = normalized(text)
    for name in TOOL_ORDER:
        if _matches(name, plain):
            try:
                return _dispatch(name, text, model)
            except (ValueError, KeyError, TypeError):
                return {'kind': 'tools_rejected'}
    # Duración a secas ("5 minutos", "media hora") → temporizador, como follow-up
    # tras "pon un temporizador". El guard Java acepta la misma forma.
    try:
        literal_seconds(text)
    except TimerValidationError:
        pass
    else:
        try:
            return _dispatch('create_timer', text, model)
        except (ValueError, KeyError, TypeError):
            return {'kind': 'tools_rejected'}
    return {'kind': 'reply', 'text': FALLBACK}


def cloud_enabled():
    """True si DeepSeek está configurado (hay API key). Import tardío, no rompe
    el arranque determinista si `cloud_model` no está disponible."""
    try:
        import cloud_model
    except Exception:
        return False
    return cloud_model.enabled()


def route_cloud(text, model):
    """Fallback a DeepSeek. Devuelve un dict final (texto o herramienta despachada)
    o None si no hay key / no responde / falla la red."""
    try:
        import cloud_model
        raw = cloud_model.reason(text, model)
    except Exception:
        return None
    if raw is None:
        return None
    if raw.get('kind') == 'text':
        return {'kind': 'reply', 'text': raw['text']}
    name = raw.get('name')
    if name in TOOL_ORDER:
        try:
            return _dispatch(name, text, model)
        except (ValueError, KeyError, TypeError):
            return {'kind': 'tools_rejected'}
    return None
