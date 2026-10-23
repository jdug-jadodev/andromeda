package local.doja.voice;

/** Pure retry gate: only pre-write transport failures may be retried. */
public final class LightBleRetryPolicy {
    public static final int MAX_ATTEMPTS = 3;
    public static final long BETWEEN_ATTEMPTS_MS = 900L;
    public static final long BETWEEN_TARGETS_MS = 1400L;

    private LightBleRetryPolicy() {}

    public static boolean mayRetry(String status, int acceptedFrameCount, int attempt) {
        if (acceptedFrameCount != 0 || attempt >= MAX_ATTEMPTS || status == null) return false;
        return "CONNECTION_FAILED".equals(status)
            || "SERVICE_DISCOVERY_FAILED".equals(status)
            || "CHARACTERISTIC_UNAVAILABLE".equals(status)
            || "WRITE_REJECTED".equals(status)
            || "TIMEOUT".equals(status);
    }

    public static long backoffMs(int nextAttempt) {
        if (nextAttempt <= 1) return BETWEEN_ATTEMPTS_MS;
        return BETWEEN_ATTEMPTS_MS * nextAttempt;
    }
}
