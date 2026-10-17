import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from spanish_wake import WakeStability, words


class WakeStabilityTests(unittest.TestCase):
    def test_accents_and_boundaries(self):
        self.assertEqual(words("¡ANDRÓMEDA!"), ["andromeda"])
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("superandromeda", 1, final=True))
        self.assertTrue(detector.observe("andrómeda", 2, final=True))

    def test_partial_must_stabilize(self):
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("andrómeda", 1.0))
        self.assertFalse(detector.observe("andrómeda", 1.08))
        self.assertTrue(detector.observe("andrómeda pon", 1.18))
        self.assertFalse(detector.observe("andrómeda", 2.0, final=True))

    def test_lost_word_resets_stability(self):
        detector = WakeStability("andrómeda")
        detector.observe("andrómeda", 1.0)
        self.assertFalse(detector.observe("androide", 1.2))
        self.assertFalse(detector.observe("andrómeda", 1.3))

    def test_confidence_guard(self):
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("andrómeda", 1.0, final=True, confidence=0.4))
        self.assertTrue(detector.observe("andrómeda", 2.0, final=True, confidence=0.95))

    def test_reset_allows_new_turn(self):
        detector = WakeStability("andrómeda")
        self.assertTrue(detector.observe("andrómeda", 1, final=True))
        detector.reset()
        self.assertTrue(detector.observe("andrómeda", 1, final=True))


if __name__ == "__main__":
    unittest.main()
