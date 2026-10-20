"""Static boundary checks for the explicit Android no-action readiness modes."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]


class VoiceReadinessContractTests(unittest.TestCase):
    def test_prepare_returns_before_any_AudioRecord_and_capture_returns_before_PCM_export(self):
        source=(ROOT/'android-voice/src/local/doja/voice/VoiceService.java').read_text(encoding='utf-8')
        source=source[source.index('    void captureConversation()'):source.index('    void executeTimer(')]
        preparation=source.index('if(preparationOnly)')
        self.assertLess(source.index('return;',preparation),source.index('new AudioRecord('))
        check=source.index('if(captureCheck) {')
        self.assertLess(source.index('return;',check),source.index('frame(4, packet'))
        self.assertIn('if(!captureCheck) new Thread(() -> responses(connection.input)',source)
        self.assertIn('},3000);',source)

    def test_capture_check_requires_visible_app_and_existing_explicit_consent(self):
        source=(ROOT/'android-voice/src/local/doja/voice/VoiceService.java').read_text(encoding='utf-8')
        begin=source.index('if((captureCheck || interruptionCheck) && (!conversationOnly')
        end=source.index('        wakeOnly',begin) if '        wakeOnly' in source[begin:] else source.index('        timerTrial =',begin)
        guard=source[begin:end]
        self.assertIn('!MainActivity.visibleToUser',guard)
        self.assertIn('getInt("continuous_voice_consent",0)!=ContinuousVoicePolicy.CONSENT_VERSION',guard)
        self.assertIn('stopSelf(); return START_NOT_STICKY;',guard)

    def test_health_store_has_only_fixed_numeric_receipt_and_never_exception_messages(self):
        for name in ('VoiceHealthPolicy.java','VoiceHealthStore.java'):
            source=(ROOT/'android-voice/src/local/doja/voice'/name).read_text(encoding='utf-8')
            for forbidden in ('getMessage(', 'lastTranscript', 'activeSpeechText', 'putString(', 'printStackTrace('): self.assertNotIn(forbidden,source)


if __name__=='__main__': unittest.main()
