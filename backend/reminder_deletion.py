"""Native-selected deletion dialogue; agenda contents and spoken targets stay in Android.

Authenticated read-only steps can ask/choose, never authorize a mutation. A fresh
affirmative after the exact Android question completes is required for one grant.
"""
import re
import uuid

from agenda_dialogue import Human
from agenda_requests import clean
from doja_intents import normalized

POLICY = 'confirmed_reminder_delete_v1'
QUESTION = 'Voy a preguntarte qué recordatorio quieres eliminar.'
CANCELLED = 'No he eliminado ningún recordatorio.'
NOT_FOUND = 'No encontré un recordatorio con ese contenido. No he eliminado ninguno.'
UNAVAILABLE = 'No pude comprobar los recordatorios. No he eliminado ninguno.'
UNKNOWN = 'No pude confirmar la eliminación. Revisa Recordatorios antes de pedirla otra vez.'
YES = {'si', 'si por favor', 'si gracias', 'si correcto', 'si elimina ese recordatorio',
       'elimina ese recordatorio', 'confirmo', 'confirmo eliminar ese recordatorio',
       'correcto', 'de acuerdo', 'adelante', 'hazlo', 'si hazlo', 'si adelante'}
MAX_AGE_MS = 300000


def requested(human):
    text = normalized(clean(human))
    if (not re.search(r'\brecordatorio\b', text)
            or not re.search(r'\b(?:elimina|eliminar|elimines|borra|borrar|borres|quita|quitar|quites|cancela|cancelar)\b', text)
            or re.search(r'[`;]|\b(?:no|nunca|jamas|ni|si|ayer|dije|dijiste|dijo|ejemplo|imagina|supongamos|'
                         r'todos|todas|recordatorios|alarma|alarmas|temporizador|volumen|busca|internet|web|enciende|apaga)\b', text)):
        raise ValueError('explicit_single_reminder_deletion_required')
    return True


def affirmative(text):
    return re.sub(r'\s*,\s*', ' ', normalized(clean(text))) in YES


def result_text(status):
    return {'REMOVED': 'Recordatorio eliminado y su aviso cancelado.', 'REJECTED': CANCELLED,
            'STALE': 'El recordatorio cambió o ya no está. No he confirmado su eliminación.', 'UNKNOWN': UNKNOWN}[status]


