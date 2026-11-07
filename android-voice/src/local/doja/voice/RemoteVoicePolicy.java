package local.doja.voice;

/** Pure remote-command policy, mirrored with backend/remote_voice.py. */
final class RemoteVoicePolicy {
    static final int FRAME = 6;
    static final String POLICY = "remote_voice_v1";
    static final String ORIGIN_LOCAL = "local", ORIGIN_REMOTE = "remote";
    static final int MAX_TEXT = 500, MAX_QUEUE = 2;
    static final long WAIT_MS = 15000, REPLY_CACHE_MS = 300000, WAV_MAX_MS = 30000;

    private static final java.util.Set<String> TOOLS =
        java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(
            "create_timer", "create_alarm", "create_reminder", "search_web",
            "set_phone_volume", "control_lights", "list_reminders", "delete_reminder")));

    static boolean id(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{8,64}");
    }

    static boolean text(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < 32 || current == 127) return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty() && trimmed.length() <= MAX_TEXT;
    }

    static boolean command(String id, String text, long clientTs) {
        return id(id) && text(text) && clientTs > 0;
    }

    static boolean toolAllowed(String name) {
        return name != null && TOOLS.contains(name);
    }

    /** A remote "yes" can only close a remote proposal, and vice versa. */
    static boolean mayConfirm(String proposalOrigin, String confirmationOrigin) {
        return (ORIGIN_LOCAL.equals(proposalOrigin) || ORIGIN_REMOTE.equals(proposalOrigin))
            && proposalOrigin.equals(confirmationOrigin);
    }

    static long deadline(long now) {
        if (now <= 0 || now > Long.MAX_VALUE - WAIT_MS) {
            throw new IllegalArgumentException("Invalid remote deadline");
        }
        return now + WAIT_MS;
    }

    private RemoteVoicePolicy() { }
}
