package local.doja.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;

/**
 * Short "speak now" cue played when the microphone is live again and she is
 * waiting for the human. It is routed to the built-in speaker on purpose, so a
 * plugged headset (used for the microphone) never hides the cue. It never opens
 * the microphone and never changes volume, mode or audio focus.
 */
final class ListeningCue {
    private static final int RATE = 22050;
    private static final int MILLIS = 130;
    private static final double HZ = 880;
    private static final Object LOCK = new Object();
    private static long lastAt;
    private static volatile int lastRoutedType;
    private static volatile int lastStep;

    private ListeningCue() {}

    /** Device the last cue was routed to; 0 when it never played. Diagnostics only. */
    static int routedOutputType() { return lastRoutedType; }

    /** Last completed stage: 1 initialized, 2 written, 3 played. Diagnostics only. */
    static int step() { return lastStep; }

    static void play(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> tone(app), "andromeda-ready-cue");
        thread.setDaemon(true);
        thread.start();
    }

    private static void tone(Context context) {
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            // A restart inside the same turn must not repeat the cue.
            if (now - lastAt < 800) return;
            lastAt = now;
        }
        AudioTrack track = null;
        try {
            int frames = RATE * MILLIS / 1000;
            short[] pcm = new short[frames];
            double fadeFrames = RATE * 0.012;
            for (int index = 0; index < frames; index++) {
                double fade = Math.min(1, Math.min(index, frames - index) / fadeFrames);
                pcm[index] = (short) (Math.sin(2 * Math.PI * HZ * index / RATE) * 0.30 * fade * Short.MAX_VALUE);
            }
            int minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) return;
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(minimum, pcm.length * 2)).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) return;
            lastStep = 1;
            AudioManager audio = context.getSystemService(AudioManager.class);
            if (audio != null) for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { track.setPreferredDevice(device); break; }
            }
            int written = 0;
            while (written < pcm.length) {
                int block = track.write(pcm, written, pcm.length - written);
                if (block <= 0) return;
                written += block;
            }
            lastStep = 2;
            track.play();
            for (int attempt = 0; attempt < 20; attempt++) {
                AudioDeviceInfo routed = track.getRoutedDevice();
                if (routed != null) { lastRoutedType = routed.getType(); break; }
                SystemClock.sleep(10);
            }
            lastStep = 3;
            SystemClock.sleep(MILLIS + 70L);
            track.stop();
        } catch (RuntimeException ignored) {
            // A missing cue must never disturb capture.
        } finally {
            if (track != null) try { track.release(); } catch (RuntimeException ignored) { }
        }
    }
}
