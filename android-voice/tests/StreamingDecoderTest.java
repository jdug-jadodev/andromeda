package local.doja.voice;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class StreamingDecoderTest {
    static int checks;
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("Streaming decoder "+checks); }
    interface Operation { void run() throws Exception; }
    static void reject(Operation operation) throws Exception { try { operation.run(); throw new AssertionError("Invalid public data accepted"); } catch(IOException expected) { checks++; } }
    static byte[] wav(int rate,short channels,int samples) {
        ByteBuffer out=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952).putInt(36+samples*2).putInt(0x45564157).putInt(0x20746d66).putInt(16);
        out.putShort((short)1).putShort(channels).putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16);
        out.putInt(0x61746164).putInt(samples*2);
        for(int index=0;index<samples;index++) out.putShort((short)(index%100));
        return out.array();
    }
    public static void main(String[] args) throws Exception {
        String original=String.join("\n","--min-active=200","--max-active=4000","--beam=11.0","--lattice-beam=4.0","--acoustic-scale=1.0",
            "--frame-subsampling-factor=3","--endpoint.silence-phones=1:2:3:4:5:6:7:8:9:10","--endpoint.rule2.min-trailing-silence=0.5",
            "--endpoint.rule3.min-trailing-silence=1.0","--endpoint.rule4.min-trailing-silence=2.0")+"\n";
        String profile=InterruptionModelProfile.configuration(original.getBytes(StandardCharsets.UTF_8));
        check(profile.equals(original+"\n--frames-per-chunk=6\n")); check(InterruptionModelProfile.FRAMES_PER_CHUNK%3==0);
        reject(() -> InterruptionModelProfile.configuration(original.replace("11.0","8.0").getBytes(StandardCharsets.UTF_8)));
        reject(() -> InterruptionModelProfile.configuration(new byte[8193]));
        byte[] input=wav(16000,(short)1,100),before=input.clone(),pcm=PublicSpeechPcm.decode(input);
        check(pcm.length==200 && Arrays.equals(input,before));
        for(int index=0;index<100;index++) check(ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).getShort(index*2)==index);
        check(PublicSpeechPcm.decode(wav(48000,(short)1,300)).length==200);
        reject(() -> PublicSpeechPcm.decode(wav(16000,(short)2,100)));
        reject(() -> PublicSpeechPcm.decode(wav(8000,(short)1,100)));
        reject(() -> PublicSpeechPcm.decode(wav(16000,(short)1,16000*13)));
        reject(() -> PublicSpeechPcm.decode(new byte[PublicSpeechPcm.MAX_WAV_BYTES+1]));
        input[40]=(byte)255; reject(() -> PublicSpeechPcm.decode(input));
        SilenceDiagnostics.Window diagnostic=SilenceDiagnostics.begin(SilenceDiagnostics.Owner.SPEECH,100000);
        diagnostic.processed(260); diagnostic.finish(SilenceDiagnostics.End.STALE_AUDIO,100300);
        check(diagnostic.snapshot().get("end").equals("STALE_AUDIO"));
        check(diagnostic.snapshot().get("began_ms").equals(100000L)); check(diagnostic.snapshot().get("ended_ms").equals(100300L));
        check(diagnostic.snapshot().get("max_processed_age_ms").equals(260L));
        check(InterruptionAudioClock.MAX_LAG_MS==250 && SilenceWindowPolicy.CALLBACK_MS==500);
        System.out.println("Streaming public decoder: "+checks+" assertions passed (unchanged weights/search, smaller work units, bounded public PCM and final numeric receipts).");
    }
}
