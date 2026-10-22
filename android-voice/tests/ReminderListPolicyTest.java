package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fake agenda only. No Android, database, scheduler, microphone, model or network. */
public final class ReminderListPolicyTest {
    static int checks;
    static final String A=String.join("",Collections.nCopies(32,"a")),B=A.replace('a','b'),C=A.replace('a','c'),D=A.replace('a','d');
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("Reminder read "+checks); }
    static void invalid(Runnable work) { try { work.run(); throw new AssertionError("Invalid read accepted"); } catch(IllegalArgumentException expected) { checks++; } }
    static AgendaEntry entry(int i,String text) {
        return new AgendaEntry("00000000-0000-0000-0000-"+String.format(Locale.ROOT,"%012d",i),AgendaEntry.Kind.REMINDER,text,
            1792600000000L+i*60000,AgendaEntry.ZONE,AgendaEntry.State.SCHEDULED,1);
    }
    public static void main(String[] args) throws Exception {
        List<String> rows=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);
        for(String row:rows.subList(1,rows.size())) {
            String[] cells=row.split("\t"); check(ReminderListPolicy.requested(cells[0])==cells[1].equals("YES"));
        }
        ReminderListPolicy.Grant grant=new ReminderListPolicy.Grant(A,B,C,D,1,99900,104000);
        ReminderListPolicy.Permit permit=ReminderListPolicy.claim(grant,A,B,C,"¿Qué recordatorios tengo?",1,99900,400000,100000);
        check(permit.begin(100001)); check(!permit.begin(100002));
        invalid(()->ReminderListPolicy.claim(grant,A,B,C,"no leas mis recordatorios",1,99900,400000,100000));
        invalid(()->ReminderListPolicy.claim(grant,A,B,D,"qué recordatorios tengo",1,99900,400000,100000));
        invalid(()->ReminderListPolicy.claim(grant,A,B,C,"qué recordatorios tengo",2,99900,400000,100000));
        invalid(()->ReminderListPolicy.claim(grant,A,B,C,"qué recordatorios tengo",1,99900,400000,104000));
        ReminderListPolicy.Receipt empty=ReminderListPolicy.render(Collections.emptyList(),0,1792500000000L);
        check(empty.status.equals("READ") && empty.total==0 && empty.shown==0 && empty.text.contains("No tienes recordatorios"));
        List<AgendaEntry> entries=Arrays.asList(entry(1,"Lavar la loza"),entry(2,"Beber agua"));
        ReminderListPolicy.Receipt result=ReminderListPolicy.render(entries,2,1792500000000L);
        check(result.status.equals("READ") && result.total==2 && result.shown==2);
        check(result.text.contains("Lavar la loza") && result.text.contains("Beber agua") && result.text.contains("Bogotá"));
        check(!result.text.contains(entries.get(0).id) && entries.get(0).state==AgendaEntry.State.SCHEDULED && entries.get(0).revision==1);
        List<AgendaEntry> many=new ArrayList<>(); for(int i=1;i<=8;i++) many.add(entry(i,"Tarea pública "+i));
        ReminderListPolicy.Receipt bounded=ReminderListPolicy.render(many,8,1792500000000L);
        check(bounded.total==8 && bounded.shown==3 && bounded.text.contains("Hay 5 más") && bounded.text.length()<=ReminderListPolicy.MAX_SPOKEN);
        check(ReminderListPolicy.unavailable().status.equals("UNAVAILABLE") && !ReminderListPolicy.unavailable().text.contains("No tienes"));
        invalid(()->ReminderListPolicy.render(entries,0,1792500000000L));
        System.out.println("Reminder read: "+checks+" assertions passed (fake agenda, native scope, local speech, no mutations).");
    }
}
