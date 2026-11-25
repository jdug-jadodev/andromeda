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
 * The scene's sound (diesel, brakes, honk, door, bursts into the air, running feet, explosion), baked from the prototype into one
 * file whose time is the scene's time. It drops when she speaks, after the explosion. Playback only.
 */
final class HuilaSound {
    static final String ASSET = "huila-viaje.wav";
    /** Drops just before she speaks (FaceReaction.HUILA) and stays down to the end. */
    static final long DUCK_AT_MS = 10200;
    static final float DUCKED = .35f;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable duck = () -> volume(DUCKED);
    private MediaPlayer player;

    HuilaSound(Context context) { this.context = context.getApplicationContext(); }

    void play() {
        stop();
        try (AssetFileDescriptor file = context.getAssets().openFd(ASSET)) {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            AudioDeviceInfo output = speaker();
            if (output != null) next.setPreferredDevice(output);   // always the loudspeaker, like her voice
            next.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            next.setOnCompletionListener(done -> { if (player == done) stop(); });
            next.prepare(); next.start();
            handler.postDelayed(duck, DUCK_AT_MS);
        } catch (IOException | RuntimeException unavailable) {
            Log.w("AndromedaHuila", "sound unavailable " + unavailable.getClass().getSimpleName());
            stop();   // the scene still plays without it
        }
    }

    boolean isPlaying() {
        try { return player != null && player.isPlaying(); }
        catch (IllegalStateException released) { return false; }
    }

    void stop() {
        handler.removeCallbacks(duck);
        if (player != null) { player.release(); player = null; }
    }

    private void volume(float value) {
        try { if (player != null) player.setVolume(value, value); } catch (IllegalStateException ignored) { }
    }

    private AudioDeviceInfo speaker() {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return null;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return device;
        return null;
    }
}
