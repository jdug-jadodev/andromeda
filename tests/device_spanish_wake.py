"""Device-only synthetic ASR/wake checks. Never opens mic or schedules alarms."""
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

sys.path.insert(0, "/opt/doha-voice/pilot")
from assistant_config import SETTINGS
from spanish_wake import SpanishWakeDetector
from doja_stream import session, receive

fixtures = Path(sys.argv[1])
began = time.monotonic()
detector = SpanishWakeDetector(SETTINGS["wake_word"], "/opt/doha-voice/models/vosk-model-small-es-0.42")
results = []
with tempfile.TemporaryDirectory(prefix="andromeda-synthetic-") as work:
    for name in ["positive", "command", "cancel", "delete", "negated", "quoted", "androide", "andres", "adormecida", "andamio", "ordinary"]:
        destination = Path(work) / f"{name}.wav"
        subprocess.run(["/usr/bin/ffmpeg", "-nostdin", "-v", "error", "-threads", "1", "-i",
                        str(fixtures / f"andromeda_fixture_{name}.wav"), "-t", "10", "-ac", "1",
                        "-ar", "16000", "-c:a", "pcm_s16le", str(destination)], check=True, timeout=10)
        detector.reset()
        hits = []
        samples_seen = 0
        with wave.open(str(destination), "rb") as audio:
            assert (audio.getframerate(), audio.getnchannels(), audio.getsampwidth()) == (16000, 1, 2)
            while pcm := audio.readframes(320):
                samples_seen += len(pcm) // 2
                if detector.accept_pcm(pcm):
                    hits.append(round(samples_seen / 16000, 2))
            for _ in range(100):
                samples_seen += 320
                if detector.accept_pcm(b"\x00" * 640):
                    hits.append(round(samples_seen / 16000, 2))
        expected = name in ("positive", "command", "cancel", "delete", "negated", "quoted")
        results.append({"case": name, "detections_at_audio_seconds": hits, "expected_wake": expected})
    print(json.dumps({"test": "spanish_synthetic_wake", "results": results,
                      "elapsed_seconds": round(time.monotonic() - began, 2),
                      "peak_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
                      "microphone_opened": False}, ensure_ascii=False))
    assert all(bool(item["detections_at_audio_seconds"]) == item["expected_wake"] for item in results), results

    # Only receive JSON intents. No Android client exists here to schedule/cancel alarms.
    for case, expected_kind in [("command", "timer"), ("cancel", "cancel_timer"), ("delete", "cancel_timer"),
                                ("negated", "say"), ("quoted", "say")]:
        detector.reset()
        left, right = socket.socketpair()
        failures = []

        def worker():
            try:
                with right:
                    session(right, detector, 20)
            except Exception as error:
                failures.append(repr(error))

        thread = threading.Thread(target=worker, daemon=True)
        thread.start()
        events = []

        def event():
            length = struct.unpack("!I", receive(left, 4))[0]
            assert length <= 16384
            return json.loads(receive(left, length))

        with left:
            left.settimeout(25)
            assert event()["wake_enabled"] is True
            heartbeat = json.dumps({"action": "heartbeat"}).encode()
            left.sendall(struct.pack("!BI", 3, len(heartbeat)) + heartbeat)
            with wave.open(str(Path(work) / f"{case}.wav"), "rb") as audio:
                while pcm := audio.readframes(320):
                    left.sendall(struct.pack("!BI", 2, len(pcm)) + pcm)
            for _ in range(100):
                left.sendall(struct.pack("!BI", 2, 640) + b"\x00" * 640)
            stop = json.dumps({"action": "stop"}).encode()
            left.sendall(struct.pack("!BI", 3, len(stop)) + stop)
            while True:
                try:
                    events.append(event())
                except EOFError:
                    break
        thread.join(timeout=5)
        assert not thread.is_alive() and not failures, failures
        wakes = [item for item in events if item.get("trigger") == "voice"]
        intents = [item for item in events if item.get("type") in ("timer", "cancel_timer", "say", "stop_alarm", "list_timers")]
        assert len(wakes) == 1 and len(intents) == 1 and intents[0]["type"] == expected_kind, (case, events)
        if expected_kind == "timer":
            assert intents[0]["seconds"] == 10
        print(json.dumps({"test": "synthetic_wake_intent", "case": case, "intent": expected_kind,
                          "passed": True, "microphone_opened": False, "alarm_scheduled": False,
                          "peak_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss}))
