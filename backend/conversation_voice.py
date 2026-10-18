"""Bounded conversation-only voice actor. Never registers or executes tools."""
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
    text = parsed['text']; plain = normalized(text)
    patterns = [
        r'\b(?:he|hemos|ya|acabo de) (?:\w+ )?(?:creado|puesto|programado|activado|iniciado|cancelado|apagado|encendido|consultado|buscado|encontrado en internet)\b',
        r'\b(?:temporizador|alarma|recordatorio)\b.{0,45}\b(?:cread[oa]|puest[oa]|programad[oa]|activad[oa]|iniciad[oa]|cancelad[oa]|list[oa])\b',
        r'\b(?:te avisare|te recordare|sonara en|voy a (?:poner|crear|programar|activar|cancelar)|timer (?:set|started)|alarm (?:set|scheduled))\b',
    ]
    if any(re.search(pattern, plain) for pattern in patterns):
        raise ValueError('Unsupported action/current-data claim')
    return text


def now_ms():
    return int(time.clock_gettime(time.CLOCK_BOOTTIME) * 1000)


def send_event(conn, event):
    data = json.dumps(event, ensure_ascii=True).encode()
    if len(data) > 16384: raise ValueError('Oversized event')
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
    def __init__(self, conn):
        self.conn, self.buffer = conn, bytearray()
        conn.settimeout(.05)

    def poll(self):
        try: data = self.conn.recv(4096)
        except (socket.timeout, ssl.SSLWantReadError): return []
        if not data: raise EOFError()
        self.buffer.extend(data)
        if len(self.buffer) > 32768: raise ValueError('Excessive buffered PCM')
        frames = []
        while len(self.buffer) >= 5:
            kind, length = struct.unpack('!BI', self.buffer[:5])
            if length > 4096: raise ValueError('Oversized frame')
            if len(self.buffer) < length + 5: break
            frames.append((kind, bytes(self.buffer[5:5 + length])))
            del self.buffer[:5 + length]
        return frames


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
        return [{'role': 'system', 'content': SYSTEM}] + [copy.deepcopy(m) for pair in self.pairs for m in pair] + [
            {'role': 'user', 'content': text}]

    def commit(self, text, answer):
        self.pairs.append([{'role': 'user', 'content': text}, {'role': 'assistant', 'content': answer}])
        self.trim()

    def unanswered(self, text):
        self.pairs.append([{'role': 'user', 'content': text}])
        self.trim()

    def trim(self):
        units = sum(text_units(m['content']) for group in self.pairs for m in group)
        while len(self.pairs) > MAX_STORED_TURNS or units > MAX_TEXT_UNITS:
            units -= sum(text_units(m['content']) for m in self.pairs.pop(0))

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
            if not isinstance(turn, dict) or not {'id', 'user', 'human_ms'} <= set(turn) <= {'id', 'user', 'human_ms', 'assistant'}: invalid()
            turn_id, user, at = turn['id'], turn['user'], turn['human_ms']
            if not isinstance(turn_id, str) or not re.fullmatch('[0-9a-f]{32}', turn_id) or turn_id in identifiers: invalid()
            if not isinstance(user, str) or not user.strip() or text_units(user) > 250 or '<|' in user: invalid()
            if type(at) is not int or not 0 < at <= last or at < previous: invalid()
            identifiers.add(turn_id); previous = at
            group = [{'role': 'user', 'content': user}]
            if 'assistant' in turn:
                answer = turn['assistant']
                if not isinstance(answer, str) or conversational_reply({'content': answer}, 'stop') != answer: invalid()
                if text_units(answer) > 700: invalid()
                group.append({'role': 'assistant', 'content': answer})
            units += sum(text_units(m['content']) for m in group)
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
    def __init__(self, key_path, owned=None, clock=now_ms):
        self.key = Path(key_path).read_text().strip()
        if len(self.key) < 32: raise ValueError('Missing inference credential')
        self.owned = owned or OwnedInference(key_path)
        self.clock, self.memory = clock, ShortMemory(clock)
        self.cancelled, self.done = threading.Event(), threading.Event()
        self.http = None
        self.result = self.error = self.thread = None
        self.context_metrics = {}

    def post(self, path, body, deadline):
        if self.cancelled.is_set() or self.clock() >= deadline: raise TimeoutError()
        connection = http.client.HTTPConnection('127.0.0.1', 11435,
            timeout=max(.1, min(22, (deadline - self.clock()) / 1000)))
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
        deadline = self.clock() + 30000
        self.post('/v1/chat/completions', self.payload([
            {'role': 'system', 'content': SYSTEM}, {'role': 'user', 'content': 'Hola.'}], 1), deadline)

    def start(self, text, speech_end, session_end):
        if self.thread is not None and self.thread.is_alive(): raise ValueError('Concurrent turn')
        self.cancelled.clear(); self.done.clear(); self.result = self.error = None
        self.context_metrics = {}
        deadline = min(self.clock() + 22000, speech_end + 26500, session_end - 1500)
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
                answer = conversational_reply(message, choice.get('finish_reason'))
                if not self.cancelled.is_set(): self.result = answer
            except Exception as error:
                self.error = type(error).__name__  # Never log input/output/credentials.
            finally: self.done.set()
        self.thread = threading.Thread(target=work, daemon=True, name='conversation-inference')
        self.thread.start()
        return deadline

    def cancel(self):
        self.cancelled.set()
        self.owned.stop()
        connection = self.http
        if connection is not None:
            try:
                if connection.sock is not None: connection.sock.shutdown(socket.SHUT_RDWR)
                connection.close()
            except OSError: pass
        if self.thread is not None: self.thread.join(timeout=.5)


