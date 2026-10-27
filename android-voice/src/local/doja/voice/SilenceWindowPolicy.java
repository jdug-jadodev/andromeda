package local.doja.voice;

import java.text.Normalizer;
import java.util.Locale;
import java.util.ArrayDeque;
import java.util.Deque;

/** Output-only, single-use, epoch-bound stop. Never creates/cancels a future event. */
final class SilenceWindowPolicy {
    static final long ARM_MS = 1500;
    static final long CALLBACK_MS = 500;
    /** Local Vosk grammar: only the interruption/cancel/name vocabulary plus unknown. */
    static final String COMMAND_GRAMMAR =
        "[\"silencio\",\"andromeda\",\"andromeda silencio\",\"cancelalo\",\"cancela\","
        + "\"cancela eso\",\"cancela lo\",\"olvidalo\",\"andromeda cancelalo\",\"[unk]\"]";
    private static final int ARMING=0, READY=1, PLAYING=2, DETECTED=3, CLAIMED=4, FAILED=5, ENDED=6;
    static final class Ticket {
        final String id;
        final long generation, expires, readyBefore, startBefore;
        private int state=ARMING;
        private boolean failureSent;
        private long started=-1;
        private long detected=-1;
        private Ticket(String id,long generation,long expires,long readyBefore,long startBefore) {
            this.id=id; this.generation=generation; this.expires=expires; this.readyBefore=readyBefore; this.startBefore=startBefore;
        }
    }
    private Ticket current;
    private long generation;
    private boolean closed;
    // Recent duplicate starts are rejected, but this is NOT a 32-output lifetime.
    // Every async operation uses ticket identity, so eviction never revives an old callback.
    private final Deque<String> seen = new ArrayDeque<>(32);
    synchronized Ticket arm(String id,long expires,long startBefore,long now,long maximumMs) {
        return arm(id,expires,startBefore,now,maximumMs,ARM_MS);
    }

    synchronized Ticket arm(String id,long expires,long startBefore,long now,long maximumMs,long readyMs) {
        if(closed || generation==Long.MAX_VALUE || id==null || id.isEmpty() || id.length()>80 || seen.contains(id)
                || (maximumMs!=30000 && maximumMs!=60000) || (readyMs!=ARM_MS && readyMs!=5000)
                || now<=0 || expires<=now || expires-now>maximumMs
                || startBefore<=now || now>Long.MAX_VALUE-readyMs) return null;
        long readyBefore=Math.min(now+readyMs,startBefore-100);
        if(readyBefore<=now) return null;
        if(seen.size()==32) seen.removeFirst(); seen.addLast(id);
        if(current!=null) current.state=ENDED;
        current=new Ticket(id,++generation,expires,readyBefore,startBefore); return current;
    }
    synchronized boolean begin(String value, long deadline, long now) {
        return begin(value, deadline, now, 30000);
    }
    synchronized boolean begin(String value, long deadline, long now, long maximumMs) {
        // Compatibility for isolated policy/public-fixture tests; production uses arm/ready/start.
        Ticket ticket=arm(value,deadline,deadline,now,maximumMs);
        return ticket!=null && ready(ticket,now) && start(ticket,now);
    }
    static String plain(String text) {
        if (text == null || text.length() > 80) return "";
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").trim().replaceAll("\\s+", " ")
            .replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$", "");
    }

    static boolean isSilence(String text) {
        String plain = plain(text);
        return plain.matches("(?:andromeda[, ]+)?silencio");
    }

    static boolean isCancel(String text) {
        String plain = plain(text);
        return plain.matches("(?:andromeda[, ]+)?(?:cancelalo|cancela|cancela eso|cancela lo|olvidalo)");
    }

    static boolean isWakeOnly(String text) {
        return "andromeda".equals(plain(text));
    }

    static boolean isCommand(String text) {
        return isSilence(text) || isCancel(text) || isWakeOnly(text);
    }
    synchronized boolean current(Ticket ticket,long now) { return !closed && ticket!=null && current==ticket && ticket.state<FAILED && now<ticket.expires; }
    synchronized boolean preparing(Ticket ticket,long now) { return current(ticket,now) && ticket.state<=READY && now<ticket.readyBefore; }
    synchronized boolean ready(Ticket ticket,long now) {
        if(!preparing(ticket,now) || ticket.state!=ARMING) return false;
        ticket.state=READY; return true;
    }
    synchronized boolean start(Ticket ticket,long now) {
        if(!current(ticket,now) || ticket.state!=READY || now>=ticket.startBefore) return false;
        ticket.state=PLAYING; ticket.started=now; return true;
    }
    synchronized long started(Ticket ticket) { return current==ticket ? ticket.started : -1; }
    synchronized boolean playing(Ticket ticket,long now) { return current(ticket,now) && ticket.state==PLAYING; }
    synchronized boolean active(String value, long now) { return current!=null && current.id.equals(value) && playing(current,now); }
    synchronized boolean accept(String value, String text, double confidence, long now) {
        return acceptOutputControl(value,isCommand(text),confidence,now);
    }
    synchronized boolean acceptOutputControl(String value, boolean explicit, double confidence, long now) {
        return current!=null && current.id.equals(value) && accept(current,explicit,confidence,now);
    }
    synchronized boolean accept(Ticket ticket, boolean explicit, double confidence, long now) {
        return accept(ticket, null, explicit, confidence, now);
    }
    /**
     * Short barge-in commands are accepted at a lower confidence because the
     * exact phrase is also required; normal prose-like commands keep 0.8.
     */
    synchronized boolean accept(Ticket ticket, String text, boolean explicit, double confidence, long now) {
        if(!playing(ticket,now) || !Double.isFinite(confidence) || confidence>1 || !explicit) return false;
        double minimum = (isCancel(text) || isWakeOnly(text)) ? .55 : .8;
        if (confidence < minimum) return false;
        ticket.state=DETECTED; ticket.detected=now; return true;
    }
    synchronized boolean claimCallback(Ticket ticket,long now) {
        if(!current(ticket,now) || ticket.state!=DETECTED || now<ticket.detected || now-ticket.detected>CALLBACK_MS) return false;
        ticket.state=CLAIMED; return true;
    }
    synchronized boolean fail(Ticket ticket) {
        if(closed || ticket==null || current!=ticket || ticket.state>=CLAIMED) return false;
        ticket.state=FAILED; return true;
    }
    synchronized boolean claimFailure(Ticket ticket) {
        if(closed || current!=ticket || ticket.state!=FAILED || ticket.failureSent) return false;
        ticket.failureSent=true; return true;
    }
    synchronized void end(Ticket ticket) { if(ticket!=null) ticket.state=ENDED; if(current==ticket) current=null; }
    synchronized void end() { end(current); }
    synchronized int retainedIds() { return seen.size(); }
    synchronized void close() { end(); closed = true; }
}
