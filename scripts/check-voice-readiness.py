"""Offline smoke test only: no microphone, speech recording or network access."""
import json
import time

from vosk import KaldiRecognizer, Model, SetLogLevel


def main():
    SetLogLevel(-1)
    started = time.monotonic()
    model = Model("/opt/doha-voice/models/vosk-model-small-es-0.42")
    loaded = time.monotonic()
    recognizer = KaldiRecognizer(model, 16000)
    recognizer.AcceptWaveform(b"\x00\x00" * 16000)
    result = json.loads(recognizer.FinalResult())
    assert result.get("text", "") == "", "Silence unexpectedly produced text"
    print(json.dumps({
        "test": "spanish_model_load_and_one_second_silence",
        "model_load_seconds": round(loaded - started, 3),
        "decode_seconds": round(time.monotonic() - loaded, 3),
        "speech_accuracy_tested": False,
        "microphone_opened": False,
    }))


if __name__ == "__main__":
    main()
