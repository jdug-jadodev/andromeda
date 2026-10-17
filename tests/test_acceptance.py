import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from acceptance import evaluate


class AcceptanceTests(unittest.TestCase):
    def setUp(self):
        self.policy = {'clock': 'device_boottime_ms', 'max_useful_response_ms': 30000,
                       'required_samples_per_case': 1, 'required_voice_cases': {'chat': 'chat', 'timer': 'action'}}
        self.chat = dict(case_id='chat', kind='chat', source='real_voice', clock='device_boottime_ms',
                         speech_end_ms=1000, useful_audio_ms=5000, useful_response=True, semantic_pass=True, timeout=False)
        self.timer = dict(self.chat, case_id='timer', kind='action', executor_verified=True, tool_finished_ms=4000)

    def test_complete_correct_evidence_passes(self):
        self.assertTrue(evaluate([self.chat, self.timer], self.policy)['passed'])

    def test_single_slow_case_fails_even_if_most_are_fast(self):
        rows = [self.chat] * 30 + [dict(self.timer, useful_audio_ms=31001)]
        result = evaluate(rows, self.policy)
        self.assertFalse(result['passed'])
        self.assertLess(result['p95_useful_response_ms'], 30000)

    def test_timeout_filler_or_wrong_answer_never_count_as_success(self):
        for change in [{'timeout': True}, {'useful_response': False}, {'semantic_pass': False}]:
            self.assertFalse(evaluate([dict(self.chat, **change), self.timer], self.policy)['passed'])

    def test_simulations_do_not_certify_real_voice(self):
        rows = [dict(self.chat, source='synthetic'), dict(self.timer, source='typed')]
        result = evaluate(rows, self.policy)
        self.assertFalse(result['passed'])
        self.assertEqual(result['real_voice_samples'], 0)

    def test_tool_confirmation_cannot_precede_actual_execution(self):
        for change in [{'executor_verified': False}, {'tool_finished_ms': 5001}, {'tool_finished_ms': None}]:
            self.assertFalse(evaluate([self.chat, dict(self.timer, **change)], self.policy)['passed'])

    def test_cross_clock_and_invalid_timestamps_rejected(self):
        for change in [{'clock': 'wall_time'}, {'speech_end_ms': True}, {'useful_audio_ms': float('nan')},
                       {'useful_audio_ms': float('inf')}, {'useful_audio_ms': -1}, {'useful_audio_ms': 999}]:
            self.assertFalse(evaluate([dict(self.chat, **change), self.timer], self.policy)['passed'])

    def test_proposal_cannot_be_substituted_for_committed_action(self):
        self.assertFalse(evaluate([self.chat, dict(self.timer, kind='proposal')], self.policy)['passed'])

    def test_real_policy_has_complete_30_second_budget(self):
        policy = json.loads((Path(__file__).resolve().parents[1] / 'config' / 'acceptance.json').read_text())
        self.assertEqual(sum(policy['budget_ms'].values()), policy['max_useful_response_ms'])
        self.assertFalse(evaluate([], policy)['passed'])


if __name__ == '__main__': unittest.main()
