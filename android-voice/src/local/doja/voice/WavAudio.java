package local.doja.voice;

import java.util.Arrays;

/**
 * Minimal WAV reader for spoken orders. The phone records PCM16 at 16 kHz mono,
 * which is exactly what Vosk expects, so no resampling or decoding is needed.
 */
final class WavAudio {
    static final int RATE = 16000;

    static final class Fault extends Exception {
        Fault(String message) { super(message); }
    }

    static byte[] pcm(byte[] wav) throws Fault {
        if (wav == null || wav.length < 44) throw new Fault("WAV vacio");
        if (!"RIFF".equals(text(wav, 0)) || !"WAVE".equals(text(wav, 8))) throw new Fault("No es WAV");
        int format = -1, channels = -1, rate = -1, bits = -1;
        byte[] data = null;
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String id = text(wav, offset);
            long declared = le32(wav, offset + 4) & 0xffffffffL;
            int body = offset + 8;
            long size = declared;
            if (size > wav.length - body) {
                // Some recorders leave a placeholder size in the data chunk.
                if (!"data".equals(id)) throw new Fault("Trozo truncado");
                size = wav.length - body;
            }
            if ("fmt ".equals(id)) {
                if (size < 16) throw new Fault("fmt corto");
                format = le16(wav, body);
                channels = le16(wav, body + 2);
                rate = le32(wav, body + 4);
                bits = le16(wav, body + 14);
            } else if ("data".equals(id)) {
                data = Arrays.copyOfRange(wav, body, body + (int) size);
            }
            offset = body + (int) size + ((int) size & 1);
        }
        if (format != 1 || channels != 1 || rate != RATE || bits != 16) {
            throw new Fault("Se esperaba PCM16 mono a 16 kHz");
        }
        if (data == null || data.length < 320 || (data.length & 1) != 0) {
            throw new Fault("PCM invalido");
        }
        return data;
    }

    private static String text(byte[] data, int offset) {
        if (offset + 4 > data.length) return "";
        return new String(data, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int le16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int le32(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8)
            | ((data[offset + 2] & 0xff) << 16) | ((data[offset + 3] & 0xff) << 24);
    }

    private WavAudio() { }
}
