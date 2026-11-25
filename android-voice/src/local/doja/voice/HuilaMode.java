package local.doja.voice;

import android.content.Context;
import android.provider.Settings;
import java.time.LocalDate;

/**
 * The trip-to-Huila countdown (11 December 2026). Presentation only, like ChristmasMode.
 * adb shell settings put global andromeda_huila 1   (0 or absent: off). After the trip it stops by itself.
 */
final class HuilaMode {
    static final String KEY = "andromeda_huila";
    static final LocalDate TRIP = LocalDate.of(2026, 12, 11);
    static final long DURATION_MS = Math.round(HuilaArt.END * 1000);

    private HuilaMode() {}

    static boolean enabled(Context context) {
        try { return Settings.Global.getInt(context.getContentResolver(), KEY, 0) == 1; }
        catch (RuntimeException unavailable) { return false; }
    }

    /** The scene only plays with the parameter on and the system animations running. */
    static boolean plays(Context context) {
        return enabled(context) && Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0;
    }

    /** Days from today to the trip; negative once it has passed. Must match backend/huila_trip.py. */
    static int daysUntilTrip(LocalDate today) { return (int) (TRIP.toEpochDay() - today.toEpochDay()); }
}
