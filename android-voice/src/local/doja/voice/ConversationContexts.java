package local.doja.voice;

import android.os.*;

/** Application RAM survives VoiceService teardown; no service or wake lock kept alive. */
final class ConversationContexts {
    static final ConversationMemory MEMORY = new ConversationMemory();
    private static final Handler expiry = new Handler(Looper.getMainLooper());
    private static final Runnable prune = ConversationContexts::scheduleExpiry;
    static synchronized void scheduleExpiry() {
        expiry.removeCallbacks(prune);
        long now = SystemClock.elapsedRealtime();
        long next = MEMORY.nextExpiry(now);
        if (next > 0) expiry.postDelayed(prune, Math.max(1, next - now));
    }
}
