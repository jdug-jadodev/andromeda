"""Actual model replies become history, with a non-executing tool simulator.

Public fictional scenarios only. No microphone/Android executor/external network.
This isolates live conversation from the production agent's memory/confirmation.
"""
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
from span_duration import seconds_from_span
spec = importlib.util.spec_from_file_location('controlled', HERE / 'evaluate-model-controlled.py')
controlled = importlib.util.module_from_spec(spec); spec.loader.exec_module(controlled)

SPAN_TOOL = {'type': 'function', 'function': {'name': 'create_timer',
    'description': 'Solicita una cuenta regresiva. Copia literalmente solo la duración del mensaje actual; no conviertas unidades ni inventes datos.',
    'parameters': {'type': 'object', 'properties': {'duration_text': {'type': 'string'}},
                   'required': ['duration_text'], 'additionalProperties': False}}}
SAMPLING = {
    'greedy': {'temperature': 0},
    'qwen_non_thinking': {'temperature': .7, 'top_p': .8, 'top_k': 20, 'min_p': 0}
}


def contains_fact(text, term):
    # Independent facts must match independently: "Camila" is not evidence
    # that the assistant remembered the nickname "Cami".
    return re.search(r'(?<!\w)' + re.escape(controlled.plain(term)) + r'(?!\w)', controlled.plain(text)) is not None


def check_reply(message, expected):
    ok, reason = controlled.check(message, expected)
    if not ok: return ok, reason
    text = controlled.plain(message.get('content') or '')
    if any(not contains_fact(text, term) for term in expected.get('contains_all', [])):
        return False, 'missing_conversation_fact'
    if any(contains_fact(text, term) for term in expected.get('forbids', [])):
        return False, 'cross_conversation_or_forbidden_fact'
    return True, reason


