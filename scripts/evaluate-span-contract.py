"""Fresh holdout cases for literal duration arguments. No actions or audio."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import time
import urllib.request

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'backend'))
from span_duration import seconds_from_span
spec = importlib.util.spec_from_file_location('controlled', HERE / 'evaluate-model-controlled.py')
controlled = importlib.util.module_from_spec(spec); spec.loader.exec_module(controlled)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('key_path')
    parser.add_argument('--regression', action='store_true'); args = parser.parse_args()
    directory = HERE if (HERE / 'span_contract_cases.json').exists() else HERE.parent / 'tests'
    raw = (directory / ('controlled_model_cases.json' if args.regression else 'span_contract_cases.json')).read_bytes()
    suite = json.loads(raw)
    if args.regression:
        for case in suite['cases']:
            expected = case['expected']
            case['expected'] = {'kind': expected['type'], **{k: v for k, v in expected.items() if k != 'type'}}
            # Only adapt the historical executor fixture's argument format.
            # Its original human request and known success/failure stay intact.
            case['messages'] = copy.deepcopy(case['messages'])
            for message in case['messages']:
                for call in message.get('tool_calls', []):
                    if json.loads(call['function']['arguments']) != {'seconds': 120}:
                        raise ValueError('Unknown historical fixture; refusing silent adaptation')
                    call['function']['arguments'] = json.dumps({'duration_text': 'dos minutos'})
    system = json.loads((directory / 'controlled_model_cases.json').read_text(encoding='utf-8'))['system']
    tool = {'type': 'function', 'function': {'name': 'create_timer',
        'description': 'Solicita una cuenta regresiva. Copia literalmente solo la duración del mensaje actual; no conviertas unidades ni inventes datos.',
        'parameters': {'type': 'object', 'properties': {'duration_text': {'type': 'string'}},
                       'required': ['duration_text'], 'additionalProperties': False}}}
    key = Path(args.key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(path, payload=None, timeout=40): return controlled.post(opener, key, path, payload, timeout)
    ready = time.monotonic() + 30
    while True:
        try: request('/health', timeout=2); break
        except OSError:
            if time.monotonic() >= ready: raise
            time.sleep(.25)
    print(json.dumps({'event': 'literal_span_contract', 'fixture_sha256': hashlib.sha256(raw).hexdigest(),
                      'regression': args.regression,
                      'microphone_opened': False, 'executor_present': False, 'external_network': False}), flush=True)
    outcomes = []
    for case in suite['cases']:
        began = time.monotonic()
        record = {'case': case['id'], 'expected': case['expected']}
        try:
            payload = {'messages': [{'role': 'system', 'content': system}] + case['messages'], 'max_tokens': 64,
                'temperature': 0, 'seed': 42, 'stream': False, 'cache_prompt': True,
                'chat_template_kwargs': {'enable_thinking': False}}
            if case.get('stage') != 'no_tools':
                payload.update(tools=[tool], tool_choice='auto', parallel_tool_calls=False)
            result = request('/v1/chat/completions', payload)
            choice = result['choices'][0]; message = choice['message']; calls = message.get('tool_calls') or []
            record.update(response=message, timings=result.get('timings'))
            if case['expected']['kind'] == 'reply':
                expected = {'type': 'reply'}
                if 'contains' in case['expected']: expected['contains'] = case['expected']['contains']
                if 'contains_any' in case['expected']: expected['contains_any'] = case['expected']['contains_any']
                ok, reason = controlled.check(message, expected)
                record.update(mechanical_pass=ok, reason=reason, semantic_review_required=True)
            else:
                if len(calls) != 1 or calls[0]['function']['name'] != 'create_timer': raise ValueError('Expected one timer call')
                arguments = calls[0]['function']['arguments']
                arguments = json.loads(arguments) if isinstance(arguments, str) else arguments
                if set(arguments) != {'duration_text'}: raise ValueError('Invalid arguments')
                seconds = seconds_from_span(arguments['duration_text'], case['messages'][-1]['content'])
                record.update(mechanical_pass=seconds == case['expected']['seconds'], seconds=seconds,
                              reason='literal_span_checked', semantic_review_required=False)
            if choice.get('finish_reason') == 'length': record.update(mechanical_pass=False, reason='truncated_generation')
        except (OSError, ValueError, TypeError, KeyError) as error:
            record.update(mechanical_pass=False, reason=str(error))
        record['seconds_elapsed'] = round(time.monotonic() - began, 3)
        record['within_compute_budget'] = record['seconds_elapsed'] <= 22
        outcomes.append(record); print(json.dumps(record, ensure_ascii=False), flush=True)
    print(json.dumps({'event': 'span_summary', 'completed': len(outcomes),
                      'mechanical_failures': [r['case'] for r in outcomes if not r['mechanical_pass']],
                      'over_compute_budget': [r['case'] for r in outcomes if not r['within_compute_budget']],
                      'semantic_review_required': True, 'production_approved': False}), flush=True)
    return 0 if all(r['mechanical_pass'] for r in outcomes) else 1


if __name__ == '__main__': sys.exit(main())
