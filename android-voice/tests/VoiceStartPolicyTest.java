package local.doja.voice;

public final class VoiceStartPolicyTest {
    public static void main(String[] args) {
        String message = VoiceStartPolicy.rejection("error", "resident_window_ending");
        if (message == null || !message.contains("ya no admite otra conversación") || !message.contains("Micrófono apagado"))
            throw new AssertionError("Missing fixed deadline rejection");
        for (String type : new String[]{null, "ready", "say", "Error", ""})
            if (VoiceStartPolicy.rejection(type, "resident_window_ending") != null)
                throw new AssertionError("Only error events can reject startup");
        for (String reason : new String[]{null, "", "context_expired", "untrusted server text", "RESIDENT_WINDOW_ENDING"})
            if (VoiceStartPolicy.rejection("error", reason) != null)
                throw new AssertionError("Unknown reasons must keep existing strict ready validation");
        System.out.println("Voice startup rejection policy passed");
    }
}
