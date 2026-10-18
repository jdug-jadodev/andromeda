package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;

/** Real pinned TLS shutdown + actual UI button, no capture/TTS/model/service start. */
public final class ShutdownInstrumentation extends Instrumentation {
    private MainActivity activity;
    private VoiceService detached;
    private VoiceConnection connection;
    private final Thread[] closeWorker = new Thread[1];
    private boolean seeded;
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle(); Context context = getTargetContext();
        Map<String, ?> timers = new HashMap<>(TimerStore.prefs(context).getAll());
        FaceState beforeState = VoiceService.uiState;
        String beforeStatus = VoiceService.lastStatus;
        long beforeExpiry = VoiceService.expiresAt;
        try {
            check(!VoiceService.active && !VoiceService.microphoneRecording && !RingService.ringing, "A real audio session is active");
            connection = VoiceConnection.open(context, "synthetic_pcm");
            check(connection.ready.optBoolean("test_fixture"), "Requires the dedicated TLS shutdown fixture, not the model backend");
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class)
                .putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            CountDownLatch buttonLaidOut = new CountDownLatch(1);
            runOnMainSync(() -> {
                // Detached object: exercise the exact connection teardown method
                // used by onDestroy, without starting a real capture service.
                detached = new VoiceService(); detached.socket = connection.socket; detached.output = connection.output;
                detached.controls.shutdownNow(); // Cleanup must not depend on this executor.
                StrictMode.ThreadPolicy beforePolicy = StrictMode.getThreadPolicy();
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build());
                try { closeWorker[0] = detached.closeConnectionAsync(); }
                finally { StrictMode.setThreadPolicy(beforePolicy); }
                check(detached.socket == null && detached.output == null, "Service retained the closed transport");
                check(closeWorker[0] != null && closeWorker[0] != Looper.getMainLooper().getThread(), "TLS close remained on the UI thread");
                // UI-only seed. No VoiceService.onStartCommand, AudioRecord or TTS.
                seeded = true;
                VoiceService.active = true; VoiceService.expiresAt = SystemClock.elapsedRealtime() + 90000;
                VoiceService.uiState = FaceState.THINKING; VoiceService.lastStatus = "Prueba de Silenciar · sin audio ni modelo";
                activity.microphoneStop.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        if (activity.microphoneStop.getWidth() > 0 && activity.microphoneStop.getHeight() > 0) {
                            activity.microphoneStop.getViewTreeObserver().removeOnGlobalLayoutListener(this); buttonLaidOut.countDown();
                        }
                    }
                });
                activity.refreshServiceState();
            });
            check(buttonLaidOut.await(3, TimeUnit.SECONDS), "Silence button never laid out");
            runOnMainSync(() -> {
                check(activity.microphoneStop.getVisibility() == View.VISIBLE, "Silence button not exposed");
                float x = (activity.microphoneStop.getLeft() + activity.microphoneStop.getRight()) / 2f;
                float y = (activity.microphoneStop.getTop() + activity.microphoneStop.getBottom()) / 2f;
                long at = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(at, at + 20, MotionEvent.ACTION_UP, x, y, 0);
                try { activity.dispatchTouchEvent(down); activity.dispatchTouchEvent(up); }
                finally { down.recycle(); up.recycle(); }
            });
            // View may post PerformClick after ACTION_UP; do not assert before
            // the real button callback has had a chance to run.
            waitForIdleSync();
            runOnMainSync(() -> {
                check(activity.state.getText().toString().contains("Micrófono apagado"), "Tap did not reach Silence action");
                check(activity.idleLayer.getVisibility() == View.VISIBLE && activity.faceLayer.getVisibility() == View.GONE,
                    "Silence did not retain the clock");
                check(!activity.isFinishing() && !activity.isDestroyed() && activity.hasWindowFocus(), "Silence exited the activity");
                VoiceService.active = false;
            });
            closeWorker[0].join(3000);
            check(!closeWorker[0].isAlive() && connection.socket.isClosed(), "TLS close did not complete");
            waitForIdleSync();
            runOnMainSync(() -> check(!activity.isFinishing() && !activity.isDestroyed() && activity.hasWindowFocus(), "Clock did not stay in foreground"));
            check(timers.equals(TimerStore.prefs(context).getAll()), "Persistent timers changed");
            check(!VoiceService.active && !VoiceService.microphoneRecording && !VoiceService.wakeListening, "Probe started audio capture");
            result.putBoolean("passed", true);
            result.putBoolean("tls_close_worker", true); result.putBoolean("clock_after_silence", true);
            result.putBoolean("microphone_opened", false); result.putBoolean("model_started", false);
            result.putBoolean("tts_initialized", false); result.putBoolean("real_service_started", false);
            result.putBoolean("timers_unchanged", true); result.putBoolean("strictmode_network_policy_preserved", true);
        } catch (Exception error) {
            result.putBoolean("passed", false); result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (detached != null) { detached.controls.shutdownNow(); VoiceSocketCleanup.closeAsync(detached.socket); }
            if (connection != null) VoiceSocketCleanup.closeAsync(connection.socket);
            if (seeded) {
                VoiceService.active = false; VoiceService.expiresAt = beforeExpiry;
                VoiceService.uiState = beforeState; VoiceService.lastStatus = beforeStatus;
            }
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
