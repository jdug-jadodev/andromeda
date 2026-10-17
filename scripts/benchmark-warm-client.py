"""Synthetic loopback benchmark. No mic, user history or external requests."""
import importlib.util
import json
import os
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.request


def server_ticks(pid):
    fields = Path(f"/proc/{pid}/stat").read_text().rsplit(") ", 1)[1].split()
    return int(fields[11]) + int(fields[12])


def main():
    work = Path(sys.argv[2])
    if sys.argv[1] == "init":
        (work / "key").write_text(secrets.token_urlsafe(32) + "\n")
        (work / "key").chmod(0o600)
        return
    key = (work / "key").read_text().strip()
    # Explicitly ignore inherited HTTP proxy variables for local benchmark traffic.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    started = time.monotonic()
    deadline = started + 25
    while True:
        try:
            request = urllib.request.Request("http://127.0.0.1:11435/health", headers=headers)
            with opener.open(request, timeout=2) as response:
                if response.status == 200:
                    break
        except (OSError, urllib.error.URLError):
            if time.monotonic() >= deadline:
                raise RuntimeError("Temporary server did not become healthy")
            time.sleep(0.25)
    print(json.dumps({"server_ready_seconds": round(time.monotonic() - started, 3)}), flush=True)
    source = Path(__file__).with_name("voice-turn.py")
    spec = importlib.util.spec_from_file_location("voice_turn", source)
    voice = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(voice)
    for name, text in [
        ("first_sky", "¿Por qué el cielo es azul?"),
        ("repeat_sky", "¿Por qué el cielo es azul?"),
        ("different_question", "¿Cómo te llamas?"),
    ]:
        payload = {
            "prompt": voice.prompt_for(text), "n_predict": 48,
            "temperature": 0.1, "top_k": 50, "repeat_penalty": 1.05,
            "seed": 42, "cache_prompt": True, "stream": True,
        }
        request = urllib.request.Request("http://127.0.0.1:11435/completion",
                                         data=json.dumps(payload).encode(), headers=headers)
        before = time.monotonic()
        first = None
        chunks = []
        final = {}
        with opener.open(request, timeout=25) as response:
            for raw in response:
                if not raw.startswith(b"data: "):
                    continue
                if raw.strip() == b"data: [DONE]":
                    break
                event = json.loads(raw[6:])
                if event.get("error"):
                    raise RuntimeError("Inference server returned an error")
                content = event.get("content", "")
                if content:
                    if first is None:
                        first = time.monotonic() - before
                    chunks.append(content)
                if event.get("stop"):
                    final = event
        print(json.dumps({
            "case": name, "first_content_seconds": round(first, 3) if first is not None else None,
            "total_seconds": round(time.monotonic() - before, 3),
            "answer": voice.clean_answer("".join(chunks)).strip(),
            "timings": final.get("timings", {}),
            "tokens_cached": final.get("tokens_cached"),
        }, ensure_ascii=False), flush=True)
    pid = int((work / "server.pid").read_text())
    ticks_before = server_ticks(pid)
    idle_start = time.monotonic()
    time.sleep(5)
    cpu_seconds = (server_ticks(pid) - ticks_before) / os.sysconf("SC_CLK_TCK")
    status = Path(f"/proc/{pid}/status").read_text().splitlines()
    rss = next(line for line in status if line.startswith("VmRSS:"))
    print(json.dumps({"idle_test_seconds": round(time.monotonic() - idle_start, 3),
                      "idle_server_cpu_seconds": cpu_seconds, "idle_server_rss": rss}), flush=True)


if __name__ == "__main__":
    main()
