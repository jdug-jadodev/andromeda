package local.doja.voice;

import java.text.Normalizer;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** Output-only, single-use, epoch-bound stop. Never creates/cancels a future event. */
final class SilenceWindowPolicy {
    private String id = "";
    private long expires;
    private boolean consumed, closed;
    private final Set<String> seen = new HashSet<>();
    synchronized boolean begin(String value, long deadline, long now) {
        return begin(value, deadline, now, 30000);
    }
    synchronized boolean begin(String value, long deadline, long now, long maximumMs) {
        if (closed || value == null || value.isEmpty() || value.length() > 80 || seen.contains(value) || seen.size() >= 32
                || (maximumMs != 30000 && maximumMs != 60000) || deadline <= now || deadline - now > maximumMs) return false;
        seen.add(value);
        id = value; expires = deadline; consumed = false; return true;
    }
    static boolean isCommand(String text) {
        if (text == null || text.length() > 80) return false;
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").trim().replaceAll("\\s+", " ").replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$", "");
        return plain.matches("(?:andromeda[, ]+)?silencio");
    }
    synchronized boolean active(String value, long now) { return !closed && !consumed && id.equals(value) && now < expires; }
    synchronized boolean accept(String value, String text, double confidence, long now) {
        if (!active(value, now) || !Double.isFinite(confidence) || confidence < .8 || confidence > 1 || !isCommand(text)) return false;
        consumed = true; return true;
    }
    synchronized void end() { id = ""; consumed = true; }
    synchronized void close() { end(); closed = true; }
}
