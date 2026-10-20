"""Offline integration/privacy gates, not acoustic/ASR acceptance or Android execution."""
from pathlib import Path
import json
import unittest

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'android-voice/src/local/doja/voice'


class InterruptionLifecycleTests(unittest.TestCase):
    def source(self,name): return (SOURCE/name).read_text(encoding='utf-8')
    def test_speech_arms_before_speak_and_tts_callback_activates_existing_ticket(self):
        text=self.source('VoiceService.java')
        apply=text[text.index('    void apply('):text.index('    void finishSpeech()')]
        self.assertIn('activeInterruption=silenceListener.arm(',apply)
        self.assertIn('tts.speak(spokenMessage',apply)
        self.assertIn('silenceListener.outputStarted(activeInterruption)',text)
        self.assertNotIn('silenceListener.begin(',text)
        self.assertIn('public void onStop(',text)
        self.assertIn('if(!stopped)',text)
        self.assertIn('SpeechDiagnostics.End.STOP_FAILED',text)
    def test_no_partial_lowered_confidence_forced_grammar_or_audio_export(self):
        text=self.source('SilenceListener.java')
        self.assertNotIn('getPartialResult',text)
        self.assertNotIn('setGrammar',text)
        self.assertNotIn('getFinalResult',text)
        self.assertIn('if (endpoint)',text)
        for forbidden in ['FileOutputStream','java.net.','MessageDigest','Log.','TimerStore','VoiceConnection']:
            self.assertNotIn(forbidden,text)
        self.assertIn('SilenceRecognition.confidence(',text)
        self.assertIn('confidence<.8',self.source('SilenceWindowPolicy.java'))
    def test_callbacks_and_cleanup_use_ticket_identity_not_just_recycled_strings(self):
        text=self.source('SilenceListener.java')
        self.assertIn('policy.claimCallback(ticket',text)
        self.assertIn('policy.claimFailure(handle.ticket)',text)
        self.assertIn('policy.end(handle.ticket)',text)
        self.assertIn('new ArrayBlockingQueue<Runnable>(2)',text)
        self.assertIn('worker.awaitTermination',text)
        self.assertIn('stopCaptureAsync(activeHandle)',text)
        self.assertIn('handle.stopRequested.get()',text)
        self.assertNotIn('seen.size() >= 32',self.source('SilenceWindowPolicy.java'))
        self.assertIn('current==ticket',self.source('SilenceWindowPolicy.java'))
    def test_audio_freshness_and_os_silencing_fail_without_capture_retries(self):
        text=self.source('SilenceListener.java')
        self.assertIn('AudioTimestamp.TIMEBASE_BOOTTIME',text)
        self.assertIn('if(!clock.alive(now))',text)
        self.assertIn('configuration.isClientSilenced()',text)
        self.assertIn('InterruptionAudioClock.afterStart(',text)
        self.assertIn('CLIENT_SILENCED',text)
        self.assertIn('SystemClock.elapsedRealtime()-end>InterruptionAudioClock.MAX_LAG_MS',text)
        self.assertIn('int source = MediaRecorder.AudioSource.VOICE_COMMUNICATION',text)
        for forbidden in ['setMode(', 'setSpeakerphoneOn(', 'setStreamVolume(']: self.assertNotIn(forbidden,text)
        self.assertEqual(text.count('new AudioRecord('),1)
        voice=self.source('VoiceService.java')
        self.assertIn('Captura bloqueada por privacidad o llamada',voice)
        self.assertIn('Captura silenciada por Android',voice)
    def test_due_event_preparation_and_continuous_voice_yield_do_not_renew_output(self):
        agenda=self.source('AgendaDeliveryService.java'); timer=self.source('RingService.java')
        self.assertIn('listener.armAlarm(',agenda); self.assertIn('listener.arm(',agenda)
        self.assertIn('if(ended || outputRequested)',agenda)
        self.assertIn('if(sessionStarted) return START_NOT_STICKY',timer)
        self.assertIn('if(!VoiceService.continuousVoiceActive) stopService',timer)
        self.assertIn('Math.max(0,soundExpiresAt-SystemClock.elapsedRealtime())',timer)
        self.assertNotIn('silenceListener.begin(',timer)
        self.assertIn('listener.end(interruption)',agenda)
    def test_only_fixed_numeric_diagnostics_with_bounded_history(self):
        text=self.source('SilenceDiagnostics.java')
        self.assertIn('windows.size() == 4',text)
        self.assertIn('"max_pcm_lag_ms"',text)
        self.assertIn('"detection_to_callback_ms"',text)
        self.assertIn('"stop_return_accepted"',text)
        for forbidden in ['put("text"','put("pcm"','put("turn_id"','MessageDigest','FileOutputStream']:
            self.assertNotIn(forbidden,text)
    def test_joint_plan_is_not_permission_or_an_automatic_physical_runner(self):
        plan=json.loads((ROOT/'artifacts/android-voice/silence-lifecycle-v1/joint-validation-plan.json').read_text(encoding='utf-8'))
        for key in ['deployment_authorized','microphone_activation_authorized','physical_acceptance','automatic_execution','physical_run_executed']:
            self.assertIs(plan[key],False)
        self.assertEqual(plan['candidate_protocol'],13)
        self.assertEqual(plan['gates_in_order'][0],'informed_consent')
        self.assertLess(plan['gates_in_order'].index('native_public_prefix_budget_and_engine_cache_purge_receipts'),plan['gates_in_order'].index('matching_candidate_build_hashes_and_coordinated_update'))
        self.assertTrue(plan['checklist_is_not_an_executable_runner'])
        self.assertTrue(any('loses process RAM' in condition for condition in plan['required_consent']))


if __name__=='__main__': unittest.main()
