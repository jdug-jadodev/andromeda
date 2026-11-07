package local.doja.voice;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Pure policy for voice messages, mirrored with contract/home-api-v1. */
final class VoiceMessagePolicy {
    static final String POLICY = "voice_message_v1";
    static final int MAX_BYTES = 2097152;
    static final int MAX_MS = 60000;
    static final int PENDING = 20;
    static final long TTL_MS = 86400000L;
    static final long MIN_INTERVAL_MS = 30000L;

    static final String QUEUED = "queued", PLAYING = "playing", PLAYED = "played",
        EXPIRED = "expired", FAILED = "failed";

    private static final Set<String> TYPES = Collections.unmodifiableSet(
        new HashSet<>(java.util.Arrays.asList("audio/mp4", "audio/aac", "audio/wav")));

    static boolean id(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{8,64}");
    }

    /** The media type may arrive with parameters; only the bare type decides. */
    static String type(String value) {
        if (value == null) return null;
        int parameters = value.indexOf(';');
        String bare = (parameters >= 0 ? value.substring(0, parameters) : value).trim().toLowerCase(java.util.Locale.US);
        return TYPES.contains(bare) ? bare : null;
    }

    static String extension(String type) {
        if ("audio/wav".equals(type)) return "wav";
        if ("audio/aac".equals(type)) return "aac";
        return "m4a";
    }

    static boolean duration(long ms) {
        return ms > 0 && ms <= MAX_MS;
    }

    static boolean bytes(long length) {
        return length > 0 && length <= MAX_BYTES;
    }

    private VoiceMessagePolicy() { }
}
