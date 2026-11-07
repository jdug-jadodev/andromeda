import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
import remote_voice


class RemoteVoiceTests(unittest.TestCase):
    def contract(self):
        base = ROOT / 'contract' / 'home-api-v1'
        policies = json.loads((base / 'policies.json').read_text(encoding='utf-8'))
        limits = json.loads((base / 'limits.json').read_text(encoding='utf-8'))
        config = json.loads((ROOT / 'config' / 'home_gateway.json').read_text(encoding='utf-8'))
        return policies, limits, config

    def test_contract_matches(self):
        policies, limits, _ = self.contract()
        self.assertEqual(remote_voice.POLICY, policies['remote_voice'])
        self.assertEqual(remote_voice.MAX_TEXT, limits['command_chars'])
        self.assertEqual(remote_voice.MAX_QUEUE, limits['command_queue'])
        self.assertEqual(remote_voice.WAIT_MS, limits['command_wait_ms'])
        self.assertEqual(remote_voice.REPLY_CACHE_MS, limits['command_reply_cache_ms'])
        self.assertEqual(remote_voice.WAV_MAX_MS, limits['wav_max_ms'])

    def test_gateway_defaults_are_off(self):
        _, _, config = self.contract()
        self.assertFalse(config['enabled'])
        self.assertFalse(config['remote_voice_enabled'])
        self.assertFalse(config['messages_enabled'])
        self.assertFalse(config['camera_enabled'])
        self.assertFalse(config['allow_loopback'])

    def test_valid_command(self):
        parsed = remote_voice.command({
            'id': 'cmd-0001', 'text': '  apaga las luces  ',
            'client_ts_ms': 1760000000000, 'want_wav': True,
        })
        self.assertEqual('apaga las luces', parsed['text'])
        self.assertEqual('remote', parsed['origin'])
        self.assertTrue(parsed['want_wav'])

    def test_rejects_bad_command(self):
        base = {'id': 'cmd-0001', 'text': 'hola', 'client_ts_ms': 1}
        for change in (
            {'id': 'corto'},
            {'id': 'cmd 0001'},
            {'text': ''},
            {'text': 'x' * 501},
            {'text': 'hola\x00'},
            {'client_ts_ms': 0},
            {'client_ts_ms': True},
            {'want_wav': 'si'},
            {'extra': 1},
        ):
            with self.subTest(change=change), self.assertRaises(remote_voice.RemoteCommandFault):
                remote_voice.command({**base, **change})

    def test_frame(self):
        frame = {
            'action': 'remote_command', 'id': 'cmd-0001',
            'text': 'apaga las luces', 'client_ts_ms': 1, 'want_wav': False,
        }
        self.assertEqual('apaga las luces', remote_voice.frame(frame)['text'])
        for change in ({'action': 'local_command'}, {'extra': 1}, {'text': ''}):
            with self.subTest(change=change), self.assertRaises(remote_voice.RemoteCommandFault):
                remote_voice.frame({**frame, **change})

    def test_tools(self):
        self.assertTrue(remote_voice.tool_allowed('control_lights'))
        self.assertTrue(remote_voice.tool_allowed('create_timer'))
        self.assertFalse(remote_voice.tool_allowed('self_destruct'))
        self.assertFalse(remote_voice.tool_allowed('camera'))
        self.assertFalse(remote_voice.tool_allowed('configure'))

    def test_confirmations_do_not_cross(self):
        self.assertTrue(remote_voice.may_confirm('remote', 'remote'))
        self.assertTrue(remote_voice.may_confirm('local', 'local'))
        self.assertFalse(remote_voice.may_confirm('local', 'remote'))
        self.assertFalse(remote_voice.may_confirm('remote', 'local'))
        self.assertFalse(remote_voice.may_confirm('unknown', 'unknown'))

    def test_deadline(self):
        self.assertEqual(31000, remote_voice.deadline(1000))
        with self.assertRaises(remote_voice.RemoteCommandFault):
            remote_voice.deadline(0)

    def test_encode_is_frame_six_payload(self):
        payload = json.loads(remote_voice.encode({
            'id': 'cmd-0001', 'text': 'hola', 'client_ts_ms': 1,
        }).decode('utf-8'))
        self.assertEqual('remote_command', payload['action'])
        self.assertEqual('cmd-0001', payload['id'])
        self.assertTrue(payload['want_wav'])


if __name__ == '__main__':
    unittest.main()
