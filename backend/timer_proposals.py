"""Timer-trial building blocks. No executor, network, storage or startup.

The model selects create_timer. The literal guard is a subsequent safety check,
not the conversational router. No legacy agent/tool catalog is activated here.
"""
from dataclasses import asdict, dataclass, replace
import json
import re
import uuid

from doja_intents import DURATION, normalized, number


TIMER_TOOL = {'type': 'function', 'function': {
    'name': 'create_timer', 'description': 'Request one countdown. Copy the COMPLETE duration from the current human request into duration_text; do not convert units or infer missing values. Runtime validates the explicit request, executes once and reports the real Android result; never claim execution yourself.',
    'parameters': {'type': 'object', 'properties': {'duration_text': {'type': 'string', 'minLength': 1, 'maxLength': 120}},
                   'required': ['duration_text'], 'additionalProperties': False}}}
CONFIRMATIONS = {'confirmo temporizador', 'confirmo el temporizador'}


class TimerValidationError(ValueError):
    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


def direct_timer_prefix(text):
    """Post-model safety check, not a tool router or permission to execute."""
    if not isinstance(text, str) or not 0 < len(text) <= 250: return False
    plain = normalized(text).strip(' .,!¡?¿')
    plain = re.sub(r'^andromeda[, ]+', '', plain)
    plain = re.sub(r'^por favor\s+', '', plain)
    return re.match(r'^(?:(?:pon|pone|ponme|crea|inicia|programa)\s+)?(?:un |una |el |la )?'
                    r'(?:temporizador|timer|cuenta atras)\b', plain) is not None


def literal_duration(text):
    if not isinstance(text, str) or not 0 < len(text) <= 250 or '<|' in text or re.search(r'["\'«»<>]|\d[.,]\d|-\s*\d', text):
        raise TimerValidationError('invalid_request')
    plain = normalized(text).strip(' .,!¡?¿')
    if any(c.isdecimal() and c not in '0123456789' for c in plain): raise TimerValidationError('invalid_request')
    plain = re.sub(r'^andromeda[, ]+', '', plain)
    plain = re.sub(r'^por favor\s+', '', plain)
    if not re.match(r'^(?:(?:pon|pone|ponme|crea|inicia|programa)\s+|(?:un |una )?(?:temporizador|timer|cuenta atras)\b)', plain):
        raise TimerValidationError('invalid_request')
    if not re.search(r'\b(?:temporizador|timer|cuenta atras)\b', plain): raise TimerValidationError('invalid_request')
    found = list(DURATION.finditer(plain)); seconds, seen = 0, set()
    if not found: raise TimerValidationError('missing_duration')
    # Match the entire structure, not a bag of harmless words: two separate
    # commands or "one hour, in two minutes" must never become a summed timer.
    duration = DURATION.pattern
    if not re.fullmatch(r'(?:(?:pon|pone|ponme|crea|inicia|programa)\s+)?(?:un |una |el |la )?'
                        r'(?:temporizador|timer|cuenta atras)\s+(?:(?:de|en)\s+)?' + duration +
                        r'(?:\s+(?:y\s+)?' + duration + r')*(?:\s+por favor)?(?:\s+ahora)?', plain):
        raise TimerValidationError('invalid_request')
    for match in found:
        unit = match[2][0]
        if unit in seen: raise TimerValidationError('invalid_request')
        try: amount = number(match[1])
        except ValueError: raise TimerValidationError('invalid_request') from None
        if amount <= 0: raise TimerValidationError('invalid_request')
        seen.add(unit); seconds += amount * {'s': 1, 'm': 60, 'h': 3600}[unit]
    if not 1 <= seconds <= 86400:
        raise TimerValidationError('out_of_range')
    return plain[found[0].start():found[-1].end()], seconds


def literal_seconds(text): return literal_duration(text)[1]


def duration_components(text):
    """Normalize notation only, never convert units, add, drop or infer parts."""
    plain = normalized(text)
    if any(c.isdecimal() and c not in '0123456789' for c in plain): raise ValueError('Unsupported numeral')
    duration = DURATION.pattern
    if not re.fullmatch(duration + r'(?:\s+(?:y\s+)?' + duration + r')*', plain):
        raise ValueError('Expected complete Spanish duration')
    return [(number(m[1]), m[2][0]) for m in DURATION.finditer(plain)]


def selected_timer_seconds(message, finish_reason, request):
    """Parse one native function call only; never eval or infer a duration."""
    if not isinstance(message, dict) or finish_reason not in {'stop', 'tool_calls'}: raise ValueError('Incomplete tool response')
    calls = message.get('tool_calls')
    if not isinstance(calls, list) or len(calls) != 1: raise ValueError('Expected one native timer call')
    call = calls[0]
    if not isinstance(call, dict) or call.get('type') != 'function': raise ValueError('Invalid native call')
    function = call.get('function')
    if not isinstance(function, dict) or set(function) != {'name', 'arguments'} or function['name'] != 'create_timer':
        raise ValueError('Unknown tool')
    args = function['arguments']
    if isinstance(args, str):
        if len(args) > 256: raise ValueError('Oversized arguments')
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result: raise ValueError('Duplicate tool argument')
                result[key] = value
            return result
        args = json.loads(args, object_pairs_hook=unique)
    if not isinstance(args, dict) or set(args) != {'duration_text'} or not isinstance(args['duration_text'], str) or not 0 < len(args['duration_text']) <= 120:
        raise ValueError('Invalid duration schema')
    duration, seconds = literal_duration(request)
    try: same = duration_components(args['duration_text']) == duration_components(duration)
    except ValueError: same = False
    if not same: raise TimerValidationError('duration_mismatch')
    return seconds


