import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


routes = load('routes', 'evaluate-inference-routes.py')
prepare = load('prepare_routes', 'prepare-inference-audit.py')
comparison = load('compare_routes', 'compare-inference-routes.py')


class InferenceRouteTests(unittest.TestCase):
    def test_first_difference_including_length(self):
        self.assertIsNone(routes.first_difference([1, 2], [1, 2]))
        self.assertEqual(routes.first_difference([1], [1, 2]), 1)
        self.assertEqual(routes.first_difference('ab', 'ac'), 1)

    def test_sampling_is_preserved_between_routes(self):
        row = {'messages': [{'role': 'user', 'content': 'hola'}], 'actual_tokens': [1, 2]}
        for model in ['qwen17', 'qwen35']:
            a = routes.generation_payload(row, model, 'chat'); b = routes.generation_payload(row, model, 'raw')
            for key in routes.sampling(model): self.assertEqual(a[key], b[key])
            self.assertEqual(a['max_tokens'], b['n_predict'])
            self.assertFalse(a['cache_prompt']); self.assertFalse(b['cache_prompt'])
            self.assertEqual(b['prompt'], [1, 2]); self.assertNotIn('tools', a)
            self.assertNotIn('messages', b)
            self.assertNotIn('chat_template_kwargs', b)

    def test_payload_does_not_mutate_frozen_messages(self):
        row = {'messages': [{'role': 'user', 'content': 'hola'}], 'actual_tokens': [1]}
        body = routes.generation_payload(row, 'qwen17', 'chat')
        body['messages'][0]['content'] = 'changed'
        self.assertEqual(row['messages'][0]['content'], 'hola')

    def test_full_turn_audit_checks_reference_tokens_and_system(self):
        prompt = '<|im_start|>system\ns<|im_end|>\n<|im_start|>user\nu<|im_end|>\n<|im_start|>assistant\n'
        row = {'id': 'x', 'messages': [{'role': 'system', 'content': 's'}, {'role': 'user', 'content': 'u'}],
               'official_prompt': prompt, 'embedded_prompt': prompt, 'official_tokens': [1, 2]}
        def request(path, body):
            if path == '/apply-template': return {'prompt': prompt}
            self.assertTrue(body['parse_special']); return {'tokens': [1, 2]}
        record = routes.audit_prompt(row, request)
        self.assertTrue(record['official_prompt_equal']); self.assertTrue(record['official_tokenizer_equal'])
        self.assertTrue(record['system_preserved']); self.assertEqual(row['actual_tokens'], [1, 2])

    def test_original_response_is_not_rewritten_by_diagnostic(self):
        text = 'Mi gata se llama Menta.'
        result = {'choices': [{'message': {'role': 'assistant', 'content': text}, 'finish_reason': 'stop'}]}
        row = routes.summarize_response(result, 'chat')
        self.assertEqual(row['text'], text); self.assertFalse(row['local_parser_changed_text'])

    def test_histories_retain_real_bad_reply_not_an_ideal_answer(self):
        rows = [{'conversation': 'a', 'turn': 'one', 'input': 'intro', 'expected': {},
                 'response': {'role': 'assistant', 'content': 'wrong'}},
                {'conversation': 'a', 'turn': 'two', 'input': 'question', 'expected': {},
                 'response': {'role': 'assistant', 'content': 'bad'}}]
        frozen = prepare.freeze_histories('s', rows, 'p')
        self.assertEqual(frozen[1]['messages'][-2]['content'], 'wrong')
        self.assertEqual(len(frozen[0]['messages']), 2)

    def test_trace_transport_is_repaired_only_reversibly(self):
        data = json.dumps({'text': 'Andrómeda'}, ensure_ascii=False).encode('utf-8').decode('cp850')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'trace'
            path.write_text(data, encoding='utf-16')
            rows, source = prepare.read_trace(path)
        self.assertEqual(rows, [{'text': 'Andrómeda'}]); self.assertTrue(source['reversed_cp850_transport'])

    def test_source_does_not_open_voice_or_register_executor(self):
        text = (ROOT / 'scripts' / 'evaluate-inference-routes.py').read_text()
        self.assertNotIn('ConversationAgent(', text)
        self.assertNotIn('doja_stream', text)
        self.assertIn("'production_approved': False", text)

    def test_numeric_comparison_rejects_changed_conditioning(self):
        a = {'messages_sha256': 'a', 'prompt_tokens_sha256': 'b'}
        with self.assertRaises(ValueError): comparison.compare(a, dict(a, messages_sha256='changed'))

    def test_partial_top_probabilities_do_not_claim_full_distribution(self):
        a = {'messages_sha256': 'a', 'prompt_tokens_sha256': 'b', 'seconds': 1, 'text': 'x',
             'probabilities_head': [{'top_logprobs': [{'bytes': [65], 'logprob': -1.}]}]}
        b = dict(a, probabilities_head=[{'top_logprobs': [{'bytes': [65], 'logprob': -1.01}]}])
        value = comparison.compare(a, b)
        self.assertAlmostEqual(value['max_common_first_token_logprob_delta'], .01)
        self.assertFalse(value['full_vocabulary_distribution_compared'])
        self.assertTrue(value['semantic_review_required'])

    def test_greedy_control_only_changes_temperature(self):
        row = {'messages': [{'role': 'user', 'content': 'hi'}], 'actual_tokens': [1, 2]}
        for model in ['qwen17', 'qwen35']:
            a = routes.generation_payload(row, model, 'raw')
            b = routes.generation_payload(row, model, 'raw', 'greedy')
            self.assertEqual(b['temperature'], 0.)
            a.pop('temperature'); b.pop('temperature')
            self.assertEqual(a, b)

    def test_stable_history_adds_only_empty_blocks_to_prior_assistant_turns(self):
        header = '<|im_start|>assistant\n'; empty = '<think>\n\n</think>\n\n'
        prompt = '<|im_start|>user\nquestion<|im_end|>\n' + header + 'wrong reply<|im_end|>\n' + header + empty
        changed = routes.stable_history_prompt(prompt)
        self.assertEqual(changed, prompt.replace(header + 'wrong reply', header + empty + 'wrong reply'))
        self.assertEqual(changed.count('wrong reply'), 1)
        self.assertTrue(changed.endswith(header + empty))

    def test_stable_history_is_identical_on_single_turn_controls(self):
        prompt = '<|im_start|>user\nquestion<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n'
        self.assertEqual(routes.stable_history_prompt(prompt), prompt)
        with self.assertRaises(ValueError): routes.stable_history_prompt('no prefix')

    def test_worker_captures_optional_arguments_before_resetting_positionals(self):
        script = (ROOT / 'scripts' / 'warm-server-worker.sh').read_text()
        self.assertLess(script.index('LOG_MODE=${9:'), script.index('set --'))

    def test_language_factorial_independently_crosses_two_languages(self):
        fixture = json.loads((ROOT / 'tests' / 'language_control_cases.json').read_bytes())
        rows = prepare.language_histories(fixture)
        self.assertEqual(len(rows), 8)
        for row in rows:
            _, system_language, user_language, case_id = row['id'].split('/')
            self.assertEqual(row['messages'][0]['content'], fixture['systems'][system_language])
            case = next(c for c in fixture['cases'] if c['id'] == case_id)
            self.assertEqual(row['messages'][1]['content'], case['questions'][user_language])
            self.assertEqual([m['role'] for m in row['messages']], ['system', 'user'])
        original = json.loads((ROOT / 'tests' / 'conversation_only_cases.json').read_bytes())
        self.assertEqual(fixture['systems']['es'], original['system'])

    def test_generic_reference_caps_output_not_conditioning(self):
        row = {'messages': [{'role': 'user', 'content': 'hi'}], 'actual_tokens': [1, 2]}
        normal = routes.generation_payload(row, 'qwen35', 'raw')
        generic = routes.generation_payload(row, 'qwen35', 'raw', 'generic')
        self.assertEqual(generic.pop('n_predict'), 32)
        self.assertEqual(normal.pop('n_predict'), 96)
        self.assertEqual(normal, generic)


if __name__ == '__main__': unittest.main()
