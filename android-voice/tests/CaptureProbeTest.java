package local.doja.voice;

import java.util.*;
import java.io.*;
import java.nio.file.*;

public final class CaptureProbeTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        CaptureProbePolicy policy = new CaptureProbePolicy(1000);
        check(policy.deadline() == 21000, "20 s ceiling changed");
        check(!policy.begin(7, 1000, true, true), "Wrong first source accepted");
        check(!policy.begin(6, 1000, false, true), "Resources bypassed");
        check(!policy.begin(6, 1000, true, false), "Parallel capture allowed");
        check(!policy.begin(6, 999, true, true), "Backdated start accepted");
        check(policy.begin(6, 1200, true, true), "First phase failed");
        check(!policy.begin(7, 7200, true, true), "Recorder not released before source change");
        check(!policy.complete(), "Half probe marked complete");
        policy.released(true);
        check(!policy.begin(6, 7700, true, true), "First source replayed");
        check(policy.begin(7, 7700, true, true), "Second phase failed");
        check(!policy.begin(7, 7800, true, true), "Duplicate phase accepted");
        policy.released(true); check(policy.complete(), "Two released phases not complete");
        check(!policy.begin(7, 14000, true, true), "Extra phase accepted");
        CaptureProbePolicy late = new CaptureProbePolicy(1000);
        check(!late.begin(6, 14501, true, true), "Started without six seconds plus final reserve");
        check(late.begin(6, 14500, true, true), "Exact preparation boundary rejected");
        late.released(false); check(!late.complete(), "Partial capture certified success");
        check(!late.begin(7, 15000, true, true), "Failed phase continued");
        CaptureProbePolicy stopped = new CaptureProbePolicy(1000); stopped.close();
        check(!stopped.begin(6, 1000, true, true), "Closed diagnostic revived");
        CaptureProbeStats stats = new CaptureProbeStats(6);
        stats.started(200, 6, 16000, 1, true, false, false); stats.configuration(48000, 0);
        stats.pcm(new byte[]{0, 0, 0, 0}, 4);
        Map<String, Object> quiet = stats.snapshot();
        check(quiet.get("frames").equals(2L) && quiet.get("nonzero_samples").equals(0L), "Silent PCM counters wrong");
        check(quiet.get("rms_mdbfs").equals(-120000L), "Silent RMS not floored");
        stats.pcm(new byte[]{0, 1, 0, -1, -1, 127, 0, -128}, 8);
        stats.partial(1); stats.partial(3); stats.result(0, false, false); stats.result(1, true, true);
        stats.decoded(40); stats.decoded(10); stats.lag(100); stats.captureEnded(6000); stats.finish(CaptureProbeStats.End.COMPLETE);
        Map<String, Object> data = stats.snapshot();
        check(data.get("frames").equals(6L) && data.get("nonzero_samples").equals(4L), "PCM16 sign/count wrong");
        check(data.get("clipped_samples").equals(2L) && data.get("peak_abs").equals(32768L), "Clipping/amplitude wrong");
        check(data.get("max_partial_words").equals(3L) && data.get("max_final_words").equals(0L), "Partial evidence lost/misreported as final");
        check(data.get("pending_final_words").equals(1L) && data.get("pending_command_matches").equals(1L), "Pending result lost");
        check(data.get("final_command_matches").equals(0L), "Pending result became a completed command");
        check(data.get("max_decode_call_ms").equals(40L) && data.get("max_observed_lag_frames").equals(94L), "Decode/lag evidence wrong");
        check(stats.completed(), "Full phase did not complete");
        check(CaptureProbeStats.wordCount("") == 0 && CaptureProbeStats.wordCount("   ") == 0, "Empty hypothesis counted");
        check(CaptureProbeStats.wordCount("dos palabras") == 2, "Word count wrong");
        data.clear(); check(stats.snapshot().size() == 36, "Mutable snapshot escaped");
        for (Object value : stats.snapshot().values()) check(value instanceof Number || value instanceof Boolean || value instanceof String, "Retained PCM/object");
        check(!stats.snapshot().containsKey("text") && !stats.snapshot().containsKey("audio"), "Retained voice/text");
        check(stats.snapshot().get("playback_requested").equals(false) && stats.snapshot().get("playback_started").equals(false), "Quiet control enabled playback");
        try { stats.playbackStarted(); throw new AssertionError("Quiet control allowed audio start"); }
        catch (IllegalStateException expected) { checks++; }
        CaptureProbeStats playing = new CaptureProbeStats(7, true);
        playing.playbackVolume(4, 15); playing.playbackStarted(); playing.playbackFrames(300, 2);
        playing.playbackStopped(CaptureProbeStats.PlaybackEnd.COMMAND, true, 900, true);
        playing.playbackStopped(CaptureProbeStats.PlaybackEnd.PHASE_END, false, 6000, true);
        check(playing.snapshot().get("playback_command_stops").equals(1L), "Command cut counted more than once");
        check(playing.snapshot().get("playback_duration_ms").equals(900L) && playing.snapshot().get("playback_end").equals("COMMAND"), "Phase timeout overwrote a command cut");
        CapturePlaybackPolicy output = new CapturePlaybackPolicy(6, 7000, 1000);
        check(!output.accept(6, 1001, true), "Unstarted output cut");
        check(output.start(1001) && !output.start(1002), "Output started twice");
        check(!output.accept(7, 1003, true), "Foreign source cut an output");
        check(!output.accept(6, 1003, false), "Partial/unvalidated hypothesis cut output");
        check(!output.accept(6, 7000, true), "Late final cut output");
        check(output.accept(6, 6999, true) && !output.accept(6, 6999, true), "Current cut not single-use");
        output.close(); check(!output.accept(6, 6000, true), "Closed output revived");
        CapturePlaybackPolicy lateOutput = new CapturePlaybackPolicy(7, 7000, 1000);
        check(!lateOutput.start(7000), "Expired playback started");
        byte[] publicPcm = Files.readAllBytes(Paths.get(args[0])); int bytes = Integer.parseInt(args[2]);
        check(Arrays.equals(publicPcm, CaptureProbeAudio.read(new ByteArrayInputStream(publicPcm), bytes, args[1])), "Pinned public PCM changed");
        byte[] changed = publicPcm.clone(); changed[1] ^= 1;
        try { CaptureProbeAudio.read(new ByteArrayInputStream(changed), bytes, args[1]); throw new AssertionError("Modified media accepted"); }
        catch (IOException expected) { checks++; }
        try { CaptureProbeAudio.read(new ByteArrayInputStream(Arrays.copyOf(publicPcm, publicPcm.length - 2)), bytes, args[1]); throw new AssertionError("Truncated media accepted"); }
        catch (IOException expected) { checks++; }
        try { CaptureProbeAudio.read(new ByteArrayInputStream(Arrays.copyOf(publicPcm, publicPcm.length + 2)), bytes, args[1]); throw new AssertionError("Oversized media accepted"); }
        catch (IOException expected) { checks++; }
        System.out.println("Capture/playback diagnostic: " + checks + " assertions passed (no device/capture/sound/LLM; public asset read only).");
    }
}
