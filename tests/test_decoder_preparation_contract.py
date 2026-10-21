from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]


class DecoderPreparationContractTests(unittest.TestCase):
    def test_prepared_decoder_is_taken_once_and_human_decoder_is_not_reset_or_pooled(self):
        source=(ROOT/'android-voice/src/local/doja/voice/SilenceListener.java').read_text(encoding='utf-8')
        self.assertIn('claimed=preparedDecoder; preparedDecoder=null; ready=false;',source)
        self.assertIn('try (Recognizer recognizer = claimed)',source)
        self.assertNotIn('recognizer.reset(',source)
        self.assertNotIn('preparedDecoder=recognizer',source)
        self.assertIn('preparationFailed=true',source)
        self.assertIn('preparation != null && !preparation.isDone()',source)

    def test_cold_work_is_public_before_capture_and_freshness_is_unchanged(self):
        source=(ROOT/'android-voice/src/local/doja/voice/SilenceListener.java').read_text(encoding='utf-8')
        before=source[:source.index('private void listen(')]
        self.assertIn('DecoderWarmup.prepare',before)
        self.assertNotIn('new AudioRecord(',before)
        self.assertIn('SystemClock.elapsedRealtime()-end>InterruptionAudioClock.MAX_LAG_MS',source)
        clock=(ROOT/'android-voice/src/local/doja/voice/InterruptionAudioClock.java').read_text()
        self.assertIn('MAX_LAG_MS=250',clock)
        self.assertIn('policy.accept(ticket,explicit,confidence',source)

    def test_fixed_output_probe_does_not_forge_human_turns_or_tool_authorization(self):
        source=(ROOT/'android-voice/src/local/doja/voice/VoiceService.java').read_text(encoding='utf-8')
        probe=source[source.index('    void checkPublicOutput()'):source.index('    void executeTimer(')]
        for forbidden in ('conversationTurn.begin(', 'model.start(', 'executeTimer(', 'executeAgenda(', 'executeVolume(', 'frame(4,'):
            self.assertNotIn(forbidden,probe)
        self.assertIn('index<3',probe)
        self.assertIn('SystemClock.elapsedRealtime()+25000',probe)
        self.assertIn('PUBLIC_OUTPUT_CHECK',probe)


if __name__=='__main__': unittest.main()
