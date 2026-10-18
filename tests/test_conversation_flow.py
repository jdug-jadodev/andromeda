from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from conversation_flow import ConversationFlow


class ConversationFlowTests(unittest.TestCase):
    def test_only_whole_explicit_commands_close_a_chat(self):
        flow = ConversationFlow()
        for text in ['Cierra la conversación.', 'gracias', '¡Muchas gracias!', 'gracias Andrómeda']:
            with self.subTest(text=text): self.assertEqual(flow.closing_reason(text), 'explicit_close')
        for text in ['no', 'sí', 'gracias pero tengo otra pregunta', 'gracias por lo de las luces',
                     'no cierres la conversación', 'no cierra la conversación', 'dije gracias',
                     '¿qué significa cierra la conversación?', 'la frase "cierra la conversación"',
                     '"cierra la conversación"', '«gracias»', 'si digo gracias seguimos', 'no enciendas las luces']:
            with self.subTest(text=text): self.assertIsNone(flow.closing_reason(text))

    def test_no_is_scoped_to_the_immediately_preceding_spoken_offer(self):
        flow = ConversationFlow(); flow.spoken('Todo listo para conversar. ¿Necesitas algo más?')
        self.assertEqual(flow.closing_reason('no'), 'declined_more')
        self.assertEqual(flow.closing_reason('No, gracias.'), 'declined_more')
        self.assertIsNone(flow.closing_reason('no, necesito saber algo más'))
        self.assertIsNone(flow.closing_reason('sí y quiero hablar de luces'))
        flow.spoken('¿La luz es azul?')
        self.assertIsNone(flow.closing_reason('no'))
        flow.spoken('¿Puedo ayudarte en algo más?'); flow.reset()
        self.assertIsNone(flow.closing_reason('no'))

    def test_quoted_or_reported_offer_is_not_a_direct_followup_offer(self):
        flow = ConversationFlow()
        for text in ['La frase es «¿Necesitas algo más?»', '¿No?',
                     'La pregunta es ¿Necesitas algo más?', 'La pregunta es: ¿Necesitas algo más?',
                     '¿Necesitas algo más? Seguimos con las luces.', 'No pregunté si necesitas algo más.']:
            flow.spoken(text)
            with self.subTest(text=text): self.assertIsNone(flow.closing_reason('no'))


if __name__ == '__main__': unittest.main()
