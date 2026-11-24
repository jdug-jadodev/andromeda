package local.doja.voice;

/** Explicit negotiated continuation; receipt text stays fixed and independently checked. */
final class ActionCompletionPolicy {
    static String text(String receipt, boolean continuing) {
        // La casa ya no pega una muletilla fija al recibo: el texto es el que se
        // dijo, tal cual. Debe coincidir con ConversationFlow.with_offer() en el
        // backend, que solo valida tamano.
        return receipt;
    }
    static boolean valid(boolean continuing, String close, boolean offered) {
        return continuing ? "".equals(close) && offered : "action_result".equals(close) && !offered;
    }
}
