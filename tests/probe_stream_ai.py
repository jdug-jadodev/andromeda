"""Device synthetic wake -> real LLM -> proposal -> yes -> intent, no actions."""
import json
from pathlib import Path
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import wave

from assistant_config import SETTINGS
from doja_stream import session, receive
from local_ai import LocalPlanner, AITurns
from spanish_wake import SpanishWakeDetector

planner = LocalPlanner(sys.argv[1])
fixtures = Path(sys.argv[2])
detector = SpanishWakeDetector(SETTINGS['wake_word'], '/opt/doha-voice/models/vosk-model-small-es-0.42')
with tempfile.TemporaryDirectory(prefix='andromeda-ai-synthetic-') as work:
    for name in ['ai_create', 'ai_cancel', 'ai_confirm', 'ai_chat']:
        subprocess.run(['/usr/bin/ffmpeg', '-nostdin', '-v', 'error', '-threads', '1', '-i',
                        str(fixtures / f'andromeda_fixture_{name}.wav'), '-t', '10', '-ac', '1',
                        '-ar', '16000', '-c:a', 'pcm_s16le', str(Path(work) / f'{name}.wav')],
                       check=True, timeout=10)
    for case, kind in [('ai_create', 'timer'), ('ai_cancel', 'cancel_timer'), ('ai_chat', 'say')]:
        detector.reset()
        left, right = socket.socketpair()
        failures = []

        def worker():
            try:
                with right:
                    session(right, detector, 90, ai_turns=AITurns(planner))
            except Exception as error:
                failures.append(repr(error))

        thread = threading.Thread(target=worker, daemon=True)
        thread.start()

        def control(action):
            data = json.dumps({'action': action}).encode()
            left.sendall(struct.pack('!BI', 3, len(data)) + data)

        def event():
            length = struct.unpack('!I', receive(left, 4))[0]
            assert 0 < length <= 16384
            return json.loads(receive(left, length))

        def utterance(name):
            with wave.open(str(Path(work) / f'{name}.wav'), 'rb') as audio:
                while pcm := audio.readframes(320):
                    left.sendall(struct.pack('!BI', 2, len(pcm)) + pcm)
            for _ in range(100):
                left.sendall(struct.pack('!BI', 2, 640) + b'\x00' * 640)
            events = []
            while True:
                item = event(); events.append(item)
                if item.get('type') in {'say', 'timer', 'cancel_timer', 'list_timers'}:
                    return item, events

        with left:
            left.settimeout(45)
            assert event()['wake_enabled']
            first, events = utterance(case)
            assert first['type'] == 'say', (case, events)
            if kind != 'say':
                assert 'confirmar' in first['text'], (case, events)
                control('tts_done')
                assert event().get('state') == 'wake'
                final, events = utterance('ai_confirm')
                assert final['type'] == kind, (case, events)
                if kind == 'timer': assert final['seconds'] == 120
                if kind == 'cancel_timer': assert final['all'] is True
            control('stop')
        thread.join(timeout=5)
        assert not thread.is_alive() and not failures, failures
        print(json.dumps({'test': 'synthetic_voice_local_llm', 'case': case, 'intent': kind,
                          'passed': True, 'microphone_opened': False,
                          'action_executed': False}), flush=True)
