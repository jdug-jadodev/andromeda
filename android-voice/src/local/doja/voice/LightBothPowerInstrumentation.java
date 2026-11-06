package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Exercises the real two-lamp order (Lotus then Mr Star) once, through the same
 * dispatcher the voice service uses. It proves the sequential GATT sequence and
 * its bounded receipt without opening the microphone or the voice model.
 * Requires an explicit authorization argument; it does write BLE.
 */
public final class LightBothPowerInstrumentation extends Instrumentation {
    private static final String AUTHORIZATION = "BOTH_POWER_ONCE";
    private boolean finished;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments == null || !AUTHORIZATION.equals(arguments.getString("authorize_write"))) {
            finishResult(false, "AUTHORIZATION_REQUIRED", 0, 0, 0, 0);
            return;
        }
        boolean on = bool(arguments, "on");
        final long started = SystemClock.elapsedRealtime();
        final LightCommandDispatcher dispatcher = new LightCommandDispatcher(getTargetContext());
        try {
            dispatcher.send(LightCommand.power(LightTarget.BOTH, on),
                LightWriteAuthorization.issueForUser(SystemClock.elapsedRealtime()), result -> {
                    long elapsed = SystemClock.elapsedRealtime() - started;
                    // Let the adapter finish its delayed gatt.close() before this
                    // process dies; otherwise a stale link breaks the next test.
                    main.postDelayed(() -> {
                        try { dispatcher.close(); } catch (RuntimeException ignored) {}
                        finishResult(result.status == LightCommandDispatcher.Status.COMPLETE_UNCONFIRMED,
                            result.status.name(), result.targetCount, result.completedTargetCount,
                            result.acceptedFrameCount, elapsed);
                    }, 3000L);
                });
        } catch (RuntimeException failure) {
            try { dispatcher.close(); } catch (RuntimeException ignored) {}
            finishResult(false, "DISPATCHER_SETUP_FAILED", 0, 0, 0, SystemClock.elapsedRealtime() - started);
        }
    }

    private static boolean bool(Bundle arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof Boolean ? (Boolean) value
            : value instanceof String && Boolean.parseBoolean((String) value);
    }

    private void finishResult(boolean passed, String status, int targets, int completed,
            int frames, long elapsedMs) {
        if (finished) return;
        finished = true;
        Bundle result = new Bundle();
        result.putBoolean("passed", passed);
        result.putString("status", status);
        result.putInt("target_count", targets);
        result.putInt("completed_target_count", completed);
        result.putInt("accepted_frame_count", frames);
        result.putLong("elapsed_ms", elapsedMs);
        result.putBoolean("order", true);
        result.putBoolean("scan_used", false);
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
