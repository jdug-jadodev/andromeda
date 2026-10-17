"""Read metadata-only JSONL traces. Never records audio or changes the device."""
import argparse
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from acceptance import evaluate

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('trace', help='JSONL annotations, no transcript/audio required')
    args = parser.parse_args()
    policy = json.loads((Path(__file__).resolve().parents[1] / 'config' / 'acceptance.json').read_text(encoding='utf-8'))
    path = Path(args.trace)
    if path.stat().st_size > 1024 * 1024:
        raise ValueError('Trace exceeds 1 MiB limit')
    records = [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines() if line.strip()]
    if not all(isinstance(row, dict) for row in records):
        raise ValueError('Expected JSON objects')
    report = evaluate(records, policy)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['passed'] else 1

if __name__ == '__main__': sys.exit(main())
