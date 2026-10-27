package local.doja.voice;

import android.app.Instrumentation;
import android.os.Bundle;
import org.vosk.Recognizer;

/** Confirms the restricted Vosk grammar loads; no microphone, audio or model inference. */
public final class CancelGrammarInstrumentation extends Instrumentation {
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try (SilenceModels.Lease lease = SilenceModels.acquire(getTargetContext());
             Recognizer recognizer = new Recognizer(lease.value, 16000,
                 SilenceWindowPolicy.COMMAND_GRAMMAR)) {
            recognizer.setWords(true);
            result.putString("result", "grammar_ok");
            result.putBoolean("microphone_opened", false);
            finish(0, result);
        } catch (Exception failure) {
            result.putString("result", "grammar_rejected");
            result.putBoolean("microphone_opened", false);
            finish(1, result);
        }
    }
}
