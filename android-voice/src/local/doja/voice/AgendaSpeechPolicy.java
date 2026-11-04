package local.doja.voice;

import java.util.*;

/** Fixed receipt-derived speech, pure Java for shared Python/Android checks. */
final class AgendaSpeechPolicy {
    private static final String[] DAYS = "lunes martes mi\u00e9rcoles jueves viernes s\u00e1bado domingo".split(" ");

    private AgendaSpeechPolicy() { }

    /** Hora corta: "7" o "7:45", sin segundos. */
    private static String clock(int minuteOfDay) {
        int hour = minuteOfDay / 60, minute = minuteOfDay % 60;
        return minute == 0 ? String.valueOf(hour) : String.format(Locale.ROOT, "%d:%02d", hour, minute);
    }

    private static Calendar bogota(long due) {
        Calendar value = Calendar.getInstance(TimeZone.getTimeZone(AgendaEntry.ZONE), new Locale("es", "CO"));
        value.setTimeInMillis(due);
        return value;
    }

    /**
     * Confirmaci\u00f3n corta: sin a\u00f1o, sin segundos y sin repetir la zona horaria.
     * Debe coincidir car\u00e1cter por car\u00e1cter con result_text() en
     * backend/assistant_tools_trial.py: Android compara el texto exacto.
     */
    static String text(String status, AgendaRequestGuard.Value value, boolean detailed) {
        if ("CREATED".equals(status)) {
            if (value.weekday != 0)
                return "Listo, alarma los " + DAYS[value.weekday - 1] + " a las " + clock(value.minute) + ".";
            if (value.minute >= 0)
                return "Listo, alarma todos los d\u00edas a las " + clock(value.minute) + ".";
            if (value.delay > 0) {
                long scale = value.delay % 3600000 == 0 ? 3600000 : value.delay % 60000 == 0 ? 60000 : 1000;
                String unit = scale == 3600000 ? "hora" : scale == 60000 ? "minuto" : "segundo";
                long count = value.delay / scale;
                return "Listo, te aviso en " + count + " " + unit + (count != 1 ? "s" : "") + ".";
            }
            Calendar moment = bogota(value.due);
            int day = (moment.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int minuteOfDay = moment.get(Calendar.HOUR_OF_DAY) * 60 + moment.get(Calendar.MINUTE);
            return "Listo, " + (value.kind == AgendaEntry.Kind.ALARM ? "alarma" : "recordatorio")
                + " para el " + DAYS[day] + " a las " + clock(minuteOfDay) + ".";
        }
        switch (status) {
            case "DUPLICATE": return "Esa orden ya ten\u00eda un registro. No la repet\u00ed; revisa la agenda manual.";
            case "REJECTED": return "Android rechaz\u00f3 la programaci\u00f3n. No he confirmado un aviso nuevo.";
            case "FAILED": return "La programaci\u00f3n fall\u00f3 y se retir\u00f3 ese intento. Revisa la agenda manual.";
            case "UNKNOWN": return "No pude confirmar el resultado. Revisa Alarmas o Recordatorios antes de pedirlo otra vez.";
            default: throw new IllegalArgumentException("Unknown agenda receipt status");
        }
    }
}
