import copy
import json
from pathlib import Path
import sys
import unittest
import tempfile
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from timer_proposals import TIMER_TOOL, TimerProposals, literal_seconds, selected_timer_seconds
from timer_trial_model import TIMER_SYSTEM, TimerTrialModel, rejection_reply

REQUEST = 'pon un temporizador de dos minutos'
SESSION, CONTEXT, ORIGIN, CONFIRM = ['a' * 32, 'b' * 32, 'c' * 32, 'd' * 32]


class TimerProposalTests(unittest.TestCase):
    def setUp(self):
        self.clock = [100000]; self.manager = TimerProposals(lambda: self.clock[0])

    def offer(self, **changes):
        args = dict(seconds=120, request=REQUEST, session_id=SESSION, context_id=CONTEXT,
                    turn_id=ORIGIN, sequence=1, speech_end=99900, session_end=400000)
        args.update(changes); return self.manager.offer(**args)

    def respond(self, text='confirmo temporizador', **changes):
        args = dict(session_id=SESSION, context_id=CONTEXT, turn_id=CONFIRM, sequence=2,
                    speech_end=99950, session_end=400000)
        args.update(changes); return self.manager.respond(text, **args)

    def test_shared_literal_cases(self):
        cases = Path(__file__).with_name('timer_request_cases.tsv').read_text(encoding='utf-8').splitlines()
        self.assertEqual(len(cases), 52)
        for line in cases:
            expected, text = line.split('\t', 1)
            with self.subTest(text=text):
                if expected == '-':
                    with self.assertRaises(ValueError): literal_seconds(text)
                else: self.assertEqual(literal_seconds(text), int(expected))

    @staticmethod
    def native(duration='dos minutos'):
        return {'tool_calls': [{'type': 'function', 'function': {'name': 'create_timer', 'arguments': {'duration_text': duration}}}]}

    def test_catalog_only_allows_creating_one_timer(self):
        self.assertEqual(TIMER_TOOL['function']['name'], 'create_timer')
        self.assertFalse(TIMER_TOOL['function']['parameters']['additionalProperties'])
        self.assertEqual(selected_timer_seconds(self.native(), 'tool_calls', REQUEST), 120)

    def test_timer_prompt_stays_compact_without_forcing_calls_or_increasing_limits(self):
        self.assertLessEqual(len(TIMER_SYSTEM), 650)  # Size regression, not a latency guarantee.
        for term in ['timer', 'temporizador', 'cuenta atrás', 'duration_text', 'negadas', 'citadas', 'hipotéticas']:
            self.assertIn(term, TIMER_SYSTEM)
        payload = TimerTrialModel.payload([{'role': 'system', 'content': 'unused'}, {'role': 'user', 'content': 'Hola.'}])
        self.assertEqual(payload['tool_choice'], 'auto'); self.assertEqual(payload['max_tokens'], 96)
        self.assertFalse(payload['parallel_tool_calls'])

    def test_timer_alias_and_ten_seconds_keep_native_selection_and_actual_result_boundary(self):
        model = TimerTrialModel.__new__(TimerTrialModel)
        for request in ['pon un timer de diez segundos', 'pon un timer de 10 segundos', 'pon un temporizador de diez segundos']:
            model.context_metrics = {}
            self.assertEqual(model.interpret_result(self.native('diez segundos'), 'tool_calls', request),
                             {'kind': 'timer_proposal', 'seconds': 10})
            self.assertEqual(model.context_metrics, {'timer_model_choice': 'native', 'timer_model_kind': 'timer_proposal', 'timer_validation': 'none'})
            self.assertEqual(model.interpret_result({'content': 'No puedo crear timers.'}, 'stop', request),
                             {'kind': 'timer_rejected', 'reason': 'invalid_call'})
            self.assertEqual(model.context_metrics, {'timer_model_choice': 'text', 'timer_model_kind': 'timer_rejected', 'timer_validation': 'invalid_call'})
            self.assertIn('La herramienta está disponible', rejection_reply({'kind': 'timer_rejected', 'reason': 'invalid_call'}))

    def test_timer_alias_cannot_erase_negation_quote_or_mixed_request(self):
        model = TimerTrialModel.__new__(TimerTrialModel)
        for request in ['no crees un timer de diez segundos', 'ayer dije pon un timer de diez segundos',
                        'pon un timer de diez segundos y no lo hagas', 'pon un timer de diez segundos y dime un chiste']:
            self.assertEqual(model.interpret_result(self.native('diez segundos'), 'tool_calls', request),
                             {'kind': 'timer_rejected', 'reason': 'invalid_request'})

    def test_model_cannot_invent_duration_or_change_unit(self):
        for value in [True, '120', 'tres minutos', '120 segundos', 2, 121, 0, 86401, 120.0]:
            with self.subTest(value=value), self.assertRaises(ValueError): selected_timer_seconds(self.native(value), 'stop', REQUEST)

    def test_model_call_does_not_override_negation_quote_or_missing_duration(self):
        for request in ['no pongas un temporizador de dos minutos', 'ayer dije pon un temporizador de dos minutos',
                        'pon un temporizador', 'quiero otro igual']:
            with self.subTest(request=request), self.assertRaises(ValueError): selected_timer_seconds(self.native(), 'stop', request)

    def test_unknown_multiple_extra_or_truncated_calls_fail(self):
        unknown = self.native(); unknown['tool_calls'][0]['function']['name'] = 'cancel_timers'
        extra = self.native(); extra['tool_calls'][0]['function']['arguments']['shell'] = 'never execute'
        multiple = self.native(); multiple['tool_calls'] *= 2
        duplicate = self.native(); duplicate['tool_calls'][0]['function']['arguments'] = '{"duration_text":"una hora","duration_text":"dos minutos"}'
        for message, reason in [(unknown, 'stop'), (extra, 'stop'), (multiple, 'stop'), (duplicate, 'stop'),
                                (self.native(), 'length'), ({'content': 'Temporizador creado'}, 'stop')]:
            with self.subTest(message=message, reason=reason), self.assertRaises(ValueError): selected_timer_seconds(message, reason, REQUEST)

    def test_native_json_arguments_are_data_only(self):
        message = self.native(); message['tool_calls'][0]['function']['arguments'] = '{"duration_text":"dos minutos"}'
        self.assertEqual(selected_timer_seconds(message, 'stop', REQUEST), 120)
        message['tool_calls'][0]['function']['arguments'] = '__import__("os").system("never")'
        with self.assertRaises(ValueError): selected_timer_seconds(message, 'stop', REQUEST)

    def test_proposal_does_not_itself_authorize_and_requires_spoken_offer(self):
        proposal = self.offer()
        self.assertEqual(self.respond()['kind'], 'offer_not_spoken')
        self.assertTrue(self.manager.spoken(proposal.proposal_id, ORIGIN))
        self.assertFalse(self.manager.spoken(proposal.proposal_id, ORIGIN))
        grant = self.respond()
        self.assertEqual(grant['kind'], 'authorized'); self.assertEqual(grant['proposal']['seconds'], 120)
        self.assertEqual(grant['execute_before_ms'], 109950)
        self.assertEqual(self.respond()['kind'], 'none')

    def test_generic_yes_never_authorizes_a_timer(self):
        proposal = self.offer(); self.manager.spoken(proposal.proposal_id, ORIGIN)
        for text in ['sí', 'confirmo', 'adelante']:
            self.assertEqual(self.respond(text)['kind'], 'explicit_confirmation_required')
        self.assertEqual(self.respond()['kind'], 'authorized')

    def test_confirmation_is_bound_to_session_chat_turn_sequence(self):
        proposal = self.offer(); self.manager.spoken(proposal.proposal_id, ORIGIN)
        for change in [dict(session_id='e' * 32), dict(context_id='e' * 32), dict(turn_id=ORIGIN),
                       dict(sequence=1), dict(sequence=True), dict(turn_id='wrong'), dict(speech_end=100101)]:
            with self.subTest(change=change), self.assertRaises(ValueError): self.respond(**change)
        self.assertEqual(self.respond()['kind'], 'authorized')

    def test_decline_and_new_question_cancel_without_authorization(self):
        for text in ['no', 'gracias', 'cierra la conversación', 'cómo te llamas']:
            self.manager = TimerProposals(lambda: self.clock[0]); p = self.offer(); self.manager.spoken(p.proposal_id, ORIGIN)
            self.assertIn(self.respond(text)['kind'], {'cancelled', 'unrelated'})
            self.assertEqual(self.respond()['kind'], 'none')

    def test_expired_proposal_and_late_confirmation_do_not_authorize(self):
        p = self.offer(); self.manager.spoken(p.proposal_id, ORIGIN)
        self.clock[0] = p.expires_ms
        self.assertEqual(self.respond()['kind'], 'expired')
        self.clock[0] = 100000; self.manager = TimerProposals(lambda: self.clock[0])
        p = self.offer(); self.manager.spoken(p.proposal_id, ORIGIN)
        self.assertEqual(self.respond(speech_end=89999)['kind'], 'expired')

    def test_pending_proposal_cannot_be_replaced_or_revived_after_close(self):
        self.offer()
        with self.assertRaises(ValueError): self.offer()
        self.manager.close()
        with self.assertRaises(ValueError): self.offer()
        self.assertEqual(self.respond()['kind'], 'none')

    def test_failed_or_wrong_offer_receipt_is_not_delivery(self):
        p = self.offer()
        self.assertFalse(self.manager.spoken('e' * 32, ORIGIN))
        self.assertFalse(self.manager.spoken(p.proposal_id, CONFIRM))
        self.assertEqual(self.respond()['kind'], 'offer_not_spoken')

    def test_session_end_caps_offer_and_execution_deadline(self):
        p = self.offer(session_end=110000)
        self.assertEqual(p.expires_ms, 108500); self.manager.spoken(p.proposal_id, ORIGIN)
        grant = self.respond(session_end=105000)
        self.assertEqual(grant['execute_before_ms'], 103500)

    def test_proposal_model_counts_native_tool_catalog_in_same_token_budget(self):
        with tempfile.TemporaryDirectory() as directory:
            key = Path(directory) / 'key'; key.write_text('x' * 32)
            model = TimerTrialModel(key, owned=Mock(), clock=lambda: 100000); requests = []
            def post(path, body, deadline):
                requests.append((path, copy.deepcopy(body), deadline))
                if path == '/apply-template': return {'prompt': 'native tool template'}
                if path == '/tokenize': return {'tokens': list(range(300))}
                return {'choices': [{'message': self.native(), 'finish_reason': 'tool_calls'}]}
            with patch.object(model, 'post', side_effect=post):
                deadline = model.start(REQUEST, 99000, 400000); model.thread.join(2)
            self.assertIsNone(model.error); self.assertEqual(model.result, {'kind': 'timer_proposal', 'seconds': 120})
            self.assertEqual(deadline, 122000); self.assertTrue(all(r[2] == deadline for r in requests))
            for path, body, _ in requests:
                if path == '/tokenize': continue
                self.assertEqual([v['function']['name'] for v in body['tools']], ['create_timer'])
                self.assertFalse(body['parallel_tool_calls']); self.assertEqual(body['max_tokens'], 96)
            self.assertNotIn('tools', model.memory.messages('hola')[0])

    def test_proposal_model_rejects_hallucinated_duration_before_any_confirmation(self):
        model = TimerTrialModel.__new__(TimerTrialModel)
        self.assertEqual(model.interpret_result(self.native('diez minutos'), 'tool_calls', REQUEST),
                         {'kind': 'timer_rejected', 'reason': 'duration_mismatch'})
        self.assertEqual(model.interpret_result({'content': 'He creado un temporizador.'}, 'stop', REQUEST),
                         {'kind': 'timer_rejected', 'reason': 'invalid_call'})
        self.assertEqual(model.interpret_result({'content': 'Listo, en dos minutos.'}, 'stop', REQUEST),
                         {'kind': 'timer_rejected', 'reason': 'invalid_call'})
        self.assertEqual(model.interpret_result({'content': 'Hola.'}, 'stop', 'hola'), {'kind': 'reply', 'text': 'Hola.'})

    def test_model_prose_cannot_ignore_the_action_part_of_a_mixed_request(self):
        model = TimerTrialModel.__new__(TimerTrialModel)
        result = model.interpret_result({'content': 'Listo. Aquí tienes un chiste.'}, 'stop',
                                        'pon un temporizador de dos minutos y cuéntame un chiste')
        self.assertEqual(result, {'kind': 'timer_rejected', 'reason': 'invalid_request'})
        self.assertIn('separa otras órdenes o preguntas', rejection_reply(result))
        self.assertEqual(model.interpret_result({'content': 'Respuesta generada.'}, 'stop', 'cuéntame un chiste'),
                         {'kind': 'reply', 'text': 'Respuesta generada.'})

    def test_real_device_errors_request_clarification_without_repairing_arguments(self):
        model = TimerTrialModel.__new__(TimerTrialModel)
        for case in json.loads(Path(__file__).with_name('timer_model_rejections.json').read_text(encoding='utf-8'))['cases']:
            original = copy.deepcopy(case['message'])
            with self.subTest(case=case['id']):
                result = model.interpret_result(case['message'], case['finish_reason'], case['request'])
                self.assertEqual(result, {'kind': 'timer_rejected', 'reason': 'invalid_call' if case['message'].get('tool_calls') else case['expected_reason']})
                self.assertEqual(case['message'], original)
                self.assertTrue(rejection_reply(result).startswith('No he creado ningún temporizador'))
                self.assertNotIn('seconds', result)
        with self.assertRaises(ValueError): rejection_reply({'kind': 'timer_proposal', 'seconds': 10})

    def test_literal_contract_never_accepts_a_partial_duration_or_inferred_missing_value(self):
        model = TimerTrialModel.__new__(TimerTrialModel)
        for request, duration, reason in [
                ('crea un temporizador de una hora y dos minutos', 'una hora', 'duration_mismatch'),
                ('ayer dije pon un temporizador de dos minutos', 'dos minutos', 'invalid_request'),
                ('pon un temporizador', 'diez segundos', 'missing_duration')]:
            self.assertEqual(model.interpret_result(self.native(duration), 'tool_calls', request),
                             {'kind': 'timer_rejected', 'reason': reason})
        self.assertEqual(selected_timer_seconds(self.native('una hora y dos minutos'), 'tool_calls',
                                               'crea un temporizador de una hora y dos minutos'), 3720)
        self.assertEqual(selected_timer_seconds(self.native('diez minutos'), 'tool_calls',
                                               'pon un temporizador de diez minutos'), 600)

    def test_real_literal_calls_normalize_number_notation_without_unit_conversion(self):
        model = TimerTrialModel.__new__(TimerTrialModel)
        for case in json.loads(Path(__file__).with_name('timer_model_literal_cases.json').read_text(encoding='utf-8'))['cases']:
            message = self.native(case['duration_text']); original = copy.deepcopy(message)
            result = model.interpret_result(message, 'tool_calls', case['request'])
            expected = ({'kind': 'timer_rejected', 'reason': case['rejected']} if 'rejected' in case
                        else {'kind': 'timer_proposal', 'seconds': case['seconds']})
            self.assertEqual(result, expected); self.assertEqual(message, original)
        for duration in ['120 segundos', '1 minuto y 60 segundos', '2 minutos y 1 segundo', '2 horas',
                         'dos', '2 minutos mañana', '2 minutos o 3 minutos', '2 minutos 0 segundos']:
            self.assertEqual(model.interpret_result(self.native(duration), 'tool_calls', REQUEST)['kind'], 'timer_rejected')

    def test_timer_adapter_is_not_enabled_by_default_voice_launcher(self):
        root = Path(__file__).resolve().parents[1]
        self.assertIn('TRIAL=${3:-conversation}', (root / 'scripts/start-andromeda-conversation.sh').read_text(encoding='utf-8'))
        self.assertIn('timer_trial=False', (root / 'backend/conversation_voice.py').read_text(encoding='utf-8'))

    def test_direct_authorization_is_same_turn_single_use_and_short_lived(self):
        grant = self.manager.authorize_request(120, REQUEST, SESSION, CONTEXT, ORIGIN, 1, 99900, 400000)
        self.assertEqual(grant['kind'], 'request_authorized'); self.assertEqual(grant['request_turn_id'], ORIGIN)
        self.assertEqual(grant['request_sequence'], 1); self.assertEqual(grant['execute_before_ms'], 104000)
        self.assertEqual(grant['proposal']['expires_ms'], 104000); self.assertIsNone(self.manager.pending)
        with self.assertRaises(ValueError): self.manager.authorize_request(120, REQUEST, SESSION, CONTEXT, ORIGIN, 1, 99900, 400000)

    def test_direct_authorization_reserves_session_speech_budget_and_refuses_late_or_incomplete(self):
        grant = self.manager.authorize_request(120, REQUEST, SESSION, CONTEXT, ORIGIN, 1, 99900, 107000)
        self.assertEqual(grant['execute_before_ms'], 102000)
        for text, speech_end, end in [('sí', 99900, 190000), ('confirmo temporizador', 99900, 190000),
                                      ('pon un temporizador', 99900, 190000), (REQUEST, 73600, 190000),
                                      (REQUEST, 99900, 105200)]:
            self.setUp()
            with self.subTest(text=text, end=end), self.assertRaises(ValueError):
                self.manager.authorize_request(120, text, SESSION, CONTEXT, ORIGIN, 1, speech_end, end)


if __name__ == '__main__': unittest.main()
