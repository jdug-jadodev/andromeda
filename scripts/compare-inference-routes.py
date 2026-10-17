"""Offline summaries of frozen on-device diagnostics; never approve semantics."""
import argparse
import json
from pathlib import Path


def read_records(path):
    raw = path.read_bytes()
    text = raw.decode('utf-16') if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else raw.decode('utf-8-sig')
    rows = []
    for line in text.splitlines():
        try: rows.append(json.loads(line))
        except ValueError: continue
    return rows


def first_top_logprobs(record):
    head = record.get('probabilities_head') or []
    if not head: return {}
    result = {}
    for item in head[0].get('top_logprobs', []):
        piece = item.get('bytes')
        if not isinstance(piece, list): piece = list(str(item.get('token', '')).encode('utf-8'))
        result[tuple(piece)] = item['logprob']
    return result


def compare(a, b):
    if a['messages_sha256'] != b['messages_sha256'] or a['prompt_tokens_sha256'] != b['prompt_tokens_sha256']:
        raise ValueError('Cannot compare different conditioning inputs')
    pa, pb = first_top_logprobs(a), first_top_logprobs(b)
    common = pa.keys() & pb.keys()
    return {'same_input_verified': True, 'text_equal': a.get('text') == b.get('text'),
            'generated_token_ids_equal': a.get('tokens') == b.get('tokens') if a.get('tokens') and b.get('tokens') else None,
            'first_top_candidates_a': len(pa), 'first_top_candidates_b': len(pb),
            'first_top_candidates_common': len(common),
            'max_common_first_token_logprob_delta': max((abs(pa[t] - pb[t]) for t in common), default=None),
            'seconds_a': a['seconds'], 'seconds_b': b['seconds'],
            'full_vocabulary_distribution_compared': False, 'semantic_review_required': True}


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('reports', nargs='+', type=Path); args = parser.parse_args()
    all_results = []
    for path in args.reports:
        records = read_records(path)
        profile = next((r for r in records if r.get('event') == 'inference_route_profile'), None)
        if not profile: continue
        summary = next((r for r in records if r.get('event') == 'inference_route_summary'), None)
        print(json.dumps({'event': 'report_summary', 'file': path.name,
                          'model': profile['model'], 'control': profile['runtime_control'], 'summary': summary}), flush=True)
        results = [r for r in records if r.get('event') == 'inference_route_result']
        all_results.extend(results)
        for a in results:
            if a['route'] != 'chat': continue
            b = next((r for r in results if r['case'] == a['case'] and r['route'] == 'raw'), None)
            if b:
                print(json.dumps(dict(event='route_comparison', model=a['model'], case=a['case'], **compare(a, b))), flush=True)
    for a in all_results:
        if a['runtime_control'] != 'baseline' or a['route'] != 'raw': continue
        for b in all_results:
            if b['model'] != a['model'] or b['case'] != a['case'] or b['runtime_control'] == 'baseline' or b['route'] != 'raw': continue
            print(json.dumps(dict(event='runtime_comparison', model=a['model'], case=a['case'],
                                  control=b['runtime_control'], **compare(a, b))), flush=True)


if __name__ == '__main__': main()
