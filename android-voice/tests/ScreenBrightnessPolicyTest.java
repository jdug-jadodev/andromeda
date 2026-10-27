package local.doja.voice;

import java.util.Calendar;

/** Pure checks for the always-on clock brightness policy. */
public final class ScreenBrightnessPolicyTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static long at(int hour, int minute) {
        Calendar calendar = Calendar.getInstance(LightSchedule.ZONE);
        calendar.clear(); calendar.set(2026, Calendar.OCTOBER, 24, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
    public static void main(String[] args) {
        RestModeSchedule rest = RestModeSchedule.defaultSchedule();
        check(ScreenBrightnessPolicy.desired(rest, at(12, 0), false) == 179, "Day active must be 70%");
        check(ScreenBrightnessPolicy.desired(rest, at(12, 0), true) == 10, "Day idle must be the visible dim level");
        check(ScreenBrightnessPolicy.desired(rest, at(23, 0), false) == 1, "Night active must be minimum");
        check(ScreenBrightnessPolicy.desired(rest, at(5, 59), true) == 1, "Night idle must be minimum");
        check(ScreenBrightnessPolicy.desired(rest, at(6, 0), false) == 179, "Morning active must be 70%");
        check(ScreenBrightnessPolicy.desired(rest, at(22, 59), true) == 10, "Before night idle must be the visible dim level");
        check(ScreenBrightnessPolicy.desired(rest.withEnabled(false), at(23, 0), false) == 179,
            "Disabled rest must not force night minimum");
        check(ScreenBrightnessPolicy.VOICE_IDLE_MS == 20000L, "Voice idle must be 20 seconds");
        check(rest.nightAt(at(23, 0)) && rest.nightAt(at(5, 59))
            && !rest.nightAt(at(22, 59)) && !rest.nightAt(at(6, 0)), "Night window boundaries failed");
        System.out.println("Screen brightness policy: " + checks + " assertions passed.");
    }
}
