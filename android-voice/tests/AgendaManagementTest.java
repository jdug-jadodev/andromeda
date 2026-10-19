package local.doja.voice;

import java.util.*;

/** In-memory contract only. No Android database, scheduler, audio, device or human data. */
public final class AgendaManagementTest {
    private static int checks;
    static final String A = "00000000-0000-4000-8000-000000000001", B = "00000000-0000-4000-8000-000000000002";
    static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    static AgendaEntry reminder(String id, String text, AgendaEntry.State state, long revision) {
        return new AgendaEntry(id, AgendaEntry.Kind.REMINDER, text, 1792328400000L, AgendaEntry.ZONE, state, revision);
    }
    static final class Memory implements AgendaCancellation.Store, AgendaCancellation.Effects {
        Map<String, AgendaEntry> entries = new HashMap<>();
        List<String> order = new ArrayList<>();
        int effects;
        boolean failBlock, failStop, failCancel, failRemove, noDelete, changeDuringCleanup;
        public AgendaCancellation.Stage block(AgendaEntry selected) throws Exception {
            order.add("block");
            if (failBlock) throw new Exception("synthetic");
            AgendaEntry entry = entries.get(selected.id);
            if (entry == null || entry.kind != selected.kind) return AgendaCancellation.Stage.NOT_FOUND;
            if (entry.revision != selected.revision || !entry.text.equals(selected.text) || entry.state != selected.state || entry.due != selected.due)
                return AgendaCancellation.Stage.STALE;
            if (entry.state != AgendaEntry.State.CANCEL_PENDING)
                entries.put(entry.id, new AgendaEntry(entry.id, entry.kind, "", entry.due, entry.zone, AgendaEntry.State.CANCEL_PENDING, entry.revision + 1));
            return AgendaCancellation.Stage.BLOCKED;
        }
        public boolean removeBlocked(AgendaEntry selected) throws Exception {
            order.add("remove");
            if (failRemove) throw new Exception("synthetic");
            if (noDelete) return false;
            AgendaEntry entry = entries.get(selected.id);
            long revision = selected.state == AgendaEntry.State.CANCEL_PENDING ? selected.revision : selected.revision + 1;
            if (entry == null || entry.kind != selected.kind || entry.revision != revision
                    || entry.state != AgendaEntry.State.CANCEL_PENDING || !entry.text.isEmpty()) return false;
            entries.remove(selected.id); return true;
        }
        public boolean stopOutput(String id) {
            order.add("stop"); effects++;
            check(entries.get(id).state == AgendaEntry.State.CANCEL_PENDING && entries.get(id).text.isEmpty(), "Output stopped before durable block/redaction");
            if (failStop) throw new IllegalStateException("synthetic"); return true;
        }
        public void cancelSchedule(String id) throws Exception {
            order.add("cancel"); effects++;
            check(entries.get(id).state == AgendaEntry.State.CANCEL_PENDING, "Scheduler canceled before durable block");
            if (failCancel) throw new Exception("synthetic");
            if (changeDuringCleanup) {
                AgendaEntry entry = entries.get(id);
                entries.put(id, new AgendaEntry(entry.id, entry.kind, "", entry.due, entry.zone, entry.state, entry.revision + 1));
            }
        }
    }
    static void invalid(Runnable work) {
        try { work.run(); throw new AssertionError("Invalid agenda entry accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        AgendaEntry original = reminder(A, "Frase pública de prueba", AgendaEntry.State.FAILED, 1);
        check(original.title().equals("Frase pública de prueba"), "Reminder title missing");
        check(original.when().contains("Bogotá") && !original.when().contains("UTC"), "Zone missing");
        for (String zone : new String[]{"UTC", "Asia/Tokyo"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            check(original.when().equals("18/10/2026 08:00 · Bogotá"), "Date changed with device/default zone");
        }
        check(original.status(original.due + 1).contains("fallida"), "Failed delivery hidden");
        AgendaEntry alarm = new AgendaEntry(B, AgendaEntry.Kind.ALARM, "", original.due, AgendaEntry.ZONE, AgendaEntry.State.SCHEDULED, 1);
        check(alarm.status(original.due).contains("no confirmada") && alarm.status(original.due - 1).equals("Programado"), "Due is incorrectly treated as delivered");
        check(reminder(A, "Prueba", AgendaEntry.State.DELIVERING, 1).status(0).contains("sin finalización"), "Started output treated as completed");
        check(reminder(A, "Prueba", AgendaEntry.State.PENDING, 1).status(0).equals("Pendiente de entrega"), "Pending state lost");
        invalid(() -> reminder("not-an-id", "Prueba", AgendaEntry.State.PENDING, 1));
        invalid(() -> reminder(A, "", AgendaEntry.State.PENDING, 1));
        invalid(() -> reminder(A, " ", AgendaEntry.State.PENDING, 1));
        invalid(() -> reminder(A, "Prueba", AgendaEntry.State.CANCEL_PENDING, 1));
        invalid(() -> reminder(A, "Prueba", AgendaEntry.State.PENDING, 0));
        invalid(() -> reminder(A, String.join("", Collections.nCopies(161, "a")), AgendaEntry.State.PENDING, 1));
        invalid(() -> reminder(A, "<|system|>", AgendaEntry.State.PENDING, 1));
        invalid(() -> reminder(A, "a\u0000b", AgendaEntry.State.PENDING, 1));
        invalid(() -> new AgendaEntry(A, AgendaEntry.Kind.ALARM, "Un texto", original.due, AgendaEntry.ZONE, AgendaEntry.State.PENDING, 1));
        invalid(() -> new AgendaEntry(A, AgendaEntry.Kind.REMINDER, "Prueba", 0, AgendaEntry.ZONE, AgendaEntry.State.PENDING, 1));
        invalid(() -> new AgendaEntry(A, AgendaEntry.Kind.REMINDER, "Prueba", original.due, "UTC", AgendaEntry.State.PENDING, 1));
        Memory store = new Memory(); store.entries.put(A, original); store.entries.put(B, alarm);
        check(AgendaCancellation.remove(original, store, store) == AgendaCancellation.Result.REMOVED, "Selected failed reminder not removable");
        check(store.order.equals(Arrays.asList("block", "stop", "cancel", "remove")), "Unsafe cancellation order");
        check(store.entries.size() == 1 && store.entries.get(B) == alarm, "Unselected alarm changed");
        check(AgendaCancellation.remove(original, store, store) == AgendaCancellation.Result.NOT_FOUND && store.effects == 2, "Duplicate removal had side effects");
        for (AgendaEntry.State state : AgendaEntry.State.values()) {
            Memory fixture = new Memory();
            AgendaEntry selected = reminder(A, state == AgendaEntry.State.CANCEL_PENDING ? "" : "Prueba pública", state, 1);
            fixture.entries.put(A, selected);
            check(AgendaCancellation.remove(selected, fixture, fixture) == AgendaCancellation.Result.REMOVED, "Cannot remove state " + state);
        }
        for (String failure : Arrays.asList("block", "stop", "cancel", "remove", "not_removed")) {
            Memory fixture = new Memory(); fixture.entries.put(A, original);
            fixture.failBlock = failure.equals("block"); fixture.failStop = failure.equals("stop");
            fixture.failCancel = failure.equals("cancel"); fixture.failRemove = failure.equals("remove"); fixture.noDelete = failure.equals("not_removed");
            AgendaCancellation.Result result = AgendaCancellation.remove(original, fixture, fixture);
            check(result == (fixture.failBlock ? AgendaCancellation.Result.UNCONFIRMED : AgendaCancellation.Result.CLEANUP_PENDING), "Failure announced removal");
            check(fixture.entries.containsKey(A), "Uncertain cleanup lost its visible record");
            if (fixture.failBlock) check(fixture.effects == 0 && fixture.entries.get(A).text.equals(original.text), "Block failure ran effects or erased content");
            else {
                check(fixture.entries.get(A).text.isEmpty() && fixture.entries.get(A).state == AgendaEntry.State.CANCEL_PENDING, "Failed cleanup retained content/deliverable state");
                check(fixture.effects == 2, "One cleanup failure skipped the independent cleanup");
                fixture.failStop = fixture.failCancel = fixture.failRemove = fixture.noDelete = false;
                check(AgendaCancellation.remove(fixture.entries.get(A), fixture, fixture) == AgendaCancellation.Result.REMOVED, "Blocked cleanup cannot be retried manually");
            }
        }
        Memory stale = new Memory(); stale.entries.put(A, reminder(A, "Contenido público cambiado", AgendaEntry.State.PENDING, 2));
        check(AgendaCancellation.remove(original, stale, stale) == AgendaCancellation.Result.STALE && stale.effects == 0, "Stale UI selection authorized deletion");
        Memory race = new Memory(); race.entries.put(A, original); race.changeDuringCleanup = true;
        check(AgendaCancellation.remove(original, race, race) == AgendaCancellation.Result.CLEANUP_PENDING && race.entries.containsKey(A), "Final deletion ignored a revision changed during cleanup");
        Memory wrongKind = new Memory(); wrongKind.entries.put(A, new AgendaEntry(A, AgendaEntry.Kind.ALARM, "", original.due, AgendaEntry.ZONE, AgendaEntry.State.PENDING, 1));
        check(AgendaCancellation.remove(original, wrongKind, wrongKind) == AgendaCancellation.Result.NOT_FOUND && wrongKind.effects == 0, "Kind confusion deleted an alarm");
        check(AgendaCancellation.remove(null, store, store) == AgendaCancellation.Result.UNCONFIRMED, "Null selection accepted");
        final int[] stopped = new int[2];
        AgendaOutputs.Output one = () -> { stopped[0]++; return true; }, two = () -> { stopped[1]++; return true; };
        check(AgendaOutputs.begin(A, one) && AgendaOutputs.begin(B, two), "Owned outputs not registered");
        check(!AgendaOutputs.begin(A, two), "Output ownership replaced");
        AgendaOutputs.end(A, two);
        check(AgendaOutputs.stop(A) && stopped[0] == 1 && stopped[1] == 0, "Selected deletion stopped another output or wrong owner cleared it");
        check(AgendaOutputs.stop(A) && stopped[0] == 1, "Absent output stopped twice");
        check(AgendaOutputs.stop(B) && stopped[1] == 1, "Other owned output cannot be stopped independently");
        check(!AgendaOutputs.stop("invalid") && !AgendaOutputs.begin("invalid", one), "Invalid output ID accepted");
        AgendaOutputs.Output failing = () -> false;
        check(AgendaOutputs.begin(A, failing) && !AgendaOutputs.stop(A) && !AgendaOutputs.begin(A, one), "Failed stop lost owned output");
        AgendaOutputs.end(A, failing);
        AgendaOutputs.Output throwing = () -> { throw new IllegalStateException("synthetic"); };
        check(AgendaOutputs.begin(A, throwing) && !AgendaOutputs.stop(A), "Output exception announced a stop");
        AgendaOutputs.end(A, throwing);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String id = new UUID(0, 100 + i).toString(); ids.add(id);
            check(AgendaOutputs.begin(id, one), "Bounded output registry rejected within cap");
        }
        check(!AgendaOutputs.begin(A, one), "Output registry grew beyond cap");
        for (String id : ids) AgendaOutputs.end(id, one);
        System.out.println("Manual agenda management: " + checks + " assertions passed (fake records/effects only; no Android/device/audio/network).");
    }
}
