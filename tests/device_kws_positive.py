"""Known positive from the detector's distributed test audio; never opens a microphone."""
import os
os.environ["OPENBLAS_NUM_THREADS"] = "1"
import json
from pathlib import Path
import sys
import wave
import numpy as np

sys.path.insert(0, "/opt/doha-voice/pilot")
from doja_stream import make_spotter

engine = make_spotter()
stream = engine.create_stream("L AY1 T AH1 P @LIGHT_UP")
base = Path("/opt/doha-voice/models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20")
with wave.open(str(base / "test_wavs/en_0.wav"), "rb") as source:
    assert source.getnchannels() == 1 and source.getsampwidth() == 2 and source.getframerate() == 16000
    samples = np.frombuffer(source.readframes(source.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
samples = np.concatenate((samples, np.zeros(16000, dtype=np.float32)))
found = []
for start in range(0, len(samples), 320):
    stream.accept_waveform(16000, samples[start:start + 320])
    while engine.is_ready(stream):
        engine.decode_stream(stream)
        result = engine.get_result(stream)
        if result:
            found.append(result)
            engine.reset_stream(stream)
assert "LIGHT_UP" in found, found
print(json.dumps({"test": "official_positive_light_up", "detections": found, "microphone_opened": False}))
