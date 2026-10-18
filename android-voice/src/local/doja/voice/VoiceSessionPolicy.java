package local.doja.voice;

/** Restricted microphone tests may never accept tools or TTS, even from the peer. */
final class VoiceSessionPolicy {
    static boolean isWakeOnlyReady(boolean wakeOnly, boolean wakeEnabled, boolean aiEnabled) {
        return wakeOnly && wakeEnabled && !aiEnabled;
    }
    static boolean wakeOnlyEventAllowed(String type) {
        return "state".equals(type) || "transcript".equals(type);
    }
    static boolean isConversationReady(int protocol, boolean conversation, boolean wakeOnly,
            boolean wakeEnabled, boolean aiEnabled, boolean actionsEnabled) {
        return protocol == 4 && conversation && !wakeOnly && wakeEnabled && aiEnabled && !actionsEnabled;
    }
    static boolean conversationEventAllowed(String type) {
        return "state".equals(type) || "transcript".equals(type) || "thinking".equals(type)
            || "say".equals(type) || "error".equals(type) || "conversation_closed".equals(type);
    }
}
