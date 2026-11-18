"""Política de fallos de protocolo: un mensaje inválido no apaga Andrómeda."""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
from continuous_voice import ProtocolWindow


class ProtocolWindowTests(unittest.TestCase):
    def test_first_faults_are_tolerated_and_a_streak_is_fatal(self):
        window = ProtocolWindow(limit=3, seconds=600)
        self.assertFalse(window.hit(1000.0))
        self.assertFalse(window.hit(1001.0))
        self.assertTrue(window.hit(1002.0))
        # Cuarto fallo en la misma racha: sigue siendo fatal.
        self.assertTrue(window.hit(1003.0))

    def test_window_expires_and_starts_again(self):
        window = ProtocolWindow(limit=2, seconds=60)
        self.assertFalse(window.hit(1000.0))
        self.assertTrue(window.hit(1001.0))
        self.assertFalse(window.hit(1062.0))
        self.assertTrue(window.hit(1063.0))

    def test_invalid_configuration_and_clock_are_rejected(self):
        for limit, seconds in ((0, 600), (-1, 600), (3, 0), (3, 4000), (True, 600)):
            with self.subTest(limit=limit, seconds=seconds):
                with self.assertRaises(ValueError):
                    ProtocolWindow(limit, seconds)
        window = ProtocolWindow()
        with self.assertRaises(ValueError):
            window.hit('ahora')

    def test_serve_recovers_before_shutting_the_resident_down(self):
        # La rama de recuperación existe y el apagado queda después del contador.
        source = (ROOT / 'backend/conversation_voice.py').read_text(encoding='utf-8')
        self.assertIn('protocol_faults = ProtocolWindow()', source)
        self.assertIn("fault.reason == 'voice_protocol_error' and not protocol_faults.hit(", source)
        recover = source.index('protocol_faults.hit(')
        shutdown = source.index('resident.monitor.reason = fault.reason; resident.shutdown()')
        self.assertLess(recover, shutdown)
        # El camino de recuperación conserva el motor y la escucha.
        branch = source[recover:shutdown]
        self.assertIn('resident.release(model)', branch)
        self.assertIn('continue', branch)


if __name__ == '__main__':
    unittest.main()
