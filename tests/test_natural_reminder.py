"""Modo natural: DeepSeek extrae el dato y la casa valida + confirma (sin re-parsear).

Sin teléfono, sin red, sin modelo: solo la capa de datos y la máquina de
confirmación del backend. `ANDROMEDA_NATURAL=1` se activa en setUp.
"""
import os
from datetime import datetime, timezone, timedelta
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))

BOGOTA = timezone(timedelta(hours=-5))


def _anchor():
    return int(datetime(2026, 11, 24, 15, 0, tzinfo=BOGOTA).timestamp() * 1000)


class NaturalReminderTests(unittest.TestCase):
    def setUp(self):
        os.environ['ANDROMEDA_NATURAL'] = '1'
        os.environ.pop('ANDROMEDA_TOOL_ARGUMENTS', None)
        # Reimportar para que tool_arguments/enabled y TOOLS lean la bandera nueva.
        import tool_arguments
        import deterministic_router
        import assistant_tools_trial
        self.tool_arguments = tool_arguments
        self.router = deterministic_router
        self.trial_mod = assistant_tools_trial

    def tearDown(self):
        os.environ.pop('ANDROMEDA_NATURAL', None)

    def test_lenient_propose_accepts_deepseek_extraction(self):
        args = {'text': 'comprar pan', 'when_text': 'mañana', 'date': '2026-11-25', 'time': '10:00'}
        transcript = 'pon un recordatorio para mañana porque me acordé que tengo que comprar pan'
        p = self.tool_arguments.propose('REMINDER', args, transcript, _anchor(), lenient=True)
        self.assertTrue(p['ok'], p)
        self.assertEqual(p['value']['text'], 'comprar pan')
        self.assertGreater(p['value']['due_ms'], _anchor())

    def test_full_natural_confirmation_and_schedule(self):
        from assistant_tools_trial import AssistantToolsTrial
        args = {'text': 'comprar pan', 'when_text': 'mañana', 'date': '2026-11-25', 'time': '10:00'}

        class Model:
            anchor_wall = _anchor()
            alarm_lifecycle = False
            series_supported = False
            guided_agenda = True
            reminder_clarification = True
            reminder_deletion = False

        result = self.router._dispatch('create_reminder', 'pon un recordatorio', Model(), args)
        self.assertEqual(result['kind'], 'reminder_ready')
        self.assertTrue(result.get('natural'))

        clock = [100000]
        trial = AssistantToolsTrial(lambda: clock[0], guided_agenda=True)
        t1 = {'id': '1' * 32, 'seq': 1, 'text': 'pon un recordatorio', 'speech_end': 99900,
              'calendar_anchor_ms': _anchor(), 'calendar_boot_ms': 100000}
        confirm = trial.model_result(result, t1, 'a' * 32, 'b' * 32, 400000)
        self.assertIn('¿Lo guardo?', confirm['text'])
        self.assertIsNotNone(trial.pending_confirm)

        # «sí» programa con nonce
        clock[0] = 103000
        t2 = {'id': '2' * 32, 'seq': 2, 'text': 'sí', 'speech_end': 102900,
              'calendar_anchor_ms': _anchor() + 3000, 'calendar_boot_ms': 103000}
        grant = trial.human(t2, 'a' * 32, 'b' * 32, 400000)
        self.assertTrue(grant.get('executing'))
        self.assertEqual(grant['event']['type'], 'agenda_execute')
        self.assertTrue(grant['event'].get('natural'))
        self.assertEqual(grant['event']['text'], 'comprar pan')

    def test_no_cancels_natural_reminder(self):
        from assistant_tools_trial import AssistantToolsTrial
        args = {'text': 'comprar pan', 'when_text': 'mañana', 'date': '2026-11-25', 'time': '10:00'}

        class Model:
            anchor_wall = _anchor()
            alarm_lifecycle = False
            series_supported = False
            guided_agenda = True
            reminder_clarification = True
            reminder_deletion = False

        result = self.router._dispatch('create_reminder', 'pon un recordatorio', Model(), args)
        clock = [100000]
        trial = AssistantToolsTrial(lambda: clock[0], guided_agenda=True)
        t1 = {'id': '1' * 32, 'seq': 1, 'text': 'pon un recordatorio', 'speech_end': 99900,
              'calendar_anchor_ms': _anchor(), 'calendar_boot_ms': 100000}
        trial.model_result(result, t1, 'a' * 32, 'b' * 32, 400000)
        clock[0] = 103000
        t2 = {'id': '2' * 32, 'seq': 2, 'text': 'no', 'speech_end': 102900,
              'calendar_anchor_ms': _anchor() + 3000, 'calendar_boot_ms': 103000}
        response = trial.human(t2, 'a' * 32, 'b' * 32, 400000)
        self.assertEqual(response['close_reason'], 'explicit_close')
        self.assertIsNone(trial.pending_confirm)

    def test_natural_grant_includes_weekday_and_minute_for_alarm_lifecycle(self):
        from assistant_tools_trial import AssistantToolsTrial
        args = {'text': 'que tengo que cenar', 'when_text': 'en dos minutos', 'date': '2026-11-24', 'time': '15:02'}

        class Model:
            anchor_wall = _anchor()
            alarm_lifecycle = True
            series_supported = False
            guided_agenda = True
            reminder_clarification = True
            reminder_deletion = False
            pending_subject = ''

        result = self.router._dispatch('create_reminder', 'recuerdame cenar', Model(), args)
        clock = [100000]
        trial = AssistantToolsTrial(lambda: clock[0], guided_agenda=True, alarm_lifecycle=True)
        t1 = {'id': '1' * 32, 'seq': 1, 'text': 'recuerdame cenar', 'speech_end': 99900,
              'calendar_anchor_ms': _anchor(), 'calendar_boot_ms': 100000}
        trial.model_result(result, t1, 'a' * 32, 'b' * 32, 400000)
        clock[0] = 103000
        t2 = {'id': '2' * 32, 'seq': 2, 'text': 'sí', 'speech_end': 102900,
              'calendar_anchor_ms': _anchor() + 3000, 'calendar_boot_ms': 103000}
        grant = trial.human(t2, 'a' * 32, 'b' * 32, 400000)
        self.assertTrue(grant.get('executing'))
        self.assertEqual(grant['event'].get('weekday'), 0)
        self.assertEqual(grant['event'].get('minute_of_day'), -1)


if __name__ == '__main__':
    unittest.main()
