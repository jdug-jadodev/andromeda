package local.doja.voice;

/** Sequential, single-use 20 s diagnostic. No Android or executable actions. */
final class CaptureProbePolicy {
    static final long TOTAL_MS = 20000, PHASE_MS = 6000, FINAL_RESERVE_MS = 500;
    private final long deadline;
    private int next;
    private boolean held, closed;
    CaptureProbePolicy(long now) {
        if (now < 0 || now > Long.MAX_VALUE - TOTAL_MS) throw new IllegalArgumentException("Invalid capture clock");
        deadline = now + TOTAL_MS;
    }
    long deadline() { return deadline; }
    synchronized boolean begin(int source, long now, boolean safe, boolean alone) {
        if (closed || held || !safe || !alone || next >= 2 || source != 6 + next
                || now < deadline - TOTAL_MS || now > deadline - PHASE_MS - FINAL_RESERVE_MS) return false;
        held = true; return true;
    }
    synchronized void released(boolean completed) {
        if (held) { held = false; if (completed) next++; else closed = true; }
    }
    synchronized boolean complete() { return next == 2 && !held; }
    synchronized void close() { closed = true; }
}
