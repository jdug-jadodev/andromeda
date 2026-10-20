"""Explicit continuous transport policy; bounded human contexts and action epochs.

No microphone, executor, storage, restart or far-future deadline. A context reset
changes authorization scope on the SAME authenticated connection, not its lifetime.
"""
import json
import re
import uuid

PROTOCOL = 12
VOICE_POLICY = 'continuous_voice_v1'
RESET_POLICY = 'client_fresh_v1'
ACTION_WINDOW_MS = 300000
CHECKPOINT_TURNS = 20
MAX_EPOCH_TURNS = 64
RESET_TIMEOUT_MS = 4000
STREAM_START_MS = 15000
STREAM_IDLE_MS = 5000
RESET_REASONS = {'dialogue_closed', 'turn_checkpoint', 'human_inactivity', 'audio_resume'}
END_REASONS = {'voice_manual_stop','voice_suspended','voice_peer_closed','voice_scope_limit','voice_request_rejected','voice_session_failed','voice_context_expired',
    'voice_stream_lost','voice_context_timeout','voice_protocol_error','voice_cancellation_stuck','voice_cache_clear_failed'}


class ContinuousVoiceFault(RuntimeError):
    def __init__(self, reason):
        if reason not in END_REASONS: raise ValueError('Unknown continuous voice fault')
        self.reason=reason; super().__init__(reason)


def identity(value): return isinstance(value, str) and re.fullmatch('[0-9a-f]{32}', value) is not None


def packet(data):
    if not isinstance(data, bytes) or not 0 < len(data) <= 131072: raise ValueError('Invalid continuous context size')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result: raise ValueError('Duplicate continuous context field')
            result[key] = value
        return result
    value = json.loads(data, object_pairs_hook=unique)
    if not isinstance(value, dict): raise ValueError('Invalid continuous context packet')
    return value


class ContextReset:
    def __init__(self, session, context, reason, clock, retained_turns=0, last_human_ms=0):
        if not identity(session) or not identity(context) or reason not in RESET_REASONS: raise ValueError('Invalid context reset')
        if (type(retained_turns) is not int or not 0 <= retained_turns <= 24 or type(last_human_ms) is not int or last_human_ms < 0
                or bool(retained_turns) != bool(last_human_ms) or reason != 'turn_checkpoint' and (retained_turns or last_human_ms)):
            raise ValueError('Invalid checkpoint memory bounds')
        self.session, self.context, self.reason, self.clock = session, context, reason, clock
        self.retained_turns, self.last_human_ms = retained_turns, last_human_ms
        self.nonce = uuid.uuid4().hex; self.before = clock() + RESET_TIMEOUT_MS; self.consumed = False

    def request(self):
        return dict(type='voice_context_request', reset_nonce=self.nonce, reason=self.reason,
            **({'retained_turns':self.retained_turns,'last_human_ms':self.last_human_ms} if self.reason=='turn_checkpoint' else {}))

    def claim(self, value):
        keys = {'action','reset_nonce','previous_session_id','previous_context_id','context','catalog','voice_entry'}
        if (self.consumed or self.clock() >= self.before or not isinstance(value,dict) or set(value) != keys
                or value['action'] != 'voice_context' or value['reset_nonce'] != self.nonce
                or value['previous_session_id'] != self.session or value['previous_context_id'] != self.context
                or type(value['voice_entry']) is not bool or not isinstance(value['catalog'],list)):
            raise ValueError('Unrelated/stale continuous context')
        snapshot = value['context']
        if not isinstance(snapshot,dict) or not identity(snapshot.get('id')): raise ValueError('Invalid continuous context')
        checkpoint = self.reason == 'turn_checkpoint'
        if checkpoint:
            if (snapshot['id'] != self.context or value['voice_entry'] or value['catalog']
                    or not isinstance(snapshot.get('turns'),list) or len(snapshot['turns']) != self.retained_turns
                    or type(snapshot.get('last_human_ms')) is not int or snapshot['last_human_ms'] != self.last_human_ms):
                raise ValueError('Invalid checkpoint scope')
        elif (snapshot['id'] == self.context or not value['voice_entry'] or len(value['catalog']) > 3
                or snapshot.get('turns') != [] or type(snapshot.get('last_human_ms')) is not int or snapshot['last_human_ms'] != 0):
            raise ValueError('Fresh client context required')
        self.consumed = True
        return snapshot, value['catalog'], not checkpoint
