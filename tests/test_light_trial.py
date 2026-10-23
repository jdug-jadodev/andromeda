import pathlib
import unittest

from assistant_tools_model import AssistantToolsModel
from assistant_tools_trial import AssistantToolsTrial
from light_trial import LightTrial, ASK


class LightTrialTests(unittest.TestCase):
    def setUp(self):
        self.clock = [100000]
        self.trial = LightTrial(lambda: self.clock[0])
        self.origin = dict(id='a' * 32, seq=1, speech_end=99900, text='enciende la luz mueble')
        self.confirm = dict(id='b' * 32, seq=2, speech_end=100900, text='sí')

    def test_requires_fresh_heard_confirmation_and_returns_no_command_fields(self):
        question = self.trial.begin(self.origin)
        self.assertEqual(question['text'], ASK)
        self.trial.spoken(self.origin['id'], True)
        grant = self.trial.human(self.confirm, 'c' * 32, 'd' * 32, 200000)
        self.assertTrue(grant['executing'])
        self.assertEqual(grant['event']['request_turn_id'], self.confirm['id'])
        self.assertNotIn('text', grant['event'])
        receipt = self.trial.result({
            'session_id': 'c' * 32, 'context_id': 'd' * 32,
            'nonce': grant['event']['nonce'], 'origin_turn_id': self.origin['id'],
            'origin_sequence': 1, 'request_turn_id': self.confirm['id'],
            'request_sequence': 2, 'status': 'COMPLETE_UNCONFIRMED',
            'target_count': 1, 'completed_target_count': 1,
            'accepted_frame_count': 1, 'detail': 'accepted_by_gatt_stack',
        }, 'c' * 32, 'd' * 32)
        self.assertEqual(receipt['actions_executed'], 1)
        self.assertIn('No tengo confirmación visual', receipt['text'])
        self.assertFalse(self.trial.active)
        self.assertIsNone(self.trial.human(dict(id='e' * 32, seq=3, speech_end=101900, text='otra cosa'),
            'c' * 32, 'd' * 32, 200000))

    def test_no_cancels_without_execution(self):
        self.trial.begin(self.origin); self.trial.spoken(self.origin['id'], True)
        result = self.trial.human(dict(self.confirm, text='no'), 'c' * 32, 'd' * 32, 200000)
        self.assertFalse(result.get('executing', False))
        self.assertEqual(result['timer_control'], 'light_cancelled')
        self.assertIsNone(self.trial.human(self.confirm, 'c' * 32, 'd' * 32, 200000))

    def test_conversation_voice_routes_light_result(self):
        source = (pathlib.Path(__file__).resolve().parents[1] / 'backend' / 'conversation_voice.py').read_text(encoding='utf-8')
        self.assertRegex(source, r"action in \{[^}]*'light_result'[^}]*\} and timers is not None")
        self.assertIn("'light_result'", source)

    def test_model_only_selects_zero_argument_capability(self):
        model = AssistantToolsModel.__new__(AssistantToolsModel)
        model.context_metrics = {}
        model.work_deadline = 200000
        model.clock = lambda: 100000
        model.cancelled = type('Cancelled', (), {'is_set': lambda self: False})()
        model.basic_assistant = model.reminder_deletion = model.reminder_clarification = False
        model.guided_agenda = model.confirmed_volume = model.alarm_lifecycle = False
        result = model.interpret_result({'tool_calls': [{'type': 'function', 'function': {'name': 'control_lights', 'arguments': '{}'}}]}, 'tool_calls', self.origin['text'])
        self.assertEqual(result, {'kind': 'light_proposal'})


if __name__ == '__main__':
    unittest.main()
