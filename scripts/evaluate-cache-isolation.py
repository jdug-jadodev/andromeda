"""Repeated identical public prompt with/without cache after fixed conditioning.

No model changes, audio, Internet, action execution or production approval.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import time
import sys
import urllib.request

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('controlled', HERE / 'evaluate-model-controlled.py')
controlled = importlib.util.module_from_spec(spec); spec.loader.exec_module(controlled)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('key_path'); args = parser.parse_args()
    directory = HERE if (HERE / 'controlled_model_cases.json').exists() else HERE.parent / 'tests'
    suite = json.loads((directory / 'controlled_model_cases.json').read_text(encoding='utf-8'))
    key = Path(args.key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    print(json.dumps({'event': 'cache_precision_profile', 'kv_type': os.environ.get('ANDROMEDA_KV_TYPE', 'unspecified')}), flush=True)
    def request(path, payload=None, timeout=40): return controlled.post(opener, key, path, payload, timeout)
    deadline = time.monotonic() + 30
    while True:
        try: request('/health', timeout=2); break
        except OSError:
            if time.monotonic() >= deadline: raise
            time.sleep(.25)

    def payload(case, tools, cache):
        result = {'messages': [{'role': 'system', 'content': suite['system']}] + case['messages'],
                  'max_tokens': 64, 'temperature': 0, 'seed': 42, 'stream': False,
                  'cache_prompt': cache, 'chat_template_kwargs': {'enable_thinking': False}}
        if tools: result.update(tools=[suite['tool']], tool_choice='auto', parallel_tool_calls=False)
        return result

    # Repeat the preceding controlled test's exact four no-tool requests.
    for case in suite['cases'][:4]:
        began = time.monotonic()
        result = request('/v1/chat/completions', payload(case, False, True))
        print(json.dumps({'event': 'conditioning', 'case': case['id'], 'response': result['choices'][0]['message'],
                          'seconds': round(time.monotonic() - began, 3)}, ensure_ascii=False), flush=True)
    case = next(c for c in suite['cases'] if c['id'] == 'same_name_with_tool')
    canonical = payload(case, True, True)
    rendered = request('/apply-template', canonical)['prompt']
    prompt_hash = hashlib.sha256(rendered.encode()).hexdigest()
    outcomes = []
    for index, cache in enumerate([True, False, True, False]):
        began = time.monotonic()
        result = request('/v1/chat/completions', payload(case, True, cache))
        message = result['choices'][0]['message']
        ok, reason = controlled.check(message, case['expected'])
        record = {'event': 'cache_isolation', 'repeat': index + 1, 'cache_requested': cache,
                  'prompt_sha256': prompt_hash, 'response': message, 'mechanical_pass': ok, 'reason': reason,
                  'timings': result.get('timings'), 'seconds': round(time.monotonic() - began, 3),
                  'microphone_opened': False, 'executor_present': False}
        outcomes.append(record)
        print(json.dumps(record, ensure_ascii=False), flush=True)
    print(json.dumps({'event': 'cache_isolation_summary',
                      'distinct_outputs': len({json.dumps(row['response'], sort_keys=True) for row in outcomes}),
                      'all_mechanical_checks_pass': all(row['mechanical_pass'] for row in outcomes),
                      'semantic_review_required': True, 'production_approved': False}), flush=True)
    return 0 if all(row['mechanical_pass'] for row in outcomes) else 1


if __name__ == '__main__': sys.exit(main())
