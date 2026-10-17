"""Device-only integration: all calibration frames are generated zeros, never microphone audio."""
import json
from pathlib import Path
import socket
import struct
import subprocess
import sys
import time

sys.path.insert(0, "/opt/doha-voice/pilot")
from doja_stream import receive
from assistant_config import SETTINGS

if SETTINGS["wake_word"] != "doja":
    print(json.dumps({"test": "legacy_doja_calibration", "skipped": True,
                      "reason": "Assistant renamed; previous calibration retired"}))
    sys.exit(0)

key = Path("/var/lib/doja-voice-pilot/token").read_text().strip()
server = subprocess.Popen([sys.executable, "/opt/doha-voice/pilot/doja_stream.py",
                           "--key", "/var/lib/doja-voice-pilot/token", "--calibrate"],
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
try:
    deadline = time.monotonic() + 40
    while True:
        try:
            conn = socket.create_connection(("127.0.0.1", 11436), timeout=2)
            break
        except OSError:
            if server.poll() is not None or time.monotonic() >= deadline:
                raise RuntimeError("Synthetic calibration server failed to start")
            time.sleep(0.25)
    with conn:
        conn.settimeout(40)
        hello = json.dumps({"token": key}).encode()
        conn.sendall(struct.pack("!BI", 1, len(hello)) + hello)
        length = struct.unpack("!I", receive(conn, 4))[0]
        assert json.loads(receive(conn, length))["type"] == "ready"
        silent_frame = struct.pack("!BI", 2, 640) + b"\x00" * 640
        for _ in range(6 * 7 * 50):
            conn.sendall(silent_frame)
        events = 0
        while True:
            try:
                length = struct.unpack("!I", receive(conn, 4))[0]
                assert length <= 16384
                json.loads(receive(conn, length))
                events += 1
            except EOFError:
                break
    output, error = server.communicate(timeout=10)
    assert server.returncode == 0, error[-1000:]
    result = next(json.loads(line) for line in output.splitlines()
                  if json.loads(line).get("event") == "calibration_result")
    assert all(value == 0 for candidate in result["counts"].values() for value in candidate.values())
    assert events == 7
    print(json.dumps({"test": "synthetic_silence_full_calibration", "passed": True,
                      "microphone_opened": False, "stages": 6, "candidates": 3}))
finally:
    if server.poll() is None:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait()
