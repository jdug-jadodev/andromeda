from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'android-voice/src/local/doja/voice'


class StreamingDecoderContractTests(unittest.TestCase):
    def test_native_neural_chunks_change_not_search_thresholds_or_weights(self):
        source=(SOURCE/'InterruptionModelProfile.java').read_text()
        self.assertIn('FRAMES_PER_CHUNK=6',source)
        self.assertIn('SOURCE_CONF_SHA256',source)
        self.assertIn('new String(original,StandardCharsets.UTF_8)',source)
        self.assertIn('Files.createSymbolicLink(path,target)',source)
        self.assertNotIn('Files.write(original',source)

    def test_failure_receipt_is_final_before_callback_is_dispatched(self):
        source=(SOURCE/'SilenceListener.java').read_text()
        tail=source[source.index('java.util.Arrays.fill(pcm,(byte)0);'):]
        self.assertLess(tail.index('diagnostic.finish(reason,'),tail.index('unavailable(handle);'))
        self.assertLess(tail.index('VoiceHealthStore.interruption(context,handle)'),tail.index('unavailable(handle);'))
        self.assertEqual(source.count('SystemClock.elapsedRealtime()-end>InterruptionAudioClock.MAX_LAG_MS'),2)

    def test_public_native_word_probe_is_not_human_authorization_or_instrumentation(self):
        source=(SOURCE/'PublicDecoderCheck.java').read_text()
        for forbidden in ['AudioRecord','VoiceConnection','ConversationTurnPolicy','policy.accept(','.command.run(','.speak(', 'Instrumentation']:
            self.assertNotIn(forbidden,source)
        self.assertIn('synthesizeToFile(PHRASES[index]',source)
        self.assertIn('output.delete()',source)
        self.assertIn('voice_public_decoder_health',source)
        self.assertIn('case"+index+"_age_ms',source)
        self.assertNotIn('putString(',source)
        voice=(SOURCE/'VoiceService.java').read_text()
        self.assertIn('decoderCheck ? this::checkPublicDecoder',voice)
        method=voice[voice.index('    void checkPublicDecoder()'):voice.index('    synchronized void frame(')]
        self.assertNotIn('VoiceConnection',method)


if __name__=='__main__': unittest.main()
