package local.doja.voice;

import java.util.Calendar;

/** Independent checks for the pure daily schedule arithmetic. */
public final class LightScheduleTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new IllegalStateException(message);
    }
    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(LightSchedule.ZONE);
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
    private static void expect(Calendar calendar, int hour, int minute) {
        check(calendar.get(Calendar.HOUR_OF_DAY) == hour, "Expected hour " + hour + " but got " + calendar.get(Calendar.HOUR_OF_DAY));
        check(calendar.get(Calendar.MINUTE) == minute, "Expected minute " + minute + " but got " + calendar.get(Calendar.MINUTE));
    }
    public static void main(String[] args) {
        LightSchedule schedule = LightSchedule.defaultFor(LightTarget.BOTH);
        check(schedule.onMinute == 1050 && schedule.offMinute == 1320, "Default times must be 17:30/22:00");
        check(schedule.days == LightSchedule.ALL_DAYS && schedule.enabled, "Default must be every day and enabled");

        LightSchedule.Event event = schedule.nextEvent(at(2026, Calendar.OCTOBER, 23, 12, 0));
        check(event != null && event.on, "At noon the next event must be on");
        Calendar next = Calendar.getInstance(LightSchedule.ZONE); next.setTimeInMillis(event.wallMs);
        expect(next, 17, 30);

        event = schedule.nextEvent(at(2026, Calendar.OCTOBER, 23, 17, 31));
        check(event != null && !event.on, "After 17:30 the next event must be off");
        next.setTimeInMillis(event.wallMs); expect(next, 22, 0);

        event = schedule.nextEvent(at(2026, Calendar.OCTOBER, 23, 22, 1));
        check(event != null && event.on, "After 22:00 the next event must be next day on");
        next.setTimeInMillis(event.wallMs); expect(next, 17, 30);
        check(next.get(Calendar.DAY_OF_MONTH) == 24, "Next event must be the following day");

        LightSchedule weekdays = schedule.withDays(LightSchedule.WEEKDAYS);
        event = weekdays.nextEvent(at(2026, Calendar.OCTOBER, 23, 22, 1));
        check(event != null && event.on, "Weekday schedule must have a next event");
        next.setTimeInMillis(event.wallMs); expect(next, 17, 30);
        check(next.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY, "Friday night must jump to Monday");

        LightSchedule weekend = schedule.withDays(LightSchedule.WEEKEND);
        event = weekend.nextEvent(at(2026, Calendar.OCTOBER, 23, 22, 1));
        check(event != null && event.on, "Weekend schedule must have a next event");
        next.setTimeInMillis(event.wallMs); expect(next, 17, 30);
        check(next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY, "Friday night must jump to Saturday");

        check(schedule.matchesNow(true, at(2026, Calendar.OCTOBER, 23, 17, 30), 15), "On alarm must match");
        check(schedule.matchesNow(true, at(2026, Calendar.OCTOBER, 23, 17, 29), 15), "One-minute early grace must match");
        check(!schedule.matchesNow(true, at(2026, Calendar.OCTOBER, 23, 17, 28), 15), "Early alarm must not match");
        check(!schedule.matchesNow(true, at(2026, Calendar.OCTOBER, 23, 17, 46), 15), "Late alarm must not match");
        check(!weekend.matchesNow(true, at(2026, Calendar.OCTOBER, 23, 17, 30), 5), "Wrong day must not match");

        check("5:30 p. m.".equals(LightSchedule.format(1050)), "12-hour formatting failed");
        check("10:00 p. m.".equals(LightSchedule.format(1320)), "12-hour formatting failed");
        check("todos los días".equals(LightSchedule.daysLabel(LightSchedule.ALL_DAYS)), "Day label failed");

        boolean rejected = false;
        try { schedule.withOn(schedule.offMinute); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Equal on/off times must be rejected");
        rejected = false;
        try { new LightSchedule("not-an-id", LightTarget.BOTH, 1050, 1320, LightSchedule.ALL_DAYS, true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Malformed schedule id must be rejected");
        rejected = false;
        try { schedule.withDays(0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Empty day mask must be rejected");

        System.out.println("Light schedule tests passed: " + checks + " checks");
    }
}
