package local.doja.voice;

/** Fixed-size freshness guard for an output-only AudioRecord, no PCM retention. */
final class InterruptionAudioClock {
    static final long MAX_LAG_MS=250;
    // Steady-state liveness: once frames flow, the mic must keep delivering.
    static final long STARTUP_MS=750;
    // Warm-up tolerance: this Moto's VOICE_COMMUNICATION AudioRecord can need well
    // over 750 ms to produce its first frame. Freshness (MAX_LAG_MS) is unchanged.
    static final long FIRST_FRAME_MS=3000;
    // Numeric stale sub-reasons; never content, only the guard that rejected.
    static final int NONE=0, FRAME_INVALID=1, TIMESTAMP_UNAVAILABLE=2, TIMESTAMP_INVALID=3, TIMESTAMP_STALE=4, IDLE=5, LAG_AFTER_DECODE=6;
    private final long began;
    private long frames,lastNow,lastEnd=-1;
    private int stale=NONE;
    private long lastStamp=-1, lastPosition=-1, lastObserveNow=-1, lastObserveEnd=-1;
    InterruptionAudioClock(long began) { if(began<=0) throw new IllegalArgumentException("Invalid capture clock"); this.began=lastNow=began; }
    boolean alive(long now) {
        if (now < lastNow) return false;
        if (frames == 0) return now - began < FIRST_FRAME_MS; // Allow the recorder to warm up before the first frame.
        return now - lastNow < STARTUP_MS;
    }
    int staleKind() { return stale; }
    long lastStampMs() { return lastStamp; }
    long lastFramePosition() { return lastPosition; }
    long framesSeen() { return frames; }
    long lastFrameAtMs() { return lastNow; }
    long lastObserveNowMs() { return lastObserveNow; }
    long lastObserveEndMs() { return lastObserveEnd; }
    long observe(int bytes,boolean timestamp,long position,long stamp,long now) {
        lastObserveNow=now;
        if(bytes<=0 || bytes>640 || bytes%2!=0 || now<lastNow || now-began>60000 || frames+bytes/2>960000) {
            stale=FRAME_INVALID; throw new IllegalArgumentException("Invalid capture frame");
        }
        lastNow=now; frames+=bytes/2;
        if(!timestamp) {
            if(now-began>=FIRST_FRAME_MS) { stale=TIMESTAMP_UNAVAILABLE; throw new IllegalArgumentException("Capture timestamp unavailable"); }
            return -1;
        }
        lastStamp=stamp; lastPosition=position;
        if(position<0 || stamp<=0 || position>frames+16000) { stale=TIMESTAMP_INVALID; throw new IllegalArgumentException("Invalid capture timestamp"); }
        long end=stamp+(frames-position)*1000/16000;
        lastObserveEnd=end;
        if(end>now+100 || now-end>MAX_LAG_MS || end<began-100 || end<lastEnd) { stale=TIMESTAMP_STALE; throw new IllegalArgumentException("Stale capture"); }
        lastEnd=end; return end;
    }
    static boolean afterStart(long end,int bytes,long started) {
        return bytes>0 && bytes<=640 && bytes%2==0 && started>0 && end>=started && end-bytes*1000L/32000>=started;
    }
}
