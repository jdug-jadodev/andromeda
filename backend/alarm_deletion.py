"""Read-only selection -> proved spoken question -> current-human deletion permit.

No storage/executor/history/model. A read receipt is NEVER a mutation grant.
"""
import uuid
import re
from agenda_dialogue import Human
from agenda_requests import clean
from alarm_schedule import deletion_target, label
from doja_intents import normalized

CANCELLED = 'No he eliminado ninguna alarma.'
UNKNOWN = 'No pude confirmar la eliminación. Revisa Alarmas antes de pedirla otra vez.'
SELECTION = {'NOT_FOUND':'No encontré esa alarma semanal.', 'AMBIGUOUS':'Hay más de una alarma con ese horario. Elige la serie en Alarmas.', 'UNKNOWN':'No pude comprobar las alarmas guardadas.'}


def question(target): return f'¿Confirmas eliminar toda la serie de la alarma {label(target["weekday"],target["minute_of_day"])}?'
def result_text(status):
    return {'REMOVED':'Alarma eliminada, incluida toda su repetición semanal.', 'REJECTED':CANCELLED,
            'STALE':'La alarma cambió. No la eliminé; revisa Alarmas.', 'UNKNOWN':UNKNOWN}[status]


class AlarmDeletion:
    def __init__(self, clock, maximum_sequence=24):
        self.clock=clock; self.maximum_sequence=maximum_sequence; self.clear()
    def clear(self): self.pending=self.selected=self.grant=None; self.armed=False
    @property
    def active(self): return self.pending is not None or self.selected is not None
    def start(self,result,turn,session,context):
        if self.active: raise ValueError('Alarm deletion already pending')
        human=Human.from_turn(turn,self.clock(),maximum_age=60000,maximum_sequence=self.maximum_sequence)
        target=deletion_target(human.text)
        if any(type(result.get(key)) is not int or result[key]!=value for key,value in target.items()): raise ValueError('Unrelated alarm target')
        if any(not isinstance(value,str) or len(value)!=32 or any(c not in '0123456789abcdef' for c in value) for value in (session,context)): raise ValueError('Invalid deletion scope')
        self.pending=dict(type='alarm_select',nonce=uuid.uuid4().hex,request_turn_id=human.id,request_sequence=human.seq,
            speech_end_ms=human.speech_end,select_before_ms=min(self.clock()+4000,human.speech_end+26500),**target)
        self.scope=(session,context)
        return dict(event=self.pending,executing=True)
    def selection(self,packet,session,context):
        expected=self.pending
        if expected is None: return None
        if (self.clock()>=expected['select_before_ms'] or (session,context)!=self.scope
                or any(type(packet.get(key)) is not type(expected[key]) or packet[key]!=expected[key] for key in ('nonce','request_turn_id','request_sequence'))
                or packet.get('session_id')!=session or packet.get('context_id')!=context): raise ValueError('Unrelated alarm selection')
        status=packet.get('status')
        if status in SELECTION:
            self.clear(); return dict(text=SELECTION[status],timer_control='alarm_selection',offered_more=True)
        if status!='FOUND': raise ValueError('Invalid selection receipt')
        event=packet.get('event_id'); revision=packet.get('revision'); due=packet.get('due_ms')
        if (not isinstance(event,str) or re.fullmatch('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',event) is None
                or type(revision) is not int or not 1<=revision<2**63-2 or type(due) is not int or due<=0
                or any(type(packet.get(key)) is not int or packet[key]!=expected[key] for key in ('weekday','minute_of_day'))): raise ValueError('Invalid selected alarm')
        self.selected=dict(expected,event_id=event,revision=revision,due_ms=due,expires=self.clock()+120000)
        self.pending=None; self.armed=False
        return dict(text=question(self.selected),timer_control='alarm_delete_question',offered_more=False)
    def spoken(self,turn,completed):
        if self.selected is not None and self.selected['request_turn_id']==turn:
            if completed: self.armed=True
            else: self.clear()
    def human(self,turn,session,context,session_end):
        if self.selected is None: return None
        human=Human.from_turn(turn,self.clock(),maximum_sequence=self.maximum_sequence)
        selected=self.selected
        if not self.armed or self.clock()>=selected['expires'] or human.seq<=selected['request_sequence'] or (session,context)!=self.scope:
            self.clear(); raise ValueError('Deletion question unheard/stale')
        plain=normalized(clean(human.text))
        if plain not in {'si','confirmo','si elimina esa alarma','elimina esa alarma','confirmo eliminar esa alarma'}:
            self.clear(); return dict(text=CANCELLED,timer_control='alarm_delete_cancelled',offered_more=True)
        now=self.clock(); before=min(now+4000,human.speech_end+26500,session_end-5000)
        if before-now<500 or session_end>now+300000: raise ValueError('Stale deletion confirmation')
        self.grant=dict(type='alarm_delete_execute',operation_id=str(uuid.uuid4()),nonce=uuid.uuid4().hex,
            event_id=selected['event_id'],revision=selected['revision'],due_ms=selected['due_ms'],weekday=selected['weekday'],minute_of_day=selected['minute_of_day'],
            origin_turn_id=selected['request_turn_id'],request_turn_id=human.id,request_sequence=human.seq,speech_end_ms=human.speech_end,execute_before_ms=before)
        self.selected=None; self.armed=False
        return dict(event=self.grant,executing=True)
    def result(self,packet,session,context):
        grant=self.grant
        if grant is None: return None
        if ((session,context)!=self.scope or packet.get('session_id')!=session or packet.get('context_id')!=context
                or any(type(packet.get(key)) is not type(grant[key]) or packet[key]!=grant[key] for key in ('operation_id','nonce','event_id','revision','request_turn_id','request_sequence'))
                or packet.get('status') not in {'REMOVED','STALE','REJECTED','UNKNOWN'}): raise ValueError('Unrelated deletion receipt')
        self.grant=None
        return dict(text=result_text(packet['status']),timer_control='alarm_delete_result',actions_executed=int(packet['status']=='REMOVED'),offered_more=True)
    def timed_out(self):
        if self.pending is not None and self.clock()>=self.pending['select_before_ms']:
            self.clear(); return dict(text=SELECTION['UNKNOWN'],timer_control='alarm_selection',offered_more=True)
        if self.grant is not None and self.clock()>=self.grant['execute_before_ms']+500:
            self.grant=None; return dict(text=UNKNOWN,timer_control='alarm_delete_result',actions_executed=0,offered_more=True)
