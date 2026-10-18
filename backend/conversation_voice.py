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
    if kind != 1 or not 0 < size <= 4096: raise ValueError('Invalid conversation hello')
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

    def messages(self, text):
        if self.clock() - self.last_human >= 3600000: self.pairs.clear()
        self.last_human = self.clock()
        return [{'role': 'system', 'content': SYSTEM}] + [copy.deepcopy(m) for pair in self.pairs for m in pair] + [
            {'role': 'user', 'content': text}]

    def commit(self, text, answer):
        self.pairs.append([{'role': 'user', 'content': text}, {'role': 'assistant', 'content': answer}])
        self.pairs = self.pairs[-6:]


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
        deadline = min(self.clock() + 22000, speech_end + 26500, session_end - 1500)
        messages = self.memory.messages(text)
        def work():
            try:
                while True:
                    rendered = self.post('/apply-template', dict(self.payload(messages), add_generation_prompt=True), deadline)['prompt']
                    tokens = self.post('/tokenize', {'content': rendered, 'add_special': True, 'parse_special': True}, deadline)['tokens']
                    if len(tokens) <= 1536: break
                    if len(messages) <= 2: raise ValueError('Input exceeds context budget')
                    del messages[1:3]  # Drop an entire completed user/assistant pair.
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


def session(conn, detector, model, limit, resource_check, source='microphone'):
    from vosk import KaldiRecognizer, SetLogLevel
    if detector is None or limit not in (90, 300) or source not in {'microphone', 'synthetic_pcm'}:
        raise ValueError('Invalid conversation session')
    SetLogLevel(-1)
    pump = FramePump(conn)
    session_id = uuid.uuid4().hex
    began = checked = now_ms(); session_end = began + limit * 1000
    state, recognizer, timeline = 'wake', None, None
    preroll = deque(maxlen=75)
    activation = last_audio = 0
    turn = None
    sequence = 0

    def reset():
        nonlocal state, recognizer, timeline, turn
        recognizer = timeline = turn = None
        preroll.clear(); detector.reset(); state = 'wake'
        send_event(conn, {'type': 'state', 'state': 'wake'})

    def activate(trigger):
        nonlocal state, recognizer, timeline, activation
        if session_end - now_ms() < 10000: return
        state, activation = 'listen', now_ms()
        recognizer = KaldiRecognizer(detector.model, 16000); recognizer.SetWords(True)
        timeline = AudioTimeline()
        for stamp, pcm in preroll:
            timeline.add(stamp, pcm); recognizer.AcceptWaveform(pcm)
        preroll.clear()
        send_event(conn, {'type': 'state', 'state': 'command', 'trigger': trigger})

    send_event(conn, {'type': 'ready', 'protocol': 2, 'conversation_only': True, 'wake_only': False,
                     'wake_enabled': True, 'ai_enabled': True, 'actions_enabled': False,
                     'clock': 'boottime_ms', 'server_time_ms': now_ms(), 'seconds': limit,
                     'session_id': session_id, 'response_start_ms': 30000, 'input_source': source})
    try:
        while now_ms() < session_end:
            current = now_ms()
            if current - checked >= 1000:
                checked = current
                if not resource_check() or not model.owned.alive(): raise RuntimeError('Conversation resource/server guard')
            if state == 'listen' and current - activation >= 8000: reset()
            if turn is not None:
                if state == 'thinking' and current >= turn['compute_end']:
                    send_event(conn, {'type': 'error', 'reason': 'deadline', 'turn_id': turn['id']})
                    return  # finally stops this dedicated inference process.
                if state == 'thinking' and model.done.is_set():
                    if model.error or not model.result:
                        send_event(conn, {'type': 'error', 'reason': 'generation_failed', 'turn_id': turn['id']})
                        reset()
                    elif current < turn['deadline_ms'] - 1500:
                        turn['answer'] = model.result; state = 'speech'
                        send_event(conn, {'type': 'say', 'text': model.result, 'id': turn['id'],
                            'turn_id': turn['id'], 'turn_seq': turn['seq'], 'speech_end_ms': turn['speech_end'],
                            'deadline_ms': turn['deadline_ms'], 'actions_executed': 0})
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
                            model.memory.commit(turn['text'], turn['answer']); reset()
                        elif action == 'tts_failed': reset()
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
                    send_event(conn, {'type': 'thinking', 'text': text, 'turn_id': turn['id'], 'turn_seq': sequence,
                        'speech_end_ms': speech_end, 'deadline_ms': turn['deadline_ms']})
                    turn['compute_end'] = model.start(text, speech_end, session_end)
                    state = 'thinking'; recognizer = timeline = None; preroll.clear()
    except EOFError:
        return
    finally:
        model.cancel()


def serve(key_path, inference_key, detector, limit, cert, tls_key, source, resource_check):
    key = Path(key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing conversation credential')
    model = ConversationModel(inference_key)
    try:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert, tls_key)
        with socket.socket() as server:
            server.bind(('127.0.0.1', 11436)); server.listen(1); server.settimeout(.5)
            print(json.dumps({'event': 'conversation_ready', 'protocol': 2, 'tls': True,
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
                            if (hello.get('protocol') != 2 or hello.get('mode') != 'conversation_only'
                                    or hello.get('source') != source or not isinstance(supplied, str)
                                    or not hmac.compare_digest(supplied.encode(), key.encode())):
                                continue
                            session(conn, detector, model, limit, resource_check, source)
                            return
                    except (OSError, EOFError, ValueError):
                        # No credentials, frames or speech text in diagnostics.
                        if not model.owned.alive(): return
    finally:
        model.cancel()
