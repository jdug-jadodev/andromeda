from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
import conversation_voice as voice
from conversation_entry import ConversationEntry


class ConversationEntryTests(unittest.TestCase):
    def setUp(self):
        self.clock = [4000000]
        self.old = voice.ShortMemory(lambda: self.clock[0])
        self.old.last_human = self.clock[0] - 1000
        self.old.commit('Hablamos de las luces del patio.', 'Respuesta pública.')
        self.entry = ConversationEntry({'a' * 32: self.old}, 'b' * 32, lambda: self.clock[0])

    def test_default_is_new_even_if_previous_dialogue_exists(self):
        result = self.entry.route('qué es una estrella')
        self.assertEqual(result, {'kind': 'new', 'id': 'b' * 32})
        self.assertEqual(len(self.old.pairs), 1)

    def test_search_requires_explicit_recuerdas_at_entry_and_confirmation(self):
        result = self.entry.route('¿Recuerdas la conversación de las luces?')
        self.assertEqual(result['kind'], 'notice'); self.assertEqual(result['matches'], 1)
        self.assertEqual(self.entry.route('sí'), {'kind': 'resume', 'id': 'a' * 32})
        self.assertEqual(self.old.last_human, 3999000)

    def test_followup_does_not_start_a_new_chat_for_each_question(self):
        self.entry.route('hola')
        for text in ['otra pregunta', 'recuerdas lo que acabo de decir']:
            self.assertEqual(self.entry.route(text), {'kind': 'model'})

    def test_missing_topic_does_not_invent_existence(self):
        result = self.entry.route('recuerdas la conversación de las cámaras')
        self.assertEqual(self.entry.stage, 'confirm_new'); self.assertNotIn('matches', result)
        self.assertEqual(self.entry.route('no'), {'kind': 'close'})

    def test_expired_offer_cannot_restore_or_renew_old_chat(self):
        self.old.last_human = self.clock[0] - 3599990
        self.entry.route('recuerdas las luces'); self.clock[0] += 10
        self.assertEqual(self.entry.route('sí')['kind'], 'notice')
        self.assertEqual(self.entry.stage, 'confirm_new')

    def test_assistant_hallucinated_topic_is_not_search_evidence(self):
        self.old.pairs[0][1]['content'] = 'También hablamos de cámaras.'
        self.assertNotIn('matches', self.entry.route('recuerdas cámaras'))

    def test_quoted_or_negated_control_is_not_a_lookup(self):
        for text in ['dije recuerdas las luces', 'no recuerdas las luces', '"recuerdas luces"']:
            entry = ConversationEntry({'a' * 32: self.old}, 'b' * 32, lambda: self.clock[0])
            self.assertEqual(entry.route(text)['kind'], 'new')

    def test_incomplete_search_asks_for_topic_then_searches_that_topic(self):
        self.assertEqual(self.entry.route('recuerdas')['kind'], 'notice')
        self.assertEqual(self.entry.route('las luces')['matches'], 1)

    def test_multiple_matches_offer_most_recent_without_merging_chats(self):
        recent = voice.ShortMemory(lambda: self.clock[0]); recent.last_human = 3999500
        recent.commit('Las luces de la cocina.', 'Otra respuesta.')
        self.entry.catalog['c' * 32] = recent
        self.assertEqual(self.entry.route('recuerdas luces')['matches'], 2)
        self.assertEqual(self.entry.route('sí')['id'], 'c' * 32)
        self.assertEqual(len(self.old.pairs), 1)

    def test_failed_or_timed_out_offer_does_not_authorize_resume(self):
        self.entry.route('recuerdas luces'); self.entry.cancel_pending()
        self.assertEqual(self.entry.route('sí')['kind'], 'new')

    def test_unclear_confirmation_does_not_silently_start_another_chat(self):
        self.entry.route('recuerdas luces')
        self.assertEqual(self.entry.route('quizás')['kind'], 'notice')
        self.assertEqual(self.entry.stage, 'confirm_resume')
        self.assertEqual(self.entry.route('sí')['kind'], 'resume')

    def test_capacity_never_evicts_a_saved_chat(self):
        self.entry.catalog = {f'{n:032x}': self.old for n in range(4)}
        self.assertEqual(self.entry.route('hola')['kind'], 'notice')
        self.assertEqual(len(self.entry.catalog), 4)

    def test_catalog_schema_duplicate_and_size_are_validated(self):
        snapshot = {'version': 1, 'id': 'a' * 32, 'last_human_ms': 3999000,
                    'turns': [{'id': 'c' * 32, 'user': 'Luces.', 'human_ms': 3999000}]}
        with patch.object(voice, 'now_ms', return_value=self.clock[0]):
            self.assertEqual(len(voice.restore_catalog([snapshot])), 1)
            for value in [{}, [snapshot] * 2, [snapshot] * 5]:
                with self.assertRaises(ValueError): voice.restore_catalog(value)


if __name__ == '__main__': unittest.main()
