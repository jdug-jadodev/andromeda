"""Simulate power decisions only. Never starts/stops a model or a microphone."""
import argparse
from dataclasses import asdict
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from assistant_power import PowerPolicy, Resources, Settings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--enabled', action='store_true', help='Simulate enabled mode, not real activation')
    parser.add_argument('--listener', action='store_true', help='Simulate listener consent; opens no microphone')
    parser.add_argument('--manual-hold', action='store_true')
    parser.add_argument('--model-running', action='store_true', help='Synthetic observed state')
    parser.add_argument('--at', type=datetime.fromisoformat, default=None, help='ISO8601 with UTC offset')
    parser.add_argument('--available-kib', type=int)
    parser.add_argument('--battery-deci-c', type=int)
    parser.add_argument('--event', choices=['tick', 'suspend', 'resume', 'rearm_safety'], default='tick')
    args = parser.parse_args()
    sample = Resources(args.available_kib, args.battery_deci_c) if args.available_kib is not None and args.battery_deci_c is not None else None
    policy = PowerPolicy(Settings(enabled=args.enabled, activation_listener=args.listener), args.manual_hold)
    try: decision = policy.decide(args.at or datetime.now(timezone.utc), sample, args.event, args.model_running)
    except ValueError: parser.error('Use a valid timezone-aware clock and typed resources')
    print(json.dumps(dict(asdict(decision), executed=False, microphone_opened=False, schedule_applied=False)))


if __name__ == '__main__': main()
