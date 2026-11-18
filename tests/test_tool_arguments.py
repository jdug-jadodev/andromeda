"""Datos de herramienta del modelo: prueba de origen y validación. Sin teléfono."""
from datetime import datetime
import json
import os
from pathlib import Path
import sys
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
import assistant_tools_model
import deterministic_router
import tool_arguments
from agenda_requests import BOGOTA

# Miércoles 18 de noviembre de 2026, 14:35 en Bogotá.
ANCHOR = int(datetime(2026, 11, 18, 14, 35, tzinfo=BOGOTA).timestamp() * 1000)
NEXT_1030 = int(datetime(2026, 11, 19, 10, 30, tzinfo=BOGOTA).timestamp() * 1000)
TUESDAY_1510 = int(datetime(2026, 11, 24, 15, 10, tzinfo=BOGOTA).timestamp() * 1000)

ANTIBIOTIC = ('recuérdame tomar el antibiótico todos los días de la semana '
              'a las diez y treinta de la mañana')
ANTIBIOTIC_ARGS = {'text': 'tomar el antibiótico',
                   'when_text': 'todos los días de la semana a las diez y treinta de la mañana'}


class FakeModel:
    alarm_lifecycle = False
    anchor_wall = ANCHOR
    guided_agenda = True
    reminder_clarification = True


