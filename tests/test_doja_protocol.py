import json
from pathlib import Path
import socket
import struct
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from doja_stream import frame, send


class ProtocolTests(unittest.TestCase):
    def test_pcm_frame(self):
        left, right = socket.socketpair()
        with left, right:
            left.sendall(struct.pack("!BI", 2, 4) + b"\x00" * 4)
            self.assertEqual(frame(right), (2, b"\x00" * 4))

    def test_reject_large_frame_without_reading_body(self):
        left, right = socket.socketpair()
        with left, right:
            left.sendall(struct.pack("!BI", 2, 1000000))
            with self.assertRaises(ValueError):
                frame(right)

    def test_utf8_event(self):
        left, right = socket.socketpair()
        with left, right:
            send(left, {"type": "say", "text": "Micrófono apagado"})
            length = struct.unpack("!I", right.recv(4))[0]
            self.assertEqual(json.loads(right.recv(length))["text"], "Micrófono apagado")


if __name__ == "__main__":
    unittest.main()
