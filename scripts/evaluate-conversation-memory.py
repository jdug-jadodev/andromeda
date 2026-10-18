"""One fixed LFM memory-presentation comparison, public text only, no voice/tools.

Both systems get identical baseline-generated histories. This isolates the short
session note, not live-dialogue quality. Mechanical topic matches require review.
"""
import copy
import hashlib
import json
from pathlib import Path
import re
import statistics
import sys
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'backend'))
from conversation_voice import ConversationModel, SYSTEM, conversational_reply, now_ms
from doja_intents import normalized

VARIANTS = ('baseline', 'session_note')
DIRECT = '¿Cuáles fueron mis dos preguntas más recientes, antes de esta?'
ACK = '¿Recuerdas las dos preguntas anteriores?'
FOLLOWUP = '¿Cuáles fueron?'


def emit(row):
    print(json.dumps(row, ensure_ascii=True), flush=True)


def build_messages(history, text, variant, note):
    if variant not in (*VARIANTS, 'structured_context'): raise ValueError('Unknown memory variant')
    if len(history) % 2 or len(history) > 12: raise ValueError('Expected bounded complete pairs')
    if any(m.get('role') != ('user' if i % 2 == 0 else 'assistant') or not isinstance(m.get('content'), str)
           for i, m in enumerate(history)):
        raise ValueError('Invalid public history')
    system = SYSTEM + ('\n' + note if variant != 'baseline' else '')
    if variant == 'structured_context':
        data = {'historial': copy.deepcopy(history), 'mensaje_actual': text}
        return [{'role': 'system', 'content': system},
                {'role': 'user', 'content': json.dumps(data, ensure_ascii=False, separators=(',', ':'))}]
    return [{'role': 'system', 'content': system}] + copy.deepcopy(history) + [{'role': 'user', 'content': text}]


def mechanical_check(text, expected):
    plain = normalized(text)
    def contains(term): return bool(re.search(r'\b' + re.escape(normalized(term)) + r'\b', plain))
    kind = expected['kind']
    if kind == 'topics':
        return (all(any(contains(t) for t in group) for group in expected['groups'])
                and not any(contains(t) for t in expected.get('forbids', [])))
    if kind == 'ack':
        return contains('si') and not contains('no recuerdo') and not contains('no las recuerdo')
    if kind == 'absence':
        return any(contains(t) for t in ('no tengo', 'no has', 'no me has', 'ninguna', 'no hay', 'primera pregunta', 'no recuerdo'))
    if kind == 'no_actions':
        return any(contains(t) for t in ('no puedo', 'no tengo', 'sin herramientas', 'no es posible', 'no estoy'))
    raise ValueError('Unknown mechanical expectation')


