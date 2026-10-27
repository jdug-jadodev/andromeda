package local.doja.voice;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Pure greeting period/key logic. The Android marker is in GreetingStore. */
final class GreetingPolicy {
    static final TimeZone ZONE = TimeZone.getTimeZone("America/Bogota");
    enum Period { MORNING, AFTERNOON, NIGHT }

    private GreetingPolicy() {}

    static Period period(long now) {
        Calendar calendar = Calendar.getInstance(ZONE);
        calendar.setTimeInMillis(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour >= 6 && hour < 12) return Period.MORNING;
        if (hour >= 12 && hour < 19) return Period.AFTERNOON;
        return Period.NIGHT;
    }

    static String greeting(long now) {
        switch (period(now)) {
            case MORNING: return "Buenos días.";
            case AFTERNOON: return "Buenas tardes.";
            default: return "Buenas noches.";
        }
    }

    /**
     * One key per period. Night crosses midnight: 23:00 and 00:30 share the
     * previous day's night key, while 06:00 starts a new morning key.
     */
    static String key(long now) {
        Calendar calendar = Calendar.getInstance(ZONE);
        calendar.setTimeInMillis(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (period(now) == Period.NIGHT && hour < 6) calendar.add(Calendar.DAY_OF_YEAR, -1);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(ZONE);
        String date = format.format(calendar.getTime());
        return date + ":" + period(now).name();
    }
}
