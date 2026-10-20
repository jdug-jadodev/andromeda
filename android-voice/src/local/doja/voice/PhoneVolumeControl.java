package local.doja.voice;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;

/** MEDIA only; no alarm/call/ringer/DND changes, root, preview sound or safety bypass. */
final class PhoneVolumeControl {
    static PhoneVolumePolicy.Receipt apply(Context context, PhoneVolumePolicy.Permit permit) {
        AudioManager audio = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        return PhoneVolumePolicy.execute(permit, new PhoneVolumePolicy.Media() {
            public int minimum() { return audio.getStreamMinVolume(AudioManager.STREAM_MUSIC); }
            public int maximum() { return audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); }
            public int current() { return audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
            public void set(int index) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0); }
        }, SystemClock::elapsedRealtime);
    }
}
