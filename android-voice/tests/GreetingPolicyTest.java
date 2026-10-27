package local.doja.voice;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Greeting period and once-per-period marker key checks. */
public final class GreetingPolicyTest {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public static void main(String[] args) {
        expect(GreetingPolicy.Period.NIGHT, GreetingPolicy.period(at(10, 24, 5, 59)), "pre-dawn night");
        expect(GreetingPolicy.Period.MORNING, GreetingPolicy.period(at(10, 24, 6, 0)), "morning boundary");
        expect(GreetingPolicy.Period.MORNING, GreetingPolicy.period(at(10, 24, 11, 59)), "late morning");
        expect(GreetingPolicy.Period.AFTERNOON, GreetingPolicy.period(at(10, 24, 12, 0)), "afternoon boundary");
        expect(GreetingPolicy.Period.AFTERNOON, GreetingPolicy.period(at(10, 24, 18, 59)), "late afternoon");
        expect(GreetingPolicy.Period.NIGHT, GreetingPolicy.period(at(10, 24, 19, 0)), "night boundary");

        expect("Buenos días.", GreetingPolicy.greeting(at(10, 24, 8, 0)), "morning text");
        expect("Buenas tardes.", GreetingPolicy.greeting(at(10, 24, 15, 0)), "afternoon text");
        expect("Buenas noches.", GreetingPolicy.greeting(at(10, 24, 23, 0)), "night text");

        String night = GreetingPolicy.key(at(10, 24, 23, 0));
        String afterMidnight = GreetingPolicy.key(at(10, 25, 0, 30));
        String beforeDawn = GreetingPolicy.key(at(10, 25, 5, 59));
        String morning = GreetingPolicy.key(at(10, 25, 6, 0));
        if (!night.equals(afterMidnight)) throw new AssertionError("night period must cross midnight");
        if (!afterMidnight.equals(beforeDawn)) throw new AssertionError("pre-dawn must share the night key");
        if (beforeDawn.equals(morning)) throw new AssertionError("morning must start a new key");

        String afternoon = GreetingPolicy.key(at(10, 24, 12, 0));
        if (night.equals(afternoon)) throw new AssertionError("afternoon and night keys must differ");
        System.out.println("Greeting period tests passed.");
    }

    private static long at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, BOGOTA).toInstant().toEpochMilli();
    }

    private static void expect(Object wanted, Object actual, String label) {
        if (!wanted.equals(actual)) throw new AssertionError(label + ": expected " + wanted + " but was " + actual);
    }
}
