package local.doja.voice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative Spanish parser for "programa las luces" phrases. It only
 * produces a local command; the backend confirmation and Android store remain
 * separate. No audio, network, BLE or human content is touched here.
 */
final class LightScheduleVoiceParser {
    private static final String NUMBER = "(?:cero|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|"
        + "diez|once|doce|trece|catorce|quince|dieciseis|diecisiete|dieciocho|diecinueve|"
        + "veinte|veintiuno|veintidos|veintitres|veinticuatro|veinticinco|veintiseis|veintisiete|"
        + "veintiocho|veintinueve|treinta|cuarenta|cincuenta)";
    private static final String MINUTE = "(?:\\d{1,2}|media|cuarto|(?:treinta|cuarenta|cincuenta)\\s+y\\s+"
        + "(?:uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve)|" + NUMBER + ")";
    private static final Pattern TIME = Pattern.compile("\\b(?:(a|desde|hasta|entre|sobre)\\s+las?\\s+)?("
        + NUMBER + "|\\d{1,2})(?:(?:\\s*:\\s*|\\s+y\\s+)(" + MINUTE
        + ")|\\s+menos\\s+cuarto)?(?:\\s+de\\s+la\\s+(manana|tarde|noche)|\\s*(a\\s*m|p\\s*m))?");
    private static final Pattern ACTION = Pattern.compile(
        "\\b(enciende|encender|prende|prender|activa|activar|apaga|apagar|desactiva|desactivar|off|on|"
        + "apagal[aeo]s?|enciendel[aeo]s?|prendel[aeo]s?|desactival[aeo]s?|actival[aeo]s?)\\b");
    private static final Pattern SECOND_TIME = Pattern.compile("\\by\\s+(\\d{1,2})\\b(?!\\s*(?:por ciento|%))");

    private LightScheduleVoiceParser() {}

    static LightScheduleVoiceCommand parse(String text) {
        if (text == null || text.length() > 250) throw new IllegalArgumentException("Horario de voz inválido");
        String value = normalize(text);
        if (value.isEmpty()) throw new IllegalArgumentException("Horario de voz vacío");

        List<TimeMatch> times = times(value);
        LightTarget target = target(value);
        if (times.isEmpty()) {
            boolean scheduling = value.matches(".*\\b(horario|programacion)\\b.*");
            if (scheduling && value.matches(".*\\b(borra|borrar|elimina|eliminar|quita|quitar|cancela|cancelar)\\b.*"))
                return new LightScheduleVoiceCommand(LightScheduleVoiceCommand.Action.DELETE,
                    target, -1, -1, -1);
            if (scheduling && value.matches(".*\\b(desactiva|desactivar|apaga|apagar)\\b.*"))
                return new LightScheduleVoiceCommand(LightScheduleVoiceCommand.Action.DISABLE,
                    target, -1, -1, -1);
            if (scheduling && value.matches(".*\\b(activa|activar|enciende|encender)\\b.*"))
                return new LightScheduleVoiceCommand(LightScheduleVoiceCommand.Action.ENABLE,
                    target, -1, -1, -1);
            throw new IllegalArgumentException("Falta el horario o una acción de programación");
        }

        boolean scheduleContext = value.matches(".*\\b(programa|programar|programacion|horario|"
            + "todos los dias|cada dia|diario|diariamente)\\b.*");
        boolean onOff = value.matches(".*\\b(enciende|encender|prende|prender|apaga|apagar|desactiva|desactivar|"
            + "encendido|apagado|on|off|apagal[aeo]s?|enciendel[aeo]s?|prendel[aeo]s?|desactival[aeo]s?|actival[aeo]s?)\\b.*");
        if (times.size() > 2 || (!scheduleContext && !onOff && times.size() < 2))
            throw new IllegalArgumentException("Indica una o dos horas de luces");

        int days = days(value);
        if (times.size() == 1) {
            TimeMatch only = times.get(0);
            int action = actionBefore(value, only.start);
            boolean off = action == 2 || value.matches(".*\\b(apagado|apaga|apagar|desactiva|desactivar|"
                + "apagal[aeo]s?|desactival[aeo]s?)\\b.*");
            boolean on = action == 1 || value.matches(".*\\b(encendido|enciende|encender|prende|prender|activa|activar|"
                + "enciendel[aeo]s?|prendel[aeo]s?|actival[aeo]s?)\\b.*");
            if (off == on) throw new IllegalArgumentException("Indica si esa hora es para encender o apagar");
            return new LightScheduleVoiceCommand(LightScheduleVoiceCommand.Action.SET, target,
                on ? only.minuteOfDay : -1, off ? only.minuteOfDay : -1, days);
        }

        TimeMatch first = times.get(0), second = times.get(1);
        int firstAction = actionBefore(value, first.start), secondAction = actionBefore(value, second.start);
        TimeMatch onTime, offTime;
        if (firstAction == 1 && secondAction != 1) { onTime = first; offTime = second; }
        else if (firstAction == 2 && secondAction != 2) { onTime = second; offTime = first; }
        else if (secondAction == 1 && firstAction != 1) { onTime = second; offTime = first; }
        else if (secondAction == 2 && firstAction != 2) { onTime = first; offTime = second; }
        else if (firstAction == 0 && secondAction == 0) { onTime = first; offTime = second; }
        else throw new IllegalArgumentException("No sé qué hora es para encender y cuál para apagar");

        if (onTime.minuteOfDay == offTime.minuteOfDay)
            throw new IllegalArgumentException("Encendido y apagado no pueden coincidir");
        return new LightScheduleVoiceCommand(LightScheduleVoiceCommand.Action.SET, target,
            onTime.minuteOfDay, offTime.minuteOfDay, days);
    }

