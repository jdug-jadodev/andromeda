"""Bounded local voice pilot. Optional local LLM; no stored audio or Internet."""
import os
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["OMP_NUM_THREADS"] = "1"
import argparse
from collections import deque
import gc
import hmac
import json
from pathlib import Path
import socket
import struct
import time
import uuid

from doja_intents import parse_command
from assistant_config import SETTINGS
from spanish_wake import SpanishWakeDetector, words

MODEL_DIR = Path("/opt/doha-voice/models")


def resource_ok():
    memory = next(int(line.split()[1]) for line in Path("/proc/meminfo").read_text().splitlines()
                  if line.startswith("MemAvailable:"))
    temp = int(Path("/sys/class/power_supply/battery/temp").read_text())
    return memory >= 500000 and 0 <= temp < 400


def receive(conn, length):
    data = bytearray()
    while len(data) < length:
        chunk = conn.recv(length - len(data))
        if not chunk:
            raise EOFError()
        data.extend(chunk)
    return bytes(data)


def frame(conn):
    kind, length = struct.unpack("!BI", receive(conn, 5))
    if length > 4096:
        raise ValueError("Oversized frame")
    return kind, receive(conn, length)


def send(conn, event):
    data = json.dumps(event, ensure_ascii=False).encode()
    conn.sendall(struct.pack("!I", len(data)) + data)


def make_spotter(score=1.0, threshold=0.35):
    import sherpa_onnx
    base = MODEL_DIR / "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
    return sherpa_onnx.KeywordSpotter(
        tokens=str(base / "tokens.txt"), keywords_file=str(base / "keywords-doja.txt"),
        encoder=str(base / "encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx"),
        decoder=str(base / "decoder-epoch-13-avg-2-chunk-16-left-64.onnx"),
        joiner=str(base / "joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx"),
        num_threads=1, keywords_score=score, keywords_threshold=threshold,
        num_trailing_blanks=1, provider="cpu")


