package local.doja.voice;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Timing of Santa's answer and the day count. Presentation only; same numbers as the HTML preview. */
final class ChristmasSanta {
    /** Seconds at which each step of the scene ends. */
    static final class Steps {
        final float arrive, open, zoom, count, hold, back, close, stay, leave;
        Steps(float arrive, float open, float zoom, float count, float hold, float back, float close, float stay, float leave) {
            this.arrive = arrive; this.open = open; this.zoom = zoom; this.count = count; this.hold = hold;
            this.back = back; this.close = close; this.stay = stay; this.leave = leave;
        }
    }
    private static final Steps COUNT = new Steps(2.2f, 3.4f, 4.8f, 6.6f, 10.5f, 11.6f, 10.8f, 11.6f, 13.4f);
    // On the day itself the camera backs out early, so the whole scene is seen under the falling gifts.
    private static final Steps TODAY = new Steps(2.2f, 3.4f, 4.8f, 6.6f, 7.4f, 8.5f, 15, 15.6f, 17.6f);

    private ChristmasSanta() {}

    static Steps steps(int days) { return days == 0 ? TODAY : COUNT; }
    static long durationMs(int days) { return Math.round(steps(days).leave * 1000); }

    /** Whole days to the next 24 December; 0 on the day itself. */
    static int daysUntilChristmas(LocalDate today) {
        LocalDate target = LocalDate.of(today.getYear(), 12, 24);
        if (today.isAfter(target)) target = target.plusYears(1);
        return (int)ChronoUnit.DAYS.between(today, target);
    }
}
