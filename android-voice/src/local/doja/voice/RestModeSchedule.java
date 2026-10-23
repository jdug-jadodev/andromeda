package local.doja.voice;

import java.util.Calendar;

/** Pure daily rest-mode schedule: 23:00 night and 06:00 morning. */
final class RestModeSchedule {
    enum Action { NIGHT, MORNING }
    static final int DEFAULT_NIGHT = 23 * 60;
    static final int DEFAULT_MORNING = 6 * 60;
    static final int ALL_DAYS = LightSchedule.ALL_DAYS;

    final int nightMinute, morningMinute, days;
    final boolean enabled;

    RestModeSchedule(int nightMinute, int morningMinute, int days, boolean enabled) {
        if (nightMinute < 0 || nightMinute > 1439 || morningMinute < 0 || morningMinute > 1439)
            throw new IllegalArgumentException("Hora de descanso fuera de rango");
        if (nightMinute == morningMinute) throw new IllegalArgumentException("Descanso y despertar no pueden coincidir");
        if (days < 1 || days > ALL_DAYS) throw new IllegalArgumentException("Días inválidos");
        this.nightMinute = nightMinute; this.morningMinute = morningMinute;
        this.days = days; this.enabled = enabled;
    }

    static RestModeSchedule defaultSchedule() {
        return new RestModeSchedule(DEFAULT_NIGHT, DEFAULT_MORNING, ALL_DAYS, true);
    }

    static final class Event {
        final long wallMs;
        final Action action;
        Event(long wallMs, Action action) { this.wallMs = wallMs; this.action = action; }
    }

    Event nextEvent(long nowWall) {
        if (nowWall <= 0) throw new IllegalArgumentException("Reloj inválido");
        Calendar base = Calendar.getInstance(LightSchedule.ZONE);
        base.setTimeInMillis(nowWall);
        Event best = null;
        for (int offset = 0; offset <= 8; offset++) {
            Calendar day = (Calendar) base.clone();
            day.add(Calendar.DAY_OF_YEAR, offset);
            if ((days & LightSchedule.dayBit(day.get(Calendar.DAY_OF_WEEK))) == 0) continue;
            for (int which = 0; which < 2; which++) {
                Action action = which == 0 ? Action.NIGHT : Action.MORNING;
                int minute = action == Action.NIGHT ? nightMinute : morningMinute;
                Calendar candidate = (Calendar) day.clone();
                candidate.set(Calendar.HOUR_OF_DAY, minute / 60);
                candidate.set(Calendar.MINUTE, minute % 60);
                candidate.set(Calendar.SECOND, 0);
                candidate.set(Calendar.MILLISECOND, 0);
                long value = candidate.getTimeInMillis();
                if (value > nowWall && (best == null || value < best.wallMs))
                    best = new Event(value, action);
            }
        }
        return best;
    }

    boolean matchesNow(Action action, long nowWall, int toleranceMinutes) {
        if (action == null || toleranceMinutes < 0) return false;
        Calendar now = Calendar.getInstance(LightSchedule.ZONE);
        now.setTimeInMillis(nowWall);
        if ((days & LightSchedule.dayBit(now.get(Calendar.DAY_OF_WEEK))) == 0) return false;
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int expected = action == Action.NIGHT ? nightMinute : morningMinute;
        int delta = (current - expected + 1440) % 1440;
        if (delta > 720) delta -= 1440;
        return delta >= -1 && delta <= toleranceMinutes;
    }

    RestModeSchedule withNight(int minute) {
        return new RestModeSchedule(minute, morningMinute, days, enabled);
    }
    RestModeSchedule withMorning(int minute) {
        return new RestModeSchedule(nightMinute, minute, days, enabled);
    }
    RestModeSchedule withEnabled(boolean value) {
        return new RestModeSchedule(nightMinute, morningMinute, days, value);
    }
}