def _identity(value):
    return isinstance(value, str) and re.fullmatch('[0-9a-f]{32}', value) is not None


@dataclass(frozen=True)
class TimerProposal:
    proposal_id: str
    timer_id: str
    nonce: str
    session_id: str
    context_id: str
    origin_turn_id: str
    origin_sequence: int
    seconds: int
    expires_ms: int

    def packet(self): return asdict(self)


class TimerProposals:
    """Current-request permits plus historical confirmation checks; bounded RAM."""
    def __init__(self, clock):
        self.clock, self.pending, self.delivered, self.closed = clock, None, False, False
        self.authorized_turns = set()

    def authorize_request(self, seconds, request, session_id, context_id, turn_id, sequence, speech_end, session_end):
        """A complete low-risk request is the authorization, not a second yes.

        The old offer/confirmation helpers remain for historical unit regressions;
        protocol 8 never exposes them. One request can authorize only one timer.
        """
        now = self.clock()
        if not _identity(turn_id) or turn_id in self.authorized_turns or len(self.authorized_turns) >= 24:
            raise ValueError('Timer request already consumed')
        p = self.offer(seconds, request, session_id, context_id, turn_id, sequence, speech_end, session_end)
        deadline = min(now + 4000, speech_end + 26500, session_end - 5000)
        self.cancel()
        if deadline - now < 500: raise ValueError('Insufficient timer execution budget')
        p = replace(p, expires_ms=deadline)
        self.authorized_turns.add(turn_id)
        return {'kind': 'request_authorized', 'proposal': p.packet(), 'request_turn_id': turn_id,
                'request_sequence': sequence, 'speech_end_ms': speech_end, 'execute_before_ms': deadline}

    def offer(self, seconds, request, session_id, context_id, turn_id, sequence, speech_end, session_end):
        now = self.clock()
        if (self.closed or self.pending is not None or not all(map(_identity, (session_id, context_id, turn_id)))
                or type(sequence) is not int or sequence < 1 or type(seconds) is not int
                or seconds != literal_seconds(request) or type(speech_end) is not int
                or not 0 < speech_end <= now + 100 or now - speech_end >= 26500
                or type(session_end) is not int or not now < session_end <= now + 300000):
            raise ValueError('Invalid timer proposal state')
        expires = min(now + 45000, session_end - 1500)
        if expires <= now + 1500: raise ValueError('Session is ending')
        self.pending = TimerProposal(uuid.uuid4().hex, str(uuid.uuid4()), uuid.uuid4().hex,
                                     session_id, context_id, turn_id, sequence, seconds, expires)
        self.delivered = False
        return self.pending

    def spoken(self, proposal_id, turn_id):
        p = self.pending
        if (self.closed or p is None or self.delivered or self.clock() >= p.expires_ms
                or p.proposal_id != proposal_id or p.origin_turn_id != turn_id): return False
        self.delivered = True; return True

    def respond(self, text, session_id, context_id, turn_id, sequence, speech_end, session_end):
        p, now = self.pending, self.clock()
        if self.closed or p is None: return {'kind': 'none'}
        if now >= p.expires_ms:
            self.cancel(); return {'kind': 'expired'}
        if (session_id != p.session_id or context_id != p.context_id or not _identity(turn_id)
                or turn_id == p.origin_turn_id or type(sequence) is not int or sequence <= p.origin_sequence
                or type(speech_end) is not int or not 0 < speech_end <= now + 100
                or type(session_end) is not int or not now < session_end <= now + 300000):
            raise ValueError('Confirmation belongs to another turn/session/chat')
        if not isinstance(text, str) or not 0 < len(text) <= 250: raise ValueError('Invalid confirmation text')
        plain = normalized(text).strip(' .,!¡?¿')
        if plain in {'no', 'no gracias', 'no, gracias', 'gracias', 'cierra la conversacion'}:
            self.cancel(); return {'kind': 'cancelled'}
        if plain in {'si', 'confirmo', 'adelante'}: return {'kind': 'explicit_confirmation_required'}
        if plain not in CONFIRMATIONS:
            self.cancel(); return {'kind': 'unrelated'}
        if not self.delivered: return {'kind': 'offer_not_spoken'}
        deadline = min(speech_end + 10000, p.expires_ms, session_end - 1500)
        if deadline <= now:
            self.cancel(); return {'kind': 'expired'}
        self.cancel()  # Consume before returning the single-use authorization.
        return {'kind': 'authorized', 'proposal': p.packet(), 'confirmation_turn_id': turn_id,
                'confirmation_sequence': sequence, 'speech_end_ms': speech_end, 'execute_before_ms': deadline}

    def cancel(self): self.pending, self.delivered = None, False
    def close(self): self.cancel(); self.closed = True
