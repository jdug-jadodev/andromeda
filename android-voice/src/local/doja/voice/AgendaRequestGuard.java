package local.doja.voice;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Independent Android full-request check after native selection. Not a tool router. */
final class AgendaRequestGuard {
    static String notation(String text) {
        String[] tokens = TimerRequestGuard.normalize(text).split(" "); List<String> result = new ArrayList<>();
        for (int index = 0; index < tokens.length;) {
            if (index + 2 < tokens.length && tokens[index + 1].equals("y")) {
                try { result.add(Integer.toString(TimerRequestGuard.amount(tokens[index] + " y " + tokens[index + 2]))); index += 3; continue; }
                catch (IllegalArgumentException ignored) {}
            }
            try { result.add(Integer.toString(TimerRequestGuard.amount(tokens[index]))); }
            catch (IllegalArgumentException ignored) { result.add(tokens[index]); }
            index++;
        }
        return String.join(" ", result);
    }
    static final class Value {
        final AgendaEntry.Kind kind; final String text; final long due, delay; final int weekday, minute;
        Value(AgendaEntry.Kind kind, String text, long due, long delay) { this(kind,text,due,delay,0,-1); }
        Value(AgendaEntry.Kind kind, String text, long due, long delay,int weekday,int minute) {
            if(weekday!=0) { AlarmSchedule.validate(weekday,minute); if(kind!=AgendaEntry.Kind.ALARM || delay!=0) throw new IllegalArgumentException("Invalid recurrence"); }
            else if(minute>=0) AlarmSchedule.validate(0,minute); // Daily recurrence, both kinds.
            else if(minute!=-1) throw new IllegalArgumentException("Invalid one-shot clock");
            this.kind = kind; this.text = text; this.due = due; this.delay = delay; this.weekday=weekday; this.minute=minute;
        }
    }
    static String clean(String human) {
        if (human == null || human.isEmpty() || human.length() > 250 || human.contains("<|")
                || Pattern.compile("[\"'«»<>\\x00-\\x1f]|[\\p{Nd}&&[^0-9]]").matcher(human).find()) throw new IllegalArgumentException("Invalid request");
        return human.trim().replaceAll("\\s+", " ").replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$", "")
            .replaceFirst("(?iu)^andr[oó]meda[, ]+", "").replaceFirst("(?iu)^por favor ", "");
    }
    static LocalDate day(String text, long anchor) {
        if (anchor <= 0) throw new IllegalArgumentException("Invalid clock");
        String date = TimerRequestGuard.normalize(clean(text)).replaceFirst("^(?:para )?(?:el )?", "");
        LocalDate today = Instant.ofEpochMilli(anchor).atZone(ZoneId.of(AgendaEntry.ZONE)).toLocalDate(), day = today;
        Matcher relative = Pattern.compile("(?:en|dentro de) (.+?) dias?").matcher(date);
        if (relative.matches()) {
            int days = TimerRequestGuard.amount(relative.group(1));
            if (days < 1 || days > 366) throw new IllegalArgumentException("Invalid day range");
            day = today.plusDays(days);
        } else if (date.equals("manana")) day = today.plusDays(1);
        else if (date.equals("pasado manana")) day = today.plusDays(2);
        else if (!date.equals("hoy")) {
            if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) day = LocalDate.parse(date);
            else if (date.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) day = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
            else {
                String[] months = "enero febrero marzo abril mayo junio julio agosto septiembre octubre noviembre diciembre".split(" ");
                Matcher parts = Pattern.compile("(.+?) de (" + String.join("|", months) + ") (?:de|del) (.+)").matcher(date);
                if (!parts.matches()) throw new IllegalArgumentException("Explicit date required");
                String yearText = parts.group(3); int year;
                if (yearText.matches("[0-9]{4}")) year = Integer.parseInt(yearText);
                else {
                    Matcher spoken = Pattern.compile("dos mil(?: (.+))?").matcher(yearText);
                    if (!spoken.matches()) throw new IllegalArgumentException("Explicit year required");
                    int suffix = spoken.group(1) == null ? 0 : TimerRequestGuard.amount(spoken.group(1));
                    if (suffix < 0 || suffix > 99) throw new IllegalArgumentException("Invalid spoken year");
                    year = 2000 + suffix;
                }
                day = LocalDate.of(year, Arrays.asList(months).indexOf(parts.group(2)) + 1, TimerRequestGuard.amount(parts.group(1)));
            }
        }
        long ahead = java.time.temporal.ChronoUnit.DAYS.between(today, day);
        if (ahead < 0 || ahead > 366) throw new IllegalArgumentException("Past/out of range day");
        return day;
    }
    static final class AmbiguousTime extends IllegalArgumentException {}
    static int[] clock(String text) {
        String clock = TimerRequestGuard.normalize(clean(text)).replaceFirst("^a (?:las? )?", "");
        int hour, minute = 0;
        if (clock.equals("medianoche") || clock.equals("mediodia")) hour = clock.equals("medianoche") ? 0 : 12;
        else {
            Matcher suffix = Pattern.compile("\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|a eme|pe eme)$").matcher(clock);
            boolean meridiem = suffix.find();
            if (!meridiem) suffix = Pattern.compile(" (de la manana|de la tarde|de la noche|del mediodia)$").matcher(clock);
            String period = "";
            suffix.reset();
            if (suffix.find()) { period = suffix.group(1); clock = clock.substring(0, suffix.start()).trim(); }
            Matcher digital = Pattern.compile("([0-9]{1,2}):([0-9]{2})").matcher(clock); boolean digits = digital.matches();
            if (digits) { hour = Integer.parseInt(digital.group(1)); minute = Integer.parseInt(digital.group(2)); }
            else {
                String[] parts = clock.split(" y ", 2); hour = TimerRequestGuard.amount(parts[0]);
                if (parts.length == 2) minute = parts[1].equals("media") ? 30 : parts[1].equals("cuarto") ? 15 : TimerRequestGuard.amount(parts[1]);
            }
            if (!period.isEmpty()) {
                if (hour < 1 || hour > 12)
                    throw new IllegalArgumentException("Ambiguous clock");
                if (meridiem) hour = hour % 12 + (period.startsWith("a") ? 0 : 12);
                else {
                    if (period.equals("de la manana") && hour == 12 || period.equals("del mediodia") && hour != 12)
                        throw new IllegalArgumentException("Ambiguous clock");
                    if (period.equals("de la noche") && hour == 12) hour = 0;
                    else if ((period.equals("de la tarde") || period.equals("de la noche")) && hour < 12) hour += 12;
                }
            } else if (!digits && hour < 13) throw new AmbiguousTime();
        }
        if(hour<0 || hour>23 || minute<0 || minute>59) throw new IllegalArgumentException("Invalid clock range");
        return new int[]{hour, minute};
    }
    static long[] dayDue(LocalDate day, String text, long anchor) {
        if (anchor <= 0) throw new IllegalArgumentException("Invalid clock");
        int[] clock = clock(text);
        long due = day.atTime(clock[0], clock[1]).atZone(ZoneId.of(AgendaEntry.ZONE)).toInstant().toEpochMilli();
        if (due <= anchor || due - anchor > 366 * 86400000L) throw new IllegalArgumentException("Past/out of range");
        return new long[]{due, 0};
    }
    static long[] due(String when, long anchor) {
        if (anchor <= 0) throw new IllegalArgumentException("Invalid clock");
        String plain = TimerRequestGuard.normalize(clean(when)).replaceFirst("^para ", "");
        Matcher match = Pattern.compile("(.+?) a (?:las? )?(.+)").matcher(plain);
        if (match.matches()) return dayDue(day(match.group(1), anchor), match.group(2), anchor);
        if (plain.startsWith("en ")) {
            try {
                long delay = TimerRequestGuard.seconds("pon un temporizador de " + plain.substring(3)) * 1000L;
                return new long[]{Math.addExact(anchor, delay), delay};
            } catch (IllegalArgumentException invalid) { /* May be a calendar day, not an elapsed countdown. */ }
        }
        Matcher todayRelative = Pattern.compile("hoy (?:en|dentro de) (.+)").matcher(plain);
        if (todayRelative.matches()) {
            try {
                long delay = TimerRequestGuard.seconds("pon un temporizador de " + todayRelative.group(1)) * 1000L;
                return new long[]{Math.addExact(anchor, delay), delay};
            } catch (IllegalArgumentException invalid) { /* Preserve strict date/time rejection. */ }
        }
        day(plain, anchor);
        throw new IllegalArgumentException("Hour required");
    }
    static List<String[]> parts(AgendaEntry.Kind kind, String raw) {
        List<String[]> candidates = new ArrayList<>();
        if (kind == AgendaEntry.Kind.ALARM) {
            Matcher match = Pattern.compile("(?:(?:pon|ponme|crea|programa) (?:una |la )?alarma|(?:una |la )?alarma) (?:para )?(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(raw);
            if (match.matches()) candidates.add(new String[]{match.group(1), ""});
        } else {
            Matcher prefix = Pattern.compile("(?:recu[eé]rdame|(?:pon|ponme|crea|programa) (?:un |el )?recordatorio) (.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(raw);
            if (prefix.matches()) {
                String tail = prefix.group(1);
                Matcher front = Pattern.compile("(.+?) (?:que|para) (.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(tail);
                if (front.matches()) candidates.add(new String[]{front.group(1).replaceFirst("(?iu)^para ", ""), front.group(2)});
                Matcher positions = Pattern.compile(" (?=(?:para )?(?:en |dentro de |hoy |ma[ñn]ana |pasado ma[ñn]ana |todos los d[ií]as |cada d[ií]a |el (?=[0-9]|[a-záéíóúñ]+(?: y [a-záéíóúñ]+)? de )|[0-9]{4}-|[0-9]{2}/))", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(tail);
                while (positions.find()) {
                    String body = tail.substring(0, positions.start());
                    if (!Pattern.compile("(?iu)\\bpara$").matcher(body).find())
                        candidates.add(new String[]{tail.substring(positions.end()).replaceFirst("(?iu)^para ", ""), body});
                }
            }
        }
        List<String[]> unique = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        for (String[] candidate : candidates) {
            if (seen.add(Arrays.asList(candidate))) unique.add(candidate);
        }
        return unique;
    }
    static Value parse(AgendaEntry.Kind kind, String human, String when, String text, long anchor) {
        return parse(kind,human,when,text,anchor,false);
    }
    static Value parse(AgendaEntry.Kind kind, String human, String when, String text, long anchor,boolean lifecycle) {
        String raw = clean(human);
        if (kind == null || when == null || text == null || Pattern.compile("\\bno (?:lo hagas|la pongas|lo pongas|la crees|lo crees|programes)\\b")
                .matcher(TimerRequestGuard.normalize(raw)).find()) throw new IllegalArgumentException("Invalid request");
        Value result = null;
        for (String[] candidate : parts(kind,raw)) {
            if (!notation(candidate[0]).equals(notation(when))
                    || !TimerRequestGuard.normalize(candidate[1]).equals(TimerRequestGuard.normalize(text))) continue;
            if (kind == AgendaEntry.Kind.REMINDER && (candidate[1].isEmpty() || candidate[1].length() > 160 || Pattern.compile(
                "[;.!?]|(?:\\b(?:y|ademas|tambien|despues|luego)\\b|,)\\s+(?:andromeda[, ]+)?(?:por favor )?"
                + "(?:pon|ponme|pone|crea|programa|inicia|recuerdame|busca|investiga|consulta|dime|cuenta|cancela|no)\\b"
                + "|\\b(?:en|dentro de) .+ (?:segundos?|minutos?|horas?|dias?)\\b")
                .matcher(TimerRequestGuard.normalize(candidate[1])).find())) continue;
            if (result != null) throw new IllegalArgumentException("Ambiguous request");
            if(lifecycle) result=AlarmSchedule.parse(kind,candidate[0],candidate[1],anchor);
            else { long[] time = due(candidate[0], anchor); result = new Value(kind, candidate[1], time[0], time[1]); }
        }
        if (result == null) throw new IllegalArgumentException("Whole current request required"); return result;
    }
}
