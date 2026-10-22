package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Public fixtures, fake store/effects only. No Android, microphone, model or phone. */
public final class ReminderDeletionTest {
    static int checks;
    static final long WALL=1792328400000L;
    static void check(boolean value) { checks++;if(!value)throw new AssertionError("Reminder deletion "+checks); }
    interface Work { void run(); }
    static void reject(Work work) { try { work.run();throw new AssertionError("Unverified reminder deletion accepted"); }catch(IllegalArgumentException expected){checks++;} }
    static String id(int index) { return String.format(Locale.ROOT,"00000000-0000-0000-0000-%012x",index); }
    static AgendaEntry entry(int index,String text) { return new AgendaEntry(id(index),AgendaEntry.Kind.REMINDER,text,WALL+index*60000L,AgendaEntry.ZONE,AgendaEntry.State.SCHEDULED,1); }
    static AgendaDialogue.Human human(String text,int seq) {
        long now=100000+(seq-1)*3000L;
        return new AgendaDialogue.Human(String.format(Locale.ROOT,"%032x",seq),seq,text,now-100,WALL+now-100000,now,now,64);
    }
    static ReminderDeletion ready(AgendaEntry entry) {
        ReminderDeletion flow=new ReminderDeletion();AgendaDialogue.Human h=human("elimina el recordatorio de "+entry.text,1);
        ReminderDeletion.Step step=flow.step(h,Arrays.asList(entry),1,100000);
        check(step.status.equals("QUESTION") && step.stage.equals("CONFIRM") && step.selected==entry);
        check(step.text.contains(entry.text) && step.text.contains(entry.when()));flow.spoken(h.id,true,100300);return flow;
    }
    static ReminderDeletion.Permit permit(AgendaEntry entry) {
        ReminderDeletion flow=ready(entry);AgendaDialogue.Human yes=human("sí",2);
        return flow.claim(id(1000),flow.origin(),entry.id,entry.revision,entry.due,yes,107000,400000,103000);
    }
    static final class FakeStore implements AgendaCancellation.Store {
        final Map<String,AgendaEntry> rows=new HashMap<>();boolean failBlock,failRemove;
        public AgendaCancellation.Stage block(AgendaEntry selected) {
            if(failBlock)throw new IllegalStateException("fake block fault");
            AgendaEntry current=rows.get(selected.id);
            if(current==null)return AgendaCancellation.Stage.NOT_FOUND;
            if(current.revision!=selected.revision || current.due!=selected.due || current.kind!=selected.kind
                    || !current.text.equals(selected.text) || current.state!=selected.state)return AgendaCancellation.Stage.STALE;
            rows.put(current.id,new AgendaEntry(current.id,current.kind,"",current.due,current.zone,AgendaEntry.State.CANCEL_PENDING,current.revision+1));
            return AgendaCancellation.Stage.BLOCKED;
        }
        public boolean removeBlocked(AgendaEntry selected) {
            if(failRemove)return false;AgendaEntry current=rows.get(selected.id);
            if(current==null || current.revision!=selected.revision+1 || current.state!=AgendaEntry.State.CANCEL_PENDING || !current.text.isEmpty())return false;
            rows.remove(selected.id);return true;
        }
    }
    static final class FakeEffects implements AgendaCancellation.Effects {
        final List<String> stopped=new ArrayList<>(),cancelled=new ArrayList<>();boolean failStop,failCancel;
        public boolean stopOutput(String id) { stopped.add(id);return !failStop; }
        public void cancelSchedule(String id) { cancelled.add(id);if(failCancel)throw new IllegalStateException("fake unschedule fault"); }
    }
    public static void main(String[] args)throws Exception {
        List<String> lines=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);
        for(String line:lines.subList(1,lines.size())) {
            String[] row=line.split("\t",-1);int actual;
            try {
                actual=row[0].equals("request")?(ReminderDeletion.requested(row[1])?1:0):row[0].equals("yes")?(ReminderDeletion.affirmative(row[1])?1:0):ReminderDeletion.choice(row[1]);
            }catch(IllegalArgumentException invalid){actual=row[0].equals("choice")?-1:0;}
            check(actual==Integer.parseInt(row[2]));
        }
        AgendaEntry first=entry(1,"regar las plantas"),second=entry(2,"regar las plantas"),other=entry(3,"llamar al taller");
        ReminderDeletion flow=new ReminderDeletion();AgendaDialogue.Human initial=human("quiero borrar un recordatorio",1);
        ReminderDeletion.Step step=flow.step(initial,Arrays.asList(first,second,other),3,100000);
        check(step.stage.equals("TARGET") && step.selected==null);flow.spoken(initial.id,true,100300);
        AgendaDialogue.Human title=human("el de regar las plantas",2);
        step=flow.step(title,Arrays.asList(first,second,other),3,103000);
        check(step.stage.equals("CHOOSE") && step.selected==null);check(step.text.contains("1. "+first.title()) && step.text.contains("2. "+second.title()));
        check(!step.text.contains(other.title()));flow.spoken(title.id,true,103300);
        AgendaDialogue.Human index=human("el segundo",3);step=flow.step(index,Arrays.asList(first,second,other),3,106000);
        check(step.stage.equals("CONFIRM") && step.selected==second);check(step.text.contains(second.when()));flow.spoken(index.id,true,106300);
        String origin=flow.origin();AgendaDialogue.Human yes=human("sí, por favor",4);
        ReminderDeletion.Permit chosen=flow.claim(id(90),origin,second.id,second.revision,second.due,yes,113000,400000,109000);
        check(chosen.selected==second && !flow.active());check(chosen.begin(109000));check(!chosen.begin(109001));

