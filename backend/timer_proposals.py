"""Inactive timer-trial building blocks. No executor, network, storage or startup.

The model selects create_timer. The literal guard is a subsequent safety check,
not the conversational router. No legacy agent/tool catalog is activated here.
"""
from dataclasses import asdict, dataclass
import json
import re
import uuid

from doja_intents import DURATION, ONES, TENS, normalized, number


TIMER_TOOL = {'type': 'function', 'function': {
    'name': 'create_timer', 'description': 'Propose one countdown from an explicit duration in the current request. The runtime validates and obtains human confirmation; never claim execution.',
    'parameters': {'type': 'object', 'properties': {'seconds': {'type': 'integer', 'minimum': 1, 'maximum': 86400}},
                   'required': ['seconds'], 'additionalProperties': False}}}
CONFIRMATIONS = {'confirmo temporizador', 'confirmo el temporizador'}


def literal_seconds(text):
    if not isinstance(text, str) or not 0 < len(text) <= 250 or '<|' in text or re.search(r'["\'«»<>]|\d[.,]\d|-\s*\d', text):
        raise ValueError('Invalid or ambiguous request')
    plain = normalized(text).strip(' .,!¡?¿')
    plain = re.sub(r'^andromeda[, ]+', '', plain)
    plain = re.sub(r'^por favor\s+', '', plain)
    if not re.match(r'^(?:(?:pon|ponme|crea|inicia|programa)\s+|(?:un |una )?(?:temporizador|timer|cuenta atras)\b)', plain):
        raise ValueError('An explicit current countdown request is required')
    if not re.search(r'\b(?:temporizador|timer|cuenta atras)\b', plain): raise ValueError('Not a countdown')
    found = list(DURATION.finditer(plain)); seconds, seen = 0, set()
    if not found: raise ValueError('Missing duration')
    for match in found:
        unit = match[2][0]
        if unit in seen: raise ValueError('Repeated duration unit')
        amount = number(match[1])
        if amount <= 0: raise ValueError('Non-positive duration component')
        seen.add(unit); seconds += amount * {'s': 1, 'm': 60, 'h': 3600}[unit]
    for match in reversed(found): plain = plain[:match.start()] + ' ' + plain[match.end():]
    allowed = {'pon', 'ponme', 'crea', 'inicia', 'programa', 'un', 'una', 'el', 'la', 'temporizador',
               'timer', 'cuenta', 'atras', 'de', 'en', 'y', 'por', 'favor', 'ahora'}
    if any(word not in allowed for word in re.findall(r'\w+', plain)) or not 1 <= seconds <= 86400:
        raise ValueError('Negated, reported, unsupported or ambiguous request')
    return seconds


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
        args = json.loads(args)
    if not isinstance(args, dict) or set(args) != {'seconds'} or type(args['seconds']) is not int:
        raise ValueError('Invalid duration schema')
    if args['seconds'] != literal_seconds(request): raise ValueError('Model duration does not match human request')
    return args['seconds']


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
    """One spoken offer, one explicit authorization, monotonic deadlines."""
    def __init__(self, clock):
        self.clock, self.pending, self.delivered, self.closed = clock, None, False, False

    def offer(self, seconds, request, session_id, context_id, turn_id, sequence, speech_end, session_end):
        now = self.clock()
        if (self.closed or self.pending is not None or not all(map(_identity, (session_id, context_id, turn_id)))
                or type(sequence) is not int or sequence < 1 or type(seconds) is not int
                or seconds != literal_seconds(request) or type(speech_end) is not int
                or not 0 < speech_end <= now + 100 or now - speech_end >= 26500):
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
                or type(speech_end) is not int or not 0 < speech_end <= now + 100):
            raise ValueError('Confirmation belongs to another turn/session/chat')
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
