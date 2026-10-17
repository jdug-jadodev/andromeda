"""Bounded on-Moto text diagnostics. No executor, microphone, or Internet calls."""
import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import time
import urllib.request

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('chat_only', HERE / 'evaluate-conversation-only.py')
chat = importlib.util.module_from_spec(spec); spec.loader.exec_module(chat)
emit = chat.emit


def sha(value):
    if not isinstance(value, bytes):
        value = json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(',', ':')).encode()
    return hashlib.sha256(value).hexdigest()


def first_difference(left, right):
    for index, (a, b) in enumerate(zip(left, right)):
        if a != b: return index
    return min(len(left), len(right)) if len(left) != len(right) else None


def sampling(model, control='baseline'):
    result = dict(chat.SAMPLING['qwen35_no_penalty' if model == 'qwen35' else 'qwen_non_thinking'])
    if control == 'greedy': result['temperature'] = 0.0
    return result


def generation_payload(row, model, route, control='baseline'):
    common = dict(sampling(model, control), seed=42, stream=False, cache_prompt=False, return_tokens=True)
    if route == 'chat':
        return dict(common, messages=copy.deepcopy(row['messages']), max_tokens=96,
                    chat_template_kwargs={'enable_thinking': False}, logprobs=True, top_logprobs=8)
    if route != 'raw': raise ValueError('Unknown generation route')
    return dict(common, prompt=list(row['actual_tokens']), n_predict=96, n_probs=8)


def audit_prompt(row, request):
    body = {'messages': row['messages'], 'add_generation_prompt': True,
            'chat_template_kwargs': {'enable_thinking': False}}
    prompt = request('/apply-template', body)['prompt']
    tokens = request('/tokenize', {'content': prompt, 'add_special': True, 'parse_special': True})['tokens']
    row['actual_tokens'] = tokens
    chars = first_difference(prompt, row['official_prompt'])
    record = {'event': 'full_turn_audit', 'case': row['id'], 'messages_sha256': sha(row['messages']),
              'prompt_sha256': sha(prompt.encode()), 'tokens_sha256': sha(tokens),
              'official_prompt_equal': prompt == row['official_prompt'],
              'embedded_reference_prompt_equal': prompt == row['embedded_prompt'],
              'official_tokenizer_equal': tokens == row['official_tokens'],
              'tokens': len(tokens), 'context_margin_with_96_output': 2048 - len(tokens) - 96,
              'roles': re.findall(r'<\|im_start\|>(\w+)', prompt),
              'input_roles': [m['role'] for m in row['messages']],
              'system_preserved': row['messages'][0]['content'] in prompt,
              'all_user_turns_preserved': all(m['content'] in prompt for m in row['messages'] if m['role'] == 'user'),
              'all_assistant_turns_preserved': all(m['content'] in prompt for m in row['messages'] if m['role'] == 'assistant'),
              'generation_tail': prompt[-70:], 'first_prompt_difference': chars,
              'first_token_difference': first_difference(tokens, row['official_tokens'])}
    if chars is not None:
        record['actual_difference_excerpt'] = prompt[max(0, chars - 100):chars + 150]
        record['official_difference_excerpt'] = row['official_prompt'][max(0, chars - 100):chars + 150]
    return record


