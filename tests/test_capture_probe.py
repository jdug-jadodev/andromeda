"""Source isolation gates; not evidence of acoustic capture or hardware timing."""
from pathlib import Path
import unittest
import hashlib
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android-voice/src/local/doja/voice'


class CaptureProbeIsolationTests(unittest.TestCase):
    def test_probe_has_no_executor_network_live_synthesis_or_human_persistence_path(self):
        source = '\n'.join((SOURCE / name).read_text(encoding='utf-8') for name in
                           ['CaptureProbeService.java', 'CaptureProbeStats.java', 'CaptureProbeRecognition.java', 'CaptureProbePlayback.java', 'CaptureProbeAudio.java'])
        for forbidden in ['java.net.', 'TextToSpeech', 'VoiceConnection', 'TimerStore', 'SharedPreferences',
                          'FileOutputStream', 'ConversationModel', 'llama-server', 'startActivity(']:
            with self.subTest(forbidden=forbidden): self.assertNotIn(forbidden, source)
        self.assertIn('SilenceModels.acquire(this)', source)

    def test_probe_is_nonexported_manual_and_not_restarted_at_boot(self):
        namespace = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT / 'android-voice/AndroidManifest.xml').getroot()
        service = next(s for s in manifest.find('application').findall('service')
                       if s.attrib[namespace + 'name'] == '.CaptureProbeService')
        self.assertEqual(service.attrib[namespace + 'exported'], 'false')
        self.assertEqual(service.attrib[namespace + 'foregroundServiceType'], 'microphone')
        source = (SOURCE / 'CaptureProbeService.java').read_text(encoding='utf-8')
        self.assertIn('MainActivity.visibleToUser', source)
        self.assertIn('Micrófono apagado · analizando fase', source)
        self.assertIn('START_NOT_STICKY', source)
        self.assertNotIn('CaptureProbeService', (SOURCE / 'BootReceiver.java').read_text(encoding='utf-8'))
        activity = (SOURCE / 'MainActivity.java').read_text(encoding='utf-8')
        self.assertIn('"Iniciar prueba con voz fija" : "Iniciar diagnóstico"', activity)
        self.assertIn('getBooleanExtra("with_playback", false)', source)
        self.assertIn('stopService(new Intent(this, CaptureProbeService.class))', activity)

    def test_diagnostic_partial_and_pending_results_only_update_counters(self):
        recognition = (SOURCE / 'CaptureProbeRecognition.java').read_text(encoding='utf-8')
        for forbidden in ['interruptSpeech', 'interruptSound', 'controlTurn', 'policy.accept', 'TimerStore', '.run()']:
            self.assertNotIn(forbidden, recognition)
        self.assertIn('stats.partial(', recognition)
        self.assertIn('stats.result(', recognition)
        self.assertIn('return !pending && match', recognition)

    def test_playback_is_pinned_public_media_and_does_not_change_device_audio_settings(self):
        lock = json.loads((ROOT / 'config/capture_probe_audio.json').read_text(encoding='utf-8'))
        self.assertEqual(lock['public_text'], 'Vamos a preparar la comida y cerrar la puerta.')
        pcm = (ROOT / 'artifacts/android-voice/public-playback' / lock['asset']).read_bytes()
        self.assertEqual(hashlib.sha256(pcm).hexdigest(), lock['sha256'])
        self.assertEqual(len(pcm), lock['bytes']); self.assertLessEqual(len(pcm), 256000)
        source = (SOURCE / 'CaptureProbePlayback.java').read_text(encoding='utf-8')
        for forbidden in ['setVolume(', 'setStreamVolume', 'setMode(', 'setSpeakerphoneOn', 'setPreferredDevice', 'requestAudioFocus', 'java.net', 'getFilesDir']:
            self.assertNotIn(forbidden, source)
        self.assertIn('AudioTrack.MODE_STATIC', source)
        service = (SOURCE / 'CaptureProbeService.java').read_text(encoding='utf-8')
        self.assertIn('closePlayback(playbackReason()); closeCapture()', service)


if __name__ == '__main__': unittest.main()
