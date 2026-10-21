import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
import conversation_voice as voice
from continuous_voice import ContextReset,ContinuousVoiceFault,packet
from resident_runtime import ContinuousResidentRuntime,Monitor,Sample
import test_conversation_voice as fixture


class ContinuousVoiceTests(unittest.TestCase):
    def reset_packet(self,event,*,checkpoint=None):
        if checkpoint is None: checkpoint=event['reason']=='turn_checkpoint'
        identity=event['context_id'] if checkpoint else ('e'*32 if event['context_id']!='e'*32 else 'd'*32)
        return dict(action='voice_context',reset_nonce=event['reset_nonce'],previous_session_id=event['session_id'],previous_context_id=event['context_id'],
            context=dict(version=1,id=identity,last_human_ms=0,turns=[]),catalog=[],voice_entry=not checkpoint)

    def context_reply(self,events):
        event=next(event for event in reversed(events) if event['type']=='voice_context_request')
        value=self.reset_packet(event)
        if not hasattr(self,'client_snapshots'): self.client_snapshots={}
        if event['reason']=='turn_checkpoint':
            turns=list(self.client_snapshots.get(event['context_id'],[]))
            for item in events:
                if item['session_id'] != event['session_id'] or item['context_id'] != event['context_id']: continue
                if item['type']=='thinking' and not item.get('administrative'):
                    row=dict(id=item['turn_id'],user=item['text'],human_ms=item['speech_end_ms'])
                    said=next((answer for answer in events if answer['type']=='say' and answer['turn_id']==item['turn_id']),None)
                    if said and not said.get('timer_control'): row['assistant']=said['text']
                    turns.append(row)
            value['context']['turns']=turns[-event['retained_turns']:] if event['retained_turns'] else []
            value['context']['last_human_ms']=event['last_human_ms']
        self.client_snapshots[value['context']['id']]=list(value['context']['turns'])
        return [(5,json.dumps(value).encode())]

    def run_voice(self,steps,**kwargs):
        return fixture.ConversationVoiceTests().run_session(steps,assistant_tools=True,guided_agenda=True,continuous_voice=True,**kwargs)

    def test_context_reset_is_nonce_bound_one_use_deadline_and_client_identity_scoped(self):
        now=[100000]
        for reason in ('dialogue_closed','human_inactivity','turn_checkpoint'):
            reset=ContextReset('a'*32,'b'*32,reason,lambda:now[0])
            event=dict(reset.request(),session_id='a'*32,context_id='b'*32)
            value=self.reset_packet(event)
            snapshot,_,fresh=reset.claim(value)
            self.assertEqual(fresh,reason!='turn_checkpoint')
            self.assertEqual(snapshot['id']=='b'*32,reason=='turn_checkpoint')
            with self.assertRaises(ValueError): reset.claim(value)
        for key,value in [('reset_nonce','f'*32),('previous_session_id','f'*32),('previous_context_id','f'*32),('voice_entry',1),('extra','no')]:
            reset=ContextReset('a'*32,'b'*32,'dialogue_closed',lambda:now[0]); event=dict(reset.request(),session_id='a'*32,context_id='b'*32)
            with self.assertRaises(ValueError): reset.claim(dict(self.reset_packet(event),**{key:value}))
        reset=ContextReset('a'*32,'b'*32,'dialogue_closed',lambda:now[0]); event=dict(reset.request(),session_id='a'*32,context_id='b'*32)
        now[0]+=4000
        with self.assertRaises(ValueError): reset.claim(self.reset_packet(event))
        with self.assertRaises(ValueError): packet(b'{"action":"x","action":"y"}')

    def test_continuous_frame_limit_is_opt_in_and_other_frames_keep_original_bounds(self):
        payload=json.dumps({'public':'x'*50000}).encode(); raw=struct.pack('!BI',5,len(payload))+payload
        def connection(data):
            sock=Mock(); chunks=[data[index:index+4096] for index in range(0,len(data),4096)]
            sock.recv.side_effect=chunks; return sock,chunks
        conn,_=connection(raw)
        with self.assertRaises(ValueError): voice.FramePump(conn).poll()
        conn,chunks=connection(raw); pump=voice.FramePump(conn,continuous_context_controls=True); events=[]
        for _ in chunks: events.extend(pump.poll())
        self.assertEqual(events,[(5,payload)]); self.assertEqual(len(pump.buffer),0)
        conn,_=connection(struct.pack('!BI',4,50000)+b'x'*4096)
        with self.assertRaises(ValueError): voice.FramePump(conn,continuous_context_controls=True).poll()

    def test_more_than_1800_seconds_connected_then_voice_activation_without_button(self):
        harness=fixture.ConversationVoiceTests(); steps=[(at,[harness.control('heartbeat')]) for at in range(100000,2001000,1000)]
        steps += [(2001020,[harness.pcm(2001020)]),(2001040,[harness.pcm(2001040)]),(2002220,harness.ack_last)]
        events,model=self.run_voice(steps,transcripts=['hola'],answer={'kind':'reply','text':'Hola.'},wake_on_pcm=True)
        self.assertEqual(events[0]['protocol'],12); self.assertEqual(events[0]['seconds'],0)
        self.assertEqual(events[0]['voice_lifetime_policy'],'continuous_voice_v1')
        self.assertTrue(any(event.get('trigger')=='voice' for event in events)); self.assertEqual(model.start.call_count,1)
        thinking=next(event for event in events if event['type']=='thinking')
        self.assertLessEqual(thinking['action_window_end_ms']-thinking['speech_end_ms'],300000)
        self.assertFalse(any(event['type']=='conversation_closed' for event in events))

    def test_goodbye_changes_client_context_on_same_channel_then_voice_starts_fresh(self):
        harness=fixture.ConversationVoiceTests()
        steps=[(100000,[harness.pcm(100000)]),(100020,[harness.pcm(100020)]),(101200,harness.ack_last),
            (101300,self.context_reply),(102000,[harness.pcm(102000)]),(102020,[harness.pcm(102020)]),(103200,harness.ack_last)]
        events,model=self.run_voice(steps,transcripts=['gracias','hola'],answer={'kind':'reply','text':'Hola.'},wake_on_pcm=True)
        closed=next(event for event in events if event['type']=='conversation_closed'); self.assertIs(closed['keep_listening'],True)
        ready=next(event for event in events if event['type']=='voice_context_ready')
        self.assertNotEqual(ready['session_id'],closed['session_id']); self.assertNotEqual(ready['context_id'],closed['context_id'])
        self.assertEqual(ready['restored_turns'],0); self.assertEqual(model.start.call_count,1)
        model.purge_cache.assert_called_once()
        self.assertEqual(len(model.observed_contexts[0]),2)
        self.assertEqual(sum(event['type']=='ready' for event in events),1)

    def test_checkpoint_bounds_authorization_ledgers_without_disconnect_after_twenty_turns(self):
        harness=fixture.ConversationVoiceTests(); steps=[]
        for index in range(25):
            at=100000+index*3000
            if index==0: steps.append((at,[harness.control('activate')]))
            steps.append((at+20,[harness.pcm(at+20)])); steps.append((at+1200,harness.ack_last))
            if index==19: steps.append((at+1300,self.context_reply))
        events,model=self.run_voice(steps,transcripts=['hola']*25,answer={'kind':'reply','text':'Hola.'})
        resets=[event for event in events if event['type']=='voice_context_request']
        self.assertEqual(len(resets),1); self.assertEqual(resets[0]['reason'],'turn_checkpoint')
        model.purge_cache.assert_not_called()
        self.assertEqual(model.start.call_count,25); self.assertEqual(sum(event['type']=='say' for event in events),25)
        self.assertGreater(len(model.observed_contexts[20]),2)  # Checkpoint preserves conversation, not stale permissions.
        turns=[event['turn_seq'] for event in events if event['type']=='thinking']
        self.assertEqual(turns,list(range(1,21))+list(range(1,6)))

    def test_more_than_twenty_four_explicit_mutations_use_fresh_scopes_without_replaying_permits(self):
        harness=fixture.ConversationVoiceTests(); steps=[]
        def receipt(events):
            grant=next(event for event in reversed(events) if event['type']=='timer_execute'); proposal=grant['proposal']
            return [harness.control('timer_result',session_id=grant['session_id'],context_id=grant['context_id'],
                proposal_id=proposal['proposal_id'],timer_id=proposal['timer_id'],nonce=proposal['nonce'],seconds=proposal['seconds'],
                request_turn_id=grant['request_turn_id'],request_sequence=grant['request_sequence'],status='CREATED')]
        for index in range(45):
            at=100000+index*3000
            if index==0: steps.append((at,[harness.control('activate')]))
            steps.extend([(at+20,[harness.pcm(at+20)]),(at+100,receipt),(at+1200,harness.ack_last)])
            if index in (19,39): steps.append((at+1300,self.context_reply))
        events,model=self.run_voice(steps,transcripts=['pon un temporizador de dos minutos']*45,answer={'kind':'timer_proposal','seconds':120})
        grants=[event for event in events if event['type']=='timer_execute']
        self.assertEqual(len(grants),45); self.assertEqual(len({grant['proposal']['nonce'] for grant in grants}),45)
        self.assertEqual(len({grant['session_id'] for grant in grants}),3); self.assertEqual(model.start.call_count,45)
        self.assertFalse(any(event['type']=='conversation_closed' for event in events))
        self.assertEqual(sum(event.get('actions_executed',0) for event in events if event['type']=='say'),45)

    def test_form_near_checkpoint_remains_bounded_and_never_grants_from_repeated_invalid_fields(self):
        harness=fixture.ConversationVoiceTests(); steps=[]
        transcripts=['hola']*19+['pon un recordatorio']+['sí']*9
        answers=[{'kind':'reply','text':'Hola.'}]*19+[{'kind':'agenda_dialogue_start','event_kind':'REMINDER'}]
        for index in range(len(transcripts)):
            at=100000+index*3000
            if index==0: steps.append((at,[harness.control('activate')]))
            steps.extend([(at+20,[harness.pcm(at+20)]),(at+1200,harness.ack_last)])
        steps.append((100000+(len(transcripts)-1)*3000+1300,self.context_reply))
        events,model=self.run_voice(steps,transcripts=transcripts,answers=answers)
        self.assertEqual(model.start.call_count,20)
        self.assertFalse(any(event['type'] in {'agenda_execute','timer_execute','volume_execute'} for event in events))
        self.assertTrue(any(event.get('timer_control')=='agenda_cancelled' for event in events))
        self.assertEqual(sum(event['type']=='voice_context_request' for event in events),1)

    def test_stale_tts_and_receipt_controls_during_handoff_are_ignored_not_authorization(self):
        harness=fixture.ConversationVoiceTests()
        def stale_and_reply(events):
            closed=next(event for event in events if event['type']=='conversation_closed')
            return [harness.control('tts_done',turn_id=closed['turn_id']),harness.control('agenda_result',status='CREATED')]+self.context_reply(events)
        events,_=self.run_voice(harness.utterance()+[(101200,harness.ack_last),(101300,stale_and_reply)],transcripts=['gracias'])
        self.assertTrue(any(event['type']=='voice_context_ready' for event in events))
        self.assertFalse(any(event['type']=='agenda_execute' for event in events))

    def test_unheard_or_missing_context_ack_and_lost_stream_fail_closed(self):
        harness=fixture.ConversationVoiceTests()
        with self.assertRaisesRegex(ContinuousVoiceFault,'voice_stream_lost'):
            self.run_voice([(100000,[harness.control('heartbeat')]),(105000,[])])
        with self.assertRaisesRegex(ContinuousVoiceFault,'voice_context_timeout'):
            self.run_voice(harness.utterance()+[(101200,harness.ack_last),(105200,[harness.control('heartbeat')])],transcripts=['gracias'])
        with self.assertRaisesRegex(ContinuousVoiceFault,'voice_protocol_error'):
            self.run_voice([(100000,[(5,b'{}')])])

    def test_inactivity_discards_human_ram_while_connection_survives(self):
        harness=fixture.ConversationVoiceTests()
        def memory_setup(memory,clock):
            memory.last_human=clock[0]-3599000; memory.commit('public old marker','Public old answer.')
        steps=[(4000000,[harness.control('heartbeat')]),(4001000,[harness.control('heartbeat')]),(4001100,self.context_reply)]
        events,model=self.run_voice(steps,initial_ms=4000000,memory_setup=memory_setup)
        request=next(event for event in events if event['type']=='voice_context_request')
        self.assertEqual(request['reason'],'human_inactivity'); self.assertEqual(model.memory.pairs,[])
        model.purge_cache.assert_called_once()
        self.assertIsNone(model.result); model.start.assert_not_called()

    def test_administrative_catalog_is_pruned_even_before_current_chat_selected(self):
        harness=fixture.ConversationVoiceTests(); stored={}
        def entry(clock):
            memory=voice.ShortMemory(lambda:clock[0]); memory.last_human=clock[0]-3599000; memory.commit('public old marker','Public old answer.')
            stored['entry']=voice.ConversationEntry({'a'*32:memory},'b'*32,lambda:clock[0]); return stored['entry']
        self.run_voice([(4000000,[harness.control('heartbeat')]),(4001000,[harness.control('heartbeat')])],initial_ms=4000000,entry=entry)
        self.assertEqual(stored['entry'].catalog,{})

    def test_audio_event_yields_then_rearms_voice_without_new_connection_or_model_stop(self):
        harness=fixture.ConversationVoiceTests()
        steps=[(100000,[harness.control('heartbeat')]),(100100,[harness.control('audio_yield')]),
            (100300,[harness.control('audio_resume')]),(100400,self.context_reply),
            (101100,[harness.pcm(101100)]),(101120,[harness.pcm(101120)]),(102300,harness.ack_last)]
        events,model=self.run_voice(steps,wake_on_pcm=True,transcripts=['hola'],answer={'kind':'reply','text':'Hola.'})
        self.assertEqual(sum(event['type']=='ready' for event in events),1)
        self.assertTrue(any(event.get('state')=='audio_yield' for event in events))
        reset=next(event for event in events if event['type']=='voice_context_request')
        self.assertEqual(reset['reason'],'audio_resume'); model.cancel_generation.assert_called_once()
        self.assertEqual(model.start.call_count,1); self.assertFalse(any(event['type']=='agenda_execute' for event in events))

    def test_audio_yield_racing_context_handoff_does_not_resume_capture_over_event(self):
        harness=fixture.ConversationVoiceTests()
        steps=harness.utterance()+[(101200,harness.ack_last),(101250,[harness.control('audio_yield')]),
            (101300,self.context_reply),(101400,[harness.control('audio_resume')]),(101500,self.context_reply),
            (102200,[harness.pcm(102200)]),(102220,[harness.pcm(102220)]),(103400,harness.ack_last)]
        events,model=self.run_voice(steps,wake_on_pcm=True,transcripts=['gracias','hola'],answer={'kind':'reply','text':'Hola.'})
        ready=[event for event in events if event['type']=='voice_context_ready']
        self.assertEqual([event['audio_yielded'] for event in ready],[True,False])
        self.assertEqual(model.start.call_count,1); self.assertEqual(sum(event['type']=='ready' for event in events),1)

    def test_pending_generation_is_cancelled_not_turned_into_action_when_event_takes_audio(self):
        harness=fixture.ConversationVoiceTests()
        events,model=self.run_voice(harness.utterance()+[(100100,[harness.control('audio_yield')]),
            (100300,[harness.control('audio_resume')]),(100400,self.context_reply)],ready=False,
            transcripts=['pon una alarma en dos minutos'],answer={'kind':'agenda_proposal'})
        model.cancel_generation.assert_called_once()
        self.assertFalse(any(event['type'] in {'agenda_execute','timer_execute','volume_execute'} for event in events))

    def test_checkpoint_cannot_silently_replace_retained_human_turn_count(self):
        reset=ContextReset('a'*32,'b'*32,'turn_checkpoint',lambda:100000,retained_turns=1,last_human_ms=99000)
        event=dict(reset.request(),session_id='a'*32,context_id='b'*32)
        with self.assertRaises(ValueError): reset.claim(self.reset_packet(event))

    def test_authenticated_continuous_session_disconnect_shuts_owned_model_no_reaccept(self):
        clock=lambda:100000; owner=Mock(); owner.alive.return_value=True
        monitor=Monitor(clock,lambda:Sample(100000,1600000,740000,160000,1000,500,240,80,'charging'),None,continuous_model=True)
        runtime=ContinuousResidentRuntime(owner,clock,monitor,enabled=True,cache_purger=lambda model:0)
        server=Mock(); server.__enter__=Mock(return_value=server); server.__exit__=Mock(return_value=False)
        raw=Mock(); raw.__enter__=Mock(return_value=raw); raw.__exit__=Mock(return_value=False); server.accept.return_value=(raw,('127.0.0.1',1))
        conn=Mock(); conn.__enter__=Mock(return_value=conn); conn.__exit__=Mock(return_value=False)
        context=Mock(); context.wrap_socket.return_value=conn
        hello=dict(token='public-key-'*4,protocol=12,mode='assistant_tools',source='synthetic_pcm',web_response_mode='worker_first_v3_spanish',
            phone_volume_policy='absolute_media_v2_turn60',agenda_dialogue_policy='guided_agenda_v1',action_completion_policy='continue_v1',
            voice_lifetime_policy='continuous_voice_v1',dialogue_reset_policy='client_fresh_v1',context=dict(version=1,id='a'*32,last_human_ms=0,turns=[]))
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key'; key.write_text(hello['token'])
            with patch.object(voice.ssl,'SSLContext',return_value=context),patch.object(voice.socket,'socket',return_value=server), \
                    patch.object(voice,'read_hello',return_value=hello),patch.object(voice,'session',return_value='voice_peer_closed') as session,patch.object(voice,'print'), \
                    patch.object(voice,'now_ms',side_effect=clock),patch.object(voice.time,'CLOCK_BOOTTIME',7,create=True), \
                    patch.object(voice.time,'clock_gettime',return_value=100,create=True):
                voice.serve(key,key,Mock(),0,'public-cert','public-key','synthetic_pcm',runtime.check,assistant_tools=True,guided_agenda=True,continuous_voice=True,resident=runtime)
        session.assert_called_once(); owner.stop.assert_called_once(); self.assertTrue(runtime.closed); self.assertEqual(server.accept.call_count,1)
        self.assertEqual(monitor.reason,'voice_peer_closed')


if __name__=='__main__': unittest.main()
