package local.doja.voice;

/** Pure retry gate: only pre-write transport failures may be retried. */
public final class LightBleRetryPolicy {
    public static final int MAX_ATTEMPTS = 3;
    /**
     * A wedged connection must be abandoned quickly: the voice turn has a
     * fixed deadline and an in-flight order may cover two lamps.
     */
    public static final long ATTEMPT_TIMEOUT_MS = 8000L;
    public static final long BETWEEN_ATTEMPTS_MS = 1200L;
    /** The previous connection needs time to actually go down before the next one. */
    public static final long BETWEEN_TARGETS_MS = 2000L;

    private LightBleRetryPolicy() {}

    public static boolean mayRetry(String status, int acceptedFrameCount, int attempt) {
        if (acceptedFrameCount != 0 || attempt >= MAX_ATTEMPTS || status == null) return false;
        // A second 8 s wait on the same wedged stack rarely helps and would
        // starve the other lamp, so TIMEOUT is retried at most once.
        if ("TIMEOUT".equals(status)) return attempt == 1;
        return "CONNECTION_FAILED".equals(status)
            || "SERVICE_DISCOVERY_FAILED".equals(status)
            || "CHARACTERISTIC_UNAVAILABLE".equals(status)
            || "WRITE_REJECTED".equals(status);
    }

    public static long backoffMs(int nextAttempt) {
        if (nextAttempt <= 1) return BETWEEN_ATTEMPTS_MS;
        return BETWEEN_ATTEMPTS_MS * nextAttempt;
    }
}
