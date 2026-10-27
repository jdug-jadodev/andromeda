import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]; sys.path.insert(0, str(ROOT / 'backend'))
import local_smalltalk as smalltalk


class LocalSmalltalkTests(unittest.TestCase):
    def test_compliments_reply_locally(self):
        expected = 'Gracias, qué lindo de tu parte.'
        for text in ('eres muy bonita', 'eres bonita', 'eres linda', 'eres hermosa',
                     'eres preciosa', 'eres inteligente', 'eres genial', 'eres increíble',
                     'eres un sol', 'eres la mejor', 'me caes bien', 'te admiro',
                     'qué bonita eres', 'qué amable eres'):
            self.assertEqual(smalltalk.answer(text), expected, text)

    def test_affection_replies_warmly(self):
        expected = 'Yo también te tengo mucho cariño.'
        for text in ('te quiero', 'te amo', 'me gustas'):
            self.assertEqual(smalltalk.answer(text), expected, text)

    def test_achievement_replies_with_congratulations(self):
        expected = '¡Felicidades! Me alegra muchísimo por ti, te lo mereces.'
        for text in ('me gané una medalla', 'gané una medalla', 'gané el premio',
                     'aprobé el examen', 'me ascendieron', 'me gradué', 'me casé',
                     'lo logré', 'lo conseguí'):
            self.assertEqual(smalltalk.answer(text), expected, text)

    def test_birthday_replies_with_congratulations(self):
        expected = '¡Feliz cumpleaños! Que la pases increíble.'
        for text in ('estoy de cumpleaños', 'es mi cumpleaños', 'hoy cumplo años',
                     'es mi cumple'):
            self.assertEqual(smalltalk.answer(text), expected, text)

    def test_joy_replies_warmly(self):
        expected = '¡Qué alegría! Me encanta escuchar eso.'
        for text in ('estoy feliz', 'estoy muy contenta', 'me siento genial',
                     'tengo una buena noticia'):
            self.assertEqual(smalltalk.answer(text), expected, text)

    def test_greetings_reply_locally(self):
        expected = '¡Hola! ¿En qué puedo ayudarte?'
        for text in ('hola', 'buenos días', 'buenas tardes', 'buenas noches',
                     'buenas', 'qué tal', 'cómo estás'):
            self.assertEqual(smalltalk.answer(text), expected, text)

    def test_negation_is_not_a_pleasantry(self):
        for text in ('no eres bonita', 'no te quiero', 'no me gustas', 'no eres la mejor',
                     'no gané', 'no es mi cumpleaños', 'no estoy feliz'):
            self.assertIsNone(smalltalk.answer(text), text)

    def test_third_person_is_not_addressed_to_assistant(self):
        for text in ('ella es bonita', 'mi mamá es linda', 'él es el mejor', 'él ganó'):
            self.assertIsNone(smalltalk.answer(text), text)

    def test_non_smalltalk_returns_none(self):
        for text in ('cuéntame un chiste', 'qué hora es', 'enciende las luces',
                     'recuérdame beber agua', 'gracias', 'hola, ¿qué hora es?'):
            self.assertIsNone(smalltalk.answer(text), text)


if __name__ == '__main__':
    unittest.main()
