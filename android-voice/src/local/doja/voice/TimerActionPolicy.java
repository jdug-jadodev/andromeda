package local.doja.voice;

import java.util.*;

/** Platform-free authorization core. No storage, scheduling, audio or root. */
final class TimerActionPolicy {
    static final class Proposal {
        final String id, timerId, nonce, session, context, origin;
        final long sequence, expires;
        final int seconds;
        Proposal(String id, String timerId, String nonce, String session, String context,
                String origin, long sequence, int seconds, long expires) {
            this.id = id; this.timerId = timerId; this.nonce = nonce; this.session = session; this.context = context;
            this.origin = origin; this.sequence = sequence; this.seconds = seconds; this.expires = expires;
        }
    }
    static final class Grant {
        final Proposal proposal;
        final String turn;
        final long sequence, speechEnd, executeBefore;
        Grant(Proposal p, String turn, long sequence, long speechEnd, long executeBefore) {
            proposal = p; this.turn = turn; this.sequence = sequence; this.speechEnd = speechEnd; this.executeBefore = executeBefore;
        }
    }
    final class Permit {
        final String timerId;
        final int seconds;
        final long executeBefore;
        private boolean begun;
        private Permit(Proposal p, long deadline) { timerId = p.timerId; seconds = p.seconds; executeBefore = deadline; }
        boolean begin(long now) {
            synchronized (TimerActionPolicy.this) {
                if (!active || begun || now >= executeBefore) return false;
                begun = true; return true;
            }
        }
        boolean withinDeadline(long now) {
            // begin() is the transaction's acceptance point. Silencing later
            // must not erase an already authorized/accepted persistent timer.
            synchronized (TimerActionPolicy.this) { return begun && now < executeBefore; }
        }
    }
    private final String session, context;
    private Proposal pending;
    private boolean spoken, active = true;
    private final Set<String> consumed = new HashSet<>();
    private final Set<String> requestedTurns = new HashSet<>();
    TimerActionPolicy(String session, String context) {
        if (!id(session) || !id(context)) throw new IllegalArgumentException("Identidad inválida");
        this.session = session; this.context = context;
    }
    private static boolean id(String value) { return value != null && value.matches("[0-9a-f]{32}"); }
    private static boolean timerId(String value) { return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); }
    synchronized boolean offer(Proposal p, String humanRequest, String currentTurn, long currentSequence, long now) {
        if (!active || pending != null || p == null || !id(p.id) || !id(p.nonce) || !timerId(p.timerId)
                || !session.equals(p.session) || !context.equals(p.context) || !id(p.origin) || !p.origin.equals(currentTurn)
                || p.sequence != currentSequence || p.sequence < 1 || p.seconds < 1 || p.seconds > 86400
                || p.expires <= now || p.expires - now > 45000 || consumed.contains(p.id) || consumed.size() >= 24) return false;
        try { if (TimerRequestGuard.seconds(humanRequest) != p.seconds) return false; }
        catch (IllegalArgumentException invalid) { return false; }
        pending = p; spoken = false; return true;
    }
    synchronized boolean offerSpoken(String origin, long now) {
        if (!active || pending == null || spoken || !pending.origin.equals(origin) || now >= pending.expires) return false;
        spoken = true; return true;
    }
    static boolean same(Proposal a, Proposal b) {
        return a != null && b != null && a.id.equals(b.id) && a.timerId.equals(b.timerId) && a.nonce.equals(b.nonce)
            && a.session.equals(b.session) && a.context.equals(b.context) && a.origin.equals(b.origin)
            && a.sequence == b.sequence && a.seconds == b.seconds && a.expires == b.expires;
    }
    synchronized Permit claimRequest(Grant g, String humanRequest, String currentTurn, long currentSequence, long now) {
        // Protocol 8: the complete current low-risk request is the authorization.
        // No later "yes", recalled request or spoken model promise can grant it.
        if (g == null || g.proposal == null || humanRequest == null || !id(g.turn) || !g.turn.equals(currentTurn)
                || !g.turn.equals(g.proposal.origin) || g.sequence != currentSequence || g.sequence != g.proposal.sequence
                || g.speechEnd <= 0 || g.speechEnd > Long.MAX_VALUE - 30000 || g.speechEnd > now + 100
                || g.executeBefore != g.proposal.expires || g.executeBefore <= now || g.executeBefore - now > 4100
                || g.executeBefore > g.speechEnd + 26500 || requestedTurns.contains(g.turn) || requestedTurns.size() >= 24)
            return null;
        if (!offer(g.proposal, humanRequest, currentTurn, currentSequence, now)) return null;
        Proposal accepted = pending;
        consumed.add(accepted.id); requestedTurns.add(g.turn); pending = null; spoken = false;
        return new Permit(accepted, g.executeBefore);
    }
    synchronized Permit claim(Grant g, String humanConfirmation, String currentTurn, long currentSequence, long now) {
        if (!active || !spoken || pending == null || g == null || humanConfirmation == null || !same(pending, g.proposal) || consumed.contains(pending.id)
                || !id(g.turn) || !g.turn.equals(currentTurn) || g.turn.equals(pending.origin)
                || g.sequence != currentSequence || g.sequence <= pending.sequence || g.speechEnd <= 0
                || g.speechEnd > Long.MAX_VALUE - 30000 || g.speechEnd > now + 100
                || g.executeBefore <= now || g.executeBefore > pending.expires || g.executeBefore > g.speechEnd + 10000) return null;
        String text = TimerRequestGuard.normalize(humanConfirmation).replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$", "");
        if (!text.equals("confirmo temporizador") && !text.equals("confirmo el temporizador")) return null;
        Proposal accepted = pending; consumed.add(pending.id); pending = null; spoken = false;
        return new Permit(accepted, g.executeBefore);
    }
    synchronized void cancelProposal() {
        if (pending != null) consumed.add(pending.id);
        pending = null; spoken = false;
    }
    synchronized void revoke() { active = false; cancelProposal(); }
}
