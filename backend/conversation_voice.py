"""Bounded TLS conversation actor; default remains action-free.

An explicitly negotiated timer trial can send one validated Android proposal.
This process never executes tools, shell or Android actions itself.
"""
from collections import deque
import copy
import hmac
import http.client
import json
import os
import re
from pathlib import Path
import signal
import socket
import ssl
import struct
import threading
import time
import uuid

from assistant_config import SETTINGS
from spanish_wake import words
from doja_intents import normalized
from conversation_flow import ConversationFlow
from conversation_entry import ConversationEntry

PROTOCOL = 6
MAX_STORED_TURNS = 24
# Listening window. Android's ListeningSleepPolicy.CLOSE_AFTER_MS must match: 15 s awake, 10 s drowsy, 5 s asleep.
LISTEN_IDLE_MS = 30000
MAX_TEXT_UNITS = 5700  # Same text budget as six maximum-sized 0.5 turns.


def text_units(text):
    return len(text.encode('utf-16-le')) // 2  # Match Android String.length().

SYSTEM = (f'Eres {SETTINGS["name"]}, una asistente conversacional. Responde en español en una o dos frases breves, '
          'manteniendo el hilo. Esta sesión no tiene herramientas, acceso a Internet ni control de dispositivos. '
          'No afirmes que has ejecutado acciones ni que consultaste datos actuales.')


def conversational_reply(message, finish_reason):
    """Reject structured calls and obvious unsupported confirmations; never infer user intent.

    This conservative output guard is not a general semantic verifier and does
    not authorize tool use. The action-free runtime remains the hard boundary.
    """
    from conversation_agent import parse_native_response
    if finish_reason != 'stop' or message.get('tool_calls'):
        raise ValueError('Incomplete or tool-shaped reply')
    parsed = parse_native_response(message.get('content') or '', [])
    if parsed['kind'] != 'reply': raise ValueError('Non-conversational output')
    text = parsed['text']
    reject_unsupported_claims(text)
    return text


def reject_unsupported_claims(text):
    plain = normalized(text)
    patterns = [
        r'\b(?:estoy|estamos)\s+(?:(?:ya|ahora)\s+)?(?:creando|poniendo|programando|activando|iniciando|cancelando|apagando|encendiendo|buscando|consultando)\b',
        r'\b(?:he|hemos|ya|acabo de) (?:\w+ )?(?:creado|puesto|programado|activado|iniciado|cancelado|eliminado|borrado|pospuesto|apagado|encendido|consultado|buscado|encontrado en internet)\b',
        r'\b(?:temporizador|alarma|recordatorio)\b.{0,45}\b(?:cread[oa]|puest[oa]|programad[oa]|activad[oa]|iniciad[oa]|cancelad[oa]|eliminad[oa]|borrad[oa]|pospuest[oa]|list[oa])\b',
        r'\b(?:te avisare|te recordare|sonara en|voy a (?:poner|crear|programar|activar|cancelar)|timer (?:set|started)|alarm (?:set|scheduled))\b',
        r'\b(?:he|hemos|ya|acabo de)\s+(?:(?:bajado|subido|ajustado|establecido)\b)',
        r'\bvolumen\b.{0,35}\b(?:ajustado|establecido|puesto|al [0-9]+)\b',
    ]
    if any(re.search(pattern, plain) for pattern in patterns):
        raise ValueError('Unsupported action/current-data claim')


def now_ms():
    return int(time.clock_gettime(time.CLOCK_BOOTTIME) * 1000)


def protocol_technical(error):
    """Fixed numeric locations, never exception messages, frame locals or data."""
    sources=('conversation_voice.py','assistant_tools_trial.py','phone_volume.py','timer_trial.py',
        'timer_proposals.py','agenda_dialogue.py','agenda_requests.py','continuous_voice.py','alarm_deletion.py')
    frames=[]; trace=error.__traceback__
    while trace is not None:
        name=Path(trace.tb_frame.f_code.co_filename).name
        if name in sources: frames.append({'source':sources.index(name)+1,'line':trace.tb_lineno})
        trace=trace.tb_next
    return {'version':1,'at_ms':now_ms(),'error':1 if isinstance(error,ValueError) else 2 if isinstance(error,OSError) else 3,
            'frames':frames[-8:]}


def record_protocol_technical(inference_key,value):
    from resident_runtime import private_directory
    directory=private_directory(Path(inference_key).parent)
    try:
        raw=json.dumps(value,allow_nan=False).encode('ascii')
        if len(raw)>4096: return
        fd=os.open('voice_fault.json',os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=directory)
        with os.fdopen(fd,'wb') as stream: stream.write(raw); stream.flush(); os.fsync(stream.fileno())
    finally: os.close(directory)


TOOLS_TECHNICAL_ENUMS = {
    'choice': ('text', 'native', 'local'),
    'tool': ('none', 'unvalidated', 'create_timer', 'create_alarm', 'create_reminder', 'search_web', 'set_phone_volume', 'control_lights', 'list_reminders', 'delete_reminder'),
    'result': ('reply', 'tools_rejected', 'timer_proposal', 'timer_rejected', 'agenda_proposal',
               'agenda_dialogue_start', 'alarm_delete_request', 'web_result', 'volume_proposal', 'volume_dialogue_start', 'light_proposal', 'reminder_list_request', 'reminder_delete_request'),
    'validation': ('invalid_native_call', 'missing_native_call', 'unsupported_reply', 'volume_request_rejected',
                   'agenda_request_rejected', 'timer_request_rejected', 'accepted', 'reply', 'agenda_question',
                   'web_query_rejected', 'web_results', 'web_no_results', 'web_provider_unavailable',
                   'local_web_followup', 'web_invalid_request', 'web_explicit_request_required',
                   'web_invalid_query', 'web_query_mismatch', 'web_mixed_request', 'volume_question', 'light_request_rejected', 'reminder_read_rejected', 'read_requested', 'local_arithmetic', 'reminder_delete_rejected', 'reminder_delete_requested'),
    'generation_error': ('TimeoutError', 'ValueError', 'OSError', 'RuntimeError', 'KeyError', 'TypeError'),
}
TOOLS_TECHNICAL_KEYS = frozenset({'version', 'at_ms', 'sequence', 'phase', 'elapsed_ms', 'choice', 'tool',
    'result', 'validation', 'generation_error', 'input_tokens', 'history_turns', 'web_attempted'})


def tools_technical(metrics, sequence, phase, elapsed_ms, generation_error=None):
    """Last decision stage only: fixed numbers, never audio/text, arguments, IDs or hashes.

    phase1 = model finished BEFORE controller/executor; phase2 = compute timeout.
    This is diagnostic evidence, NOT an Android receipt or an authorization path.
    """
    if (not isinstance(metrics, dict) or type(sequence) is not int or not 1 <= sequence <= 64
            or type(phase) is not int or phase not in (1, 2)):
        raise ValueError('Invalid tools technical scope')
    def code(name, value):
        values = TOOLS_TECHNICAL_ENUMS[name]
        return values.index(value) + 1 if type(value) is str and value in values else 0
    def bounded(value, maximum):
        return value if type(value) is int and 0 <= value <= maximum else -1
    return {'version': 1, 'at_ms': now_ms(), 'sequence': sequence, 'phase': phase,
        'elapsed_ms': bounded(elapsed_ms, 600000),
        'choice': code('choice', metrics.get('tools_model_choice')),
        'tool': code('tool', metrics.get('tools_selected_tool')),
        'result': code('result', metrics.get('tools_model_kind')),
        'validation': code('validation', metrics.get('tools_validation')),
        'generation_error': code('generation_error', generation_error),
        'input_tokens': bounded(metrics.get('input_tokens'), 1536),
        'history_turns': bounded(metrics.get('history_turns_available'), MAX_STORED_TURNS),
        'web_attempted': int(metrics['tools_web_attempted']) if type(metrics.get('tools_web_attempted')) is bool else -1}


