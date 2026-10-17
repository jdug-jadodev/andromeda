import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ablation', ROOT / 'scripts' / 'evaluate-contract-ablation.py')
ablation = importlib.util.module_from_spec(spec); spec.loader.exec_module(ablation)


class AblationTests(unittest.TestCase):
    def message(self, args, name='create_timer'):
        return {'tool_calls': [{'function': {'name': name, 'arguments': json.dumps(args)}}]}

    def test_quantity_is_converted_without_llm_arithmetic(self):
        result = ablation.inspect_response(self.message({'quantity': 3, 'unit': 'minutes'}),
                                           {'kind': 'tool', 'seconds': 180}, 'quantity_unit')
        self.assertTrue(result['mechanical_pass']); self.assertEqual(result['seconds'], 180)

    def test_same_numeric_value_wrong_unit_fails(self):
        result = ablation.inspect_response(self.message({'quantity': 3, 'unit': 'seconds'}),
                                           {'kind': 'tool', 'seconds': 180}, 'quantity_unit')
        self.assertFalse(result['mechanical_pass'])

    def test_tool_in_safe_reply_case_fails(self):
        self.assertFalse(ablation.inspect_response(self.message({'quantity': 3, 'unit': 'minutes'}),
                                                  {'kind': 'reply'}, 'quantity_unit')['mechanical_pass'])

    def test_wrong_tool_and_surplus_parameters_rejected(self):
        for message in [self.message({'seconds': 180}, 'shell'), self.message({'quantity': 3, 'unit': 'minutes', 'command': 'id'})]:
            self.assertFalse(ablation.inspect_response(message, {'kind': 'tool', 'seconds': 180}, 'quantity_unit')['mechanical_pass'])

    def test_holdout_case_ids_unique(self):
        cases = json.loads((ROOT / 'tests' / 'contract_ablation_cases.json').read_text(encoding='utf-8'))['cases']
        self.assertEqual(len(cases), len({case['id'] for case in cases}))


if __name__ == '__main__': unittest.main()
