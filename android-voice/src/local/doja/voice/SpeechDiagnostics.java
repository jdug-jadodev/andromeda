package local.doja.voice;

import java.util.*;

/** Eight content-free TTS summaries, RAM only, expiring after one hour. No text/IDs/audio. */
final class SpeechDiagnostics {
    enum End { DONE, DONE_WITHOUT_START, ENGINE_ERROR, NOT_READY, START_REJECTED,
        PLAYBACK_LIMIT, INTERRUPTED, LATE_START, SERVICE_STOP, OTHER, INTERRUPT_UNAVAILABLE, STOP_FAILED, ENGINE_STOPPED }
    private static final Deque<Window> windows = new ArrayDeque<>();
    private static long sequence;
    static synchronized Window begin(long now, int units) {
        expire(now);
        Window window = new Window(++sequence, now, Math.max(0, Math.min(700, units)));
        if (windows.size() == 8) windows.removeFirst();
        windows.addLast(window); return window;
    }
    private static void expire(long now) {
        while (!windows.isEmpty() && (now < windows.getFirst().queued || now - windows.getFirst().queued >= 3600000)) windows.removeFirst();
    }
    static synchronized List<Map<String, Object>> snapshot(long now) {
        expire(now); List<Map<String, Object>> result = new ArrayList<>();
        for (Window window : windows) result.add(window.snapshot());
        return result;
    }
    static boolean timeoutApplies(boolean speaking, int currentGeneration, int scheduledGeneration, boolean sameUtterance) {
        return speaking && currentGeneration > 0 && currentGeneration == scheduledGeneration && sameUtterance;
    }
    static final class Window {
        private final long number, queued;
        private final int units;
        private long started = -1, ended = -1;
        private End end;
        Window(long number, long queued, int units) { this.number = number; this.queued = queued; this.units = units; }
        synchronized void started(long now) { if (ended < 0 && started < 0) started = Math.max(queued, now); }
        synchronized void finish(End reason, long now) {
            if (ended >= 0) return;
            ended = Math.max(queued, now);
            end = reason == null ? End.OTHER : reason == End.DONE && started < 0 ? End.DONE_WITHOUT_START : reason;
        }
        synchronized Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("attempt", number); result.put("text_units", units);
            result.put("queue_to_start_ms", started < 0 ? -1 : started - queued);
            result.put("queue_to_end_ms", ended < 0 ? -1 : ended - queued);
            result.put("playback_ms", started < 0 || ended < 0 ? -1 : Math.max(0, ended - started));
            result.put("end", end == null ? "PENDING" : end.name()); result.put("completed", end == End.DONE);
            return result;
        }
    }
}
