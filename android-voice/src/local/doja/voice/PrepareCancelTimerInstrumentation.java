package local.doja.voice;

import android.app.*;
import android.os.Bundle;
import java.util.UUID;

/** Opt-in setup for a human cancellation trial. Creates ONE REAL 3-minute timer.
 * Never run without consent. Unlike TimerInstrumentation, this uses real prefs
 * and intentionally leaves the timer active for cancellation by voice or UI.
 */
public class PrepareCancelTimerInstrumentation extends Instrumentation {
    boolean consent;
    public void onCreate(Bundle args) {
        super.onCreate(args);
        consent = args != null && "true".equals(args.getString("consent"));
        start();
    }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!consent) throw new IllegalStateException("Explicit test consent required");
            if (!TimerStore.prefs(getTargetContext()).getAll().isEmpty())
                throw new IllegalStateException("Existing timers; refusing to mix with user data");
            String id = UUID.randomUUID().toString();
            TimerStore.create(getTargetContext(), id, 180);
            if (!TimerStore.prefs(getTargetContext()).contains(id)) throw new IllegalStateException("Timer not persisted");
            result.putString("test", "prepare_real_cancel_trial");
            result.putInt("seconds", 180);
            result.putBoolean("timer_active", true);
            result.putBoolean("cleanup_required", true);
            result.putBoolean("microphone_opened", false);
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
