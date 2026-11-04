import sys
import threading
import time
import unittest
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
if not hasattr(time, 'clock_gettime'):   # Windows; the phone has it.
    time.clock_gettime = lambda clock: time.monotonic(); time.CLOCK_BOOTTIME = 7

from christmas_days import answer, days_to_christmas


class ChristmasDays(unittest.TestCase):
    def test_counts_down_by_itself(self):
        self.assertEqual(days_to_christmas(date(2026, 11, 4)), 50)
        self.assertEqual(days_to_christmas(date(2026, 11, 5)), 49)
        self.assertEqual(days_to_christmas(date(2026, 12, 23)), 1)
        self.assertEqual(days_to_christmas(date(2026, 12, 24)), 0)
        self.assertEqual(days_to_christmas(date(2026, 12, 25)), 364)
        self.assertEqual(days_to_christmas(date(2028, 2, 29)), 299)

    def test_recognises_the_question(self):
        for phrase in ('cuántos días faltan para navidad', 'en cuántos días es navidad', 'ya es navidad',
                       'cuánto falta para navidad', 'cuántos días quedan para la navidad', 'qué día es navidad',
                       'cuándo es navidad', 'hoy es navidad', 'falta mucho para navidad'):
            self.assertEqual(answer(phrase, date(2026, 11, 4))['kind'], 'santa', phrase)

    def test_leaves_everything_else_alone(self):
        for phrase in ('feliz navidad', 'cuéntame un cuento de navidad', 'cuántos días faltan', 'qué día es hoy',
                       'pon una alarma para navidad', 'recuérdame cuánto falta para navidad', 'busca qué día es navidad',
                       'pon música de navidad', 'prende las luces de navidad', ''):
            self.assertIsNone(answer(phrase, date(2026, 11, 4)), phrase)

    def test_says_it_right(self):
        self.assertEqual(answer('cuántos días faltan para navidad', date(2026, 11, 4))['text'], 'Faltan 50 días para Navidad.')
        self.assertEqual(answer('cuántos días faltan para navidad', date(2026, 12, 23))['text'], 'Falta un día para Navidad.')
        self.assertEqual(answer('ya es navidad', date(2026, 11, 4))['text'], 'Todavía no. Faltan 50 días para Navidad.')
        self.assertEqual(answer('ya es navidad', date(2026, 12, 24))['text'], '¡Es hoy! ¡Feliz Navidad!')


class ChristmasTurn(unittest.TestCase):
    def model(self):
        from assistant_tools_model import AssistantToolsModel
        from conversation_voice import ShortMemory
        model = object.__new__(AssistantToolsModel)
        model.clock = lambda: 1000
        model.memory, model.thread, model.basic_assistant = ShortMemory(model.clock), None, True
        model.cancelled, model.done = threading.Event(), threading.Event()
        model.result = model.error = None
        model._public_preparing = False
        return model

    def test_goes_before_the_date_answer_and_the_cloud(self):
        for phrase in ('qué día es navidad', 'cuántos días faltan para navidad', 'ya es navidad'):
            model = self.model()
            model.start(phrase, 900, 301000)
            self.assertTrue(model.done.is_set())
            self.assertEqual(model.result['kind'], 'santa', phrase)

    def test_other_questions_keep_their_answer(self):
        model = self.model()
        model.start('qué día es hoy', 900, 301000)
        self.assertEqual(model.result['kind'], 'reply')
        self.assertIn('Hoy es', model.result['text'])

    def test_scene_is_only_a_spoken_answer_for_the_session(self):
        from assistant_tools_trial import AssistantToolsTrial
        response = AssistantToolsTrial(lambda: 1000).model_result({'kind': 'santa', 'text': 'Faltan 50 días para Navidad.'}, {'id': 'x'}, 's', 'c', 0)
        self.assertEqual(response, {'text': 'Faltan 50 días para Navidad.', 'face_scene': 'SANTA', 'offered_more': False})


if __name__ == '__main__':
    unittest.main()
