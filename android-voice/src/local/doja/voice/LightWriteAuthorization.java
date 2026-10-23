package local.doja.voice;

/** Single-use, short-lived human confirmation for real light writes. */
public final class LightWriteAuthorization {
    public static final long TTL_MS = 60000L;
    private final long expiresAt;
    private boolean consumed;

    private LightWriteAuthorization(long issuedAt) {
        this.expiresAt = issuedAt + TTL_MS;
    }

    public static LightWriteAuthorization issueForUser(long now) {
        return new LightWriteAuthorization(now);
    }

    /** Internal single-use grant for a schedule the user explicitly stored. */
    public static LightWriteAuthorization issueForSchedule(long now) {
        return new LightWriteAuthorization(now);
    }

    public synchronized boolean consume(long now) {
        if (consumed || now > expiresAt) return false;
        consumed = true;
        return true;
    }

    public synchronized boolean isConsumed() { return consumed; }
}
