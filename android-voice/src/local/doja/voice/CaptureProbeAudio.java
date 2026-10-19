package local.doja.voice;

import java.io.*;
import java.security.*;

/** Only the signed, fixed public PCM asset; never an arbitrary user/media path. */
final class CaptureProbeAudio {
    static byte[] read(InputStream input, int bytes, String sha256) throws IOException {
        if (input == null || bytes < 32000 || bytes > 256000 || bytes % 2 != 0
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IOException("Invalid public audio lock");
        byte[] data = new byte[bytes + 1]; int total = 0;
        while (total < data.length) {
            int read = input.read(data, total, data.length - total);
            if (read < 0) break;
            if (read == 0) {
                int value = input.read(); if (value < 0) break; data[total++] = (byte)value;
            } else total += read;
        }
        if (total != bytes) throw new IOException("Public audio size mismatch");
        try {
            byte[] expected = new byte[32];
            for (int i = 0; i < 32; i++) expected[i] = (byte)Integer.parseInt(sha256.substring(i * 2, i * 2 + 2), 16);
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(data, 0, bytes);
            if (!MessageDigest.isEqual(expected, digest.digest())) throw new IOException("Public audio hash mismatch");
        } catch (NoSuchAlgorithmException impossible) { throw new IOException("Digest unavailable", impossible); }
        return java.util.Arrays.copyOf(data, bytes);
    }
}
