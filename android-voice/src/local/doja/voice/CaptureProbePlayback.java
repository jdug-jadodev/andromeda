package local.doja.voice;

import android.content.Context;
import android.media.*;
import android.os.SystemClock;
import local.doja.voice.CaptureProbeStats.PlaybackEnd;

/** Playback of pinned public PCM only. No volume, mode, route or audio-focus changes. */
final class CaptureProbePlayback {
    private AudioTrack track;
    private final CapturePlaybackPolicy policy;
    private final CaptureProbeStats stats;
    private boolean started, closed;
    private long began;
    CaptureProbePlayback(Context context, byte[] pcm, int source, long deadline, CaptureProbeStats stats) {
        this.stats = stats; policy = new CapturePlaybackPolicy(source, deadline, SystemClock.elapsedRealtime());
        AudioManager manager = context.getSystemService(AudioManager.class);
        stats.playbackVolume(manager.getStreamVolume(AudioManager.STREAM_MUSIC), manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        try {
            track = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length).build();
            if (track.getState() == AudioTrack.STATE_UNINITIALIZED || track.write(pcm, 0, pcm.length) != pcm.length
                    || track.setLoopPoints(0, pcm.length / 2, -1) != AudioTrack.SUCCESS) throw new IllegalStateException("Public playback unavailable");
        } catch (RuntimeException failure) { close(PlaybackEnd.ERROR); throw failure; }
    }
    synchronized boolean start() {
        if (closed || !policy.start(SystemClock.elapsedRealtime())) return false;
        track.play();
        began = SystemClock.elapsedRealtime(); started = true;
        if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) throw new IllegalStateException("Public playback did not start");
        stats.playbackStarted(); sample(); return true;
    }
    synchronized void sample() {
        if (track == null || closed) return;
        try {
            AudioDeviceInfo routed = track.getRoutedDevice();
            stats.playbackFrames(Integer.toUnsignedLong(track.getPlaybackHeadPosition()), routed == null ? -1 : routed.getType());
        } catch (RuntimeException unavailable) { /* Nonessential diagnostics must not stop capture. */ }
    }
    synchronized boolean command(int source, boolean validatedFinal) {
        if (closed || !started || track == null || track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING
                || !policy.accept(source, SystemClock.elapsedRealtime(), validatedFinal)) return false;
        close(PlaybackEnd.COMMAND); return true;
    }
    synchronized void close(PlaybackEnd reason) {
        if (closed) return;
        if (track != null) {
            try { sample(); } catch (RuntimeException ignored) {}
            try { if (started) track.pause(); } catch (RuntimeException ignored) {}
            try { if (started) track.stop(); } catch (RuntimeException ignored) {}
            boolean stopped = false;
            try { stopped = track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING; } catch (RuntimeException ignored) {}
            try { track.release(); } catch (RuntimeException ignored) {}
            track = null;
            stats.playbackStopped(reason, reason == PlaybackEnd.COMMAND, started ? Math.max(0, SystemClock.elapsedRealtime() - began) : 0, stopped);
        }
        policy.close(); closed = true;
    }
}