def identity(value, event=False):
    return isinstance(value, str) and re.fullmatch(
        r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' if event else r'[0-9a-f]{32}', value) is not None


class ReminderDeletion:
    def __init__(self, clock, maximum_sequence=64):
        self.clock = clock; self.maximum_sequence = maximum_sequence; self.clear()

    def clear(self):
        self.origin = self.question_turn = self.stage = ''
        self.created = self.last_seq = self.answers = self.heard_at = 0
        self.armed = False; self.pending = self.selected = self.grant = None
        self.scope = None

    @property
    def active(self): return bool(self.origin) and self.grant is None

    def step(self, human, session_end):
        now = self.clock(); before = min(now+4000, human.speech_end+56500, session_end-5000)
        if before-now < 500 or not now < session_end <= now+300000:
            raise ValueError('stale_reminder_selection')
        self.pending = dict(type='reminder_delete_step', nonce=uuid.uuid4().hex,
            origin_turn_id=self.origin, request_turn_id=human.id, request_sequence=human.seq,
            speech_end_ms=human.speech_end, select_before_ms=before)
        self.armed = False
        return dict(event=dict(self.pending), executing=True)

    def start(self, turn, session, context, session_end):
        if self.active or self.pending or self.grant: raise ValueError('deletion_already_pending')
        human = Human.from_turn(turn, self.clock(), maximum_age=60000, maximum_sequence=self.maximum_sequence)
        requested(human.text)
        if not identity(session) or not identity(context): raise ValueError('invalid_deletion_scope')
        self.origin = human.id; self.created = self.clock(); self.last_seq = human.seq
        self.scope = (session, context)
        return self.step(human, session_end)

    def spoken(self, turn, completed):
        if not self.active or turn != self.question_turn: return
        if completed: self.armed = True; self.heard_at = self.clock()
        else: self.clear()

    def human(self, turn, session, context, session_end):
        if not self.active: return None
        human = Human.from_turn(turn, self.clock(), maximum_sequence=self.maximum_sequence)
        if (self.pending or not self.armed or (session,context) != self.scope or human.seq <= self.last_seq
                or human.id == self.question_turn or human.speech_end < self.heard_at
                or self.clock()-self.created >= MAX_AGE_MS):
            self.clear(); raise ValueError('unheard_or_stale_reminder_question')
        self.last_seq = human.seq; self.answers += 1
        if self.answers > 8:
            self.clear(); return dict(text=CANCELLED,timer_control='reminder_delete_cancelled',offered_more=True)
        if self.stage != 'CONFIRM' or not affirmative(human.text): return self.step(human, session_end)
        now = self.clock(); before = min(now+4000,human.speech_end+26500,session_end-5000)
        if before-now < 500 or not now < session_end <= now+300000: raise ValueError('stale_deletion_confirmation')
        self.grant = dict(type='reminder_delete_execute',operation_id=str(uuid.uuid4()),nonce=uuid.uuid4().hex,
            origin_turn_id=self.origin,request_turn_id=human.id,request_sequence=human.seq,
            speech_end_ms=human.speech_end,execute_before_ms=before,**self.selected)
        self.armed = False
        return dict(event=dict(self.grant),executing=True)

    def selection(self, packet, session, context):
        grant = self.pending
        if grant is None: return None
        expected = dict(session_id=session,context_id=context,**{k:grant[k] for k in ('nonce','request_turn_id','request_sequence')})
        if (self.scope != (session,context) or self.clock() >= grant['select_before_ms']
                or any(type(packet.get(k)) is not type(v) or packet[k] != v for k,v in expected.items())):
            raise ValueError('unrelated_reminder_selection')
        status = packet.get('status')
        base = set(expected) | {'action','status'}
        if packet.get('action') != 'reminder_delete_selection': raise ValueError('invalid_selection_action')
        if status in {'CANCELLED','NOT_FOUND','UNAVAILABLE'}:
            if set(packet) != base: raise ValueError('extra_selection_data')
            self.clear()
            return dict(text={'CANCELLED':CANCELLED,'NOT_FOUND':NOT_FOUND,'UNAVAILABLE':UNAVAILABLE}[status],
                timer_control='reminder_delete_selection',offered_more=True,
                reminder_delete=dict(nonce=grant['nonce'],status=status))
        stage = packet.get('stage')
        if status != 'QUESTION' or stage not in {'TARGET','CHOOSE','CONFIRM'}:
            raise ValueError('invalid_reminder_question')
        fields = {'event_id','revision','due_ms'} if stage == 'CONFIRM' else set()
        if set(packet) != base | {'stage'} | fields: raise ValueError('unexpected_selection_fields')
        selected = None
        if fields:
            if (not identity(packet['event_id'],True) or type(packet['revision']) is not int
                    or not 0 < packet['revision'] < 2**63-2 or type(packet['due_ms']) is not int or packet['due_ms'] <= 0):
                raise ValueError('invalid_selected_reminder')
            selected = {k:packet[k] for k in fields}
        self.pending = None; self.stage = stage; self.selected = selected
        self.question_turn = grant['request_turn_id']; self.armed = False
        return dict(text=QUESTION,timer_control='reminder_delete_question',offered_more=False,
            reminder_delete=dict(nonce=grant['nonce'],status='QUESTION',stage=stage))

    def result(self, packet, session, context):
        grant = self.grant
        if grant is None: return None
        expected = dict(session_id=session,context_id=context,**{k:grant[k] for k in
            ('operation_id','nonce','event_id','revision','request_turn_id','request_sequence')})
        if (self.scope != (session,context) or set(packet) != set(expected)|{'action','status'}
                or packet.get('action') != 'reminder_delete_result'
                or any(type(packet.get(k)) is not type(v) or packet[k] != v for k,v in expected.items())
                or packet.get('status') not in {'REMOVED','STALE','REJECTED','UNKNOWN'}):
            raise ValueError('unrelated_reminder_delete_receipt')
        self.clear()
        return dict(text=result_text(packet['status']),timer_control='reminder_delete_result',
            actions_executed=int(packet['status']=='REMOVED'),offered_more=True)

    def timed_out(self):
        if self.pending and self.clock() >= self.pending['select_before_ms']:
            pending = self.pending; self.clear()
            return dict(text=UNAVAILABLE,timer_control='reminder_delete_selection',offered_more=True,
                reminder_delete=dict(nonce=pending['nonce'],status='UNAVAILABLE'))
        if self.grant and self.clock() >= self.grant['execute_before_ms']+500:
            self.clear(); return dict(text=UNKNOWN,timer_control='reminder_delete_result',actions_executed=0,offered_more=True)
        return None
