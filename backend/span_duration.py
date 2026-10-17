"""Validate an extracted duration against current user text before conversion.

Experimental argument contract, not a command classifier or an action executor.
Only exact supported spans are accepted; incomplete/extra durations fail closed.
"""
import re
from doja_intents import DURATION, normalized, number


def seconds_from_span(duration_text, user_text):
    if not isinstance(duration_text, str) or not isinstance(user_text, str) or not 0 < len(duration_text) <= 160:
        raise ValueError('Invalid duration span')
    span, source = normalized(duration_text), normalized(user_text)
    if not span:
        raise ValueError('Empty span')
    positions = list(re.finditer(r'(?<!\w)' + re.escape(span) + r'(?!\w)', source))
    if len(positions) != 1:
        raise ValueError('Duration must occur exactly once in the human message')
    position = positions[0]
    source_durations = list(DURATION.finditer(source))
    if not source_durations or any(m.start() < position.start() or m.end() > position.end() for m in source_durations):
        raise ValueError('Ambiguous or incomplete selection')
    matches = list(DURATION.finditer(span))
    remaining, total, seen = span, 0, set()
    for match in reversed(matches):
        remaining = remaining[:match.start()] + ' ' + remaining[match.end():]
    if not matches or not re.fullmatch(r'[\s,y]*', remaining):
        raise ValueError('Unsupported duration syntax')
    for match in matches:
        unit = match[2][0]
        if unit in seen: raise ValueError('Repeated duration unit')
        seen.add(unit)
        total += number(match[1]) * {'s': 1, 'm': 60, 'h': 3600}[unit]
    if not 1 <= total <= 86400:
        raise ValueError('Duration out of range')
    return total