def record_tools_technical(work, value):
    """Atomic single private receipt, bounded independently from discarded stdout."""
    import stat
    from resident_runtime import private_directory
    if (not isinstance(value, dict) or set(value) != TOOLS_TECHNICAL_KEYS
            or any(type(number) is not int or not -1 <= number < 10**12 for number in value.values())
            or value['version'] != 1 or value['phase'] not in (1, 2)
            or not 1 <= value['sequence'] <= 64 or not 0 < value['at_ms'] < 10**12
            or any(not 0 <= value[key] <= len(enum) for key, enum in TOOLS_TECHNICAL_ENUMS.items())
            or not -1 <= value['input_tokens'] <= 1536 or not -1 <= value['history_turns'] <= MAX_STORED_TURNS
            or not -1 <= value['web_attempted'] <= 1 or not -1 <= value['elapsed_ms'] <= 600000):
        raise ValueError('Invalid tools technical receipt')
    directory = private_directory(work); inode = None
    temporary, name = 'voice_tools_health.new', 'voice_tools_health.json'
    try:
        try: old = os.stat(name, dir_fd=directory, follow_symlinks=False)
        except FileNotFoundError: pass
        else:
            if (not stat.S_ISREG(old.st_mode) or old.st_uid != os.geteuid()
                    or stat.S_IMODE(old.st_mode) != 0o600 or old.st_nlink != 1 or old.st_size > 4096):
                raise ValueError('Unknown tools technical file')
        raw = json.dumps(value, allow_nan=False).encode('ascii')
        if len(raw) > 4096: raise ValueError('Oversized tools technical receipt')
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=directory)
        with os.fdopen(fd, 'wb') as stream:
            inode = os.fstat(stream.fileno()).st_ino
            stream.write(raw); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, name, src_dir_fd=directory, dst_dir_fd=directory); inode = None; os.fsync(directory)
    finally:
        try:
            if inode is not None and os.stat(temporary, dir_fd=directory, follow_symlinks=False).st_ino == inode:
                os.unlink(temporary, dir_fd=directory)
        finally: os.close(directory)


def observe_tools_technical(model, sequence, speech_end, phase):
    # Only serve() assigns this internal path. Never derived from wire, model or
    # human text. Missing diagnostics must not break an otherwise valid request.
    work = getattr(model, 'technical_work', None)
    if not isinstance(work, Path): return
    try:
        record_tools_technical(work, tools_technical(model.context_metrics, sequence, phase,
            now_ms() - speech_end, model.error))
    except (OSError, ValueError, TypeError): pass


def send_event(conn, event):
    data = json.dumps(event, ensure_ascii=True).encode()
    if len(data) > 65536: raise ValueError('Oversized event')
    conn.sendall(struct.pack('!I', len(data)) + data)


def read_hello(conn, deadline):
    """Absolute authentication limit, not a fresh timeout for every byte."""
    def exact(length):
        data = bytearray()
        while len(data) < length:
            remaining = deadline - now_ms()
            if remaining <= 0: raise TimeoutError('Authentication deadline')
            conn.settimeout(min(4, remaining / 1000))
            chunk = conn.recv(length - len(data))
            if not chunk: raise EOFError()
            data.extend(chunk)
        return bytes(data)
    kind, size = struct.unpack('!BI', exact(5))
    if kind != 1 or not 0 < size <= 131072: raise ValueError('Invalid conversation hello')
    hello = json.loads(exact(size))
    if not isinstance(hello, dict): raise ValueError('Invalid conversation authentication')
    return hello


class FramePump:
    def __init__(self, conn, continuous_context_controls=False):
        self.conn, self.buffer = conn, bytearray()
        self.continuous_context_controls = continuous_context_controls
        conn.settimeout(.05)

    def poll(self):
        try: data = self.conn.recv(4096)
        except (socket.timeout, ssl.SSLWantReadError): return []
        if not data: raise EOFError()
        self.buffer.extend(data)
        if len(self.buffer) > (135168 if self.continuous_context_controls else 32768): raise ValueError('Excessive buffered PCM')
        frames = []
        while len(self.buffer) >= 5:
            kind, length = struct.unpack('!BI', self.buffer[:5])
            maximum = 131072 if kind == 5 and self.continuous_context_controls else 4096
            if length > maximum: raise ValueError('Oversized frame')
            if len(self.buffer) < length + 5: break
            frames.append((kind, bytes(self.buffer[5:5 + length])))
            del self.buffer[:5 + length]
        return frames


class AudioFreshness:
    """Invalid PCM is NEVER decoded or retimestamped. A short gap is not EOF.

    Discard a partial request and require fresh wake/button activation. Persistent
    clock failure still closes after 5s; no transport/model restart or action retry.
    """
    def __init__(self):
        self.last = 0; self.gap_since = None; self.changed = False
        self.status = self.reason = self.rejected = self.gaps = 0
        self.max_lag = self.max_future = self.max_backward = 0

    def expired(self, now):
        return self.gap_since is not None and now-self.gap_since >= 5000

    def clear_gap(self):
        # Mic turns off (thinking/speaking): any transient gap is no longer a
        # "persistent clock failure", so drop it before the next listening window.
        self.gap_since = None

    def accept(self, stamp, now):
        self.changed = False
        if self.expired(now): return False
        lag, future, backward = max(0,now-stamp), max(0,stamp-now), max(0,self.last-stamp)
        reason = 2 if future>100 else 1 if lag>3000 else 3 if backward else 0
        if reason:
            self.rejected = min(10**12,self.rejected+1)
            self.max_lag=max(self.max_lag,min(10**12,lag)); self.max_future=max(self.max_future,min(10**12,future))
            self.max_backward=max(self.max_backward,min(10**12,backward)); self.reason=reason; self.status=1
            if self.gap_since is None:
                self.gap_since=now; self.gaps=min(10**12,self.gaps+1); self.changed=True
            return False
        if self.gap_since is not None:
            self.changed=True; self.status=2
        self.gap_since=None; self.last=stamp
        return True

    def snapshot(self, now):
        return dict(version=1,at_ms=now,status=3 if self.expired(now) else self.status,reason=self.reason,
            rejected=self.rejected,gaps=self.gaps,max_lag_ms=self.max_lag,max_future_ms=self.max_future,max_backward_ms=self.max_backward)


def observe_audio_technical(model, freshness, now):
    """Fixed bounded numeric receipt, not a transcript, audio hash or permission."""
    import stat
    work=getattr(model,'technical_work',None)
    if not isinstance(work,Path): return
    directory=None; inode=None; temporary='voice_audio_health.new'
    try:
        from resident_runtime import private_directory
        directory=private_directory(work); name='voice_audio_health.json'
        try: info=os.stat(name,dir_fd=directory,follow_symlinks=False)
        except FileNotFoundError: pass
        else:
            if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o600: return
        raw=json.dumps(freshness.snapshot(now),allow_nan=False).encode('ascii')
        fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=directory)
        inode=os.fstat(fd).st_ino
        with os.fdopen(fd,'wb') as output: output.write(raw)
        os.replace(temporary,name,src_dir_fd=directory,dst_dir_fd=directory)
    except (OSError,ValueError): pass  # Diagnostics cannot authorize, close or restart a session.
    finally:
        if directory is not None:
            try:
                if inode is not None and os.stat(temporary,dir_fd=directory,follow_symlinks=False).st_ino==inode:
                    os.unlink(temporary,dir_fd=directory)
            except FileNotFoundError: pass
            os.close(directory)


class AudioTimeline:
    def __init__(self):
        self.total = 0
        self.frames = deque(maxlen=800)

    def add(self, end_ms, pcm):
        self.total += len(pcm) // 2
        self.frames.append((self.total, end_ms))

    def speech_end(self, result):
        alignment = result.get('result')
        if not alignment: raise ValueError('ASR alignment missing')
        sample = int(float(alignment[-1]['end']) * 16000)
        if not 0 < sample <= self.total: raise ValueError('ASR alignment outside audio')
        for frame_end, end_ms in self.frames:
            if frame_end >= sample:
                # Conservative margin for ASR word-boundary estimation. This is
                # still an estimate, not an independently verified human end.
                return int(end_ms - (frame_end - sample) / 16 - 200)
        raise ValueError('ASR alignment expired')


