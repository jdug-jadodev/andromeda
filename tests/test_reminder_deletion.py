import contextlib
import csv
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock,Mock,patch

ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'backend'))
import reminder_deletion as deletion
from assistant_tools_trial import AssistantToolsTrial
import conversation_voice as voice
import test_assistant_tools as native_fixture
import test_reminder_clarification as reminder_fixture
import test_conversation_voice as actor_fixture
import test_continuous_voice as continuous_fixture

SESSION='a'*32;CONTEXT='b'*32;EVENT='00000000-0000-0000-0000-000000000071';DUE=1792328460000

def turn(text,seq):return reminder_fixture.turn(text,seq)
def selection(grant,status='QUESTION',stage='CONFIRM',**extra):
    value=dict(action='reminder_delete_selection',session_id=SESSION,context_id=CONTEXT,status=status,
        **{k:grant[k]for k in ('nonce','request_turn_id','request_sequence')})
    if status=='QUESTION':
        value['stage']=stage
        if stage=='CONFIRM':value.update(event_id=EVENT,revision=1,due_ms=DUE)
    return dict(value,**extra)
def receipt(grant,status='REMOVED'):
    return dict(action='reminder_delete_result',session_id=SESSION,context_id=CONTEXT,status=status,
        **{k:grant[k]for k in ('nonce','operation_id','event_id','revision','request_turn_id','request_sequence')})


