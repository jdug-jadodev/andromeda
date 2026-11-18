"""Aviso pendiente: preguntas, fusión y resumen. Sin teléfono, modelo ni ejecución."""
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
import pending_action as pending
from agenda_requests import BOGOTA
from pending_action import (AmbiguousClock, PastTime, TTL_MS, new, merge,
    missing, expired, load, summary, resolve_confirmation)

# Miércoles 18 de noviembre de 2026, 12:00 en Bogotá.
WED = int(datetime(2026, 11, 18, 12, 0, tzinfo=timezone(timedelta(hours=-5))).timestamp() * 1000)
TUESDAY_24 = int(datetime(2026, 11, 24, 15, 0, tzinfo=BOGOTA).timestamp() * 1000)


class PendingActionTests(unittest.TestCase):
    def dialogue(self, tool, turns, anchor=WED, now_ms=1000):
        """Recorre turnos humanos y devuelve (resultados, aviso final)."""
        state, results = new(tool, 'a' * 32, now_ms), []
        for index, text in enumerate(turns):
            result = merge(state, text, anchor, now_ms + index)
            results.append(result)
            state = result['pending']
            if state is None:
                break
        return results, state

    def test_plan_reference_dialogue_step_by_step(self):
        results, state = self.dialogue('REMINDER',
            ['Reunión para algo', 'el martes', 'a las 3 de la tarde'])
        self.assertEqual([r['kind'] for r in results],
            ['pending_question', 'pending_question', 'pending_ready'])
        self.assertEqual(results[0]['text'], '¿Para cuándo te lo recuerdo?')
        self.assertEqual(results[1]['text'], '¿A qué hora te lo recuerdo?')
        self.assertTrue(results[2]['confirm'])
        self.assertEqual(results[2]['value']['due_ms'], TUESDAY_24)
        self.assertEqual(results[2]['text'],
            '“Reunión para algo”, el martes 24 de noviembre de 2026 a las 15:00. ¿Lo guardo?')

    def test_full_request_in_one_sentence_with_prefix(self):
        results, _ = self.dialogue('REMINDER',
            ['recuérdame ver el partido de champions el martes a las 3 de la tarde'])
        self.assertEqual([r['kind'] for r in results], ['pending_ready'])
        self.assertEqual(results[0]['value']['slots']['what'], 'ver el partido de champions')
        self.assertEqual(results[0]['value']['due_ms'], TUESDAY_24)

    def test_full_request_in_one_sentence_without_prefix(self):
        # «Reunión para algo el martes a las 3»: tema y cuándo de golpe.
        results, _ = self.dialogue('REMINDER',
            ['Reunión para algo el martes a las 3 de la tarde'])
        self.assertEqual([r['kind'] for r in results], ['pending_ready'])
        self.assertEqual(results[0]['value']['slots']['what'], 'Reunión para algo')
        self.assertEqual(results[0]['value']['due_ms'], TUESDAY_24)

    def test_one_question_per_turn_and_only_what_is_missing(self):
        results, _ = self.dialogue('REMINDER',
            ['comprar pan', 'el martes', 'a las 3', 'de la tarde'])
        texts = [r['text'] for r in results if r['kind'] == 'pending_question']
        self.assertEqual(texts, ['¿Para cuándo te lo recuerdo?', '¿A qué hora te lo recuerdo?',
                                 '¿3 de la mañana o de la tarde?'])
        # Una sola pregunta por turno, y nunca la oferta de cierre.
        for text in texts:
            self.assertEqual(text.count('?'), 1, text)
            self.assertNotIn('Necesitas algo', text)

    def test_corrections_keep_the_rest(self):
        results, _ = self.dialogue('REMINDER',
            ['reunión con Ana', 'el martes', 'a las 3 de la tarde', 'no, el 25'])
        self.assertEqual(results[-1]['value']['due_ms'],
            int(datetime(2026, 11, 25, 15, 0, tzinfo=BOGOTA).timestamp() * 1000))
        self.assertEqual(results[-1]['value']['slots']['what'], 'reunión con Ana')
        results, _ = self.dialogue('REMINDER',
            ['reunión', 'el 26', 'a las 3 de la tarde', 'mejor a las 4 de la tarde'])
        self.assertEqual(results[-1]['value']['due_ms'],
            int(datetime(2026, 11, 26, 16, 0, tzinfo=BOGOTA).timestamp() * 1000))

    def test_ambiguous_hour_asks_morning_or_evening_and_keeps_day(self):
        results, state = self.dialogue('REMINDER', ['comprar pan', 'el martes', 'a las 3'])
        self.assertEqual(results[-1]['kind'], 'pending_question')
        self.assertEqual(results[-1]['text'], '¿3 de la mañana o de la tarde?')
        self.assertEqual(results[-1]['slot'], 'period')
        self.assertEqual(state['slots']['day'], '2026-11-24')
        self.assertNotIn('hour', state['slots'])
        self.assertEqual(state['asked_hour'], 3)
        self.assertEqual(missing(state), 'hour')

    def test_past_or_impossible_dates_say_so(self):
        results, _ = self.dialogue('REMINDER', ['comprar pan', 'el 31 de noviembre a las 8 de la mañana'])
        self.assertEqual(results[-1]['kind'], 'pending_invalid')
        self.assertIn('no existe', results[-1]['text'])
        # Una hora de hoy que ya pasó también se dice, no se acepta.
        results, _ = self.dialogue('REMINDER', ['comprar pan', 'hoy a las 9 de la mañana'])
        self.assertEqual(results[-1]['kind'], 'pending_invalid')
        self.assertIn('ya pasó', results[-1]['text'])
        later = int(datetime(2026, 12, 1, 12, 0, tzinfo=BOGOTA).timestamp() * 1000)
        results, _ = self.dialogue('REMINDER',
            ['comprar pan', 'el 24 de noviembre a las 8', 'de la mañana'], anchor=later)
        # El año resuelto se dice en el resumen; no se asume en silencio.
        self.assertIn('de 2027', results[-1]['text'])

    def test_weekday_mismatch_is_said_not_hidden(self):
        results, _ = self.dialogue('REMINDER',
            ['reunión', 'el martes 25 de noviembre a las 3 de la tarde'])
        self.assertEqual(results[-1]['kind'], 'pending_invalid')
        self.assertIn('no coincide', results[-1]['text'])

    def test_alarm_weekly_and_one_shot(self):
        results, _ = self.dialogue('ALARM', ['todos los martes a las 7 de la mañana'])
        self.assertEqual(results[-1]['kind'], 'pending_ready')
        self.assertEqual(results[-1]['value']['weekly'], {'weekday': 2, 'minute_of_day': 420})
        self.assertEqual(results[-1]['text'], 'Alarma todos los martes a las 07:00. ¿La guardo?')
        results, _ = self.dialogue('ALARM', ['el 24 a las 7 de la mañana'])
        self.assertEqual(results[-1]['kind'], 'pending_ready')
        self.assertNotIn('weekly', results[-1]['value'])
        self.assertIn('¿La guardo?', results[-1]['text'])

    def test_timer_is_direct_without_confirmation(self):
        results, _ = self.dialogue('TIMER', ['5 minutos'])
        self.assertEqual(results[-1]['kind'], 'pending_ready')
        self.assertFalse(results[-1]['confirm'])
        self.assertEqual(results[-1]['value']['duration_ms'], 300000)
        self.assertEqual(results[-1]['text'], 'Temporizador de 5 minutos.')

    def test_other_topic_does_not_hijack_the_pending(self):
        results, state = self.dialogue('REMINDER',
            ['comprar pan', 'cuéntame un chiste', 'el martes', 'a las 3 de la tarde'])
        self.assertEqual(results[1]['kind'], 'pending_other_topic')
        self.assertIsNotNone(results[1]['pending'])
        self.assertEqual(state['slots']['what'], 'comprar pan')
        self.assertEqual(results[-1]['value']['due_ms'], TUESDAY_24)

    def test_cancel_discards_and_saves_nothing(self):
        for phrase in ('cancela', 'déjalo así', 'mejor no', 'olvídalo'):
            with self.subTest(phrase=phrase):
                results, state = self.dialogue('REMINDER', ['comprar pan', phrase])
                self.assertEqual(results[-1]['kind'], 'pending_cancelled')
                self.assertIsNone(state)
                self.assertIn('No he guardado nada', results[-1]['text'])

    def test_ttl_expires_and_returns_stale(self):
        state = new('REMINDER', 'a' * 32, 1000)
        self.assertFalse(expired(state, 1000 + TTL_MS - 1))
        self.assertTrue(expired(state, 1000 + TTL_MS))
        result = merge(state, 'comprar pan', WED, 1000 + TTL_MS)
        self.assertEqual(result['kind'], 'pending_stale')
        self.assertIsNone(result['pending'])
        self.assertIn('caducó', result['text'])

    def test_summary_is_one_line_and_asks_to_save(self):
        text = summary({'tool': 'REMINDER', 'slots': {'what': 'Reunión para algo',
            'day': '2026-11-24', 'hour': 15, 'minute': 0, 'due_ms': TUESDAY_24}})
        self.assertNotIn('\n', text)
        self.assertTrue(text.endswith('¿Lo guardo?'))
        text = summary({'tool': 'ALARM', 'slots': {'day': '2026-11-24', 'hour': 7,
            'minute': 0, 'due_ms': TUESDAY_24}})
        self.assertTrue(text.endswith('¿La guardo?'))

    def test_pending_round_trips_through_json(self):
        results, state = self.dialogue('REMINDER', ['Reunión para algo', 'el martes'])
        restored = json.loads(json.dumps(state))
        self.assertEqual(load(restored), state)
        self.assertEqual(merge(restored, 'a las 3 de la tarde', WED, 1000)['kind'], 'pending_ready')

    def test_confirmation_phrases(self):
        state = new('REMINDER', 'a' * 32, 1000)
        for text, expected in (('sí', 'confirmed'), ('dale', 'confirmed'), ('no', 'cancelled'),
                               ('cancela', 'cancelled'), ('mejor a las 4', 'unclear'),
                               ('eso no es lo que dije', 'unclear')):
            with self.subTest(text=text):
                self.assertEqual(resolve_confirmation(state, text), expected)

    def test_invalid_pending_is_rejected(self):
        good = new('REMINDER', 'a' * 32, 1000)
        for broken in ({'schema': 0}, {'tool': 'MUSIC'}, {**good, 'created_ms': 0},
                       {**good, 'slots': []}, {}, None):
            with self.subTest(broken=broken):
                with self.assertRaises(ValueError):
                    load(broken)
        with self.assertRaises(ValueError):
            new('MUSIC', 'a' * 32, 1000)
        with self.assertRaises(ValueError):
            new('REMINDER', 'a' * 32, 0)

    def test_time_helpers_fail_closed(self):
        for text in ('', 'reunión para algo', 'comprar pan'):
            with self.subTest(text=text):
                with self.assertRaises(ValueError):
                    pending._time_slots('REMINDER', text, WED)
        with self.assertRaises(AmbiguousClock):
            pending._time_slots('REMINDER', 'a las 3', WED)
        with self.assertRaises(PastTime):
            pending._time_slots('REMINDER', 'hoy a las 9 de la mañana', WED)
        with self.assertRaises(ValueError):
            pending._time_slots('REMINDER', 'el 19 de noviembre de 2026 a las 9 de la mañana',
                int(datetime(2026, 11, 20, 12, 0, tzinfo=BOGOTA).timestamp() * 1000))


if __name__ == '__main__':
    unittest.main()
