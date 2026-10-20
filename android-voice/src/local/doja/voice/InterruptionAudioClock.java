package local.doja.voice;

/** Fixed-size freshness guard for an output-only AudioRecord, no PCM retention. */
final class InterruptionAudioClock {
    static final long MAX_LAG_MS=250, STARTUP_MS=750;
    private final long began;
    private long frames,lastNow,lastEnd=-1;
    InterruptionAudioClock(long began) { if(began<=0) throw new IllegalArgumentException("Invalid capture clock"); this.began=lastNow=began; }
    boolean alive(long now) { return now>=lastNow && now-lastNow<STARTUP_MS; }
    long observe(int bytes,boolean timestamp,long position,long stamp,long now) {
        if(bytes<=0 || bytes>640 || bytes%2!=0 || now<lastNow || now-began>60000 || frames+bytes/2>960000) throw new IllegalArgumentException("Invalid capture frame");
        lastNow=now; frames+=bytes/2;
        if(!timestamp) {
            if(now-began>=STARTUP_MS) throw new IllegalArgumentException("Capture timestamp unavailable");
            return -1;
        }
        if(position<0 || stamp<=0 || position>frames+16000) throw new IllegalArgumentException("Invalid capture timestamp");
        long end=stamp+(frames-position)*1000/16000;
        if(end>now+100 || now-end>MAX_LAG_MS || end<began-100 || end<lastEnd) throw new IllegalArgumentException("Stale capture");
        lastEnd=end; return end;
    }
    static boolean afterStart(long end,int bytes,long started) {
        return bytes>0 && bytes<=640 && bytes%2==0 && started>0 && end>=started && end-bytes*1000L/32000>=started;
    }
}