class ReminderDeletionTests(unittest.TestCase):
    def model(self,enabled=True):
        model=reminder_fixture.ReminderClarificationTests().model()
        if enabled:model.enable_reminder_deletion()
        return model

    def ready(self,stage='CONFIRM',trial=False):
        clock=[100000]
        flow=AssistantToolsTrial(lambda:clock[0],True,64,True,True,True,True,True) if trial else deletion.ReminderDeletion(lambda:clock[0])
        human=turn('elimina el recordatorio de regar las plantas',1)
        first=(flow.model_result({'kind':'reminder_delete_request'},human,SESSION,CONTEXT,400000) if trial
            else flow.start(human,SESSION,CONTEXT,400000))
        answer=(flow.result(selection(first['event'],stage=stage),SESSION,CONTEXT) if trial
            else flow.selection(selection(first['event'],stage=stage),SESSION,CONTEXT))
        self.assertEqual(answer['timer_control'],'reminder_delete_question')
        self.assertNotIn('event',answer);clock[0]=100300;flow.spoken(human['id'],True)
        return clock,flow,first

    def test_shared_current_request_and_fresh_yes_never_repair_a_misheard_word(self):
        with(ROOT/'tests/reminder_delete_cases.tsv').open(encoding='utf-8',newline='')as stream:
            for row in csv.DictReader(stream,delimiter='\t'):
                if row['operation']=='choice':continue
                fn=deletion.requested if row['operation']=='request'else deletion.affirmative
                try:actual=bool(fn(row['human']))
                except ValueError:actual=False
                with self.subTest(operation=row['operation'],human=row['human']):self.assertEqual(actual,row['expected']=='1')

    def test_seventh_native_tool_only_after_negotiation_no_model_target_arguments(self):
        before=self.model(False).payload([{'role':'system','content':'public'},{'role':'user','content':'Hola.'}])
        model=self.model();model.enable_reminder_deletion()
        after=model.payload([{'role':'system','content':'public'},{'role':'user','content':'Hola.'}])
        self.assertEqual(after['tools'][:6],before['tools']);self.assertEqual(len(after['tools']),7)
        self.assertEqual(after['max_tokens'],96);self.assertEqual(after['tool_choice'],'auto');self.assertFalse(after['parallel_tool_calls'])
        self.assertEqual(after['tools'][-1]['function']['parameters'],{'type':'object','properties':{}})
        for text in ('elimina el recordatorio de regar las plantas','quiero borrar un recordatorio'):
            self.assertEqual(model.interpret_result(native_fixture.native('delete_reminder'),'tool_calls',text),{'kind':'reminder_delete_request'})
            self.assertEqual(self.model(False).interpret_result(native_fixture.native('delete_reminder'),'tool_calls',text)['kind'],'tools_rejected')
        for message,text in [(native_fixture.native('delete_reminder',event_id=EVENT),'elimina el recordatorio'),
                (native_fixture.native('delete_reminder'),'no elimines el recordatorio'),
                (native_fixture.native('delete_reminder'),'borra todos los recordatorios'),
                (native_fixture.native('create_reminder'),'elimina el recordatorio')]:
            self.assertEqual(model.interpret_result(message,'tool_calls',text)['kind'],'tools_rejected')
        model.post.assert_not_called()

    def test_unknown_confirmation_reasks_then_one_matching_receipt_no_agenda_contents(self):
        clock,flow,_=self.ready(trial=True)
        human=turn('te',2);clock[0]=human['calendar_boot_ms'];step=flow.human(human,SESSION,CONTEXT,400000)
        self.assertEqual(step['event']['type'],'reminder_delete_step');self.assertEqual(len(flow.consumed),0)
        question=flow.result(selection(step['event']),SESSION,CONTEXT)
        self.assertEqual(question['text'],deletion.QUESTION);self.assertNotIn('event',question)
        clock[0]+=300;flow.spoken(human['id'],True)
        yes=turn('sí, por favor',3);clock[0]=yes['calendar_boot_ms'];grant=flow.human(yes,SESSION,CONTEXT,400000)['event']
        self.assertEqual(grant['type'],'reminder_delete_execute');self.assertEqual(grant['event_id'],EVENT)
        self.assertLessEqual(grant['execute_before_ms']-clock[0],4000);self.assertLessEqual(grant['execute_before_ms'],yes['speech_end']+26500)
        self.assertEqual(len(flow.consumed),1)
        for key in ('text','title','candidates','content_hash'):self.assertNotIn(key,grant)
        with self.assertRaises(ValueError):flow.result(dict(receipt(grant),nonce='f'*32),SESSION,CONTEXT)
        response=flow.result(receipt(grant),SESSION,CONTEXT)
        self.assertEqual(response['actions_executed'],1);self.assertIn('Recordatorio eliminado',response['text'])
        self.assertIsNone(flow.result(receipt(grant),SESSION,CONTEXT))
        with self.assertRaises(ValueError):flow.model_result({'kind':'volume_dialogue_start'},yes,SESSION,CONTEXT,400000)

    def test_no_unheard_interrupted_replayed_pre_question_or_expired_confirmation(self):
        for fault in ('unheard','interrupted','same_turn','earlier_speech','replayed_sequence','expired'):
            clock,flow,_=self.ready()
            if fault=='unheard':flow.armed=False
            elif fault=='interrupted':flow.spoken(turn('',1)['id'],False)
            yes=turn('sí',2);clock[0]=yes['calendar_boot_ms']
            if fault=='same_turn':yes['id']=turn('',1)['id']
            elif fault=='earlier_speech':yes['speech_end']=100200
            elif fault=='replayed_sequence':yes['seq']=1
            elif fault=='expired':clock[0]=400000;yes=dict(yes,calendar_boot_ms=400000,speech_end=399900)
            with self.subTest(fault=fault):
                if fault=='interrupted':self.assertIsNone(flow.human(yes,SESSION,CONTEXT,clock[0]+300000))
                else:
                    with self.assertRaises(ValueError):flow.human(yes,SESSION,CONTEXT,clock[0]+300000)
                self.assertIsNone(flow.grant)

    def test_read_receipts_cannot_authorize_before_human_yes_or_export_content(self):
        clock=[100000];flow=deletion.ReminderDeletion(lambda:clock[0]);human=turn('elimina el recordatorio',1)
        grant=flow.start(human,SESSION,CONTEXT,400000)['event']
        for packet in (selection(grant,text='private agenda'),selection(grant,event_id='bad'),
                dict(selection(grant),request_sequence=True),selection(grant,status='REMOVED'),
                selection(grant,stage='TARGET',candidates=[])):
            with self.assertRaises(ValueError):flow.selection(packet,SESSION,CONTEXT)
            self.assertIsNone(flow.grant)
        answer=flow.selection(selection(grant),SESSION,CONTEXT)
        self.assertEqual(answer['actions_executed']if'actions_executed'in answer else 0,0)
        self.assertNotIn('event',answer);self.assertIsNone(flow.grant)

    def test_selection_and_deletion_timeouts_are_unknown_without_retry_or_late_success(self):
        clock=[100000];flow=deletion.ReminderDeletion(lambda:clock[0])
        grant=flow.start(turn('elimina el recordatorio',1),SESSION,CONTEXT,400000)['event']
        clock[0]=grant['select_before_ms'];reply=flow.timed_out()
        self.assertEqual(reply['text'],deletion.UNAVAILABLE);self.assertFalse(flow.active)
        self.assertIsNone(flow.selection(selection(grant),SESSION,CONTEXT));self.assertIsNone(flow.timed_out())
        clock,flow,_=self.ready(trial=True);yes=turn('sí',2);clock[0]=yes['calendar_boot_ms']
        grant=flow.human(yes,SESSION,CONTEXT,400000)['event'];clock[0]=grant['execute_before_ms']+500
        reply=flow.timed_out();self.assertEqual(reply['actions_executed'],0);self.assertIn(deletion.UNKNOWN,reply['text'])
        self.assertIsNone(flow.result(receipt(grant),SESSION,CONTEXT));self.assertIsNone(flow.timed_out())

    def test_cancellation_not_found_and_unavailable_never_claim_mutation(self):
        for status,text in [('CANCELLED',deletion.CANCELLED),('NOT_FOUND',deletion.NOT_FOUND),('UNAVAILABLE',deletion.UNAVAILABLE)]:
            clock=[100000];flow=deletion.ReminderDeletion(lambda:clock[0])
            grant=flow.start(turn('elimina el recordatorio',1),SESSION,CONTEXT,400000)['event']
            reply=flow.selection(selection(grant,status=status),SESSION,CONTEXT)
            self.assertEqual(reply['text'],text);self.assertNotIn('event',reply);self.assertFalse(flow.active)

    def test_one_pending_dialogue_or_mutation_blocks_other_tool_and_wake_revokes_scope(self):
        clock,flow,_=self.ready(trial=True)
        for kind in ('agenda_dialogue_start','volume_dialogue_start','reminder_list_request','reminder_delete_request'):
            with self.assertRaises(ValueError):flow.model_result({'kind':kind,'event_kind':'REMINDER'},turn('public',2),SESSION,CONTEXT,400000)
        flow.cancel();self.assertFalse(flow.pending_dialogue);self.assertIsNone(flow.reminder_delete.grant)
        with self.assertRaises(ValueError):AssistantToolsTrial(lambda:100000,True,64,True,True,True,False,True)

    def test_actor_target_choose_confirmation_receipt_then_listing_without_extra_native_calls(self):
        h=actor_fixture.ConversationVoiceTests()
        def choose(stage):
            def callback(events):
                grant=next(e for e in reversed(events)if e['type']=='reminder_delete_step')
                packet=selection(grant,stage=stage);packet.update(session_id=grant['session_id'],context_id=grant['context_id'])
                return [h.control(**packet)]
            return callback
        def removed(events):
            grant=next(e for e in reversed(events)if e['type']=='reminder_delete_execute')
            packet=receipt(grant);packet.update(session_id=grant['session_id'],context_id=grant['context_id']);return[h.control(**packet)]
        def read(events):
            grant=next(e for e in reversed(events)if e['type']=='reminder_read')
            return[h.control('reminder_read_result',status='READ',total=0,shown=0,
                **{k:grant[k]for k in ('session_id','context_id','nonce','request_turn_id','request_sequence')})]
        steps=h.utterance()+[(100100,choose('TARGET')),(101200,h.ack_last),(103020,[h.pcm(103020)]),(103100,choose('CHOOSE')),
            (104200,h.ack_last),(106020,[h.pcm(106020)]),(106100,choose('CONFIRM')),(107200,h.ack_last),
            (109020,[h.pcm(109020)]),(109100,removed),(110200,h.ack_last),(112020,[h.pcm(112020)]),(112100,read),(113200,h.ack_last)]
        events,model=h.run_session(steps,assistant_tools=True,guided_agenda=True,continuous_voice=True,alarm_lifecycle=True,
            confirmed_volume=True,reminder_read=True,reminder_clarification=True,reminder_deletion=True,
            transcripts=['quiero borrar un recordatorio','regar las plantas','el segundo','sí','qué recordatorios tengo'],
            answers=[{'kind':'reminder_delete_request'},{'kind':'reminder_list_request'}])
        self.assertEqual(events[0]['reminder_delete_policy'],deletion.POLICY);self.assertEqual(len(events[0]['allowed_tools']),7)
        self.assertEqual(model.start.call_count,2);self.assertEqual(sum(e['type']=='reminder_delete_execute'for e in events),1)
        self.assertEqual(sum(e.get('actions_executed',0)for e in events if e['type']=='say'),1)
        self.assertEqual(sum(e['type']=='ready'for e in events),1);self.assertFalse(any(e['type']=='conversation_closed'for e in events))
        self.assertFalse(any(e['type']in{'agenda_execute','timer_execute','volume_execute','alarm_delete_execute'}for e in events))
        for event in events:
            if event['type']=='say'and event.get('timer_control')=='reminder_delete_question':self.assertEqual(event['text'],deletion.QUESTION)

    def test_handshake_rejects_missing_or_wrong_deletion_policy_before_context_restore(self):
        for policy in (None,'wrong',deletion.POLICY):
            with self.subTest(policy=policy),tempfile.TemporaryDirectory()as directory:
                key=Path(directory)/'key';key.write_text('public-key-'*4)
                owner=Mock();owner.alive.return_value=True;clock=lambda:100000
                monitor=continuous_fixture.Monitor(clock,lambda:continuous_fixture.Sample(100000,1600000,740000,160000,1000,500,240,80,'charging'),None,continuous_model=True)
                runtime=continuous_fixture.ContinuousResidentRuntime(owner,clock,monitor,enabled=True,cache_purger=lambda model:0)
                server=MagicMock();raw=MagicMock();conn=MagicMock();tls=Mock();tls.wrap_socket.return_value=conn
                server.__enter__.return_value=server;raw.__enter__.return_value=raw;conn.__enter__.return_value=conn
                server.accept.side_effect=[(raw,('127.0.0.1',1)),RuntimeError('end public fixture')]
                hello=dict(token=key.read_text(),protocol=13,mode='assistant_tools',source='synthetic_pcm',
                    web_response_mode='worker_first_v3_spanish',phone_volume_policy='confirmed_media_v3',
                    agenda_dialogue_policy='guided_agenda_v1',action_completion_policy='continue_v1',
                    voice_lifetime_policy='continuous_voice_v1',dialogue_reset_policy='client_fresh_v1',
                    reminder_read_policy='android_reminder_read_v1',reminder_creation_policy='confirmed_reminder_intent_v1',
                    alarm_lifecycle_policy='weekly_alarm_lifecycle_v1',context=dict(version=1,id='a'*32,last_human_ms=0,turns=[]))
                if policy is not None:hello['reminder_delete_policy']=policy
                model=MagicMock();model.owned.alive.return_value=True;model.thread=None
                def construct(key,owned=None):model.owned=owned;return model
                with patch.object(voice.ssl,'SSLContext',return_value=tls),patch.object(voice.socket,'socket',return_value=server), \
                        patch.object(voice,'read_hello',return_value=hello),patch.object(voice,'session',return_value='voice_peer_closed')as session, \
                        patch('assistant_tools_model.AssistantToolsModel',side_effect=construct),patch.object(voice,'now_ms',return_value=100000),patch.object(voice,'print'):
                    def serve():voice.serve(key,key,Mock(),0,'public-cert','public-key','synthetic_pcm',runtime.check,
                        assistant_tools=True,guided_agenda=True,continuous_voice=True,resident=runtime,alarm_lifecycle=True,
                        confirmed_volume=True,reminder_read=True,reminder_clarification=True,reminder_deletion=True)
                    if policy==deletion.POLICY:
                        serve();session.assert_called_once();self.assertTrue(session.call_args.kwargs['reminder_deletion'])
                        model.memory.restore.assert_called_once()
                    else:
                        with self.assertRaisesRegex(RuntimeError,'end public fixture'):serve()
                        session.assert_not_called();model.memory.restore.assert_not_called()
                model.enable_reminder_deletion.assert_called_once()


if __name__=='__main__':unittest.main()
