"""Native-selected, current-request-only read scope. Reminder CONTENT stays in Android.

No database, model, executor, network or persistence. Receipts contain counts only.
"""
import re
import uuid
from doja_intents import normalized

POLICY = 'android_reminder_read_v1'
ACK = 'Voy a leer el resultado de tus recordatorios guardados.'
UNAVAILABLE = 'No pude leer los recordatorios guardados. No he cambiado ninguno.'


def current_read(human):
    if (not isinstance(human, str) or not 0 < len(human.encode('utf-16-le')) // 2 <= 250
            or re.search(r'[\x00-\x1f\x7f"\'«»<>]', human)):
        raise ValueError('invalid_reminder_read')
    text = normalized(human)
    if (not re.search(r'\brecordatorios?\b', text)
            or not re.search(r'\b(?:lista|listar|listame|muestra|muestrame|mostrar|dime|dame|lee|leeme|leer|ver|consulta|consultar|que|cuales|cuantos|tengo|hay|tienes)\b', text)
            or re.search(r'\b(?:no|nunca|ni|si|ayer|dije|ejemplo|imagina|supongamos|pon|ponme|crea|crear|programa|recuerdame|cancela|borra|elimina|quita|apaga|enciende|internet|web|google|whatsapp)\b', text)):
        raise ValueError('explicit_reminder_read_required')
    return True


class ReminderRead:
    def __init__(self, clock):
        self.clock = clock; self.inflight = None; self.consumed = set(); self.closed = False

    def authorize(self, turn, session, context, session_end):
        now = self.clock(); current_read(turn['text'])
        if (self.closed or self.inflight or turn['id'] in self.consumed or len(self.consumed) >= 24
                or not all(isinstance(value, str) and re.fullmatch('[0-9a-f]{32}', value) for value in (turn['id'],session,context))
                or type(turn['seq']) is not int or not 1 <= turn['seq'] <= 64
                or type(turn['speech_end']) is not int or not 0 < turn['speech_end'] <= now + 100
                or now - turn['speech_end'] >= 56500 or type(session_end) is not int or not now < session_end <= now + 300000):
            raise ValueError('invalid_reminder_read_scope')
        before = min(now + 4000, turn['speech_end'] + 56500, session_end - 5000)
        if before-now < 500: raise ValueError('insufficient_read_budget')
        self.consumed.add(turn['id'])
        self.inflight = dict(type='reminder_read', session_id=session, context_id=context, nonce=uuid.uuid4().hex,
            request_turn_id=turn['id'], request_sequence=turn['seq'], speech_end_ms=turn['speech_end'], read_before_ms=before)
        return {'event':dict(self.inflight),'executing':True}

    @staticmethod
    def reply(grant, status, total=0, shown=0):
        return {'text':ACK if status=='READ' else UNAVAILABLE,'timer_control':'reminder_list','actions_executed':0,
            'offered_more':True,'reminder_read':dict(nonce=grant['nonce'],status=status,total=total,shown=shown)}

    def result(self, packet, session, context):
        grant = self.inflight
        if grant is None: return None
        expected = {key:grant[key] for key in ('session_id','context_id','nonce','request_turn_id','request_sequence')}
        if (grant['session_id']!=session or grant['context_id']!=context
                or set(packet)!=set(expected)|{'action','status','total','shown'} or packet.get('action')!='reminder_read_result'
                or any(type(packet.get(key)) is not type(value) or packet[key]!=value for key,value in expected.items())
                or packet.get('status') not in {'READ','UNAVAILABLE'}
                or type(packet.get('total')) is not int or not 0 <= packet['total'] <= 100000
                or type(packet.get('shown')) is not int or not 0 <= packet['shown'] <= min(3,packet['total'])
                or packet['status']=='UNAVAILABLE' and (packet['total'] or packet['shown'])):
            raise ValueError('unrelated_reminder_read_receipt')
        self.inflight = None
        return self.reply(grant,packet['status'],packet['total'],packet['shown'])

    def timed_out(self):
        if not self.inflight or self.clock() < self.inflight['read_before_ms'] + 500: return None
        grant = self.inflight; self.inflight = None
        return self.reply(grant,'TIMEOUT')

    def cancel(self): self.inflight = None
    def close(self): self.closed = True; self.cancel()
