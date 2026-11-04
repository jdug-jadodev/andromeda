package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.View;

/** UI-only Christmas checks. Needs andromeda_navidad=1. No VoiceService/TTS start, microphone or action. */
public final class ChristmasInstrumentation extends Instrumentation {
    private MainActivity activity;
    private long pauseMs;
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        pauseMs = arguments != null && arguments.containsKey("pause_ms") ? Long.parseLong(arguments.getString("pause_ms")) : 300;
        start();
    }
    public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        try {
            check(!VoiceService.active && !RingService.ringing, "An active session must not be interrupted by UI tests");
            check(ChristmasMode.enabled(context), "Set andromeda_navidad to 1 first");
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class)
                .putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            SystemClock.sleep(600);   // The clock fits itself after the first layout.
            runOnMainSync(() -> {
                check(activity.idleLayer.getBackground() instanceof ChristmasClockDrawable, "Clock has no decorations");
                check(activity.backdrop != null && !activity.backdrop.isShowing(), "Backdrop visible before she is called");
                float time = activity.clock.getPaint().measureText(activity.clock.getText().toString());
                check(time <= activity.idleLayer.getWidth() * (1 - 2 * ChristmasMode.CLOCK_SIDE) + 1, "The time reaches the decorations");
            });
            SystemClock.sleep(pauseMs);
            // Called: the backdrop comes first and she follows it.
            runOnMainSync(() -> {
                activity.present(FaceState.LISTENING, "Vista previa · escuchando (simulado)");
                check(activity.backdrop.isShowing() && activity.faceLayer.getVisibility() == View.GONE, "Face did not wait for the backdrop");
            });
            SystemClock.sleep(ChristmasMode.BACKDROP_HOLD_MS + 300);
            runOnMainSync(() -> check(activity.faceLayer.getVisibility() == View.VISIBLE, "Face never appeared"));
            SystemClock.sleep(pauseMs);
            // Goodbye: the face leaves, the clock falls over the backdrop and then the backdrop goes.
            runOnMainSync(() -> {
                activity.present(FaceState.SPEAKING, "Vista previa · despedida (simulado)");
                activity.present(FaceState.READY, "Esperando activación");
                check(activity.isClosingFace(), "Goodbye did not start");
            });
            SystemClock.sleep(FaceExitMotion.GONE_MS + ChristmasMode.BACKDROP_FADE_MS + 500);
            runOnMainSync(() -> check(!activity.backdrop.isShowing() && activity.idleLayer.getVisibility() == View.VISIBLE, "Backdrop stayed after the goodbye"));
            // An answer never waits for the backdrop.
            runOnMainSync(() -> {
                activity.present(FaceState.LISTENING, "Vista previa · escuchando (simulado)");
                activity.present(FaceState.THINKING, "Vista previa · pensando (simulado)");
                check(activity.faceLayer.getVisibility() == View.VISIBLE, "Face waited during an answer");
            });
            // Session closed while she was still waiting: nothing stays behind.
            runOnMainSync(() -> activity.present(FaceState.READY, "Esperando activación"));
            SystemClock.sleep(FaceExitMotion.GONE_MS + ChristmasMode.BACKDROP_FADE_MS + 500);
            runOnMainSync(() -> {
                activity.present(FaceState.LISTENING, "Vista previa · escuchando (simulado)");
                activity.present(FaceState.MUTED, "Micrófono apagado");
                check(activity.faceLayer.getVisibility() == View.GONE, "Held face appeared after the session closed");
            });
            SystemClock.sleep(ChristmasMode.BACKDROP_HOLD_MS + ChristmasMode.BACKDROP_FADE_MS + 300);
            runOnMainSync(() -> check(activity.faceLayer.getVisibility() == View.GONE && !activity.backdrop.isShowing(), "Closed session left Christmas on screen"));
            check(!VoiceService.active && !RingService.ringing, "Visual test started audio");
            result.putBoolean("passed", true);
            result.putBoolean("microphone_opened", false);
        } catch (Exception error) {
            result.putBoolean("passed", false);
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
