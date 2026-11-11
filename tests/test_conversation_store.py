import sqlite3
import sys
import tempfile
import unittest
from contextlib import closing
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))

from conversation_store import ConversationStore, INACTIVITY_MS, MAX_TEXT_UNITS


def epoch_ms(year, month, day, hour=12, minute=0):
    return int(datetime(year, month, day, hour, minute,
                        tzinfo=timezone(timedelta(hours=-5), 'America/Bogota')).timestamp() * 1000)


class ConversationStoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / 'memory.db'
        self.now = [epoch_ms(2026, 11, 10)]
        self.store = ConversationStore(self.path, lambda: self.now[0])
        self.chat = 'a' * 32
        self.turn = 'b' * 32
        self.assertTrue(self.store.create(self.chat))

    def tearDown(self):
        self.tmp.cleanup()

    def test_round_trip_persists_only_text_and_web_fields(self):
        self.assertTrue(self.store.record_human(self.chat, self.turn, '¿Qué dije?', self.now[0]))
        self.assertTrue(self.store.record_answer(self.chat, self.turn, 'Preguntaste por la luz.'))
        saved = self.store.load(self.chat)
        self.assertEqual(saved['id'], self.chat)
        self.assertEqual(saved['turns'], [{
            'id': self.turn, 'user': '¿Qué dije?', 'human_ms': self.now[0],
            'assistant': 'Preguntaste por la luz.'
        }])
        with closing(sqlite3.connect(self.path)) as db:
            tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            self.assertEqual(tables, {'conversations', 'turns'})
            columns = {row[1] for row in db.execute('PRAGMA table_info(turns)')}
            self.assertNotIn('audio', columns)
            self.assertNotIn('transcript_audio', columns)

    def test_rejects_duplicate_turn_and_invalid_text(self):
        self.assertTrue(self.store.record_human(self.chat, self.turn, 'hola', self.now[0]))
        self.assertFalse(self.store.record_human(self.chat, self.turn, 'duplicado', self.now[0] + 1))
        with self.assertRaises(ValueError):
            self.store.record_human(self.chat, 'c' * 32, 'x' * 251, self.now[0] + 2)
        with self.assertRaises(ValueError):
            self.store.record_human(self.chat, 'c' * 32, 'tool <|injected|>', self.now[0] + 2)

    def test_human_turn_adopts_an_expired_identity(self):
        self.store.record_human(self.chat, self.turn, 'hola', self.now[0])
        self.now[0] += INACTIVITY_MS
        self.assertIsNone(self.store.load(self.chat))
        next_turn = 'c' * 32
        self.assertTrue(self.store.record_human(self.chat, next_turn, 'sigo aquí', self.now[0]))
        self.assertEqual([t['user'] for t in self.store.load(self.chat)['turns']], ['sigo aquí'])

    def test_catalog_evicts_the_oldest_chat_instead_of_blocking(self):
        for value in 'def':
            self.assertTrue(self.store.create(value * 32, self.now[0]))
        # A fifth distinct chat replaces the least recently used one.
        fifth = '9' * 32
        self.assertTrue(self.store.create(fifth, self.now[0]))
        self.assertIsNone(self.store.load(self.chat, self.now[0]))
        self.assertIsNotNone(self.store.load(fifth, self.now[0]))
        self.assertLessEqual(len(self.store.list_metadata(self.now[0])), 4)

        for i in range(24):
            turn_id = f'{i + 1:032x}'
            at = self.now[0] + i + 1
            self.now[0] = at
            self.assertTrue(self.store.record_human(self.chat, turn_id, 'u' * 250, at))
            self.assertTrue(self.store.record_answer(self.chat, turn_id, 'a' * 700))
        saved = self.store.load(self.chat, self.now[0] + 24)
        self.assertLessEqual(len(saved['turns']), 24)
        units = sum(len(t['user'].encode('utf-16-le')) // 2
                    + len(t.get('assistant', '').encode('utf-16-le')) // 2
                    for t in saved['turns'])
        self.assertLessEqual(units, MAX_TEXT_UNITS)
        self.assertEqual(saved['turns'][-1]['id'], f'{24:032x}')

    def test_one_hour_inactivity_expires_on_exact_boundary(self):
        self.store.record_human(self.chat, self.turn, 'hola', self.now[0])
        self.now[0] += INACTIVITY_MS - 1
        self.assertIsNotNone(self.store.load(self.chat))
        self.now[0] += 1
        self.assertIsNone(self.store.load(self.chat))

    def test_midnight_boundary_keeps_a_live_chat(self):
        before = epoch_ms(2026, 11, 10, 23, 59)
        self.now[0] = before
        live = 'd' * 32
        self.assertTrue(self.store.create(live, before))
        new_day = epoch_ms(2026, 11, 11, 0, 0)
        self.assertIsNotNone(self.store.load(live, new_day))
        self.assertIsNone(self.store.load(live, before + INACTIVITY_MS))

    def test_forget_removes_turn_rows(self):
        self.store.record_human(self.chat, self.turn, 'dato privado', self.now[0])
        self.assertTrue(self.store.delete(self.chat))
        self.assertIsNone(self.store.load(self.chat))
        with closing(sqlite3.connect(self.path)) as db:
            self.assertEqual(db.execute('SELECT COUNT(*) FROM turns').fetchone()[0], 0)

    def test_restart_restores_text_but_not_expired_chat(self):
        self.store.record_human(self.chat, self.turn, 'quedamos en hablar de la luz', self.now[0])
        self.store.record_answer(self.chat, self.turn, 'La luz del salón.',)
        reopened = ConversationStore(self.path, lambda: self.now[0] + 500)
        self.assertEqual(reopened.load(self.chat)['turns'][0]['assistant'], 'La luz del salón.')
        self.assertIsNone(reopened.load(self.chat, self.now[0] + INACTIVITY_MS))

    def test_authenticated_snapshot_migrates_once_and_cannot_overwrite(self):
        migrating = 'c' * 32
        snapshot = {'version': 1, 'id': migrating, 'last_human_ms': self.now[0], 'turns': [{
            'id': self.turn, 'user': 'pregunta inicial', 'assistant': 'respuesta inicial', 'human_ms': self.now[0]
        }]}
        self.assertTrue(self.store.import_snapshot(snapshot))
        changed = dict(snapshot, turns=[dict(snapshot['turns'][0], user='texto obsoleto')])
        self.assertFalse(self.store.import_snapshot(changed))
        self.assertEqual(self.store.load(migrating)['turns'][0]['user'], 'pregunta inicial')

    def test_database_file_is_private(self):
        if hasattr(self.path.stat(), 'st_mode') and os_name_posix():
            self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)


def os_name_posix():
    import os
    return os.name == 'posix'


if __name__ == '__main__':
    unittest.main()
