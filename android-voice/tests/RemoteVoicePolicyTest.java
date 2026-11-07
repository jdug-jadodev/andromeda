package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain JVM test: no Android, no device, no network. */
public final class RemoteVoicePolicyTest {
    public static void main(String[] args) throws Exception {
        check(RemoteVoicePolicy.FRAME == 6, "frame");
        check("remote_voice_v1".equals(RemoteVoicePolicy.POLICY), "policy");
        check(RemoteVoicePolicy.toolAllowed("control_lights"), "lights");
        check(RemoteVoicePolicy.toolAllowed("create_timer"), "timer");
        check(RemoteVoicePolicy.toolAllowed("list_reminders"), "reminders");
        check(!RemoteVoicePolicy.toolAllowed("self_destruct"), "self destruct");
        check(!RemoteVoicePolicy.toolAllowed("camera"), "camera");
        check(!RemoteVoicePolicy.toolAllowed("configure"), "configure");
        check(RemoteVoicePolicy.command("cmd-0001", "apaga las luces", 1760000000000L), "command");
        check(!RemoteVoicePolicy.command("short", "hola", 1), "short id");
        check(!RemoteVoicePolicy.command("cmd-0001", "", 1), "empty text");
        check(!RemoteVoicePolicy.command("cmd-0001", "x".repeat(501), 1), "long text");
        check(!RemoteVoicePolicy.command("cmd-0001", "hola\u0000", 1), "control");
        check(!RemoteVoicePolicy.command("cmd-0001", "hola", 0), "timestamp");
        check(RemoteVoicePolicy.mayConfirm("remote", "remote"), "remote confirm");
        check(RemoteVoicePolicy.mayConfirm("local", "local"), "local confirm");
        check(!RemoteVoicePolicy.mayConfirm("local", "remote"), "cross confirm");
        check(!RemoteVoicePolicy.mayConfirm("remote", "local"), "cross confirm reverse");
        check(RemoteVoicePolicy.deadline(1000) == 16000, "deadline");

        Path contract = Path.of(args.length > 0 ? args[0] : "contract/home-api-v1");
        String policies = Files.readString(contract.resolve("policies.json"), StandardCharsets.UTF_8);
        String limits = Files.readString(contract.resolve("limits.json"), StandardCharsets.UTF_8);
        check(RemoteVoicePolicy.POLICY.equals(stringValue(policies, "remote_voice")), "contract policy");
        check(RemoteVoicePolicy.MAX_TEXT == intValue(limits, "command_chars"), "contract text");
        check(RemoteVoicePolicy.MAX_QUEUE == intValue(limits, "command_queue"), "contract queue");
        check(RemoteVoicePolicy.WAIT_MS == intValue(limits, "command_wait_ms"), "contract wait");
        check(RemoteVoicePolicy.REPLY_CACHE_MS == intValue(limits, "command_reply_cache_ms"), "contract cache");
        check(RemoteVoicePolicy.WAV_MAX_MS == intValue(limits, "wav_max_ms"), "contract wav");

        System.out.println("Remote voice policy tests passed.");
    }

    private static String stringValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("missing " + key);
        return matcher.group(1);
    }

    private static int intValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("missing " + key);
        return Integer.parseInt(matcher.group(1));
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    private RemoteVoicePolicyTest() { }
}