def session(conn, detector, model, limit, resource_check, source='microphone', context_id=None, entry=None):
    from vosk import KaldiRecognizer, SetLogLevel
    if detector is None or limit not in (90, 300) or source not in {'microphone', 'synthetic_pcm'}:
        raise ValueError('Invalid conversation session')
    SetLogLevel(-1)
    pump = FramePump(conn)
    session_id = uuid.uuid4().hex
    context_id = context_id or uuid.uuid4().hex
    def emit(event):
        send_event(conn, dict(event, session_id=session_id, context_id=context_id))
    began = checked = now_ms(); session_end = began + limit * 1000
    state, recognizer, timeline = 'wake', None, None
    preroll = deque(maxlen=75)
    activation = last_audio = 0
    turn = None
    sequence = 0
    flow = ConversationFlow()

    def reset(continue_chat=False):
        nonlocal state, recognizer, timeline, turn
        recognizer = timeline = turn = None
        preroll.clear(); detector.reset(); state = 'wake'
        if continue_chat:
            activate('followup', delay_ms=600)
        else:
            flow.reset()
            if entry is not None: entry.cancel_pending()
            emit({'type': 'state', 'state': 'wake'})

    def activate(trigger, delay_ms=0):
        nonlocal state, recognizer, timeline, activation
        if session_end - now_ms() < 10000:
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
        turn['answer'], turn['close_reason'] = answer, close_reason
        state = 'speech'
        turn['offered_more'] = not bool(close_reason) if offered_more is None else offered_more
        emit({'type': 'say', 'text': answer, 'id': turn['id'], 'turn_id': turn['id'],
            'turn_seq': turn['seq'], 'speech_end_ms': turn['speech_end'],
            'deadline_ms': turn['deadline_ms'], 'actions_executed': 0,
            'close_reason': close_reason, 'offered_more': turn['offered_more']})

    def closed():
        emit({'type': 'conversation_closed', 'turn_id': turn['id'], 'turn_seq': turn['seq'],
            'speech_end_ms': turn['speech_end'], 'deadline_ms': turn['deadline_ms'],
            'reason': turn['close_reason']})

    emit({'type': 'ready', 'protocol': PROTOCOL, 'conversation_only': True, 'wake_only': False,
                     'wake_enabled': True, 'ai_enabled': True, 'actions_enabled': False,
                     'clock': 'boottime_ms', 'server_time_ms': now_ms(), 'seconds': limit,
                     'response_start_ms': 30000, 'input_source': source, 'context_version': 1,
                      'restored_turns': len(model.memory.pairs), 'continuous_chat': True,
                       'listen_idle_ms': 50000, 'followup_offer': True, 'closing_farewell': True,
                       'voice_entry': entry is not None})
    try:
        while now_ms() < session_end:
            current = now_ms()
            if current - checked >= 1000:
                checked = current
                if not resource_check() or not model.owned.alive(): raise RuntimeError('Conversation resource/server guard')
            # Inactivity pauses reception; it does not delete or split a chat.
            if state == 'listen' and current - activation >= 50000: reset()
            if turn is not None:
                if state == 'thinking' and current >= turn['compute_end']:
                    emit({'type': 'error', 'reason': 'deadline', 'turn_id': turn['id']})
                    return  # finally stops this dedicated inference process.
                if state == 'thinking' and model.done.is_set():
                    print(json.dumps({'event': 'conversation_context', 'source': source,
                        'turn_seq': turn['seq'], **model.context_metrics}), flush=True)
                    if model.error or not model.result:
                        model.memory.unanswered(turn['text'])
                        emit({'type': 'error', 'reason': 'generation_failed', 'turn_id': turn['id']})
                        reset()
                    elif current < turn['deadline_ms'] - 1500:
                        try:
                            answer = flow.with_offer(model.result)
                            conversational_reply({'content': answer}, 'stop')
                        except ValueError:
                            model.memory.unanswered(turn['text'])
                            emit({'type': 'error', 'reason': 'generation_failed', 'turn_id': turn['id']})
                            reset(); continue
                        say(answer)
                    else: return
                elif state == 'speech' and current >= turn['deadline_ms'] and not turn.get('tts_started'):
                    return
                elif state == 'speech' and current > turn['deadline_ms'] + 20000: return
            for kind, data in pump.poll():
                if kind == 3:
                    control = json.loads(data); action = control.get('action')
                    if action == 'stop': return
                    if action == 'pause': return
                    if action == 'activate' and state == 'wake': activate('button')
                    if action in {'tts_started', 'tts_done', 'tts_failed'}:
                        if turn is None or control.get('turn_id') != turn['id'] or state != 'speech': continue
                        if action == 'tts_started':
                            if turn.get('tts_started'): continue
                            at = control.get('at_ms')
                            if type(at) is not int or not turn['speech_end'] <= at <= min(now_ms() + 100, turn['deadline_ms']): return
                            turn['tts_started'] = True
                            print(json.dumps({'event': 'conversation_tts_start', 'source': source,
                                'turn_seq': turn['seq'], 'speech_end_to_callback_ms': at - turn['speech_end'],
                                'speech_end_estimated': True, 'useful_audio_verified': False,
                                'actions_executed': 0}), flush=True)
                        elif action == 'tts_done' and turn.get('tts_started'):
                            if not turn.get('administrative'): model.memory.commit(turn['text'], turn['answer'])
                            flow.spoken(turn['answer'], offered_more=turn['offered_more'])
                            print(json.dumps({'event': 'conversation_entry_notice' if turn.get('administrative') else 'conversation_memory_commit', 'source': source,
                                'turn_seq': turn['seq'], 'retained_turns': len(model.memory.pairs),
                                'retained_pairs': sum(len(g) == 2 for g in model.memory.pairs)}), flush=True)
                            if turn['close_reason']:
                                closed(); return
                            reset(continue_chat=True)
                        elif action == 'tts_failed':
                            if not turn.get('administrative'): model.memory.unanswered(turn['text'])
                            print(json.dumps({'event': 'conversation_memory_skipped', 'source': source,
                                'turn_seq': turn['seq'], 'reason': 'tts_failed',
                                'retained_turns': len(model.memory.pairs),
                                'retained_pairs': sum(len(g) == 2 for g in model.memory.pairs)}), flush=True)
                            if turn['close_reason']:
                                closed(); return
                            reset()
                    continue
                if kind != 4 or not 8 < len(data) <= 648 or (len(data) - 8) % 2:
                    raise ValueError('Expected timestamped PCM16')
                stamp = struct.unpack('!Q', data[:8])[0]; pcm = data[8:]
                current = now_ms()
                if stamp > current + 100 or current - stamp > 1000 or stamp < last_audio:
                    raise ValueError('Stale or incompatible audio clock')
                last_audio = stamp
                if state == 'wake':
                    preroll.append((stamp, pcm))
                    if detector.accept_pcm(pcm): activate('voice')
                elif state == 'listen':
                    if stamp < activation: continue  # Discard queued audio/TTS tail.
                    timeline.add(stamp, pcm)
                    if not recognizer.AcceptWaveform(pcm): continue
                    result = json.loads(recognizer.Result()); text = result.get('text', '').strip()[:250]
                    if not text or words(text) == words(SETTINGS['wake_word']): continue
                    speech_end = timeline.speech_end(result)
                    head = text.split(maxsplit=1)
                    if len(head) == 2 and words(head[0]) == words(SETTINGS['wake_word']): text = head[1]
                    if now_ms() >= speech_end + 26500: return
                    sequence += 1
                    if sequence > 24: return
                    turn = {'id': uuid.uuid4().hex, 'seq': sequence, 'speech_end': speech_end,
                             'deadline_ms': speech_end + 30000, 'text': text}
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
                        model.memory.last_human = speech_end
                        say(ConversationFlow.FAREWELL, closing)
                        recognizer = timeline = None; preroll.clear()
                        continue  # Close only after farewell receipt/failure; never invoke LFM.
                    turn['compute_end'] = model.start(text, speech_end, session_end)
                    state = 'thinking'; recognizer = timeline = None; preroll.clear()
    except EOFError:
        return
    finally:
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


