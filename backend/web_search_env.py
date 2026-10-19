"""Minimal private .env reader: two allowed names, no shell or interpolation."""
import os
from pathlib import Path
import re
import stat

ENV_PATH = Path('/var/lib/andromeda-web-search/.env')
NAMES = frozenset({'WEB_SEARCH_URL', 'WEB_SEARCH_TOKEN'})


def parse_env(raw):
    if not isinstance(raw, str) or len(raw) > 8192: raise ValueError('Invalid web search environment')
    result = {}
    for line in raw.splitlines():
        line = line.strip()
        if not line or line.startswith('#'): continue
        name, separator, value = line.partition('=')
        name, value = name.strip(), value.strip()
        if not separator or name not in NAMES or name in result: raise ValueError('Invalid web search environment')
        if value[:1] in {'"', "'"}:
            if len(value) < 2 or value[-1] != value[0]: raise ValueError('Invalid web search environment')
            value = value[1:-1]
        if not value or re.search(r'[\s\x00-\x1f\x7f$`\\\'\"]', value): raise ValueError('Invalid web search environment')
        result[name] = value
    if set(result) != NAMES: raise ValueError('Incomplete web search environment')
    return result


def load_private_env(path=ENV_PATH):
    path = Path(path)
    try: parent = path.parent.lstat()
    except FileNotFoundError: return {}
    if not stat.S_ISDIR(parent.st_mode) or parent.st_uid != os.geteuid() or stat.S_IMODE(parent.st_mode) != 0o700:
        raise ValueError('Unsafe web search environment directory')
    try: fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    except FileNotFoundError: return {}
    with os.fdopen(fd, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
                or stat.S_IMODE(info.st_mode) != 0o600 or info.st_size > 8192):
            raise ValueError('Unsafe web search environment file')
        return parse_env(stream.read(8193).decode('utf-8'))


def configure(environ, path=ENV_PATH):
    try:
        values = load_private_env(path)
        for name, value in values.items(): environ.setdefault(name, value)
        return True
    except (OSError, ValueError, UnicodeError):
        # Web configuration cannot prevent voice/agenda startup. Fail web closed,
        # not the whole assistant. Never print the file contents or exception.
        for name in NAMES: environ.pop(name, None)
        return False