class ShortMemory:
    def __init__(self, clock=now_ms):
        self.clock, self.pairs, self.last_human = clock, [], 0

    def messages(self, text, activity_ms=None):
        if self.clock() - self.last_human >= 3600000: self.pairs.clear()
        self.last_human = self.clock() if activity_ms is None else activity_ms
        # Typed remote data is NEVER included in native action-selection chat.
        return [{'role': 'system', 'content': SYSTEM}] + [{'role': m['role'], 'content': m['content']}
            for pair in self.pairs for m in pair if not m.get('web_followup')] + [
            {'role': 'user', 'content': text}]

    def commit(self, text, answer):
        self.pairs.append([{'role': 'user', 'content': text}, {'role': 'assistant', 'content': answer}])
        self.trim()

    def unanswered(self, text):
        self.pairs.append([{'role': 'user', 'content': text}])
        self.trim()

    def commit_web(self, text, answer):
        from worker_answers import validate_service_answer
        from web_search import current_query
        current_query(text)
        validate_service_answer(answer)
        self.pairs.append([{'role': 'user', 'content': text, 'web_answer': answer}])
        self.trim()

    def commit_followup(self, text, answer):
        conversational_reply({'content': answer}, 'stop')
        self.pairs.append([{'role': 'user', 'content': text},
                           {'role': 'assistant', 'content': answer, 'web_followup': True}])
        self.trim()

    def trim(self):
        units = sum(text_units(m['content']) + text_units(m.get('web_answer', '')) for group in self.pairs for m in group)
        while len(self.pairs) > MAX_STORED_TURNS or units > MAX_TEXT_UNITS:
            units -= sum(text_units(m['content']) + text_units(m.get('web_answer', '')) for m in self.pairs.pop(0))

    def restore(self, context):
        """Authenticated, bounded app RAM snapshot; never a prompt/system message."""
        def invalid(): raise ValueError('Invalid context snapshot')
        if not isinstance(context, dict) or set(context) != {'version', 'id', 'last_human_ms', 'turns'}: invalid()
        identity, last, turns = context['id'], context['last_human_ms'], context['turns']
        if type(context['version']) is not int or context['version'] != 1: invalid()
        if not isinstance(identity, str) or not re.fullmatch('[0-9a-f]{32}', identity): invalid()
        if type(last) is not int or not isinstance(turns, list) or len(turns) > MAX_STORED_TURNS: invalid()
        now = self.clock()
        if not 0 <= last <= now + 100 or bool(turns) != bool(last): invalid()
        if last and now - last >= 3600000: raise ContextExpired('Context expired')
        restored, identifiers, previous, units = [], set(), 0, 0
        for turn in turns:
            if not isinstance(turn, dict) or not {'id', 'user', 'human_ms'} <= set(turn) <= {'id', 'user', 'human_ms', 'assistant', 'web_answer', 'web_followup'}: invalid()
            if sum(key in turn for key in ('assistant', 'web_answer', 'web_followup')) > 1: invalid()
            turn_id, user, at = turn['id'], turn['user'], turn['human_ms']
            if not isinstance(turn_id, str) or not re.fullmatch('[0-9a-f]{32}', turn_id) or turn_id in identifiers: invalid()
            if not isinstance(user, str) or not user.strip() or text_units(user) > 250 or '<|' in user: invalid()
            if type(at) is not int or not 0 < at <= last or at < previous: invalid()
            identifiers.add(turn_id); previous = at
            group = [{'role': 'user', 'content': user}]
            if 'web_answer' in turn:
                from worker_answers import validate_service_answer
                from web_search import current_query
                current_query(user)
                validate_service_answer(turn['web_answer'])
                group[0]['web_answer'] = turn['web_answer']
            if 'assistant' in turn or 'web_followup' in turn:
                answer = turn.get('assistant', turn.get('web_followup'))
                if not isinstance(answer, str) or conversational_reply({'content': answer}, 'stop') != answer: invalid()
                if text_units(answer) > 700: invalid()
                group.append(dict(role='assistant', content=answer, **({'web_followup': True} if 'web_followup' in turn else {})))
            units += sum(text_units(m['content']) + text_units(m.get('web_answer', '')) for m in group)
            if units > MAX_TEXT_UNITS: invalid()
            restored.append(group)
        if turns and previous != last: invalid()
        self.pairs, self.last_human = restored, last
        return identity


class ContextExpired(ValueError):
    pass


class OwnedInference:
    """May stop only the dedicated non-root server belonging to this workdir."""
    def __init__(self, key_path):
        self.key_path = Path(key_path)
        self.pid = int(self.key_path.with_name('server.pid').read_text())
        if self.pid <= 1: raise ValueError('Invalid inference PID')
        self.start_ticks = self.identity()
        if self.start_ticks is None: raise ValueError('Inference ownership not verified')

    def identity(self):
        try:
            proc = Path('/proc') / str(self.pid)
            status = (proc / 'status').read_text()
            uid = next(line.split()[1] for line in status.splitlines() if line.startswith('Uid:'))
            args = (proc / 'cmdline').read_bytes().split(b'\x00')
            expected = ('/data/local/linux/debian-final/tmp/' + self.key_path.parent.name + '/key').encode()
            stat = (proc / 'stat').read_text().rsplit(') ', 1)[1].split()
            if (uid != '10218' or stat[0] == 'Z' or expected not in args
                    or not args[0].endswith(b'/llama-b11669/llama-server')
                    or b'/data/local/doha-inference/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf' not in args):
                return None
            return stat[19]
        except (OSError, ValueError, StopIteration, IndexError): return None

    def alive(self):
        return self.identity() == self.start_ticks

    def stop(self):
        if not self.alive(): return
        try: os.kill(self.pid, signal.SIGTERM)
        except ProcessLookupError: return
        deadline = time.monotonic() + .6
        while self.alive() and time.monotonic() < deadline: time.sleep(.02)
        if self.alive():
            try: os.kill(self.pid, signal.SIGKILL)
            except ProcessLookupError: pass


