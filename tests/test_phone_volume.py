import csv
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
from phone_volume import VolumeTrial, current_volume, result_text
from assistant_tools_model import AssistantToolsModel
from assistant_tools_trial import AssistantToolsTrial
from conversation_voice import reject_unsupported_claims
import test_assistant_tools as fixtures

native = fixtures.native


class PhoneVolumeTests(unittest.TestCase):
    def turn(self): return dict(id='c'*32, seq=1, speech_end=99900, text='baja el volumen a la mitad')

    def test_half_is_described_to_native_model_without_new_fields_or_forced_routing(self):
        payload=AssistantToolsModel.payload([{'role':'system','content':'fixture'},{'role':'user','content':self.turn()['text']}])
        self.assertEqual(payload['tool_choice'],'auto'); self.assertFalse(payload['parallel_tool_calls'])
        self.assertEqual([tool['function']['name'] for tool in payload['tools']],
            ['create_timer','create_alarm','create_reminder','search_web','set_phone_volume'])
        spec=payload['tools'][-1]['function']
        self.assertIn('a la mitad',spec['description']); self.assertIn('sin argumentos',spec['description'].lower())
        self.assertIn('cien por ciento',spec['description']); self.assertIn('veinte por ciento',spec['description'])
        self.assertEqual(spec['parameters'],{'type':'object','properties':{}})
        self.assertEqual(payload['messages'][-1]['content'],self.turn()['text'])

    def test_described_half_is_not_normalized_into_an_action_after_missing_native_call(self):
        model=fixtures.AssistantToolsTests().model()
        with patch('assistant_tools_model.current_volume') as parser:
            result=model.interpret_result({'content':'Volumen reducido a la mitad.'},'stop',self.turn()['text'])
        self.assertEqual(result['kind'],'tools_rejected'); parser.assert_not_called()
        self.assertEqual(model.context_metrics['tools_validation'],'missing_native_call')

    def test_shared_whole_request_grammar(self):
        with (ROOT/'tests/phone_volume_cases.tsv').open(encoding='utf-8', newline='') as stream:
            for row in csv.DictReader(stream, delimiter='\t'):
                with self.subTest(human=row['human']):
                    if row['percent'] == 'INVALID':
                        with self.assertRaises(ValueError): current_volume(row['human'])
                    else: self.assertEqual(current_volume(row['human']), int(row['percent']))
        for human in (None, '', 'a'*251, 'pon el volumen al 50\x00', 'pon el volumen al <|50|>'):
            with self.assertRaises(ValueError): current_volume(human)

    def test_only_exact_native_selection_can_propose_never_execute_or_search(self):
        model = fixtures.AssistantToolsTests().model()
        human = self.turn()['text']
        with patch('assistant_tools_model.buscar_en_internet') as search:
            self.assertEqual(model.interpret_result(native('set_phone_volume'), 'tool_calls', human), dict(kind='volume_proposal', percent=50))
            for message, finish in ((native('set_phone_volume', percent='50'), 'tool_calls'),
                    (native('set_phone_volume'), 'length'), ({'content':'Listo, volumen al 50 %.'}, 'stop')):
                with patch('assistant_tools_model.current_volume') as parser:
                    self.assertEqual(model.interpret_result(message, finish, human)['kind'], 'tools_rejected')
                    parser.assert_not_called()
            search.assert_not_called(); model.post.assert_not_called()

    def test_volume_after_web_never_uses_the_tool_free_followup_path(self):
        for human in (self.turn()['text'], 'no bajes el volumen', 'sube el volumen al cien'):
            self.assertTrue(AssistantToolsModel.requires_native_selection(human))
        for claim in ('He bajado el volumen.', 'Volumen ajustado al 50 %.', 'Volumen al 50 %.'):
            with self.assertRaises(ValueError): reject_unsupported_claims(claim)

    def test_new_explicit_volume_verbs_require_native_call_too(self):
        for human in ('súbele el volumen al cien por ciento','bájale el volumen al veinte','suba el volumen al cien',
                      'baje el volumen al veinte','ponle el volumen al cien'):
            model=fixtures.AssistantToolsTests().model()
            with patch('assistant_tools_model.current_volume') as parser:
                self.assertEqual(model.interpret_result({'content':'De acuerdo.'},'stop',human)['kind'],'tools_rejected')
                parser.assert_not_called()
            self.assertEqual(model.context_metrics['tools_validation'],'missing_native_call')
            self.assertTrue(AssistantToolsModel.requires_native_selection(human))

    def authorize(self):
        self.clock = [100000]; trial = VolumeTrial(lambda:self.clock[0]); turn = self.turn()
        grant = trial.authorize(dict(percent=50), turn, 'a'*32, 'b'*32, 190000)['event']
        packet = {k:grant[k] for k in ('session_id','context_id','nonce','request_turn_id','request_sequence','percent','stream')}
        packet.update(action='volume_result', status='APPLIED', actual_percent=53)
        return trial, turn, grant, packet

    def test_receipt_is_typed_current_bound_and_informs_real_quantized_level(self):
        trial, turn, grant, packet = self.authorize()
        self.assertEqual(grant['execute_before_ms'], 104000)
        for key, val in (('percent',True),('nonce','bad'),('stream','alarm'),('actual_percent',True),('actual_percent',101),('status','OK')):
            with self.assertRaises(ValueError): trial.result(dict(packet, **{key:val}), 'a'*32, 'b'*32)
        reply = trial.result(packet, 'a'*32, 'b'*32)
        self.assertEqual(reply['text'], result_text('APPLIED',50,53)); self.assertIn('53 %',reply['text'])
        self.assertEqual(reply['actions_executed'],1); self.assertFalse(reply['offered_more'])
        self.assertIsNone(trial.result(packet,'a'*32,'b'*32))
        with self.assertRaises(ValueError): trial.authorize(dict(percent=50),turn,'a'*32,'b'*32,190000)

    def test_deadline_cancel_limit_and_unknown_never_retry(self):
        trial, turn, grant, packet = self.authorize()
        self.clock[0] = grant['execute_before_ms']+500
        response=trial.timed_out(); self.assertEqual(response['actions_executed'],0)
        self.assertIn('No pude confirmar',response['text']); self.assertIsNone(trial.timed_out())
        self.assertIsNone(trial.result(packet,'a'*32,'b'*32))
        for changes in ({'percent':True},{'percent':51}):
            with self.assertRaises(ValueError): VolumeTrial(lambda:100000).authorize(changes,turn,'a'*32,'b'*32,190000)
        with self.assertRaises(ValueError): VolumeTrial(lambda:126000).authorize(dict(percent=50),turn,'a'*32,'b'*32,190000)
        trial.close()
        with self.assertRaises(ValueError): trial.authorize(dict(percent=50),dict(turn,id='d'*32),'a'*32,'b'*32,190000)

    def test_cross_tool_single_mutation_rule(self):
        trial=AssistantToolsTrial(lambda:100000); turn=self.turn()
        trial.model_result(dict(kind='volume_proposal',percent=50),turn,'a'*32,'b'*32,190000)
        for kind in ('timer_proposal','agenda_proposal','volume_proposal'):
            with self.assertRaises(ValueError): trial.model_result(dict(kind=kind,percent=50),turn,'a'*32,'b'*32,190000)

    def test_actor_requires_receipt_before_speech_and_closes_without_conversation_claim(self):
        from test_conversation_voice import ConversationVoiceTests
        h=ConversationVoiceTests()
        def ack(events):
            g=next(e for e in events if e['type']=='volume_execute')
            return [h.control('volume_result',status='APPLIED',actual_percent=53,
                **{k:g[k] for k in ('session_id','context_id','nonce','request_turn_id','request_sequence','percent','stream')})]
        events,model=h.run_session(h.utterance()+[(100100,ack),(101000,h.ack_last)],
            transcripts=[self.turn()['text']],answer=dict(kind='volume_proposal',percent=50),assistant_tools=True)
        self.assertEqual(events[0]['protocol'],10); self.assertEqual(events[0]['phone_volume_policy'],'absolute_media_v1')
        self.assertEqual(sum(e['type']=='volume_execute' for e in events),1)
        self.assertIn('53 %',next(e for e in events if e['type']=='say')['text'])
        self.assertEqual(events[-1]['type'],'conversation_closed'); self.assertEqual(len(model.memory.pairs[0]),1)
        events,_=h.run_session(h.utterance(),transcripts=[self.turn()['text']],answer=dict(kind='volume_proposal',percent=50),assistant_tools=True)
        self.assertFalse(any(e['type']=='say' for e in events))

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
    def test_java_authorization_fake_audio_and_shared_receipts(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture=Path(folder)/'receipts.tsv'
            fixture.write_text('\n'.join(f'{s}\t{p}\t{a}\t{result_text(s,p,a)}' for s,p,a in
                [('APPLIED',50,53),('APPLIED',0,0),('UNCHANGED',100,100),('LIMITED',100,73),('REJECTED',50,-1),('UNKNOWN',50,-1)]),encoding='utf-8')
            source=ROOT/'android-voice/src/local/doja/voice'
            run=subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(source/'TimerRequestGuard.java'),
                str(source/'VolumeDialogue.java'),str(source/'PhoneVolumePolicy.java'),str(ROOT/'android-voice/tests/PhoneVolumePolicyTest.java')],capture_output=True,timeout=30)
            self.assertEqual(run.returncode,0,run.stderr.decode(errors='replace'))
            run=subprocess.run(['java','-cp',folder,'local.doja.voice.PhoneVolumePolicyTest',str(ROOT/'tests/phone_volume_cases.tsv'),str(fixture)],capture_output=True,timeout=20)
            self.assertEqual(run.returncode,0,run.stderr.decode(errors='replace'))


if __name__=='__main__': unittest.main()
