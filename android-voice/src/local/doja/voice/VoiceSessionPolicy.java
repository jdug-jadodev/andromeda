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
        return protocol == 6 && conversation && !wakeOnly && wakeEnabled && aiEnabled && !actionsEnabled;
    }
    static boolean conversationEventAllowed(String type) {
        return "state".equals(type) || "transcript".equals(type) || "thinking".equals(type)
            || "say".equals(type) || "error".equals(type) || "conversation_closed".equals(type) || "context_selected".equals(type);
    }
    static boolean timerEventAllowed(String type) {
        return conversationEventAllowed(type) || "timer_execute".equals(type);
    }
    static boolean toolsEventAllowed(String type) { return timerEventAllowed(type) || "agenda_execute".equals(type) || "volume_execute".equals(type) || "reminder_read".equals(type); }
    static boolean continuousEventAllowed(String type) { return toolsEventAllowed(type) || "voice_context_request".equals(type) || "voice_context_ready".equals(type); }
    static boolean alarmEventAllowed(String type) { return continuousEventAllowed(type) || "alarm_select".equals(type) || "alarm_delete_execute".equals(type) || "reminder_delete_step".equals(type) || "reminder_delete_execute".equals(type); }
    static boolean isTimerReady(int protocol, boolean timerTrial, boolean conversationOnly, boolean wakeOnly,
            boolean wakeEnabled, boolean aiEnabled, boolean actionsEnabled) {
        return protocol == 8 && timerTrial && !conversationOnly && !wakeOnly && wakeEnabled && aiEnabled && actionsEnabled;
    }
}
