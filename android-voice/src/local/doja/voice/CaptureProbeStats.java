package local.doja.voice;

import java.util.*;

/** One phase's numeric summaries only; never retains PCM, ASR text or content IDs. */
final class CaptureProbeStats {
    enum End { PREPARING, RECORDING, COMPLETE, DEADLINE, USER_STOP, GUARD, CONFLICT, ERROR }
    enum PlaybackEnd { NONE, COMMAND, PHASE_END, DEADLINE, USER_STOP, GUARD, ERROR }
    private final int requested;
    private final boolean playbackRequested;
    private int actual = -1, rate = -1, channels = -1, deviceRate = -1, silenced = -1;
    private long frames, nonzero, clipped, peak, partials, maxPartial, finals, maxFinal, pending, finalMatches, pendingMatches;
    private long captureAfter = -1, captureDuration, maxDecode, maxLag;
    private double squares;
    private boolean aecAvailable, aecEnabled, muted;
    private End end = End.PREPARING;
    private boolean playbackStarted, playbackStopped;
    private int mediaVolume = -1, mediaMaxVolume = -1, outputDeviceType = -1;
    private long playbackDuration, maxPlaybackHead, commandStops;
    private PlaybackEnd playbackEnd = PlaybackEnd.NONE;
    CaptureProbeStats(int requested) { this(requested, false); }
    CaptureProbeStats(int requested, boolean playbackRequested) {
        if (requested != 6 && requested != 7) throw new IllegalArgumentException("Only the two public capture sources");
        this.requested = requested; this.playbackRequested = playbackRequested;
    }
    synchronized void started(long after, int actual, int rate, int channels, boolean available, boolean enabled, boolean muted) {
        captureAfter = after; this.actual = actual; this.rate = rate; this.channels = channels;
        aecAvailable = available; aecEnabled = enabled; this.muted = muted; end = End.RECORDING;
    }
    synchronized void configuration(int deviceRate, int silenced) {
        this.deviceRate = deviceRate; this.silenced = silenced == 0 || silenced == 1 ? silenced : -1;
    }
    synchronized void pcm(byte[] pcm, int count) {
        if (count < 0 || count > pcm.length || count % 2 != 0) throw new IllegalArgumentException("Invalid PCM frame");
        frames += count / 2;
        for (int i = 0; i < count; i += 2) {
            int value = (short)((pcm[i] & 255) | pcm[i + 1] << 8), magnitude = Math.abs(value);
            if (value != 0) nonzero++;
            if (magnitude >= 32767) clipped++;
            peak = Math.max(peak, magnitude); squares += (double)value * value;
        }
    }
    static int wordCount(String transientText) {
        if (transientText == null || transientText.trim().isEmpty()) return 0;
        return Math.min(128, transientText.trim().split("\\s+").length);
    }
    synchronized void partial(int count) { partials++; maxPartial = Math.max(maxPartial, Math.min(128, Math.max(0, count))); }
    synchronized void result(int count, boolean match, boolean tail) {
        count = Math.min(128, Math.max(0, count));
        if (tail) { pending = count; if (match) pendingMatches++; }
        else { finals++; maxFinal = Math.max(maxFinal, count); if (match) finalMatches++; }
    }
    synchronized void decoded(long milliseconds) { maxDecode = Math.max(maxDecode, Math.max(0, milliseconds)); }
    synchronized void lag(long producedFrames) { maxLag = Math.max(maxLag, Math.max(0, producedFrames - frames)); }
    synchronized void captureEnded(long duration) { captureDuration = Math.max(0, duration); }
    synchronized void finish(End reason) { end = reason; }
    synchronized boolean completed() { return end == End.COMPLETE && captureDuration >= CaptureProbePolicy.PHASE_MS; }
    synchronized void playbackVolume(int current, int maximum) { mediaVolume = current; mediaMaxVolume = maximum; }
    synchronized void playbackStarted() {
        if (!playbackRequested) throw new IllegalStateException("Playback not authorized for this phase"); playbackStarted = true;
    }
    synchronized void playbackFrames(long frames, int device) { maxPlaybackHead = Math.max(maxPlaybackHead, frames); outputDeviceType = device; }
    synchronized void playbackStopped(PlaybackEnd reason, boolean command, long duration, boolean stopped) {
        if (playbackEnd != PlaybackEnd.NONE) return;
        playbackEnd = reason; playbackStopped = stopped; playbackDuration = duration;
        if (playbackStarted && command && stopped) commandStops++;
    }
    synchronized Map<String, Object> snapshot() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source_requested", requested); values.put("source_actual", actual); values.put("end", end.name());
        values.put("sample_rate", rate); values.put("channels", channels); values.put("device_rate", deviceRate);
        values.put("capture_after_ms", captureAfter); values.put("capture_duration_ms", captureDuration);
        values.put("frames", frames); values.put("nonzero_samples", nonzero); values.put("clipped_samples", clipped); values.put("peak_abs", peak);
        values.put("rms_mdbfs", frames == 0 || squares == 0 ? -120000L
            : Math.max(-120000L, Math.round(20000 * Math.log10(Math.sqrt(squares / frames) / 32768d))));
        values.put("partial_reads", partials); values.put("max_partial_words", maxPartial);
        values.put("final_results", finals); values.put("max_final_words", maxFinal); values.put("pending_final_words", pending);
        values.put("final_command_matches", finalMatches); values.put("pending_command_matches", pendingMatches);
        values.put("max_decode_call_ms", maxDecode); values.put("max_observed_lag_frames", maxLag);
        values.put("aec_available", aecAvailable); values.put("aec_enabled", aecEnabled);
        values.put("microphone_muted", muted); values.put("client_silenced", silenced);
        values.put("playback_requested", playbackRequested); values.put("playback_started", playbackStarted); values.put("playback_stopped", playbackStopped);
        values.put("playback_end", playbackEnd.name()); values.put("playback_duration_ms", playbackDuration); values.put("playback_command_stops", commandStops);
        values.put("max_playback_head_frames", maxPlaybackHead); values.put("output_device_type", outputDeviceType);
        values.put("media_volume", mediaVolume); values.put("media_max_volume", mediaMaxVolume);
        return values;
    }
}
