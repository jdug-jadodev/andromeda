package local.doja.voice;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Fixed public silence only, before AudioRecord/output. Never reuses human decoder state. */
final class DecoderWarmup {
    static final int CHUNKS=100, BYTES=640;
    static final long MAX_MS=4000;
    interface Decoder {
        boolean accept(byte[] silence,int length) throws Exception;
        boolean empty(boolean endpoint) throws Exception;
    }
    static final class Result {
        final long began,duration,maxChunk,maxTail;
        Result(long began,long duration,long maxChunk,long maxTail) { this.began=began; this.duration=duration; this.maxChunk=maxChunk; this.maxTail=maxTail; }
    }
    static Result prepare(Decoder decoder,LongSupplier clock,BooleanSupplier wanted) throws Exception {
        long began=clock.getAsLong(),previous=began,max=0,tail=0;
        if(began<=0) throw new IllegalStateException("Invalid public preparation clock");
        byte[] silence=new byte[BYTES];
        try {
            for(int index=0;index<CHUNKS;index++) {
                long before=clock.getAsLong();
                if(!wanted.getAsBoolean() || before<previous || before-began>=MAX_MS) throw new IllegalStateException("Public decoder preparation stopped");
                Arrays.fill(silence,(byte)0);
                boolean endpoint=decoder.accept(silence,silence.length);
                long after=clock.getAsLong();
                if(after<before || after-began>=MAX_MS) throw new IllegalStateException("Public decoder preparation expired");
                long elapsed=after-before; max=Math.max(max,elapsed); if(index>=CHUNKS*3/4) tail=Math.max(tail,elapsed);
                if(endpoint && !decoder.empty(true)) throw new IllegalStateException("Nonempty public preparation endpoint");
                previous=after;
            }
            if(!wanted.getAsBoolean() || !decoder.empty(false)) throw new IllegalStateException("Nonempty public preparation state");
            long finished=clock.getAsLong();
            if(finished<previous || finished-began>=MAX_MS) throw new IllegalStateException("Public decoder finalization expired");
            return new Result(began,finished-began,max,tail);
        } finally { Arrays.fill(silence,(byte)0); }
    }
}
