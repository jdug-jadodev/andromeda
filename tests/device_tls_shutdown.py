"""One bounded real TLS peer for shutdown instrumentation, with no model/ASR/audio.

The ready message is a synthetic protocol double, not an inference service.
Run as UID 10218, only while voice/inference ports are unused. No private logs.
"""
import hmac
import json
import os
from pathlib import Path
import socket
import ssl
import sys

sys.path.insert(0, '/opt/doha-voice/pilot')
from conversation_voice import now_ms, read_hello, send_event, ShortMemory
import uuid


def main():
    if os.getuid() != 10218: raise RuntimeError('Fixture must be non-root')
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1', 11435)) == 0:
            raise RuntimeError('Inference is active; fixture refused')
    private = Path('/var/lib/andromeda-conversation')
    key = (private / 'token').read_text().strip()
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(private / 'server-cert.pem', private / 'server-key.pem')
    with socket.socket() as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(('127.0.0.1', 11436)); server.listen(1); server.settimeout(30)
        print(json.dumps({'event': 'tls_shutdown_fixture_ready', 'model_started': False,
                          'microphone_opened': False, 'tts_initialized': False}), flush=True)
        raw, _ = server.accept()
        with raw:
            raw.settimeout(4)
            with context.wrap_socket(raw, server_side=True) as conn:
                hello = read_hello(conn, now_ms() + 4000)
                token = hello.get('token', '')
                if (hello.get('protocol') != 6 or hello.get('mode') != 'conversation_only'
                        or hello.get('source') != 'synthetic_pcm' or not isinstance(token, str)
                        or not hmac.compare_digest(token.encode(), key.encode())):
                    raise ValueError('Invalid fixture authentication')
                memory = ShortMemory(); context_id = memory.restore(hello.get('context'))
                send_event(conn, {'type': 'ready', 'protocol': 6, 'conversation_only': True,
                    'wake_only': False, 'wake_enabled': True, 'ai_enabled': True, 'actions_enabled': False,
                    'clock': 'boottime_ms', 'server_time_ms': now_ms(), 'seconds': 90,
                    'response_start_ms': 30000, 'input_source': 'synthetic_pcm', 'test_fixture': True,
                    'context_id': context_id, 'context_version': 1, 'restored_turns': len(memory.pairs),
                    'session_id': uuid.uuid4().hex, 'continuous_chat': True, 'listen_idle_ms': 30000,
                    'followup_offer': True, 'closing_farewell': True})
                conn.settimeout(10)
                if conn.recv(1) != b'': raise ValueError('Expected shutdown, not audio/control data')
                print(json.dumps({'event': 'tls_shutdown_peer_closed', 'passed': True,
                                  'model_started': False, 'microphone_opened': False}), flush=True)


if __name__ == '__main__': main()
