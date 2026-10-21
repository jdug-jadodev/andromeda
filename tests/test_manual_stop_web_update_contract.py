"""Offline operator boundaries: current data, same identity, no automatic voice restart."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]


class ManualStopWebUpdateContractTests(unittest.TestCase):
    def test_startup_baseline_is_current_installed_snapshot_not_historical_empty_agenda(self):
        source=(ROOT/'artifacts/android-voice/startup-diagnosis-v1/operate.py').read_text()
        self.assertIn("baseline=op.metadata_snapshot(); assert baseline==installed['snapshot']",source)
        self.assertNotIn('migration-verified.json',source)

    def test_update_preserves_app_identity_and_is_admitted_only_while_idle(self):
        source=(ROOT/'artifacts/android-voice/web-response-spanish-install-v1/install.py').read_text()
        self.assertIn('user_confirmed_previous_Detener_was_manual_accidental',source)
        self.assertIn("op.idle(); dep.package(32,previous['apk_sha256'])",source)
        self.assertIn("op.out(['install','-r',str(APK)]",source)
        self.assertIn('dep.certificate(APK)==dep.certificate(old)',source)
        self.assertIn("assert op.metadata_snapshot()==snapshot",source)
        self.assertIn('second_install_attempt',source)
        for forbidden in ('uninstall','force-stop','pm clear','control.sh stop','kill -'):
            self.assertNotIn(forbidden,source)

    def test_public_probe_precedes_microphone_and_cannot_dispatch_tools_or_worker_queries(self):
        source=(ROOT/'artifacts/android-voice/web-response-spanish-live-v1/start.py').read_text()
        self.assertIn("installed['user_confirmed_previous_stop_manual']",source)
        self.assertIn('before_activation=public_native_check',source)
        self.assertIn('purge_engine_cache(model.key,now_ms,owner.alive)',source)
        self.assertIn('second_erase_confirms_empty_slot',source)
        for forbidden in ('buscar_en_internet(', 'interpret_result(', 'model.cancel(', 'model.owned.stop(', 'am start-foreground-service'):
            self.assertNotIn(forbidden,source)
        generic=(ROOT/'artifacts/android-voice/voice-session-observed-v1/start-and-observe.py').read_text()
        self.assertLess(generic.index('if before_activation is not None:'),generic.index("op.root('am start-foreground-service"))

    def test_typed_translation_requires_coordinated_capability_not_older_client_fallback(self):
        python=(ROOT/'backend/conversation_voice.py').read_text()
        android=(ROOT/'android-voice/src/local/doja/voice/VoiceConnection.java').read_text()
        self.assertGreaterEqual(python.count('worker_first_v3_spanish'),2)
        self.assertGreaterEqual(android.count('worker_first_v3_spanish'),2)
        self.assertNotIn('worker_first_v2_medium',python+android)


if __name__=='__main__': unittest.main()
