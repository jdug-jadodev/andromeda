package local.doja.voice;

public final class DecoderWarmupTest {
    static int checks;
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("Decoder preparation "+checks); }
    static final class Fake implements DecoderWarmup.Decoder {
        final long[] now; int chunks,endpoints; boolean nonempty;
        Fake(long[] now) { this.now=now; }
        public boolean accept(byte[] pcm,int length) {
            check(length==640); for(byte value:pcm) check(value==0);
            chunks++; now[0]+=chunks==1?283:2;
            return chunks%25==0;
        }
        public boolean empty(boolean endpoint) { if(endpoint) endpoints++; return !nonempty; }
    }
    interface Operation { void run() throws Exception; }
    static void reject(Operation operation) throws Exception { try { operation.run(); throw new AssertionError("Unsafe preparation accepted"); } catch(IllegalStateException expected) { checks++; } }
    public static void main(String[] args) throws Exception {
        long[] now={100000}; Fake fake=new Fake(now);
        DecoderWarmup.Result result=DecoderWarmup.prepare(fake,() -> now[0],() -> true);
        check(fake.chunks==100 && fake.endpoints==4); check(result.maxChunk==283 && result.maxTail==2); check(result.duration<4000);
        Fake nonempty=new Fake(new long[]{100000}); nonempty.nonempty=true;
        reject(() -> DecoderWarmup.prepare(nonempty,() -> nonempty.now[0],() -> true));
        Fake stopped=new Fake(new long[]{100000}); reject(() -> DecoderWarmup.prepare(stopped,() -> stopped.now[0],() -> false)); check(stopped.chunks==0);
        long[] expired={100000}; reject(() -> DecoderWarmup.prepare(new Fake(expired),() -> { long v=expired[0]; expired[0]+=4000; return v; },() -> true));
        check(InterruptionAudioClock.MAX_LAG_MS==250); check(SilenceWindowPolicy.ARM_MS==1500 && SilenceWindowPolicy.CALLBACK_MS==500);
        System.out.println("Public decoder preparation: "+checks+" assertions passed (fixed zeros, bounded time, no capture or human state; freshness unchanged).");
    }
}
