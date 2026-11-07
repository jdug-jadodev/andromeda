package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain JVM test: limits against the contract and WAV validation, no Android. */
public final class VoiceAudioPolicyTest {
    public static void main(String[] args) throws Exception {
        check(RemoteAudioPolicy.id("cmd-0001"), "id");
        check(!RemoteAudioPolicy.id("short"), "short id");
        check(RemoteAudioPolicy.duration(1) && RemoteAudioPolicy.duration(30000), "duration");
        check(!RemoteAudioPolicy.duration(0) && !RemoteAudioPolicy.duration(30001), "duration limits");

        byte[] pcm = WavAudio.pcm(wav(1, 16000, 16, 320));
        check(pcm.length == 320, "parsed pcm");

        checkFault(wav(2, 16000, 16, 320), "stereo");
        checkFault(wav(1, 8000, 16, 320), "rate");
        checkFault(wav(1, 16000, 8, 320), "bits");
        checkFault(new byte[10], "truncated");
        checkFault(new byte[44], "empty data");

        Path contract = Path.of(args.length > 0 ? args[0] : "contract/home-api-v1");
        String limits = Files.readString(contract.resolve("limits.json"), StandardCharsets.UTF_8);
        check(RemoteAudioPolicy.MAX_BYTES == intValue(limits, "command_audio_bytes"), "contract bytes");
        check(RemoteAudioPolicy.MAX_MS == intValue(limits, "command_audio_ms"), "contract ms");

        System.out.println("Voice audio policy tests passed.");
    }

    private static byte[] wav(int channels, int rate, int bits, int dataBytes) {
        byte[] wav = new byte[44 + dataBytes];
        int blockAlign = channels * bits / 8;
        putText(wav, 0, "RIFF");
        putInt(wav, 4, 36 + dataBytes);
        putText(wav, 8, "WAVE");
        putText(wav, 12, "fmt ");
        putInt(wav, 16, 16);
        putShort(wav, 20, 1);
        putShort(wav, 22, channels);
        putInt(wav, 24, rate);
        putInt(wav, 28, rate * blockAlign);
        putShort(wav, 32, blockAlign);
        putShort(wav, 34, bits);
        putText(wav, 36, "data");
        putInt(wav, 40, dataBytes);
        return wav;
    }

    private static void putText(byte[] target, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private static void putShort(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
        target[offset + 3] = (byte) (value >> 24);
    }

    private static void checkFault(byte[] wav, String name) {
        try {
            WavAudio.pcm(wav);
            throw new AssertionError(name);
        } catch (WavAudio.Fault expected) { }
    }

    private static int intValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("missing " + key);
        return Integer.parseInt(matcher.group(1));
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    private VoiceAudioPolicyTest() { }
}