def validated_request(message, user_text):
    calls = message.get('tool_calls') or []
    if len(calls) != 1: raise ValueError('Expected exactly one native call')
    call = calls[0]
    if not isinstance(call.get('id'), str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', call['id']):
        raise ValueError('Invalid native call ID')
    function = call['function']
    if function['name'] != 'create_timer': raise ValueError('Unknown tool')
    args = function['arguments']
    args = json.loads(args) if isinstance(args, str) else args
    if not isinstance(args, dict) or set(args) != {'duration_text'}: raise ValueError('Invalid arguments')
    return call['id'], seconds_from_span(args['duration_text'], user_text)


class ShadowConversation:
    def __init__(self, system, request, think_budget=0, sampling='greedy', seed=42):
        if think_budget not in (0, 32): raise ValueError('Invalid thinking budget')
        if sampling not in SAMPLING or sampling == 'qwen_non_thinking' and think_budget:
            raise ValueError('Invalid sampling/thinking combination')
        self.messages = [{'role': 'system', 'content': system}]
        self.request, self.call_ids = request, set()
        self.think_budget, self.last_reasoning_chars = think_budget, 0
        self.sampling, self.seed = sampling, seed

    def infer(self):
        # Preserve the actual user/assistant/tool history. No scripted assistant
        # repairs, hidden retries, fact injection or prompt tuning per turn.
        payload = {'messages': copy.deepcopy(self.messages), 'tools': [SPAN_TOOL],
                   'tool_choice': 'auto', 'parallel_tool_calls': False, 'max_tokens': 96 if self.think_budget else 64,
                   **SAMPLING[self.sampling], 'seed': self.seed, 'stream': False, 'cache_prompt': True,
                   'chat_template_kwargs': {'enable_thinking': bool(self.think_budget)}}
        response = self.request(payload)
        choice = response['choices'][0]
        if choice.get('finish_reason') == 'length': raise ValueError('Truncated generation')
        message = copy.deepcopy(choice['message'])
        self.last_reasoning_chars = len(message.pop('reasoning_content', '') or '')
        stored = {'role': 'assistant', 'content': message.get('content')}
        if message.get('tool_calls'): stored['tool_calls'] = copy.deepcopy(message['tool_calls'])
        self.messages.append(stored)
        return message, response.get('timings')

    def turn(self, case):
        self.messages.append({'role': 'user', 'content': case['text']})
        began = time.monotonic()
        record = {'turn': case['id'], 'input': case['text'], 'expected': case['expected'],
                  'android_actions_executed': 0, 'microphone_opened': False,
                  'semantic_review_required': True, 'executor_simulated': True}
        try:
            first, timings = self.infer()
            record.update(first_response=first, first_timings=timings,
                          first_reasoning_chars=self.last_reasoning_chars,
                          first_inference_seconds=round(time.monotonic() - began, 3))
            calls = first.get('tool_calls') or []
            if not calls:
                if case['expected']['type'] == 'tool':
                    record.update(mechanical_pass=False, reason='no_tool_call')
                else:
                    ok, reason = check_reply(first, case['expected'])
                    record.update(mechanical_pass=ok, reason=reason)
            else:
                call_id, seconds = validated_request(first, case['text'])
                if call_id in self.call_ids: raise ValueError('Repeated native call ID')
                self.call_ids.add(call_id)
                record.update(requested_seconds=seconds,
                              mechanical_pass=case['expected']['type'] == 'tool' and seconds == case['expected'].get('seconds'),
                              reason='validated_request' if case['expected']['type'] == 'tool' else 'unexpected_action_request')
                # The simulator has no scheduling/storage/device API. Unknown
                # or unapproved fixture calls receive a blocked result.
                mode = case.get('simulator', 'blocked')
                if mode == 'success':
                    result = {'ok': True, 'seconds': seconds, 'message': f'Temporizador de {seconds} segundos activado.'}
                elif mode == 'failure':
                    result = {'ok': False, 'message': 'No se pudo guardar: no se creó el temporizador.'}
                else:
                    result = {'ok': False, 'message': 'Operación no autorizada en esta prueba. No se creó ningún temporizador.'}
                self.messages.append({'role': 'tool', 'tool_call_id': call_id,
                                      'content': json.dumps(result, ensure_ascii=False)})
                record['simulated_result'] = result
                final, final_timings = self.infer()
                record.update(final_response=final, final_timings=final_timings)
                ok, reason = check_reply(final, {'type': 'reply'})
                if not ok: record.update(mechanical_pass=False, reason=reason)
                if mode == 'failure':
                    ok, reason = check_reply(final, {'type': 'reply', 'contains_any': ['no', 'fallo', 'error']})
                    if not ok: record.update(mechanical_pass=False, reason='failed_result_not_acknowledged')
        except (OSError, ValueError, KeyError, TypeError) as error:
            record.update(mechanical_pass=False, reason=str(error))
            # A rejected argument is still a tool result. Leaving a native call
            # pending and continuing with the next human message would create
            # an invalid conversation and bias later answers about execution.
            calls = record.get('first_response', {}).get('tool_calls') or []
            unresolved = [call for call in calls if isinstance(call.get('id'), str)
                          and re.fullmatch(r'[A-Za-z0-9_-]{1,64}', call['id'])
                          and not any(m.get('role') == 'tool' and m.get('tool_call_id') == call['id'] for m in self.messages)]
            for call in unresolved:
                self.messages.append({'role': 'tool', 'tool_call_id': call['id'], 'content': json.dumps({
                    'ok': False, 'code': 'validation_rejected',
                    'message': 'Solicitud rechazada al validar argumentos. No se creó ningún temporizador.'}, ensure_ascii=False)})
            if unresolved:
                record['validation_feedback_added'] = True
                try:
                    final, final_timings = self.infer()
                    record.update(final_response=final, final_timings=final_timings)
                except (OSError, ValueError, KeyError, TypeError) as followup_error:
                    record['feedback_response_error'] = type(followup_error).__name__
        record['total_compute_seconds'] = round(time.monotonic() - began, 3)
        record['within_22s_compute_budget'] = record['total_compute_seconds'] <= 22
        record['history_messages'] = len(self.messages)
        return record


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('key_path')
    parser.add_argument('--contract-v2', action='store_true')
    parser.add_argument('--seed', type=int, choices=[42, 7], default=42); args = parser.parse_args()
    directory = HERE if (HERE / 'sequential_conversation_cases.json').exists() else HERE.parent / 'tests'
    raw = (directory / 'sequential_conversation_cases.json').read_bytes()
    suite = json.loads(raw)
    system = json.loads((directory / 'controlled_model_cases.json').read_text(encoding='utf-8'))['system']
    if args.contract_v2:
        contract_path = HERE / 'conversation_contract.json'
        if not contract_path.exists(): contract_path = HERE.parent / 'config' / 'conversation_contract.json'
        system = json.loads(contract_path.read_text(encoding='utf-8'))['system']
    key = Path(args.key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    deadline = time.monotonic() + 30
    while True:
        try: controlled.post(opener, key, '/health', timeout=2); break
        except OSError:
            if time.monotonic() >= deadline: raise
            time.sleep(.25)
    think_budget = int(os.environ.get('ANDROMEDA_THINK_BUDGET', '0'))
    if think_budget not in (0, 32): raise ValueError('Invalid thinking budget')
    sampling = os.environ.get('ANDROMEDA_SAMPLING', 'greedy')
    if sampling not in SAMPLING: raise ValueError('Invalid sampling profile')
    def request(payload): return controlled.post(opener, key, '/v1/chat/completions', payload,
                                                timeout=60 if args.contract_v2 or think_budget else 40)
    print(json.dumps({'event': 'sequential_evaluation', 'fixture_sha256': hashlib.sha256(raw).hexdigest(),
                      'model_profile': os.environ.get('ANDROMEDA_MODEL', 'unspecified'),
                      'cache_policy': os.environ.get('ANDROMEDA_CACHE_POLICY', 'unspecified'),
                      'system_contract_version': 2 if args.contract_v2 else 1,
                      'system_sha256': hashlib.sha256(system.encode()).hexdigest(),
                      'thinking_budget': think_budget,
                      'sampling': SAMPLING[sampling], 'seed': args.seed,
                      'actual_model_replies_in_history': True, 'android_executor_present': False,
                      'microphone_opened': False, 'external_network': False}), flush=True)
    started, outcomes = time.monotonic(), []
    for scenario in suite['scenarios']:
        conversation = ShadowConversation(system, request, think_budget=think_budget, sampling=sampling, seed=args.seed)
        for case in scenario['turns']:
            if time.monotonic() - started >= 300: break
            record = conversation.turn(case); record['conversation'] = scenario['id']
            outcomes.append(record); print(json.dumps(record, ensure_ascii=False), flush=True)
    summary = {'event': 'sequential_summary', 'completed': len(outcomes),
               'planned': sum(len(s['turns']) for s in suite['scenarios']),
               'mechanical_failures': [(r['conversation'], r['turn']) for r in outcomes if not r['mechanical_pass']],
               'over_compute_budget': [(r['conversation'], r['turn']) for r in outcomes if not r['within_22s_compute_budget']],
               'semantic_review_required': True, 'production_approved': False, 'voice_acceptance': False}
    print(json.dumps(summary), flush=True)
    return 0 if len(outcomes) == summary['planned'] and not summary['mechanical_failures'] else 1


if __name__ == '__main__': sys.exit(main())
