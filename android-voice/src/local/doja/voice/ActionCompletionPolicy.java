package local.doja.voice;

/** Explicit negotiated continuation; receipt text stays fixed and independently checked. */
final class ActionCompletionPolicy {
    static final String OFFER = "¿Necesitas algo más?";
    static String text(String receipt, boolean continuing) {
        return continuing ? receipt + " " + OFFER : receipt;
    }
    static boolean valid(boolean continuing, String close, boolean offered) {
        return continuing ? "".equals(close) && offered : "action_result".equals(close) && !offered;
    }
}
