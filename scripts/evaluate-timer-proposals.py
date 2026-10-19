"""Fixed public text probe of LFM native tool selection. NO Android executor.

Every case starts empty. This cannot authorize, persist or schedule a timer.
Raw responses below contain only the fixed public cases, never human speech.
"""
import json
from pathlib import Path
import sys
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'backend'))
from conversation_voice import now_ms
from timer_trial_model import TimerTrialModel

CASES = (
    ('asr_ten_minutes', 'pon un temporizador de diez minutos', 600),
    ('seconds', 'Pon un temporizador de diez segundos.', 10),
    ('minutes', 'Pon un temporizador de dos minutos.', 120),
    ('combined', 'Crea un temporizador de una hora y dos minutos.', 3720),
    ('negation', 'No pongas un temporizador de dos minutos.', None),
    ('reported', 'Ayer dije pon un temporizador de dos minutos.', None),
    ('missing', 'Pon un temporizador.', None),
    ('new_question', '¿Qué es una estrella?', None),
    ('bare_yes', 'Sí.', None),
)


def emit(row): print(json.dumps(row, ensure_ascii=True), flush=True)


def main():
    if len(sys.argv) != 2: raise ValueError('Expected private inference key path only')
    model = TimerTrialModel(sys.argv[1]); end = now_ms() + 200000
    emit({'event': 'timer_proposal_probe_started', 'model': 'lfm', 'cases': len(CASES),
          'context_tokens': 2048, 'input_tokens': 1536, 'output_tokens': 96,
          'public_text_only': True, 'microphone_opened': False, 'audio_played': False,
          'executor_present': False, 'android_actions_executed': 0, 'external_network': False})
    failures = []
    try:
        model.prepare()  # Cold prefix preparation, not a scored audible answer.
        for identity, text, seconds in CASES:
            if now_ms() >= end: raise TimeoutError('Bounded text experiment expired')
            model.memory.pairs.clear(); model.memory.last_human = 0
            raw = []; original = model.interpret_result
            def capture(message, reason, request):
                raw.append({'message': message, 'finish_reason': reason})
                return original(message, reason, request)
            model.interpret_result = capture
            started = now_ms(); compute_end = model.start(text, started, end)
            if not model.done.wait(max(0, compute_end - now_ms()) / 1000 + .5):
                raise TimeoutError('Text generation did not finish within its compute deadline')
            model.interpret_result = original
            passed = model.error is None and isinstance(model.result, dict) and (
                model.result == {'kind': 'timer_proposal', 'seconds': seconds} if seconds is not None
                else model.result.get('kind') == 'reply')
            if not passed: failures.append(identity)
            emit({'event': 'timer_proposal_probe_case', 'case': identity, 'request': text,
                  'expected_seconds': seconds, 'result': model.result, 'error': model.error,
                  'raw_public_response': raw, 'mechanical_pass': passed,
                  'elapsed_ms': now_ms() - started, **model.context_metrics})
        emit({'event': 'timer_proposal_probe_summary', 'cases': len(CASES), 'failed_cases': failures,
              'proposal_selection_only': True, 'voice_approved': False,
              'executor_connected': False, 'android_actions_executed': 0})
        return 1 if failures else 0
    finally: model.cancel()


if __name__ == '__main__': sys.exit(main())
