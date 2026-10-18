"""One frozen public replay: native history versus history in the current JSON turn."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import time

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('memory_comparison', HERE / 'evaluate-conversation-memory.py')
comparison = importlib.util.module_from_spec(spec); spec.loader.exec_module(comparison)
VARIANTS = ('baseline', 'structured_context')


def public_cases(fixture):
    if fixture['version'] != 1 or fixture['model_id'] != 'lfm': raise ValueError('Expected fixed LFM-only replay')
    for case in fixture['cases']:
        history = copy.deepcopy(fixture['histories'][case['history']])
        if 'history_pairs' in case:
            count = case['history_pairs']
            if type(count) is not int or not 0 <= count <= len(history) // 2: raise ValueError('Invalid replay subset')
            history = history[:count * 2]
        comparison.build_messages(history, case['text'], 'baseline', '')
        yield case, history


def main():
    if len(sys.argv) != 2: raise ValueError('Expected private inference key path only')
    path = HERE / 'structured_context_cases.json'
    if not path.exists(): path = HERE.parent / 'tests/structured_context_cases.json'
    raw = path.read_bytes(); fixture = json.loads(raw)
    cases = list(public_cases(fixture))
    if len(cases) != 10: raise ValueError('Expected exactly ten frozen paired probes')
    model = comparison.ConversationModel(sys.argv[1])
    experiment = comparison.Experiment(model, fixture['candidate_note'], variants=VARIANTS)
    comparison.emit({'event': 'structured_context_started', 'fixture_sha256': hashlib.sha256(raw).hexdigest(),
        'source_public_report_sha256': fixture['source_public_report_sha256'],
        'source_synthetic_report_sha256': fixture['source_synthetic_report_sha256'],
        'candidate_note': fixture['candidate_note'], 'model': 'lfm', 'variants': VARIANTS,
        'context_tokens': 2048, 'input_tokens': 1536, 'output_tokens': 96, 'temperature': 0, 'seed': 42,
        'max_experiment_seconds': 240, 'matched_public_dialogue': True, 'payload_representation_differs': True,
        'microphone_opened': False, 'tools_present': False, 'executor_present': False,
        'external_network': False, 'public_fixture_only': True})
    try:
        started = time.monotonic()
        for variant in VARIANTS: experiment.call([], 'Hola.', variant, maximum=1)
        comparison.emit({'event': 'structured_prefix_preparation', 'seconds': round(time.monotonic() - started, 3), 'scored': False})
        for case, history in cases:
            experiment.pair(case['id'], history, case['text'], case['expected'])
        summary = experiment.summary(); summary['event'] = 'structured_context_summary'
        comparison.emit(summary)
    finally:
        model.cancel()


if __name__ == '__main__': main()