class ConversationModel:
    response_start_ms = 30000
    compute_ms = 22000
    preparation_ms = 30000  # Public cold initialization, never a human-turn deadline.
    def __init__(self, key_path, owned=None, clock=now_ms):
        self.key = Path(key_path).read_text().strip()
        if len(self.key) < 32: raise ValueError('Missing inference credential')
        self.owned = owned or OwnedInference(key_path)
        self.clock, self.memory = clock, ShortMemory(clock)
        self.cancelled, self.done = threading.Event(), threading.Event()
        self.http = None
        self.result = self.error = self.thread = None
        self.context_metrics = {}
        self._public_preparing = False
        self.public_prefix_error = None

    def post(self, path, body, deadline):
        if self.cancelled.is_set() or self.clock() >= deadline: raise TimeoutError()
        connection = http.client.HTTPConnection('127.0.0.1', 11435,
            timeout=max(.1, min(self.compute_ms / 1000, (deadline - self.clock()) / 1000)))
        self.http = connection
        try:
            connection.request('POST', path, json.dumps(body, ensure_ascii=True).encode(),
                {'Authorization': 'Bearer ' + self.key, 'Content-Type': 'application/json'})
            response = connection.getresponse()
            raw = response.read(32769)
            if response.status != 200 or len(raw) > 32768: raise ValueError('Invalid local inference response')
            if self.cancelled.is_set() or self.clock() >= deadline: raise TimeoutError()
            return json.loads(raw)
        finally:
            connection.close()
            if self.http is connection: self.http = None

    @staticmethod
    def payload(messages, maximum=96):
        return {'messages': messages, 'max_tokens': maximum, 'stream': False, 'cache_prompt': True,
                'temperature': 0, 'seed': 42, 'chat_template_kwargs': {'enable_thinking': False}}

    def prepare(self):
        deadline = self.clock() + self.preparation_ms
        self.post('/v1/chat/completions', self.payload([
            {'role': 'system', 'content': SYSTEM}, {'role': 'user', 'content': 'Hola.'}], 1), deadline)

    def interpret_result(self, message, finish_reason, request):
        # The installed conversation path remains action-free. Experimental
        # proposal-only subclasses can override parsing without owning an executor.
        return conversational_reply(message, finish_reason)

    def turn_deadline(self, speech_end, session_end):
        return min(self.clock() + self.compute_ms, speech_end + self.response_start_ms - 3500, session_end - 1500)

    def start(self, text, speech_end, session_end):
        self.finish_public_prefix()
        if self.thread is not None and self.thread.is_alive(): raise ValueError('Concurrent turn')
        self.cancelled.clear(); self.done.clear(); self.result = self.error = None
        self.context_metrics = {}
        deadline = self.turn_deadline(speech_end, session_end)
        messages = self.memory.messages(text, activity_ms=speech_end)
        available_pairs = sum(len(group) == 2 for group in self.memory.pairs)
        available_turns = len(self.memory.pairs)
        def work():
            try:
                while True:
                    rendered = self.post('/apply-template', dict(self.payload(messages), add_generation_prompt=True), deadline)['prompt']
                    tokens = self.post('/tokenize', {'content': rendered, 'add_special': True, 'parse_special': True}, deadline)['tokens']
                    if len(tokens) <= 1536: break
                    if len(messages) <= 2: raise ValueError('Input exceeds context budget')
                    # Unanswered human turns have no fabricated assistant reply.
                    count = 2 if len(messages) > 3 and messages[2]['role'] == 'assistant' else 1
                    del messages[1:1 + count]
                used_pairs = sum(m['role'] == 'assistant' for m in messages[1:-1])
                used_turns = sum(m['role'] == 'user' for m in messages[1:-1])
                self.context_metrics = {
                    'history_pairs_available': available_pairs,
                    'history_pairs_used': used_pairs,
                    'history_pairs_trimmed': available_pairs - used_pairs,
                    'history_turns_available': available_turns,
                    'history_turns_used': used_turns,
                    'history_turns_trimmed': available_turns - used_turns,
                    'input_tokens': len(tokens), 'input_token_budget': 1536,
                }
                result = self.post('/v1/chat/completions', self.payload(messages), deadline)
                choice = result['choices'][0]; message = choice['message']
                answer = self.interpret_result(message, choice.get('finish_reason'), text)
                if not self.cancelled.is_set(): self.result = answer
            except Exception as error:
                self.error = type(error).__name__  # Never log input/output/credentials.
            finally: self.done.set()
        self.thread = threading.Thread(target=work, daemon=True, name='conversation-inference')
        self.thread.start()
        return deadline

    def finish_public_prefix(self):
        if not getattr(self, '_public_preparing', False): return
        # Public maintenance yields to a NEW human request, never retries it.
        if not self.cancel_generation():
            from continuous_voice import ContinuousVoiceFault
            raise ContinuousVoiceFault('voice_cancellation_stuck')
        self._public_preparing = False

    def warm_public_prefix(self):
        """Only AFTER verified cache erasure/context reset; fixed public Hola only.

        One cancellable generation thread, no transcript, tool grant or new model.
        Does not block the voice/context event loop or renew any human deadline.
        """
        self.finish_public_prefix()
        if self.thread is not None and self.thread.is_alive(): raise ValueError('Concurrent public preparation')
        self.cancelled.clear(); self.public_prefix_error = None; self._public_preparing = True
        def work():
            try: self.prepare()
            except Exception as error: self.public_prefix_error = type(error).__name__
        self.thread = threading.Thread(target=work, daemon=True, name='public-tools-prefix')
        self.thread.start()

    def cancel_generation(self):
        """Cancel only current generation; caller must stop owner if it stays stuck."""
        self.cancelled.set()
        connection = self.http
        if connection is not None:
            try:
                if connection.sock is not None: connection.sock.shutdown(socket.SHUT_RDWR)
                connection.close()
            except OSError: pass
        if self.thread is not None: self.thread.join(timeout=.5)
        self.result = self.error = None
        return self.thread is None or not self.thread.is_alive()

    def cancel(self):
        self.cancelled.set()
        self.owned.stop()
        self.cancel_generation()


