package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain JVM test: no Android, no device, no network, no files beyond the contract. */
public final class VoiceMessagePolicyTest {
    public static void main(String[] args) throws Exception {
        check("voice_message_v1".equals(VoiceMessagePolicy.POLICY), "policy");
        check(VoiceMessagePolicy.id("msg-0001"), "id");
        check(!VoiceMessagePolicy.id("msg"), "short id");
        check(!VoiceMessagePolicy.id("msg/0001"), "path id");
        check("audio/mp4".equals(VoiceMessagePolicy.type("audio/mp4")), "mp4");
        check("audio/mp4".equals(VoiceMessagePolicy.type("Audio/MP4; codecs=mp4a")), "parameters");
        check("audio/wav".equals(VoiceMessagePolicy.type("audio/wav")), "wav");
        check(VoiceMessagePolicy.type("audio/ogg") == null, "ogg");
        check(VoiceMessagePolicy.type(null) == null, "missing type");
        check("m4a".equals(VoiceMessagePolicy.extension("audio/mp4")), "extension");
        check("wav".equals(VoiceMessagePolicy.extension("audio/wav")), "wav extension");
        check(VoiceMessagePolicy.duration(1) && VoiceMessagePolicy.duration(60000), "duration");
        check(!VoiceMessagePolicy.duration(0) && !VoiceMessagePolicy.duration(60001), "duration limits");
        check(VoiceMessagePolicy.bytes(1) && VoiceMessagePolicy.bytes(2097152), "bytes");
        check(!VoiceMessagePolicy.bytes(0) && !VoiceMessagePolicy.bytes(2097153), "bytes limits");

        Path contract = Path.of(args.length > 0 ? args[0] : "contract/home-api-v1");
        String policies = Files.readString(contract.resolve("policies.json"), StandardCharsets.UTF_8);
        String limits = Files.readString(contract.resolve("limits.json"), StandardCharsets.UTF_8);
        check(VoiceMessagePolicy.POLICY.equals(stringValue(policies, "voice_message")), "contract policy");
        check(VoiceMessagePolicy.MAX_BYTES == intValue(limits, "message_bytes"), "contract bytes");
        check(VoiceMessagePolicy.MAX_MS == intValue(limits, "message_ms"), "contract ms");
        check(VoiceMessagePolicy.PENDING == intValue(limits, "messages_pending"), "contract pending");
        check(VoiceMessagePolicy.TTL_MS == longValue(limits, "message_ttl_ms"), "contract ttl");
        check(VoiceMessagePolicy.MIN_INTERVAL_MS == longValue(limits, "message_min_interval_ms"), "contract interval");

        System.out.println("Voice message policy tests passed.");
    }

    private static String stringValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("missing " + key);
        return matcher.group(1);
    }

    private static int intValue(String json, String key) {
        return (int) longValue(json, key);
    }

    private static long longValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("missing " + key);
        return Long.parseLong(matcher.group(1));
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    private VoiceMessagePolicyTest() { }
}
