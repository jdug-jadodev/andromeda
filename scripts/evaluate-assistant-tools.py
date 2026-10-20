"""Public native-selection-only control. No Android executor/capture/playback/external network."""
import json
from datetime import datetime, timedelta
from pathlib import Path
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
import assistant_tools_model as adapter
from conversation_voice import now_ms
from agenda_requests import BOGOTA

CASES = [('alarm', 'pon una alarma mañana a las ocho de la mañana', 'agenda_proposal'),
          ('alarm_relative', 'pon una alarma en dos minutos', 'agenda_proposal'),
          ('alarm_relative_repeat_2', 'pon una alarma en dos minutos', 'agenda_proposal'),
          ('alarm_relative_repeat_3', 'pon una alarma en dos minutos', 'agenda_proposal'),
         ('reminder', 'recuérdame beber agua en dos minutos', 'agenda_proposal'),
          ('reminder_direct', 'crea un recordatorio en dos minutos para beber agua', 'agenda_proposal'),
          ('reminder_date', 'recuérdame el veinticinco de octubre de 2026 a las tres de la tarde que tengo una cita', 'agenda_proposal'),
          ('reminder_date_repeat_2', 'recuérdame el veinticinco de octubre de 2026 a las tres de la tarde que tengo una cita', 'agenda_proposal'),
          ('reminder_date_repeat_3', 'recuérdame el veinticinco de octubre de 2026 a las tres de la tarde que tengo una cita', 'agenda_proposal'),
          ('reminder_date_alternate', 'crea un recordatorio para el veintiséis de octubre de 2026 a las 16:00 para renovar el seguro', 'agenda_proposal'),
         ('timer', 'pon un temporizador de dos minutos', 'timer_proposal'),
         ('web', 'busca en Internet qué es una nebulosa', 'web_result'),
         ('negated', 'no pongas una alarma mañana a las ocho de la mañana', 'no_action'),
         ('reminder_negated', 'no me recuerdes beber agua en dos minutos', 'no_action'),
         ('ambiguous', 'pon una alarma mañana a las ocho', 'no_action'),
         ('reminder_quoted', 'dije «recuérdame beber agua en dos minutos»', 'no_action'),
         ('alarm_reported', 'ayer dije pon una alarma mañana a las ocho de la mañana', 'no_action'),
         ('reminder_mixed', 'recuérdame beber agua en dos minutos y dime un chiste', 'no_action'),
         ('reminder_incomplete', 'recuérdame beber agua', 'no_action'),
         ('alarm_mixed', 'pon una alarma mañana a las ocho de la mañana y busca en Internet nebulosas', 'no_action')]


def correct_fields(identity, result, anchor):
    """Check actual proposal data, not just that some tool returned a kind."""
    if identity == 'alarm':
        day = datetime.fromtimestamp(anchor / 1000, BOGOTA).date() + timedelta(days=1)
        expected = dict(event_kind='ALARM', text='', delay_ms=0, zone='America/Bogota',
                        due_ms=int(datetime(day.year, day.month, day.day, 8, tzinfo=BOGOTA).timestamp() * 1000))
    elif identity.startswith('alarm_relative'):
        expected = dict(event_kind='ALARM', text='', delay_ms=120000,
                        zone='America/Bogota', due_ms=anchor + 120000)
    elif identity.startswith('reminder_date'):
        expected = dict(event_kind='REMINDER', delay_ms=0, zone='America/Bogota',
                        **({'text': 'renovar el seguro', 'due_ms': 1793048400000} if identity == 'reminder_date_alternate'
                           else {'text': 'tengo una cita', 'due_ms': 1792958400000}))
    elif identity in {'reminder', 'reminder_direct'}:
        expected = dict(event_kind='REMINDER', text='beber agua', delay_ms=120000,
                        zone='America/Bogota', due_ms=anchor + 120000)
    elif identity == 'timer': expected = dict(seconds=120)
    elif identity == 'web': return bool(result.get('sources'))
    else: return True
    return all(type(result.get(key)) is type(value) and result.get(key) == value for key, value in expected.items())


def main():
    if len(sys.argv) != 2: raise ValueError('Inference key path required')
    model = adapter.AssistantToolsModel(sys.argv[1]); end = now_ms() + 200000
    adapter.buscar_en_internet = lambda *args, **kwargs: {'respuesta': 'Extracto público simulado para probar selección, no una búsqueda real.',
        'contexto_para_ia': '', 'fuentes': [{'title': 'Public synthetic source', 'url': 'https://example.org/public',
        'excerpt': 'Extracto público simulado para probar selección, no una búsqueda real.'}]}
    failed = []
    try:
        began = now_ms(); model.prepare()
        print(json.dumps({'event': 'tools_prefix_prepared', 'elapsed_ms': now_ms()-began, 'public_text_only': True, 'executor_present': False}), flush=True)
        for identity, human, expected in CASES:
            raw = []; original = model.interpret_result
            def capture(message, finish, request):
                raw.append({'message': message, 'finish_reason': finish})
                return original(message, finish, request)
            model.interpret_result = capture
            model.memory.pairs.clear(); model.memory.last_human = 0
            model.anchor_wall, model.anchor_boot = int(time.time()*1000), now_ms()
            began = now_ms(); deadline = model.start(human, began, end)
            if not model.done.wait(max(0, deadline-now_ms())/1000 + .5): raise TimeoutError('Native selection deadline')
            kind = model.result.get('kind', '') if isinstance(model.result, dict) else ''
            model.interpret_result = original
            passed = model.error is None and (kind == expected if expected != 'no_action' else kind in {'reply', 'tools_rejected'})
            fields_valid = correct_fields(identity, model.result or {}, model.anchor_wall)
            passed = passed and fields_valid
            if not passed: failed.append(identity)
            print(json.dumps({'event': 'tools_native_case', 'case': identity, 'kind': kind, 'error': model.error,
                'passed': passed, 'proposal_fields_valid': fields_valid, 'elapsed_ms': now_ms()-began, 'raw_public_response': raw, **model.context_metrics}), flush=True)
        print(json.dumps({'event': 'tools_native_summary', 'failed': failed, 'audio_played': False, 'microphone_opened': False,
                          'android_actions_executed': 0, 'web_provider_simulated': True, 'voice_accepted': False}), flush=True)
        return int(bool(failed))
    finally: model.cancel()


if __name__ == '__main__': sys.exit(main())
