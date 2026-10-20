package local.doja.voice;

/** One user-enabled mode, all currently supported native tools; never auto-listen. */
final class UnifiedVoicePolicy {
    static final int SESSION_SECONDS = 300;
    static final int CONSENT_VERSION = 2;
    static final String START = "Activar Andrómeda";
    static final String STOP = "Detener Andrómeda";
    static String action(boolean active) { return active ? STOP : START; }
    static boolean mayStart(boolean active, boolean preview, boolean agendaDelivery, boolean captureDiagnostic) {
        return !active && !preview && !agendaDelivery && !captureDiagnostic;
    }
}
