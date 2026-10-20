package local.doja.voice;

/** Fixed local rejection messages only; never display arbitrary server text. */
final class VoiceStartPolicy {
    static String rejection(String type, String reason) {
        if (!"error".equals(type)) return null;
        if ("resident_window_ending".equals(reason))
            return "La prueba está por terminar; ya no admite otra conversación. Micrófono apagado.";
        return null;
    }
}
