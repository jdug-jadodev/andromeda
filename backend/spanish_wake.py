"""Experimental Spanish wake detector reusing Vosk's existing model.

The restricted-grammar confidence is a heuristic, NOT a calibrated probability
that the user addressed the assistant. Real positive/negative tests are required.
"""
import json
import re
import unicodedata


def words(text):
    plain = "".join(char for char in unicodedata.normalize("NFD", text.casefold())
                    if unicodedata.category(char) != "Mn")
    return re.findall(r"[^\W\d_]+", plain)


class WakeStability:
    def __init__(self, target, stable_seconds=0.16, minimum_confidence=0.5):
        tokens = words(target)
        if len(tokens) != 1:
            raise ValueError("Expected one wake word")
        self.target = tokens[0]
        self.stable_seconds = stable_seconds
        self.minimum_confidence = minimum_confidence
        self.reset()

    def reset(self):
        self.since = None
        self.latched = False

    def observe(self, text, audio_time, final=False, confidence=None):
        if self.latched:
            return False
        if self.target not in words(text) or (confidence is not None and confidence < self.minimum_confidence):
            self.since = None
            return False
        if self.since is None:
            self.since = audio_time
        if final or audio_time - self.since >= self.stable_seconds:
            self.latched = True
            return True
        return False


class SpanishWakeDetector:
    def __init__(self, target, model_dir, model=None):
        from vosk import Model, SetLogLevel
        SetLogLevel(-1)
        self.model = model if model is not None else Model(str(model_dir))
        if self.model.vosk_model_find_word(target) < 0:
            raise ValueError("Wake word is absent from the Spanish model; refusing aliases")
        self.target = target
        distractors = ["androide", "andrés", "andamio", "adormecida", "dormida"]
        self.grammar = [target, "[unk]"] + [word for word in distractors
            if word != target and self.model.vosk_model_find_word(word) >= 0]
        self.stability = WakeStability(target)
        self.reset()

    def reset(self):
        from vosk import KaldiRecognizer
        self.recognizer = KaldiRecognizer(self.model, 16000, json.dumps(self.grammar, ensure_ascii=False))
        self.recognizer.SetWords(True)
        self.recognizer.SetPartialWords(True)
        self.audio_time = 0.0
        self.stability.reset()

    def accept_pcm(self, data):
        if not data or len(data) % 2 or len(data) > 640:
            raise ValueError("Expected at most 20 ms PCM16 mono at 16 kHz")
        self.audio_time += len(data) / 32000.0
        final = self.recognizer.AcceptWaveform(data)
        event = json.loads(self.recognizer.Result() if final else self.recognizer.PartialResult())
        matches = [item.get("conf") for item in event.get("result" if final else "partial_result", [])
                   if self.stability.target in words(item.get("word", "")) and "conf" in item]
        confidence = min(matches) if matches else None
        return self.stability.observe(event.get("text" if final else "partial", ""),
                                      self.audio_time, final, confidence)
