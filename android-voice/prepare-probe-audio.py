"""Reproduce the pinned public PCM from its fixed offline-TTS WAV. Never captures."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import wave

parser = argparse.ArgumentParser()
parser.add_argument('--from-fixed-wav', type=Path, required=True,
                    help='Public ordinary fixture only, not human audio; output must match the pinned hash')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
lock = json.loads((root / 'config/capture_probe_audio.json').read_text(encoding='utf-8'))
with wave.open(str(args.from_fixed_wav), 'rb') as wav:
    if wav.getnchannels() != 1 or wav.getsampwidth() != 2 or wav.getcomptype() != 'NONE':
        raise ValueError('Expected the fixed public mono PCM16 fixture')
    rate = wav.getframerate()
    if not 16000 <= rate <= 48000 or wav.getnframes() > rate * 8:
        raise ValueError('Public fixture rate/duration out of bounds')
    raw = wav.readframes(wav.getnframes())
samples = struct.unpack('<' + 'h' * (len(raw) // 2), raw)
output = bytearray()
for i in range(len(samples) * 16000 // rate):
    position = i * rate / 16000
    left = int(position); right = min(left + 1, len(samples) - 1)
    output.extend(struct.pack('<h', round(samples[left] + (samples[right] - samples[left]) * (position - left))))
if len(output) != lock['bytes'] or hashlib.sha256(output).hexdigest() != lock['sha256']:
    raise ValueError('Not the pinned public clip; never substitute other/human audio or update the lock automatically')
path = root / 'artifacts/android-voice/public-playback/capture-probe-public.pcm'
path.parent.mkdir(parents=True, exist_ok=True)
if path.exists() and path.read_bytes() != output:
    raise ValueError('Existing local PCM differs; inspect it before replacement')
path.write_bytes(output)
print('Pinned public PCM reproduced; no recording, playback, model or network startup.')
