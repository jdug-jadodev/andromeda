"""Evaluate annotated voice evidence, not generated text or simulated success.

This is an offline acceptance gate, NOT a live cancellation/thermal controller.
Contains no microphone, network, model calls or action execution.
"""
from collections import Counter
import math


def evaluate(records, policy):
    failures, latencies, counts = [], [], Counter()
    required = policy['required_voice_cases']
    for index, row in enumerate(records):
        label = str(row.get('case_id', index))
        if row.get('source') != 'real_voice':
            continue  # Synthetic/typed tests can never certify real voice.
        try:
            case = row['case_id']
            if case not in required or row.get('kind') != required[case]:
                raise ValueError('unknown_case_or_wrong_kind')
            if row.get('clock') != policy['clock']:
                raise ValueError('incompatible_clock')
            start, end = row['speech_end_ms'], row['useful_audio_ms']
            if any(type(value) not in (int, float) or not math.isfinite(value) or value < 0 for value in (start, end)):
                raise ValueError('invalid_timestamp')
            if end < start:
                raise ValueError('reversed_timestamps')
            latency = end - start
            latencies.append(latency)
            counts[case] += 1
            if latency > policy['max_useful_response_ms']:
                raise ValueError('latency_over_30s')
            # A timeout, filler phrase or wrong answer within 30 s is NOT success.
            if row.get('semantic_pass') is not True or row.get('useful_response') is not True or row.get('timeout') is not False:
                raise ValueError('not_a_verified_useful_answer')
            if required[case] in {'action', 'tool_read'}:
                tool_end = row.get('tool_finished_ms')
                if (row.get('executor_verified') is not True or type(tool_end) not in (int, float)
                        or not math.isfinite(tool_end) or not start <= tool_end <= end):
                    raise ValueError('executor_result_not_verified_before_speech')
        except (KeyError, ValueError, TypeError) as error:
            failures.append({'case': label, 'reason': str(error)})
    for case in required:
        if counts[case] < policy['required_samples_per_case']:
            failures.append({'case': case, 'reason': 'insufficient_real_voice_samples'})
    ordered = sorted(latencies)
    return {'passed': not failures, 'failures': failures, 'real_voice_samples': sum(counts.values()),
            'max_useful_response_ms': max(latencies) if latencies else None,
            'p95_useful_response_ms': ordered[math.ceil(.95 * len(ordered)) - 1] if ordered else None,
            'note': 'Voice gate only; privacy, sustained resources and charge protection need separate approval.'}
