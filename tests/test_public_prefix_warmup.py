from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]; sys.path.insert(0,str(ROOT/'backend'))
import conversation_voice as voice
from continuous_voice import ContinuousVoiceFault
import test_conversation_voice as fixture
import test_continuous_voice as continuous_fixture


class PublicPrefixWarmupTests(unittest.TestCase):
    def model(self,folder):
        key=Path(folder)/'key'; key.write_text('x'*32)
        return voice.ConversationModel(key,owned=Mock(),clock=lambda:100000)

    def test_public_warmup_uses_prepare_only_and_does_not_read_or_commit_human_memory(self):
        with tempfile.TemporaryDirectory() as folder:
            model=self.model(folder); model.memory=Mock(); model.prepare=Mock()
            model.warm_public_prefix(); model.thread.join(1)
            model.prepare.assert_called_once(); model.memory.messages.assert_not_called(); model.memory.commit.assert_not_called()
            self.assertIsNone(model.result); self.assertIsNone(model.public_prefix_error)
            self.assertFalse(model.done.is_set()); model.finish_public_prefix(); self.assertFalse(model._public_preparing)
            model.owned.stop.assert_not_called()

    def test_warmup_is_cancelled_before_human_generation_without_stopping_owner_or_retry(self):
        with tempfile.TemporaryDirectory() as folder:
            model=self.model(folder); entered=threading.Event()
            def prepare(): entered.set(); model.cancelled.wait(1)
            model.prepare=Mock(side_effect=prepare); model.warm_public_prefix(); self.assertTrue(entered.wait(1))
            model.finish_public_prefix(); self.assertFalse(model.thread.is_alive()); self.assertFalse(model._public_preparing)
            model.owned.stop.assert_not_called(); model.prepare.assert_called_once()

    def test_stuck_cancellation_is_not_recovered_or_restarted(self):
        with tempfile.TemporaryDirectory() as folder:
            model=self.model(folder); model._public_preparing=True
            with patch.object(model,'cancel_generation',return_value=False),self.assertRaises(ContinuousVoiceFault) as fault:
                model.finish_public_prefix()
            self.assertEqual(fault.exception.reason,'voice_cancellation_stuck'); model.owned.stop.assert_not_called()

    def test_warm_failure_does_not_claim_a_human_result_or_change_deadlines(self):
        with tempfile.TemporaryDirectory() as folder:
            model=self.model(folder); model.prepare=Mock(side_effect=TimeoutError)
            model.warm_public_prefix(); model.thread.join(1)
            self.assertEqual(model.public_prefix_error,'TimeoutError'); self.assertIsNone(model.result)
            self.assertEqual(model.turn_deadline(99000,400000),122000)
            model.finish_public_prefix(); model.owned.stop.assert_not_called()

    def test_only_fresh_reset_starts_public_warm_after_handoff_not_before_erasure(self):
        h=fixture.ConversationVoiceTests(); controls=continuous_fixture.ContinuousVoiceTests()
        events,model=h.run_session(h.utterance()+[(101200,h.ack_last),(101300,controls.context_reply)],
            assistant_tools=True,guided_agenda=True,continuous_voice=True,reminder_read=True,transcripts=['gracias'])
        model.purge_cache.assert_called_once(); model.warm_public_prefix.assert_called_once()
        self.assertTrue(any(event['type']=='voice_context_ready' for event in events))


if __name__=='__main__': unittest.main()
