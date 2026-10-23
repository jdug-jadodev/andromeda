import csv
import contextlib
import io
import json
from pathlib import Path
import sys
import unittest
import runpy
import tempfile
from unittest.mock import MagicMock,patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
import agenda_dialogue as form
from assistant_tools_model import AssistantToolsModel, TOOLS
from assistant_tools_trial import AssistantToolsTrial, result_text
from test_assistant_tools import native


def turn(text, sequence):
    now = 100000 + (sequence - 1) * 3000
    return dict(id=f'{sequence:032x}', seq=sequence, text=text, speech_end=now-100,
                calendar_boot_ms=now, calendar_anchor_ms=1792328400000 + (sequence-1)*3000)


class AgendaDialogueTests(unittest.TestCase):
    def test_guided_preparation_warms_the_same_native_payload_as_runtime_without_model_or_network(self):
        for guided in (False,True):
            with self.subTest(guided=guided), tempfile.TemporaryDirectory() as directory:
                key=Path(directory)/'key'; key.write_text('public-fixture-'*3); key.with_name('server.pid').write_text('123')
                arguments=['prepare-conversation.py',str(key),'--assistant-tools']+(['--guided-agenda'] if guided else [])
                model=MagicMock(); model.payload.return_value={'messages':[]}; model.post.side_effect=[{'prompt':'public'},{'tokens':[1]}]
                opener=MagicMock(); opener.open.return_value.__enter__.return_value.status=200
                with patch.object(sys,'argv',arguments),patch('assistant_tools_model.AssistantToolsModel',return_value=model), \
                        patch('urllib.request.build_opener',return_value=opener),patch('conversation_voice.now_ms',return_value=100000),contextlib.redirect_stdout(io.StringIO()):
                    runpy.run_path(str(ROOT/'scripts/prepare-conversation.py'),run_name='__main__')
                self.assertEqual(model.enable_agenda_dialogue.call_count,int(guided)); model.prepare.assert_called_once()
                self.assertEqual(model.payload.call_args.args[1],1)

    def test_shared_exact_receipt_speech(self):
        with (ROOT/'tests/agenda_receipt_cases.tsv').open(encoding='utf-8',newline='') as stream:
            for row in csv.DictReader(stream,delimiter='\t'):
                with self.subTest(status=row['status'],detailed=row['detailed']):
                    self.assertEqual(result_text(row['status'],row['kind'],int(row['due_ms']),row['text'],int(row['delay_ms']),row['detailed']=='1'),row['expected'])
    def test_shared_python_android_human_traces(self):
        previous = None
        with (ROOT/'tests/agenda_dialogue_cases.tsv').open(encoding='utf-8', newline='') as stream:
            for row in csv.DictReader(stream, delimiter='\t'):
                if previous != row['case']: draft = form.AgendaDialogue(); previous = row['case']
                human = turn(row['human'], int(row['step'])); now = human['calendar_boot_ms']
                def step():
                    return draft.begin(row['kind'], human, now) if int(row['step']) == 1 else draft.answer(human, now)
                with self.subTest(case=row['case'], step=row['step']):
                    if row['expected'] == 'INVALID':
                        with self.assertRaises(ValueError): step()
                        continue
                    result = step()
                    if row['expected'] == 'none': self.assertIsNone(result); continue
                    expected = {'question': 'agenda_question', 'proposal': 'guided_agenda_proposal', 'cancelled': 'agenda_cancelled'}[row['expected']]
                    self.assertEqual(result['kind'], expected)
                    self.assertEqual(result['text'], getattr(form, row['text'], row['text']))
                    if result['kind'] == 'guided_agenda_proposal':
                        value = draft.claim(result, human, now)
                        self.assertGreater(value['due_ms'], human['calendar_anchor_ms']); self.assertFalse(draft.active)
                        with self.assertRaises(ValueError): draft.claim(result, human, now)
                    if row['heard'] != '-': draft.spoken(human['id'], row['heard'] == '1')

    def test_selected_native_kind_required_missing_call_never_opens_form(self):
        from test_assistant_tools import AssistantToolsTests
        model = AssistantToolsTests().model(); model.enable_agenda_dialogue()
        result = model.interpret_result(native('create_reminder'), 'tool_calls', 'pon un recordatorio')
        self.assertEqual(result, {'kind': 'agenda_dialogue_start', 'event_kind': 'REMINDER'})
        for message, finish, text in [({'content':'¿Para qué es el recordatorio?'}, 'stop', 'pon un recordatorio'),
                (native('create_alarm'), 'tool_calls', 'pon un recordatorio'),
                (native('create_reminder', text='inventado'), 'tool_calls', 'pon un recordatorio'),
                (native('create_reminder'), 'tool_calls', 'ayer dije pon un recordatorio')]:
            self.assertEqual(model.interpret_result(message, finish, text)['kind'], 'tools_rejected')
        body = model.payload([{'role':'system','content':'old'}, {'role':'user','content':'pon un recordatorio'}])
        self.assertEqual(body['tool_choice'], 'auto'); self.assertEqual(len(body['tools']), len(TOOLS))
        self.assertEqual(body['max_tokens'], 96); self.assertFalse(body['parallel_tool_calls'])

    def test_questions_grant_nothing_and_receipt_continues_only_in_negotiated_mode(self):
        clock = [100000]; trial = AssistantToolsTrial(lambda: clock[0], guided_agenda=True)
        first = turn('pon un recordatorio', 1)
        response = trial.model_result({'kind':'agenda_dialogue_start','event_kind':'REMINDER'}, first, 'a'*32, 'b'*32, 400000)
        self.assertEqual(response['text'], form.SUBJECT); self.assertNotIn('event', response); self.assertIsNone(trial.inflight)
        trial.spoken(first['id'], True)
        body = turn('lavar la loza', 2); clock[0] = body['calendar_boot_ms']
        response = trial.human(body, 'a'*32, 'b'*32, 400000)
        self.assertEqual(response['text'], form.WHEN); self.assertNotIn('event', response)
        trial.spoken(body['id'], True)
        final = turn('en cinco minutos', 3); clock[0] = final['calendar_boot_ms']
        response = trial.human(final, 'a'*32, 'b'*32, 400000); grant = response['event']
        self.assertTrue(response['executing']); self.assertEqual(grant['text'], 'lavar la loza')
        self.assertEqual(grant['dialogue_origin_turn_id'], first['id']); self.assertEqual(grant['request_turn_id'], final['id'])
        self.assertEqual(grant['due_ms'], final['calendar_anchor_ms']+300000); self.assertEqual(grant['delay_ms'], 300000)
        self.assertEqual(len(trial.consumed), 1); self.assertFalse(trial.dialogue.active)
        packet = {key:grant[key] for key in ('event_id','nonce','event_kind','due_ms','request_turn_id','request_sequence')}
        packet.update(action='agenda_result', session_id='a'*32, context_id='b'*32, status='CREATED')
        self.assertIsNotNone(trial.inflight)
        result = trial.result(packet, 'a'*32, 'b'*32)
        self.assertEqual(result['actions_executed'], 1); self.assertEqual(result['close_reason'], '')
        self.assertTrue(result['offered_more']); self.assertTrue(result['text'].endswith('¿Necesitas algo más?'))
        self.assertIn('Recordatorio guardado: lavar la loza.', result['text']); self.assertIn('En 5 minutos', result['text'])
        self.assertIsNone(trial.result(packet, 'a'*32, 'b'*32))

    def test_foreign_payload_turn_replay_and_expired_forms_never_authorize(self):
        def ready():
            draft = form.AgendaDialogue(); first = turn('recuérdame lavar la loza', 1)
            draft.begin('REMINDER', first, 100000); draft.spoken(first['id'], True)
            final = turn('en cinco minutos', 2); proposal = draft.answer(final, 103000)
            return draft, final, proposal
        for key, value in [('text','otro contenido'), ('due_ms',True), ('dialogue_origin_turn_id','f'*32), ('dialogue_final_sequence',True)]:
            draft, final, proposal = ready()
            with self.assertRaises(ValueError): draft.claim(dict(proposal, **{key:value}), final, 103000)
        draft, final, proposal = ready()
        with self.assertRaises(ValueError): draft.claim(proposal, dict(final,id='f'*32), 103000)
        draft, final, proposal = ready()
        with self.assertRaises(ValueError): draft.claim(proposal, final, 500000)
        draft, final, proposal = ready(); draft.clear()
        with self.assertRaises(ValueError): draft.claim(proposal, final, 103000)
        self.assertNotIn('lavar', json.dumps(draft.__dict__))

    def test_native_initial_selection_may_take_longer_than_one_second_without_invalidating_question(self):
        draft = form.AgendaDialogue(); first = turn('pon un recordatorio', 1)
        self.assertEqual(draft.begin('REMINDER', first, 130000)['text'], form.SUBJECT)
        draft.spoken(first['id'], True)
        next_turn = dict(turn('lavar la loza', 2), speech_end=131900, calendar_boot_ms=132000)
        self.assertEqual(draft.answer(next_turn,132000)['text'], form.WHEN)
        self.assertEqual(form.subject('lavar la loza en la cocina'), 'lavar la loza en la cocina')
        with self.assertRaises(ValueError): form.subject('a'*159+'\U0001f600')

    def test_actor_guided_reminder_then_web_without_closure_or_second_native_selection_for_form_fields(self):
        from test_conversation_voice import ConversationVoiceTests
        harness = ConversationVoiceTests()
        def receipt(events):
            grant = next(event for event in events if event['type'] == 'agenda_execute')
            return [harness.control('agenda_result',session_id=grant['session_id'],context_id=grant['context_id'],status='CREATED',
                **{key:grant[key] for key in ('event_id','nonce','event_kind','due_ms','request_turn_id','request_sequence')})]
        steps = harness.utterance() + [(101200,harness.ack_last), (103020,[harness.pcm(103020)]),
            (104200,harness.ack_last), (106020,[harness.pcm(106020)]), (106100,receipt),
            (107200,harness.ack_last), (109020,[harness.pcm(109020)]), (110200,harness.ack_last)]
        events, model = harness.run_session(steps,transcripts=['pon un recordatorio','lavar la loza','en cinco minutos','busca en Internet nebulosas'],
            assistant_tools=True,guided_agenda=True,answers=[{'kind':'agenda_dialogue_start','event_kind':'REMINDER'},
                {'kind':'web_result','text':'Respuesta pública de prueba.','sources':[]}])
        self.assertEqual(events[0]['protocol'],11); self.assertEqual(events[0]['agenda_dialogue_policy'],'guided_agenda_v1')
        spoken = [event for event in events if event['type']=='say']
        self.assertEqual([event['timer_control'] for event in spoken],['agenda_question','agenda_question','agenda_result','web'])
        self.assertEqual([event['actions_executed'] for event in spoken],[0,0,1,0])
        self.assertFalse(any(event['type']=='conversation_closed' for event in events))
        self.assertEqual(sum(event['type']=='agenda_execute' for event in events),1); self.assertEqual(model.start.call_count,2)

    def test_interrupted_question_cannot_authorize_following_field(self):
        from test_conversation_voice import ConversationVoiceTests
        harness=ConversationVoiceTests()
        def interrupt(events):
            event=next(event for event in events if event['type']=='say')
            return [harness.control('tts_started',turn_id=event['turn_id'],at_ms=101200),harness.control('tts_interrupted',turn_id=event['turn_id'])]
        events,model=harness.run_session(harness.utterance()+[(101200,interrupt),(103020,[harness.pcm(103020)])],
            transcripts=['pon un recordatorio','lavar la loza'],assistant_tools=True,guided_agenda=True,
            answers=[{'kind':'agenda_dialogue_start','event_kind':'REMINDER'},{'kind':'tools_rejected'}])
        self.assertFalse(any(event['type']=='agenda_execute' for event in events)); self.assertEqual(model.start.call_count,2)


if __name__ == '__main__': unittest.main()
