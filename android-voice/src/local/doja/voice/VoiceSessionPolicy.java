package local.doja.voice;

/** Restricted microphone tests may never accept tools or TTS, even from the peer. */
final class VoiceSessionPolicy {
    static boolean isWakeOnlyReady(boolean wakeOnly, boolean wakeEnabled, boolean aiEnabled) {
        return wakeOnly && wakeEnabled && !aiEnabled;
    }
    static boolean wakeOnlyEventAllowed(String type) {
        return "state".equals(type) || "transcript".equals(type);
    }
}
