package local.doja.voice;

/** Single active turn; no late or duplicate response may enter TTS. */
final class ConversationTurnPolicy {
    private String id = "";
    private long sequence, speechEnd, deadline;
    private boolean scheduled, started, ended;
    private String closeReason = "";
    synchronized boolean begin(String value, long seq, long end, long expires, long now) {
        if (!id.isEmpty() || value == null || !value.matches("[0-9a-f]{32}") || seq <= sequence
                || end <= 0 || end > Long.MAX_VALUE - 30000 || expires != end + 30000
                || end > now + 100 || now >= expires) return false;
        id = value; sequence = seq; speechEnd = end; deadline = expires;
        scheduled = started = ended = false; closeReason = ""; return true;
    }
    synchronized boolean acceptReply(String value, long seq, long end, long expires, long now) {
        return acceptReply(value, seq, end, expires, now, "");
    }
    synchronized boolean acceptReply(String value, long seq, long end, long expires, long now, String reason) {
        if (!matches(value) || seq != sequence || end != speechEnd || expires != deadline
                || scheduled || now >= deadline - 1500 || reason == null
                || !(reason.isEmpty() || "explicit_close".equals(reason) || "declined_more".equals(reason))) return false;
        scheduled = true; closeReason = reason; return true;
    }
    synchronized boolean acceptStart(String value, long now) {
        if (!matches(value) || !scheduled || started || ended || now > deadline) return false;
        started = true; return true;
    }
    synchronized boolean matches(String value) { return !id.isEmpty() && id.equals(value); }
    synchronized boolean speechStarted(String value) { return matches(value) && started; }
    synchronized boolean acceptClose(String value, long seq, long end, long expires, long now) {
        return matches(value) && seq == sequence && end == speechEnd && expires == deadline
            && awaitingClose() && (now <= deadline || now - deadline <= 20000);
    }
    synchronized boolean finishSpeech(String value, boolean completed) {
        if (!matches(value) || !scheduled || ended || completed && !started) return false;
        ended = true;
        if (closeReason.isEmpty()) clear();
        return true;
    }
    synchronized boolean awaitingClose() { return !id.isEmpty() && ended && !closeReason.isEmpty(); }
    synchronized String closeReason() { return closeReason; }
    synchronized boolean expiredBeforeStart(long now) { return !id.isEmpty() && !started && !ended && now >= deadline; }
    synchronized boolean hasActiveTurn() { return !id.isEmpty(); }
    synchronized void clear() { id = ""; scheduled = started = ended = false; closeReason = ""; }
}
