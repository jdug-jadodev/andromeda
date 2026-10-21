package local.doja.voice;

import java.util.*;
import java.util.regex.*;

/** Pure authorization/execution contract. Only the Android adapter touches media volume. */
final class PhoneVolumePolicy {
    static int requested(String human) {
        if (human == null || human.isEmpty() || human.length() > 250
                || Pattern.compile("[\\x00-\\x1f\\x7f\"'«»<>]|[0-9][.,][0-9]").matcher(human).find())
            throw new IllegalArgumentException("Invalid volume request");
        String plain = TimerRequestGuard.normalize(human).replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$", "")
            .replaceFirst("^andromeda[, ]+", "").replaceFirst("^por favor ", "");
        Matcher match = Pattern.compile("(?:baja|baje|bajale|sube|suba|subele|pon|ponme|ponle|ajusta|establece) el volumen"
            + "(?: del telefono| multimedia)? (?:a|al) (.+?)(?: por favor)?").matcher(plain);
        if (!match.matches()) throw new IllegalArgumentException("Explicit volume required");
        String value = match.group(1);
        if (value.equals("la mitad")) return 50;
        value = value.replaceFirst("(?: por ciento|\\s*%)$", "");
        int percent = value.equals("cien") ? 100 : TimerRequestGuard.amount(value);
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Volume out of range");
        return percent;
    }
    static final class Grant {
        final String session, context, turn, nonce;
        final long sequence, speechEnd, before;
        final int percent;
        Grant(String session, String context, String turn, String nonce, long sequence, long end, long before, int percent) {
            this.session = session; this.context = context; this.turn = turn; this.nonce = nonce;
            this.sequence = sequence; speechEnd = end; this.before = before; this.percent = percent;
        }
    }
    final class Permit {
        final Grant grant;
        private boolean begun;
        private Permit(Grant grant) { this.grant = grant; }
        boolean begin(long now) {
            synchronized (PhoneVolumePolicy.this) {
                if (!active || begun || now >= grant.before) return false;
                begun = true; return true;
            }
        }
        boolean mayWrite(long now) { synchronized (PhoneVolumePolicy.this) { return active && begun && now < grant.before; } }
    }
    private final String session, context;
    private final long maximumAge;
    private final Set<String> consumed = new HashSet<>();
    private boolean active = true;
    PhoneVolumePolicy(String session, String context) {
        this(session,context,30000);
    }
    PhoneVolumePolicy(String session, String context, int responseMs) {
        if (!id(session) || !id(context)) throw new IllegalArgumentException("Invalid identity");
        if(responseMs!=30000 && responseMs!=60000) throw new IllegalArgumentException("Invalid volume window");
        this.session = session; this.context = context;
        maximumAge=responseMs-3500;
    }
    private static boolean id(String value) { return value != null && value.matches("[0-9a-f]{32}"); }
    synchronized Permit claim(Grant g, String human, String turn, long sequence, long end, long sessionEnd, long now) {
        return claimPercent(g,requested(human),turn,sequence,end,sessionEnd,now);
    }
    synchronized Permit claimConfirmed(Grant g,VolumeDialogue dialogue,String origin,String question,
            VolumeDialogue.Human human,long sessionEnd,long now) {
        if(g==null) throw new IllegalArgumentException("Missing volume grant");
        int percent=dialogue.claim(origin,question,human,g.percent,now);
        return claimPercent(g,percent,human.id,human.sequence,human.speechEnd,sessionEnd,now);
    }
    private Permit claimPercent(Grant g,int percent,String turn,long sequence,long end,long sessionEnd,long now) {
        if (!active || g == null || !session.equals(g.session) || !context.equals(g.context) || !id(g.turn) || !id(g.nonce)
                || !g.turn.equals(turn) || g.sequence != sequence || sequence < 1 || g.speechEnd != end || end <= 0
                || end > now + 100 || now - end >= maximumAge || g.before <= now || g.before - now > 4100
                || g.before - end > maximumAge || g.before > sessionEnd - 5000 || consumed.contains(turn) || consumed.size() >= 24
                || percent != g.percent) throw new IllegalArgumentException("Invalid volume grant");
        consumed.add(turn); return new Permit(g);
    }
    synchronized void close() { active = false; }
    enum Status { APPLIED, UNCHANGED, LIMITED, REJECTED, UNKNOWN }
    static final class Receipt {
        final Status status;
        final int actual;
        Receipt(Status status, int actual) { this.status = status; this.actual = actual; }
        int actions() { return status == Status.APPLIED || status == Status.LIMITED ? 1 : 0; }
    }
    interface Media {
        int minimum(); int maximum(); int current(); void set(int index);
    }
    interface Clock { long now(); }
    static int index(int percent, int maximum) {
        if (percent < 0 || percent > 100 || maximum < 1 || maximum > 1000) throw new IllegalArgumentException("Invalid media range");
        return (percent * maximum + 50) / 100;
    }
    static int percent(int index, int maximum) {
        if (maximum < 1 || maximum > 1000 || index < 0 || index > maximum) throw new IllegalArgumentException("Invalid media index");
        return (index * 100 + maximum / 2) / maximum;
    }
    static Receipt execute(Permit permit, Media media, Clock clock) {
        if (!permit.begin(clock.now())) return new Receipt(Status.REJECTED, -1);
        boolean attempted = false;
        try {
            int max = media.maximum(), min = media.minimum(), target = index(permit.grant.percent, max), before = media.current();
            percent(before, max);
            if (min < 0 || min > max || target < min || !permit.mayWrite(clock.now())) return new Receipt(Status.REJECTED, -1);
            if (before == target) return new Receipt(Status.UNCHANGED, percent(before, max));
            attempted = true; media.set(target); // One write only. Never retry a clamp/error.
            int actual = media.current();
            return new Receipt(actual == target ? Status.APPLIED : Status.LIMITED, percent(actual, max));
        } catch (RuntimeException failure) { return new Receipt(attempted ? Status.UNKNOWN : Status.REJECTED, -1); }
    }
    static String resultText(Receipt r, int requested) {
        switch (r.status) {
            case APPLIED: case UNCHANGED:
                return r.actual == requested ? "Volumen multimedia al " + r.actual + " %."
                    : "Volumen multimedia aproximadamente al " + r.actual + " % (pedido: " + requested + " %).";
            case LIMITED: return "Android dejó el volumen multimedia al " + r.actual + " %; no confirmé el " + requested + " % solicitado.";
            case REJECTED: return "Android rechazó el ajuste de volumen. No he confirmado un cambio.";
            default: return "No pude confirmar el volumen. Revisa el teléfono antes de pedir otro ajuste.";
        }
    }
}
