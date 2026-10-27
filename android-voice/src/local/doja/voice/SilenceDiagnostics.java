package local.doja.voice;

import java.util.*;

/** Four numeric output-window summaries in process RAM. No PCM, words, IDs or disk. */
final class SilenceDiagnostics {
    enum Owner { SPEECH, TONE, SYNTHETIC }
    enum End { NOT_READY, GUARD, WAIT_MIC, RECORDING, DETECTED, EXPIRED, OUTPUT_ENDED, CLOSED, ERROR, ARM_TIMEOUT, STALE_AUDIO, CLIENT_SILENCED }
    private static final Deque<Window> windows = new ArrayDeque<>();
    private static long sequence;
    static synchronized Window begin(Owner owner, long now) {
        Window window = new Window(++sequence, owner, now);
        if (windows.size() == 4) windows.removeFirst();
        windows.addLast(window); return window;
    }
    static synchronized List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Window window : windows) result.add(window.snapshot());
        return result;
    }
    static final class Window {
        private final long number, began;
        private final Owner owner;
        private End end = End.WAIT_MIC;
        private long frames, buffersWithSignal, endpoints, rejected, maxWords, callbacks;
        private long captureAfter = -1, duration;
        private long readyAfter=-1,outputAfter=-1,maxLag,maxDecode,maxProcessed,detected=-1,callbackAt=-1,stopAt=-1,ended=-1;
        private long maxConfidence = -1;
        private int stopAccepted=-1;
        private int source = -1, clientSilenced = -1;
        private int staleKind=-1;
        private long staleStamp=-1, stalePosition=-1, staleFrames=-1, staleNow=-1, staleEnd=-1, staleIdleGap=-1;
        private boolean echoAvailable, echoEnabled, microphoneMuted;
        Window(long number, Owner owner, long now) { this.number = number; this.owner = owner; began = now; }
        synchronized void capture(long now, int source, boolean available, boolean enabled, boolean muted) {
            captureAfter = Math.max(0, now - began); this.source = source;
            echoAvailable = available; echoEnabled = enabled; microphoneMuted = muted; end = End.RECORDING;
        }
        synchronized void silenced(int value) { clientSilenced = value == 0 || value == 1 ? value : -1; }
        synchronized void frame(byte[] pcm, int count) {
            frames += count / 2;
            for (int i = 0; i + 1 < count; i += 2) {
                int sample = (short)((pcm[i] & 255) | pcm[i + 1] << 8);
                if (Math.abs(sample) > 64) { buffersWithSignal++; break; }
            }
        }
        synchronized void endpoint(int words, boolean accepted) {
            endpoints++; maxWords = Math.max(maxWords, words);
            if (!accepted) rejected++;
        }
        synchronized void callback() { callbacks++; }
        synchronized void ready(long now) { readyAfter=Math.max(0,now-began); }
        synchronized void outputStarted(long now) { outputAfter=Math.max(0,now-began); }
        synchronized void lag(long lag) { maxLag=Math.max(maxLag,Math.max(0,lag)); }
        synchronized void decoded(long milliseconds) { maxDecode=Math.max(maxDecode,Math.max(0,milliseconds)); }
        synchronized void confidence(double value) {
            if (Double.isFinite(value) && value >= 0 && value <= 1)
                maxConfidence = Math.max(maxConfidence, Math.round(value * 1000));
        }
        synchronized void processed(long milliseconds) { maxProcessed=Math.max(maxProcessed,Math.max(0,milliseconds)); }
        synchronized void detected(long now) { detected=now; }
        synchronized void callback(long now) { callbacks++; callbackAt=now; }
        synchronized void stopped(boolean accepted,long now) { stopAccepted=accepted?1:0; stopAt=now; }
        synchronized void stale(int kind,long stamp,long position,long frames,long now,long end,long idleGap) {
            staleKind=kind; staleStamp=stamp; stalePosition=position; staleFrames=frames; staleNow=now; staleEnd=end; staleIdleGap=idleGap;
        }
        synchronized void finish(End reason, long now) { end = reason; ended=now; duration = Math.max(0, now - began); }
        synchronized Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("window", number); result.put("owner", owner.name()); result.put("end", end.name());
            result.put("capture_after_ms", captureAfter); result.put("duration_ms", duration); result.put("source", source);
            result.put("frames", frames); result.put("buffers_with_signal", buffersWithSignal);
            result.put("endpoints", endpoints); result.put("rejected_segments", rejected); result.put("max_words", maxWords);
            result.put("max_confidence_permille", maxConfidence);
            result.put("callbacks_sent", callbacks); result.put("aec_available", echoAvailable); result.put("aec_enabled", echoEnabled);
            result.put("microphone_muted", microphoneMuted); result.put("client_silenced", clientSilenced);
            result.put("ready_after_ms",readyAfter); result.put("output_after_ms",outputAfter);
            result.put("max_pcm_lag_ms",maxLag); result.put("max_decode_ms",maxDecode);
            result.put("began_ms",began); result.put("ended_ms",ended); result.put("max_processed_age_ms",maxProcessed);
            result.put("detection_to_callback_ms",detected<0 || callbackAt<detected ? -1 : callbackAt-detected);
            result.put("stop_return_accepted",stopAccepted); result.put("callback_to_stop_ms",callbackAt<0 || stopAt<callbackAt ? -1 : stopAt-callbackAt);
            result.put("stale_kind",staleKind); result.put("stale_stamp_ms",staleStamp);
            result.put("stale_frame_position",stalePosition); result.put("stale_frames",staleFrames);
            result.put("stale_now_ms",staleNow); result.put("stale_end_ms",staleEnd); result.put("stale_idle_gap_ms",staleIdleGap);
            return result;
        }
    }
}