def session(conn, spotter, limit, ai_turns=None):
    from vosk import Model, KaldiRecognizer, SetLogLevel
    spanish = isinstance(spotter, SpanishWakeDetector)
    if spotter is not None and not spanish:
        import numpy as np
    SetLogLevel(-1)
    conn.settimeout(15)
    stream = spotter.create_stream() if spotter is not None and not spanish else None
    preroll = deque(maxlen=75)  # <=1.5 seconds for 20 ms PCM packets
    model = recognizer = None
    state = "wake"
    began = checked = time.monotonic()
    activation = 0.0
    activations = 0
    pending_words = []

    def reset():
        nonlocal model, recognizer, state, stream
        model = recognizer = None
        gc.collect()
        if spanish:
            spotter.reset()
        else:
            stream = spotter.create_stream() if spotter is not None else None
        preroll.clear()
        pending_words.clear()
        state = "wake"
        send(conn, {"type": "state", "state": state})

    def activate(source="voice"):
        nonlocal model, recognizer, state, activation, activations
        remaining = limit - (time.monotonic() - began)
        if remaining < 15:
            state = "speaking"
            send(conn, {"type": "say", "text": "El piloto está por terminar. Reinícialo antes de dar una nueva orden."})
            print(json.dumps({"event": "wake_rejected", "reason": "pilot_ending",
                              "remaining_seconds": round(remaining, 2)}), flush=True)
            return
        activations += 1
        if activations > (24 if ai_turns is not None else 8):
            raise ValueError("Pilot activation limit reached")
        state = "command"
        activation = time.monotonic()
        pending_words.clear()
        send(conn, {"type": "state", "state": "command", "trigger": source})
        model = spotter.model if spanish else Model(str(MODEL_DIR / "vosk-model-small-es-0.42"))
        recognizer = KaldiRecognizer(model, 16000)
        for data in preroll:
            recognizer.AcceptWaveform(data)
        preroll.clear()
        print(json.dumps({"event": "wake", "activation": activations, "source": source,
                          "engine": "vosk_es" if spanish else "legacy_or_manual",
                          "remaining_seconds": round(remaining, 2)}), flush=True)

    send(conn, {"type": "ready", "seconds": limit, "name": SETTINGS["name"],
                "wake_word": SETTINGS["wake_word"], "wake_enabled": spotter is not None,
                "ai_enabled": ai_turns is not None})
    while time.monotonic() - began < limit:
        now = time.monotonic()
        if now - checked >= 1:
            checked = now
            if ai_turns is not None and hasattr(ai_turns, "store"):
                ai_turns.store.prune()
            if not resource_ok():
                raise RuntimeError("Pilot stopped by resource guard")
        if state == "command" and now - activation > 12:
            print(json.dumps({"event": "command_timeout", "pending_word_count": len(" ".join(pending_words).split())}), flush=True)
            send(conn, {"type": "say", "text": "No entendí una orden completa. Inténtalo otra vez."})
            state = "speaking"
        kind, data = frame(conn)
        if kind == 3:
            action = json.loads(data).get("action")
            if action == "stop":
                return
            if action == "tool_result" and ai_turns is not None and hasattr(ai_turns, "complete"):
                result = json.loads(data)
                send(conn, {"type": "thinking"})
                intent = ai_turns.complete(result.get("call_id"), result.get("ok"), result.get("message"))
                intent["id"] = str(uuid.uuid4())
                state = "speaking"
                send(conn, intent)
                continue
            if action in ("tts_done", "resume"):
                reset()
            elif action in ("tts_started", "pause"):
                state = "speaking"
                preroll.clear()
            elif action == "activate" and state == "wake":
                activate("button")
            continue
        if kind != 2 or not data or len(data) % 2 or len(data) > 640:
            raise ValueError("Expected at most 20ms of PCM16 mono")
        if state == "speaking":
            continue
        if state == "wake":
            preroll.append(data)
            if spotter is None:
                continue  # Manual-only mode while the new wake word is not validated.
            if spanish:
                detected = spotter.accept_pcm(data)
            else:
                samples = np.frombuffer(data, dtype="<i2").astype(np.float32) / 32768.0
                stream.accept_waveform(16000, samples)
                detected = False
                while spotter.is_ready(stream):
                    spotter.decode_stream(stream)
                    if spotter.get_result(stream):
                        detected = True
                        break
            if detected:
                activate()
        elif recognizer.AcceptWaveform(data):
            text = json.loads(recognizer.Result()).get("text", "").strip()
            if not text:
                continue
            if words(text) == words(SETTINGS["wake_word"]) and not pending_words:
                continue  # A standalone wake word is not the following command.
            pending_words.append(text)
            text = " ".join(pending_words)[:250]
            if ai_turns is not None:
                send(conn, {"type": "transcript", "text": text[:250]})
                send(conn, {"type": "thinking"})
                intent = ai_turns(text)
            else:
                intent = parse_command(text)
            if intent.pop("needs_duration", False):
                continue  # Allow a short pause between 'temporizador' and its duration.
            send(conn, {"type": "transcript", "text": text[:250]})
            intent["id"] = str(uuid.uuid4())
            # Actual timer creation/confirmation happens in Android, not in the model.
            state = "speaking"
            send(conn, intent)
            model = recognizer = None
            gc.collect()
            print(json.dumps({"event": "intent", "kind": intent["type"],
                              "seconds": intent.get("seconds"),
                               "command_seconds": round(time.monotonic() - activation, 3)}), flush=True)
    print(json.dumps({"event": "session_ended", "reason": "pilot_timeout", "phase": state,
                      "pending_word_count": len(" ".join(pending_words).split())}), flush=True)


def calibrate(conn, engines):
    """Compare candidates against scripted positives/negatives, without actions or stored audio."""
    import numpy as np
    candidates = [("baseline", 1.0, 0.35), ("default_threshold", 1.0, 0.25),
                  ("boosted", 1.5, 0.25)]
    stages = ["SILENCIO", "DOJA", "ROJA", "HOJA", "DUCHA", "DOJA FINAL"]
    counts = {name: {stage: 0 for stage in stages} for name, _, _ in candidates}
    send(conn, {"type": "ready", "seconds": 60})
    began = time.monotonic()
    checked = began
    for stage in stages:
        streams = [(name, engine, engine.create_stream()) for name, engine in engines]
        text = "Calibración: guarda silencio 7 segundos." if stage == "SILENCIO" else f"Calibración: di {stage.replace(' FINAL', '')} tres veces, con pausas (7 s)."
        send(conn, {"type": "calibration", "text": text})
        samples_seen = 0
        while samples_seen < 7 * 16000:
            now = time.monotonic()
            if now - began > 75:
                raise RuntimeError("Calibration limit reached")
            if now - checked >= 1:
                checked = now
                if not resource_ok():
                    raise RuntimeError("Calibration resource guard")
            kind, data = frame(conn)
            if kind == 3:
                if json.loads(data).get("action") == "stop":
                    return
                continue
            if kind != 2 or not data or len(data) % 2 or len(data) > 640:
                raise ValueError("Invalid calibration PCM")
            samples = np.frombuffer(data, dtype="<i2").astype(np.float32) / 32768.0
            samples_seen += len(samples)
            for name, engine, stream in streams:
                stream.accept_waveform(16000, samples)
                while engine.is_ready(stream):
                    engine.decode_stream(stream)
                    if engine.get_result(stream):
                        counts[name][stage] += 1
                        engine.reset_stream(stream)
                        break
        print(json.dumps({"event": "calibration_stage", "stage": stage,
                          "hits": {name: counts[name][stage] for name, _, _ in candidates}}), flush=True)
    print(json.dumps({"event": "calibration_result", "counts": counts}), flush=True)
    send(conn, {"type": "calibration", "text": "Calibración terminada. Micrófono se apagará."})


