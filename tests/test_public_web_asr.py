import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import Mock
import wave

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
spec = importlib.util.spec_from_file_location('public_web_asr', ROOT / 'scripts/evaluate-web-asr.py')
public = importlib.util.module_from_spec(spec); spec.loader.exec_module(public)


class PublicWebAsrTests(unittest.TestCase):
    def fixture(self, directory, rate=16000):
        path = Path(directory) / public.FIXTURE_NAME
        with wave.open(str(path), 'wb') as audio:
            audio.setparams((1, 2, rate, 0, 'NONE', 'not compressed')); audio.writeframes(b'\0' * 640)
        manifest = {'fixture_text': public.FIXTURE_TEXT, 'offline_synthesis': True,
                    'wav_sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
        path.with_suffix('.json').write_text(json.dumps(manifest))
        return path, manifest

    def test_only_fixed_public_bounded_pcm_and_matching_manifest_are_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            path, manifest = self.fixture(directory)
            self.assertEqual(public.public_pcm(path), b'\0' * 640)
            for key, value in [('fixture_text', 'otro texto'), ('offline_synthesis', False), ('wav_sha256', 'wrong')]:
                path.with_suffix('.json').write_text(json.dumps(dict(manifest, **{key: value})))
                with self.subTest(key=key), self.assertRaises(ValueError): public.public_pcm(path)
            path, _ = self.fixture(directory, 22050)
            with self.assertRaises(ValueError): public.public_pcm(path)
            with self.assertRaises(ValueError): public.public_pcm(Path(directory) / 'human.wav')

    def test_client_cannot_emit_execution_or_tts_receipts(self):
        conn = Mock()
        for action in ['agenda_result', 'timer_result', 'tts_started', 'tts_done', 'activate']:
            with self.subTest(action=action), self.assertRaises(ValueError): public.control(conn, action)
        conn.sendall.assert_not_called()
        public.control(conn, 'stop')
        raw = conn.sendall.call_args.args[0]
        self.assertEqual(raw[0], 3); self.assertEqual(json.loads(raw[5:]), {'action': 'stop'})

    def test_event_reader_handles_fragmented_frames_and_rejects_oversized_events(self):
        conn = Mock(); raw = json.dumps({'type': 'state'}).encode()
        conn.recv.side_effect = [struct.pack('!I', len(raw))[:2], struct.pack('!I', len(raw))[2:], raw[:3], raw[3:]]
        self.assertEqual(public.read_event(conn), {'type': 'state'})
        conn.recv.side_effect = [struct.pack('!I', 16385)]
        with self.assertRaises(ValueError): public.read_event(conn)
        conn.recv.side_effect = [b'']
        with self.assertRaises(EOFError): public.read_event(conn)

    def test_regression_checks_require_exact_fields_and_never_accept_mutation_as_no_action(self):
        self.assertTrue(public.correct_result({'kind': 'tools_rejected'}, {'kind': 'no_action'}))
        for result in [None, {'kind': 'agenda_proposal'}, {'kind': 'web_result'}, {'kind': 'timer_proposal'}]:
            self.assertFalse(public.correct_result(result, {'kind': 'no_action'}))
        self.assertTrue(public.correct_result({'kind': 'timer_proposal', 'seconds': 120}, {'kind': 'timer_proposal', 'seconds': 120}))
        self.assertFalse(public.correct_result({'kind': 'timer_proposal', 'seconds': True}, {'kind': 'timer_proposal', 'seconds': 1}))
        self.assertEqual(len(public.REGRESSIONS), 9)

    def test_public_diagnostics_stub_the_current_provider_not_an_unused_legacy_name(self):
        for filename in ('evaluate-web-asr.py', 'evaluate-assistant-tools.py', 'evaluate-web-answers.py'):
            source = (ROOT/'scripts'/filename).read_text(encoding='utf-8')
            self.assertIn('adapter.buscar_en_internet = ', source)
            self.assertNotIn('adapter.search = ', source)


if __name__ == '__main__': unittest.main()
