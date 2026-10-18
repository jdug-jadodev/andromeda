"""Explicit voice entry and bounded lexical lookup, never an LLM/tool executor.

Catalog entries contain only already validated, unexpired user turns. Matching a
topic proves a textual candidate exists, not that the model remembers it.
"""
import re
from doja_intents import normalized
from conversation_flow import ConversationFlow


class ConversationEntry:
    STOP = set('a al algo alguna algun andromeda anterior anteriores antes aquella aquel asunto ayer charla como con conversacion cosa de del el en esa ese eso esta este esto fue hablabamos hablamos hay la las lo los me mi mis nos nuestra nuestro o pasada pasado por que recuerdas recordar se sobre su sus tal tema te tengo tuvimos ultima ultimo un una unos unas y yo'.split())

    def __init__(self, catalog, new_id, clock):
        self.catalog, self.new_id, self.clock = catalog, new_id, clock
        self.stage, self.candidate = 'entry', None

    def prune(self):
        now = self.clock()
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
            if plain in {'no', 'no gracias', 'no, gracias'}:
                return {'kind': 'close'}
            if plain == 'si':
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
            for identity, memory in self.catalog.items():
                evidence = set().union(*(self.terms(g[0]['content']) for g in memory.pairs))
                if terms <= evidence: matches.append((memory.last_human, identity))
            if matches:
                self.candidate = max(matches)[1]; self.stage = 'confirm_resume'
                message = ('Encontré una charla sobre ese tema. ¿Quieres retomarla?' if len(matches) == 1 else
                           'Encontré varias charlas sobre ese tema. ¿Quieres retomar la más reciente?')
                return {'kind': 'notice', 'text': message, 'matches': len(matches)}
            self.stage = 'confirm_new'
            return {'kind': 'notice', 'text': 'No encuentro esa conversación entre las charlas disponibles. El contexto caduca tras una hora sin actividad. ¿Quieres empezar una nueva?'}
        return self.new()

    def new(self):
        if len(self.catalog) >= 4:
            self.stage = 'entry'
            return {'kind': 'notice', 'text': 'Hay cuatro charlas guardadas. Silencia y olvida una desde Contexto antes de empezar otra.'}
        self.stage = 'active'
        return {'kind': 'new', 'id': self.new_id}
