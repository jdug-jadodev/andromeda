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

    def test_ux_offer_preserves_answer_and_avoids_duplicate_direct_question(self):
        self.assertEqual(ConversationFlow.with_offer('Una estrella emite luz.'), 'Una estrella emite luz. ¿Necesitas algo más?')
        for text in ['¿Necesitas algo más?', 'Bien. ¿Puedo ayudarte en algo más?']:
            self.assertEqual(ConversationFlow.with_offer(text), text)
        self.assertEqual(ConversationFlow.with_offer('Hola'), 'Hola. ¿Necesitas algo más?')

    def test_appended_offer_is_recognized_even_when_answer_contains_quotes(self):
        flow = ConversationFlow(); answer = flow.with_offer('Se llama «luz».')
        flow.spoken(answer, offered_more=True)
        self.assertEqual(flow.closing_reason('no'), 'declined_more')
        self.assertNotIn('di Andrómeda', flow.FAREWELL)

    def test_followup_never_truncates_a_long_answer_to_fit(self):
        with self.assertRaises(ValueError): ConversationFlow.with_offer('x' * 700)
        with self.assertRaises(ValueError): ConversationFlow.with_offer('🚀' * 350)


if __name__ == '__main__': unittest.main()
