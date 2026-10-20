package local.doja.voice;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** ISO weekday 1=Monday. Never infer recurrence for an existing one-shot record. */
final class AlarmSchedule {
    static final String POLICY = "weekly_alarm_lifecycle_v1";
    static final int PROTOCOL = 13;
    static final String[] DAYS = "lunes martes miércoles jueves viernes sábado domingo".split(" ");
    static final String DAY_PATTERN = "lunes|martes|miercoles|jueves|viernes|sabado|domingo";
    static AgendaRequestGuard.Value parse(AgendaEntry.Kind kind, String when, String text, long anchor) {
        Matcher match = Pattern.compile("(?:el |los |cada |todos los )?("+DAY_PATTERN+") a (?:las? )?(.+)")
            .matcher(TimerRequestGuard.normalize(AgendaRequestGuard.clean(when)));
        if (kind != AgendaEntry.Kind.ALARM || !match.matches()) {
            long[] time=AgendaRequestGuard.due(when,anchor); return new AgendaRequestGuard.Value(kind,text,time[0],time[1]);
        }
        int weekday=Arrays.asList("lunes martes miercoles jueves viernes sabado domingo".split(" ")).indexOf(match.group(1))+1;
        String clock=match.group(2); Matcher period=Pattern.compile("\\s+(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)$").matcher(clock);
        if (period.find()) {
            boolean morning=period.group(1).startsWith("a"); clock=clock.substring(0,period.start());
            if (morning && clock.matches("(?:12|doce)(?::[0-9]{2})?")) clock=clock.contains(":") ? "00"+clock.substring(clock.indexOf(':')) : "00:00";
            else clock+=morning ? " de la manana" : " de la tarde";
        }
        LocalDate today=Instant.ofEpochMilli(anchor).atZone(ZoneId.of(AgendaEntry.ZONE)).toLocalDate();
        LocalDate day=today.plusDays((weekday-today.getDayOfWeek().getValue()+7)%7);
        LocalDate reference=day.isAfter(today) ? day : day.plusDays(7);
        long parsed=AgendaRequestGuard.due(reference+" a las "+clock,anchor)[0];
        LocalTime time=Instant.ofEpochMilli(parsed).atZone(ZoneId.of(AgendaEntry.ZONE)).toLocalTime();
        return new AgendaRequestGuard.Value(kind,text,next(weekday,time.getHour()*60+time.getMinute(),anchor),0,weekday,time.getHour()*60+time.getMinute());
    }
    static long next(int weekday, int minute, long now) {
        validate(weekday,minute);
        ZonedDateTime local=Instant.ofEpochMilli(now).atZone(ZoneId.of(AgendaEntry.ZONE));
        LocalDate day=local.toLocalDate().plusDays((weekday-local.getDayOfWeek().getValue()+7)%7);
        long due=day.atTime(minute/60,minute%60).atZone(local.getZone()).toInstant().toEpochMilli();
        return due>now ? due : day.plusDays(7).atTime(minute/60,minute%60).atZone(local.getZone()).toInstant().toEpochMilli();
    }
    static void validate(int weekday,int minute) {
        if(weekday<1 || weekday>7 || minute<0 || minute>=1440) throw new IllegalArgumentException("Invalid weekly schedule");
    }
    static String label(int weekday,int minute) {
        validate(weekday,minute); return String.format(Locale.ROOT,"cada %s a las %02d:%02d, hora de Bogotá",DAYS[weekday-1],minute/60,minute%60);
    }
    static int[] deletionTarget(String human) {
        Matcher match=Pattern.compile("(?:elimina|borra|cancela) la alarma (?:del |de los |de cada |de todos los |el |los |cada )?(.+)")
            .matcher(TimerRequestGuard.normalize(AgendaRequestGuard.clean(human)));
        if(!match.matches()) throw new IllegalArgumentException("Explicit alarm deletion required");
        AgendaRequestGuard.Value value=parse(AgendaEntry.Kind.ALARM,match.group(1),"",1792501200000L);
        if(value.weekday==0) throw new IllegalArgumentException("Weekly alarm target required");
        return new int[]{value.weekday,value.minute};
    }
}
