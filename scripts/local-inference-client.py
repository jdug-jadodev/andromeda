"""Bounded authenticated loopback client, without external tools or proxy access."""
import importlib.util
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request


def main():
    mode, key_path = sys.argv[1:3]
    key = Path(key_path).read_text().strip()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    if mode == "warmup":
        spec = importlib.util.spec_from_file_location("voice_turn", Path(__file__).with_name("voice-turn.py"))
        voice = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(voice)
        prompt = voice.prompt_for("¿Cómo te llamas?")
        deadline = time.monotonic() + 25
        while True:
            try:
                request = urllib.request.Request("http://127.0.0.1:11435/health", headers=headers)
                with opener.open(request, timeout=2):
                    break
            except (OSError, urllib.error.URLError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("Local inference server unavailable")
                time.sleep(0.25)
        limit = 8
    elif mode == "infer":
        prompt = Path(sys.argv[3]).read_text(encoding="utf-8")
        limit = 96
    else:
        raise ValueError("Expected warmup or infer")
    if len(prompt) > 8000:
        raise ValueError("Prompt exceeds prototype limit")
    payload = {"prompt": prompt, "n_predict": limit, "stream": True, "cache_prompt": True,
               "temperature": 0.1, "top_k": 50, "repeat_penalty": 1.05, "seed": 42}
    request = urllib.request.Request("http://127.0.0.1:11435/completion",
                                     data=json.dumps(payload).encode(), headers=headers)
    started = time.monotonic()
    first = None
    chunks = []
    final = {}
    with opener.open(request, timeout=25) as response:
        for raw in response:
            if time.monotonic() - started > 40 or sum(map(len, chunks)) > 4000:
                raise RuntimeError("Inference response exceeded prototype limits")
            if not raw.startswith(b"data: "):
                continue
            if raw.strip() == b"data: [DONE]":
                break
            event = json.loads(raw[6:])
            if event.get("error"):
                raise RuntimeError("Local inference returned an error")
            content = event.get("content", "")
            if content:
                if first is None:
                    first = time.monotonic() - started
                chunks.append(content)
            if event.get("stop"):
                final = event
    if not final:
        raise RuntimeError("Incomplete inference; refusing partial speech")
    if mode == "infer":
        Path(sys.argv[4]).write_text("".join(chunks), encoding="utf-8")
    print(json.dumps({"mode": mode, "first_content_seconds": round(first, 3) if first is not None else None,
                      "inference_seconds": round(time.monotonic() - started, 3),
                      "timings": final.get("timings", {})}))


if __name__ == "__main__":
    main()
