import csv
import contextlib
import io
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, Mock, patch

ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'backend'))
import agenda_dialogue as form
from assistant_tools_trial import AssistantToolsTrial
from assistant_tools_model import AssistantToolsModel, REMINDER_CLARIFICATION_SYSTEM, REMINDER_SELECTION_EXAMPLE
import test_assistant_tools as tools_fixture
import test_conversation_voice as actor_fixture
import conversation_voice as voice

WALL=1792328400000
def turn(text,seq,now=None):
    at=100000+(seq-1)*3000 if now is None else now
    return dict(id=f'{seq:032x}',seq=seq,text=text,speech_end=at-100,calendar_boot_ms=at,calendar_anchor_ms=WALL+at-100000)


class ReminderClarificationTests(unittest.TestCase):
    def model(self,enabled=True):
        model=tools_fixture.AssistantToolsTests().model();model.enable_alarm_lifecycle();model.enable_confirmed_volume();model.enable_basic_assistant()
        if enabled:model.enable_reminder_clarification()
        return model

    def test_shared_human_traces_questions_never_supply_old_fields_or_mutation(self):
        previous=None
        with(ROOT/'tests/reminder_clarification_cases.tsv').open(encoding='utf-8',newline='') as stream:
            for row in csv.DictReader(stream,delimiter='\t'):
                if previous!=row['case']:draft=form.AgendaDialogue(64,True,True);previous=row['case']
                human=turn(row['human'],int(row['step']));now=human['calendar_boot_ms']
                def step():return draft.begin('REMINDER',human,now) if row['step']=='1' else draft.answer(human,now)
                with self.subTest(case=row['case'],step=row['step']):
                    if row['expected']=='INVALID':
                        with self.assertRaises(ValueError):step()
                        continue
                    value=step()
                    if row['expected']=='none':self.assertIsNone(value);continue
                    expected={'question':'agenda_question','cancelled':'agenda_cancelled','proposal':'guided_agenda_proposal'}[row['expected']]
                    self.assertEqual(value['kind'],expected);self.assertEqual(value['text'],getattr(form,row['text'],row['text']))
                    if value['kind']=='guided_agenda_proposal':
                        if row['case'] not in {'legacy_complete_open'}:self.assertNotEqual(value['dialogue_origin_turn_id'],f'{1:032x}')
                        self.assertEqual(value['delay_ms'],300000);self.assertEqual(draft.claim(value,human,now)['text'],'regar las plantas')
                        with self.assertRaises(ValueError):draft.claim(value,human,now)
                    if row['heard']!='-':draft.spoken(human['id'],row['heard']=='1',now+300)

    def test_native_selection_and_negotiated_policy_required_no_repaired_text_or_model_parameters(self):
        for text in ('con un recordatorio','me ayudas con un recordatorio','necesito que me recuerdes algo'):
            self.assertEqual(self.model().interpret_result(tools_fixture.native('create_reminder'),'tool_calls',text),
                {'kind':'agenda_dialogue_start','event_kind':'REMINDER'})
            self.assertEqual(self.model(False).interpret_result(tools_fixture.native('create_reminder'),'tool_calls',text)['kind'],'tools_rejected')
        for message in (tools_fixture.native('create_alarm'),tools_fixture.native('create_reminder',text='regar las plantas')):
            self.assertEqual(self.model().interpret_result(message,'tool_calls','con un recordatorio')['kind'],'tools_rejected')
        self.assertNotEqual(self.model().interpret_result({'content':form.REMINDER_INTENT},'stop','con un recordatorio')['kind'],'agenda_dialogue_start')
        model=self.model();body=model.payload([{'role':'system','content':'public'},{'role':'user','content':'con un recordatorio'}])
        self.assertEqual(body['tool_choice'],'auto');self.assertFalse(body['parallel_tool_calls']);self.assertEqual(len(body['tools']),6)
        self.assertEqual(body['max_tokens'],96);self.assertEqual(body['tools'][2]['function']['parameters'],{'type':'object','properties':{}})
        model.post.assert_not_called()

    def test_clear_requests_and_other_tools_preserve_installed_interpretation(self):
        for name,text in [('create_reminder','recuérdame beber agua en cinco minutos'),
                ('create_reminder','pon un recordatorio'),('create_alarm','pon una alarma a las 8pm los martes'),
                ('list_reminders','qué recordatorios tengo'),('set_phone_volume','sube el volumen al cien por ciento')]:
            with self.subTest(name=name):
                self.assertEqual(self.model().interpret_result(tools_fixture.native(name),'tool_calls',text),
                    self.model(False).interpret_result(tools_fixture.native(name),'tool_calls',text))

    def test_known_native_indirect_misselection_cannot_read_agenda_or_create_grant(self):
        model=self.model()
        result=model.interpret_result(tools_fixture.native('list_reminders'),'tool_calls','necesito que me recuerdes algo')
        self.assertEqual(result['kind'],'tools_rejected');model.post.assert_not_called()
        trial=AssistantToolsTrial(lambda:100000,True,64,True,True,True,True)
        response=trial.model_result(result,turn('necesito que me recuerdes algo',1),'a'*32,'b'*32,400000)
        self.assertNotIn('event',response);self.assertIsNone(trial.inflight)
        self.assertFalse(trial.pending_dialogue);self.assertIsNone(trial.read.inflight)

    def test_public_prefix_55s_does_not_extend_human_response_compute_or_action_windows(self):
        model=self.model();baseline=self.model(False)
        model.post.side_effect=None;model.post.return_value={};model.memory=Mock()
        model.prepare();path,body,deadline=model.post.call_args.args
        self.assertEqual(path,'/v1/chat/completions');self.assertEqual(deadline,155000)
        self.assertEqual(body['max_tokens'],1);model.memory.messages.assert_not_called()
        self.assertEqual(model.response_start_ms,baseline.response_start_ms);self.assertEqual(model.response_start_ms,150000)
        self.assertEqual(model.compute_ms,baseline.compute_ms);self.assertEqual(model.compute_ms,150000)
        self.assertEqual(model.turn_deadline(99900,400000),baseline.turn_deadline(99900,400000))
        self.assertEqual(baseline.preparation_ms,45000);self.assertEqual(model.preparation_ms,55000)

    def test_heard_intent_and_all_fresh_human_fields_only_then_single_matching_receipt(self):
        clock=[100000];trial=AssistantToolsTrial(lambda:clock[0],True,64,True,True,True,True)
        first=turn('con un recordatorio beber agua mañana a las 20:00',1)
        response=trial.model_result({'kind':'agenda_dialogue_start','event_kind':'REMINDER'},first,'a'*32,'b'*32,400000)
        self.assertEqual(response['text'],form.REMINDER_INTENT);self.assertNotIn('event',response)
        clock[0]=100300;trial.spoken(first['id'],True)
        for human,text in [(turn('sí',2),form.SUBJECT),(turn('regar las plantas',3),form.WHEN)]:
            clock[0]=human['calendar_boot_ms'];response=trial.human(human,'a'*32,'b'*32,400000)
            self.assertEqual(response['text'],text);self.assertNotIn('event',response);self.assertIsNone(trial.inflight)
            clock[0]+=300;trial.spoken(human['id'],True)
        final=turn('en cinco minutos',4);clock[0]=final['calendar_boot_ms'];grant=trial.human(final,'a'*32,'b'*32,400000)['event']
        self.assertEqual(grant['text'],'regar las plantas');self.assertEqual(grant['dialogue_origin_turn_id'],turn('sí',2)['id'])
        self.assertEqual(grant['request_turn_id'],final['id']);self.assertLessEqual(grant['execute_before_ms']-clock[0],4000)
        packet={key:grant[key] for key in ('event_id','nonce','event_kind','due_ms','request_turn_id','request_sequence','weekday','minute_of_day')}
        packet.update(action='agenda_result',session_id='a'*32,context_id='b'*32,status='CREATED')
        with self.assertRaises(ValueError):trial.result(dict(packet,nonce='f'*32),'a'*32,'b'*32)
        reply=trial.result(packet,'a'*32,'b'*32);self.assertEqual(reply['actions_executed'],1);self.assertIn('Recordatorio guardado: regar las plantas',reply['text'])
        self.assertIsNone(trial.result(packet,'a'*32,'b'*32));self.assertEqual(len(trial.consumed),1)

    def test_yes_recorded_before_question_completion_replay_and_expiry_do_not_authorize(self):
        first=turn('con un recordatorio',1)
        for bad in ('earlier_speech','same_turn','expiry'):
            draft=form.AgendaDialogue(64,True,True);draft.begin('REMINDER',first,100000);draft.spoken(first['id'],True,102950)
            value=turn('sí',2)
            if bad=='earlier_speech':value['speech_end']=102940
            elif bad=='same_turn':value['id']=first['id']
            else:value=turn('sí',2,400000)
            with self.subTest(bad=bad),self.assertRaises(ValueError):draft.answer(value,value['calendar_boot_ms'])
            self.assertFalse(draft.active)
        draft=form.AgendaDialogue(64,True,True);draft.begin('REMINDER',first,100000);draft.spoken(first['id'],True)
        self.assertFalse(draft.active)  # Missing heard timestamp is not authorization.

    def test_old_or_mismatching_client_rejected_before_restoring_context(self):
        for policy in (None,'unknown_policy',form.REMINDER_POLICY):
            with self.subTest(policy=policy),tempfile.TemporaryDirectory() as directory:
                key=Path(directory)/'key';key.write_text('public-key-'*4)
                hello=dict(token=key.read_text(),protocol=11,mode='assistant_tools',source='synthetic_pcm',
                    web_response_mode='worker_first_v3_spanish',phone_volume_policy='absolute_media_v2_turn60',
                    agenda_dialogue_policy='guided_agenda_v1',action_completion_policy='continue_v1',context={})
                if policy is not None:hello['reminder_creation_policy']=policy
                server=MagicMock();raw=MagicMock();conn=MagicMock();tls=Mock();tls.wrap_socket.return_value=conn
                server.__enter__.return_value=server;raw.__enter__.return_value=raw;conn.__enter__.return_value=conn
                server.accept.side_effect=[(raw,('127.0.0.1',1)),RuntimeError('end public fixture')]
                model=MagicMock();model.owned.alive.return_value=True
                with patch.object(voice.ssl,'SSLContext',return_value=tls),patch.object(voice.socket,'socket',return_value=server), \
                        patch.object(voice,'read_hello',return_value=hello),patch.object(voice,'session') as session, \
                        patch('assistant_tools_model.AssistantToolsModel',return_value=model),patch.object(voice,'now_ms',return_value=100000),patch.object(voice,'print'):
                    def serve():voice.serve(key,key,Mock(),300,'public-cert','public-key','synthetic_pcm',lambda:True,
                        assistant_tools=True,guided_agenda=True,reminder_clarification=True)
                    if policy==form.REMINDER_POLICY:
                        serve();session.assert_called_once();self.assertTrue(session.call_args.kwargs['reminder_clarification'])
                        model.memory.restore.assert_called_once()
                    else:
                        with self.assertRaisesRegex(RuntimeError,'end public fixture'):serve()
                        session.assert_not_called();model.memory.restore.assert_not_called()
                model.enable_reminder_clarification.assert_called_once()

    def test_preparation_and_runtime_use_same_payload_preserve_base_system_other_tools_and_budgets(self):
        baseline=self.model(False).payload([{'role':'system','content':'public'},{'role':'user','content':'Hola.'}],1)
        expected=self.model().payload([{'role':'system','content':'public'},{'role':'user','content':'Hola.'}],1)
        self.assertEqual(expected['messages'][0]['content'],REMINDER_CLARIFICATION_SYSTEM)
        end=1+len(REMINDER_SELECTION_EXAMPLE)
        self.assertEqual(expected['messages'][1:end],REMINDER_SELECTION_EXAMPLE)
        self.assertEqual(baseline['messages'][1:],expected['messages'][end:])
        for old,new in zip(baseline['tools'],expected['tools']):
            if old['function']['name'] not in {'create_reminder','list_reminders','search_web'}:self.assertEqual(old,new)
            else:
                self.assertEqual(old['function']['name'],new['function']['name'])
                self.assertEqual(old['function']['parameters'],new['function']['parameters'])
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key';key.write_text('public-key-'*4);key.with_name('server.pid').write_text('123')
            bodies=[]
            def post(path,body,deadline):
                if path=='/apply-template':bodies.append(body);return {'prompt':'public fixture'}
                if path=='/tokenize':return {'tokens':[1]}
                raise AssertionError('Unexpected inference request')
            opener=MagicMock();opener.open.return_value.__enter__.return_value.status=200
            argv=['prepare-conversation.py',str(key),'--assistant-tools','--guided-agenda','--alarm-lifecycle']
            with patch.object(sys,'argv',argv),patch.object(AssistantToolsModel,'__init__',return_value=None), \
                    patch.object(AssistantToolsModel,'post',side_effect=post),patch.object(AssistantToolsModel,'prepare') as prepare, \
                    patch.object(AssistantToolsModel,'cancel') as cancel,patch('urllib.request.build_opener',return_value=opener), \
                    patch.object(voice,'now_ms',return_value=100000),contextlib.redirect_stdout(io.StringIO()):
                runpy.run_path(str(ROOT/'scripts/prepare-conversation.py'),run_name='__main__')
            runtime=self.model();runtime.enable_reminder_deletion()
            expected_prepared=runtime.payload([{'role':'system','content':'public'},{'role':'user','content':'Hola.'}],1)
            body=dict(bodies[0]);self.assertTrue(body.pop('add_generation_prompt'));self.assertEqual(body,expected_prepared)
            prepare.assert_called_once();cancel.assert_not_called()

    def test_actor_ambiguous_yes_fields_receipt_then_read_same_connection(self):
        h=actor_fixture.ConversationVoiceTests()
        def receipt(events):
            grant=next(e for e in reversed(events) if e['type']=='agenda_execute')
            return [h.control('agenda_result',session_id=grant['session_id'],context_id=grant['context_id'],status='CREATED',
                **{key:grant[key] for key in ('event_id','nonce','event_kind','due_ms','request_turn_id','request_sequence','weekday','minute_of_day')})]
        def read(events):
            grant=next(e for e in reversed(events) if e['type']=='reminder_read')
            return [h.control('reminder_read_result',status='READ',total=1,shown=1,
                **{key:grant[key] for key in ('session_id','context_id','nonce','request_turn_id','request_sequence')})]
        steps=h.utterance()+[(101200,h.ack_last),(103020,[h.pcm(103020)]),(104200,h.ack_last),
            (106020,[h.pcm(106020)]),(107200,h.ack_last),(109020,[h.pcm(109020)]),(109100,receipt),(110200,h.ack_last),
            (112020,[h.pcm(112020)]),(112100,read),(113200,h.ack_last)]
        events,model=h.run_session(steps,assistant_tools=True,guided_agenda=True,continuous_voice=True,alarm_lifecycle=True,
            confirmed_volume=True,reminder_read=True,reminder_clarification=True,
            transcripts=['con un recordatorio','sí','regar las plantas','en cinco minutos','qué recordatorios tengo'],
            answers=[{'kind':'agenda_dialogue_start','event_kind':'REMINDER'},{'kind':'reminder_list_request'}])
        spoken=[e for e in events if e['type']=='say'];self.assertEqual([e['text'] for e in spoken[:3]],[form.REMINDER_INTENT,form.SUBJECT,form.WHEN])
        self.assertEqual(sum(e['type']=='agenda_execute' for e in events),1);self.assertEqual(model.start.call_count,2)
        self.assertEqual(events[0]['reminder_creation_policy'],form.REMINDER_POLICY)
        self.assertEqual(sum(e['type']=='ready' for e in events),1);self.assertFalse(any(e['type']=='conversation_closed' for e in events))
        self.assertEqual(sum(e.get('actions_executed',0) for e in spoken),1)
        self.assertFalse(any(e['type'] in {'timer_execute','volume_execute','alarm_delete_execute'} for e in events))


if __name__=='__main__':unittest.main()
