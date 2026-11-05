package local.doja.voice;

import java.time.ZonedDateTime;

/** Santa's notice comes at 13:00 and 22:00 Bogotá, and is skipped rather than played late. */
public final class ChristmasNoticeTest {
    public static void main(String[] args) {
        next("2026-11-05T09:00", "2026-11-05T13:00");
        next("2026-11-05T13:00", "2026-11-05T22:00");
        next("2026-11-05T13:00:01", "2026-11-05T22:00");
        next("2026-11-05T22:00", "2026-11-06T13:00");
        next("2026-11-05T23:30", "2026-11-06T13:00");
        next("2026-12-31T22:30", "2027-01-01T13:00");
        long due = at("2026-11-05T13:00");
        expect(ChristmasNoticeTimes.onTime(due, due), "on the hour");
        expect(ChristmasNoticeTimes.onTime(due, due + 9 * 60_000L), "a few minutes late");
        expect(!ChristmasNoticeTimes.onTime(due, due + 11 * 60_000L), "too late is skipped");
        expect(!ChristmasNoticeTimes.onTime(due, due - 5 * 60_000L), "too early is skipped");
        expect(!ChristmasNoticeTimes.onTime(0, due), "an alarm without a time is skipped");
        System.out.println("Christmas notice tests passed.");
    }

    private static long at(String local) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(local), ChristmasNoticeTimes.BOGOTA).toInstant().toEpochMilli();
    }

    private static void next(String now, String wanted) {
        expect(ChristmasNoticeTimes.next(at(now)) == at(wanted), now + " -> " + wanted);
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
