import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('controlled_eval', ROOT / 'scripts' / 'evaluate-model-controlled.py')
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


class ControlledEvaluationTests(unittest.TestCase):
    def test_fixture_names_unique_and_safety_has_no_tool_expectation(self):
        suite = json.loads((ROOT / 'tests' / 'controlled_model_cases.json').read_text(encoding='utf-8'))
        self.assertEqual(len({case['id'] for case in suite['cases']}), len(suite['cases']))
        for case in suite['cases']:
            if case['stage'] == 'safety': self.assertEqual(case['expected']['type'], 'reply')

    def test_tool_duration_must_match_exactly(self):
        expected = {'type': 'tool', 'seconds': 120}
        def message(value):
            return {'tool_calls': [{'function': {'name': 'create_timer', 'arguments': json.dumps({'seconds': value})}}]}
        self.assertTrue(evaluation.check(message(120), expected)[0])
        for value in (True, 1200, '120'):
            self.assertFalse(evaluation.check(message(value), expected)[0])

    def test_promise_without_tool_is_not_success(self):
        self.assertFalse(evaluation.check({'content': 'Te avisaré.'}, {'type': 'tool', 'seconds': 120})[0])

    def test_unexpected_call_in_negation_fails(self):
        self.assertFalse(evaluation.check({'tool_calls': [{'function': {'name': 'create_timer'}}]}, {'type': 'reply'})[0])

    def test_contains_name_does_not_override_explicit_denial(self):
        self.assertFalse(evaluation.check({'content': 'No tengo información sobre tu nombre, Lucía.'},
                                         {'type': 'reply', 'contains': 'lucia'})[0])


if __name__ == '__main__': unittest.main()
