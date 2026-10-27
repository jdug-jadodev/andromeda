package local.doja.voice;

public final class SilencePolicyTest {
    static int assertions;
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (String text : new String[]{"Andrómeda, silencio", "andromeda silencio", "¡Andrómeda, silencio!", "silencio", "¡Silencio!", "SILENCIO.", "Andrómeda", "cancélalo", "cancela eso", "cancela lo", "Andrómeda, cancélalo"})
            check(SilenceWindowPolicy.isCommand(text), "Command rejected");
        for (String text : new String[]{"no silencio", "ayer dije silencio", "silencio no", "silencio y cancela el timer", "no Andrómeda silencio", "Andrómeda no silencio",
                "ayer dije Andrómeda silencio", "\"Andrómeda silencio\"", "Andrómeda silencio y cancela el timer",
                "Andrés silencio", "Andrómeda pon un temporizador"}) check(!SilenceWindowPolicy.isCommand(text), "Other/negated/reported text accepted");
        SilenceWindowPolicy policy = new SilenceWindowPolicy();
        check(!policy.begin("", 11000, 1000), "Empty epoch accepted");
        check(!policy.begin("a", 31001, 1000), "Unbounded window accepted");
        check(policy.begin("a", 11000, 1000), "Valid window rejected");
        check(!policy.accept("b", "andromeda silencio", 1, 1001), "Stale/foreign output interrupted");
        check(!policy.accept("a", "andromeda silencio", .79, 1001), "Weak confidence accepted");
        check(!policy.accept("a", "andromeda silencio", Double.NaN, 1001), "NaN accepted");
        check(!policy.accept("a", "andromeda silencio", 1.1, 1001), "Impossible confidence accepted");
        check(!policy.accept("a", "no silencio", 1, 1001), "Negated bare command accepted");
        check(policy.accept("a", "andromeda silencio", 1, 1001), "Valid command rejected");
        check(!policy.accept("a", "andromeda silencio", 1, 1002), "Duplicate interruption accepted");
        check(!policy.begin("a", 12000, 1002), "Old epoch revived");
        check(policy.begin("b", 12000, 1002), "New output rejected"); policy.end();
        check(!policy.accept("b", "andromeda silencio", 1, 1003), "Completed output interrupted");
        check(policy.begin("c", 12000, 1003), "Next output rejected");
        check(!policy.accept("c", "andromeda silencio", 1, 12000), "Late command accepted");
        policy.close(); check(!policy.begin("d", 13000, 12001), "Closed listener revived");
        SilenceWindowPolicy bare = new SilenceWindowPolicy();
        check(!bare.accept("bare", "silencio", 1, 1000), "Bare command accepted without output window");
        check(bare.begin("bare", 2000, 1000), "Bare command window failed");
        check(bare.accept("bare", "silencio", .9, 1001), "Bare command rejected during output");
        check(!bare.accept("bare", "silencio", .9, 1002), "Bare command replayed");
        bare.close(); check(!bare.accept("bare", "silencio", 1, 1003), "Bare command revived a closed window");
        SilenceDiagnostics.Window metrics = SilenceDiagnostics.begin(SilenceDiagnostics.Owner.SYNTHETIC, 1000);
        metrics.capture(1100, 7, true, true, false); metrics.silenced(0);
        metrics.frame(new byte[]{0, 0, 0, 0}, 4);
        metrics.frame(new byte[]{0, 1, 0, 0}, 4); metrics.endpoint(1, false); metrics.endpoint(1, true); metrics.callback();
        metrics.finish(SilenceDiagnostics.End.DETECTED, 1400);
        java.util.Map<String, Object> snapshot = metrics.snapshot();
        check(snapshot.get("frames").equals(4L) && snapshot.get("buffers_with_signal").equals(1L), "Numeric capture counters incorrect");
        check(snapshot.get("capture_after_ms").equals(100L) && snapshot.get("duration_ms").equals(400L), "Numeric timings incorrect");
        check(snapshot.get("endpoints").equals(2L) && snapshot.get("rejected_segments").equals(1L), "Endpoint counters incorrect");
        check(snapshot.get("callbacks_sent").equals(1L) && snapshot.get("client_silenced").equals(0), "Callback/OS-silencing evidence missing");
        snapshot.put("end", "tampered"); check(metrics.snapshot().get("end").equals("DETECTED"), "Snapshot leaked mutable state");
        for (int i = 0; i < 8; i++) SilenceDiagnostics.begin(SilenceDiagnostics.Owner.SYNTHETIC, 2000 + i).finish(SilenceDiagnostics.End.OUTPUT_ENDED, 3000 + i);
        check(SilenceDiagnostics.snapshot().size() == 4, "RAM diagnostics grew unbounded");
        for (java.util.Map<String, Object> window : SilenceDiagnostics.snapshot()) {
            check(!window.containsKey("pcm") && !window.containsKey("text") && !window.containsKey("turn_id"), "Diagnostic stored human content/IDs");
            window.clear();
        }
        check(SilenceDiagnostics.snapshot().get(0).size() == 34, "Caller altered retained diagnostics");
        check(SilenceDiagnostics.snapshot().get(0).get("began_ms").equals(2004L)
            && SilenceDiagnostics.snapshot().get(0).get("ended_ms").equals(3004L), "Final numeric receipt clock missing");
        Object full = new Object(), interrupt = new Object();
        check(AudioInputGate.acquire(full), "Full microphone lease failed");
        check(!AudioInputGate.acquire(interrupt), "Two AudioRecords allowed");
        AudioInputGate.release(interrupt); check(!AudioInputGate.acquire(interrupt), "Foreign owner released microphone");
        AudioInputGate.release(full); check(AudioInputGate.acquire(interrupt), "Interrupt did not acquire released mic");
        check(!AudioInputGate.acquire(full), "Full capture resumed over interrupt capture");
        AudioInputGate.release(interrupt); check(AudioInputGate.acquire(full), "Full capture could not resume"); AudioInputGate.release(full);
        System.out.println("Silence windows/microphone ownership: " + assertions + " assertions passed (no device/audio/network).");
    }
}
