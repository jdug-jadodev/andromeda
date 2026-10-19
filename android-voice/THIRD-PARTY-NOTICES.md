# Local output-interruption runtime

Pinned archives and SHA-256: `config/voice_interrupt_deps.json`. The build verifies
local copies; downloads require the explicit `prepare-speech-deps.py --download`.

- **Vosk Android 0.3.47**, Alpha Cephei: Apache-2.0.
  Source/license: https://github.com/alphacep/vosk-api/tree/v0.3.47
  Native ASR library included for arm64-v8a only.
- **JNA 5.13.0**, JNA contributors: LGPL-2.1-or-later OR Apache-2.0;
  Apache-2.0 option selected for this internal build.
  Source/license: https://github.com/java-native-access/jna/tree/5.13.0
  The upstream classes.jar includes `META-INF/LICENSE`.
- The Spanish **vosk-model-small-es-0.42** already installed on the Moto is copied
  into the app's private files, verified file-by-file, not downloaded again or
  packaged in the APK. Upstream model/license listing:
  https://alphacephei.com/vosk/models (Apache-2.0).

These are local ASR runtime libraries/weights, not cloud services or another
generative assistant. The APK is a private pilot, not a public distribution;
review and carry the full upstream/native dependency notices before distributing
it. No human audio or conversation is included in the dependencies or fixtures.
