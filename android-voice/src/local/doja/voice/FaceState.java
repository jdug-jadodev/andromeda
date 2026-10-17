package local.doja.voice;

/** Presentation states, never an authorization to record or execute a tool. */
public enum FaceState {
    IDLE("En reposo"), PREPARING("Preparando"), READY("Sesión preparada"),
    LISTENING("Escuchando"), THINKING("Procesando"), SPEAKING("Hablando"),
    MUTED("Micrófono apagado"), ERROR("Requiere atención");

    public final String label;
    FaceState(String label) { this.label = label; }

    public static FaceState parse(String value) {
        if (value != null) {
            try { return valueOf(value); }
            catch (IllegalArgumentException ignored) {}
        }
        return ERROR; // An unknown backend/UI value must not imply listening.
    }
}