def session(conn, detector, model, limit, resource_check, source='microphone', context_id=None, entry=None, timer_trial=False, assistant_tools=False, guided_agenda=False, continuous_voice=False, alarm_lifecycle=False, confirmed_volume=False, reminder_read=False, reminder_clarification=False, reminder_deletion=False):
    from vosk import KaldiRecognizer, SetLogLevel
    from continuous_voice import (ContextReset, ContinuousVoiceFault, packet as context_packet, ACTION_WINDOW_MS, CHECKPOINT_TURNS,
        MAX_EPOCH_TURNS, STREAM_START_MS, STREAM_IDLE_MS, VOICE_POLICY, RESET_POLICY)
    if detector is None or limit not in ((0,) if continuous_voice else (90, 300)) or source not in {'microphone', 'synthetic_pcm'}:
        raise ValueError('Invalid conversation session')
    if guided_agenda and not assistant_tools: raise ValueError('Guided agenda requires tools')
    if confirmed_volume and not guided_agenda: raise ValueError('Confirmed volume requires guided tools')
    if reminder_read and not guided_agenda: raise ValueError('Reminder reading requires guided tools')
    if reminder_clarification and not guided_agenda: raise ValueError('Reminder clarification requires guided tools')
    if reminder_deletion and not (reminder_read and reminder_clarification and alarm_lifecycle): raise ValueError('Reminder deletion requires coordinated reminders')
    if continuous_voice and (not guided_agenda or timer_trial): raise ValueError('Continuous voice requires guided tools')
    if alarm_lifecycle and not continuous_voice: raise ValueError('Alarm lifecycle requires coordinated continuous voice')
    SetLogLevel(-1)
    response_start_ms = 60000 if assistant_tools else 30000
    pump = FramePump(conn, continuous_context_controls=True) if continuous_voice else FramePump(conn)
    session_id = uuid.uuid4().hex
    context_id = context_id or uuid.uuid4().hex
    def emit(event):
        send_event(conn, dict(event, session_id=session_id, context_id=context_id))
    began = checked = now_ms(); session_end = began + limit * 1000
    last_input, stream_started, pending_reset = began, False, None
    checkpoint_followup = False
    accept_audio_after = 0
    audio_yielded = False
    state, recognizer, timeline = 'wake', None, None
    preroll = deque(maxlen=75)
    activation = last_audio = 0
    audio_freshness = AudioFreshness() if continuous_voice else None
    turn = None
    sequence = 0
    flow = ConversationFlow()
    timers = None
    if assistant_tools:
        from assistant_tools_trial import AssistantToolsTrial
        timers = AssistantToolsTrial(now_ms, guided_agenda=guided_agenda, maximum_sequence=MAX_EPOCH_TURNS if continuous_voice else 24,alarm_lifecycle=alarm_lifecycle,confirmed_volume=confirmed_volume,reminder_read=reminder_read,reminder_clarification=reminder_clarification,reminder_deletion=reminder_deletion)
    elif timer_trial:
        from timer_trial import TimerTrial
        timers = TimerTrial(now_ms)

    def reset(continue_chat=False, audio_resync=False):
        nonlocal state, recognizer, timeline, turn
        recognizer = timeline = turn = None
        preroll.clear(); detector.reset(); state = 'wake'
        if continue_chat:
            activate('followup', delay_ms=600)
        else:
            flow.reset()
            if timers is not None: timers.cancel()
            if entry is not None: entry.cancel_pending()
            emit({'type': 'state', 'state': 'wake', **({'reason':'audio_clock_resync'} if audio_resync else {})})

    def activate(trigger, delay_ms=0):
        nonlocal state, recognizer, timeline, activation
        if not continuous_voice and session_end - now_ms() < 10000:
            emit({'type': 'state', 'state': 'wake'}); return
        state, activation = 'listen', now_ms() + delay_ms
        recognizer = KaldiRecognizer(detector.model, 16000); recognizer.SetWords(True)
        timeline = AudioTimeline()
        for stamp, pcm in preroll:
            timeline.add(stamp, pcm); recognizer.AcceptWaveform(pcm)
        preroll.clear()
        emit({'type': 'state', 'state': 'command', 'trigger': trigger})

    def say(answer, close_reason='', offered_more=None):
        nonlocal state
        if audio_freshness is not None: audio_freshness.clear_gap()
        turn['answer'], turn['close_reason'] = answer, close_reason
        state = 'speech'
        turn['offered_more'] = not bool(close_reason) if offered_more is None else offered_more
        emit({'type': 'say', 'text': answer, 'id': turn['id'], 'turn_id': turn['id'],
            'turn_seq': turn['seq'], 'speech_end_ms': turn['speech_end'],
             'deadline_ms': turn['deadline_ms'], 'actions_executed': turn.get('actions_executed', 0),
              'timer_control': turn.get('timer_control', ''),
                 **({'reminder_read':turn['reminder_read']} if turn.get('timer_control')=='reminder_list' else {}),
                 **({'reminder_delete':turn['reminder_delete']} if turn.get('reminder_delete') is not None else {}),
               **({'agenda_kind': turn['agenda_kind']} if turn.get('timer_control') == 'agenda_question' else {}),
               **({'sources': turn.get('sources', [])} if assistant_tools else {}),
              **({'web_answer': turn['web_answer']} if assistant_tools and turn.get('timer_control') == 'web'
                 and turn.get('web_answer') is not None else {}),
             'close_reason': close_reason, 'offered_more': turn['offered_more']})

    def timer_response(response):
        nonlocal state
        if response.get('event'): emit(response['event'])
        if response.get('executing'):
            state = 'executing'; return
        turn['timer_control'] = response.get('timer_control', '')
        turn['agenda_kind'] = response.get('agenda_kind', '')
        turn['sources'] = response.get('sources', [])
        turn['web_answer'] = response.get('web_answer')
        turn['reminder_read'] = response.get('reminder_read')
        turn['reminder_delete'] = response.get('reminder_delete')
        turn['actions_executed'] = response.get('actions_executed', 0)
        say(response['text'], close_reason=response.get('close_reason', ''), offered_more=response.get('offered_more', False))

    def closed():
        emit({'type': 'conversation_closed', 'turn_id': turn['id'], 'turn_seq': turn['seq'],
            'speech_end_ms': turn['speech_end'], 'deadline_ms': turn['deadline_ms'],
            'reason': turn['close_reason'], **({'keep_listening': True} if continuous_voice else {})})

    def request_context(reason, followup=False):
        nonlocal state, recognizer, timeline, turn, entry, pending_reset, checkpoint_followup
        if pending_reset is not None: raise ValueError('Continuous context already pending')
        if reason != 'turn_checkpoint' and model.purge_cache() is not True: raise ContinuousVoiceFault('voice_cache_clear_failed')
        pending_reset = ContextReset(session_id, context_id, reason, now_ms,
            len(model.memory.pairs) if reason=='turn_checkpoint' else 0, model.memory.last_human if reason=='turn_checkpoint' else 0)
        timers.close(); flow.reset(); recognizer = timeline = turn = entry = None
        preroll.clear(); detector.reset(); model.memory = ShortMemory(now_ms)
        model.result = model.error = None; model.context_metrics.clear()
        checkpoint_followup = followup; state = 'context'
        emit(pending_reset.request())

    def completed_turn(continue_chat):
        if (continuous_voice and sequence >= CHECKPOINT_TURNS and not timers.pending_dialogue
                and (entry is None or entry.stage == 'active')):
            request_context('turn_checkpoint', followup=continue_chat)
        else: reset(continue_chat=continue_chat)

    emit({'type': 'ready', 'protocol': 13 if alarm_lifecycle else 12 if continuous_voice else 11 if guided_agenda else 10 if assistant_tools else 8 if timer_trial else PROTOCOL, 'conversation_only': not (timer_trial or assistant_tools), 'wake_only': False,
                      'wake_enabled': True, 'ai_enabled': True, 'actions_enabled': timer_trial or assistant_tools,
                      **({'assistant_tools': True} if assistant_tools else {}),
                         'timer_trial': timer_trial, 'allowed_tools': (['create_timer', 'create_alarm', 'create_reminder', 'search_web', 'set_phone_volume', 'control_lights'] + (['list_reminders'] if reminder_read else []) + (['delete_reminder'] if reminder_deletion else [])) if assistant_tools else ['create_timer'] if timer_trial else [],
                      'timer_policy': 'request_authorized_close' if timer_trial else '',
                     'clock': 'boottime_ms', 'server_time_ms': now_ms(), 'seconds': limit,
                      'response_start_ms': response_start_ms, 'input_source': source, 'context_version': 1,
                          **({'web_response_mode': 'worker_first_v3_spanish', 'phone_volume_policy': 'confirmed_media_v3' if confirmed_volume else 'absolute_media_v2_turn60' if guided_agenda else 'absolute_media_v1', 'light_control_policy': 'voice_light_command_v1'} if assistant_tools else {}),
                         **({'reminder_read_policy':'android_reminder_read_v1'} if reminder_read else {}),
                          **({'reminder_creation_policy':'confirmed_reminder_intent_v1'} if reminder_clarification else {}),
                          **({'reminder_delete_policy':'confirmed_reminder_delete_v1'} if reminder_deletion else {}),
                         **({'agenda_dialogue_policy': 'guided_agenda_v1', 'action_completion_policy': 'continue_v1'} if guided_agenda else {}),
                         **({'alarm_lifecycle_policy':'weekly_alarm_lifecycle_v1'} if alarm_lifecycle else {}),
                        **({'voice_lifetime_policy': VOICE_POLICY, 'dialogue_reset_policy': RESET_POLICY,
                            'action_window_ms': ACTION_WINDOW_MS, 'stream_start_ms': STREAM_START_MS, 'stream_idle_ms': STREAM_IDLE_MS} if continuous_voice else {}),
                      'restored_turns': len(model.memory.pairs), 'continuous_chat': True,
                       'listen_idle_ms': LISTEN_IDLE_MS, 'followup_offer': True, 'closing_farewell': True,
                       'voice_entry': entry is not None, 'speech_interrupt': True})
    try:
        while continuous_voice or now_ms() < session_end:
            current = now_ms()
            if current - checked >= 1000:
                checked = current
                if not resource_check() or not model.owned.alive(): raise RuntimeError('Conversation resource/server guard')
                if continuous_voice and entry is not None: entry.prune()
            if continuous_voice:
                # Only judge the audio clock while frames are actually expected.
                # During thinking/speech the mic is legitimately off, so a stale gap
                # from a briefly-blocked loop (e.g. the weather fetch) must not be
                # read as a persistent clock failure.
                if state in ('wake','listen') and audio_freshness.expired(current):
                    observe_audio_technical(model,audio_freshness,current)
                    raise ValueError('Persistent invalid audio clock')
                if current-last_input >= (STREAM_IDLE_MS if stream_started else STREAM_START_MS): raise ContinuousVoiceFault('voice_stream_lost')
                if pending_reset is not None and current >= pending_reset.before: raise ContinuousVoiceFault('voice_context_timeout')
                if (pending_reset is None and turn is None and model.memory.last_human
                        and current-model.memory.last_human >= 3600000): request_context('human_inactivity')
            # Inactivity pauses reception; it does not delete or split a chat.
            if state == 'listen' and current - activation >= LISTEN_IDLE_MS: reset()
            if turn is not None:
                if state == 'thinking' and current >= turn['compute_end']:
                    if assistant_tools: observe_tools_technical(model, sequence, turn['speech_end'], 2)
                    if continuous_voice:
                        # No executor was dispatched in thinking. Cancel the owned
                        # generation; never retry it or recover a stuck cancellation.
                        if not model.cancel_generation(): raise ContinuousVoiceFault('voice_cancellation_stuck')
                        model.memory.unanswered(turn['text'])
                        emit({'type':'error','reason':'deadline','turn_id':turn['id'],'recoverable':True,'actions_executed':0})
                        flow.reset(); completed_turn(True); continue
                    emit({'type': 'error', 'reason': 'deadline', 'turn_id': turn['id']})
                    return  # finally stops this dedicated inference process.
                if state == 'thinking' and model.done.is_set():
                    if assistant_tools: observe_tools_technical(model, sequence, turn['speech_end'], 1)
                    print(json.dumps({'event': 'conversation_context', 'source': source,
                        'turn_seq': turn['seq'], **model.context_metrics}), flush=True)
                    if model.error or not model.result:
                        model.memory.unanswered(turn['text'])
                        emit({'type': 'error', 'reason': 'generation_failed', 'turn_id': turn['id'],
                              **({'recoverable':True,'actions_executed':0} if continuous_voice else {})})
                        flow.reset(); completed_turn(continuous_voice)
                    elif current < turn['deadline_ms'] - 1500:
                        try:
                            if timers is not None:
                                response = timers.model_result(model.result, turn, session_id, context_id, session_end)
                                if response.get('timer_control') or response.get('executing'):
                                    timer_response(response); continue
                                result = response['text']
                            else: result = model.result
                            answer = flow.with_offer(result)
                            conversational_reply({'content': answer}, 'stop')
                        except ValueError:
                            model.memory.unanswered(turn['text'])
                            emit({'type': 'error', 'reason': 'generation_failed', 'turn_id': turn['id'],
                                  **({'recoverable':True,'actions_executed':0} if continuous_voice else {})})
                            flow.reset(); completed_turn(continuous_voice); continue
                        say(answer)
                    else: return
                elif state == 'executing':
                    response = timers.timed_out()
                    if response is not None:
                        if current >= turn['deadline_ms'] - 1500: return
                        timer_response(response)
                elif state == 'speech' and current >= turn['deadline_ms'] and not turn.get('tts_started'):
                    return
                elif state == 'speech' and current > turn['deadline_ms'] + (60000 if turn.get('timer_control')=='reminder_list' or (turn.get('web_answer') or {}).get('mode') in {'service_answer', 'service_translation', 'service_excerpt'} else 20000): return
            for kind, data in pump.poll():
                if continuous_voice:
                    last_input = now_ms(); stream_started = True
                    if kind == 5:
                        if pending_reset is None: raise ValueError('Unsolicited continuous context')
                        snapshot, catalog, voice_entry = pending_reset.claim(context_packet(data))
                        warm_public = reminder_read and pending_reset.reason != 'turn_checkpoint'
                        memory = ShortMemory(now_ms); chosen = memory.restore(snapshot)
                        restored = restore_catalog(catalog)
                        if chosen in restored: raise ValueError('Duplicate continuous context')
                        old_session, old_context, nonce = session_id, context_id, pending_reset.nonce
                        model.memory = memory; context_id = chosen; session_id = uuid.uuid4().hex
                        entry = ConversationEntry(restored, chosen, now_ms) if voice_entry else None
                        sequence = 0; timers = AssistantToolsTrial(now_ms, guided_agenda=True, maximum_sequence=MAX_EPOCH_TURNS,alarm_lifecycle=alarm_lifecycle,confirmed_volume=confirmed_volume,reminder_read=reminder_read,reminder_clarification=reminder_clarification,reminder_deletion=reminder_deletion)
                        pending_reset = None
                        accept_audio_after = now_ms() + 600
                        emit({'type': 'voice_context_ready', 'previous_session_id': old_session, 'previous_context_id': old_context,
                            'reset_nonce': nonce, 'voice_entry': voice_entry, 'restored_turns': len(memory.pairs),
                            'voice_lifetime_policy': VOICE_POLICY, 'dialogue_reset_policy': RESET_POLICY,'audio_yielded':audio_yielded})
                        if audio_yielded:
                            state = 'yield'; emit({'type':'state','state':'audio_yield'})
                        else: reset(continue_chat=checkpoint_followup)
                        if warm_public and not audio_yielded: model.warm_public_prefix()
                        continue
                    if pending_reset is not None:
                        if kind == 3:
                            action = json.loads(data).get('action')
                            if action == 'stop': return 'voice_manual_stop'
                            if action == 'pause': return 'voice_suspended'
                            if action == 'audio_yield': audio_yielded = True; checkpoint_followup = False; continue
                            if action == 'audio_resume' and audio_yielded: audio_yielded = False; continue
                            if action not in {'heartbeat','tts_started','tts_done','tts_failed','tts_interrupted','timer_result','agenda_result','volume_result','light_result','alarm_selection','alarm_delete_result','reminder_read_result','reminder_delete_selection','reminder_delete_result','activate'}:
                                raise ValueError('Control during context handoff')
                        elif kind != 4: raise ValueError('Invalid continuous context frame')
                        continue  # Discard queued microphone/TTS tail, never transcribe during handoff.
                if kind == 3:
                    control = json.loads(data); action = control.get('action')
                    if action == 'stop': return 'voice_manual_stop' if continuous_voice else None
                    if action == 'pause': return 'voice_suspended' if continuous_voice else None
                    if continuous_voice and action == 'audio_yield':
                        if state == 'yield': continue
                        audio_yielded = True
                        if not model.cancel_generation(): raise ContinuousVoiceFault('voice_cancellation_stuck')
                        if turn is not None and not turn.get('administrative'): model.memory.unanswered(turn['text'])
                        timers.close(); timers = AssistantToolsTrial(now_ms,guided_agenda=True,maximum_sequence=MAX_EPOCH_TURNS,alarm_lifecycle=alarm_lifecycle,confirmed_volume=confirmed_volume,reminder_read=reminder_read,reminder_clarification=reminder_clarification,reminder_deletion=reminder_deletion)
                        turn = recognizer = timeline = None; preroll.clear(); detector.reset(); flow.reset()
                        model.result = model.error = None; model.context_metrics.clear(); state = 'yield'
                        emit({'type':'state','state':'audio_yield'}); continue
                    if continuous_voice and action == 'audio_resume':
                        if state != 'yield': raise ValueError('Unsolicited audio resume')
                        audio_yielded = False
                        request_context('audio_resume'); continue
                    if action == 'activate' and state == 'wake': activate('button')
                    if action in {'timer_result', 'agenda_result', 'volume_result', 'light_result','alarm_selection','alarm_delete_result','reminder_read_result','reminder_delete_selection','reminder_delete_result'} and timers is not None:
                        if action in {'reminder_delete_selection','reminder_delete_result'} and not reminder_deletion: raise ValueError('Reminder deletion not negotiated')
                        if action=='reminder_read_result' and not reminder_read: raise ValueError('Reminder reading not negotiated')
                        if action in {'alarm_selection','alarm_delete_result'} and not alarm_lifecycle: raise ValueError('Alarm policy not negotiated')
                        if action in {'agenda_result', 'volume_result', 'light_result'} and not assistant_tools: raise ValueError('Tools not enabled')
                        if state == 'executing':
                            response = timers.result(control, session_id, context_id)
                            if response is not None:
                                if now_ms() >= turn['deadline_ms'] - 1500: return
                                timer_response(response)
                        continue
                    if action in {'tts_started', 'tts_done', 'tts_failed', 'tts_interrupted'}:
                        if turn is None or control.get('turn_id') != turn['id'] or state != 'speech': continue
                        if action == 'tts_interrupted' and not turn.get('tts_started'): continue
                        if action == 'tts_started':
                            if turn.get('tts_started'): continue
                            at = control.get('at_ms')
                            if type(at) is not int or not turn['speech_end'] <= at <= min(now_ms() + 100, turn['deadline_ms']): return
                            turn['tts_started'] = True
                            print(json.dumps({'event': 'conversation_tts_start', 'source': source,
                                'turn_seq': turn['seq'], 'speech_end_to_callback_ms': at - turn['speech_end'],
                                'speech_end_estimated': True, 'useful_audio_verified': False,
                                    'actions_executed': turn.get('actions_executed', 0)}), flush=True)
                        elif action == 'tts_done' and turn.get('tts_started'):
                            if guided_agenda: timers.spoken(turn['id'], True)
                            if (turn.get('web_answer') or {}).get('mode') in {'service_answer', 'service_translation'}:
                                model.memory.commit_web(turn['text'], turn['web_answer']['answer_es'])
                            elif (turn.get('web_answer') or {}).get('mode') == 'service_excerpt':
                                model.memory.commit_web(turn['text'], turn['web_answer']['evidence'])
                            elif turn.get('timer_control') == 'web_followup':
                                model.memory.commit_followup(turn['text'], turn['answer'])
                            elif turn.get('timer_control'):
                                model.memory.unanswered(turn['text'])
                            elif not turn.get('administrative'): model.memory.commit(turn['text'], turn['answer'])
                            flow.spoken(turn['answer'], offered_more=turn['offered_more'])
                            print(json.dumps({'event': 'conversation_entry_notice' if turn.get('administrative') else 'conversation_memory_commit', 'source': source,
                                'turn_seq': turn['seq'], 'retained_turns': len(model.memory.pairs),
                                'retained_pairs': sum(len(g) == 2 for g in model.memory.pairs)}), flush=True)
                            if turn['close_reason']:
                                closed()
                                if not continuous_voice: return
                                request_context('dialogue_closed'); continue
                            completed_turn(True)
                        elif action in {'tts_failed', 'tts_interrupted'}:
                            if guided_agenda: timers.spoken(turn['id'], False)
                            if not turn.get('administrative'): model.memory.unanswered(turn['text'])
                            print(json.dumps({'event': 'conversation_memory_skipped', 'source': source,
                                'turn_seq': turn['seq'], 'reason': action,
                                'retained_turns': len(model.memory.pairs),
                                'retained_pairs': sum(len(g) == 2 for g in model.memory.pairs)}), flush=True)
                            if turn['close_reason']:
                                closed()
                                if not continuous_voice: return
                                request_context('dialogue_closed'); continue
                            if action == 'tts_interrupted':
                                flow.reset()  # An unheard offer never authorizes a later "no".
                                if turn.get('administrative') and entry is not None: entry.cancel_pending()
                            completed_turn(action == 'tts_interrupted')
                    continue
                if kind != 4 or not 8 < len(data) <= 648 or (len(data) - 8) % 2:
                    raise ValueError('Expected timestamped PCM16')
                stamp = struct.unpack('!Q', data[:8])[0]; pcm = data[8:]
                current = now_ms()
                if audio_freshness is not None:
                    # The mic is off (or the turn is already captured) outside
                    # wake/listen. Discard that tail here, before judging the clock,
                    # so a briefly-blocked loop (e.g. the weather fetch) can never
                    # leave a stale gap that later trips "invalid audio clock".
                    if state not in ('wake','listen'): continue
                    if not audio_freshness.accept(stamp,current):
                        if audio_freshness.changed:
                            observe_audio_technical(model,audio_freshness,current)
                            if state in {'wake','listen'}: reset(audio_resync=True)
                        continue  # Never decode/replay an invalid packet or use it to fill a form.
                    if audio_freshness.changed: observe_audio_technical(model,audio_freshness,current)
                elif stamp > current + 100 or current - stamp > 1000 or stamp < last_audio:
                    raise ValueError('Stale or incompatible audio clock')
                last_audio = stamp
                if continuous_voice and stamp < accept_audio_after: continue
                if state == 'wake':
                    preroll.append((stamp, pcm))
                    if detector.accept_pcm(pcm): activate('voice')
                elif state == 'listen':
                    if stamp < activation: continue  # Discard queued audio/TTS tail.
                    timeline.add(stamp, pcm)
                    if not recognizer.AcceptWaveform(pcm): continue
                    result = json.loads(recognizer.Result()); text = result.get('text', '').strip()
                    if text_units(text) > 250:
                        # Never trim off a trailing negation/qualification and
                        # authorize a shorter request the human did not make.
                        emit({'type': 'error', 'reason': 'request_too_long'}); return 'voice_request_rejected' if continuous_voice else None
                    if not text or words(text) == words(SETTINGS['wake_word']): continue
                    speech_end = timeline.speech_end(result)
                    head = text.split(maxsplit=1)
                    if len(head) == 2 and words(head[0]) == words(SETTINGS['wake_word']): text = head[1]
                    if now_ms() >= speech_end + response_start_ms - 3500: return
                    sequence += 1
                    if sequence > (MAX_EPOCH_TURNS if continuous_voice else 24): return 'voice_scope_limit' if continuous_voice else None
                    if continuous_voice: session_end = min(now_ms(), speech_end) + ACTION_WINDOW_MS
                    turn = {'id': uuid.uuid4().hex, 'seq': sequence, 'speech_end': speech_end,
                             'deadline_ms': speech_end + response_start_ms, 'text': text}
                    if assistant_tools:
                        turn.update(calendar_anchor_ms=int(time.time() * 1000), calendar_boot_ms=now_ms())
                    route = entry.route(text) if entry is not None else {'kind': 'model'}
                    if route['kind'] in {'new', 'resume'}:
                        old_id = context_id
                        model.memory = ShortMemory(now_ms) if route['kind'] == 'new' else entry.catalog[route['id']]
                        context_id = route['id']
                        emit({'type': 'context_selected', 'previous_context_id': old_id,
                            'selection': route['kind'], 'restored_turns': len(model.memory.pairs)})
                        print(json.dumps({'event': 'conversation_context_selected', 'source': source,
                            'selection': route['kind'], 'restored_turns': len(model.memory.pairs)}), flush=True)
                    turn['administrative'] = route['kind'] in {'notice', 'close'}
                    emit({'type': 'thinking', 'text': text, 'turn_id': turn['id'], 'turn_seq': sequence,
                           'speech_end_ms': speech_end, 'deadline_ms': turn['deadline_ms'],
                            **({key: turn[key] for key in ('calendar_anchor_ms', 'calendar_boot_ms')} if assistant_tools else {}),
                            **({'action_window_end_ms': session_end} if continuous_voice else {}),
                           'administrative': turn['administrative']})
                    recognizer = timeline = None; preroll.clear()
                    if route['kind'] == 'notice':
                        say(route['text'], offered_more=False); continue
                    if route['kind'] == 'resume' or route['kind'] == 'new' and plain_confirmation(text):
                        model.memory.last_human = speech_end
                        say('Retomamos esa charla. ¿En qué seguimos?' if route['kind'] == 'resume' else
                            'Empezamos una charla nueva. ¿De qué quieres hablar?', offered_more=False)
                        continue
                    closing = flow.closing_reason(text)
                    if route['kind'] == 'close': closing = route.get('reason', 'declined_more')
                    if closing:
                        if timers is not None: timers.cancel()
                        model.memory.last_human = speech_end
                        say(ConversationFlow.FAREWELL, closing)
                        recognizer = timeline = None; preroll.clear()
                        continue  # Close only after farewell receipt/failure; never invoke LFM.
                    if assistant_tools: model.anchor_wall, model.anchor_boot = turn['calendar_anchor_ms'], turn['calendar_boot_ms']
                    if guided_agenda:
                        try: response = timers.human(turn, session_id, context_id, session_end)
                        except ValueError:
                            if (not continuous_voice or timers.inflight or timers.timer.inflight
                                    or timers.volume.inflight or timers.deletion.grant): raise
                            # A stale/unheard form is not a malformed transport.
                            # Nothing was dispatched; revoke the form, never retry it.
                            timers.cancel(); flow.reset()
                            model.memory.messages(text, activity_ms=speech_end); model.memory.unanswered(text)
                            emit({'type':'error','reason':'invalid_request','turn_id':turn['id'],'recoverable':True,'actions_executed':0})
                            completed_turn(True); continue
                        if response is not None:
                            model.memory.messages(text, activity_ms=speech_end)
                            timer_response(response); continue
                    turn['compute_end'] = model.start(text, speech_end, session_end)
                    state = 'thinking'; recognizer = timeline = None; preroll.clear()
    except EOFError:
        return 'voice_peer_closed' if continuous_voice else None
    except ValueError as error:
        if continuous_voice:
            fault=ContinuousVoiceFault('voice_protocol_error'); fault.technical=protocol_technical(error)
            raise fault from None
        raise
    finally:
        if timers is not None: timers.close()
        model.cancel()


