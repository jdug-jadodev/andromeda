package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.View;

/** UI-only Christmas checks. Needs andromeda_navidad=1. No VoiceService/TTS start, microphone or action. */
public final class ChristmasInstrumentation extends Instrumentation {
    private MainActivity activity;
    private long pauseMs;
    private boolean shots;
    /** Taken from inside the test: an adb capture can wait until the test ends. */
    private void screenshot(String name) throws java.io.IOException {
        android.graphics.Bitmap picture = getUiAutomation().takeScreenshot();
        if (picture == null) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), name + ".jpg"))) {
            picture.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
        }
        picture.recycle();
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        pauseMs = arguments != null && arguments.containsKey("pause_ms") ? Long.parseLong(arguments.getString("pause_ms")) : 300;
        shots = arguments != null && arguments.containsKey("shots");
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
            // Santa: she steps aside, comes back at the end, and the question works on the day itself too.
            for (int days : new int[]{50, 0}) {
                runOnMainSync(() -> {
                    activity.present(FaceState.THINKING, "Vista previa · pensando (simulado)");
                    activity.playSanta(days);
                    check(activity.isSantaPlaying() && activity.faceLayer.getVisibility() == View.INVISIBLE, "Santa did not take the screen");
                    check(activity.backdrop.isShowing() && !activity.border.isProcessing(), "The loader runs over Santa");
                    activity.present(FaceState.SPEAKING, "Vista previa · hablando (simulado)");
                    activity.present(FaceState.LISTENING, "Vista previa · escuchando (simulado)");
                });
                long began = SystemClock.uptimeMillis();
                long framesBefore = activity.backdrop.getDrawnFrames();
                if (shots) for (long at : new long[]{1500, 3000, 4500, 5600, 8000, 11000}) {
                    SystemClock.sleep(Math.max(0, began + at - SystemClock.uptimeMillis()));
                    screenshot("santa-" + days + "-" + at);
                }
                SystemClock.sleep(Math.max(0, began + ChristmasSanta.durationMs(days) - 100 - SystemClock.uptimeMillis()));
                long drawn = activity.backdrop.getDrawnFrames() - framesBefore;
                result.putLong("santa_fps_" + days, drawn * 1000 / (SystemClock.uptimeMillis() - began));
                check(drawn * 1000 / (SystemClock.uptimeMillis() - began) >= 15, "Santa is too slow for this phone: " + drawn + " frames");
                SystemClock.sleep(600);
                runOnMainSync(() -> {
                    check(!activity.isSantaPlaying() && activity.faceLayer.getVisibility() == View.VISIBLE, "She did not come back after Santa");
                    activity.present(FaceState.MUTED, "Micrófono apagado");
                });
                SystemClock.sleep(ChristmasMode.BACKDROP_FADE_MS + 300);
            }
            // A new question ends the scene at once; the voice is never kept waiting.
            runOnMainSync(() -> {
                activity.present(FaceState.THINKING, "Vista previa · pensando (simulado)");
                activity.playSanta(50);
                activity.present(FaceState.SPEAKING, "Vista previa · hablando (simulado)");
                activity.present(FaceState.THINKING, "Vista previa · pensando (simulado)");
                check(!activity.isSantaPlaying() && activity.faceLayer.getVisibility() == View.VISIBLE, "Santa kept the screen during a new turn");
                activity.present(FaceState.MUTED, "Micrófono apagado");
            });
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
