package local.doja.voice;

/** Explicit negotiated continuation; receipt text stays fixed and independently checked. */
final class ActionCompletionPolicy {
    static final String OFFER = "¿Necesitas algo más?";
    static String text(String receipt, boolean continuing) {
        // Nunca despues de una pregunta: corta al humano a mitad de la orden.
        // Debe coincidir con ConversationFlow.with_offer() en el backend.
        if (!continuing || receipt.replaceAll("\\s+$", "").endsWith("?")) return receipt;
        return receipt + " " + OFFER;
    }
    static boolean valid(boolean continuing, String close, boolean offered) {
        return continuing ? "".equals(close) && offered : "action_result".equals(close) && !offered;
    }
}
