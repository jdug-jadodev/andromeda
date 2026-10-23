package local.doja.voice;

import java.util.Calendar;

/** Pure checks for 23:00/06:00 rest-mode arithmetic. */
public final class RestModeScheduleTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new IllegalStateException(message); }
    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(LightSchedule.ZONE);
        calendar.clear(); calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
    private static void expect(Calendar calendar, int hour, int minute) {
        check(calendar.get(Calendar.HOUR_OF_DAY) == hour, "Expected hour " + hour);
        check(calendar.get(Calendar.MINUTE) == minute, "Expected minute " + minute);
    }
    public static void main(String[] args) {
        RestModeSchedule schedule = RestModeSchedule.defaultSchedule();
        check(schedule.nightMinute == 1380 && schedule.morningMinute == 360, "Defaults must be 23:00/06:00");
        check(schedule.days == RestModeSchedule.ALL_DAYS && schedule.enabled, "Default must be every day and enabled");

        RestModeSchedule.Event event = schedule.nextEvent(at(2026, Calendar.OCTOBER, 23, 22, 59));
        check(event != null && event.action == RestModeSchedule.Action.NIGHT, "Before 23:00 next must be night");
        Calendar next = Calendar.getInstance(LightSchedule.ZONE); next.setTimeInMillis(event.wallMs);
        expect(next, 23, 0);

        event = schedule.nextEvent(at(2026, Calendar.OCTOBER, 23, 23, 1));
        check(event != null && event.action == RestModeSchedule.Action.MORNING, "After 23:00 next must be morning");
        next.setTimeInMillis(event.wallMs); expect(next, 6, 0);
        check(next.get(Calendar.DAY_OF_MONTH) == 24, "Morning must be the next day");

        event = schedule.nextEvent(at(2026, Calendar.OCTOBER, 24, 6, 1));
        check(event != null && event.action == RestModeSchedule.Action.NIGHT, "After 06:00 next must be night");
        next.setTimeInMillis(event.wallMs); expect(next, 23, 0);

        event = schedule.nextEvent(at(2026, Calendar.OCTOBER, 24, 5, 59));
        check(event != null && event.action == RestModeSchedule.Action.MORNING, "Before 06:00 next must be morning");
        next.setTimeInMillis(event.wallMs); expect(next, 6, 0);

        check(schedule.matchesNow(RestModeSchedule.Action.NIGHT, at(2026, Calendar.OCTOBER, 23, 23, 0), 15), "Night match");
        check(schedule.matchesNow(RestModeSchedule.Action.NIGHT, at(2026, Calendar.OCTOBER, 23, 22, 59), 15), "One-minute early grace");
        check(!schedule.matchesNow(RestModeSchedule.Action.NIGHT, at(2026, Calendar.OCTOBER, 23, 22, 58), 15), "Too early must not match");
        check(schedule.matchesNow(RestModeSchedule.Action.MORNING, at(2026, Calendar.OCTOBER, 24, 6, 14), 15), "Late morning match");
        check(!schedule.matchesNow(RestModeSchedule.Action.MORNING, at(2026, Calendar.OCTOBER, 24, 6, 16), 15), "Too late must not match");

        boolean rejected = false;
        try { schedule.withNight(schedule.morningMinute); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Equal times must be rejected");
        rejected = false;
        try { new RestModeSchedule(1380, 360, 0, true); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Empty days must be rejected");

        System.out.println("Rest mode schedule tests passed: " + checks + " checks");
    }
}
