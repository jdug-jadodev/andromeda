package local.doja.voice;

import java.io.IOException;
import java.nio.*;

/** Bounded PCM16 conversion for fixed PUBLIC synthesis checks only, not capture. */
final class PublicSpeechPcm {
    static final int MAX_WAV_BYTES=1200000;
    static byte[] decode(byte[] wav) throws IOException {
        if(wav==null || wav.length<44 || wav.length>MAX_WAV_BYTES) throw new IOException("Invalid public WAV size");
        ByteBuffer bytes=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if(bytes.getInt(0)!=0x46464952 || bytes.getInt(8)!=0x45564157) throw new IOException("Invalid public WAV header");
        int rate=0,offset=-1,length=0;
        for(int cursor=12;cursor+8<=wav.length;) {
            int kind=bytes.getInt(cursor),size=bytes.getInt(cursor+4),body=cursor+8;
            if(size<0 || body+(long)size>wav.length) throw new IOException("Invalid public WAV chunk");
            if(kind==0x20746d66) {
                if(rate!=0 || size<16 || bytes.getShort(body)!=1 || bytes.getShort(body+2)!=1 || bytes.getShort(body+14)!=16)
                    throw new IOException("Expected public mono PCM16");
                rate=bytes.getInt(body+4);
            } else if(kind==0x61746164) {
                if(offset!=-1) throw new IOException("Repeated public WAV data");
                offset=body; length=size;
            }
            cursor=body+size+size%2;
        }
        if(rate<16000 || rate>48000 || offset<0 || length<=0 || length%2!=0) throw new IOException("Missing public PCM");
        int samples=(int)((long)(length/2)*16000/rate);
        if(samples>16000*12) throw new IOException("Public speech exceeds bound");
        ByteBuffer output=ByteBuffer.allocate(samples*2).order(ByteOrder.LITTLE_ENDIAN);
        for(int index=0;index<samples;index++) {
            double position=(double)index*rate/16000; int left=(int)position,right=Math.min(left+1,length/2-1);
            short a=bytes.getShort(offset+left*2),b=bytes.getShort(offset+right*2);
            output.putShort((short)Math.round(a+(b-a)*(position-left)));
        }
        return output.array();
    }
}