def plain_confirmation(text):
    return normalized(text).strip(' .,!¡?¿') == 'si'


def restore_catalog(value):
    if not isinstance(value, list) or len(value) > 4: raise ValueError('Invalid conversation catalog')
    catalog, identifiers = {}, set()
    for snapshot in value:
        memory = ShortMemory(now_ms)
        try: identity = memory.restore(snapshot)
        except ContextExpired: continue
        if identity in identifiers: raise ValueError('Duplicate catalog identity')
        identifiers.add(identity)
        catalog[identity] = memory
    return catalog


def serve(key_path, inference_key, detector, limit, cert, tls_key, source, resource_check, timer_trial=False, assistant_tools=False, resident=None, guided_agenda=False, continuous_voice=False, alarm_lifecycle=False, confirmed_volume=False, reminder_read=False, reminder_clarification=False, reminder_deletion=False):
    from continuous_voice import ContinuousVoiceFault, END_REASONS
    key = Path(key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing conversation credential')
    if guided_agenda and not assistant_tools: raise ValueError('Guided agenda requires tools')
    if confirmed_volume and not guided_agenda: raise ValueError('Confirmed volume requires guided tools')
    if reminder_read and not guided_agenda: raise ValueError('Reminder reading requires guided tools')
    if reminder_clarification and not guided_agenda: raise ValueError('Reminder clarification requires guided tools')
    if reminder_deletion and not (reminder_read and reminder_clarification and alarm_lifecycle): raise ValueError('Reminder deletion requires coordinated reminders')
    if alarm_lifecycle and not continuous_voice: raise ValueError('Alarm lifecycle requires coordinated continuous voice')
    if continuous_voice and (not guided_agenda or resident is None or not resident.continuous_model or limit != 0):
        raise ValueError('Continuous voice requires explicit continuous model owner')
    if resident is not None:
        from resident_runtime import ResidentRuntime
        if not isinstance(resident,ResidentRuntime) or timer_trial or not assistant_tools:
            raise ValueError('Resident mode requires explicit tools supervisor')
    if assistant_tools:
        if timer_trial: raise ValueError('Conflicting tool modes')
        from assistant_tools_model import AssistantToolsModel
        model_type = AssistantToolsModel
    elif timer_trial:
        from timer_trial_model import TimerTrialModel
        model_type = TimerTrialModel
    else: model_type = ConversationModel
    def make_model():
        value = model_type(inference_key,owned=resident.acquire()) if resident is not None else model_type(inference_key)
        if assistant_tools and resident is not None: value.technical_work = Path(inference_key).parent
        if guided_agenda: value.enable_agenda_dialogue()
        if alarm_lifecycle: value.enable_alarm_lifecycle()
        if confirmed_volume: value.enable_confirmed_volume()
        if reminder_read: value.enable_basic_assistant()
        if reminder_clarification: value.enable_reminder_clarification()
        if reminder_deletion: value.enable_reminder_deletion()
        if continuous_voice: value.purge_cache = lambda: resident.purge_cache(value)
        return value
    model = make_model()
    try:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert, tls_key)
        with socket.socket() as server:
            # Reuse TIME_WAIT only; bind/listener guards still refuse an active peer.
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server.bind(('127.0.0.1', 11436)); server.listen(1); server.settimeout(.5)
            print(json.dumps({'event': 'conversation_ready', 'protocol': 13 if alarm_lifecycle else 12 if continuous_voice else 11 if guided_agenda else 10 if assistant_tools else 8 if timer_trial else PROTOCOL, 'tls': True,
                              'input_source': source, 'microphone_opened': False, 'actions_enabled': timer_trial or assistant_tools}), flush=True)
            deadline = resident.deadline if resident is not None else now_ms() + 300000
            while deadline is None or now_ms() < deadline:
                if not resource_check() or not model.owned.alive(): raise RuntimeError('Conversation readiness guard')
                try: raw, _ = server.accept()
                except socket.timeout: continue
                with raw:
                    authenticated = False
                    authentication_end = now_ms() + 4000
                    if deadline is not None: authentication_end = min(authentication_end, deadline)
                    raw.settimeout(max(.001, (authentication_end - now_ms()) / 1000))
                    try:
                        with context.wrap_socket(raw, server_side=True) as conn:
                            hello = read_hello(conn, authentication_end)
                            if assistant_tools and hello.get('web_response_mode') != 'worker_first_v3_spanish': continue
                            if assistant_tools and hello.get('phone_volume_policy') != ('confirmed_media_v3' if confirmed_volume else 'absolute_media_v2_turn60' if guided_agenda else 'absolute_media_v1'): continue
                            if assistant_tools and hello.get('light_control_policy') != 'voice_light_command_v1': continue
                            if reminder_read and hello.get('reminder_read_policy')!='android_reminder_read_v1': continue
                            if reminder_clarification and hello.get('reminder_creation_policy')!='confirmed_reminder_intent_v1': continue
                            if reminder_deletion and hello.get('reminder_delete_policy')!='confirmed_reminder_delete_v1': continue
                            if guided_agenda and (hello.get('agenda_dialogue_policy') != 'guided_agenda_v1' or hello.get('action_completion_policy') != 'continue_v1'): continue
                            if continuous_voice and (hello.get('voice_lifetime_policy') != 'continuous_voice_v1' or hello.get('dialogue_reset_policy') != 'client_fresh_v1'): continue
                            if alarm_lifecycle and hello.get('alarm_lifecycle_policy')!='weekly_alarm_lifecycle_v1': continue
                            supplied = hello.get('token', '')
                            if (hello.get('protocol') != (13 if alarm_lifecycle else 12 if continuous_voice else 11 if guided_agenda else 10 if assistant_tools else 8 if timer_trial else PROTOCOL)
                                    or hello.get('mode') != ('assistant_tools' if assistant_tools else 'timer_trial' if timer_trial else 'conversation_only')
                                    or hello.get('source') != source or not isinstance(supplied, str)
                                    or not hmac.compare_digest(supplied.encode(), key.encode())):
                                continue
                            authenticated = True
                            if resident is not None and not continuous_voice and not resident.may_accept(limit):
                                # Lifetime admission is NOT a TLS failure. Authenticate
                                # first, reject before restoring any human context or
                                # starting a session; never extend the original deadline.
                                send_event(conn, {'type': 'error', 'reason': 'resident_window_ending'})
                                continue
                            try: context_id = model.memory.restore(hello.get('context'))
                            except ContextExpired:
                                send_event(conn, {'type': 'error', 'reason': 'context_expired'})
                                if continuous_voice:
                                    resident.monitor.reason = 'voice_context_expired'; resident.shutdown(); return
                                continue
                            entry = None
                            if hello.get('voice_entry') is True:
                                if model.memory.pairs: raise ValueError('Voice entry must start unselected')
                                catalog = restore_catalog(hello.get('catalog'))
                                if context_id in catalog: raise ValueError('New identity already exists')
                                entry = ConversationEntry(catalog, context_id, now_ms)
                            elif source == 'microphone':
                                raise ValueError('Microphone requires explicit voice entry')
                            print(json.dumps({'event': 'conversation_context_restored', 'source': source,
                                'restored_turns': len(model.memory.pairs),
                                'restored_answers': sum(len(g) == 2 for g in model.memory.pairs)}), flush=True)
                            try:
                                end_reason = session(conn, detector, model, limit, resource_check, source, context_id, entry, timer_trial, assistant_tools,
                                    **({'guided_agenda': True} if guided_agenda else {}), **({'continuous_voice': True} if continuous_voice else {}), **({'alarm_lifecycle':True} if alarm_lifecycle else {}), **({'confirmed_volume':True} if confirmed_volume else {}), **({'reminder_read':True} if reminder_read else {}), **({'reminder_clarification':True} if reminder_clarification else {}), **({'reminder_deletion':True} if reminder_deletion else {}))
                            except ContinuousVoiceFault as fault:
                                if not continuous_voice: raise
                                if getattr(fault,'technical',None) is not None:
                                    try: record_protocol_technical(inference_key,fault.technical)
                                    except (OSError,ValueError): pass
                                resident.monitor.reason = fault.reason; resident.shutdown(); return
                            if continuous_voice:
                                if resident.monitor.reason == 'running':
                                    resident.monitor.reason = end_reason if type(end_reason) is str and end_reason in END_REASONS else 'voice_session_failed'
                                resident.shutdown(); return  # Stop/disconnect revokes both voice and owned model, never reconnect automatically.
                            if resident is None: return
                            # Session.cancel revokes only this lease. Drop all
                            # process-side human state before accepting another.
                            resident.release(model); model = None; entry = None
                            detector.reset()
                            if not resident.check(): return
                            model = make_model()
                    except (OSError, EOFError, ValueError) as error:
                        # No credentials, frames or speech text in diagnostics.
                        if continuous_voice and authenticated:
                            if isinstance(error,ValueError):
                                try: record_protocol_technical(inference_key,protocol_technical(error))
                                except (OSError,ValueError): pass
                            if resident.monitor.reason == 'running':
                                resident.monitor.reason = 'voice_protocol_error' if isinstance(error,ValueError) else 'voice_peer_closed'
                            resident.shutdown(); return
                        if model is None or not model.owned.alive(): return
                        if resident is not None:
                            resident.release(model); model = None; entry = None
                            detector.reset()
                            if not resident.check(): return
                            model = make_model()
    finally:
        if model is not None:
            if resident is not None: resident.release(model)
            else: model.cancel()
