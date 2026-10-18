package local.doja.voice;

/** Single active turn; no late or duplicate response may enter TTS. */
final class ConversationTurnPolicy {
    private String id = "";
    private long sequence, speechEnd, deadline;
    private boolean scheduled, started;
    synchronized boolean begin(String value, long seq, long end, long expires, long now) {
        if (!id.isEmpty() || value == null || !value.matches("[0-9a-f]{32}") || seq <= sequence
                || end <= 0 || end > Long.MAX_VALUE - 30000 || expires != end + 30000
                || end > now + 100 || now >= expires) return false;
        id = value; sequence = seq; speechEnd = end; deadline = expires;
        scheduled = started = false; return true;
    }
    synchronized boolean acceptReply(String value, long seq, long end, long expires, long now) {
        if (!matches(value) || seq != sequence || end != speechEnd || expires != deadline
                || scheduled || now >= deadline - 1500) return false;
        scheduled = true; return true;
    }
    synchronized boolean acceptStart(String value, long now) {
        if (!matches(value) || !scheduled || started || now > deadline) return false;
        started = true; return true;
    }
    synchronized boolean matches(String value) { return !id.isEmpty() && id.equals(value); }
    synchronized boolean expiredBeforeStart(long now) { return !id.isEmpty() && !started && now >= deadline; }
    synchronized boolean hasActiveTurn() { return !id.isEmpty(); }
    synchronized void clear() { id = ""; scheduled = started = false; }
}
