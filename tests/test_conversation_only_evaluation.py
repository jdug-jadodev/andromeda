import importlib.util
import hashlib
import json
import contextlib
import io
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('chat_only', ROOT / 'scripts' / 'evaluate-conversation-only.py')
chat = importlib.util.module_from_spec(spec); spec.loader.exec_module(chat)


def response(text, **extra):
    return {'choices': [{'finish_reason': 'stop', 'message': {'role': 'assistant', 'content': text, **extra}}]}


class ConversationOnlyEvaluationTests(unittest.TestCase):
    def test_trace_output_preserves_unicode_through_native_console_encodings(self):
        record = {'text': 'Andrómeda, Óscar y tú. 🐱'}
        output = io.StringIO()
        with contextlib.redirect_stdout(output): chat.emit(record)
        encoded = output.getvalue()
        self.assertTrue(encoded.isascii())
        self.assertEqual(json.loads(encoded.encode('ascii').decode('cp850')), record)

    def test_no_catalog_or_tool_selection_reaches_api(self):
        value = chat.payload([{'role': 'user', 'content': 'hola'}], 'qwen_non_thinking')
        self.assertNotIn('tools', value); self.assertNotIn('tool_choice', value)
        self.assertEqual(value['temperature'], .7)
        self.assertFalse(value['chat_template_kwargs']['enable_thinking'])

    def test_qwen35_official_text_non_thinking_sampling_reaches_api(self):
        value = chat.payload([], 'qwen35_non_thinking')
        self.assertEqual((value['temperature'], value['top_p'], value['top_k'], value['min_p']), (1., 1., 20, 0))
        self.assertEqual(value['presence_penalty'], 2.)
        self.assertEqual(value['repeat_penalty'], 1.)
        self.assertFalse(value['chat_template_kwargs']['enable_thinking'])
        self.assertNotIn('tools', value)

    def test_qwen35_candidate_uses_unchanged_cases_and_output_limit(self):
        candidate = json.loads((ROOT / 'config' / 'qwen35_candidate.json').read_text(encoding='utf-8'))
        for case in candidate['cases']:
            self.assertEqual(hashlib.sha256((ROOT / case['path']).read_bytes()).hexdigest(), case['sha256'])
        value = chat.payload([], candidate['sampling_profile'])
        self.assertEqual(value['max_tokens'], candidate['runtime']['max_output_tokens'])

    def test_qwen35_penalty_control_changes_exactly_one_sampling_parameter(self):
        base = chat.payload([], 'qwen35_non_thinking')
        control = chat.payload([], 'qwen35_no_penalty')
        self.assertEqual(control['presence_penalty'], 0.)
        base.pop('presence_penalty'); control.pop('presence_penalty')
        self.assertEqual(base, control)

    def test_generated_replies_become_history_without_repairs(self):
        requests = []
        def request(body):
            requests.append(body); return response('Texto realmente generado.')
        cases = [{'id': str(i), 'text': 'hola', 'expected': {'type': 'reply'}} for i in range(2)]
        records = list(chat.evaluate_conversation('system', cases, request, 'greedy'))
        self.assertEqual(requests[1]['messages'][2]['content'], 'Texto realmente generado.')
        self.assertEqual(len(records), 2)

    def test_invalid_output_aborts_instead_of_injecting_a_fixture_repair(self):
        cases = [{'id': str(i), 'text': 'hola', 'expected': {'type': 'reply'}} for i in range(2)]
        records = list(chat.evaluate_conversation('system', cases, lambda _: response('{"name":"create_timer","arguments":{}}'), 'greedy'))
        self.assertEqual(len(records), 1); self.assertTrue(records[0]['history_continuation_aborted'])

    def test_no_history_shared_between_conversations(self):
        seen = []
        def request(body): seen.append(body); return response('Hola.')
        case = [{'id': 'a', 'text': 'hola', 'expected': {'type': 'reply'}}]
        list(chat.evaluate_conversation('system', case, request, 'greedy'))
        list(chat.evaluate_conversation('system', case, request, 'greedy'))
        self.assertEqual(len(seen[0]['messages']), len(seen[1]['messages']))

    def test_native_tool_calls_are_not_accepted_as_prose(self):
        with self.assertRaises(ValueError): chat.reply(response('listo', tool_calls=[{}]))

    def test_truncation_is_not_accepted(self):
        value = response('incompleto'); value['choices'][0]['finish_reason'] = 'length'
        with self.assertRaises(ValueError): chat.reply(value)

    def test_precision_control_fixture_is_frozen(self):
        control = json.loads((ROOT / 'config' / 'model_precision_control.json').read_text())
        self.assertEqual(hashlib.sha256((ROOT / control['fixture']).read_bytes()).hexdigest(), control['fixture_sha256'])
        self.assertEqual(control['shared_settings']['max_output_tokens'], chat.payload([], 'qwen_non_thinking')['max_tokens'])

    def test_non_text_content_is_rejected(self):
        with self.assertRaises(ValueError): chat.reply(response(['not', 'text']))

    def test_name_is_not_also_proof_of_shorter_nickname(self):
        message = {'content': 'Tú eres Camila.'}
        ok, reason = chat.sequential.check_reply(message, {'type': 'reply', 'contains_all': ['camila', 'cami']})
        self.assertFalse(ok); self.assertEqual(reason, 'missing_conversation_fact')
        self.assertTrue(chat.sequential.check_reply({'content': 'Te llamas Camila y prefieres Cami.'},
                        {'type': 'reply', 'contains_all': ['camila', 'cami']})[0])

    def test_forbidden_name_does_not_match_inside_an_unrelated_word(self):
        self.assertTrue(chat.sequential.check_reply({'content': 'Óscar, hay alternativas.'},
                        {'type': 'reply', 'contains': 'oscar', 'forbids': ['nati']})[0])

    def test_identity_contract_is_global_and_not_a_fixture_answer(self):
        identity = json.loads((ROOT / 'config' / 'conversation_identity.json').read_text(encoding='utf-8'))['system']
        self.assertIn('El usuario es otra persona', identity)
        for name in ['Natalia', 'Roco', 'Óscar', 'Camila', 'Menta', 'Hugo', 'Laura', 'Hache']:
            self.assertNotIn(name, identity)
        self.assertLess(len(identity), 500)

    def test_new_identity_holdouts_are_independent_and_all_reply_only(self):
        suite = json.loads((ROOT / 'tests' / 'identity_holdout_cases.json').read_text(encoding='utf-8'))
        self.assertEqual(len(suite['conversations']), 2)
        self.assertEqual(sum(len(c['turns']) for c in suite['conversations']), 8)
        self.assertTrue(all(t['expected']['type'] == 'reply' for c in suite['conversations'] for t in c['turns']))


if __name__ == '__main__': unittest.main()
