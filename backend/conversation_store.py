"""Bounded, local SQLite persistence for temporary conversation context.

This module is storage-only: it has no socket, model, Android or audio access.
Runtime/protocol integration is deliberately separate so this store can be
tested with disposable databases before any phone data is touched.
"""
from datetime import datetime, timedelta, timezone
from contextlib import contextmanager
import os
from pathlib import Path
import re
import sqlite3


DEFAULT_PATH = Path('/var/lib/andromeda-conversation/memory.db')
TIMEZONE = timezone(timedelta(hours=-5), 'America/Bogota')
INACTIVITY_MS = 60 * 60 * 1000
MAX_CONVERSATIONS = 4
MAX_TURNS = 24
MAX_TEXT_UNITS = 5700
_ID = re.compile(r'^[0-9a-f]{32}$')


def _units(text):
    return len(text.encode('utf-16-le')) // 2


def _day_key(now_ms):
    return datetime.fromtimestamp(now_ms / 1000, TIMEZONE).date().isoformat()


class ConversationStore:
    """Persist only bounded text turns; never audio or generated summaries."""

    def __init__(self, path=DEFAULT_PATH, clock=None):
        self.path = Path(path)
        self.clock = clock or (lambda: __import__('time').time_ns() // 1_000_000)
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.path.parent, 0o700)
        self._initialize()

    def _connect(self):
        db = sqlite3.connect(str(self.path), timeout=2.0)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA secure_delete=ON')
        db.execute('PRAGMA journal_mode=DELETE')
        db.execute('PRAGMA synchronous=FULL')
        db.execute('PRAGMA foreign_keys=ON')
        return db

    @contextmanager
    def _session(self):
        db = self._connect()
        try:
            with db:
                yield db
        finally:
            db.close()

    def _initialize(self):
        with self._session() as db:
            version = db.execute('PRAGMA user_version').fetchone()[0]
            if version not in (0, 1):
                raise RuntimeError('Unsupported conversation database version')
            if version == 0:
                db.executescript('''
                    CREATE TABLE conversations (
                        id TEXT PRIMARY KEY NOT NULL CHECK(length(id)=32),
                        created_ms INTEGER NOT NULL CHECK(created_ms>0),
                        last_human_ms INTEGER NOT NULL CHECK(last_human_ms>=0),
                        day_key TEXT NOT NULL CHECK(length(day_key)=10),
                        closed INTEGER NOT NULL DEFAULT 0 CHECK(closed IN (0,1))
                    );
                    CREATE TABLE turns (
                        conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                        ordinal INTEGER NOT NULL CHECK(ordinal>=0),
                        id TEXT NOT NULL CHECK(length(id)=32),
                        user_text TEXT NOT NULL CHECK(length(user_text)>0),
                        assistant_text TEXT,
                        web_answer TEXT,
                        web_followup INTEGER NOT NULL DEFAULT 0 CHECK(web_followup IN (0,1)),
                        human_ms INTEGER NOT NULL CHECK(human_ms>0),
                        PRIMARY KEY(conversation_id, ordinal),
                        UNIQUE(conversation_id, id),
                        CHECK(assistant_text IS NULL OR web_answer IS NULL)
                    );
                    CREATE INDEX turns_by_id ON turns(conversation_id,id);
                    CREATE INDEX conversations_expiry ON conversations(day_key,last_human_ms);
                    PRAGMA user_version=1;
                ''')
        os.chmod(self.path, 0o600)

    def _purge_locked(self, db, now_ms):
        # Only one hour without a human turn expires a chat. There is no
        # midnight boundary: a conversation that is still in use must survive
        # the day change instead of disappearing under the speaker.
        db.execute('''DELETE FROM conversations
            WHERE (last_human_ms>0 AND (?<last_human_ms OR ?-last_human_ms>=?))
            OR (last_human_ms=0 AND (?<created_ms OR ?-created_ms>=?))''',
            (now_ms, now_ms, INACTIVITY_MS, now_ms, now_ms, INACTIVITY_MS))

    def _evict_locked(self, db):
        """Drop the least recently used chat instead of blocking a turn."""
        oldest = db.execute('''SELECT id FROM conversations
            ORDER BY CASE WHEN last_human_ms>0 THEN last_human_ms ELSE created_ms END ASC,id ASC LIMIT 1''').fetchone()
        if oldest is not None:
            db.execute('DELETE FROM conversations WHERE id=?', (oldest['id'],))

    def purge(self, now_ms=None):
        now = self.clock() if now_ms is None else now_ms
        with self._session() as db:
            db.execute('BEGIN IMMEDIATE')
            self._purge_locked(db, now)
            db.commit()
            db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
            db.execute('VACUUM')

    def create(self, identity, now_ms=None):
        now = self.clock() if now_ms is None else now_ms
        if not isinstance(identity, str) or not _ID.fullmatch(identity):
            raise ValueError('Invalid conversation identity')
        with self._session() as db:
            db.execute('BEGIN IMMEDIATE')
            self._purge_locked(db, now)
            exists = db.execute('SELECT 1 FROM conversations WHERE id=?', (identity,)).fetchone()
            if exists:
                db.rollback()
                return False
            if db.execute('SELECT COUNT(*) FROM conversations').fetchone()[0] >= MAX_CONVERSATIONS:
                self._evict_locked(db)
            db.execute('INSERT INTO conversations(id,created_ms,last_human_ms,day_key) VALUES(?,?,0,?)',
                       (identity, now, _day_key(now)))
            db.commit()
        return True

    def list_metadata(self, now_ms=None):
        now = self.clock() if now_ms is None else now_ms
        with self._session() as db:
            db.execute('BEGIN IMMEDIATE')
            self._purge_locked(db, now)
            rows = db.execute('''SELECT c.id,c.created_ms,c.last_human_ms,c.closed,COUNT(t.id) AS turns
                FROM conversations c LEFT JOIN turns t ON t.conversation_id=c.id
                GROUP BY c.id ORDER BY CASE WHEN c.last_human_ms>0 THEN c.last_human_ms ELSE c.created_ms END DESC,c.id''').fetchall()
            db.commit()
        return [dict(row) for row in rows]

    def load(self, identity, now_ms=None):
        now = self.clock() if now_ms is None else now_ms
        with self._session() as db:
            db.execute('BEGIN IMMEDIATE')
            self._purge_locked(db, now)
            chat = db.execute('SELECT id,last_human_ms FROM conversations WHERE id=?', (identity,)).fetchone()
            if chat is None:
                db.commit()
                return None
            rows = db.execute('''SELECT id,user_text,assistant_text,web_answer,web_followup,human_ms
                FROM turns WHERE conversation_id=? ORDER BY ordinal''', (identity,)).fetchall()
            db.commit()
        turns = []
        for row in rows:
            turn = {'id': row['id'], 'user': row['user_text'], 'human_ms': row['human_ms']}
            if row['assistant_text'] is not None:
                turn['web_followup' if row['web_followup'] else 'assistant'] = row['assistant_text']
            if row['web_answer'] is not None:
                turn['web_answer'] = row['web_answer']
            turns.append(turn)
        return {'version': 1, 'id': chat['id'], 'last_human_ms': chat['last_human_ms'], 'turns': turns}

    def import_snapshot(self, snapshot, now_ms=None):
        """One-time, bounded migration hook for an authenticated legacy client."""
        now = self.clock() if now_ms is None else now_ms
        if not isinstance(snapshot, dict) or set(snapshot) != {'version', 'id', 'last_human_ms', 'turns'}:
            raise ValueError('Invalid conversation snapshot')
        identity, last, turns = snapshot['id'], snapshot['last_human_ms'], snapshot['turns']
        if (snapshot['version'] != 1 or not isinstance(identity, str) or not _ID.fullmatch(identity)
                or type(last) is not int or not isinstance(turns, list) or len(turns) > MAX_TURNS):
            raise ValueError('Invalid conversation snapshot')
        total, previous, identifiers = 0, 0, set()
        for item in turns:
            if not isinstance(item, dict) or not {'id', 'user', 'human_ms'} <= set(item) <= {
                    'id', 'user', 'human_ms', 'assistant', 'web_answer', 'web_followup'}:
                raise ValueError('Invalid conversation snapshot')
            if sum(key in item for key in ('assistant', 'web_answer', 'web_followup')) > 1:
                raise ValueError('Invalid conversation snapshot')
            turn_id, user, at = item['id'], item['user'], item['human_ms']
            answer = item.get('assistant', item.get('web_followup'))
            web = item.get('web_answer')
            if (not isinstance(turn_id, str) or not _ID.fullmatch(turn_id) or turn_id in identifiers
                    or not isinstance(user, str) or not user.strip() or _units(user) > 250 or '<|' in user
                    or type(at) is not int or at <= previous or at > last
                    or answer is not None and (not isinstance(answer, str) or not answer.strip() or _units(answer) > 700 or '<|' in answer)
                    or web is not None and (not isinstance(web, str) or not web.strip() or _units(web) > 700 or '<|' in web)):
                raise ValueError('Invalid conversation snapshot')
            identifiers.add(turn_id); previous = at
            total += _units(user) + _units(answer or '') + _units(web or '')
        if bool(turns) != bool(last) or turns and previous != last or total > MAX_TEXT_UNITS:
            raise ValueError('Invalid conversation snapshot')
        if last and (last > now + 100 or now - last >= INACTIVITY_MS):
            raise ValueError('Expired conversation snapshot')
        with self._session() as db:
            db.execute('BEGIN IMMEDIATE')
            self._purge_locked(db, now)
            existing = db.execute('SELECT 1 FROM conversations WHERE id=?', (identity,)).fetchone()
            if not existing:
                if db.execute('SELECT COUNT(*) FROM conversations').fetchone()[0] >= MAX_CONVERSATIONS:
                    self._evict_locked(db)
                db.execute('INSERT INTO conversations(id,created_ms,last_human_ms,day_key) VALUES(?,?,?,?)',
                           (identity, now, last, _day_key(now)))
            else:
                db.rollback()
                # SQLite is authoritative after the first migration; never allow
                # a stale app snapshot to overwrite server-side turns.
                return False
            db.executemany('''INSERT INTO turns(conversation_id,ordinal,id,user_text,assistant_text,web_answer,web_followup,human_ms)
                VALUES(?,?,?,?,?,?,?,?)''', ((identity, index, item['id'], item['user'],
                item.get('assistant', item.get('web_followup')), item.get('web_answer'),
                int('web_followup' in item), item['human_ms']) for index, item in enumerate(turns)))
            db.commit()
        return True

    def record_human(self, identity, turn_id, text, now_ms=None):
        now = self.clock() if now_ms is None else now_ms
        if (not isinstance(identity, str) or not _ID.fullmatch(identity)
                or not isinstance(turn_id, str) or not _ID.fullmatch(turn_id)
                or not isinstance(text, str) or not text.strip() or _units(text) > 250
                or '<|' in text):
            raise ValueError('Invalid conversation turn')
        with self._session() as db:
            db.execute('BEGIN IMMEDIATE')
            self._purge_locked(db, now)
            chat = db.execute('SELECT last_human_ms FROM conversations WHERE id=?', (identity,)).fetchone()
            if chat is None:
                # The 1 h bound (or a process restart) can remove the row
                # between the handshake and the utterance. Adopt the identity
                # and keep talking: memory is an aid, never a gate.
                if db.execute('SELECT COUNT(*) FROM conversations').fetchone()[0] >= MAX_CONVERSATIONS:
                    self._evict_locked(db)
                db.execute('INSERT INTO conversations(id,created_ms,last_human_ms,day_key) VALUES(?,?,0,?)',
                           (identity, now, _day_key(now)))
                chat = {'last_human_ms': 0}
            if chat['last_human_ms'] > now:
                db.rollback()
                return False
            ordinal = db.execute('SELECT COALESCE(MAX(ordinal),-1)+1 FROM turns WHERE conversation_id=?',
                                 (identity,)).fetchone()[0]
            if ordinal and db.execute('SELECT MAX(human_ms) FROM turns WHERE conversation_id=?',
                                      (identity,)).fetchone()[0] > now:
                db.rollback()
                return False
            try:
                db.execute('INSERT INTO turns(conversation_id,ordinal,id,user_text,human_ms) VALUES(?,?,?,?,?)',
                           (identity, ordinal, turn_id, text, now))
            except sqlite3.IntegrityError:
                db.rollback()
                return False
            db.execute('UPDATE conversations SET last_human_ms=?,day_key=?,closed=0 WHERE id=?',
                       (now, _day_key(now), identity))
            self._trim_locked(db, identity)
            db.commit()
        return True

    def record_answer(self, identity, turn_id, text, *, web_answer=False, web_followup=False):
        if (not isinstance(identity, str) or not _ID.fullmatch(identity)
                or not isinstance(turn_id, str) or not _ID.fullmatch(turn_id)
                or not isinstance(text, str) or not text.strip() or _units(text) > 700
                or '<|' in text or web_answer and web_followup):
            raise ValueError('Invalid conversation answer')
        field = 'web_answer' if web_answer else 'assistant_text'
        with self._session() as db:
            db.execute('BEGIN IMMEDIATE')
            self._purge_locked(db, self.clock())
            column = 'web_followup' if web_followup else '0'
            cursor = db.execute(f'''UPDATE turns SET {field}=?,web_followup={column}
                WHERE conversation_id=? AND id=? AND assistant_text IS NULL AND web_answer IS NULL''',
                (text, identity, turn_id))
            if cursor.rowcount != 1:
                db.rollback()
                return False
            self._trim_locked(db, identity)
            db.commit()
        return True

    def close(self, identity):
        with self._session() as db:
            return db.execute('UPDATE conversations SET closed=1 WHERE id=?', (identity,)).rowcount == 1

    def delete(self, identity):
        with self._session() as db:
            db.execute('PRAGMA secure_delete=ON')
            db.execute('BEGIN IMMEDIATE')
            removed = db.execute('DELETE FROM conversations WHERE id=?', (identity,)).rowcount == 1
            db.commit()
            if removed: db.execute('VACUUM')
            return removed

    def _trim_locked(self, db, identity):
        rows = db.execute('SELECT ordinal,id,user_text,assistant_text,web_answer,web_followup,human_ms FROM turns WHERE conversation_id=? ORDER BY ordinal',
                          (identity,)).fetchall()
        units = sum(_units(row['user_text']) + _units(row['assistant_text'] or '') + _units(row['web_answer'] or '')
                    for row in rows)
        remove = []
        while len(rows) - len(remove) > MAX_TURNS or units > MAX_TEXT_UNITS:
            row = rows[len(remove)]
            remove.append(row['ordinal'])
            units -= _units(row['user_text']) + _units(row['assistant_text'] or '') + _units(row['web_answer'] or '')
        if remove:
            removed = set(remove)
            kept = [row for row in rows if row['ordinal'] not in removed]
            db.execute('DELETE FROM turns WHERE conversation_id=?', (identity,))
            db.executemany('''INSERT INTO turns(conversation_id,ordinal,id,user_text,assistant_text,web_answer,web_followup,human_ms)
                VALUES(?,?,?,?,?,?,?,?)''',
                ((identity, index, row['id'], row['user_text'], row['assistant_text'], row['web_answer'],
                  row['web_followup'], row['human_ms']) for index, row in enumerate(kept)))
