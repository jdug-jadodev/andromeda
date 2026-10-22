package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Public fixtures, RAM-only dialogues, fake clocks and sealed permits. No Android/effects. */
public final class CalendarDatesTest {
    static final long WALL=1792610130000L;
    static int checks;
    static void check(boolean value) { checks++;if(!value)throw new AssertionError("Calendar dates assertion "+checks); }
    interface Work { void run(); }
    static void reject(Work work) {
        boolean rejected=false;try { work.run(); }catch(RuntimeException expected){rejected=true;}
        check(rejected);
    }
    static AgendaDialogue.Human human(int sequence,String text) { return human(sequence,text,WALL+(sequence-1)*3000); }
    static AgendaDialogue.Human human(int sequence,String text,long wall) {
        long boot=100000+(sequence-1)*3000L;
        return new AgendaDialogue.Human(String.format(Locale.ROOT,"%032x",sequence),sequence,text,boot-100,wall,boot,boot,64);
    }
    static void heard(AgendaDialogue draft,AgendaDialogue.Human human) { draft.spoken(human.id,true,human.boot+300); }
    static AgendaDialogue ready(String text) {
        AgendaDialogue draft=new AgendaDialogue(64,true,true);AgendaDialogue.Human first=human(1,text);
        check(draft.begin(AgendaEntry.Kind.REMINDER,first).kind.equals("question"));heard(draft,first);return draft;
    }
    public static void main(String[] args)throws Exception {
        List<String> lines=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);
        for(String line:lines.subList(1,lines.size())) {
            String[] row=line.split("\t",-1);long anchor=Long.parseLong(row[2]);
            if(row[3].equals("INVALID"))reject(()->AgendaRequestGuard.due(row[1],anchor));
            else {
                long[] due=AgendaRequestGuard.due(row[1],anchor);
                check(due[0]==Long.parseLong(row[3]));check(due[1]==Long.parseLong(row[4]));
            }
        }
        for(int period=0;period<2;period++) {
            String[] suffixes=period==0 ? new String[]{"am","a. m.","a m","a eme"} : new String[]{"pm","p. m.","p m","pe eme"};
            for(int hour=1;hour<=12;hour++)for(int minute:new int[]{0,15,30,59})for(String suffix:suffixes) {
                long expected=LocalDate.of(2026,11,2).atTime(hour%12+period*12,minute).atZone(java.time.ZoneId.of(AgendaEntry.ZONE)).toInstant().toEpochMilli();
                long[] value=AgendaRequestGuard.due(String.format(Locale.ROOT,"el dos de noviembre del 2026 a las %d:%02d %s",hour,minute,suffix),WALL);
                check(value[0]==expected && value[1]==0);
            }
        }
        long[] todayClock=AgendaRequestGuard.due("hoy a las 23:59",WALL);
        long expectedToday=LocalDate.of(2026,10,21).atTime(23,59).atZone(java.time.ZoneId.of(AgendaEntry.ZONE)).toInstant().toEpochMilli();
        check(todayClock[0]==expectedToday && todayClock[1]==0);
        for(String relative:new String[]{"hoy en dos minutos","hoy dentro de dos minutos"}) {
            long[] value=AgendaRequestGuard.due(relative,WALL);check(value[0]==WALL+120000L && value[1]==120000L);
        }
        reject(()->AgendaRequestGuard.due("hoy en cero minutos",WALL));
        String when="el dos de noviembre del 2026 a las 5pm",body="regar el jardín";
        for(String text:new String[]{"recuérdame "+body+" para "+when,"recuérdame "+when+" que "+body,
                "crea un recordatorio para "+when+" para "+body}) {
            AgendaRequestGuard.Value value=AgendaRequestGuard.parse(AgendaEntry.Kind.REMINDER,text,when,body,WALL,true);
            check(value.due==1793656800000L && value.delay==0 && value.text.equals(body));
            reject(()->AgendaRequestGuard.parse(AgendaEntry.Kind.REMINDER,text,when.replace("5pm","6pm"),body,WALL,true));
            AgendaPermit permit=AgendaPermit.validated("00000000-0000-0000-0000-000000000001",AgendaEntry.Kind.REMINDER,text,when,body,
                value.due,0,104000,WALL,100000,100000,WALL,true);
            check(permit.begin(100000,WALL));check(!permit.begin(100000,WALL));
        }
        for(String duration:new String[]{"en tres días","dentro de tres días"}) {
            String relative=duration+" a las 5pm",text="recuérdame "+body+" "+relative;
            check(AgendaRequestGuard.parse(AgendaEntry.Kind.REMINDER,text,relative,body,WALL,true).due==1792879200000L);
        }
        for(String invalid:new String[]{"no me recuerdes "+body+" "+when,"ayer dije recuérdame "+body+" "+when,
                "recuérdame "+body+" "+when+" y crea una alarma","recuérdame "+body+" y pon una alarma "+when,
                "recuérdame "+body+" para "+when.replace("noviembre","nobiembre")})
            reject(()->AgendaRequestGuard.parse(AgendaEntry.Kind.REMINDER,invalid,when,body,WALL,true));
        String question=AgendaDialogue.hourQuestion(LocalDate.of(2026,10,24));
        check(question.equals("¿A qué hora quieres el recordatorio para el 24/10/2026? Indica mañana o tarde, o usa formato de 24 horas."));
        for(boolean lifecycle:new boolean[]{false,true}) {
            for(String date:new String[]{"en tres días","para el veinticuatro de octubre del dos mil veintiséis"}) {
                AgendaDialogue draft=new AgendaDialogue(64,lifecycle,true);AgendaDialogue.Human first=human(1,"recuérdame "+body);
                check(draft.begin(AgendaEntry.Kind.REMINDER,first).text.equals(AgendaDialogue.WHEN));heard(draft,first);
                AgendaDialogue.Human day=human(2,date);AgendaDialogue.Decision ask=draft.answer(day,day.boot);
                check(ask.kind.equals("question") && ask.text.equals(question) && ask.value==null);heard(draft,day);
                AgendaDialogue.Human ambiguous=human(3,"a las cinco");ask=draft.answer(ambiguous,ambiguous.boot);
                check(ask.kind.equals("question") && ask.text.equals(question) && ask.value==null);heard(draft,ambiguous);
                AgendaDialogue.Human last=human(4,"a las cinco pm");AgendaDialogue.Decision proposal=draft.answer(last,last.boot);
                check(proposal.kind.equals("proposal") && proposal.value.due==1792879200000L && proposal.value.delay==0);
                check(proposal.when.equals(last.text));
                reject(()->draft.claim(proposal.eventKind,proposal.origin,last.id,last.sequence,proposal.when,proposal.text,1793656800000L,0,last.boot));
                AgendaPermit permit=AgendaPermit.validatedDialogue("00000000-0000-0000-0000-000000000001",proposal.eventKind,draft,proposal.origin,last.id,last.sequence,
                    proposal.when,proposal.text,proposal.value.due,0,last.boot+4000,last.wall,last.boot,last.boot,last.wall);
                check(!draft.active() && permit.begin(last.boot,last.wall) && !permit.begin(last.boot,last.wall));
                reject(()->draft.claim(proposal.eventKind,proposal.origin,last.id,last.sequence,proposal.when,proposal.text,proposal.value.due,0,last.boot));
            }
        }
        for(String opening:new String[]{"recuérdame "+body+" en tres días","recuérdame "+body+" para el veinticuatro de octubre del 2026"}) {
            AgendaDialogue draft=new AgendaDialogue(64,true,true);AgendaDialogue.Human first=human(1,opening);
            AgendaDialogue.Decision ask=draft.begin(AgendaEntry.Kind.REMINDER,first);
            check(ask.text.equals(question) && ask.value==null);heard(draft,first);
            AgendaDialogue.Human last=human(2,"17:00");check(draft.answer(last,last.boot).value.due==1792879200000L);
        }
        AgendaDialogue midnight=new AgendaDialogue(64,true,true);AgendaDialogue.Human first=human(1,"recuérdame "+body+" en tres días",1792645140000L);
        midnight.begin(AgendaEntry.Kind.REMINDER,first);heard(midnight,first);
        AgendaDialogue.Human last=human(2,"5pm",1792645260000L);check(midnight.answer(last,last.boot).value.due==1792879200000L);
        AgendaDialogue replacement=ready("recuérdame "+body+" en tres días");last=human(2,when);
        check(replacement.answer(last,last.boot).value.due==1793656800000L);
        AgendaDialogue malformed=ready("recuérdame "+body+" en tres días");last=human(2,when.replace("noviembre","nobiembre"));
        check(malformed.answer(last,last.boot).text.equals(AgendaDialogue.INVALID_TIME));heard(malformed,last);
        last=human(3,"5pm");check(malformed.answer(last,last.boot).text.equals(AgendaDialogue.INVALID_TIME));
        AgendaDialogue interrupted=ready("recuérdame "+body+" en tres días");interrupted.spoken(String.format(Locale.ROOT,"%032x",1),false,100500);
        check(!interrupted.active() && interrupted.answer(human(2,"5pm"),103000)==null);
        AgendaDialogue unheard=new AgendaDialogue(64,true,true);unheard.begin(AgendaEntry.Kind.REMINDER,human(1,"recuérdame "+body+" en tres días"));
        reject(()->unheard.answer(human(2,"5pm"),103000));check(!unheard.active());
        AgendaDialogue expired=ready("recuérdame "+body+" en tres días");
        AgendaDialogue.Human tooLate=new AgendaDialogue.Human(String.format(Locale.ROOT,"%032x",2),2,"5pm",400000,WALL,400001,400001,64);
        reject(()->expired.answer(tooLate,400001));check(!expired.active());
        AgendaDialogue cancelled=ready("recuérdame "+body+" en tres días");check(cancelled.answer(human(2,"cancela el recordatorio"),103000).kind.equals("cancelled"));
        check(!cancelled.active());
        System.out.println("Calendar dates: "+checks+" assertions passed (shared public dates, fresh heard hour, fake clocks/permits only).");
    }
}