        // A misheard affirmative is a new heard question, not an authorization.
        flow=ready(first);AgendaDialogue.Human wrong=human("te",2);
        step=flow.step(wrong,Arrays.asList(first),1,103000);check(step.stage.equals("CONFIRM") && step.selected==first);
        ReminderDeletion guard=flow;reject(()->guard.claim(id(90),guard.origin(),first.id,1,first.due,wrong,107000,400000,103000));
        check(!flow.active()); // An attempted grant before playback revokes the form, it cannot be resurrected.
        flow=ready(first);flow.step(wrong,Arrays.asList(first),1,103000);
        flow.spoken(wrong.id,true,103300);AgendaDialogue.Human newYes=human("sí",3);
        ReminderDeletion.Permit confirmed=flow.claim(id(91),flow.origin(),first.id,1,first.due,newYes,110000,400000,106000);
        check(confirmed.selected==first);confirmed.revoke();check(!confirmed.begin(106000));

        for(String fault:new String[]{"unheard","interrupted","same_turn","before_heard","expired","wrong_id","revision","due","origin","deadline"}) {
            ReminderDeletion form=ready(first);AgendaDialogue.Human answer=human("sí",2);
            if(fault.equals("unheard"))form= new ReminderDeletion();
            if(fault.equals("interrupted"))form.spoken(human("public",1).id,false,100300);
            if(fault.equals("same_turn"))answer=new AgendaDialogue.Human(human("public",1).id,2,"sí",102900,WALL+3000,103000,103000,64);
            if(fault.equals("before_heard"))answer=new AgendaDialogue.Human(human("public",2).id,2,"sí",100200,WALL+3000,103000,103000,64);
            if(fault.equals("expired"))answer=new AgendaDialogue.Human(human("public",2).id,2,"sí",399900,WALL+300000,400000,400000,64);
            ReminderDeletion selected=form;AgendaDialogue.Human h=answer;
            reject(()->selected.claim(id(90),fault.equals("origin")?"f0000000000000000000000000000000":selected.origin(),fault.equals("wrong_id")?other.id:first.id,
                fault.equals("revision")?2:1,fault.equals("due")?first.due+1:first.due,h,fault.equals("deadline")?120000:107000,700000,fault.equals("expired")?400000:103000));
        }
        for(String cancel:new String[]{"no","no gracias","cancela el borrado"}) {
            ReminderDeletion form=ready(first);ReminderDeletion.Step stopped=form.step(human(cancel,2),Arrays.asList(first),1,103000);
            check(stopped.status.equals("CANCELLED") && !form.active());
        }
        ReminderDeletion absent=new ReminderDeletion();step=absent.step(human("elimina el recordatorio de comprar pan",1),Arrays.asList(first),1,100000);
        check(step.status.equals("NOT_FOUND") && !absent.active());
        ReminderDeletion truncated=new ReminderDeletion();step=truncated.step(human("elimina el recordatorio de regar las plantas",1),Arrays.asList(first),2,100000);
        check(step.status.equals("UNAVAILABLE") && !truncated.active());
        AgendaEntry alarm=new AgendaEntry(id(8),AgendaEntry.Kind.ALARM,"",WALL+60000,AgendaEntry.ZONE,AgendaEntry.State.SCHEDULED,1);
        ReminderDeletion wrongKind=new ReminderDeletion();step=wrongKind.step(human("elimina el recordatorio de regar las plantas",1),Arrays.asList(first,alarm),2,100000);
        check(step.status.equals("UNAVAILABLE") && !wrongKind.active());
        AgendaEntry quoted=entry(7,"nota con \"comillas\"");ReminderDeletion dataOnly=new ReminderDeletion();
        step=dataOnly.step(human("elimina el recordatorio de regar las plantas",1),Arrays.asList(first,quoted),2,100000);
        check(step.selected==first); // Stored text is data, never another instruction or a parser exception.

