"""Pinned Android ASR runtime libraries, not model downloads. Offline by default."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--download', action='store_true', help='Explicitly fetch pinned public SDK AARs from Maven Central')
args = parser.parse_args()
config = json.loads((root / 'config/voice_interrupt_deps.json').read_text(encoding='utf-8'))
target = root / 'artifacts/android-voice/speech-deps'
target.mkdir(parents=True, exist_ok=True)
for item in config['dependencies']:
    name = item['name']
    if '/' in name or '\\' in name or not item['url'].startswith('https://repo.maven.apache.org/maven2/'):
        raise ValueError('Invalid dependency lock')
    archive = target / (name + '.aar')
    if not archive.exists():
        if not args.download: raise ValueError('Missing speech SDK; run prepare-speech-deps.py --download explicitly')
        with urllib.request.urlopen(item['url'], timeout=60) as response: data = response.read(20000001)
        if len(data) > 20000000 or hashlib.sha256(data).hexdigest() != item['sha256']: raise ValueError('SDK checksum mismatch')
        archive.write_bytes(data)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != item['sha256']: raise ValueError('Existing SDK checksum mismatch')
    with zipfile.ZipFile(archive) as data:
        for entry in ['classes.jar', 'jni/' + config['abi'] + '/' + item['native']]:
            output = target / name / entry
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data.read(entry))
print('Pinned Vosk/JNA Android SDKs verified; no ASR/model download or startup.')
