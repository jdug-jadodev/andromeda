"""Four fresh protocol-6 memory instances behind TLS; public test doubles only.

No model, ASR, TTS, microphone, alarm scheduling or private transcript logs.
Only a synthetic client is accepted. The parent listener expires within 60 s.
"""
import hmac
import json
import os
from pathlib import Path
import socket
import ssl
import sys
import uuid

sys.path.insert(0, '/opt/doha-voice/pilot')
from conversation_voice import ShortMemory, now_ms, read_hello, send_event
from doja_stream import frame

QUESTION = '¿Qué es una estrella?'
ANSWER = 'Una estrella emite luz y calor.'
PENDING = '¿Qué es un planeta?'


def main():
    if os.getuid() != 10218: raise RuntimeError('Non-root fixture required')
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1', 11435)) == 0: raise RuntimeError('Model active; fixture refused')
    private = Path('/var/lib/andromeda-conversation')
    key = (private / 'token').read_text().strip()
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(private / 'server-cert.pem', private / 'server-key.pem')
    original_id = None; last_human = 0; deadline = now_ms() + 60000
    with socket.socket() as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(('127.0.0.1', 11436)); server.listen(1)
        print(json.dumps({'event': 'context_retention_fixture_ready', 'model_started': False, 'microphone_opened': False}), flush=True)
        for phase in range(4):
            server.settimeout(max(.001, (deadline - now_ms()) / 1000))
            raw, _ = server.accept()
            with raw:
                raw.settimeout(4)
                with tls.wrap_socket(raw, server_side=True) as conn:
                    hello = read_hello(conn, min(now_ms() + 4000, deadline)); token = hello.get('token', '')
                    if (hello.get('protocol') != 6 or hello.get('mode') != 'conversation_only'
                            or hello.get('source') != 'synthetic_pcm' or not isinstance(token, str)
                            or not hmac.compare_digest(token.encode(), key.encode())):
                        raise ValueError('Invalid synthetic fixture authentication')
                    memory = ShortMemory(); context_id = memory.restore(hello.get('context'))
                    expected = [0, 1, 2, 0][phase]
                    if len(memory.pairs) != expected: raise ValueError('Restored count mismatch')
                    if phase == 0: original_id = context_id
                    elif phase in (1, 2) and context_id != original_id: raise ValueError('Conversation identity lost')
                    elif phase == 3 and context_id == original_id: raise ValueError('New conversation reused history')
                    if phase in (1, 2):
                        if memory.last_human != last_human: raise ValueError('Reopen renewed human activity')
                        if memory.pairs[0] != [{'role': 'user', 'content': QUESTION}, {'role': 'assistant', 'content': ANSWER}]:
                            raise ValueError('Completed pair changed')
                    if phase == 2:
                        if memory.pairs[1] != [{'role': 'user', 'content': PENDING}]: raise ValueError('Interrupted question lost or answer invented')
                        roles = [m['role'] for m in memory.messages('Otra pregunta pública')]
                        if roles != ['system', 'user', 'assistant', 'user', 'user']: raise ValueError('Native context roles changed')
                    session_id = uuid.uuid4().hex
                    def emit(event): send_event(conn, dict(event, session_id=session_id, context_id=context_id))
                    emit({'type': 'ready', 'protocol': 6, 'conversation_only': True, 'wake_only': False,
                        'wake_enabled': True, 'ai_enabled': True, 'actions_enabled': False, 'test_fixture': True,
                        'clock': 'boottime_ms', 'server_time_ms': now_ms(), 'seconds': 90, 'response_start_ms': 30000,
                        'input_source': 'synthetic_pcm', 'context_version': 1, 'restored_turns': expected,
                        'continuous_chat': True, 'listen_idle_ms': 50000, 'followup_offer': True, 'closing_farewell': True})
                    conn.settimeout(6)
                    if phase in (0, 1):
                        last_human = now_ms() - 300; turn_id = uuid.uuid4().hex
                        emit({'type': 'thinking', 'text': QUESTION if phase == 0 else PENDING,
                            'turn_id': turn_id, 'turn_seq': 1, 'speech_end_ms': last_human, 'deadline_ms': last_human + 30000})
                        if phase == 0:
                            emit({'type': 'say', 'text': ANSWER, 'id': turn_id, 'turn_id': turn_id, 'turn_seq': 1,
                                'speech_end_ms': last_human, 'deadline_ms': last_human + 30000, 'actions_executed': 0})
                            for action in ('tts_started', 'tts_done'):
                                kind, data = frame(conn); control = json.loads(data)
                                if kind != 3 or control.get('action') != action or control.get('turn_id') != turn_id:
                                    raise ValueError('Mock delivery receipt mismatch')
                    else:
                        emit({'type': 'state', 'state': 'wake', 'pending_human_restored': phase == 2})
                    if conn.recv(1) != b'': raise ValueError('Expected channel close, no PCM')
                    print(json.dumps({'event': 'context_retention_phase', 'phase': phase + 1,
                        'restored_turns': expected, 'fresh_backend_memory': True, 'passed': True}), flush=True)
        print(json.dumps({'event': 'context_retention_fixture_complete', 'passed': True,
            'model_started': False, 'microphone_opened': False, 'audio_played': False}), flush=True)


if __name__ == '__main__': main()
