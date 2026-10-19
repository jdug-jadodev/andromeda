package local.doja.voice;

/** One source/output window. Validated final ASR only; no partial/tail replay. */
final class CapturePlaybackPolicy {
    private final int source;
    private final long deadline;
    private boolean started, consumed, closed;
    CapturePlaybackPolicy(int source, long deadline, long now) {
        if ((source != 6 && source != 7) || now < 0 || deadline <= now || deadline - now > CaptureProbePolicy.PHASE_MS)
            throw new IllegalArgumentException("Invalid playback window");
        this.source = source; this.deadline = deadline;
    }
    synchronized boolean start(long now) {
        if (closed || started || now >= deadline) return false; started = true; return true;
    }
    synchronized boolean accept(int source, long now, boolean validatedFinal) {
        if (closed || consumed || !started || !validatedFinal || source != this.source || now >= deadline) return false;
        consumed = true; return true;
    }
    synchronized void close() { closed = true; }
}