        for(String fault:new String[]{"none","changed","missing","block","stop","cancel","remove"}) {
            FakeStore store=new FakeStore();store.rows.put(first.id,first);store.rows.put(other.id,other);store.rows.put(alarm.id,alarm);
            ReminderDeletion.Permit p=permit(first);check(p.begin(103000));
            if(fault.equals("changed"))store.rows.put(first.id,new AgendaEntry(first.id,first.kind,first.text,first.due,first.zone,first.state,2));
            if(fault.equals("missing"))store.rows.remove(first.id);
            store.failBlock=fault.equals("block");store.failRemove=fault.equals("remove");
            FakeEffects effects=new FakeEffects();effects.failStop=fault.equals("stop");effects.failCancel=fault.equals("cancel");
            AgendaCancellation.Result result=AgendaCancellation.remove(p.selected,store,effects);
            if(fault.equals("none")) { check(result==AgendaCancellation.Result.REMOVED);check(!store.rows.containsKey(first.id)); }
            else if(fault.equals("changed"))check(result==AgendaCancellation.Result.STALE);
            else if(fault.equals("missing"))check(result==AgendaCancellation.Result.NOT_FOUND);
            else if(fault.equals("block"))check(result==AgendaCancellation.Result.UNCONFIRMED);
            else { check(result==AgendaCancellation.Result.CLEANUP_PENDING);check(store.rows.get(first.id).text.isEmpty()); }
            check(store.rows.get(other.id)==other && store.rows.get(alarm.id)==alarm);
            for(String target:effects.stopped)check(target.equals(first.id));for(String target:effects.cancelled)check(target.equals(first.id));
            check(!p.begin(103001)); // No automatic replay, even for uncertain cleanup.
        }
        check(!VoiceSessionPolicy.toolsEventAllowed("reminder_delete_execute"));check(VoiceSessionPolicy.alarmEventAllowed("reminder_delete_execute"));
        System.out.println("Reminder deletion: "+checks+" assertions passed (fresh exact target, ambiguity, repeated question, single-use, fake-only cancellation faults).");
    }
}
