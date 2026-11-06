package local.doja.voice;

import android.app.Instrumentation;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Audible routing check for a wired headset: speaks one fixed public line and
 * reports the device the AudioTrack was actually routed to. It never opens the
 * microphone, the model or a real voice session.
 */
public final class SpeakerRouteInstrumentation extends Instrumentation {
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SpeakerSpeech speech = null;
        try {
            check(!VoiceService.active, "An active voice session must not be interrupted");
            Context context = getTargetContext();
            CountDownLatch ready = new CountDownLatch(1);
            AtomicInteger status = new AtomicInteger(TextToSpeech.ERROR);
            SpeakerSpeech[] holder = new SpeakerSpeech[1];
            runOnMainSync(() -> holder[0] = new SpeakerSpeech(context,
                value -> { status.set(value); ready.countDown(); }, "com.google.android.tts"));
            check(ready.await(15, TimeUnit.SECONDS) && status.get() == TextToSpeech.SUCCESS, "TTS unavailable");
            speech = holder[0];
            final SpeakerSpeech output = speech;
            check(output.setLanguage(new Locale("es", "US")) != TextToSpeech.ERROR, "No Spanish voice");
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger started = new AtomicInteger(0), failed = new AtomicInteger(0);
            output.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { started.set(output.routedOutputType()); }
                @Override public void onDone(String id) { done.countDown(); }
                @Override public void onError(String id) { failed.incrementAndGet(); done.countDown(); }
            });
            check(output.speak("Hola. Soy Andromeda. Esta es una prueba del altavoz.",
                TextToSpeech.QUEUE_FLUSH, new Bundle(), "route-check") == TextToSpeech.SUCCESS, "Synthesis rejected");
            check(done.await(25, TimeUnit.SECONDS), "No audible completion");
            check(failed.get() == 0, "Playback failed");
            int routed = output.routedOutputType();
            result.putInt("routed_device_type", routed);
            result.putBoolean("builtin_speaker", routed == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
            result.putBoolean("passed", true);
        } catch (Exception failure) {
            result.putBoolean("passed", false);
            result.putString("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } finally {
            if (speech != null) speech.shutdown();
        }
        finish(result.getBoolean("passed") ? android.app.Activity.RESULT_OK : android.app.Activity.RESULT_CANCELED, result);
    }
}
