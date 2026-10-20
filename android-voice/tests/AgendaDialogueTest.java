package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class AgendaDialogueTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Agenda dialogue assertion " + checks); }
    static String text(String value) {
        switch (value) {
            case "SUBJECT": return AgendaDialogue.SUBJECT;
            case "WHEN": return AgendaDialogue.WHEN;
            case "ALARM_WHEN": return AgendaDialogue.ALARM_WHEN;
            case "INVALID_TIME": return AgendaDialogue.INVALID_TIME;
            case "OTHER_REQUEST": return AgendaDialogue.OTHER_REQUEST;
            case "CANCELLED": return AgendaDialogue.CANCELLED;
            default: return value;
        }
    }
    public static void main(String[] args) throws Exception {
        String previous = ""; AgendaDialogue draft = null;
        List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            String[] row = line.split("\t", -1);
            if (!row[0].equals(previous)) { draft = new AgendaDialogue(); previous = row[0]; }
            int step = Integer.parseInt(row[1]); long now = 100000 + (step-1)*3000L;
            AgendaDialogue.Human human;
            AgendaDialogue.Decision result;
            try {
                human = new AgendaDialogue.Human(String.format("%032x",step),step,row[3],now-100,1792328400000L+(step-1)*3000L,now,now);
                result = step == 1 ? draft.begin(AgendaEntry.Kind.valueOf(row[2]),human) : draft.answer(human,now);
            } catch (IllegalArgumentException error) { check(row[5].equals("INVALID")); continue; }
            check(!row[5].equals("INVALID"));
            if (row[5].equals("none")) { check(result == null); continue; }
            check(result != null && result.kind.equals(row[5])); check(result.text.equals(text(row[6])));
            if (result.kind.equals("proposal")) {
                AgendaRequestGuard.Value value = draft.claim(result.eventKind,result.origin,human.id,human.sequence,result.when,result.text,result.value.due,result.value.delay,now);
                check(value.due > human.wall); check(!draft.active());
                try { draft.claim(result.eventKind,result.origin,human.id,human.sequence,result.when,result.text,result.value.due,result.value.delay,now); throw new AssertionError("Replay accepted"); }
                catch (IllegalArgumentException expected) { checks++; }
            }
            if (!row[4].equals("-")) draft.spoken(human.id,row[4].equals("1"));
        }
        for (boolean continuing : new boolean[]{false,true}) {
            check(ActionCompletionPolicy.valid(continuing,continuing ? "" : "action_result",continuing));
            check(!ActionCompletionPolicy.valid(continuing,continuing ? "action_result" : "",!continuing));
            check(ActionCompletionPolicy.text("Recibo real.",continuing).endsWith(continuing ? ActionCompletionPolicy.OFFER : "real."));
        }
        lines = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        for (String line : lines.subList(1,lines.size())) {
            String[] row = line.split("\t",-1);
            AgendaRequestGuard.Value value = new AgendaRequestGuard.Value(AgendaEntry.Kind.valueOf(row[1]),row[2],Long.parseLong(row[3]),Long.parseLong(row[4]));
            check(AgendaSpeechPolicy.text(row[0],value,row[5].equals("1")).equals(row[6]));
        }
        // Grant fields cannot replace a locally reconstructed form value.
        AgendaDialogue draftGuard = new AgendaDialogue();
        AgendaDialogue.Human first = new AgendaDialogue.Human(String.format("%032x",1),1,"recuérdame lavar la loza",99900,1792328400000L,100000,100000);
        draftGuard.begin(AgendaEntry.Kind.REMINDER,first); draftGuard.spoken(first.id,true);
        AgendaDialogue.Human last = new AgendaDialogue.Human(String.format("%032x",2),2,"en cinco minutos",102900,1792328403000L,103000,103000);
        AgendaDialogue.Decision proposal = draftGuard.answer(last,103000);
        try { draftGuard.claim(proposal.eventKind,"f0000000000000000000000000000000",last.id,last.sequence,proposal.when,proposal.text,proposal.value.due,proposal.value.delay,103000); throw new AssertionError("Foreign origin accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { draftGuard.claim(proposal.eventKind,proposal.origin,last.id,last.sequence,proposal.when,"inventado",proposal.value.due,proposal.value.delay,103000); throw new AssertionError("Invented body accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        AgendaPermit permit = AgendaPermit.validatedDialogue("00000000-0000-0000-0000-000000000001",proposal.eventKind,draftGuard,proposal.origin,last.id,last.sequence,
            proposal.when,proposal.text,proposal.value.due,proposal.value.delay,107000,last.wall,last.boot,103000,last.wall);
        check(permit.begin(103000,last.wall)); check(!permit.begin(103000,last.wall)); check(!draftGuard.active());
        System.out.println("Guided agenda and continuation: " + checks + " assertions passed (public shared turns, fake clocks, no device).");
    }
}
