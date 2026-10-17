import json
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
import doja_stream


class WakeOnlyTests(unittest.TestCase):
    def run_session(self, frames, detector=None, resource=True):
        clock = [0]
        events = []
        detector = detector or Mock(model=object())
        detector.accept_pcm.return_value = True
        recognizer = Mock()
        recognizer.AcceptWaveform.return_value = True
        recognizer.Result.return_value = json.dumps({'text': 'pon un temporizador de diez segundos'})
        vosk = types.SimpleNamespace(KaldiRecognizer=Mock(return_value=recognizer), SetLogLevel=Mock())
        iterator = iter(frames)
        def read(_):
            at, kind, data = next(iterator)
            clock[0] = at
            return kind, data
        with patch.dict(sys.modules, vosk=vosk), patch.object(doja_stream.time, 'monotonic', side_effect=lambda: clock[0]), \
             patch.object(doja_stream, 'frame', side_effect=read), patch.object(doja_stream, 'send', side_effect=lambda _, event: events.append(event)), \
             patch.object(doja_stream, 'resource_ok', return_value=resource), patch.object(doja_stream, 'print'), \
             patch.object(doja_stream, 'parse_command', side_effect=AssertionError('Intent parser must not run')):
            doja_stream.wake_only_session(Mock(), detector, 30)
        return events, detector

    def test_wake_and_transcript_never_produce_action_or_speech(self):
        events, _ = self.run_session([(1, 2, b'\x00' * 640), (2, 2, b'\x00' * 640),
                                      (3, 3, b'{"action":"tool_result","ok":true}'), (4, 3, b'{"action":"stop"}')])
        self.assertTrue(events[0]['wake_only']); self.assertFalse(events[0]['ai_enabled'])
        self.assertEqual(events[1]['trigger'], 'voice')
        self.assertTrue(any(e['type'] == 'transcript' for e in events))
        self.assertTrue(all(e['type'] in {'ready', 'state', 'transcript'} for e in events))

    def test_face_window_returns_to_wake_after_eight_seconds(self):
        events, detector = self.run_session([(1, 2, b'\x00' * 640), (9, 3, b'{"action":"heartbeat"}'),
                                             (9.1, 3, b'{"action":"heartbeat"}'), (10, 3, b'{"action":"stop"}')])
        self.assertTrue(any(e.get('state') == 'wake' for e in events)); detector.reset.assert_called()

    def test_pause_prevents_activation_and_resume_resets(self):
        events, _ = self.run_session([(1, 3, b'{"action":"pause"}'), (2, 2, b'\x00' * 640),
                                      (3, 3, b'{"action":"resume"}'), (4, 2, b'\x00' * 640), (5, 3, b'{"action":"stop"}')])
        self.assertEqual(len([e for e in events if e.get('trigger') == 'voice']), 1)

    def test_manual_activation_is_not_reported_as_voice(self):
        events, _ = self.run_session([(1, 3, b'{"action":"activate"}'), (2, 3, b'{"action":"stop"}')])
        self.assertEqual(events[1]['trigger'], 'button')

    def test_invalid_pcm_is_rejected(self):
        with self.assertRaises(ValueError): self.run_session([(1, 2, b'odd')])

    def test_resource_guard_stops_session(self):
        with self.assertRaises(RuntimeError): self.run_session([(2, 3, b'{"action":"heartbeat"}')], resource=False)


if __name__ == '__main__': unittest.main()
