"""Verify the locally generated public playback fixture. No downloads/startup."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
lock = json.loads((root / 'config/capture_probe_audio.json').read_text(encoding='utf-8'))
if (lock['asset'] != 'capture-probe-public.pcm' or lock['sample_rate'] != 16000
        or lock['channels'] != 1 or lock['encoding'] != 'PCM16_LE'
        or not 32000 <= lock['bytes'] <= 256000 or lock['bytes'] % 2):
    raise ValueError('Invalid fixed public audio lock')
path = root / 'artifacts/android-voice/public-playback' / lock['asset']
if not path.exists():
    raise ValueError('Missing fixed public PCM; explicitly regenerate the documented public fixture, never substitute human audio')
data = path.read_bytes()
if len(data) != lock['bytes'] or hashlib.sha256(data).hexdigest() != lock['sha256']:
    raise ValueError('Fixed public PCM integrity mismatch; do not update its lock automatically')
print('Public fixed PCM verified; no recording, playback, model or network startup.')
