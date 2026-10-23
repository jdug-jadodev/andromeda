"""Offline policy/SQLite migration/actor regressions, NOT phone or native-model acceptance."""
import json
from pathlib import Path
import re
import sqlite3
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
from agenda_requests import current_event
from alarm_schedule import weekly,schedule
from alarm_deletion import AlarmDeletion,question
from assistant_tools_model import AssistantToolsModel,TOOLS
from assistant_tools_trial import AssistantToolsTrial
from agenda_dialogue import AgendaDialogue
import test_conversation_voice as fixture

WALL=1792515600000
EVENT='00000000-0000-4000-8000-000000000001'
SESSION,CONTEXT='a'*32,'b'*32


class AlarmLifecycleTests(unittest.TestCase):
    def setUp(self): self.now=[100000]
    def turn(self,seq,text):
        now=self.now[0]
        return dict(id=f'{seq:032x}',seq=seq,text=text,speech_end=now-100,calendar_anchor_ms=WALL+now-100000,calendar_boot_ms=now)
    def deletion(self):
        deletion=AlarmDeletion(lambda:self.now[0],64)
        turn=self.turn(1,'elimina la alarma del lunes a las 17:00')
        event=deletion.start(dict(kind='alarm_delete_request',weekday=1,minute_of_day=1020),turn,SESSION,CONTEXT)['event']
        packet=dict(action='alarm_selection',session_id=SESSION,context_id=CONTEXT,nonce=event['nonce'],request_turn_id=turn['id'],request_sequence=1,
            status='FOUND',event_id=EVENT,revision=2,due_ms=WALL,weekday=1,minute_of_day=1020)
        return deletion,turn,event,packet
    def native(self,text,tool='create_alarm',args=None,enabled=True):
        model=AssistantToolsModel.__new__(AssistantToolsModel); model.anchor_wall=WALL; model.context_metrics={}
        if enabled: model.enable_alarm_lifecycle()
        call={'tool_calls':[dict(type='function',function=dict(name=tool,arguments=json.dumps(args or {})))]}
        return model.interpret_result(call,'tool_calls',text)

    def test_shared_seven_day_am_pm_schedules_match_android_and_require_opt_in(self):
        for row in (ROOT/'tests/weekly_alarm_cases.tsv').read_text(encoding='utf-8').splitlines():
            if not row or row.startswith('#'): continue
            when,weekday,minute,due=row.split('\t')
            with self.subTest(when=when):
                if weekday=='REJECT':
                    with self.assertRaises(ValueError): schedule('ALARM',when,WALL)
                    continue
                value=current_event('ALARM','pon una alarma para '+when,WALL,alarm_lifecycle=True)
                self.assertEqual((value['weekday'],value['minute_of_day'],value['due_ms'],value['delay_ms']),(int(weekday),int(minute),int(due),0))
                with self.assertRaises(ValueError): current_event('ALARM','pon una alarma para '+when,WALL)
                # Equal time is the NEXT week, not immediate replay.
                self.assertEqual(weekly(when,int(due))['due_ms'],int(due)+7*86400000)

    def test_explicit_date_and_elapsed_alarms_remain_once(self):
        for when in ['mañana a las 17:00','en dos minutos','hoy en dos minutos',
                     'hoy dentro de dos minutos','hoy a las 23:59','2026-12-24 a las 23:00']:
            value=current_event('ALARM','pon una alarma '+when,WALL,True)
            self.assertEqual((value['weekday'],value['minute_of_day']),(0,-1))
        with self.assertRaises(ValueError): schedule('REMINDER','lunes a las 17:00',WALL)

    def test_current_natural_time_first_Tuesday_20h_requires_native_selection_and_matching_receipt(self):
        for human in ('una alarma a las 8pm los martes','pon una alarma a las ocho de la noche los martes'):
            selected=self.native(human); self.assertEqual(selected['kind'],'agenda_proposal')
            self.assertEqual((selected['weekday'],selected['minute_of_day']),(2,1200))
        trial=AssistantToolsTrial(lambda:self.now[0],True,64,True)
        turn=self.turn(1,'pon una alarma a las 8pm los martes'); selected=self.native(turn['text'])
        event=trial.model_result(selected,turn,SESSION,CONTEXT,self.now[0]+300000)['event']
        packet={key:event[key] for key in ('event_id','nonce','event_kind','due_ms','request_turn_id','request_sequence','weekday','minute_of_day')}
        packet.update(action='agenda_result',status='CREATED',session_id=SESSION,context_id=CONTEXT)
        with self.assertRaises(ValueError): trial.result(dict(packet,minute_of_day=480),SESSION,CONTEXT)
        response=trial.result(packet,SESSION,CONTEXT)
        self.assertIn('cada martes a las 20:00',response['text']); self.assertEqual(response['actions_executed'],1)

    def test_same_five_native_tools_and_no_model_supplied_target_or_schedule(self):
        payload=AssistantToolsModel.alarm_payload([{'role':'system','content':'Public'},{'role':'user','content':'Hola.'}])
        self.assertEqual(len(payload['tools']),len(TOOLS)); self.assertEqual(payload['tool_choice'],'auto')
        self.assertFalse(payload['parallel_tool_calls'])
        self.assertEqual([tool['function']['name'] for tool in payload['tools']],[tool['function']['name'] for tool in TOOLS])
        self.assertEqual(self.native('pon una alarma el lunes a las 17:00')['weekday'],1)
        self.assertEqual(self.native('elimina la alarma del lunes a las 17:00')['kind'],'alarm_delete_request')
        for human in ['no elimina la alarma del lunes a las 17:00','dijo elimina la alarma del lunes a las 17:00','elimina todas las alarmas','elimina la alarma del lunes a las 17:00 y baja el volumen a 10']:
            self.assertEqual(self.native(human)['kind'],'tools_rejected')
        self.assertEqual(self.native('elimina la alarma del lunes a las 17:00',args={'weekday':'1'})['kind'],'tools_rejected')
        self.assertEqual(self.native('elimina la alarma del lunes a las 17:00',enabled=False)['kind'],'tools_rejected')

    def test_guided_alarm_time_is_actual_final_human_weekday_not_model_data(self):
        form=AgendaDialogue(64,True); first=self.turn(1,'pon una alarma'); form.begin('ALARM',first,self.now[0]); form.spoken(first['id'],True)
        self.now[0]+=3000; final=self.turn(2,'viernes a las 12 a. m.'); result=form.answer(final,self.now[0])
        self.assertEqual((result['weekday'],result['minute_of_day']),(5,0))
        with self.assertRaises(ValueError): form.claim(dict(result,weekday=4),final,self.now[0])
        self.assertEqual(form.claim(result,final,self.now[0])['dialogue_final_turn_id'],final['id'])

    def test_read_only_selection_alone_or_unheard_confirmation_never_authorizes_deletion(self):
        deletion,turn,_,packet=self.deletion(); response=deletion.selection(packet,SESSION,CONTEXT)
        self.assertNotIn('event',response); self.assertEqual(response['text'],question(packet)); self.assertFalse(response['offered_more'])
        self.now[0]+=3000
        with self.assertRaises(ValueError): deletion.human(self.turn(2,'sí'),SESSION,CONTEXT,self.now[0]+300000)
        deletion,turn,_,packet=self.deletion(); deletion.selection(packet,SESSION,CONTEXT); deletion.spoken(turn['id'],False)
        self.assertFalse(deletion.active); self.assertIsNone(deletion.human(self.turn(3,'sí'),SESSION,CONTEXT,self.now[0]+300000))

    def test_delete_grant_bound_to_heard_target_current_human_and_real_receipt_once(self):
        deletion,turn,_,packet=self.deletion(); deletion.selection(packet,SESSION,CONTEXT); deletion.spoken(turn['id'],True)
        self.now[0]+=3000; final=self.turn(2,'sí, elimina esa alarma')
        # Internal punctuation is intentionally not erased to fabricate a positive command.
        rejected=deletion.human(final,SESSION,CONTEXT,self.now[0]+300000)
        self.assertNotIn('event',rejected)
        deletion,turn,_,packet=self.deletion(); deletion.selection(packet,SESSION,CONTEXT); deletion.spoken(turn['id'],True)
        self.now[0]+=3000; final=self.turn(2,'sí'); grant=deletion.human(final,SESSION,CONTEXT,self.now[0]+300000)['event']
        self.assertEqual(grant['event_id'],EVENT); self.assertEqual(grant['revision'],2); self.assertLessEqual(grant['execute_before_ms']-self.now[0],4000)
        receipt={key:grant[key] for key in ('operation_id','nonce','event_id','revision','request_turn_id','request_sequence')}
        receipt.update(action='alarm_delete_result',session_id=SESSION,context_id=CONTEXT,status='REMOVED')
        with self.assertRaises(ValueError): deletion.result(dict(receipt,revision=3),SESSION,CONTEXT)
        response=deletion.result(receipt,SESSION,CONTEXT); self.assertEqual(response['actions_executed'],1)
        self.assertIsNone(deletion.result(receipt,SESSION,CONTEXT))

    def test_selection_ambiguity_stale_nonce_and_confirmation_timeout_never_pick_first(self):
        for status in ['NOT_FOUND','AMBIGUOUS','UNKNOWN']:
            deletion,_,_,packet=self.deletion(); response=deletion.selection(dict(packet,status=status),SESSION,CONTEXT)
            self.assertNotIn('event',response); self.assertFalse(deletion.active)
        deletion,_,_,packet=self.deletion()
        for key,value in [('nonce','c'*32),('session_id','c'*32),('weekday',2),('revision',True)]:
            with self.assertRaises(ValueError): deletion.selection(dict(packet,**{key:value}),SESSION,CONTEXT)
        deletion,turn,_,packet=self.deletion(); deletion.selection(packet,SESSION,CONTEXT); deletion.spoken(turn['id'],True)
        self.now[0]+=120000
        with self.assertRaises(ValueError): deletion.human(self.turn(2,'sí'),SESSION,CONTEXT,self.now[0]+300000)

    def test_no_extra_mutation_or_retries_when_delete_receipt_is_uncertain(self):
        trial=AssistantToolsTrial(lambda:self.now[0],True,64,True); turn=self.turn(1,'elimina la alarma del lunes a las 17:00')
        selection=trial.model_result(dict(kind='alarm_delete_request',weekday=1,minute_of_day=1020),turn,SESSION,CONTEXT,self.now[0]+300000)['event']
        with self.assertRaises(ValueError): trial.model_result(dict(kind='volume_proposal',percent=50),turn,SESSION,CONTEXT,self.now[0]+300000)
        packet=dict(action='alarm_selection',session_id=SESSION,context_id=CONTEXT,nonce=selection['nonce'],request_turn_id=turn['id'],request_sequence=1,status='FOUND',event_id=EVENT,revision=2,due_ms=WALL,weekday=1,minute_of_day=1020)
        trial.result(packet,SESSION,CONTEXT); trial.spoken(turn['id'],True); self.now[0]+=3000
        final=self.turn(2,'sí'); grant=trial.human(final,SESSION,CONTEXT,self.now[0]+300000)['event']
        with self.assertRaises(ValueError): trial.model_result(dict(kind='timer_proposal',seconds=120),final,SESSION,CONTEXT,self.now[0]+300000)
        self.now[0]=grant['execute_before_ms']+500; response=trial.timed_out()
        self.assertEqual(response['actions_executed'],0); self.assertIn('No pude confirmar',response['text']); self.assertIsNone(trial.timed_out())

    def test_weekly_confirmation_requires_matching_android_recurrence_receipt(self):
        trial=AssistantToolsTrial(lambda:self.now[0],True,64,True)
        turn=self.turn(1,'pon una alarma el lunes a las 17:00'); selected=self.native(turn['text'])
        event=trial.model_result(selected,turn,SESSION,CONTEXT,self.now[0]+300000)['event']
        packet={key:event[key] for key in ('event_id','nonce','event_kind','due_ms','request_turn_id','request_sequence','weekday','minute_of_day')}
        packet.update(action='agenda_result',status='CREATED',session_id=SESSION,context_id=CONTEXT)
        with self.assertRaises(ValueError): trial.result(dict(packet,weekday=2),SESSION,CONTEXT)
        response=trial.result(packet,SESSION,CONTEXT)
        self.assertEqual(response['text'],'Alarma semanal guardada: cada lunes a las 17:00, hora de Bogotá. Próxima: 26/10/2026 a las 17:00. ¿Necesitas algo más?')
        self.assertEqual(response['actions_executed'],1); self.assertFalse(response['close_reason'])

    def test_plain_model_output_cannot_claim_deletion_or_snooze_without_receipt(self):
        model=AssistantToolsModel.__new__(AssistantToolsModel); model.anchor_wall=WALL; model.context_metrics={}; model.enable_alarm_lifecycle()
        for text in ['He eliminado la alarma.','Alarma borrada.','Alarma pospuesta cinco minutos.']:
            self.assertEqual(model.interpret_result({'content':text},'stop','hola')['kind'],'tools_rejected')
        self.assertEqual(model.interpret_result({'content':'Hola.'},'stop','elimina la alarma del lunes a las 17:00')['kind'],'tools_rejected')

    def test_interrupted_or_cancelled_deletion_actor_never_executes(self):
        harness=fixture.ConversationVoiceTests()
        def selected(events):
            event=next(item for item in reversed(events) if item['type']=='alarm_select')
            return [harness.control('alarm_selection',session_id=event['session_id'],context_id=event['context_id'],nonce=event['nonce'],request_turn_id=event['request_turn_id'],request_sequence=event['request_sequence'],status='FOUND',event_id=EVENT,revision=2,due_ms=WALL,weekday=1,minute_of_day=1020)]
        def interrupted(events):
            event=next(item for item in reversed(events) if item['type']=='say')
            return [harness.control('tts_started',turn_id=event['turn_id'],at_ms=event['speech_end_ms']+1180),harness.control('tts_interrupted',turn_id=event['turn_id'])]
        for ack,answer in [(harness.ack_last,'no'),(interrupted,'sí')]:
            steps=harness.utterance()+[(100100,selected),(101200,ack),(103020,[harness.pcm(103020)]),(104200,harness.ack_last)]
            events,_=harness.run_session(steps,transcripts=['elimina la alarma del lunes a las 17:00',answer],answers=[dict(kind='alarm_delete_request',weekday=1,minute_of_day=1020),dict(kind='reply',text='Dime una petición completa.')],assistant_tools=True,guided_agenda=True,continuous_voice=True,alarm_lifecycle=True)
            self.assertFalse(any(item['type']=='alarm_delete_execute' for item in events))

    def test_uncertain_occurrence_is_held_not_retried_on_boot_or_stale_receiver(self):
        source=(ROOT/'android-voice/src/local/doja/voice/AgendaStore.java').read_text(encoding='utf-8')
        self.assertIn('store.unsettled(entry.id)',source); self.assertIn('if(unsettled(id))',source)
        self.assertIn("status IN ('PREPARING','UNKNOWN')",source)
        self.assertIn('return advanceWeekly(current)',source)
        self.assertIn('AlarmOccurrenceTransaction.apply(permit',source)
        playback=(ROOT/'android-voice/src/local/doja/voice/AgendaDeliveryService.java').read_text(encoding='utf-8')
        self.assertIn('if(!VoiceService.continuousVoiceActive) stopService',playback)
        self.assertIn('controlNonce.equals(intent.getStringExtra("control_nonce"))',playback)
        self.assertIn('"andromeda://alarm/"+id+"/"+revision+"/"+controlNonce+"/"+action',playback)
        self.assertIn('"SNOOZED".equals(status)',playback)
        self.assertNotIn('VoiceConnection',playback)

    def test_coordinated_actor_delete_then_web_without_closure_or_second_model_selection_for_yes(self):
        harness=fixture.ConversationVoiceTests()
        def selected(events):
            event=next(item for item in reversed(events) if item['type']=='alarm_select')
            return [harness.control('alarm_selection',session_id=event['session_id'],context_id=event['context_id'],nonce=event['nonce'],request_turn_id=event['request_turn_id'],request_sequence=event['request_sequence'],status='FOUND',event_id=EVENT,revision=2,due_ms=WALL,weekday=1,minute_of_day=1020)]
        def removed(events):
            event=next(item for item in reversed(events) if item['type']=='alarm_delete_execute')
            keys=('session_id','context_id','operation_id','nonce','event_id','revision','request_turn_id','request_sequence')
            return [harness.control('alarm_delete_result',**{key:event[key] for key in keys},status='REMOVED')]
        steps=harness.utterance()+[(100100,selected),(101200,harness.ack_last),(103020,[harness.pcm(103020)]),(103100,removed),(104200,harness.ack_last),
            (106020,[harness.pcm(106020)]),(107200,harness.ack_last)]
        events,model=harness.run_session(steps,transcripts=['elimina la alarma del lunes a las 17:00','sí','busca en Internet clima'],answers=[dict(kind='alarm_delete_request',weekday=1,minute_of_day=1020),dict(kind='web_result',text='Respuesta pública de prueba.',sources=[])],assistant_tools=True,guided_agenda=True,continuous_voice=True,alarm_lifecycle=True)
        self.assertEqual(events[0]['protocol'],13); self.assertEqual(events[0]['alarm_lifecycle_policy'],'weekly_alarm_lifecycle_v1')
        self.assertEqual(model.start.call_count,2); self.assertEqual(sum(item['type']=='alarm_delete_execute' for item in events),1)
        self.assertFalse(any(item['type']=='conversation_closed' for item in events))
        self.assertEqual(sum(item.get('actions_executed',0) for item in events if item['type']=='say'),1)
        self.assertTrue(any(item.get('timer_control')=='web' for item in events))

    def test_v1_v2_migration_preserves_obligations_and_receipts_without_guessing_weekdays(self):
        source=(ROOT/'android-voice/src/local/doja/voice/AgendaStore.java').read_text(encoding='utf-8')
        statements=[json.loads('"'+value+'"') for value in re.findall(r'db\.execSQL\("(ALTER TABLE agenda [^"\n]+)"\)',source)]
        self.assertEqual(len(statements),3)
        action=re.search(r'db\.execSQL\("(CREATE TABLE agenda_actions [^"\n]+)"\)',source).group(1)
        for version in (1,2):
            with self.subTest(version=version),sqlite3.connect(':memory:') as db:
                db.execute('CREATE TABLE agenda(id TEXT PRIMARY KEY,kind TEXT,text TEXT,due_ms INTEGER,zone TEXT,state TEXT,revision INTEGER'+(',elapsed_due INTEGER NOT NULL DEFAULT 0 CHECK(elapsed_due>=0)' if version==2 else '')+')')
                row=[EVENT,'REMINDER','Public fixture',WALL,'America/Bogota','PENDING',4]+([123456] if version==2 else [])
                db.execute('INSERT INTO agenda VALUES ('+','.join('?' for _ in row)+')',row)
                db.execute('CREATE TABLE agenda_receipts(id TEXT PRIMARY KEY,status TEXT,expires_ms INTEGER)'); db.execute('INSERT INTO agenda_receipts VALUES (?,?,?)',(EVENT,'DELIVERED',WALL+100000))
                for statement in statements[0 if version==1 else 1:]: db.execute(statement)
                db.execute(action)
                self.assertEqual(db.execute('SELECT * FROM agenda').fetchone(),tuple(row+([0] if version==1 else [])+[0,-1]))
                self.assertEqual(db.execute('SELECT status FROM agenda_receipts').fetchone(),('DELIVERED',))
                self.assertEqual(db.execute('SELECT COUNT(*) FROM agenda_actions').fetchone(),(0,))
                with self.assertRaises(sqlite3.IntegrityError): db.execute('UPDATE agenda SET weekday=1,minute_of_day=1020')


if __name__=='__main__': unittest.main()
