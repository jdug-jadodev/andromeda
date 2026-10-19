package local.doja.voice;

import java.util.*;

public final class SpeechDiagnosticsTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Speech check " + checks); }
    public static void main(String[] args) {
        SpeechDiagnostics.Window done = SpeechDiagnostics.begin(1000, 120);
        done.started(1300); done.finish(SpeechDiagnostics.End.DONE, 8300);
        Map<String, Object> row = done.snapshot();
        check(row.get("completed").equals(true)); check(row.get("queue_to_start_ms").equals(300L));
        check(row.get("playback_ms").equals(7000L)); check(row.get("queue_to_end_ms").equals(7300L));
        done.finish(SpeechDiagnostics.End.SERVICE_STOP, 9000); check(done.snapshot().get("end").equals("DONE"));
        for (SpeechDiagnostics.End end : SpeechDiagnostics.End.values()) {
            SpeechDiagnostics.Window failed = SpeechDiagnostics.begin(10000, 700);
            failed.finish(end, 11000); failed.started(12000);
            Map<String, Object> item = failed.snapshot();
            check(item.get("completed").equals(false)); check(item.get("queue_to_start_ms").equals(-1L));
            check(item.get("end").equals(end == SpeechDiagnostics.End.DONE ? "DONE_WITHOUT_START" : end.name()));
        }
        check(SpeechDiagnostics.snapshot(12000).size() == 8);
        check(SpeechDiagnostics.snapshot(3610000).isEmpty());
        SpeechDiagnostics.Window limit = SpeechDiagnostics.begin(4000000, 9000);
        limit.started(4001000); limit.finish(SpeechDiagnostics.End.PLAYBACK_LIMIT, 4020000);
        check(limit.snapshot().get("end").equals("PLAYBACK_LIMIT")); check(limit.snapshot().get("text_units").equals(700));
        check(!SpeechDiagnostics.timeoutApplies(false, 1, 1, true));
        check(!SpeechDiagnostics.timeoutApplies(true, 1, 1, false)); // Old utterance ended, a new thinking phase is active.
        check(!SpeechDiagnostics.timeoutApplies(true, 2, 1, true));
        check(!SpeechDiagnostics.timeoutApplies(true, 0, 0, true));
        check(SpeechDiagnostics.timeoutApplies(true, 2, 2, true));
        check(SpeechDiagnostics.snapshot(1).isEmpty()); // Defensive monotonic-clock reset.
        Set<String> allowed = new HashSet<>(Arrays.asList("attempt", "text_units", "queue_to_start_ms", "queue_to_end_ms", "playback_ms", "end", "completed"));
        check(allowed.equals(row.keySet()));
        for (Object value : row.values()) check(value instanceof Number || value instanceof Boolean || "DONE".equals(value));
        System.out.println("Speech completion diagnostics: " + checks + " assertions passed (no device/audio/network/storage).");
    }
}
