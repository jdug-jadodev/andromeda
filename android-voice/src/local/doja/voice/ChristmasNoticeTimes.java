package local.doja.voice;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** When Santa's daily notice is due: 13:00 and 22:00 in Bogotá. Plain Java so it can be tested off the phone. */
final class ChristmasNoticeTimes {
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final int[] HOURS = {13, 22};
    /** A notice the phone delivers later than this (it was off or asleep) is skipped, not played late. */
    static final long LATE_MS = 10 * 60_000L;

    private ChristmasNoticeTimes() {}

    /** The first notice strictly after {@code nowMs}. */
    static long next(long nowMs) { return next(nowMs, HOURS); }

    /** The first of these daily hours (ascending) strictly after {@code nowMs}. */
    static long next(long nowMs, int[] hours) {
        ZonedDateTime now = java.time.Instant.ofEpochMilli(nowMs).atZone(BOGOTA);
        for (int day = 0; day < 2; day++) for (int hour : hours) {
            ZonedDateTime at = now.toLocalDate().plusDays(day).atTime(hour, 0).atZone(BOGOTA);
            if (at.isAfter(now)) return at.toInstant().toEpochMilli();
        }
        throw new IllegalStateException("No notice within two days");
    }

    /** Like next(), but 0 once the next one would fall after {@code lastDay}. */
    static long nextUntil(long nowMs, int[] hours, java.time.LocalDate lastDay) {
        long due = next(nowMs, hours);
        return java.time.Instant.ofEpochMilli(due).atZone(BOGOTA).toLocalDate().isAfter(lastDay) ? 0 : due;
    }

    static boolean onTime(long dueMs, long nowMs) {
        return dueMs > 0 && nowMs >= dueMs - 60_000L && nowMs - dueMs <= LATE_MS;
    }
}
