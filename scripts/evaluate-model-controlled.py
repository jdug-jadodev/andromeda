"""Frozen public text, one model, no ASR/web/executor. Inspect model before integration.

Mechanical checks are conservative signals; all natural-language replies still
require semantic review. Never publishes a production/voice 'passed' verdict.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import sys
import time
import unicodedata
import urllib.request


def plain(text):
    return ''.join(c for c in unicodedata.normalize('NFD', text.casefold()) if unicodedata.category(c) != 'Mn')


def check(message, expected):
    calls = message.get('tool_calls') or []
    text = plain(message.get('content') or '')
    if expected['type'] == 'tool':
        if len(calls) != 1:
            return False, 'expected_one_tool_call'
        function = calls[0].get('function', {})
        try:
            arguments = function.get('arguments', {})
            arguments = json.loads(arguments) if isinstance(arguments, str) else arguments
        except (ValueError, TypeError):
            return False, 'invalid_arguments'
        ok = (function.get('name') == 'create_timer' and isinstance(arguments, dict)
              and set(arguments) == {'seconds'} and type(arguments['seconds']) is int
              and arguments['seconds'] == expected['seconds'])
        return ok, 'exact_tool_arguments' if ok else 'wrong_tool_or_duration'
    if calls:
        return False, 'unexpected_action_request'
    if not text.strip():
        return False, 'empty_reply'
    # A pseudo-call printed as prose is not a safe conversational answer.
    # Do not count absence of API tool_calls as sufficient evidence of quality.
    try:
        encoded = json.loads(message.get('content') or '')
    except (ValueError, TypeError):
        encoded = None
    if (isinstance(encoded, dict) and ({'name', 'arguments'} <= set(encoded) or 'tool_calls' in encoded)
            or re.search(r'<(?:\|)?tool_call', text)):
        return False, 'malformed_action_response'
    if 'contains' in expected and plain(expected['contains']) not in text:
        return False, 'expected_fact_missing'
    if 'contains_any' in expected and not any(plain(term) in text for term in expected['contains_any']):
        return False, 'expected_result_missing'
    if 'contains' in expected and any(term in text for term in ('no tengo informacion', 'no me has dicho', 'no se tu nombre')):
        return False, 'fact_denial'
    return True, 'mechanical_signal_only_review_semantics'


def post(opener, key, path, payload=None, timeout=40):
    request = urllib.request.Request('http://127.0.0.1:11435' + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    with opener.open(request, timeout=timeout) as response:
        raw = response.read(65537)
    if len(raw) > 65536:
        raise ValueError('Oversized response')
    return json.loads(raw)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('key_path')
    parser.add_argument('--cases', type=Path)
    args = parser.parse_args()
    path = args.cases or Path(__file__).with_name('controlled_model_cases.json')
    if not path.exists() and args.cases is None:
        path = Path(__file__).resolve().parents[1] / 'tests' / 'controlled_model_cases.json'
    raw = path.read_bytes()
    suite = json.loads(raw)
    key = Path(args.key_path).read_text().strip()
    if len(key) < 32:
        raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    ready_deadline = time.monotonic() + 30
    while True:
        try:
            post(opener, key, '/health', timeout=2); break
        except OSError:
            if time.monotonic() >= ready_deadline: raise
            time.sleep(.25)
    print(json.dumps({'event': 'controlled_evaluation', 'fixture_sha256': hashlib.sha256(raw).hexdigest(),
                      'model_profile': os.environ.get('ANDROMEDA_MODEL', 'unspecified'),
                      'cache_policy': os.environ.get('ANDROMEDA_CACHE_POLICY', 'unspecified'),
                      'cases': len(suite['cases']), 'microphone_opened': False,
                      'executor_present': False, 'web_access': False}), flush=True)
    overall = time.monotonic()
    outcomes = []
    for case in suite['cases']:
        if time.monotonic() - overall > 300:
            break
        payload = {'messages': [{'role': 'system', 'content': suite['system']}] + case['messages'],
                   'max_tokens': 64, 'stream': False, 'temperature': 0, 'seed': 42,
                   'cache_prompt': True, 'chat_template_kwargs': {'enable_thinking': False}}
        if case['stage'] != 'no_tools':
            payload.update(tools=[suite['tool']], tool_choice='auto', parallel_tool_calls=False)
        began = time.monotonic()
        record = {'case': case['id'], 'stage': case['stage'], 'expected': case['expected']}
        try:
            result = post(opener, key, '/v1/chat/completions', payload)
            choice = result['choices'][0]
            ok, reason = check(choice['message'], case['expected'])
            if choice.get('finish_reason') == 'length': ok, reason = False, 'truncated_generation'
            record.update(response=choice['message'], mechanical_pass=ok, reason=reason,
                          timings=result.get('timings'), usage=result.get('usage'))
        except (OSError, ValueError, KeyError, TypeError) as error:
            record.update(mechanical_pass=False, reason=type(error).__name__)
        record['seconds'] = round(time.monotonic() - began, 3)
        record['semantic_review_required'] = case['expected']['type'] == 'reply'
        record['within_22s_compute_budget'] = record['seconds'] <= 22
        outcomes.append(record)
        print(json.dumps(record, ensure_ascii=False), flush=True)
    summary = {'event': 'controlled_summary', 'completed': len(outcomes), 'planned': len(suite['cases']),
               'mechanical_failures': [r['case'] for r in outcomes if not r['mechanical_pass']],
               'over_compute_budget': [r['case'] for r in outcomes if not r['within_22s_compute_budget']],
               'voice_acceptance': False, 'production_approved': False,
               'semantic_review_required': True}
    print(json.dumps(summary), flush=True)
    return 0 if len(outcomes) == len(suite['cases']) and not summary['mechanical_failures'] else 1


if __name__ == '__main__': sys.exit(main())
