"""Frozen sequential conversations WITHOUT tools or actions; no voice approval."""
import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.request

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'backend'))
from conversation_agent import parse_native_response
spec = importlib.util.spec_from_file_location('sequential', HERE / 'evaluate-sequential-conversations.py')
sequential = importlib.util.module_from_spec(spec); spec.loader.exec_module(sequential)
SAMPLING = dict(sequential.SAMPLING, qwen35_non_thinking={
    'temperature': 1.0, 'top_p': 1.0, 'top_k': 20, 'min_p': 0,
    'presence_penalty': 2.0, 'repeat_penalty': 1.0})
SAMPLING['qwen35_no_penalty'] = dict(SAMPLING['qwen35_non_thinking'], presence_penalty=0.0)


def emit(record):
    # adb -> Windows PowerShell may decode native UTF-8 using OEM cp850.
    # ASCII JSON escapes preserve Unicode losslessly through either console.
    print(json.dumps(record, ensure_ascii=True), flush=True)


def payload(messages, sampling):
    # No empty tool schema either: the native template must be conversation-only.
    return {'messages': copy.deepcopy(messages), 'max_tokens': 96, 'stream': False,
            'cache_prompt': True, 'seed': 42, **SAMPLING[sampling],
            'chat_template_kwargs': {'enable_thinking': False}}


def reply(response):
    choice = response['choices'][0]
    if choice.get('finish_reason') != 'stop': raise ValueError('Incomplete generation')
    message = choice['message']
    if message.get('tool_calls'): raise ValueError('Unexpected tool call')
    if not isinstance(message.get('content'), str): raise ValueError('Invalid reply content')
    value = parse_native_response(message['content'], [])
    if value['kind'] != 'reply': raise ValueError('Non-conversational output')
    return {'role': 'assistant', 'content': value['text']}


def evaluate_conversation(system, cases, request, sampling, clock=time.monotonic):
    messages = [{'role': 'system', 'content': system}]
    for case in cases:
        messages.append({'role': 'user', 'content': case['text']})
        start = clock()
        record = {'turn': case['id'], 'input': case['text'], 'expected': case['expected'],
                  'semantic_review_required': True, 'android_actions_executed': 0}
        try:
            response = request(payload(messages, sampling))
            # Keep the actual generated reply, not a golden/fixture response.
            message = reply(response)
            messages.append(message)
            ok, reason = sequential.check_reply(message, case['expected'])
            record.update(response=message, mechanical_pass=ok, reason=reason,
                          timings=response.get('timings'), usage=response.get('usage'))
        except (ValueError, TypeError, KeyError, OSError) as error:
            record.update(mechanical_pass=False, reason=str(error), history_continuation_aborted=True)
        record['seconds'] = round(clock() - start, 3)
        record['within_22s_compute_budget'] = record['seconds'] <= 22
        record['history_messages'] = len(messages)
        yield record
        # Do not invent a recovery reply or bias later turns with broken history.
        if record.get('history_continuation_aborted'): return


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('key_path')
    parser.add_argument('--identity', action='store_true')
    parser.add_argument('--holdouts', action='store_true')
    parser.add_argument('--memory-samples', action='store_true'); args = parser.parse_args()
    directory = HERE if (HERE / 'conversation_only_cases.json').exists() else HERE.parent / 'tests'
    raw = (directory / 'conversation_only_cases.json').read_bytes(); suite = json.loads(raw)
    if args.holdouts:
        raw = (directory / 'identity_holdout_cases.json').read_bytes()
        suite['conversations'] = json.loads(raw)['conversations']
    if args.identity:
        identity = HERE / 'conversation_identity.json'
        if not identity.exists(): identity = HERE.parent / 'config' / 'conversation_identity.json'
        suite['system'] = json.loads(identity.read_text(encoding='utf-8'))['system']
    sampling = os.environ.get('ANDROMEDA_SAMPLING', 'greedy')
    if sampling not in SAMPLING: raise ValueError('Unknown sampling profile')
    key = Path(args.key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(body):
        return sequential.controlled.post(opener, key, '/v1/chat/completions', body, timeout=40)
    emit({'event': 'conversation_only_evaluation', 'fixture_sha256': hashlib.sha256(raw).hexdigest(),
                      'system_variant': 'identity' if args.identity else 'baseline', 'holdouts': args.holdouts,
                      'system_sha256': hashlib.sha256(suite['system'].encode()).hexdigest(),
                      'model': os.environ.get('ANDROMEDA_MODEL'), 'sampling': SAMPLING[sampling],
                      'cache_policy': os.environ.get('ANDROMEDA_CACHE_POLICY'),
                      'microphone_opened': False, 'tools_present': False, 'executor_present': False,
                      'external_network': False})
    # Explicit cold preparation, not a scored answer or injected conversation fact.
    started = time.monotonic()
    deadline = started + 30
    while True:
        try: sequential.controlled.post(opener, key, '/health', timeout=2); break
        except OSError:
            if time.monotonic() >= deadline: raise
            time.sleep(.25)
    memory = None
    if args.memory_samples:
        memory_spec = importlib.util.spec_from_file_location('model_memory', HERE / 'measure-model-memory.py')
        memory = importlib.util.module_from_spec(memory_spec); memory_spec.loader.exec_module(memory)
        pid_text = Path(args.key_path).with_name('server.pid').read_text().strip()
        if not pid_text.isdigit() or int(pid_text) <= 1: raise ValueError('Invalid server PID')
        pid = int(pid_text)
        memory.sample('model_loaded_before_warmup', pid)
    warm = payload([{'role': 'system', 'content': suite['system']}, {'role': 'user', 'content': 'Hola.'}], sampling)
    rendered = sequential.controlled.post(opener, key, '/apply-template', dict(warm, add_generation_prompt=True))['prompt']
    emit({'event': 'template_audit', 'prompt_sha256': hashlib.sha256(rendered.encode()).hexdigest(),
                      'roles': re.findall(r'<\|im_start\|>(\w+)', rendered),
                      'system_preserved': suite['system'] in rendered,
                      'system_characters': len(suite['system'])})
    warm['max_tokens'] = 1
    prepared = request(warm)
    emit({'event': 'cold_preparation', 'seconds': round(time.monotonic() - started, 3),
          'usage': prepared.get('usage')})
    if memory is not None: memory.sample('model_loaded_after_warmup', pid)
    outcomes = []
    for conversation in suite['conversations']:
        for record in evaluate_conversation(suite['system'], conversation['turns'], request, sampling):
            record['conversation'] = conversation['id']; outcomes.append(record)
            emit(record)
    if memory is not None: memory.sample('model_loaded_after_conversations', pid)
    summary = {'event': 'conversation_only_summary', 'completed': len(outcomes),
               'planned': sum(len(c['turns']) for c in suite['conversations']),
               'mechanical_failures': [(r['conversation'], r['turn']) for r in outcomes if not r['mechanical_pass']],
               'over_compute_budget': [(r['conversation'], r['turn']) for r in outcomes if not r['within_22s_compute_budget']],
               'semantic_review_required': True, 'voice_acceptance': False, 'production_approved': False}
    emit(summary)
    return 0 if summary['completed'] == summary['planned'] and not summary['mechanical_failures'] else 1


if __name__ == '__main__': sys.exit(main())
