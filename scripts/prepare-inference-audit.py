"""Workstation only: freeze public histories and official template/tokenizer references.

No inference here. Large weights are only read for GGUF metadata, never executed.
Run with the isolated Jinja2/tokenizers environment described in the report.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
REFERENCES = {
    'qwen17': ('Qwen/Qwen3-1.7B', '70d244cc86ccca08cf5af4e1e306ecf908b1ad5e',
               'Qwen_Qwen3-1.7B-Q4_K_M.gguf',
               'conversation-only-qwen-20261017-152610.jsonl', 'identity-baseline-holdout-20261017-162032.jsonl'),
    'qwen35': ('Qwen/Qwen3.5-2B', '15852e8c16360a2fea060d615a32b45270f8a8fc',
               'Qwen3.5-2B-Q4_K_M.gguf',
               'qwen35-no-penalty-conversation-20261017-165133.jsonl', 'qwen35-no-penalty-holdout-20261017-164840.jsonl'),
}


def digest(value):
    if not isinstance(value, bytes):
        value = json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(',', ':')).encode()
    return hashlib.sha256(value).hexdigest()


def gguf_metadata(path):
    fixed = {0: 'B', 1: 'b', 2: 'H', 3: 'h', 4: 'I', 5: 'i', 6: 'f', 7: '?', 10: 'Q', 11: 'q', 12: 'd'}
    wanted = {'general.architecture', 'tokenizer.chat_template', 'tokenizer.ggml.model',
              'tokenizer.ggml.pre', 'tokenizer.ggml.bos_token_id', 'tokenizer.ggml.eos_token_id',
              'tokenizer.ggml.add_bos_token', 'tokenizer.ggml.add_eos_token'}
    with path.open('rb') as handle:
        def number(code): return struct.unpack('<' + code, handle.read(struct.calcsize('<' + code)))[0]
        def string(keep=True):
            size = number('Q')
            if size > 128 * 1024 * 1024: raise ValueError('Excessive GGUF metadata string')
            if keep: return handle.read(size).decode('utf-8')
            handle.seek(size, 1)
        def value(kind, keep):
            if kind == 8: return string(keep)
            if kind == 9:
                item, count = number('I'), number('Q')
                if keep: return [value(item, True) for _ in range(count)]
                if item in fixed: handle.seek(count * struct.calcsize('<' + fixed[item]), 1)
                else:
                    for _ in range(count): value(item, False)
                return
            if kind not in fixed: raise ValueError('Unknown GGUF metadata type')
            result = number(fixed[kind])
            return result if keep else None
        if handle.read(4) != b'GGUF' or number('I') != 3: raise ValueError('Expected GGUF v3')
        number('Q')
        count = number('Q'); result = {}
        for _ in range(count):
            key = string(); kind = number('I')
            found = value(kind, key in wanted)
            if key in wanted: result[key] = found
        return result


def read_trace(path):
    data = path.read_bytes()
    text = data.decode('utf-16') if data.startswith((b'\xff\xfe', b'\xfe\xff')) else data.decode('utf-8-sig')
    # Original stdout passed through cp850. Only reverse when the archived
    # corruption marker exists; prove byte-for-byte reversibility of the text.
    repaired = False
    if '\u251c' in text:
        original = text
        text = text.encode('cp850').decode('utf-8')
        if text.encode('utf-8').decode('cp850') != original: raise ValueError('Non-reversible trace transport')
        repaired = True
    records = []
    for line in text.splitlines():
        try: records.append(json.loads(line))
        except ValueError: continue
    return records, {'file': path.name, 'sha256': digest(data), 'reversed_cp850_transport': repaired}


def freeze_histories(system, rows, prefix):
    histories, messages = [], {}
    for row in rows:
        if 'turn' not in row: continue
        conversation = row['conversation']
        history = messages.setdefault(conversation, [{'role': 'system', 'content': system}])
        history.append({'role': 'user', 'content': row['input']})
        histories.append({'id': prefix + '/' + conversation + '/' + row['turn'],
                          'messages': json.loads(json.dumps(history)), 'expected': row['expected']})
        if row.get('history_continuation_aborted') or not row.get('response'):
            raise ValueError('Cannot reconstruct incomplete conversation')
        history.append(row['response'])
    return histories


def download(directory, repo, revision, filename):
    target = directory / filename
    if not target.exists():
        url = 'https://huggingface.co/' + repo + '/resolve/' + revision + '/' + filename
        with urllib.request.urlopen(url, timeout=45) as response: data = response.read(25 * 1024 * 1024 + 1)
        if len(data) > 25 * 1024 * 1024: raise ValueError('Reference metadata too large')
        target.write_bytes(data)
    return target


def language_histories(suite):
    result = []
    for system_language, system in suite['systems'].items():
        for user_language in ['es', 'en']:
            for case in suite['cases']:
                result.append({'id': 'language/' + system_language + '/' + user_language + '/' + case['id'],
                               'messages': [{'role': 'system', 'content': system},
                                            {'role': 'user', 'content': case['questions'][user_language]}],
                               'expected': {'semantic_review_facts': case['semantic_review_facts']}})
    return result


def prepare(model, destination, language=False):
    from jinja2.sandbox import ImmutableSandboxedEnvironment
    from tokenizers import Tokenizer
    repo, revision, filename, regression, holdout = REFERENCES[model]
    directory = destination / model
    directory.mkdir(parents=True, exist_ok=True)
    config_path = download(directory, repo, revision, 'tokenizer_config.json')
    tokenizer_path = download(directory, repo, revision, 'tokenizer.json')
    config = json.loads(config_path.read_bytes())
    if model == 'qwen35':
        template_path = download(directory, repo, revision, 'chat_template.jinja')
        official = template_path.read_text(encoding='utf-8')
    else: official = config['chat_template']
    metadata = gguf_metadata(ROOT / 'artifacts' / 'models' / filename)
    embedded = metadata.pop('tokenizer.chat_template')
    (directory / 'embedded.jinja').write_text(embedded, encoding='utf-8')
    (directory / 'official.jinja').write_text(official, encoding='utf-8')
    environment = ImmutableSandboxedEnvironment(trim_blocks=True, lstrip_blocks=True)
    def fail(message): raise ValueError(message)
    environment.globals['raise_exception'] = fail
    reference_template = environment.from_string(official)
    embedded_template = environment.from_string(embedded)
    tokenizer = Tokenizer.from_file(str(tokenizer_path))
    tokenizer.no_padding(); tokenizer.no_truncation()
    suite = json.loads((ROOT / 'tests' / 'conversation_only_cases.json').read_bytes())
    records, sources = [], []
    for name, prefix in [(regression, 'regression'), (holdout, 'holdout')]:
        rows, source = read_trace(ROOT / 'artifacts' / 'evaluations' / name)
        sources.append(source); records += freeze_histories(suite['system'], rows, prefix)
    # New controls fixed before inference; no ideal assistant turns injected.
    for key, text in [
        ('fresh/pet', 'Me llamo Inés y mi gato se llama Dátil. ¿Cómo me llamo, de quién es el gato y cómo se llama?'),
        ('fresh/gift', 'Soy Diego. Mi vecina Pilar me regaló una taza. ¿La taza es tuya o mía?'),
    ]:
        records.append({'id': key, 'messages': [{'role': 'system', 'content': suite['system']},
                        {'role': 'user', 'content': text}], 'expected': {'semantic_review_required': True}})
    if language:
        raw_language = (ROOT / 'tests' / 'language_control_cases.json').read_bytes()
        records = language_histories(json.loads(raw_language))
        sources = [{'file': 'tests/language_control_cases.json', 'sha256': digest(raw_language)}]
    for row in records:
        parameters = dict(messages=row['messages'], add_generation_prompt=True, enable_thinking=False,
                          bos_token=config.get('bos_token') or '', eos_token=config.get('eos_token') or '')
        row['official_prompt'] = reference_template.render(**parameters)
        row['embedded_prompt'] = embedded_template.render(**parameters)
        row['official_tokens'] = tokenizer.encode(row['official_prompt'], add_special_tokens=False).ids
        row['embedded_reference_tokens'] = tokenizer.encode(row['embedded_prompt'], add_special_tokens=False).ids
    selected = ['regression/natalia/perspective', 'holdout/camila/name_and_nickname', 'fresh/pet', 'fresh/gift']
    if language: selected = [row['id'] for row in records]
    artifact = {'version': 1, 'model': model, 'repository': repo, 'revision': revision,
                'sources': sources, 'gguf_metadata': metadata,
                'official_template_sha256': digest(official.encode()),
                'embedded_template_sha256': digest(embedded.encode()),
                'tokenizer_json_sha256': digest(tokenizer_path.read_bytes()),
                'generation_cases': selected, 'audits': records,
                'inference_on_workstation': False, 'production_approved': False}
    output = directory / ('language_route_cases.json' if language else 'inference_route_cases.json')
    output.write_text(json.dumps(artifact, ensure_ascii=True, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'model': model, 'file': str(output), 'sha256': digest(output.read_bytes()),
                      'audits': len(records), 'generation_cases': selected,
                      'template_text_equal': official == embedded, 'metadata': metadata}), flush=True)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--language', action='store_true')
    args = parser.parse_args()
    for model in REFERENCES: prepare(model, args.output, args.language)


if __name__ == '__main__': main()
