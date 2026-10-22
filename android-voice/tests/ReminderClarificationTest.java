package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Same public human turns as Python. No Android, microphone, model, files of the human or scheduler. */
public final class ReminderClarificationTest {
    static int checks;
    static final long WALL=1792328400000L;
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("Reminder clarification "+checks); }
    interface Work { void run(); }
    static void reject(Work work) { try { work.run(); throw new AssertionError("Unverified reminder accepted"); } catch(IllegalArgumentException expected) { checks++; } }
    static String text(String value) {
        switch(value) {
            case "REMINDER_INTENT":return AgendaDialogue.REMINDER_INTENT;
            case "SUBJECT":return AgendaDialogue.SUBJECT;
            case "WHEN":return AgendaDialogue.WHEN;
            case "CANCELLED":return AgendaDialogue.CANCELLED;
            default:return value;
        }
    }
    static AgendaDialogue.Human human(String value,int seq,long now,long end) {
        return new AgendaDialogue.Human(String.format(Locale.ROOT,"%032x",seq),seq,value,end,WALL+now-100000,now,now,64);
    }
    public static void main(String[] args) throws Exception {
        String previous="";AgendaDialogue draft=null;
        List<String> lines=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);
        for(String line:lines.subList(1,lines.size())) {
            String[] row=line.split("\t",-1);if(!previous.equals(row[0])) { draft=new AgendaDialogue(64,true,true);previous=row[0]; }
            int seq=Integer.parseInt(row[1]);long now=100000+(seq-1)*3000L;
            AgendaDialogue.Human human;
            AgendaDialogue.Decision value;
            try {
                human=human(row[2],seq,now,now-100);
                value=seq==1?draft.begin(AgendaEntry.Kind.REMINDER,human):draft.answer(human,now);
            }
            catch(IllegalArgumentException invalid) { check(row[4].equals("INVALID"));continue; }
            check(!row[4].equals("INVALID"));
            if(row[4].equals("none")) { check(value==null);continue; }
            check(value!=null && value.kind.equals(row[4]) && value.text.equals(text(row[5])));
            if(value.kind.equals("proposal")) {
                check(value.value.delay==300000 && value.text.equals("regar las plantas"));
                if(!row[0].equals("legacy_complete_open")) check(!value.origin.equals(String.format("%032x",1)));
                AgendaDialogue guarded=draft;
                reject(()->guarded.claim(value.eventKind,String.format("%032x",99),human.id,human.sequence,value.when,value.text,value.value.due,value.value.delay,now));
                reject(()->guarded.claim(value.eventKind,value.origin,human.id,human.sequence,value.when,"inventado",value.value.due,value.value.delay,now));
                AgendaPermit permit=AgendaPermit.validatedDialogue("00000000-0000-0000-0000-000000000001",value.eventKind,draft,
                    value.origin,human.id,human.sequence,value.when,value.text,value.value.due,value.value.delay,now+4000,human.wall,human.boot,now,human.wall);
                check(permit.value.due>human.wall && !draft.active());
                check(permit.begin(now,human.wall));check(!permit.begin(now,human.wall));
                reject(()->guarded.claim(value.eventKind,value.origin,human.id,human.sequence,value.when,value.text,value.value.due,value.value.delay,now));
            }
            if(!row[3].equals("-")) draft.spoken(human.id,row[3].equals("1"),now+300);
        }
        AgendaDialogue.Human first=human("con un recordatorio",1,100000,99900);
        for(String bad:new String[]{"before_heard","same_turn","expired"}) {
            AgendaDialogue form=new AgendaDialogue(64,true,true);form.begin(AgendaEntry.Kind.REMINDER,first);form.spoken(first.id,true,102950);
            AgendaDialogue.Human answer=bad.equals("same_turn")?new AgendaDialogue.Human(first.id,2,"sí",102990,WALL+3000,103000,103000,64)
                :human("sí",2,bad.equals("expired")?400000:103000,bad.equals("expired")?399900:102940);
            reject(()->form.answer(answer,bad.equals("expired")?400000:103000));check(!form.active());
        }
        AgendaDialogue missing=new AgendaDialogue(64,true,true);missing.begin(AgendaEntry.Kind.REMINDER,first);missing.spoken(first.id,true);check(!missing.active());
        reject(()->AgendaDialogue.opening(AgendaEntry.Kind.REMINDER,"con un recordatorio"));
        reject(()->AgendaDialogue.opening(AgendaEntry.Kind.ALARM,"con una alarma",true));
        System.out.println("Reminder clarification: "+checks+" assertions passed (shared human turns, heard intent, fresh fields, no actions).");
    }
}
