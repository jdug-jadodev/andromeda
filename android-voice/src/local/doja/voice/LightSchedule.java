package local.doja.voice;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** Pure daily light schedule. No Android, BLE, network, audio or storage. */
final class LightSchedule {
    static final int ALL_DAYS = 0x7F;
    static final int WEEKDAYS = 0x1F;
    static final int WEEKEND = 0x60;
    static final TimeZone ZONE = TimeZone.getTimeZone("America/Bogota");

    final String id;
    final LightTarget target;
    final int onMinute;
    final int offMinute;
    final int days;
    final boolean enabled;

    LightSchedule(String id, LightTarget target, int onMinute, int offMinute, int days, boolean enabled) {
        if (!validId(id)) throw new IllegalArgumentException("ID de horario inválido");
        if (target == null) throw new IllegalArgumentException("Falta el objetivo");
        if (onMinute < 0 || onMinute > 1439 || offMinute < 0 || offMinute > 1439)
            throw new IllegalArgumentException("Hora fuera de rango");
        if (onMinute == offMinute) throw new IllegalArgumentException("Encendido y apagado no pueden coincidir");
        if (days < 1 || days > ALL_DAYS) throw new IllegalArgumentException("Días inválidos");
        this.id = id; this.target = target; this.onMinute = onMinute; this.offMinute = offMinute;
        this.days = days; this.enabled = enabled;
    }

    static boolean validId(String value) {
        return value != null && value.matches("[0-9a-f]{32}");
    }

    static LightSchedule defaultFor(LightTarget target) {
        if (target == null) throw new IllegalArgumentException("Falta el objetivo");
        return new LightSchedule(UUID.randomUUID().toString().replace("-", ""), target,
            17 * 60 + 30, 22 * 60, ALL_DAYS, true);
    }

    static final class Event {
        final long wallMs;
        final boolean on;
        Event(long wallMs, boolean on) { this.wallMs = wallMs; this.on = on; }
    }

    /** Next on/off occurrence strictly after nowWall, honoring the selected days. */
    Event nextEvent(long nowWall) {
        if (nowWall <= 0) throw new IllegalArgumentException("Reloj inválido");
        Calendar base = Calendar.getInstance(ZONE);
        base.setTimeInMillis(nowWall);
        Event best = null;
        for (int offset = 0; offset <= 8; offset++) {
            Calendar day = (Calendar) base.clone();
            day.add(Calendar.DAY_OF_YEAR, offset);
            if ((days & dayBit(day.get(Calendar.DAY_OF_WEEK))) == 0) continue;
            for (int which = 0; which < 2; which++) {
                boolean on = which == 0;
                int minute = on ? onMinute : offMinute;
                Calendar candidate = (Calendar) day.clone();
                candidate.set(Calendar.HOUR_OF_DAY, minute / 60);
                candidate.set(Calendar.MINUTE, minute % 60);
                candidate.set(Calendar.SECOND, 0);
                candidate.set(Calendar.MILLISECOND, 0);
                long value = candidate.getTimeInMillis();
                if (value > nowWall && (best == null || value < best.wallMs)) best = new Event(value, on);
            }
        }
        return best;
    }

    /** True if the alarm is still for this schedule and within a small late window. */
    boolean matchesNow(boolean on, long nowWall, int toleranceMinutes) {
        if (toleranceMinutes < 0) throw new IllegalArgumentException("Tolerancia inválida");
        Calendar now = Calendar.getInstance(ZONE);
        now.setTimeInMillis(nowWall);
        if ((days & dayBit(now.get(Calendar.DAY_OF_WEEK))) == 0) return false;
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int expected = on ? onMinute : offMinute;
        int delta = (current - expected + 1440) % 1440;
        if (delta > 720) delta -= 1440;
        // Exact alarms may arrive late; only a one-minute early grace is accepted.
        return delta >= -1 && delta <= toleranceMinutes;
    }

    LightSchedule withOn(int minute) {
        return new LightSchedule(id, target, minute, offMinute, days, enabled);
    }
    LightSchedule withOff(int minute) {
        return new LightSchedule(id, target, onMinute, minute, days, enabled);
    }
    LightSchedule withDays(int value) {
        return new LightSchedule(id, target, onMinute, offMinute, value, enabled);
    }
    LightSchedule withEnabled(boolean value) {
        return new LightSchedule(id, target, onMinute, offMinute, days, value);
    }

    static int dayBit(int calendarDay) {
        return 1 << ((calendarDay + 5) % 7); // Monday=bit0 ... Sunday=bit6
    }

    static String format(int minute) {
        int hour = minute / 60, min = minute % 60;
        String suffix = hour < 12 ? "a. m." : "p. m.";
        int display = hour % 12; if (display == 0) display = 12;
        return String.format(Locale.ROOT, "%d:%02d %s", display, min, suffix);
    }

    static String daysLabel(int value) {
        if (value == ALL_DAYS) return "todos los días";
        if (value == WEEKDAYS) return "lunes a viernes";
        if (value == WEEKEND) return "fin de semana";
        return "días seleccionados";
    }
}
