"""Clock fault reconstruction using public synthetic PCM, no phone/model/audio."""
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]; sys.path.insert(0,str(ROOT/'backend'))
from conversation_voice import AudioFreshness,observe_audio_technical
from continuous_voice import ContinuousVoiceFault
import test_conversation_voice as fixture


class AudioClockRecoveryTests(unittest.TestCase):
    def test_all_invalid_clock_reasons_are_quarantined_not_retimestampped_or_watermarks(self):
        for stamp,now,reason in [(98999,100000,1),(100101,100000,2),(99999,100020,3)]:
            with self.subTest(reason=reason):
                value=AudioFreshness(); self.assertTrue(value.accept(100000,100000))
                self.assertFalse(value.accept(stamp,now)); self.assertTrue(value.changed)
                self.assertEqual(value.reason,reason); self.assertEqual(value.last,100000)
                self.assertTrue(value.accept(100040,100040)); self.assertTrue(value.changed)
                self.assertEqual(value.status,2); self.assertEqual(value.rejected,1); self.assertIsNone(value.gap_since)
                self.assertTrue(all(type(v) is int for v in value.snapshot(100040).values()))

    def test_persistent_failure_ends_after_five_seconds_even_if_heartbeats_or_fresh_packet_arrive(self):
        value=AudioFreshness(); value.accept(98000,100000)
        for now in range(100020,105000,20):
            self.assertFalse(value.accept(now-2000,now)); self.assertFalse(value.expired(now))
        self.assertTrue(value.expired(105000)); self.assertFalse(value.accept(105000,105000))
        h=fixture.ConversationVoiceTests()
        with self.assertRaisesRegex(ContinuousVoiceFault,'voice_protocol_error'):
            h.run_session([(100000,[h.pcm(98000)]),(104000,[h.control('heartbeat')]),(105000,[h.control('heartbeat')]),(105001,[])],
                assistant_tools=True,guided_agenda=True,continuous_voice=True)

    def test_after_joke_late_audio_does_not_close_connection_or_create_reminder_from_suffix(self):
        h=fixture.ConversationVoiceTests()
        steps=h.utterance()+[(101200,h.ack_last),(103000,[h.pcm(101900)]),(103020,[h.pcm(103020)]),
            (103100,[h.control('activate')]),(103120,[h.pcm(103120)]),(104300,h.ack_last)]
        events,model=h.run_session(steps,assistant_tools=True,guided_agenda=True,continuous_voice=True,
            transcripts=['cuenta un chiste','pon un recordatorio'],
            answers=[{'kind':'reply','text':'Chiste público.'},{'kind':'agenda_dialogue_start','event_kind':'REMINDER'}])
        self.assertEqual(model.start.call_count,2); self.assertEqual(sum(e['type']=='ready' for e in events),1)
        self.assertTrue(any(e.get('reason')=='audio_clock_resync' for e in events))
        self.assertTrue(any(e.get('timer_control')=='agenda_question' for e in events))
        self.assertFalse(any(e['type'] in {'agenda_execute','timer_execute','volume_execute'} for e in events))

    def test_partial_reminder_form_is_revoked_and_fresh_suffix_cannot_fill_or_execute_it(self):
        h=fixture.ConversationVoiceTests()
        steps=h.utterance()+[(101200,h.ack_last),(103000,[h.pcm(101900)]),(103020,[h.pcm(103020)])]
        events,model=h.run_session(steps,assistant_tools=True,guided_agenda=True,continuous_voice=True,
            transcripts=['pon un recordatorio','en dos minutos'],answer={'kind':'agenda_dialogue_start','event_kind':'REMINDER'})
        self.assertEqual(model.start.call_count,1)
        self.assertFalse(any(e['type'] in {'agenda_execute','timer_execute','volume_execute'} for e in events))

    def test_invalid_audio_during_dispatched_mutation_does_not_cancel_retry_or_confirm_uncertain_result(self):
        h=fixture.ConversationVoiceTests()
        events,model=h.run_session(h.utterance()+[(100100,[h.pcm(98000)]),(100120,[h.pcm(100120)]),(104600,[]),(105200,h.ack_last)],
            assistant_tools=True,guided_agenda=True,continuous_voice=True,
            transcripts=['pon un temporizador de dos minutos'],answer={'kind':'timer_proposal','seconds':120})
        self.assertEqual(sum(e['type']=='timer_execute' for e in events),1)
        self.assertFalse(any(e.get('reason')=='audio_clock_resync' for e in events))
        said=next(e for e in events if e['type']=='say'); self.assertEqual(said['actions_executed'],0)
        self.assertEqual(model.start.call_count,1)

    def test_diagnostics_disabled_do_not_touch_files_or_model(self):
        model=Mock(); value=AudioFreshness(); value.accept(1,100000)
        with patch('resident_runtime.private_directory') as directory:
            observe_audio_technical(model,value,100000)
        directory.assert_not_called(); model.start.assert_not_called(); model.cancel.assert_not_called()

    @unittest.skipUnless(os.name=='posix','directory FD semantics require POSIX')
    def test_fixed_numeric_receipt_private_bounded_and_no_unknown_files_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            work=Path(folder); work.chmod(0o700); model=Mock(technical_work=work); value=AudioFreshness(); value.accept(1,100000)
            observe_audio_technical(model,value,100000); path=work/'voice_audio_health.json'
            receipt=json.loads(path.read_bytes()); self.assertEqual(receipt,value.snapshot(100000))
            self.assertEqual(stat.S_IMODE(path.stat().st_mode),0o600)
            path.unlink(); other=work/'other'; other.write_text('UNKNOWN'); path.symlink_to(other)
            observe_audio_technical(model,value,100020); self.assertTrue(path.is_symlink()); self.assertEqual(other.read_text(),'UNKNOWN')


if __name__=='__main__': unittest.main()