    private static List<TimeMatch> times(String value) {
        List<TimeMatch> result = new ArrayList<>();
        Matcher matcher = TIME.matcher(value);
        while (matcher.find()) {
            String hourText = matcher.group(2);
            Integer hour = number(hourText);
            if (hour == null || hour < 0 || hour > 23) continue;
            int minute = 0;
            String minuteText = matcher.group(3);
            if (minuteText != null) {
                Integer parsed = minute(minuteText);
                if (parsed == null || parsed < 0 || parsed > 59) continue;
                minute = parsed;
            } else if (matcher.group(0).contains("menos cuarto")) {
                minute = 45;
            }
            Integer periodCode = period(matcher.group(4));
            Boolean pm = periodCode == null ? null : periodCode == 1;
            boolean hasMarker = matcher.group(1) != null
                || minuteText != null || pm != null || matcher.group(0).contains("menos cuarto");
            if (!hasMarker) continue;
            result.add(new TimeMatch(matcher.start(), matcher.end(), hour, minute, pm));
        }
        if (result.size() == 1) {
            TimeMatch first = result.get(0);
            Matcher second = SECOND_TIME.matcher(value);
            if (second.find(first.end)) {
                Integer hour = number(second.group(1));
                if (hour != null && hour >= 0 && hour <= 23)
                    result.add(new TimeMatch(second.start(), second.end(), hour, 0, null));
            }
        }
        result.sort(Comparator.comparingInt(item -> item.start));
        return applyPeriods(value, result);
    }

