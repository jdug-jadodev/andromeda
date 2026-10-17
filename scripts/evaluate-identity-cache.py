"""Two exact identity prompts, cached/recomputed/repeated. No model or prompt tuning."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import time
import urllib.request

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('chat_only', HERE / 'evaluate-conversation-only.py')
chat = importlib.util.module_from_spec(spec); spec.loader.exec_module(chat)


def probe(system, introduction, question, request):
    history = [{'role': 'system', 'content': system}, {'role': 'user', 'content': introduction}]
    first = chat.reply(request('/v1/chat/completions', chat.payload(history, 'qwen_non_thinking')))
    history += [first, {'role': 'user', 'content': question['text']}]
    body = chat.payload(history, 'qwen_non_thinking')
    rendered = request('/apply-template', dict(body, add_generation_prompt=True))['prompt']
    digest = hashlib.sha256(rendered.encode()).hexdigest()
    for repeat, cache in enumerate([True, False, True], 1):
        query = copy.deepcopy(body); query['cache_prompt'] = cache
        start = time.monotonic()
        record = {'repeat': repeat, 'cache_requested': cache, 'prompt_sha256': digest,
                  'conditioning_response': first, 'semantic_review_required': True}
        try:
            result = request('/v1/chat/completions', query)
            message = chat.reply(result)
            ok, reason = chat.sequential.check_reply(message, question['expected'])
            actual_cache = (result.get('timings') or {}).get('cache_n')
            if not cache and actual_cache != 0:
                ok, reason = False, 'full_recomputation_not_verified'
            record.update(response=message, mechanical_pass=ok, reason=reason,
                          timings=result.get('timings'), usage=result.get('usage'))
        except (ValueError, TypeError, KeyError, OSError) as error:
            record.update(mechanical_pass=False, reason=str(error))
        record['seconds'] = round(time.monotonic() - start, 3)
        record['within_22s_compute_budget'] = record['seconds'] <= 22
        yield record


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('key_path'); args = parser.parse_args()
    directory = HERE if (HERE / 'conversation_only_cases.json').exists() else HERE.parent / 'tests'
    identity = HERE / 'conversation_identity.json'
    if not identity.exists(): identity = HERE.parent / 'config' / 'conversation_identity.json'
    system = json.loads(identity.read_text(encoding='utf-8'))['system']
    holdouts = json.loads((directory / 'identity_holdout_cases.json').read_bytes())
    original = json.loads((directory / 'conversation_only_cases.json').read_bytes())
    # Frozen inputs, not ideal assistant responses. Each conditioning reply is
    # generated once and remains byte-for-byte identical in all three requests.
    cases = [('camila', holdouts['conversations'][0]['turns'][0], holdouts['conversations'][0]['turns'][1]),
             ('oscar', original['conversations'][1]['turns'][0], original['conversations'][1]['turns'][1])]
    key = Path(args.key_path).read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(path, body=None, timeout=90):
        return chat.sequential.controlled.post(opener, key, path, body, timeout=timeout)
    ready = time.monotonic() + 30
    while True:
        try: request('/health', timeout=2); break
        except OSError:
            if time.monotonic() >= ready: raise
            time.sleep(.25)
    print(json.dumps({'event': 'identity_cache_control', 'model': 'qwen17_q4',
                      'system_sha256': hashlib.sha256(system.encode()).hexdigest(),
                      'diagnostic_timeout_seconds': 90, 'compute_acceptance_budget_seconds': 22,
                      'microphone_opened': False, 'executor_present': False}), flush=True)
    warm = chat.payload([{'role': 'system', 'content': system}, {'role': 'user', 'content': 'Hola.'}], 'qwen_non_thinking')
    warm['max_tokens'] = 1; request('/v1/chat/completions', warm)
    results = []
    for name, introduction, question in cases:
        for record in probe(system, introduction['text'], question, request):
            record['case'] = name; results.append(record)
            print(json.dumps(record, ensure_ascii=False), flush=True)
    print(json.dumps({'event': 'identity_cache_summary', 'completed': len(results),
                      'planned': 6, 'semantic_review_required': True,
                      'production_approved': False, 'voice_acceptance': False}), flush=True)
    return 0 if len(results) == 6 and all(r['mechanical_pass'] for r in results) else 1


if __name__ == '__main__': raise SystemExit(main())