class ToolArgumentsTests(unittest.TestCase):
    def propose(self, kind='REMINDER', args=None, transcript=ANTIBIOTIC, **options):
        return tool_arguments.propose(kind, dict(ANTIBIOTIC_ARGS if args is None else args),
                                      transcript, ANCHOR, **options)

    @unittest.skipIf(tool_arguments.enabled(), 'La bandera está encendida en este proceso')
    def test_flag_is_off_by_default_and_schemas_are_unchanged(self):
        self.assertFalse(tool_arguments.enabled())
        reminder = next(item for item in assistant_tools_model.TOOLS
                        if item['function']['name'] == 'create_reminder')
        self.assertEqual(reminder['function']['parameters']['properties'], {})
        self.assertEqual(reminder['function']['parameters']['required'], [])

    def test_extend_only_changes_agenda_tools(self):
        extended = tool_arguments.extend(assistant_tools_model.TOOLS)
        by_name = {item['function']['name']: item['function']['parameters'] for item in extended}
        self.assertEqual(by_name['create_reminder']['required'], ['text', 'when_text'])
        self.assertIn('repeat', by_name['create_reminder']['properties'])
        self.assertEqual(by_name['create_alarm']['required'], ['when_text'])
        self.assertEqual(by_name['create_timer']['properties'], {'duration_text': {'type': 'string'}})
        self.assertEqual(by_name['search_web']['properties'], {})

    def test_antibiotic_series_is_understood_from_the_quoted_span(self):
        result = self.propose(series=True)
        self.assertTrue(result['ok'], result)
        value = result['value']
        self.assertEqual(value['text'], 'tomar el antibiótico')
        self.assertEqual(value['repeat'], 'daily')
        self.assertEqual(value['source'], 'span')
        self.assertEqual(value['due_ms'], NEXT_1030)
        self.assertEqual(value['minute_of_day'], 10 * 60 + 30)

    def test_series_is_refused_until_the_app_supports_it(self):
        result = self.propose()
        self.assertFalse(result['ok'])
        self.assertEqual(result['reason'], 'series_unsupported')
        self.assertIn('una sola vez', result['message'])

    def test_invented_subject_or_time_is_rejected(self):
        for args in ({'text': 'comprar pan', 'when_text': ANTIBIOTIC_ARGS['when_text']},
                     {'text': ANTIBIOTIC_ARGS['text'], 'when_text': 'mañana a las once'}):
            with self.subTest(args=args):
                result = self.propose(args=args, series=True)
                self.assertFalse(result['ok'])
                self.assertEqual(result['reason'], 'no_evidence')
        self.assertFalse(self.propose(args={'when_text': ANTIBIOTIC_ARGS['when_text']},
                                      series=True)['ok'])

    def test_model_date_is_used_only_when_the_span_cannot_be_parsed(self):
        transcript = 'recuérdame llamar al taller el día martes a las tres y diez de la tarde'
        result = self.propose(args={'text': 'llamar al taller',
            'when_text': 'el día martes a las tres y diez de la tarde',
            'date': '2026-11-24', 'time': '15:10'}, transcript=transcript)
        self.assertTrue(result['ok'], result)
        self.assertEqual(result['value']['source'], 'model')
        self.assertEqual(result['value']['due_ms'], TUESDAY_1510)
        self.assertEqual(result['value']['repeat'], 'once')

    def test_model_date_must_match_the_named_weekday(self):
        transcript = 'recuérdame llamar al taller el día martes a las tres y diez de la tarde'
        result = self.propose(args={'text': 'llamar al taller',
            'when_text': 'el día martes a las tres y diez de la tarde',
            'date': '2026-11-25', 'time': '15:10'}, transcript=transcript)
        self.assertFalse(result['ok'])
        self.assertEqual(result['reason'], 'weekday_mismatch')

    def test_past_and_out_of_range_dates_are_rejected(self):
        transcript = 'recuérdame llamar al taller el día martes a las tres y diez de la tarde'
        for text_date in ('2026-11-17', '2028-11-24'):
            with self.subTest(date=text_date):
                result = self.propose(args={'text': 'llamar al taller',
                    'when_text': 'el día martes a las tres y diez de la tarde',
                    'date': text_date, 'time': '15:10'}, transcript=transcript)
                self.assertFalse(result['ok'])
                self.assertEqual(result['reason'], 'past')

    def test_ambiguous_hour_asks_morning_or_evening(self):
        transcript = 'recuérdame tomar el antibiótico todos los días a las diez'
        result = self.propose(args={'text': 'tomar el antibiótico',
            'when_text': 'todos los días a las diez'}, transcript=transcript, series=True)
        self.assertFalse(result['ok'])
        self.assertEqual(result['reason'], 'ambiguous')
        self.assertEqual(result['message'], '¿10 de la mañana o de la tarde?')

    def test_contradictory_repeat_is_rejected(self):
        transcript = 'recuérdame llamar al taller mañana a las diez de la mañana'
        result = self.propose(args={'text': 'llamar al taller',
            'when_text': 'mañana a las diez de la mañana', 'repeat': 'daily'},
            transcript=transcript, series=True)
        self.assertFalse(result['ok'])
        self.assertEqual(result['reason'], 'bad_repeat')

    def test_alarm_weekday_is_weekly_only_with_lifecycle(self):
        transcript = 'pon una alarma todos los martes a las siete de la mañana'
        args = {'when_text': 'todos los martes a las siete de la mañana'}
        refused = self.propose('ALARM', args, transcript)
        self.assertEqual(refused['reason'], 'series_unsupported')
        accepted = self.propose('ALARM', args, transcript, lifecycle=True)
        self.assertTrue(accepted['ok'], accepted)
        self.assertEqual(accepted['value']['repeat'], 'weekly')
        self.assertEqual((accepted['value']['weekday'], accepted['value']['minute_of_day']), (2, 420))

    def test_native_call_accepts_optional_arguments_only_with_the_new_schema(self):
        message = {'tool_calls': [{'type': 'function', 'function': {
            'name': 'create_reminder', 'arguments': json.dumps(ANTIBIOTIC_ARGS, ensure_ascii=False)}}]}
        with mock.patch.object(assistant_tools_model, 'TOOLS',
                               tool_arguments.extend(assistant_tools_model.TOOLS)):
            name, args = assistant_tools_model.native_call(message, 'tool_calls')
            self.assertEqual((name, args), ('create_reminder', ANTIBIOTIC_ARGS))
            for broken in ({'text': 'tomar el antibiótico'},
                           {**ANTIBIOTIC_ARGS, 'inventado': 'x'},
                           {'text': 'tomar el antibiótico', 'when_text': ''}):
                with self.subTest(broken=broken):
                    with self.assertRaises(ValueError):
                        assistant_tools_model.native_call({'tool_calls': [{'type': 'function',
                            'function': {'name': 'create_reminder',
                                         'arguments': json.dumps(broken, ensure_ascii=False)}}]}, 'tool_calls')

    def test_dispatch_uses_model_arguments_only_when_enabled(self):
        model = FakeModel()
        with mock.patch.dict(os.environ, {tool_arguments.FLAG: '1'}):
            refused = deterministic_router._dispatch('create_reminder', ANTIBIOTIC, model,
                                                     dict(ANTIBIOTIC_ARGS))
            self.assertEqual(refused['kind'], 'reply')
            self.assertIn('una sola vez', refused['text'])
            model.series_supported = True
            accepted = deterministic_router._dispatch('create_reminder', ANTIBIOTIC, model,
                                                      dict(ANTIBIOTIC_ARGS))
            self.assertEqual(accepted['kind'], 'agenda_proposal')
            self.assertEqual(accepted['repeat'], 'daily')
            self.assertEqual(accepted['due_ms'], NEXT_1030)
        with mock.patch.dict(os.environ, {tool_arguments.FLAG: ''}):
            fallback = deterministic_router._dispatch('create_reminder', ANTIBIOTIC, model,
                                                      dict(ANTIBIOTIC_ARGS))
            self.assertEqual(fallback, {'kind': 'agenda_dialogue_start', 'event_kind': 'REMINDER'})


if __name__ == '__main__':
    unittest.main()
