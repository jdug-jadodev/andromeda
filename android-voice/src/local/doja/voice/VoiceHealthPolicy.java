package local.doja.voice;

/** Last terminal technical receipt only: integers/booleans, never exception messages. */
final class VoiceHealthPolicy {
    static final int BEGIN=1, CONNECT=2, READY=3, CHANNELS=4, TTS=5, SILENCE=6,
        PCM_CREATE=7, PCM_START=8, PCM_CLOCK=9, STREAMING=10, OUTPUT=11,
        INTERRUPTION=12, RESPONSES=13, CONTEXT=14, STOP=15, PREPARED=16;
    static final int NONE=0, IO=1, JSON=2, SECURITY=3, STATE=4, ARGUMENT=5, TIMEOUT=6, OTHER=7, INTERRUPT_UNAVAILABLE=8;
    static final class Receipt {
        final int phase, error, source, line;
        final long began, ended;
        final boolean microphone;
        Receipt(int phase,int error,int source,int line,long began,long ended,boolean microphone) {
            this.phase=phase; this.error=error; this.source=source; this.line=line;
            this.began=began; this.ended=ended; this.microphone=microphone;
        }
    }
    private int phase=BEGIN;
    private long began;
    private boolean microphone;
    private Receipt terminal;
    synchronized void begin(long now) { if(began!=0 || now<=0) throw new IllegalStateException("Health window already started"); began=now; }
    synchronized void phase(int next) { if(next<BEGIN || next>PREPARED) throw new IllegalArgumentException("Invalid health phase"); if(terminal==null) phase=next; }
    synchronized void microphoneStarted() { if(terminal==null) microphone=true; }
    synchronized Receipt finish(int code,Throwable error,long now) {
        if(terminal!=null) return terminal;
        if(began<=0 || now<began || code<NONE || code>INTERRUPT_UNAVAILABLE) throw new IllegalArgumentException("Invalid health receipt");
        int source=0,line=0;
        if(error!=null) for(StackTraceElement frame:error.getStackTrace()) {
            String name=frame.getFileName(); int known="VoiceService.java".equals(name)?1:"VoiceConnection.java".equals(name)?2:
                "ConversationChannel.java".equals(name)?3:"SilenceListener.java".equals(name)?4:"SilenceModels.java".equals(name)?5:0;
            if(known!=0 && frame.getLineNumber()>0 && frame.getLineNumber()<100000) { source=known; line=frame.getLineNumber(); break; }
        }
        terminal=new Receipt(phase,code,source,line,began,now,microphone); return terminal;
    }
}
