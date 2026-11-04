package local.doja.voice;

import java.time.LocalDate;

/** The day count must match backend/christmas_days.py, and the scene must last what the screen holds for it. */
public final class ChristmasSantaTest {
    public static void main(String[] args) {
        days(50, 2026, 11, 4); days(49, 2026, 11, 5); days(1, 2026, 12, 23); days(0, 2026, 12, 24);
        days(364, 2026, 12, 25); days(357, 2027, 1, 1); days(299, 2028, 2, 29);
        expect(ChristmasSanta.durationMs(50) == 13400, "count scene length");
        expect(ChristmasSanta.durationMs(0) == 17600, "Christmas day scene length");
        expect(FaceReaction.parseScene("SANTA") == FaceReaction.SANTA, "the backend name reaches the scene");
        expect(FaceReaction.parseScene("SANTA_TODAY").santa() && FaceReaction.SANTA.santa() && !FaceReaction.EVIL_BOMB.santa(), "the simulation is a Santa scene");
        expect(FaceReaction.SANTA.speechDelayMs() < ChristmasSanta.durationMs(50), "she speaks during the scene");
        System.out.println("Christmas Santa tests passed.");
    }

    private static void days(int wanted, int year, int month, int day) {
        expect(ChristmasSanta.daysUntilChristmas(LocalDate.of(year, month, day)) == wanted, year + "-" + month + "-" + day);
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
