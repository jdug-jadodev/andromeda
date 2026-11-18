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
    // Marcadores que abren una expresion de tiempo. Se aplican al texto humano tal
    // cual, por eso toleran tildes y ñ ("mañana", "miércoles", "próximo").
    private static final String WEEKDAY_WORDS = "lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo";
    private static final String TIME_OPENING =
        "(?:para )?(?:en |dentro de |hoy\\b|ma[ñn]ana\\b|pasado ma[ñn]ana\\b|todos los d[ií]as\\b|"
        + "cada d[ií]a\\b|este\\b|esta\\b|(?:el |la )?(?:pr[oó]xim[oa]|siguiente)\\b|"
        + "(?:el |la )?(?:" + WEEKDAY_WORDS + ")\\b|"
        + "el (?=[0-9]|[a-záéíóúñ]+(?: y [a-záéíóúñ]+)? de )|[0-9]{4}-|[0-9]{2}/)";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern WHEN_START = Pattern.compile(TIME_OPENING, FLAGS);
    private static final Pattern WHEN_BOUNDARY = Pattern.compile(" (?=" + TIME_OPENING + ")", FLAGS);
    private static final Pattern BODY_TAIL = Pattern.compile(
        "\\b(?:para|de|el|la|los|las|este|esta|pr[oó]xim[oa]|siguiente)$", FLAGS);
    /** True si la frase (o su cola) empieza con una expresion de tiempo. */
    static boolean opensWithTime(String text) { return WHEN_START.matcher(text).lookingAt(); }
    private static final String[] DAYS = "lunes martes miercoles jueves viernes sabado domingo".split(" ");
    /**
     * Expresion que solo nombra un dia de la semana ("el martes", "los martes",
     * "cada martes", "todos los martes"). Una alarma semanal debe pasar por el
     * ciclo de vida; no se convierte en una alarma de una sola vez.
     */
    static boolean weeklyDay(String when) {
        String plain = TimerRequestGuard.normalize(clean(when)).replaceFirst("^(?:para )?", "");
        String day = plain.replaceFirst(" a (?:las? )?.+$", "");
        String names = String.join("|", DAYS);
        return day.matches("(?:(?:el|los) |todos los |cada )?(?:" + names + ")")
            || day.matches("(?:el |la )?(?:proximo|proxima|siguiente) (?:" + names + ")");
    }
    static LocalDate day(String text, long anchor) {
        if (anchor <= 0) throw new IllegalArgumentException("Invalid clock");
        String value = TimerRequestGuard.normalize(clean(text)).replaceFirst("^(?:para )?(?:el )?", "");
        LocalDate today = Instant.ofEpochMilli(anchor).atZone(ZoneId.of(AgendaEntry.ZONE)).toLocalDate(), day;
        // "el martes": la proxima ocurrencia; "este martes" puede ser hoy y
        // "el proximo/siguiente martes" siempre es una semana despues si hoy
        // ya es martes. Nunca se asume la hora.
        boolean thisWeek = value.startsWith("este ");
        if (thisWeek) value = value.substring(5);
        boolean nextWeek = Pattern.compile("^(?:el )?(?:proximo|proxima|siguiente) ").matcher(value).find();
        if (nextWeek) value = value.replaceFirst("^(?:el )?(?:proximo|proxima|siguiente) ", "");
        String[] weekdays = DAYS;
        String weekday = null;
        Matcher label = Pattern.compile("(" + String.join("|", weekdays) + ")(?= |$)").matcher(value);
        if (label.lookingAt()) { weekday = label.group(1); value = value.substring(label.end()).trim(); }
        try {
            Matcher relative = Pattern.compile("(?:en|dentro de) (.+?) dias?").matcher(value);
            if (relative.matches()) {
                int days = TimerRequestGuard.amount(relative.group(1));
                if (days < 1 || days > 366) throw new IllegalArgumentException("Invalid day range");
                day = today.plusDays(days);
            } else if (value.equals("hoy") || value.equals("manana") || value.equals("pasado manana")) {
                day = today.plusDays(value.equals("hoy") ? 0 : value.equals("manana") ? 1 : 2);
            } else if (value.isEmpty() && weekday != null) {
                int delta = (Arrays.asList(weekdays).indexOf(weekday) + 1 - today.getDayOfWeek().getValue() + 7) % 7;
                if (nextWeek && delta == 0) delta = 7;
                if (delta == 0 && !thisWeek) delta = 7;
                day = today.plusDays(delta);
            } else {
                boolean explicitMonth = Pattern.compile(" (?:de|del) (?:este|el) mes$").matcher(value).find();
                if (explicitMonth) value = value.replaceFirst(" (?:de|del) (?:este|el) mes$", "");
                if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) day = LocalDate.parse(value);
                else if (value.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) day = LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
                else if (value.matches("[0-9]{1,2}")) {
                    // "el 24": este mes si todavia no paso; si ya paso, el proximo mes.
                    int dayNum = Integer.parseInt(value);
                    day = LocalDate.of(today.getYear(), today.getMonthValue(), dayNum);
                    if (day.isBefore(today) && !explicitMonth)
                        day = LocalDate.of(today.getYear() + (today.getMonthValue() == 12 ? 1 : 0), today.getMonthValue() % 12 + 1, dayNum);
                    else if (day.isBefore(today)) throw new IllegalArgumentException("Past day in this month");
                } else {
                    String[] months = "enero febrero marzo abril mayo junio julio agosto septiembre octubre noviembre diciembre".split(" ");
                    Matcher parts = Pattern.compile("(.+?) de (" + String.join("|", months) + ")(?: (?:de|del) (.+))?").matcher(value);
                    if (!parts.matches()) throw new IllegalArgumentException("Explicit date required");
                    int month = Arrays.asList(months).indexOf(parts.group(2)) + 1;
                    int dayNum = TimerRequestGuard.amount(parts.group(1));
                    String yearText = parts.group(3);
                    if (yearText == null) {
                        // Dia y mes sin año: asume el actual; si ya paso, el proximo.
                        day = LocalDate.of(today.getYear(), month, dayNum);
                        if (day.isBefore(today)) day = LocalDate.of(today.getYear() + 1, month, dayNum);
                    } else {
                        int year;
                        if (yearText.matches("[0-9]{4}")) year = Integer.parseInt(yearText);
                        else {
                            Matcher spoken = Pattern.compile("dos mil(?: (.+))?").matcher(yearText);
                            if (!spoken.matches()) throw new IllegalArgumentException("Explicit year required");
                            int suffix = spoken.group(1) == null ? 0 : TimerRequestGuard.amount(spoken.group(1));
                            if (suffix < 0 || suffix > 99) throw new IllegalArgumentException("Invalid spoken year");
                            year = 2000 + suffix;
                        }
                        day = LocalDate.of(year, month, dayNum);
                    }
                }
            }
            // Un dia de la semana explicito debe coincidir con la fecha resuelta.
            if (weekday != null && !value.isEmpty() && day.getDayOfWeek().getValue() != Arrays.asList(weekdays).indexOf(weekday) + 1)
                throw new IllegalArgumentException("Weekday/date mismatch");
            long ahead = java.time.temporal.ChronoUnit.DAYS.between(today, day);
            if (ahead < 0 || ahead > 366) throw new IllegalArgumentException("Past/out of range day");
            return day;
        } catch (DateTimeException error) { throw new IllegalArgumentException("invalid_date", error); }
    }
    static final class AmbiguousTime extends IllegalArgumentException {}
    private static boolean singleNumber(String token) {
        if (token.isEmpty() || token.matches("[0-9]+")) return false;
        try { TimerRequestGuard.amount(token); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    static int[] clock(String text) {
        String clock = TimerRequestGuard.normalize(clean(text)).replaceFirst("^(?:a )?(?:las? )?", "");
        clock = clock.replaceAll("\\bmedio dia\\b", "mediodia").replaceAll("\\bmedia noche\\b", "medianoche");
        int hour, minute = 0;
        if (clock.equals("medianoche") || clock.equals("mediodia")) hour = clock.equals("medianoche") ? 0 : 12;
        else {
            // El ASR omite a veces la preposicion: "once y media la noche".
            Matcher suffix = Pattern.compile("\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|a eme|pe eme)$").matcher(clock);
            boolean meridiem = suffix.find();
            if (!meridiem) {
                suffix = Pattern.compile(" (de la manana|de la tarde|de la noche|del mediodia|la manana|la tarde|la noche|mediodia)$").matcher(clock);
                suffix.find();
            }
            String period = "";
            if (suffix.hasMatch()) {
                period = suffix.group(1);
                if (period.equals("la manana") || period.equals("la tarde") || period.equals("la noche")) period = "de " + period;
                if (period.equals("mediodia")) period = "del mediodia";
                clock = clock.substring(0, suffix.start()).trim();
            }
            Matcher digital = Pattern.compile("([0-9]{1,2}):([0-9]{2})").matcher(clock); boolean digits = digital.matches();
            if (digits) { hour = Integer.parseInt(digital.group(1)); minute = Integer.parseInt(digital.group(2)); }
            else {
                String[] parts = clock.split(" y ", 2);
                if (parts.length == 2) {
                    hour = TimerRequestGuard.amount(parts[0]);
                    minute = parts[1].equals("media") ? 30 : parts[1].equals("cuarto") ? 15 : TimerRequestGuard.amount(parts[1]);
                } else {
                    // El ASR transcribe "11:20" como "once veinte" (hora + minuto, sin "y").
                    String[] tokens = clock.split(" ");
                    if (tokens.length == 2 && singleNumber(tokens[0]) && singleNumber(tokens[1])) {
                        hour = TimerRequestGuard.amount(tokens[0]); minute = TimerRequestGuard.amount(tokens[1]);
                    } else hour = TimerRequestGuard.amount(clock);
                }
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
            } else if (!digits && hour < 12) throw new AmbiguousTime();
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
        try {
            long delay = TimerRequestGuard.seconds("pon un temporizador de " + plain) * 1000L;
            return new long[]{Math.addExact(anchor, delay), delay};
        } catch (IllegalArgumentException invalid) { /* Not a bare duration; may be a calendar day. */ }
        day(plain, anchor);
        throw new IllegalArgumentException("Hour required");
    }
    /** Candidatos (cuando, contenido) del cuerpo de una frase, sin el prefijo. */
    static List<String[]> tailParts(String tail) {
        List<String[]> candidates = new ArrayList<>();
        tail = tail.replaceFirst("(?iu)^que ", "");
        Matcher front = Pattern.compile("(.+?) (?:que|para) (.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(tail);
        if (front.matches()) candidates.add(new String[]{front.group(1).replaceFirst("(?iu)^(?:para|de) ", ""), front.group(2)});
        Matcher positions = WHEN_BOUNDARY.matcher(tail);
        while (positions.find()) {
            String body = tail.substring(0, positions.start());
            // "para", "de" o un articulo suelto a la izquierda no son contenido.
            if (BODY_TAIL.matcher(body).find()) continue;
            candidates.add(new String[]{tail.substring(positions.end()).replaceFirst("(?iu)^para ", ""), body.replaceFirst("(?iu)^(?:para|de) ", "")});
        }
        // Solo la hora, sin contenido: la casa preguntara que recordar.
        if (opensWithTime(tail)) candidates.add(new String[]{tail.replaceFirst("(?iu)^para ", ""), ""});
        return candidates;
    }
    /** Candidatos para una respuesta suelta («comprar crema dental para mañana»). */
    static List<String[]> bareParts(String raw) { return tailParts(clean(raw)); }
    static List<String[]> parts(AgendaEntry.Kind kind, String raw) {
        List<String[]> candidates = new ArrayList<>();
        if (kind == AgendaEntry.Kind.ALARM) {
            Matcher match = Pattern.compile("(?:(?:pon|ponme|crea|programa) (?:una |la )?alarma|(?:una |la )?alarma) (?:para )?(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(raw);
            if (match.matches()) candidates.add(new String[]{match.group(1), ""});
        } else {
            Matcher prefix = Pattern.compile("(?:recu[eé]rdame|(?:pon|ponme|crea|programa) (?:un |el )?recordatorio) (.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(raw);
            if (prefix.matches()) candidates.addAll(tailParts(prefix.group(1)));
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
        // La primera interpretacion literal que valide gana; no se exige que la
        // frase tenga una sola forma. "ver el partido de champions el martes..."
        // deja de confundir el tema con la fecha.
        for (String[] candidate : parts(kind,raw)) {
            if (!notation(candidate[0]).equals(notation(when))
                    || !TimerRequestGuard.normalize(candidate[1]).equals(TimerRequestGuard.normalize(text))) continue;
            if (kind == AgendaEntry.Kind.ALARM && !lifecycle && weeklyDay(candidate[0])) continue;
            if (kind == AgendaEntry.Kind.REMINDER && (candidate[1].isEmpty() || candidate[1].length() > 160 || Pattern.compile(
                "[;.!?]|(?:\\b(?:y|ademas|tambien|despues|luego)\\b|,)\\s+(?:andromeda[, ]+)?(?:por favor )?"
                + "(?:pon|ponme|pone|crea|programa|inicia|recuerdame|busca|investiga|consulta|dime|cuenta|cancela|no)\\b"
                + "|\\b(?:en|dentro de) .+ (?:segundos?|minutos?|horas?|dias?)\\b")
                .matcher(TimerRequestGuard.normalize(candidate[1])).find())) continue;
            try {
                if(lifecycle) return AlarmSchedule.parse(kind,candidate[0],candidate[1],anchor);
                long[] time = due(candidate[0], anchor);
                return new Value(kind, candidate[1], time[0], time[1]);
            } catch (RuntimeException invalid) { /* A malformed interpretation grants nothing; try the next. */ }
        }
        throw new IllegalArgumentException("Whole current request required");
    }
}