class Experiment:
    def __init__(self, model, note, emit_record=emit, clock=now_ms, variants=VARIANTS):
        if tuple(variants) not in (VARIANTS, ('baseline', 'structured_context')):
            raise ValueError('Only one candidate may be compared with the baseline')
        self.model, self.note, self.emit, self.clock = model, note, emit_record, clock
        self.variants = tuple(variants)
        self.end = clock() + 240000
        self.rows, self.seeds = [], 0

    def call(self, history, text, variant, maximum=96):
        messages = build_messages(history, text, variant, self.note)
        deadline = min(self.clock() + 22000, self.end)
        body = ConversationModel.payload(messages, maximum)
        started = time.monotonic()
        rendered = self.model.post('/apply-template', dict(body, add_generation_prompt=True), deadline)['prompt']
        tokens = self.model.post('/tokenize', {'content': rendered, 'add_special': True, 'parse_special': True}, deadline)['tokens']
        if len(tokens) > 1536: raise ValueError('Fixture exceeds production input budget; do not trim matched history')
        response = self.model.post('/v1/chat/completions', body, deadline)
        # Source dialogue is identical; its payload representation may differ.
        source_dialogue = copy.deepcopy(history) + [{'role': 'user', 'content': text}]
        digest = lambda value: hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True).encode()).hexdigest()
        row = {'variant': variant, 'seconds': round(time.monotonic() - started, 3),
               'input_tokens': len(tokens), 'history_pairs': len(history) // 2,
               'shared_conditioning_sha256': digest(source_dialogue),
               'payload_dialogue_sha256': digest(messages[1:]),
               'messages': messages, 'usage': response.get('usage'), 'timings': response.get('timings')}
        choice = response['choices'][0]
        # Preserve the complete raw public reply, even if the production output guard rejects it.
        row['response'] = choice['message']; row['finish_reason'] = choice.get('finish_reason')
        try:
            row['text'] = conversational_reply(choice['message'], choice.get('finish_reason'))
            row['production_reply_allowed'] = True
        except (ValueError, TypeError, KeyError):
            row['text'] = choice['message'].get('content') or ''
            row['production_reply_allowed'] = False
        return row

    def seed(self, topic):
        history = []
        for index, question in enumerate(topic['questions']):
            row = self.call(history, question, 'baseline')
            row.update(event='memory_seed', topic=topic['id'], seed_turn=index + 1,
                       public_fixture=True, scored=False)
            self.emit(row); self.seeds += 1
            if not row['production_reply_allowed']: raise ValueError('Incomplete/guarded seed; no ideal answer may be substituted')
            history.extend([{'role': 'user', 'content': question}, {'role': 'assistant', 'content': row['text']}])
        return history

    def pair(self, case, history, text, expected):
        result = {}
        # Alternate order while retaining the same shared history and sampling.
        order = self.variants if len(self.rows) // 2 % 2 == 0 else self.variants[::-1]
        for variant in order:
            row = self.call(history, text, variant)
            row.update(event='memory_probe', case=case, expected=expected,
                mechanical_pass=row['production_reply_allowed'] and mechanical_check(row['text'], expected),
                semantic_review_required=True, public_fixture=True,
                microphone_opened=False, actions_executed=0)
            self.rows.append(row); result[variant] = row; self.emit(row)
        if len({r['shared_conditioning_sha256'] for r in result.values()}) != 1:
            raise ValueError('Variants received different dialogue conditioning')
        return result

    def run(self, fixture):
        for topic in fixture['topics']:
            history = self.seed(topic)
            self.pair(topic['id'] + '/direct', history, DIRECT, topic['expected'])
            if topic.get('acknowledgement_probe'):
                ack = self.pair(topic['id'] + '/ack', history, ACK, {'kind': 'ack'})
                shared = ack['baseline']
                if not shared['production_reply_allowed']:
                    raise ValueError('Cannot invent a reply for the frozen follow-up history')
                extended = history + [{'role': 'user', 'content': ACK}, {'role': 'assistant', 'content': shared['text']}]
                self.pair(topic['id'] + '/after_ack', extended, FOLLOWUP, topic['expected'])
        for control in fixture['controls']:
            self.pair(control['id'], [], control['text'], control['expected'])

    def summary(self):
        variants = {}
        for variant in self.variants:
            rows = [r for r in self.rows if r['variant'] == variant]
            values = [r['seconds'] for r in rows]
            variants[variant] = {'probes': len(rows),
                'mechanical_passes': sum(r['mechanical_pass'] for r in rows),
                'guarded_or_incomplete': sum(not r['production_reply_allowed'] for r in rows),
                'seconds_median': round(statistics.median(values), 3) if values else None,
                'seconds_max': max(values, default=None),
                'input_tokens_max': max((r['input_tokens'] for r in rows), default=None)}
        return {'event': 'memory_comparison_summary', 'seeds': self.seeds, 'compared_generations': len(self.rows),
                'variants': variants, 'semantic_review_required': True, 'voice_acceptance': False,
                'production_changed': False, 'model_comparisons_reopened': False}


def main():
    if len(sys.argv) != 2: raise ValueError('Expected private inference key path only')
    path = HERE / 'conversation_memory_cases.json'
    if not path.exists(): path = HERE.parent / 'tests/conversation_memory_cases.json'
    raw = path.read_bytes(); fixture = json.loads(raw)
    if fixture['version'] != 1 or fixture['model_id'] != 'lfm': raise ValueError('Expected the frozen LFM-only fixture')
    model = ConversationModel(sys.argv[1])
    experiment = Experiment(model, fixture['candidate_note'])
    emit({'event': 'memory_comparison_started', 'fixture_sha256': hashlib.sha256(raw).hexdigest(),
          'model': 'lfm', 'variants': VARIANTS, 'candidate_note': fixture['candidate_note'],
          'context_tokens': 2048, 'input_tokens': 1536, 'output_tokens': 96,
          'temperature': 0, 'seed': 42, 'max_experiment_seconds': 240,
          'microphone_opened': False, 'tools_present': False, 'executor_present': False,
          'external_network': False, 'public_fixture_only': True})
    try:
        started = time.monotonic()
        for variant in VARIANTS: experiment.call([], 'Hola.', variant, maximum=1)
        emit({'event': 'memory_prefix_preparation', 'seconds': round(time.monotonic() - started, 3), 'scored': False})
        experiment.run(fixture)
        emit(experiment.summary())
    finally:
        model.cancel()


if __name__ == '__main__': main()
