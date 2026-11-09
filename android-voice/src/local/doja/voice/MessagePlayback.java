package local.doja.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * Reproduce un mensaje de voz por el altavoz del telefono, aunque haya
 * auriculares conectados. Los callbacks llegan siempre en el hilo principal.
 */
final class MessagePlayback {
    enum Result { COMPLETED, FAILED, STOPPED }

    interface Listener {
        void onFinished(String id, Result result);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private String playingId = "";
    private Listener listener;

    MessagePlayback(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean playing() {
        return player != null;
    }

    String currentId() {
        return playingId;
    }

    /** Must be called on the main thread. */
    boolean play(File file, String id, Listener callback) {
        stop();
        if (file == null || !file.isFile() || id == null || id.isEmpty()) return false;
        MediaPlayer created = new MediaPlayer();
        try {
            created.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
            created.setDataSource(file.getAbsolutePath());
            AudioDeviceInfo speaker = speaker();
            if (speaker != null) created.setPreferredDevice(speaker);
            created.setOnCompletionListener(done -> finish(id, Result.COMPLETED));
            created.setOnErrorListener((broken, what, extra) -> {
                finish(id, Result.FAILED);
                return true;
            });
            created.setOnPreparedListener(ready -> {
                if (player != ready) {
                    try {
                        ready.release();
                    } catch (RuntimeException ignored) { }
                    return;
                }
                try {
                    ready.start();
                } catch (RuntimeException failure) {
                    finish(id, Result.FAILED);
                }
            });
            playingId = id;
            listener = callback;
            player = created;
            // Async so a long message never blocks the main thread.
            created.prepareAsync();
            return true;
        } catch (Exception failure) {
            try {
                created.release();
            } catch (RuntimeException ignored) { }
            player = null;
            playingId = "";
            listener = null;
            return false;
        }
    }

    /** Stops the current message; it is reported as STOPPED so it can replay. */
    void stop() {
        MediaPlayer current = player;
        if (current == null) return;
        String id = playingId;
        Listener callback = listener;
        player = null;
        playingId = "";
        listener = null;
        try {
            current.stop();
        } catch (RuntimeException ignored) { }
        try {
            current.release();
        } catch (RuntimeException ignored) { }
        if (callback != null && !id.isEmpty()) callback.onFinished(id, Result.STOPPED);
    }

    private void finish(String id, Result result) {
        MediaPlayer current = player;
        Listener callback = listener;
        player = null;
        playingId = "";
        listener = null;
        if (current != null) {
            try {
                current.release();
            } catch (RuntimeException ignored) { }
        }
        if (callback != null) callback.onFinished(id, result);
    }

    /** Android cannot route MediaPlayer per app, but it can prefer a device. */
    private AudioDeviceInfo speaker() {
        return AudioRoute.preferred(context);
    }

    void shutdown() {
        main.post(this::stop);
    }
}
