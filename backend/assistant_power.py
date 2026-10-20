"""Pure resident-model policy prototype. NO daemon/executor/microphone/storage.

Not wired to the installed app. Config is disabled by default. Resource and
manual holds dominate the clock; a night wake override ends at that night's end.
"""
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import re
from doja_intents import normalized

BOGOTA = timezone(timedelta(hours=-5), 'America/Bogota')


def power_phrase(human):
    """Exact vocabulary for a future LOCAL bootstrap listener, not an LFM tool.

    The future caller must supply a fresh, trusted ASR event, excluding TTS and
    remote/web/history text. This function alone authorizes no process action.
    """
    if not isinstance(human, str) or len(human) > 100 or re.search(r'[\x00-\x1f\x7f"\'«»<>]', human): return None
    plain = normalized(human).strip(' .,!¡?¿')
    plain = re.sub(r'^andromeda[, ]+', '', plain) if plain.startswith(('andromeda ', 'andromeda,')) else ''
    return {'suspension': 'suspend', 'suspencion': 'suspend', 'buenos dias': 'resume', 'activate': 'resume'}.get(plain)


@dataclass(frozen=True)
class Settings:
    enabled: bool = False
    activation_listener: bool = False
    quiet_start: int = 23 * 60
    quiet_end: int = 6 * 60

    def __post_init__(self):
        if (type(self.enabled) is not bool or type(self.activation_listener) is not bool or
                type(self.quiet_start) is not int or type(self.quiet_end) is not int or
                not 0 <= self.quiet_start < 1440 or not 0 <= self.quiet_end < 1440 or self.quiet_start == self.quiet_end):
            raise ValueError('Invalid power settings')


@dataclass(frozen=True)
class Resources:
    available_kib: int
    battery_deci_c: int

    def valid(self):
        return type(self.available_kib) is int and self.available_kib >= 0 and type(self.battery_deci_c) is int and -100 <= self.battery_deci_c <= 800

    def can_start(self): return self.valid() and self.available_kib >= 1200000 and self.battery_deci_c < 380
    def can_remain(self): return self.valid() and self.available_kib >= 500000 and self.battery_deci_c < 400


@dataclass(frozen=True)
class Decision:
    state: str
    model_desired: bool
    activation_listener_desired: bool
    plan_only: bool = True


class PowerPolicy:
    def __init__(self, settings=Settings(), manual_hold=False):
        if type(manual_hold) is not bool: raise ValueError('Invalid manual hold')
        self.settings, self.manual_hold = settings, manual_hold
        self.resource_hold, self.wake_until = False, None
        self.last_wall = None

    def quiet(self, now):
        minute = now.hour * 60 + now.minute; s = self.settings
        return (minute >= s.quiet_start or minute < s.quiet_end) if s.quiet_start > s.quiet_end else s.quiet_start <= minute < s.quiet_end

    def quiet_end(self, now):
        end = now.replace(hour=self.settings.quiet_end // 60, minute=self.settings.quiet_end % 60, second=0, microsecond=0)
        return end + timedelta(days=1) if end <= now else end

    def decide(self, now, resources, event='tick', model_running=False):
        if not isinstance(now, datetime) or now.tzinfo is None or now.utcoffset() is None: raise ValueError('Timezone-aware clock required')
        if event not in {'tick', 'suspend', 'resume', 'rearm_safety'} or type(model_running) is not bool: raise ValueError('Invalid power event')
        now = now.astimezone(BOGOTA)
        if self.last_wall is not None and now < self.last_wall: self.wake_until = None
        self.last_wall = now
        if event == 'suspend': self.manual_hold = True; self.wake_until = None
        if not self.settings.enabled: return Decision('DISABLED', False, False)
        safe = isinstance(resources, Resources) and (resources.can_remain() if model_running else resources.can_start())
        if not safe: self.resource_hold = True
        if event == 'rearm_safety' and isinstance(resources, Resources) and resources.can_start(): self.resource_hold = False
        if event == 'resume' and not self.resource_hold:
            self.manual_hold = False
            self.wake_until = self.quiet_end(now) if self.quiet(now) else None
        if self.resource_hold: return Decision('SAFETY_HOLD', False, False)
        listener = self.settings.activation_listener
        if self.manual_hold: return Decision('MANUAL_SUSPEND', False, listener)
        if self.wake_until is not None and now >= self.wake_until: self.wake_until = None
        if self.quiet(now) and self.wake_until is None: return Decision('NIGHT_SLEEP', False, listener)
        return Decision('RESIDENT', True, listener)
