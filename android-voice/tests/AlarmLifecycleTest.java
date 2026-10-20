package local.doja.voice;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AlarmLifecycleTest {
    static int checks;
    static final long WALL=1792515600000L,BOOT=100000;
    static final String ID="00000000-0000-4000-8000-000000000001",OP="00000000-0000-4000-8000-000000000002";
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("Alarm assertion "+checks); }
    interface Action { void run(); }
    static void reject(Action action) { try { action.run(); throw new AssertionError("Unsafe alarm accepted"); } catch(IllegalArgumentException | IllegalStateException expected) { checks++; } }
    static AgendaEntry sounding(int weekday,int minute) { return new AgendaEntry(ID,AgendaEntry.Kind.ALARM,"",WALL,AgendaEntry.ZONE,AgendaEntry.State.DELIVERING,2,0,weekday,minute); }
    static AgendaDialogue.Human human(int sequence,String text) {
        long at=BOOT+(sequence-1)*3000; return new AgendaDialogue.Human(String.format("%032x",sequence),sequence,text,at-100,WALL+(sequence-1)*3000,at,at,64);
    }
    static AlarmOccurrencePolicy.Permit permit(AgendaEntry entry,AlarmOccurrencePolicy.Action action) {
        return new AlarmOccurrencePolicy(entry,BOOT+30000,BOOT).claim(action,OP,BOOT,WALL);
    }
    static final class Fake implements AlarmOccurrenceTransaction.Store,AlarmOccurrenceTransaction.Clock {
        AgendaEntry current=sounding(1,1020);
        long boot=BOOT,wall=WALL; int writes,schedules,receipts,reserves;
        String fail="";
        public long boot() { return boot; } public long wall() { return wall; }
        public boolean reserve() { reserves++; if(fail.equals("reserve")) return false; return true; }
        public AgendaEntry current() { return current; }
        public boolean move(AgendaEntry selected,long due,long elapsed) {
            if(fail.equals("move")) return false;
            writes++; current=new AgendaEntry(selected.id,selected.kind,selected.text,due,selected.zone,AgendaEntry.State.SCHEDULED,selected.revision+1,elapsed,selected.weekday,selected.minute); return true;
        }
        public boolean finish(AgendaEntry selected) {
            writes++;
            current=selected.weekday==0 ? null : new AgendaEntry(selected.id,selected.kind,selected.text,AlarmSchedule.next(selected.weekday,selected.minute,wall),selected.zone,AgendaEntry.State.SCHEDULED,selected.revision+1,0,selected.weekday,selected.minute);
            return true;
        }
        public void schedule() { schedules++; if(fail.equals("schedule")) throw new IllegalStateException("Public fake"); }
        public void failed() { current=new AgendaEntry(current.id,current.kind,current.text,current.due,current.zone,AgendaEntry.State.FAILED,current.revision+1,current.elapsedDue,current.weekday,current.minute); }
        public boolean receipt(String status,long due) { receipts++; return !fail.equals("receipt"); }
    }
    public static void main(String[] args) throws Exception {
        for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)) {
            if(line.isEmpty() || line.startsWith("#")) continue;
            String[] row=line.split("\t"); String when=row[0];
            if(row[1].equals("REJECT")) { reject(() -> AlarmSchedule.parse(AgendaEntry.Kind.ALARM,when,"",WALL)); continue; }
            int day=Integer.parseInt(row[1]),minute=Integer.parseInt(row[2]); long due=Long.parseLong(row[3]);
            AgendaRequestGuard.Value value=AgendaRequestGuard.parse(AgendaEntry.Kind.ALARM,"pon una alarma para "+when,AgendaRequestGuard.clean(when),"",WALL,true);
            check(value.weekday==day && value.minute==minute && value.due==due && value.delay==0);
            check(AgendaSpeechPolicy.text("CREATED",value,true).startsWith("Alarma semanal guardada: "+AlarmSchedule.label(day,minute)+". Próxima: "));
            check(AlarmSchedule.next(day,minute,due)==due+7*86400000L); // Exact boundary always advances a week.
            reject(() -> AgendaRequestGuard.parse(AgendaEntry.Kind.ALARM,"pon una alarma para "+when,when,"",WALL));
            AgendaDialogue form=new AgendaDialogue(64,true); AgendaDialogue.Human first=human(1,"pon una alarma");
            form.begin(AgendaEntry.Kind.ALARM,first); form.spoken(first.id,true);
            AgendaDialogue.Decision decision=form.answer(human(2,when),BOOT+3000);
            check(decision.kind.equals("proposal") && decision.value.weekday==day && decision.value.minute==minute);
        }
        for(String unsafe:new String[]{"no elimina la alarma del lunes a las 17:00","elimina todas las alarmas","dijo elimina la alarma del lunes a las 17:00","elimina la alarma del lunes a las cinco","elimina la alarma del lunes a las 17:00 y busca noticias"}) reject(() -> AlarmSchedule.deletionTarget(unsafe));
        AlarmDeletion deletion=new AlarmDeletion(); AgendaEntry selected=sounding(1,1020); AgendaDialogue.Human opening=human(1,"elimina la alarma del lunes a las 17:00");
        deletion.select(opening,selected,BOOT); reject(() -> deletion.human(human(2,"sí"),BOOT+3000));
        deletion.select(opening,selected,BOOT); deletion.spoken(opening.id,false); check(!deletion.active());
        deletion.select(opening,selected,BOOT); deletion.spoken(opening.id,true); check(deletion.human(human(2,"sí"),BOOT+3000));
        reject(() -> deletion.claim(OP,opening.id,ID,3,WALL,1,1020,human(2,"sí"),BOOT+7000,BOOT+300000,BOOT+3000));
        reject(() -> deletion.claim(OP,opening.id,ID,2,WALL,1,1020,human(3,"sí"),BOOT+7000,BOOT+300000,BOOT+3000));
        AlarmDeletion.Permit removal=deletion.claim(OP,opening.id,ID,2,WALL,1,1020,human(2,"sí"),BOOT+7000,BOOT+300000,BOOT+3000);
        check(removal.begin(BOOT+3100)); check(!removal.begin(BOOT+3100)); check(!deletion.active());
        deletion.select(opening,selected,BOOT); deletion.spoken(opening.id,true); check(!deletion.human(human(2,"sí y elimina todas"),BOOT+3000)); check(!deletion.active());
        for(String safe:new String[]{"pospón cinco minutos","posponla cinco minutos","Andrómeda pospón la alarma 5 minutos"}) check(AlarmOccurrencePolicy.command(safe)==AlarmOccurrencePolicy.Action.SNOOZE);
        for(String unsafe:new String[]{"no pospon cinco minutos","pospon diez minutos","pospon cinco minutos y elimina la alarma","elimina la alarma","dijo pospon cinco minutos"}) check(AlarmOccurrencePolicy.command(unsafe)==null);
        check(AlarmOccurrencePolicy.command("silencio")==AlarmOccurrencePolicy.Action.SILENCE);
        check(AlarmOccurrencePolicy.command("descarta esta alarma")==AlarmOccurrencePolicy.Action.DISMISS);
        check(AlarmDeletion.unsupportedClaim("He eliminado la alarma."));
        check(AlarmDeletion.unsupportedClaim("Alarma pospuesta cinco minutos."));
        check(!AlarmDeletion.unsupportedClaim("¿Cuál alarma quieres eliminar?"));
        AlarmOccurrencePolicy scope=new AlarmOccurrencePolicy(selected,BOOT+30000,BOOT);
        check(scope.claim(AlarmOccurrencePolicy.Action.SILENCE,OP,BOOT,WALL)==null);
        check(scope.claim(AlarmOccurrencePolicy.Action.SNOOZE,OP,BOOT+30000,WALL)==null);
        check(scope.claim(AlarmOccurrencePolicy.Action.SNOOZE,OP,BOOT,WALL)!=null);
        check(scope.claim(AlarmOccurrencePolicy.Action.DISMISS,OP,BOOT,WALL)==null);
        for(int day=1;day<=7;day++) {
            Fake store=new Fake(); store.current=sounding(day,1020); AlarmOccurrencePolicy.Permit snooze=permit(store.current,AlarmOccurrencePolicy.Action.SNOOZE);
            AlarmOccurrenceTransaction.Result receipt=AlarmOccurrenceTransaction.apply(snooze,store,store);
            check(receipt.status.equals("SNOOZED") && receipt.due==WALL+300000);
            check(store.current.weekday==day && store.current.minute==1020 && store.current.elapsedDue==BOOT+300000 && store.writes==1 && store.schedules==1);
            check(AlarmOccurrenceTransaction.apply(snooze,store,store).status.equals("REJECTED")); check(store.writes==1 && store.schedules==1);
            check(AlarmSchedule.next(day,1020,receipt.due)>receipt.due);
        }
        for(String failure:new String[]{"reserve","move","schedule","receipt","stale","clock","expired"}) {
            Fake store=new Fake(); store.fail=failure; AlarmOccurrencePolicy.Permit snooze=permit(store.current,AlarmOccurrencePolicy.Action.SNOOZE);
            if(failure.equals("stale")) store.current=null;
            if(failure.equals("clock")) store.wall+=10000;
            if(failure.equals("expired")) store.boot+=4000;
            check(!AlarmOccurrenceTransaction.apply(snooze,store,store).status.equals("SNOOZED"));
            check(store.writes<=1 && store.schedules<=1 && store.receipts<=1 && store.reserves<=1);
            if(failure.equals("schedule")) check(store.current.state==AgendaEntry.State.FAILED && store.current.weekday==1);
        }
        Fake dismiss=new Fake(); check(AlarmOccurrenceTransaction.apply(permit(dismiss.current,AlarmOccurrencePolicy.Action.DISMISS),dismiss,dismiss).status.equals("DISMISSED"));
        check(dismiss.current!=null && dismiss.current.weekday==1 && dismiss.current.due>WALL);
        Fake once=new Fake(); once.current=sounding(0,-1); check(AlarmOccurrenceTransaction.apply(permit(once.current,AlarmOccurrencePolicy.Action.SNOOZE),once,once).status.equals("SNOOZED")); check(once.current.weekday==0);
        Fake onceDismiss=new Fake(); onceDismiss.current=sounding(0,-1); check(AlarmOccurrenceTransaction.apply(permit(onceDismiss.current,AlarmOccurrencePolicy.Action.DISMISS),onceDismiss,onceDismiss).status.equals("DISMISSED")); check(onceDismiss.current==null);
        System.out.println("Alarm lifecycle: "+checks+" assertions passed (weekly schedules, heard deletion, five-minute transaction/faults; no Android/audio/storage).");
    }
}
