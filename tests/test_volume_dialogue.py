import csv
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]; sys.path.insert(0,str(ROOT/'backend'))
from volume_dialogue import VolumeDialogue,Human,candidate,confirm,ASK,CANCELLED
from phone_volume import VolumeTrial
from assistant_tools_trial import AssistantToolsTrial
import test_assistant_tools as tools_fixture
import test_conversation_voice as voice_fixture

A,B,C,D=[char*32 for char in 'abcd']
def turn(text='ponlo al veinte porciento',id=A,seq=1,end=99900):
    return dict(id=id,seq=seq,text=text,speech_end=end)


class VolumeDialogueTests(unittest.TestCase):
    def form(self,text='ponlo al veinte porciento'):
        value=VolumeDialogue(); self.assertEqual(value.begin(turn(text),100000)['kind'],'volume_question'); return value

    def test_shared_human_field_fixtures_are_not_execution_authorization(self):
        with(ROOT/'tests/volume_dialogue_cases.tsv').open(encoding='utf-8',newline='') as source:
            for row in csv.DictReader(source,delimiter='\t'):
                with self.subTest(human=row['human']):
                    if row['candidate']=='INVALID':
                        with self.assertRaises(ValueError): candidate(row['human'])
                    else: self.assertEqual(candidate(row['human']),None if row['candidate']=='NONE' else int(row['candidate']))
        for row in ('¿Me puedes bajar el volumen al veinte por ciento?', 'ponlo al veinte porciento'):
            result=self.form(row).prepared; self.assertIsNone(result)

    def test_native_choice_only_opens_form_and_does_not_use_literal_sentence_guard(self):
        model=tools_fixture.AssistantToolsTests().model(); model.enable_confirmed_volume()
        with patch('assistant_tools_model.current_volume') as legacy:
            self.assertEqual(model.interpret_result(tools_fixture.native('set_phone_volume'),'tool_calls',
                '¿Me puedes bajar el volumen al veinte por ciento?'),{'kind':'volume_dialogue_start'})
        legacy.assert_not_called(); self.assertEqual(model.context_metrics['tools_validation'],'volume_question')

    def test_no_execution_before_heard_question_or_from_model_data(self):
        form=self.form()
        self.assertEqual(form.answer(turn('sí',B,2,100200),100220)['kind'],'volume_cancelled')
        with self.assertRaises(ValueError): form.claim({'percent':20},turn('sí',B,2,100200),100220)
        trial=VolumeTrial(lambda:100000,60000,True)
        with self.assertRaises(ValueError): trial.authorize({'percent':20},turn(),A,B,400000)
        with self.assertRaises(ValueError): trial.authorize_confirmed({'percent':20},turn(),A,B,400000)

    def test_current_heard_human_yes_creates_one_bound_short_grant(self):
        clock=[100000]; trial=VolumeTrial(lambda:clock[0],60000,True)
        trial.dialogue.begin(turn(),clock[0]); trial.dialogue.spoken(A,True,100100)
        clock[0]=100220; human=turn('sí por favor',B,2,100200); result=trial.dialogue.answer(human,clock[0])
        grant=trial.authorize_confirmed(result,human,A,C,400000)['event']
        self.assertEqual(grant['percent'],20); self.assertEqual(grant['request_turn_id'],B)
        self.assertEqual(grant['volume_origin_turn_id'],A); self.assertEqual(grant['volume_question_turn_id'],A)
        self.assertEqual(grant['execute_before_ms'],104220)
        with self.assertRaises(ValueError): trial.authorize_confirmed(result,human,A,C,400000)

    def test_missing_number_and_yes_never_invent_one(self):
        form=self.form('bájalo un poco'); form.spoken(A,True,100100)
        self.assertEqual(form.answer(turn('sí',B,2,100200),100220)['text'],ASK)
        form.spoken(B,True,100300)
        self.assertEqual(form.answer(turn('veinte por ciento',C,3,100400),100420)['text'],confirm(20))
        self.assertEqual(form.origin,C); self.assertIsNone(form.prepared)

    def test_correction_requires_new_heard_question_and_old_callbacks_do_not_arm_it(self):
        form=self.form(); form.spoken(A,True,100100)
        self.assertEqual(form.answer(turn('treinta por ciento',B,2,100200),100220)['text'],confirm(30))
        form.spoken(A,True,100300)
        self.assertEqual(form.answer(turn('sí',C,3,100400),100420)['kind'],'volume_cancelled')
        form=self.form(); form.spoken(A,True,100100); form.answer(turn('treinta por ciento',B,2,100200),100220)
        form.spoken(B,True,100300); human=turn('correcto',C,3,100400); result=form.answer(human,100420)
        self.assertEqual(form.claim(result,human,100420)['percent'],30)

    def test_cancel_switch_stale_unheard_and_future_or_foreign_turns_never_grant(self):
        for answer in ('no','no gracias','cancela el ajuste','sí pero no lo hagas','no lo cambies'):
            form=self.form(); form.spoken(A,True,100100)
            result=form.answer(turn(answer,B,2,100200),100220)
            self.assertFalse(result and result['kind']=='confirmed_volume_proposal')
        form=self.form(); form.spoken(A,False,100100); self.assertIsNone(form.answer(turn('sí',B,2,100200),100220))
        form=self.form(); form.spoken(A,True,100100)
        self.assertIsNone(form.answer(turn('busca en Internet nebulosas',B,2,100200),100220)); self.assertFalse(form.active)
        for end,now in ((100050,100220),(400000,400001)):
            form=self.form(); form.spoken(A,True,100100)
            self.assertEqual(form.answer(turn('sí',B,2,end),now)['kind'],'volume_cancelled')
        with self.assertRaises(ValueError): Human.from_turn(turn('sí',B,65,100200),100220,64)

    def test_tampered_amount_or_provenance_cannot_skip_confirmation(self):
        form=self.form(); form.spoken(A,True,100100); human=turn('sí',B,2,100200); result=form.answer(human,100220)
        for key,value in (('percent',100),('volume_origin_turn_id',C),('volume_question_turn_id',C),('request_turn_id',C),('request_sequence',3)):
            with self.assertRaises(ValueError): form.claim(dict(result,**{key:value}),human,100220)
        self.assertEqual(form.claim(result,human,100220)['percent'],20)

    def test_natural_affirmations_remain_bound_and_mixed_or_negated_yes_never_executes(self):
        for answer in ('Sí, por favor','sí hazlo','confirmo','dale','adelante'):
            form=self.form(); form.spoken(A,True,100100)
            self.assertEqual(form.answer(turn(answer,B,2,100200),100220)['kind'],'confirmed_volume_proposal')
        for answer in ('sí pero no','sí y busca en Internet nebulosas','si digo si que pasa','di «sí»'):
            form=self.form(); form.spoken(A,True,100100); result=form.answer(turn(answer,B,2,100200),100220)
            self.assertFalse(result and result['kind']=='confirmed_volume_proposal')
        model=tools_fixture.AssistantToolsTests().model(); model.enable_confirmed_volume()
        self.assertEqual(model.interpret_result(tools_fixture.native('set_phone_volume',percent='20'),'tool_calls','baja el volumen al veinte')['kind'],'tools_rejected')

    def test_full_actor_question_yes_android_receipt_then_another_tool_same_connection(self):
        h=voice_fixture.ConversationVoiceTests()
        def android(events):
            grant=next(e for e in reversed(events) if e['type']=='volume_execute')
            return [h.control('volume_result',status='APPLIED',actual_percent=20,
                **{key:grant[key] for key in ('session_id','context_id','nonce','request_turn_id','request_sequence','percent','stream')})]
        steps=h.utterance()+[(101200,h.ack_last),(102020,[h.pcm(102020)]),(102100,android),
            (103200,h.ack_last),(104020,[h.pcm(104020)]),(105200,h.ack_last)]
        events,model=h.run_session(steps,assistant_tools=True,guided_agenda=True,continuous_voice=True,confirmed_volume=True,
            transcripts=['¿Me puedes bajar el volumen al veinte porciento?','sí','hola'],
            answers=[{'kind':'volume_dialogue_start'},{'kind':'reply','text':'Hola.'}])
        self.assertEqual(events[0]['phone_volume_policy'],'confirmed_media_v3')
        grants=[e for e in events if e['type']=='volume_execute']; self.assertEqual(len(grants),1)
        self.assertEqual(grants[0]['percent'],20); self.assertEqual(model.start.call_count,2)
        self.assertEqual(sum(e['type']=='ready' for e in events),1)
        self.assertTrue(any(e.get('timer_control')=='volume_question' for e in events))
        self.assertTrue(any(e.get('timer_control')=='volume_result' and '20 %' in e['text'] for e in events))
        self.assertFalse(any(e['type']=='error' for e in events))


if __name__=='__main__': unittest.main()
