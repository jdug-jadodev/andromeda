import sys
import types
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))

import deterministic_router
from assistant_tools_trial import AssistantToolsTrial, CANCEL_TEXT
from timer_proposals import literal_seconds


def model():
    return types.SimpleNamespace(
        reminder_deletion=True, basic_assistant=True, alarm_lifecycle=True,
        guided_agenda=True, anchor_wall=1792610130000, confirmed_volume=True,
        tv_enabled=True, reminder_clarification=True)


CANCELS = ('cancelalo', 'cancelala', 'cancelame', 'cancela', 'cancela eso', 'cancela esto',
           'cancela lo', 'dejalo', 'dejala', 'olvidalo', 'olvidala', 'olvidalo ya',
           'detente', 'deten', 'detente ya', 'parate', 'para ya', 'no sigas', 'aborta',
           'cancelalo ya', 'cancela ya', 'cancelar', 'cancelar por favor',
           'andromeda cancelalo', 'andromeda, cancelalo', 'por favor cancelalo')


class GlobalCancelTests(unittest.TestCase):
    def test_cancel_phrases_stop_any_pending_request(self):
        # Bug real: tras "pon un temporizador" no había diálogo abierto y
        # "cancélalo" se iba a la nube (kind=reply -> cloud_fallback).
        for phrase in CANCELS:
            with self.subTest(phrase=phrase):
                self.assertEqual(deterministic_router.route(phrase, model()), {'kind': 'cancelled'})

    def test_bare_no_keeps_its_own_meaning(self):
        # "no" puede ser parte de otra orden ("no, ponlo a las ocho").
        self.assertNotEqual(deterministic_router.route('no', model()), {'kind': 'cancelled'})

    def test_cancelled_turn_says_short_farewell_and_closes(self):
        trial = AssistantToolsTrial(lambda: 100000)
        turn = dict(id='a' * 32, seq=1, text='cancelalo', speech_end=99900)
        self.assertEqual(
            trial.model_result({'kind': 'cancelled'}, turn, 'b' * 32, 'c' * 32, 200000),
            {'text': CANCEL_TEXT, 'offered_more': False, 'close_reason': 'explicit_close'})
        self.assertNotIn('?', CANCEL_TEXT)
        self.assertNotIn('Necesitas', CANCEL_TEXT)


class TimerPhrasingTests(unittest.TestCase):
    def test_timer_accepts_connectors_and_articles(self):
        for phrase in ('pon un temporizador de 5 minutos',
                       'con un temporizador de 5 minutos',
                       'o un temporizador de 5 minutos',
                       'y un temporizador de cinco minutos',
                       'un temporizador de 5 minutos',
                       'pon temporizador de 5 minutos',
                       'temporizador de cinco minutos'):
            with self.subTest(phrase=phrase):
                self.assertEqual(literal_seconds(phrase), 300)

    def test_bare_duration_after_the_question(self):
        for phrase in ('5 minutos', 'de 5 minutos', 'de cinco minutos', 'cinco minutos'):
            with self.subTest(phrase=phrase):
                self.assertEqual(literal_seconds(phrase), 300)

    def test_timer_without_duration_asks_instead_of_rejecting(self):
        for phrase in ('pon un temporizador', 'con un temporizador', 'un temporizador'):
            with self.subTest(phrase=phrase):
                self.assertEqual(deterministic_router.route(phrase, model()),
                                 {'kind': 'timer_rejected', 'reason': 'missing_duration'})

    def test_router_proposes_a_complete_timer(self):
        self.assertEqual(deterministic_router.route('con un temporizador de 5 minutos', model()),
                         {'kind': 'timer_proposal', 'seconds': 300})


if __name__ == '__main__':
    unittest.main()
