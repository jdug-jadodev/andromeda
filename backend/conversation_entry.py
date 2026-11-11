"""Explicit voice entry and bounded lexical lookup, never an LLM/tool executor.

Catalog entries contain only already validated, unexpired user turns. Matching a
topic proves a textual candidate exists, not that the model remembers it.
"""
import re
from doja_intents import normalized
from conversation_flow import ConversationFlow
from affirmation import is_negative, is_affirmative


class ConversationEntry:
    STOP = set('a al algo alguna algun andromeda anterior anteriores antes aquella aquel asunto ayer charla como con conversacion cosa de del el en esa ese eso esta este esto fue hablabamos hablamos hay la las lo los me mi mis nos nuestra nuestro o pasada pasado por que recuerdas recordar se sobre su sus tal tema te tengo tuvimos ultima ultimo un una unos unas y yo'.split())

    def __init__(self, catalog, new_id, clock, loader=None, catalog_loader=None):
        self.catalog, self.new_id, self.clock = catalog, new_id, clock
        self.loader, self.catalog_loader, self.selected = loader, catalog_loader, None
        self.stage, self.candidate = 'entry', None

    def memory(self, identity):
        if self.loader is None: return self.catalog.get(identity)
        if identity != self.candidate and identity != self.new_id: return None
        self.selected = self.loader(identity)
        return self.selected

    def prune(self):
        now = self.clock()
        if self.catalog_loader is not None:
            self.catalog = {identity: item for identity, item in self.catalog_loader().items()
                            if identity != self.new_id}
        elif self.loader is None:
            self.catalog = {k: v for k, v in self.catalog.items()
                            if not v.pairs or 0 <= now - v.last_human < 3600000}

    def cancel_pending(self):
        if self.stage != 'active': self.stage, self.candidate = 'entry', None

    @classmethod
    def terms(cls, text):
        # No embeddings/model, stemming, synonyms or inferred personal facts.
        return set(re.findall(r'\b[a-z0-9]+\b', normalized(text))) - cls.STOP

    def route(self, text):
        self.prune()
        plain = normalized(text).strip(' .,!¡?¿')
        if self.stage == 'active': return {'kind': 'model'}
        if plain in ConversationFlow.CLOSE: return {'kind': 'close', 'reason': 'explicit_close'}
        if self.stage == 'search_topic':
            plain = 'recuerdas ' + plain; self.stage = 'entry'
        if self.stage in {'confirm_resume', 'confirm_new'}:
            if is_negative(text):
                return {'kind': 'close'}
            if is_affirmative(text):
                if self.stage == 'confirm_new': return self.new()
                if self.candidate not in self.catalog:
                    self.stage = 'confirm_new'
                    return {'kind': 'notice', 'text': 'Esa charla ya no está disponible. ¿Quieres empezar una nueva?'}
                self.stage = 'active'
                return {'kind': 'resume', 'id': self.candidate}
            if not re.match(r'^recuerdas(?:\s|$)', plain):
                return {'kind': 'notice', 'text': 'Responde sí para confirmar o no para cerrar. También puedes pedir otro tema con recuerdas.'}
            # A fresh explicit lookup replaces the previous candidate.
            self.candidate = None; self.stage = 'entry'
        match = re.fullmatch(r'recuerdas(?:\s+(.*))?', plain)
        if match:
            terms = self.terms(match[1] or '')
            if not terms:
                self.stage = 'search_topic'
                return {'kind': 'notice', 'text': '¿Qué tema de la conversación quieres buscar?'}
            matches = []
            for identity, value in self.catalog.items():
                memory = self.loader(identity) if self.loader is not None else value
                if memory is None: continue
                evidence = set().union(*(self.terms(g[0]['content']) for g in memory.pairs))
                if terms <= evidence: matches.append((memory.last_human, identity))
            if matches:
                self.candidate = max(matches)[1]; self.stage = 'confirm_resume'
                message = ('Encontré una charla sobre ese tema. ¿Quieres retomarla?' if len(matches) == 1 else
                           'Encontré varias charlas sobre ese tema. ¿Quieres retomar la más reciente?')
                return {'kind': 'notice', 'text': message, 'matches': len(matches)}
            self.stage = 'confirm_new'
            return {'kind': 'notice', 'text': 'No encuentro esa conversación entre las charlas disponibles. El contexto caduca tras una hora sin actividad. ¿Quieres empezar una nueva?'}
        return self.continue_recent()

    def continue_recent(self):
        """A voice entry continues the latest live chat instead of opening one.

        Memory exists to keep the thread, not to force "recuerdas" before every
        sentence. The SQLite store still bounds the catalog and its 1 h TTL.
        """
        latest = []
        for identity, item in self.catalog.items():
            if isinstance(item, dict):
                turns, at = item.get('turns', 0), item.get('last_human_ms') or item.get('created_ms') or 0
            else:
                turns, at = len(item.pairs), item.last_human
            if turns > 0: latest.append((at, identity))
        if latest:
            self.candidate = max(latest)[1]
            self.stage = 'active'
            return {'kind': 'resume', 'id': self.candidate, 'automatic': True}
        return self.new()

    def new(self):
        self.stage = 'active'
        return {'kind': 'new', 'id': self.new_id}
