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
    static long next(long nowMs) {
        ZonedDateTime now = java.time.Instant.ofEpochMilli(nowMs).atZone(BOGOTA);
        for (int day = 0; day < 2; day++) for (int hour : HOURS) {
            ZonedDateTime at = now.toLocalDate().plusDays(day).atTime(hour, 0).atZone(BOGOTA);
            if (at.isAfter(now)) return at.toInstant().toEpochMilli();
        }
        throw new IllegalStateException("No notice within two days");
    }

    static boolean onTime(long dueMs, long nowMs) {
        return dueMs > 0 && nowMs >= dueMs - 60_000L && nowMs - dueMs <= LATE_MS;
    }
}
