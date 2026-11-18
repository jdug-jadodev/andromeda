import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from spanish_wake import WakeStability, words


class WakeStabilityTests(unittest.TestCase):
    def test_accents_and_boundaries(self):
        self.assertEqual(words("¡ANDRÓMEDA!"), ["andromeda"])
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("superandromeda", 1, final=True, confidence=0.95))
        self.assertTrue(detector.observe("andrómeda", 2, final=True, confidence=0.95))

    def test_partial_must_stabilize(self):
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("andrómeda", 1.0, confidence=0.95))
        self.assertFalse(detector.observe("andrómeda", 1.08, confidence=0.95))
        self.assertTrue(detector.observe("andrómeda pon", 1.26, confidence=0.95))
        self.assertFalse(detector.observe("andrómeda", 2.0, final=True, confidence=0.95))

    def test_lost_word_resets_stability(self):
        detector = WakeStability("andrómeda")
        detector.observe("andrómeda", 1.0, confidence=0.95)
        self.assertFalse(detector.observe("androide", 1.2, confidence=0.95))
        self.assertFalse(detector.observe("andrómeda", 1.3, confidence=0.95))

    def test_confidence_guard(self):
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("andrómeda", 1.0, final=True, confidence=0.4))
        self.assertTrue(detector.observe("andrómeda", 2.0, final=True, confidence=0.95))

    def test_confidence_is_required(self):
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("andrómeda", 1.0, final=True, confidence=None))
        self.assertTrue(detector.observe("andrómeda", 2.0, final=True, confidence=0.95))

    def test_prefix_only_requires_the_first_word(self):
        detector = WakeStability("andrómeda")
        self.assertFalse(detector.observe("hola andrómeda", 1.0, final=True, confidence=0.95))
        self.assertFalse(detector.observe("ayer andrómeda dijo", 2.0, final=True, confidence=0.95))
        self.assertTrue(detector.observe("andrómeda", 3.0, final=True, confidence=0.95))
        detector.reset()
        self.assertTrue(detector.observe("oye andrómeda", 4.0, final=True, confidence=0.95))

    def test_middle_allowed_if_configured(self):
        detector = WakeStability("andrómeda", prefix_only=False)
        self.assertTrue(detector.observe("hola andrómeda", 1.0, final=True, confidence=0.95))

    def test_reset_allows_new_turn(self):
        detector = WakeStability("andrómeda")
        self.assertTrue(detector.observe("andrómeda", 1, final=True, confidence=0.95))
        detector.reset()
        self.assertTrue(detector.observe("andrómeda", 1, final=True, confidence=0.95))


if __name__ == "__main__":
    unittest.main()
