"""One controlled contrast: native template + seconds vs quantity/unit.

Fixed public text. No microphone, external sources, Android executor or actions.
No prompt changes per case; no production/semantic approval from string matches.
"""
import argparse
import difflib
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import time
import urllib.request

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'backend'))
from durations import duration_seconds

spec = importlib.util.spec_from_file_location('controlled', HERE / 'evaluate-model-controlled.py')
controlled = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controlled)


def quantity_tool():
    return {'type': 'function', 'function': {'name': 'create_timer',
        'description': 'Solicita crear una cuenta regresiva. Expresa la cantidad y unidad indicadas por el usuario.',
        'parameters': {'type': 'object', 'properties': {'quantity': {'type': 'number', 'exclusiveMinimum': 0},
            'unit': {'type': 'string', 'enum': ['seconds', 'minutes', 'hours']}},
            'required': ['quantity', 'unit'], 'additionalProperties': False}}}


def inspect_response(message, expected, variant):
    calls = message.get('tool_calls') or []
    if expected['kind'] == 'reply':
        exp = dict(type='reply')
        if 'contains' in expected: exp['contains'] = expected['contains']
        ok, reason = controlled.check(message, exp)
        return {'mechanical_pass': ok, 'reason': reason, 'semantic_review_required': True}
    if len(calls) != 1 or calls[0].get('function', {}).get('name') != 'create_timer':
        return {'mechanical_pass': False, 'reason': 'expected_one_timer_call', 'semantic_review_required': True}
    try:
        args = calls[0]['function']['arguments']
        args = json.loads(args) if isinstance(args, str) else args
        if variant == 'seconds':
            if set(args) != {'seconds'} or type(args['seconds']) is not int:
                raise ValueError('Invalid seconds')
            seconds = duration_seconds(args['seconds'], 'seconds')
        else:
            if set(args) != {'quantity', 'unit'}: raise ValueError('Invalid quantity/unit')
            seconds = duration_seconds(args['quantity'], args['unit'])
        return {'mechanical_pass': seconds == expected['seconds'], 'seconds': seconds,
                'reason': 'exact_duration' if seconds == expected['seconds'] else 'wrong_duration',
                'semantic_review_required': False}
    except (ValueError, KeyError, TypeError):
        return {'mechanical_pass': False, 'reason': 'invalid_duration', 'semantic_review_required': False}


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('key_path'); args = parser.parse_args()
    fixture_dir = HERE if (HERE / 'controlled_model_cases.json').exists() else HERE.parent / 'tests'
    baseline_raw = (fixture_dir / 'controlled_model_cases.json').read_bytes()
    holdout_raw = (fixture_dir / 'contract_ablation_cases.json').read_bytes()
    baseline, holdout = json.loads(baseline_raw), json.loads(holdout_raw)
    key = Path(args.key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(path, payload=None, timeout=40): return controlled.post(opener, key, path, payload, timeout)
    deadline = time.monotonic() + 30
    while True:
        try: request('/health', timeout=2); break
        except OSError:
            if time.monotonic() >= deadline: raise
            time.sleep(.25)
    tool_variants = {'seconds': baseline['tool'], 'quantity_unit': quantity_tool()}
    print(json.dumps({'event': 'contract_ablation', 'fixture_sha256': hashlib.sha256(holdout_raw).hexdigest(),
                      'baseline_sha256': hashlib.sha256(baseline_raw).hexdigest(),
                      'microphone_opened': False, 'executor_present': False, 'external_network': False}), flush=True)
    memory = holdout['cases'][0]['messages']
    base_messages = [{'role': 'system', 'content': baseline['system']}] + memory
    rendered = {}
    for name, tool in [('none', None)] + list(tool_variants.items()):
        payload = {'messages': base_messages, 'add_generation_prompt': True,
                   'chat_template_kwargs': {'enable_thinking': False}}
        if tool is not None: payload['tools'] = [tool]
        result = request('/apply-template', payload)
        prompt = result['prompt']
        rendered[name] = prompt
        print(json.dumps({'event': 'rendered_prompt', 'variant': name, 'prompt': prompt,
                          'roles': re.findall(r'<\|im_start\|>(\w+)', prompt),
                          'latest_question_preserved': memory[-1]['content'] in prompt,
                          'name_preserved': 'Lucía' in prompt}, ensure_ascii=False), flush=True)
    diff = '\n'.join(difflib.unified_diff(rendered['none'].splitlines(), rendered['seconds'].splitlines(),
                                        fromfile='without_tools', tofile='seconds_tool', lineterm=''))
    print(json.dumps({'event': 'template_diff', 'diff': diff}, ensure_ascii=False), flush=True)
    started, outcomes = time.monotonic(), []
    for variant, tool in tool_variants.items():
        for case in holdout['cases']:
            if time.monotonic() - started > 300: break
            payload = {'messages': [{'role': 'system', 'content': baseline['system']}] + case['messages'],
                       'tools': [tool], 'tool_choice': 'auto', 'parallel_tool_calls': False,
                       'max_tokens': 64, 'temperature': 0, 'seed': 42, 'stream': False,
                       'cache_prompt': True, 'chat_template_kwargs': {'enable_thinking': False}}
            began = time.monotonic()
            record = {'case': case['id'], 'variant': variant, 'expected': case['expected']}
            try:
                result = request('/v1/chat/completions', payload)
                choice = result['choices'][0]
                record.update(inspect_response(choice['message'], case['expected'], variant))
                record.update(response=choice['message'], timings=result.get('timings'))
                if choice.get('finish_reason') == 'length':
                    record.update(mechanical_pass=False, reason='truncated_generation')
            except (OSError, ValueError, KeyError, TypeError) as error:
                record.update(mechanical_pass=False, reason=type(error).__name__)
            record['seconds_elapsed'] = round(time.monotonic() - began, 3)
            record['within_compute_budget'] = record['seconds_elapsed'] <= 22
            outcomes.append(record)
            print(json.dumps(record, ensure_ascii=False), flush=True)
    print(json.dumps({'event': 'ablation_summary', 'completed': len(outcomes), 'planned': 12,
                      'mechanical_failures': [(r['variant'], r['case']) for r in outcomes if not r['mechanical_pass']],
                      'over_compute_budget': [(r['variant'], r['case']) for r in outcomes if not r['within_compute_budget']],
                      'semantic_review_required': True, 'production_approved': False}), flush=True)
    return 0 if len(outcomes) == 12 and all(r['mechanical_pass'] for r in outcomes) else 1


if __name__ == '__main__': sys.exit(main())