def serve(key_path, inference_key, detector, limit, cert, tls_key, source, resource_check):
    key = Path(key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing conversation credential')
    model = ConversationModel(inference_key)
    try:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert, tls_key)
        with socket.socket() as server:
            # Reuse TIME_WAIT only; bind/listener guards still refuse an active peer.
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server.bind(('127.0.0.1', 11436)); server.listen(1); server.settimeout(.5)
            print(json.dumps({'event': 'conversation_ready', 'protocol': PROTOCOL, 'tls': True,
                              'input_source': source, 'microphone_opened': False, 'actions_enabled': False}), flush=True)
            deadline = now_ms() + 300000
            while now_ms() < deadline:
                if not resource_check() or not model.owned.alive(): raise RuntimeError('Conversation readiness guard')
                try: raw, _ = server.accept()
                except socket.timeout: continue
                with raw:
                    authentication_end = min(now_ms() + 4000, deadline)
                    raw.settimeout(max(.001, (authentication_end - now_ms()) / 1000))
                    try:
                        with context.wrap_socket(raw, server_side=True) as conn:
                            hello = read_hello(conn, authentication_end)
                            supplied = hello.get('token', '')
                            if (hello.get('protocol') != PROTOCOL or hello.get('mode') != 'conversation_only'
                                    or hello.get('source') != source or not isinstance(supplied, str)
                                    or not hmac.compare_digest(supplied.encode(), key.encode())):
                                continue
                            try: context_id = model.memory.restore(hello.get('context'))
                            except ContextExpired:
                                send_event(conn, {'type': 'error', 'reason': 'context_expired'})
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
                            session(conn, detector, model, limit, resource_check, source, context_id, entry)
                            return
                    except (OSError, EOFError, ValueError):
                        # No credentials, frames or speech text in diagnostics.
                        if not model.owned.alive(): return
    finally:
        model.cancel()