def summarize_response(result, route):
    if route == 'chat':
        choice = result['choices'][0]
        message = choice['message']
        text = message.get('content')
        record = {'raw_message': message, 'text': text, 'finish_reason': choice.get('finish_reason'),
                  'probabilities_head': (choice.get('logprobs') or {}).get('content', [])[:3]}
        try:
            parsed = chat.reply(result)
            record.update(parsed_reply=parsed, local_parser_changed_text=parsed['content'] != text)
        except (ValueError, TypeError, KeyError) as error:
            record['local_parser_error'] = str(error)
    else:
        record = {'text': result.get('content'), 'tokens': result.get('tokens'),
                  'stopped_eos': result.get('stopped_eos'), 'stopped_limit': result.get('stopped_limit'),
                  'stop_type': result.get('stop_type'), 'generation_settings': result.get('generation_settings'),
                  'probabilities_head': result.get('completion_probabilities', result.get('probs', []))[:3]}
    record.update(timings=result.get('timings'), usage=result.get('usage'),
                  tokens_evaluated=result.get('tokens_evaluated'), tokens_predicted=result.get('tokens_predicted'))
    return record


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('key_path'); args = parser.parse_args()
    model = os.environ['ANDROMEDA_MODEL']
    control = os.environ.get('ANDROMEDA_RUNTIME_CONTROL', 'baseline')
    if model not in {'qwen17', 'qwen35'}: raise ValueError('Unsupported diagnostic model')
    path = HERE / ('inference_route_cases_' + model + '.json')
    if not path.exists(): path = HERE.parent / 'artifacts' / 'evaluations' / 'inference-reference' / model / 'inference_route_cases.json'
    data = path.read_bytes(); artifact = json.loads(data)
    if artifact['model'] != model: raise ValueError('Reference/model mismatch')
    key_path = Path(args.key_path); key = key_path.read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(endpoint, body=None, timeout=90):
        query = urllib.request.Request('http://127.0.0.1:11435' + endpoint,
            data=json.dumps(body, ensure_ascii=True).encode() if body is not None else None,
            headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
        with opener.open(query, timeout=timeout) as response: raw = response.read(524289)
        if len(raw) > 524288: raise ValueError('Excessive diagnostic response')
        return json.loads(raw)
    began = time.monotonic(); ready = began + 35
    while True:
        try: request('/health', timeout=2); break
        except OSError:
            if time.monotonic() >= ready: raise
            time.sleep(.25)
    emit({'event': 'inference_route_profile', 'model': model, 'runtime_control': control,
          'cache_policy': os.environ.get('ANDROMEDA_CACHE_POLICY'), 'fixture_sha256': sha(data),
          'official_revision': artifact['revision'], 'sampling': sampling(model, control),
          'full_recomputation_for_route_comparison': True, 'max_output_tokens': 96,
          'microphone_opened': False, 'executor_present': False, 'external_network': False,
          'diagnostic_request_timeout_s': 90, 'compute_acceptance_budget_s': 22})
    props = request('/props')
    emit({'event': 'server_props', 'default_generation_settings': props.get('default_generation_settings'),
          'chat_template_sha256': sha(str(props.get('chat_template', '')).encode())})
    for line in key_path.with_name('server.log').read_text(errors='replace').splitlines():
        if re.search(r'system_info:|load_backend:|flash_attn|n_ubatch|repack|general.architecture|CPU.*buffer', line):
            emit({'event': 'runtime_evidence', 'line': line.replace(key, '[REDACTED]')})
    audited, outcomes = [], []
    for row in artifact['audits']:
        audit = audit_prompt(row, request); audited.append(audit); emit(audit)
        if audit['context_margin_with_96_output'] < 0: raise ValueError('Context overflow')
    # No answer, ideal or generated, from this experiment is fed to another
    # scored prompt. Every route/control uses the pre-frozen real histories.
    routes = ['chat', 'raw'] if control == 'baseline' else ['raw']
    for row in artifact['audits']:
        if row['id'] not in artifact['generation_cases']: continue
        emit({'event': 'frozen_generation_input', 'case': row['id'], 'messages': row['messages'],
              'messages_sha256': sha(row['messages']), 'tokens_sha256': sha(row['actual_tokens'])})
        for route in routes:
            if time.monotonic() - began > 325:
                emit({'event': 'diagnostic_global_reserve', 'completed': len(outcomes)}); break
            started = time.monotonic()
            body = generation_payload(row, model, route, control)
            result = request('/v1/chat/completions' if route == 'chat' else '/completion', body)
            record = dict(event='inference_route_result', case=row['id'], route=route,
                          model=model, runtime_control=control,
                          messages_sha256=sha(row['messages']), prompt_tokens_sha256=sha(row['actual_tokens']),
                          seconds=round(time.monotonic() - started, 3), **summarize_response(result, route))
            record['within_22s_compute_budget'] = record['seconds'] <= 22
            record['semantic_review_required'] = True
            outcomes.append(record); emit(record)
    emit({'event': 'inference_route_summary', 'audit_count': len(audited),
          'official_prompt_mismatches': [r['case'] for r in audited if not r['official_prompt_equal']],
          'official_tokenizer_mismatches': [r['case'] for r in audited if not r['official_tokenizer_equal']],
          'completed': len(outcomes), 'planned': len(routes) * len(artifact['generation_cases']),
          'production_approved': False, 'voice_acceptance': False})
    # Libraries may buffer early startup logs when stderr is redirected. Read
    # the narrowly selected evidence again at the end, without dumping headers.
    for line in key_path.with_name('server.log').read_text(errors='replace').splitlines():
        if re.search(r'system_info|CPU :|flash_attn|n_ubatch|repack|general.architecture|CPU.*buffer', line):
            emit({'event': 'runtime_evidence_final', 'line': line.replace(key, '[REDACTED]')})


if __name__ == '__main__': main()
