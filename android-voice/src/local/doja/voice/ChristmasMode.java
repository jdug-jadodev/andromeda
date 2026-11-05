package local.doja.voice;

import android.content.Context;
import android.provider.Settings;

/**
 * The Christmas parameter. Presentation only: it never touches voice, tools or the timing of a turn.
 * adb shell settings put global andromeda_navidad 1   (0 or absent: off). Applies within a second, no reinstall.
 */
final class ChristmasMode {
    static final String KEY = "andromeda_navidad";
    static final long BACKDROP_HOLD_MS = 2000, BACKDROP_FADE_MS = 700;
    /** Each side of the clock screen kept free for the decorations, as a fraction of its width. */
    static final float CLOCK_SIDE = .295f;

    private ChristmasMode() {}

    /** Santa only plays with the parameter on and the system animations running. */
    static boolean playsSanta(Context context) {
        return enabled(context) && Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0;
    }

    static boolean enabled(Context context) {
        try { return Settings.Global.getInt(context.getContentResolver(), KEY, 0) == 1; }
        catch (RuntimeException unavailable) { return false; }
    }
}
