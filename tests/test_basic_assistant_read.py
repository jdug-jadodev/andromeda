import csv
import json
from pathlib import Path
import sys
import threading
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]; sys.path.insert(0,str(ROOT/'backend'))
from reminder_read import ReminderRead,current_read,ACK,UNAVAILABLE
from assistant_tools_model import AssistantToolsModel,BASIC_SYSTEM,GENERAL_FOLLOWUP_SYSTEM,TOOLS
from assistant_tools_trial import AssistantToolsTrial
from conversation_voice import ShortMemory
import test_assistant_tools as fixture
import test_conversation_voice as actor

A,B,C=[char*32 for char in 'abc']
def turn(text='¿Qué recordatorios tengo?'): return dict(id=C,seq=1,speech_end=99900,text=text)


class BasicAssistantReadTests(unittest.TestCase):
    def test_shared_read_scope_never_routes_or_mutates(self):
        with(ROOT/'tests/reminder_read_cases.tsv').open(encoding='utf-8',newline='') as stream:
            for row in csv.DictReader(stream,delimiter='\t'):
                with self.subTest(human=row['human']):
                    if row['allowed']=='YES': self.assertTrue(current_read(row['human']))
                    else:
                        with self.assertRaises(ValueError): current_read(row['human'])

    def model(self):
        value=fixture.AssistantToolsTests().model(); value.enable_agenda_dialogue(); value.enable_confirmed_volume(); value.enable_basic_assistant(); return value

    def test_new_catalog_has_actual_read_tool_and_general_reply_instructions_without_modifying_legacy(self):
        model=self.model(); body=model.payload([{'role':'system','content':'public'},{'role':'user','content':'cuánto es 2+2'}])
        self.assertEqual(body['messages'][0]['content'],BASIC_SYSTEM)
        self.assertIn('Answer in Spanish',body['messages'][0]['content'])
        self.assertIn('You HAVE the listed tools',body['messages'][0]['content'])
        self.assertIn('CALL the relevant function',body['messages'][0]['content'])
        self.assertIn('not quotes, web data or history',body['messages'][0]['content'])
        self.assertEqual(body['tools'][-1]['function']['name'],'list_reminders')
        self.assertEqual(body['tools'][-1]['function']['parameters'],{'type':'object','properties':{}})
        self.assertEqual(body['tool_choice'],'auto'); self.assertFalse(body['parallel_tool_calls'])
        self.assertEqual(body['max_tokens'],96); self.assertEqual(len(TOOLS),5)
        self.assertEqual(len(AssistantToolsModel.payload(body['messages'])['tools']),5)

    def test_only_selected_negotiated_zero_argument_read_request_is_accepted(self):
        model=self.model()
        self.assertEqual(model.interpret_result(fixture.native('list_reminders'),'tool_calls',turn()['text']),{'kind':'reminder_list_request'})
        for message in (fixture.native('list_reminders',query=turn()['text']),{'content':'Tienes tres recordatorios.'}):
            self.assertEqual(model.interpret_result(message,'tool_calls' if message.get('tool_calls') else 'stop',turn()['text'])['kind'],'tools_rejected')
        old=fixture.AssistantToolsTests().model()
        self.assertEqual(old.interpret_result(fixture.native('list_reminders'),'tool_calls',turn()['text'])['kind'],'tools_rejected')
        model.post.assert_not_called()

    def test_web_continuation_can_answer_math_without_enabling_tools_or_changing_translator(self):
        model=self.model(); model.cancelled=threading.Event(); seen=[]
        def post(path,body,deadline):
            seen.append((path,body))
            if path=='/apply-template': return {'prompt':'PUBLIC'}
            if path=='/tokenize': return {'tokens':[1]}
            return {'choices':[{'message':{'content':'Dos más dos son cuatro.'},'finish_reason':'stop'}]}
        model.post=Mock(side_effect=post)
        result=model.continuation('cuánto es 2+2',('qué es una nebulosa','Una nube de gas y polvo.'),[],110000)
        self.assertEqual(result['text'],'Dos más dos son cuatro.')
        self.assertEqual(len(seen),3)
        for _,body in seen:
            self.assertNotIn('tools',body)
            if 'messages' in body: self.assertEqual(body['messages'][0]['content'],GENERAL_FOLLOWUP_SYSTEM)

    def test_basic_math_after_web_does_not_require_web_evidence_or_second_generation(self):
        model=self.model(); model.memory=ShortMemory(lambda:100000); model.memory.last_human=99900
        model.memory.commit_web('busca en Internet nebulosas','Una nebulosa es una nube de gas y polvo.')
        with patch('assistant_tools_model.followup') as second:
            self.assertEqual(model.interpret_result({'content':'Dos más dos son cuatro.'},'stop','cuánto es 2+2'),
                {'kind':'reply','text':'Dos más dos son cuatro.'})
        second.assert_not_called(); model.post.assert_not_called()
        self.assertTrue(model.mathematical_question('cuánto es dos más dos'))
        self.assertFalse(model.mathematical_question('qué es una nebulosa'))

    def authorize(self):
        self.clock=[100000]; read=ReminderRead(lambda:self.clock[0]); grant=read.authorize(turn(),A,B,400000)['event']
        packet={key:grant[key] for key in ('session_id','context_id','nonce','request_turn_id','request_sequence')}
        packet.update(action='reminder_read_result',status='READ',total=2,shown=2)
        return read,grant,packet

    def test_receipt_counts_are_current_bound_no_content_or_mutation_confirmation(self):
        read,grant,packet=self.authorize()
        for key,value in (('nonce','bad'),('request_sequence',True),('total',True),('shown',3),('status','CREATED')):
            with self.assertRaises(ValueError): read.result(dict(packet,**{key:value}),A,B)
        with self.assertRaises(ValueError): read.result(dict(packet,text='PRIVATE_NOTE'),A,B)
        reply=read.result(packet,A,B)
        self.assertEqual(reply['text'],ACK); self.assertEqual(reply['actions_executed'],0)
        self.assertNotIn('text',reply['reminder_read']); self.assertNotIn('PRIVATE',json.dumps(reply))
        self.assertIsNone(read.result(packet,A,B))
        with self.assertRaises(ValueError): read.authorize(turn(),A,B,400000)

    def test_timeout_cannot_claim_empty_or_successful_list_and_never_retries(self):
        read,grant,packet=self.authorize(); self.clock[0]=grant['read_before_ms']+500
        reply=read.timed_out(); self.assertEqual(reply['text'],UNAVAILABLE)
        self.assertEqual(reply['reminder_read']['status'],'TIMEOUT'); self.assertEqual(reply['actions_executed'],0)
        self.assertIsNone(read.timed_out()); self.assertIsNone(read.result(packet,A,B))

    def test_full_actor_reads_then_math_on_same_connection_no_execution_or_content_transfer(self):
        h=actor.ConversationVoiceTests()
        def android(events):
            read=next(e for e in reversed(events) if e['type']=='reminder_read')
            return [h.control('reminder_read_result',status='READ',total=2,shown=2,
                **{key:read[key] for key in ('session_id','context_id','nonce','request_turn_id','request_sequence')})]
        events,model=h.run_session(h.utterance()+[(100100,android),(101200,h.ack_last),(102020,[h.pcm(102020)]),(103200,h.ack_last)],
            assistant_tools=True,guided_agenda=True,continuous_voice=True,reminder_read=True,
            transcripts=['qué recordatorios tengo','cuánto es 2+2'],answers=[{'kind':'reminder_list_request'},{'kind':'reply','text':'Cuatro.'}])
        self.assertIn('list_reminders',events[0]['allowed_tools']); self.assertEqual(events[0]['reminder_read_policy'],'android_reminder_read_v1')
        self.assertEqual(sum(e['type']=='reminder_read' for e in events),1)
        self.assertFalse(any(e['type'] in {'timer_execute','agenda_execute','volume_execute'} for e in events))
        answer=next(e for e in events if e.get('timer_control')=='reminder_list')
        self.assertEqual(answer['text'],ACK); self.assertEqual(answer['reminder_read']['total'],2)
        self.assertTrue(any(e['type']=='say' and 'Cuatro' in e['text'] for e in events)); self.assertEqual(sum(e['type']=='ready' for e in events),1)


if __name__=='__main__': unittest.main()
