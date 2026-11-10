package local.doja.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drop-in replacement for the voice's TextToSpeech that always renders through
 * the phone's built-in speaker, even while a wired headset is connected.
 *
 * Capture is deliberately untouched: the recorder keeps whichever device
 * Android chooses, so a plugged headset microphone is used for input while her
 * answer still comes out of the phone speaker. Android exposes no per-device
 * route for TextToSpeech, but it does for AudioTrack, so the engine only
 * synthesizes each line to a temporary WAV and playback goes through an
 * AudioTrack whose preferred device is TYPE_BUILTIN_SPEAKER.
 *
 * The public UtteranceProgressListener keeps the meaning the service relies on:
 * onStart = audible playback began, onDone = playback finished, onError/onStop =
 * nothing more will be heard. All state is the public utterance id, never audio
 * content, and temporary files are deleted as soon as playback ends.
 */
final class SpeakerSpeech {
    private final Context context;
    private final TextToSpeech engine;
    private final ExecutorService player = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "andromeda-speaker-output");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, String> publicIds = new ConcurrentHashMap<>();
    private final Map<String, File> files = new ConcurrentHashMap<>();
    private final Map<String, File> copies = new ConcurrentHashMap<>();
    private final Map<String, Runnable> completions = new ConcurrentHashMap<>();
    private final Set<String> silentIds = ConcurrentHashMap.newKeySet();
    private final Object trackLock = new Object();
    private volatile UtteranceProgressListener listener;
    private volatile AudioTrack playing;
    private volatile String audibleId = "";
    private volatile int lastRoutedType;
    private volatile boolean released;

    SpeakerSpeech(Context context, TextToSpeech.OnInitListener ready, String engineName) {
        this.context = context.getApplicationContext();
        engine = new TextToSpeech(this.context, ready, engineName);
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String synthId) { /* Synthesis only; audible start follows playback. */ }
            @Override public void onDone(String synthId) { synthesized(synthId); }
            @Override public void onError(String synthId) { failed(synthId); }
            @Override public void onError(String synthId, int errorCode) { failed(synthId); }
            @Override public void onStop(String synthId, boolean interrupted) { stopped(synthId); }
        });
    }

    Set<Voice> getVoices() { return engine.getVoices(); }
    int setVoice(Voice voice) { return engine.setVoice(voice); }
    int setLanguage(Locale locale) { return engine.setLanguage(locale); }
    void setOnUtteranceProgressListener(UtteranceProgressListener value) { listener = value; }

    /** Device the last audible track was routed to; 0 when no track played. Diagnostics only. */
    int routedOutputType() { return lastRoutedType; }

    int speak(CharSequence text, int queueMode, Bundle params, String utteranceId) {
        return speak(text, queueMode, params, utteranceId, null, null);
    }

    /**
     * Same playback, plus a durable copy of the rendered WAV when `copyTo` is
     * given. Used only for remote replies the companion app may download.
     */
    int speak(CharSequence text, int queueMode, Bundle params, String utteranceId, File copyTo) {
        return speak(text, queueMode, params, utteranceId, copyTo, null);
    }

    /** `after` runs once the line ends, fails or is stopped. */
    int speak(CharSequence text, int queueMode, Bundle params, String utteranceId, File copyTo,
            Runnable after) {
        return speak(text, queueMode, params, utteranceId, copyTo, after, false);
    }

    /**
     * A silent line is synthesized (and copied) but never heard in the house:
     * remote answers go only to the phone that asked.
     */
    int speak(CharSequence text, int queueMode, Bundle params, String utteranceId, File copyTo,
            Runnable after, boolean silent) {
        if (released || text == null || utteranceId == null) return TextToSpeech.ERROR;
        flushPlayback();
        try { engine.stop(); } catch (RuntimeException ignored) { }
        File file = new File(context.getCacheDir(), "andromeda-speech-" + UUID.randomUUID() + ".wav");
        String synthId = "synth-" + UUID.randomUUID();
        publicIds.put(synthId, utteranceId);
        files.put(synthId, file);
        if (copyTo != null) copies.put(synthId, copyTo);
        if (after != null) completions.put(utteranceId, after);
        if (silent) silentIds.add(utteranceId);
        audibleId = utteranceId;
        try {
            if (engine.synthesizeToFile(text, params, file, synthId) == TextToSpeech.ERROR) {
                publicIds.remove(synthId);
                files.remove(synthId);
                copies.remove(synthId);
                completions.remove(utteranceId);
                silentIds.remove(utteranceId);
                deleteQuietly(file);
                return TextToSpeech.ERROR;
            }
            return TextToSpeech.SUCCESS;
        } catch (RuntimeException failure) {
            publicIds.remove(synthId);
            files.remove(synthId);
            copies.remove(synthId);
            completions.remove(utteranceId);
            silentIds.remove(utteranceId);
            deleteQuietly(file);
            return TextToSpeech.ERROR;
        }
    }

    /** Stops synthesis and audible playback. A public onStop follows, like the engine. */
    int stop() {
        String stoppedId = flushPlayback();
        try { engine.stop(); } catch (RuntimeException ignored) { }
        if (!stoppedId.isEmpty()) notifyStop(stoppedId);
        return TextToSpeech.SUCCESS;
    }

    void shutdown() {
        released = true;
        flushPlayback();
        player.shutdownNow();
        for (File file : files.values()) deleteQuietly(file);
        files.clear();
        copies.clear();
        publicIds.clear();
        try { engine.shutdown(); } catch (RuntimeException ignored) { }
    }

    private void synthesized(String synthId) {
        String publicId = publicIds.remove(synthId);
        File file = files.remove(synthId);
        File copy = copies.remove(synthId);
        if (publicId == null || file == null) { deleteQuietly(file); return; }
        if (!publicId.equals(audibleId) || released) { deleteQuietly(file); return; }
        if (copy != null) copyQuietly(file, copy);
        try {
            player.execute(() -> play(publicId, file));
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            deleteQuietly(file);
        }
    }

    private void failed(String synthId) {
        String publicId = publicIds.remove(synthId);
        File file = files.remove(synthId);
        copies.remove(synthId);
        deleteQuietly(file);
        if (publicId != null && publicId.equals(audibleId) && !released) notifyError(publicId);
    }

    private void stopped(String synthId) {
        String publicId = publicIds.remove(synthId);
        File file = files.remove(synthId);
        copies.remove(synthId);
        deleteQuietly(file);
        if (publicId != null && publicId.equals(audibleId) && !released) notifyStop(publicId);
    }

    /** Silences the current line and invalidates its pending completion. */
    private String flushPlayback() {
        synchronized (trackLock) {
            AudioTrack track = playing;
            playing = null;
            if (track != null) {
                try { track.pause(); track.flush(); track.stop(); } catch (RuntimeException ignored) { }
                try { track.release(); } catch (RuntimeException ignored) { }
            }
        }
        String previous = audibleId;
        audibleId = "";
        return previous;
    }

    private void play(String publicId, File file) {
        AudioTrack track = null;
        try {
            Pcm pcm = Pcm.read(file);
            int encoding = pcm.bits == 8 ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;
            int channels = pcm.channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int minimum = AudioTrack.getMinBufferSize(pcm.sampleRate, channels, encoding);
            if (minimum <= 0) throw new IOException("Unsupported synthesis format");
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(encoding)
                    .setSampleRate(pcm.sampleRate).setChannelMask(channels).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(minimum, 8192)).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("Speaker output unavailable");
            AudioDeviceInfo speaker = speaker();
            if (speaker != null) track.setPreferredDevice(speaker);
            synchronized (trackLock) {
                if (!publicId.equals(audibleId) || released) throw new IOException("Playback cancelled");
                playing = track;
            }
            track.play();
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) throw new IOException("Playback did not start");
            if (silentIds.remove(publicId)) {
                track.setVolume(0f);
                android.util.Log.i("AndromedaSpeech", "silent reply " + publicId);
            }
            AudioDeviceInfo routed = track.getRoutedDevice();
            if (routed != null) {
                lastRoutedType = routed.getType();
                android.util.Log.i("AndromedaSpeech", "route=" + routed.getType());
            }
            notifyStart(publicId);
            try (InputStream input = new FileInputStream(file)) {
                skipFully(input, pcm.dataOffset);
                byte[] buffer = new byte[8192];
                int remaining = pcm.dataLength, count;
                while (remaining > 0 && publicId.equals(audibleId) && !released
                        && (count = input.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                    writeAll(track, publicId, buffer, count);
                    remaining -= count;
                }
                if (remaining > 0) throw new IOException("Playback cancelled");
            }
            track.stop();
            if (publicId.equals(audibleId) && !released) notifyDone(publicId);
        } catch (Exception failure) {
            // A flush already emptied audibleId, so a cancelled line reports nothing.
            if (publicId.equals(audibleId) && !released) notifyError(publicId);
        } finally {
            boolean owned;
            synchronized (trackLock) { owned = playing == track; if (owned) playing = null; }
            if (owned) try { track.release(); } catch (RuntimeException ignored) { }
            deleteQuietly(file);
        }
    }

    /** Writes under the route lock so a flush never releases the track mid-write. */
    private void writeAll(AudioTrack track, String publicId, byte[] buffer, int count) throws IOException {
        int written = 0;
        while (written < count) {
            synchronized (trackLock) {
                if (playing != track || !publicId.equals(audibleId) || released) throw new IOException("Playback cancelled");
                int block = track.write(buffer, written, count - written);
                if (block <= 0) throw new IOException("Playback rejected");
                written += block;
            }
        }
    }

    private AudioDeviceInfo speaker() {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return null;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return device;
        }
        return null;
    }

    private void notifyStart(String id) { UtteranceProgressListener value = listener; if (value != null) try { value.onStart(id); } catch (RuntimeException ignored) { } }
    private void notifyDone(String id) { UtteranceProgressListener value = listener; if (value != null) try { value.onDone(id); } catch (RuntimeException ignored) { } finished(id); }
    private void notifyError(String id) { UtteranceProgressListener value = listener; if (value != null) try { value.onError(id); } catch (RuntimeException ignored) { } finished(id); }
    private void notifyStop(String id) { UtteranceProgressListener value = listener; if (value != null) try { value.onStop(id, true); } catch (RuntimeException ignored) { } finished(id); }

    /** Runs the queued continuation once, whatever ended the line. */
    private void finished(String id) {
        Runnable after = completions.remove(id);
        if (after != null) try { after.run(); } catch (RuntimeException ignored) { }
    }

    private static void deleteQuietly(File file) {
        if (file != null) try { file.delete(); } catch (RuntimeException ignored) { }
    }

    /** A failed copy never blocks playback; the phone simply gets no WAV. */
    private static void copyQuietly(File source, File target) {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
        } catch (Exception failure) {
            deleteQuietly(target);
        }
    }

    private static void skipFully(InputStream input, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) throw new IOException("Truncated WAV");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    /** Minimal RIFF reader for the 16-bit mono WAV produced by the TTS engine. */
    private static final class Pcm {
        int sampleRate, channels, bits;
        long dataOffset;
        int dataLength;

        static Pcm read(File file) throws IOException {
            Pcm pcm = new Pcm();
            try (InputStream input = new FileInputStream(file)) {
                byte[] riff = new byte[12];
                readFully(input, riff);
                if (riff[0] != 'R' || riff[1] != 'I' || riff[2] != 'F' || riff[3] != 'F') throw new IOException("Not a WAV");
                long consumed = 12;
                byte[] chunk = new byte[8];
                while (true) {
                    readFully(input, chunk);
                    String id = new String(chunk, 0, 4, "US-ASCII");
                    int size = little32(chunk, 4);
                    consumed += 8;
                    if ("data".equals(id)) {
                        pcm.dataOffset = consumed;
                        pcm.dataLength = size;
                        if (pcm.sampleRate <= 0 || pcm.channels <= 0 || pcm.bits <= 0) throw new IOException("Missing WAV format");
                        return pcm;
                    }
                    if ("fmt ".equals(id)) {
                        if (size < 16 || size > 1024) throw new IOException("Invalid WAV format");
                        byte[] format = new byte[size];
                        readFully(input, format);
                        consumed += size;
                        pcm.channels = little16(format, 2);
                        pcm.sampleRate = little32(format, 4);
                        pcm.bits = little16(format, 14);
                    } else {
                        skipFully(input, size);
                        consumed += size;
                    }
                    if ((size & 1) != 0) { skipFully(input, 1); consumed++; }
                }
            }
        }

        private static void readFully(InputStream input, byte[] buffer) throws IOException {
            int offset = 0;
            while (offset < buffer.length) {
                int count = input.read(buffer, offset, buffer.length - offset);
                if (count < 0) throw new IOException("Truncated WAV");
                offset += count;
            }
        }

        private static int little16(byte[] value, int offset) {
            return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
        }

        private static int little32(byte[] value, int offset) {
            return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8)
                | ((value[offset + 2] & 0xff) << 16) | ((value[offset + 3] & 0xff) << 24);
        }
    }
}