def main():
    import resource
    parser = argparse.ArgumentParser()
    parser.add_argument("--key", required=True)
    parser.add_argument("--seconds", type=int, default=90)
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--calibrate", action="store_true")
    parser.add_argument("--test-wake", action="store_true", help="Enable Spanish wake word for this bounded pilot only")
    parser.add_argument("--ai-key", help="Enable local LLM interpretation; mutations require confirmation")
    parser.add_argument("--chat", action="store_true", help="Conversational tool loop with bounded recent memory")
    args = parser.parse_args()
    ai_turns = None
    if args.ai_key:
        if args.chat:
            from conversation_agent import ConversationAgent, ChatModel
            ai_turns = ConversationAgent(ChatModel(args.ai_key))
        else:
            from local_ai import AITurns, LocalPlanner
            ai_turns = AITurns(LocalPlanner(args.ai_key))
    elif args.chat:
        raise ValueError("Chat requires a local inference server")
    if args.calibrate and SETTINGS["wake_word"] != "doja":
        raise ValueError("Legacy Doja calibration is retired; prepare the new Spanish wake word first")
    if SETTINGS["wake_enabled"]:
        raise ValueError("Permanent activation is not validated; use --test-wake for a bounded pilot")
    if args.test_wake and args.calibrate:
        raise ValueError("Choose a single pilot mode")
    if not 10 <= args.seconds <= 300 or not resource_ok():
        raise ValueError("Invalid pilot limit or insufficient resources")
    started = time.monotonic()
    if args.test_wake:
        spotter = SpanishWakeDetector(SETTINGS["wake_word"], MODEL_DIR / "vosk-model-small-es-0.42")
    else:
        spotter = make_spotter() if args.smoke else None
    engines = None
    if args.calibrate and not args.smoke:
        engines = [(name, make_spotter(score, threshold)) for name, score, threshold in
                   [("baseline", 1.0, 0.35), ("default_threshold", 1.0, 0.25), ("boosted", 1.5, 0.25)]]
    if args.smoke:
        if isinstance(spotter, SpanishWakeDetector):
            for _ in range(50):
                assert not spotter.accept_pcm(b"\x00" * 640)
        else:
            import numpy as np
            stream = spotter.create_stream()
            stream.accept_waveform(16000, np.zeros(16000, dtype=np.float32))
            while spotter.is_ready(stream):
                spotter.decode_stream(stream)
                assert not spotter.get_result(stream)
        print(json.dumps({"smoke": "one_second_silence", "seconds": round(time.monotonic() - started, 3),
                          "peak_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss}))
        return
    key = Path(args.key).read_text().strip()
    if len(key) < 32:
        raise ValueError("Missing pilot credential")
    with socket.socket() as server:
        server.bind(("127.0.0.1", 11436))
        server.listen(1)
        server.settimeout(1)
        print(json.dumps({"event": "ready", "bind": "127.0.0.1:11436"}), flush=True)
        deadline = time.monotonic() + (60 if ai_turns is not None else 180)
        while time.monotonic() < deadline:
            try:
                conn, _ = server.accept()
            except socket.timeout:
                if not resource_ok():
                    raise RuntimeError("Resource guard")
                continue
            with conn:
                conn.settimeout(5)
                try:
                    kind, data = frame(conn)
                    supplied = json.loads(data).get("token", "") if kind == 1 else ""
                except (ValueError, EOFError, OSError):
                    continue
                if not isinstance(supplied, str) or not hmac.compare_digest(supplied.encode(), key.encode()):
                    continue
                try:
                    if args.calibrate:
                        calibrate(conn, engines)
                    else:
                        session(conn, spotter, args.seconds, ai_turns=ai_turns)
                except (EOFError, OSError) as error:
                    print(json.dumps({"event": "client_disconnected", "kind": type(error).__name__}), flush=True)
                break
    stats = resource.getrusage(resource.RUSAGE_SELF)
    print(json.dumps({"event": "stopped", "elapsed_seconds": round(time.monotonic() - started, 2),
                      "peak_rss_kib": stats.ru_maxrss, "cpu_seconds": round(stats.ru_utime + stats.ru_stime, 2)}), flush=True)


if __name__ == "__main__":
    main()
