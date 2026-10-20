package local.doja.voice;

/** Single active turn; no late or duplicate response may enter TTS. */
final class ConversationTurnPolicy {
    private String id = "";
    private long sequence, speechEnd, deadline;
    private boolean scheduled, started, ended;
    private String closeReason = "";
    synchronized boolean begin(String value, long seq, long end, long expires, long now) {
        return begin(value, seq, end, expires, now, 30000);
    }
    synchronized boolean begin(String value, long seq, long end, long expires, long now, long responseMs) {
        if (!id.isEmpty() || value == null || !value.matches("[0-9a-f]{32}") || seq <= sequence
                || (responseMs != 30000 && responseMs != 60000)
                || end <= 0 || end > Long.MAX_VALUE - responseMs || expires != end + responseMs
                || end > now + 100 || now >= expires) return false;
        id = value; sequence = seq; speechEnd = end; deadline = expires;
        scheduled = started = ended = false; closeReason = ""; return true;
    }
    synchronized boolean acceptReply(String value, long seq, long end, long expires, long now) {
        return acceptReply(value, seq, end, expires, now, "");
    }
    synchronized boolean acceptReply(String value, long seq, long end, long expires, long now, String reason) {
        return acceptReply(value, seq, end, expires, now, reason, false);
    }
    synchronized boolean acceptReply(String value, long seq, long end, long expires, long now, String reason, boolean actionResult) {
        if (!matches(value) || seq != sequence || end != speechEnd || expires != deadline
                || scheduled || now >= deadline - 1500 || reason == null
                || !(reason.isEmpty() || "explicit_close".equals(reason) || "declined_more".equals(reason)
                    || actionResult && "action_result".equals(reason))) return false;
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
    synchronized void resetScope() {
        if (hasActiveTurn()) throw new IllegalStateException("Cannot replace an active turn scope");
        sequence = 0; clear();
    }
}
