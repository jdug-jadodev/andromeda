"""Cold-turn boundaries and recovery: fake media/clock only, no Android or real grants."""
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
import conversation_voice as voice
from phone_volume import VolumeTrial
from continuous_voice import ContinuousVoiceFault
import test_conversation_voice as fixture


class VolumeTurnRecoveryTests(unittest.TestCase):
    def test_new_negotiated_volume_window_accepts_cold_selection_but_grant_is_still_four_seconds(self):
        turn=dict(id='c'*32,seq=1,speech_end=100000,text='sube el volumen al cien por ciento')
        with self.assertRaises(ValueError): VolumeTrial(lambda:135000).authorize({'percent':100},turn,'a'*32,'b'*32,400000)
        grant=VolumeTrial(lambda:135000,60000).authorize({'percent':100},turn,'a'*32,'b'*32,400000)['event']
        self.assertEqual(grant['execute_before_ms'],139000)
        self.assertEqual(grant['speech_end_ms'],100000)
        with self.assertRaises(ValueError): VolumeTrial(lambda:156001,60000).authorize({'percent':100},turn,'a'*32,'b'*32,400000)
        with self.assertRaises(ValueError): VolumeTrial(lambda:135000,60000).authorize({'percent':20},turn,'a'*32,'b'*32,400000)
        with self.assertRaises(ValueError): VolumeTrial(lambda:100000,90000)

    def test_expired_generation_releases_turn_and_accepts_next_request_on_same_session(self):
        h=fixture.ConversationVoiceTests(); steps=h.utterance()
        steps += [(at,[h.control('heartbeat')]) for at in range(101000,124000,1000)]
        steps += [(124000,[]),(124620,[h.pcm(124620)]),(125800,h.ack_last)]
        events,model=h.run_session(steps,assistant_tools=True,guided_agenda=True,continuous_voice=True,
            ready=[False,True],transcripts=['petición pública uno','hola'],answer={'kind':'reply','text':'Hola.'})
        errors=[item for item in events if item['type']=='error']
        self.assertEqual(errors[0]['reason'],'deadline'); self.assertTrue(errors[0]['recoverable']); self.assertEqual(errors[0]['actions_executed'],0)
        self.assertEqual(sum(item['type']=='ready' for item in events),1)
        self.assertEqual(model.start.call_count,2); model.cancel_generation.assert_called_once()
        self.assertTrue(any(item['type']=='say' and 'Hola' in item['text'] for item in events))
        self.assertFalse(any(item['type'] in {'volume_execute','agenda_execute','timer_execute'} for item in events))

    def test_stale_form_is_revoked_without_turning_into_transport_failure_or_action(self):
        h=fixture.ConversationVoiceTests()
        with patch('assistant_tools_trial.AssistantToolsTrial.human',side_effect=[ValueError('PRIVATE_NOT_EXPORTED'),None]):
            events,model=h.run_session(h.utterance()+[(101020,[h.pcm(101020)]),(102200,h.ack_last)],
                assistant_tools=True,guided_agenda=True,continuous_voice=True,
                transcripts=['pon una alarma','hola'],answer={'kind':'reply','text':'Hola.'})
        error=next(item for item in events if item['type']=='error')
        self.assertEqual(error['reason'],'invalid_request'); self.assertTrue(error['recoverable'])
        self.assertNotIn('PRIVATE_NOT_EXPORTED',json.dumps(events)); self.assertEqual(model.start.call_count,1)
        self.assertTrue(any(item['type']=='say' for item in events)); self.assertFalse(any(item['type']=='agenda_execute' for item in events))

    def test_numeric_protocol_location_never_contains_exception_message_or_locals(self):
        try: raise ValueError('PRIVATE_CURRENT_REQUEST')
        except ValueError as error:
            with patch.object(voice,'now_ms',return_value=100000): receipt=voice.protocol_technical(error)
        self.assertEqual(set(receipt),{'version','at_ms','error','frames'})
        self.assertNotIn('PRIVATE',json.dumps(receipt)); self.assertLessEqual(len(receipt['frames']),8)

    def test_android_recovery_refuses_stale_errors_and_any_dispatched_mutation(self):
        source=(ROOT/'android-voice/src/local/doja/voice/VoiceService.java').read_text()
        branch=source[source.index('} else if ("error".equals(type))'):source.index('} else if ("conversation_closed".equals(type))')]
        for expected in ('conversationTurn.matches(', 'mutationRequested', 'speechStarted(', 'actions_executed', 'recoverable'):
            self.assertIn(expected,branch)
        self.assertIn('voice_failure_origin',(ROOT/'android-voice/src/local/doja/voice/VoiceHealthStore.java').read_text())


if __name__=='__main__': unittest.main()
