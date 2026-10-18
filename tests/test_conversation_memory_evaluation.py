import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import Mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('memory_comparison', ROOT / 'scripts/evaluate-conversation-memory.py')
memory = importlib.util.module_from_spec(spec); spec.loader.exec_module(memory)


class MemoryComparisonTests(unittest.TestCase):
    def test_variant_changes_only_system_not_questions_or_prior_replies(self):
        history = [{'role': 'user', 'content': 'public question'}, {'role': 'assistant', 'content': 'bad actual reply'}]
        baseline = memory.build_messages(history, 'recall', 'baseline', 'short generic note')
        candidate = memory.build_messages(history, 'recall', 'session_note', 'short generic note')
        self.assertEqual(baseline[1:], candidate[1:])
        self.assertEqual(candidate[0]['content'], baseline[0]['content'] + '\nshort generic note')
        candidate[1]['content'] = 'changed'
        self.assertEqual(history[0]['content'], 'public question')

    def test_history_must_be_bounded_and_have_complete_roles(self):
        for history in [[{'role': 'user', 'content': 'x'}],
                        [{'role': 'assistant', 'content': 'x'}, {'role': 'user', 'content': 'y'}],
                        [{'role': 'user', 'content': 'x'}, {'role': 'assistant', 'content': 'y'}] * 7]:
            with self.subTest(history=history), self.assertRaises(ValueError):
                memory.build_messages(history, 'question', 'baseline', 'note')
        with self.assertRaises(ValueError): memory.build_messages([], 'x', 'unknown', 'note')

    def make_experiment(self):
        model = Mock(); rows = []
        def post(path, body, deadline):
            self.assertEqual(deadline, 23000)
            self.assertNotIn('tools', body)
            if path == '/apply-template': return {'prompt': 'native public prompt'}
            if path == '/tokenize': return {'tokens': [1, 2, 3]}
            return {'choices': [{'message': {'content': 'No recuerdo.'}, 'finish_reason': 'stop'}]}
        model.post.side_effect = post
        return memory.Experiment(model, 'generic note', rows.append, clock=lambda: 1000), rows

    def test_same_payload_limits_deadline_and_shared_history_are_used_for_both(self):
        experiment, rows = self.make_experiment()
        history = [{'role': 'user', 'content': 'q'}, {'role': 'assistant', 'content': 'bad'}]
        pair = experiment.pair('public', history, 'which?', {'kind': 'absence'})
        self.assertEqual(len(rows), 2)
        self.assertEqual(pair['baseline']['shared_conditioning_sha256'], pair['session_note']['shared_conditioning_sha256'])
        calls = [c for c in experiment.model.post.call_args_list if c.args[0] == '/v1/chat/completions']
        for call in calls:
            self.assertEqual(call.args[1]['temperature'], 0)
            self.assertEqual(call.args[1]['max_tokens'], 96)
            self.assertTrue(call.args[1]['cache_prompt'])

    def test_bad_generated_seed_reply_is_preserved_not_replaced_with_expected_answer(self):
        experiment, rows = self.make_experiment()
        history = experiment.seed({'id': 'public', 'questions': ['Q1', 'Q2']})
        self.assertEqual([m['content'] for m in history], ['Q1', 'No recuerdo.', 'Q2', 'No recuerdo.'])
        self.assertEqual(rows[1]['messages'][2]['content'], 'No recuerdo.')

    def test_truncated_public_reply_is_preserved_but_cannot_seed_a_fictional_history(self):
        experiment, rows = self.make_experiment()
        original = experiment.model.post.side_effect
        def post(path, body, deadline):
            result = original(path, body, deadline)
            if path == '/v1/chat/completions': result['choices'][0]['finish_reason'] = 'length'
            return result
        experiment.model.post.side_effect = post
        with self.assertRaises(ValueError): experiment.seed({'id': 'public', 'questions': ['Q1']})
        self.assertFalse(rows[0]['production_reply_allowed'])
        self.assertEqual(rows[0]['response']['content'], 'No recuerdo.')

    def test_mechanical_topic_matches_are_not_semantic_approval(self):
        experiment, rows = self.make_experiment()
        self.assertTrue(memory.mechanical_check('estrella y planeta', {'kind': 'topics', 'groups': [['estrella'], ['planeta']]}))
        self.assertFalse(memory.mechanical_check('estrellado y planeta', {'kind': 'topics', 'groups': [['estrella'], ['planeta']]}))
        self.assertFalse(memory.mechanical_check('No, no las recuerdo.', {'kind': 'ack'}))
        summary = experiment.summary()
        self.assertTrue(summary['semantic_review_required'])
        self.assertFalse(summary['production_changed']); self.assertFalse(summary['voice_acceptance'])

    def test_topic_coincidence_can_be_a_false_positive_and_still_requires_review(self):
        text = 'No tengo memoria de las preguntas anteriores. ¿Quieres que te cuente sobre las estrellas o los planetas?'
        expected = {'kind': 'topics', 'groups': [['estrella', 'estrellas'], ['planeta', 'planetas']]}
        # Preserve the limitation of the scorer, not a manufactured semantic pass.
        self.assertTrue(memory.mechanical_check(text, expected))
        experiment, rows = self.make_experiment()
        experiment.model.post.side_effect = lambda path, body, deadline: (
            {'prompt': 'public'} if path == '/apply-template' else {'tokens': [1]} if path == '/tokenize'
            else {'choices': [{'message': {'content': text}, 'finish_reason': 'stop'}]})
        experiment.pair('public_false_positive', [], 'which?', expected)
        self.assertTrue(all(r['semantic_review_required'] for r in rows))
        self.assertFalse(experiment.summary()['production_changed'])

    def test_fixture_is_one_note_four_topics_and_eight_matched_probes(self):
        fixture = json.loads((ROOT / 'tests/conversation_memory_cases.json').read_bytes())
        self.assertEqual(fixture['model_id'], 'lfm'); self.assertEqual(len(fixture['topics']), 4)
        self.assertEqual(sum(len(t['questions']) for t in fixture['topics']), 9)
        self.assertEqual(len(fixture['topics']) + 2 * sum(t.get('acknowledgement_probe', False) for t in fixture['topics']) + len(fixture['controls']), 8)
        self.assertNotIn('estrella', fixture['candidate_note']); self.assertNotIn('planeta', fixture['candidate_note'])

    def test_coordinator_never_opens_voice_or_changes_runtime_system(self):
        script = (ROOT / 'scripts/test-conversation-memory.sh').read_text()
        self.assertNotIn('doja_stream.py', script); self.assertNotIn('am start', script)
        self.assertIn('660 2048 lfm f16 advanced 0 voice', script)
        self.assertIn('evaluate-conversation-memory.py', script)


if __name__ == '__main__': unittest.main()
