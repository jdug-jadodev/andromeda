import copy
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('identity_cache', ROOT / 'scripts' / 'evaluate-identity-cache.py')
control = importlib.util.module_from_spec(spec); spec.loader.exec_module(control)


def response(text, cache=0):
    return {'choices': [{'finish_reason': 'stop', 'message': {'role': 'assistant', 'content': text}}], 'timings': {'cache_n': cache}}


class IdentityCacheControlTests(unittest.TestCase):
    def test_repeats_only_change_cache_flag_not_history_or_parameters(self):
        seen = []
        def request(path, body):
            if path == '/apply-template': return {'prompt': 'fixed rendered prompt'}
            seen.append(copy.deepcopy(body)); return response('Texto generado.')
        question = {'text': '¿Nombre?', 'expected': {'type': 'reply'}}
        rows = list(control.probe('system', 'hola', question, request))
        self.assertEqual([r['cache_requested'] for r in rows], [True, False, True])
        canonical = []
        for query in seen[1:]: query.pop('cache_prompt'); canonical.append(query)
        self.assertEqual(canonical[0], canonical[1]); self.assertEqual(canonical[1], canonical[2])
        self.assertEqual(canonical[0]['messages'][2]['content'], 'Texto generado.')
        self.assertEqual(len({r['prompt_sha256'] for r in rows}), 1)

    def test_recomputation_must_be_verified_not_assumed_from_request(self):
        def request(path, body):
            return {'prompt': 'fixed'} if path == '/apply-template' else response('Hola.', cache=10)
        rows = list(control.probe('system', 'hola', {'text': 'sigue', 'expected': {'type': 'reply'}}, request))
        self.assertFalse(rows[1]['mechanical_pass'])
        self.assertEqual(rows[1]['reason'], 'full_recomputation_not_verified')
