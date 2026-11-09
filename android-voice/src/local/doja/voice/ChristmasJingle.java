package local.doja.voice;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;

/**
 * The jingle of Santa's bag: starts as his hand unties it and drops while she speaks, so her words stay clear.
 * Same timing as artifacts/andromeda-face-preview.html. Playback only; it never records or opens the microphone.
 */
final class ChristmasJingle {
    static final String ASSET = "navidad-bolsa.mp3";
    static final long START_MS = 2000, DUCK_MS = 400;
    static final float DUCKED = .3f;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable start = this::start;
    private MediaPlayer player;
    private long duckStarted;
    private final Runnable duck = new Runnable() {
        public void run() {
            if (player == null) return;
            if (duckStarted == 0) duckStarted = android.os.SystemClock.uptimeMillis();
            float done = Math.min(1, (android.os.SystemClock.uptimeMillis() - duckStarted) / (float)DUCK_MS);
            float volume = 1 - (1 - DUCKED) * done;
            player.setVolume(volume, volume);
            if (done < 1) handler.postDelayed(this, 40);
        }
    };

    ChristmasJingle(Context context) { this.context = context.getApplicationContext(); }

    /** speakAtMs: when she starts talking, from the start of the scene. */
    void play(long speakAtMs) {
        stop();
        handler.postDelayed(start, START_MS);
        handler.postDelayed(duck, Math.max(START_MS, speakAtMs));
    }

    boolean isPlaying() {
        try { return player != null && player.isPlaying(); }
        catch (IllegalStateException released) { return false; }
    }

    void stop() {
        handler.removeCallbacks(start); handler.removeCallbacks(duck); duckStarted = 0;
        if (player != null) { player.release(); player = null; }
    }

    private void start() {
        try (AssetFileDescriptor file = context.getAssets().openFd(ASSET)) {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            // Si hay audifonos, el jingle va con ellos; si no, al altavoz.
            AudioDeviceInfo output = speaker();
            if (output != null) next.setPreferredDevice(output);
            next.setVolume(1f, 1f);
            next.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            next.setOnCompletionListener(done -> { if (player == done) stop(); });
            next.prepare(); next.start();
            Log.i("AndromedaJingle", "playing on " + (output == null ? "default" : output.getType()));
        } catch (IOException | RuntimeException unavailable) {
            Log.w("AndromedaJingle", "unavailable " + unavailable.getClass().getSimpleName());
            stop();   // without the jingle the scene still plays
        }
    }

    private AudioDeviceInfo speaker() {
        return AudioRoute.preferred(context);
    }
}
