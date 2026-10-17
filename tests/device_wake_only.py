"""Real Vosk + fixed public audio through the restricted stream; never opens mic."""
import json
from pathlib import Path
import resource
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time
import wave

sys.path.insert(0, '/opt/doha-voice/pilot')
from assistant_config import SETTINGS
from spanish_wake import SpanishWakeDetector
from doja_stream import receive, wake_only_session

fixtures = Path(sys.argv[1])
detector = SpanishWakeDetector(SETTINGS['wake_word'], '/opt/doha-voice/models/vosk-model-small-es-0.42')
for name in ['positive', 'command', 'negated', 'quoted', 'androide', 'andres', 'adormecida', 'andamio', 'ordinary']:
    with tempfile.TemporaryDirectory(prefix='wake-only-') as work:
        output = Path(work) / 'fixture.wav'
        subprocess.run(['/usr/bin/ffmpeg', '-nostdin', '-v', 'error', '-threads', '1', '-i',
                        str(fixtures / f'andromeda_fixture_{name}.wav'), '-t', '10', '-ac', '1',
                        '-ar', '16000', '-c:a', 'pcm_s16le', str(output)], check=True, timeout=10)
        detector.reset()
        left, right = socket.socketpair()
        failures, events = [], []
        def worker():
            try:
                with right: wake_only_session(right, detector, 30)
            except Exception as error: failures.append(type(error).__name__)
        thread = threading.Thread(target=worker, daemon=True); thread.start()
        def event():
            size = struct.unpack('!I', receive(left, 4))[0]
            assert 0 < size <= 16384
            return json.loads(receive(left, size))
        with left:
            left.settimeout(20)
            ready = event()
            assert ready['wake_only'] and ready['wake_enabled'] and not ready['ai_enabled']
            with wave.open(str(output), 'rb') as audio:
                assert (audio.getframerate(), audio.getnchannels(), audio.getsampwidth()) == (16000, 1, 2)
                while pcm := audio.readframes(320):
                    left.sendall(struct.pack('!BI', 2, len(pcm)) + pcm)
            for _ in range(100): left.sendall(struct.pack('!BI', 2, 640) + b'\x00' * 640)
            if name == 'positive':
                # A real wall-clock wait, with live PCM cadence so it is not a
                # socket timeout. Checks the eight-second return-to-clock event.
                for _ in range(420):
                    left.sendall(struct.pack('!BI', 2, 640) + b'\x00' * 640)
                    time.sleep(.02)
            stop = b'{"action":"stop"}'
            left.sendall(struct.pack('!BI', 3, len(stop)) + stop)
            while True:
                try: events.append(event())
                except EOFError: break
        thread.join(timeout=5)
        assert not thread.is_alive() and not failures, failures
        wakes = [e for e in events if e.get('trigger') == 'voice']
        expected = name in {'positive', 'command', 'negated', 'quoted'}
        assert bool(wakes) == expected, name
        assert all(e['type'] in {'state', 'transcript'} for e in events), name
        if name == 'positive': assert any(e.get('state') == 'wake' for e in events), 'No return to wake'
        if name == 'command':
            assert any('temporizador' in e.get('text', '') for e in events if e['type'] == 'transcript'), 'No command transcription'
        print(json.dumps({'test': 'synthetic_wake_only', 'case': name, 'passed': True,
                          'voice_activations': len(wakes), 'actions_executed': 0,
                          'transcript_events': sum(e['type'] == 'transcript' for e in events),
                          'return_to_wake': any(e.get('state') == 'wake' for e in events),
                          'microphone_opened': False, 'tts_played': False,
                          'peak_rss_kib': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss}), flush=True)
