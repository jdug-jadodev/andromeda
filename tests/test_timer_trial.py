from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from timer_trial import TimerTrial
from interaction_policy import ToolPolicy, TIMER_POLICY
import test_conversation_voice as fixture


class TimerTrialTests(unittest.TestCase):
    def setUp(self):
        self.clock = [100000]; self.timer = TimerTrial(lambda: self.clock[0])
        self.turn = {'id': 'c' * 32, 'seq': 1, 'speech_end': 99900, 'text': 'pon un temporizador de dos minutos'}
        self.session, self.context = 'a' * 32, 'b' * 32

    def authorize(self):
        result = self.timer.model_result({'kind': 'timer_proposal', 'seconds': 120}, self.turn,
                                         self.session, self.context, 190000)
        self.assertTrue(result['executing']); self.assertNotIn('text', result)
        return result['event']

    def packet(self, status='CREATED'):
        grant = self.authorize(); p = grant['proposal']
        return dict(action='timer_result', session_id=self.session, context_id=self.context,
                    proposal_id=p['proposal_id'], timer_id=p['timer_id'], nonce=p['nonce'], seconds=p['seconds'],
                    request_turn_id=grant['request_turn_id'], request_sequence=grant['request_sequence'], status=status)

    def test_action_result_closes_only_after_exact_android_receipt(self):
        packet = self.packet(); result = self.timer.result(packet, self.session, self.context)
        self.assertEqual(result['actions_executed'], 1)
        self.assertEqual(result['text'], 'Temporizador de 120 segundos activado.')
        self.assertEqual(result['close_reason'], 'action_result'); self.assertFalse(result['offered_more'])
        self.assertIsNone(self.timer.result(packet, self.session, self.context))

    def test_failed_uncertain_or_duplicate_result_is_not_success_but_finishes_attempt(self):
        for status in ['DUPLICATE', 'REJECTED', 'FAILED', 'UNKNOWN']:
            self.setUp(); packet = self.packet(status)
            result = self.timer.result(packet, self.session, self.context)
            self.assertEqual(result['actions_executed'], 0); self.assertNotIn('activado', result['text'])
            self.assertEqual(result['close_reason'], 'action_result'); self.assertFalse(result['offered_more'])

    def test_mismatched_result_cannot_acknowledge_another_action(self):
        packet = self.packet()
        for key, value in [('timer_id', 'wrong'), ('seconds', 2), ('seconds', True), ('nonce', 'wrong'),
                           ('request_sequence', '1'), ('request_turn_id', 'wrong'),
                           ('session_id', 'wrong'), ('context_id', 'wrong'), ('status', 'success')]:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                self.timer.result(dict(packet, **{key: value}), self.session, self.context)
        self.assertEqual(self.timer.result(packet, self.session, self.context)['actions_executed'], 1)

    def test_lost_ack_is_unknown_not_false_failure_or_automatic_retry(self):
        self.authorize(); self.assertIsNone(self.timer.timed_out())
        self.clock[0] = 104500
        result = self.timer.timed_out()
        self.assertIn('incierto', result['text']); self.assertNotIn('no he creado', result['text'])
        self.assertEqual(result['close_reason'], 'action_result')
        self.assertIsNone(self.timer.timed_out()); self.assertIsNone(self.timer.inflight)

    def test_tool_effect_and_risk_not_presence_of_tools_determine_lifetime(self):
        self.assertEqual(TIMER_POLICY.route(validated=True, standalone=True), 'execute_close')
        self.assertEqual(TIMER_POLICY.route(validated=True, standalone=False), 'execute_continue')
        self.assertEqual(TIMER_POLICY.route(validated=False, standalone=True), 'clarify')
        self.assertEqual(ToolPolicy('read', 'low').route(validated=True, standalone=True), 'respond_continue')
        self.assertEqual(ToolPolicy('write', 'high').route(validated=True, standalone=True), 'require_confirmation')
        with self.assertRaises(ValueError): ToolPolicy('shell', 'low').route(validated=True, standalone=True)

    def test_request_is_single_use_and_bound_to_original_turn_not_later_yes(self):
        grant = self.authorize()
        self.assertEqual(grant['request_turn_id'], self.turn['id']); self.assertEqual(grant['request_sequence'], 1)
        self.assertEqual(grant['proposal']['expires_ms'], 104000)
        self.assertEqual(grant['execute_before_ms'], 104000)
        with self.assertRaises(ValueError): self.authorize()
        for text in ['sí', 'confirmo temporizador', 'no', 'ayer dije pon un temporizador de dos minutos',
                     'no pongas un temporizador de dos minutos', 'pon un temporizador de dos minutos y dime un chiste']:
            self.setUp(); self.turn['text'] = text
            with self.subTest(text=text), self.assertRaises(ValueError): self.authorize()

    def run_actor(self, tail, transcripts, answer=None):
        harness = fixture.ConversationVoiceTests()
        return harness.run_session(harness.utterance() + tail, transcripts=transcripts,
            answer=answer or {'kind': 'timer_proposal', 'seconds': 120}, timer_trial=True)

    @staticmethod
    def ack(events): return fixture.ConversationVoiceTests().ack_last(events)

    @staticmethod
    def result_event(events):
        grant = next(e for e in reversed(events) if e['type'] == 'timer_execute'); p = grant['proposal']
        return [fixture.ConversationVoiceTests.control('timer_result', session_id=grant['session_id'], context_id=grant['context_id'],
            proposal_id=p['proposal_id'], timer_id=p['timer_id'], nonce=p['nonce'], seconds=p['seconds'],
            request_turn_id=grant['request_turn_id'], request_sequence=grant['request_sequence'], status='CREATED')]

    def test_actor_executes_request_then_speaks_and_closes_without_confirmation_or_offer(self):
        events, model = self.run_actor([(100100, self.result_event), (101000, self.ack)],
                                       ['pon un temporizador de dos minutos'])
        self.assertEqual(events[0]['protocol'], 8); self.assertTrue(events[0]['actions_enabled'])
        self.assertEqual(events[0]['timer_policy'], 'request_authorized_close')
        self.assertEqual(len([e for e in events if e['type'] == 'timer_execute']), 1)
        replies = [e for e in events if e['type'] == 'say']; self.assertEqual(len(replies), 1)
        self.assertEqual(replies[0]['text'], 'Temporizador de 120 segundos activado.')
        self.assertFalse(replies[0]['offered_more']); self.assertEqual(replies[0]['close_reason'], 'action_result')
        self.assertEqual(events[-1]['type'], 'conversation_closed'); self.assertEqual(events[-1]['reason'], 'action_result')
        self.assertEqual(len(model.memory.pairs), 1); self.assertEqual(len(model.memory.pairs[0]), 1)
        self.assertEqual(model.start.call_count, 1); model.cancel.assert_called_once()

    def test_actor_never_speaks_success_without_an_executor_result(self):
        events, _ = self.run_actor([], ['pon un temporizador de dos minutos'])
        self.assertTrue(any(e['type'] == 'timer_execute' for e in events))
        self.assertFalse(any(e['type'] in {'say', 'conversation_closed'} for e in events))

    def test_actor_unheard_result_still_closes_and_does_not_invent_spoken_memory(self):
        h = fixture.ConversationVoiceTests
        def failed(events):
            reply = next(e for e in events if e['type'] == 'say')
            return [h.control('tts_failed', turn_id=reply['turn_id'])]
        events, model = self.run_actor([(100100, self.result_event), (101000, failed)], ['pon un temporizador de dos minutos'])
        self.assertEqual(events[-1]['type'], 'conversation_closed'); self.assertEqual(len(model.memory.pairs[0]), 1)

    def test_queries_and_stories_keep_the_conversation_open(self):
        for text in ['cuéntame un chiste', 'cuéntame una historia', 'qué es una estrella']:
            events, _ = self.run_actor([(101000, self.ack)], [text], answer={'kind': 'reply', 'text': 'Respuesta pública.'})
            self.assertFalse(any(e['type'] in {'timer_execute', 'conversation_closed'} for e in events))
            reply = next(e for e in events if e['type'] == 'say')
            self.assertTrue(reply['offered_more']); self.assertEqual(reply['close_reason'], '')

    def test_interrupted_action_result_closes_without_retry_or_cancellation(self):
        h = fixture.ConversationVoiceTests
        def interrupted(events):
            reply = next(e for e in events if e['type'] == 'say')
            return [h.control('tts_started', turn_id=reply['turn_id'], at_ms=101000),
                    h.control('tts_interrupted', turn_id=reply['turn_id'])]
        events, model = self.run_actor([(100100, self.result_event), (101000, interrupted)], ['pon un temporizador de dos minutos'])
        self.assertTrue(events[0]['speech_interrupt'])
        self.assertEqual(events[-1]['type'], 'conversation_closed')
        self.assertEqual(events[-1]['reason'], 'action_result')
        self.assertEqual(len([e for e in events if e['type'] == 'timer_execute']), 1)
        self.assertFalse(any(e.get('trigger') == 'followup' for e in events))
        self.assertEqual(len(model.memory.pairs[0]), 1)

    def test_incomplete_or_mixed_request_asks_and_does_not_close_or_execute(self):
        for text, reason in [('pon un temporizador', 'missing_duration'),
                             ('pon un temporizador de dos minutos y dime un chiste', 'invalid_request')]:
            events, _ = self.run_actor([(101000, self.ack)], [text], answer={'kind': 'timer_rejected', 'reason': reason})
            self.assertFalse(any(e['type'] in {'timer_execute', 'conversation_closed'} for e in events))
            self.assertEqual(next(e for e in events if e['type'] == 'say')['close_reason'], '')

    def test_long_utterance_is_rejected_whole_not_truncated_before_qualifier(self):
        text = 'pon un temporizador de dos minutos' + ' ' * 240 + 'no lo hagas'
        events, model = self.run_actor([], [text])
        self.assertTrue(any(e['type'] == 'error' and e['reason'] == 'request_too_long' for e in events))
        self.assertFalse(any(e['type'] in {'thinking', 'timer_execute', 'say'} for e in events))
        model.start.assert_not_called(); model.cancel.assert_called_once()


if __name__ == '__main__': unittest.main()