    /**
     * Applies explicit am/pm first. If only one side is explicit, it applies
     * to the other side. With no marker at all, evening is assumed for
     * 1..11 h because this parser is only reached for light schedules; this is
     * still a candidate until the spoken confirmation.
     */
    private static List<TimeMatch> applyPeriods(String value, List<TimeMatch> values) {
        boolean anyPm = false, anyAm = false;
        for (TimeMatch item : values) {
            if (item.pm == null) continue;
            if (item.pm) anyPm = true; else anyAm = true;
        }
        boolean allLow = true, anyMidnightOrNoon = false;
        for (TimeMatch item : values) {
            if (item.hour > 12) allLow = false;
            if (item.hour == 0 || item.hour == 12) anyMidnightOrNoon = true;
        }
        boolean assumeEvening = !anyPm && !anyAm && allLow && !anyMidnightOrNoon
            && !value.matches(".*\\bmanana\\b.*");
        List<TimeMatch> result = new ArrayList<>();
        for (TimeMatch item : values) {
            int hour = item.hour;
            boolean hasPeriod = item.pm != null || anyPm || anyAm;
            boolean pm = item.pm != null ? item.pm : anyPm ? true : false;
            if (item.pm == null && anyPm && anyAm) hasPeriod = false;
            if (hasPeriod) {
                if (hour > 12) throw new IllegalArgumentException("Hora incompatible con la parte del día");
                if (pm) hour = hour == 12 ? 12 : hour + 12;
                else hour = hour == 12 ? 0 : hour;
            } else if (assumeEvening) {
                if (hour > 12) throw new IllegalArgumentException("Hora ambigua");
                hour = hour == 12 ? 12 : hour + 12;
            }
            if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hora fuera de rango");
            result.add(new TimeMatch(item.start, item.end, hour, item.minute, item.pm));
        }
        return result;
    }

    private static Integer period(String value) {
        if (value == null) return null;
        if (value.equals("manana") || value.replace(" ", "").equals("am")) return 0;
        return 1;
    }

    private static Integer minute(String value) {
        String text = value.trim();
        if ("media".equals(text)) return 30;
        if ("cuarto".equals(text)) return 15;
        return number(text);
    }

    private static Integer number(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.matches("\\d{1,2}")) return Integer.parseInt(value);
        try { return TimerRequestGuard.amount(value); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static int actionBefore(String value, int start) {
        String window = value.substring(Math.max(0, start - 70), start);
        Matcher matcher = ACTION.matcher(window);
        int result = 0;
        while (matcher.find()) {
            String word = matcher.group(1);
            result = word.matches("enciende|encender|prende|prender|activa|activar|on|"
                + "enciendel[aeo]s?|prendel[aeo]s?|actival[aeo]s?") ? 1 : 2;
        }
        return result;
    }

    private static int days(String value) {
        if (value.matches(".*\\b(todos los dias|cada dia|diario|diariamente)\\b.*")) return LightSchedule.ALL_DAYS;
        if (value.matches(".*\\b(lunes a viernes|entre semana|dias de semana|dias habiles)\\b.*"))
            return LightSchedule.WEEKDAYS;
        if (value.matches(".*\\b(fin de semana|sabados? y domingos?)\\b.*")) return LightSchedule.WEEKEND;
        return -1;
    }

    private static LightTarget target(String value) {
        boolean lotus = value.matches(".*\\b(lotus|lotuslamp|melk|luz mueble|mueble)\\b.*");
        boolean star = value.matches(".*\\b(mr star|gatt[- ]+demo|luz del televisor|luz del tv|luz tv|televisor|"
            + "television|tv|tele)\\b.*");
        if (value.matches(".*\\b(ambas|ambos|las dos|las luces)\\b.*") || (lotus && star) || (!lotus && !star))
            return LightTarget.BOTH;
        return lotus ? LightTarget.LOTUS : LightTarget.MR_STAR;
    }

    private static String normalize(String text) {
        String value = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return value.replaceAll("[¿?!,;.]", " ").replaceAll("\\s+", " ").trim()
            .replaceFirst("^andromeda[, ]+", "").trim();
    }

    private static final class TimeMatch {
        final int start, end, hour, minute, minuteOfDay;
        final Boolean pm;
        TimeMatch(int start, int end, int hour, int minute, Boolean pm) {
            this.start = start; this.end = end; this.hour = hour; this.minute = minute;
            this.pm = pm; this.minuteOfDay = hour * 60 + minute;
        }
    }
}
