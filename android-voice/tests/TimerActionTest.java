package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fake disk/scheduler only: never programs an Android alarm or touches real data. */
public final class TimerActionTest {
    static int assertions;
    static final String SESSION = id('a'), CHAT = id('b'), ORIGIN = id('c'), CONFIRM = id('d');
    static final String TIMER = "00000000-0000-0000-0000-000000000001";
    static final String REQUEST = "pon un temporizador de dos minutos";
    static String id(char c) { char[] out = new char[32]; Arrays.fill(out, c); return new String(out); }
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static TimerActionPolicy.Proposal proposal(int seconds) {
        return new TimerActionPolicy.Proposal(id('e'), TIMER, id('f'), SESSION, CHAT, ORIGIN, 1, seconds, 145000);
    }
    static TimerActionPolicy ready() {
        TimerActionPolicy policy = new TimerActionPolicy(SESSION, CHAT);
        check(policy.offer(proposal(120), REQUEST, ORIGIN, 1, 100000), "Valid offer rejected");
        check(policy.offerSpoken(ORIGIN, 100000), "Spoken offer rejected"); return policy;
    }
    static TimerActionPolicy.Grant grant() { return new TimerActionPolicy.Grant(proposal(120), CONFIRM, 2, 99950, 109950); }
    static TimerActionPolicy.Permit permit(TimerActionPolicy policy) {
        TimerActionPolicy.Permit permit = policy.claim(grant(), "confirmo temporizador", CONFIRM, 2, 100000);
        check(permit != null, "Valid confirmation rejected"); return permit;
    }
    static final class FakeStore implements TimerCreation.Store {
        final Map<String, Integer> timers = new HashMap<>();
        final Map<String, TimerCreation.Receipt> receipts = new HashMap<>();
        final Set<String> scheduled = new HashSet<>(), failures = new HashSet<>();
        final List<String> operations = new ArrayList<>();
        Runnable afterPersist;
        int capacity;
        void step(String name) { operations.add(name); if (failures.contains(name)) throw new IllegalStateException(name); }
        public TimerCreation.Receipt receipt(String id) { step("read"); return receipts.get(id); }
        public int activeCount() { return timers.size(); }
        public int receiptCount() { return capacity > 0 ? capacity : receipts.size(); }
        public boolean exists(String id) { return timers.containsKey(id); }
        public void receipt(TimerCreation.Receipt value) { step("receipt_" + value.status.name()); receipts.put(value.id, value); }
        public void persist(String id, int seconds) {
            timers.put(id, seconds); step("persist"); if (afterPersist != null) afterPersist.run();
        }
        public void schedule(String id, int seconds) { scheduled.add(id); step("schedule"); }
        public void remove(String id) { step("remove"); timers.remove(id); }
        public void cancel(String id) { step("cancel"); scheduled.remove(id); }
    }
    public static void main(String[] args) throws Exception {
        check(!VoiceSessionPolicy.isConversationReady(7, true, false, true, true, true), "Action trial accepted as old conversation");
        check(VoiceSessionPolicy.isTimerReady(8, true, false, false, true, true, true), "Explicit timer contract rejected");
        check(!VoiceSessionPolicy.isTimerReady(7, true, false, false, true, true, true), "Confirmation-era timer protocol accepted");
        check(!VoiceSessionPolicy.isTimerReady(6, true, false, false, true, true, true), "Old protocol accepted as timer trial");
        check(!VoiceSessionPolicy.isTimerReady(7, false, false, false, true, true, true), "Unselected timer mode accepted");
        for (String event : new String[]{"timer_execute"}) {
            check(VoiceSessionPolicy.timerEventAllowed(event), "Typed timer event rejected");
            check(!VoiceSessionPolicy.conversationEventAllowed(event), "Action leaked into default conversation");
        }
        for (String event : new String[]{"timer_proposal", "timer", "cancel_timer", "list_timers", "stop_alarm", "tool_call"})
            check(!VoiceSessionPolicy.timerEventAllowed(event), "Legacy/broad action leaked into restricted trial");
        List<String> cases = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        check(cases.size() == 52, "Missing shared literal fixtures");
        for (String line : cases) {
            String[] parts = line.split("\t", 2);
            try {
                int actual = TimerRequestGuard.seconds(parts[1]);
                check(!parts[0].equals("-") && actual == Integer.parseInt(parts[0]), "Wrong literal: " + line);
            } catch (IllegalArgumentException invalid) { check(parts[0].equals("-"), "Valid request rejected: " + line); }
        }
        TimerActionPolicy policy = new TimerActionPolicy(SESSION, CHAT);
        check(!policy.offer(proposal(2), REQUEST, ORIGIN, 1, 100000), "Model invented seconds");
        check(!policy.offer(proposal(120), "no pongas un temporizador de dos minutos", ORIGIN, 1, 100000), "Negation authorized");
        check(!policy.offer(proposal(120), REQUEST, CONFIRM, 1, 100000), "Wrong originating turn accepted");
        check(!policy.offer(proposal(120), REQUEST, ORIGIN, 2, 100000), "Wrong originating sequence accepted");
        check(policy.offer(proposal(120), REQUEST, ORIGIN, 1, 100000), "Valid offer rejected");
        check(policy.claim(grant(), "confirmo temporizador", CONFIRM, 2, 100000) == null, "Unspoken offer authorized");
        check(!policy.offerSpoken(CONFIRM, 100000), "Foreign TTS completed offer");
        check(policy.offerSpoken(ORIGIN, 100000), "Valid TTS receipt rejected");
        check(!policy.offerSpoken(ORIGIN, 100000), "Duplicate TTS accepted");
        for (String text : new String[]{"sí", "confirmo", "adelante", "confirmo temporizador y cancela el otro", "no confirmo temporizador"})
            check(policy.claim(grant(), text, CONFIRM, 2, 100000) == null, "Generic/ambiguous confirmation authorized");
        check(policy.claim(grant(), "confirmo temporizador", id('9'), 2, 100000) == null, "Wrong confirmation turn accepted");
        check(policy.claim(grant(), "confirmo temporizador", CONFIRM, 1, 100000) == null, "Wrong confirmation sequence accepted");
        TimerActionPolicy.Proposal wrongNonce = new TimerActionPolicy.Proposal(id('e'), TIMER, id('9'), SESSION, CHAT, ORIGIN, 1, 120, 145000);
        check(policy.claim(new TimerActionPolicy.Grant(wrongNonce, CONFIRM, 2, 99950, 109950), "confirmo temporizador", CONFIRM, 2, 100000) == null, "Changed nonce accepted");
        check(policy.claim(new TimerActionPolicy.Grant(proposal(120), CONFIRM, 2, 99950, 109951), "confirmo temporizador", CONFIRM, 2, 100000) == null, "Extended deadline accepted");
        check(policy.claim(grant(), "confirmo temporizador", CONFIRM, 2, 109950) == null, "Expired grant accepted");
        TimerActionPolicy.Permit accepted = permit(policy);
        check(policy.claim(grant(), "confirmo temporizador", CONFIRM, 2, 100000) == null, "Duplicate grant accepted");
        check(!policy.offer(proposal(120), REQUEST, ORIGIN, 1, 100000), "Used proposal revived");
        TimerActionPolicy declined = ready(); declined.cancelProposal();
        check(!declined.offer(proposal(120), REQUEST, ORIGIN, 1, 100000), "Cancelled proposal replayed");
        for (TimerCreation.Status status : TimerCreation.Status.values()) {
            check(TimerCreation.recoverable(new TimerCreation.Receipt(TIMER, 120, status), 120)
                == (status == TimerCreation.Status.CREATED || status == TimerCreation.Status.PREPARING), "Wrong crash recovery status");
        }
        check(!TimerCreation.recoverable(null, 120), "Missing journal recovered");
        check(!TimerCreation.recoverable(new TimerCreation.Receipt(TIMER, 600, TimerCreation.Status.CREATED), 120), "Changed duration recovered");

        FakeStore success = new FakeStore();
        TimerCreation.Receipt result = TimerCreation.create(accepted, success, () -> 100000);
        check(result.status == TimerCreation.Status.CREATED && success.timers.get(TIMER) == 120 && success.scheduled.contains(TIMER), "Actual scheduler result not recorded");
        check(success.operations.equals(Arrays.asList("read", "receipt_PREPARING", "persist", "schedule", "receipt_CREATED")), "Creation was acknowledged too early");
        int before = success.operations.size();
        check(TimerCreation.create(accepted, success, () -> 100000).status == TimerCreation.Status.REJECTED, "Permit executed twice");
        check(success.operations.size() == before, "Duplicate permit touched storage");

        TimerActionPolicy revoked = ready(); TimerActionPolicy.Permit cancelled = permit(revoked); revoked.revoke();
        FakeStore untouched = new FakeStore();
        check(TimerCreation.create(cancelled, untouched, () -> 100000).status == TimerCreation.Status.REJECTED && untouched.operations.isEmpty(), "Stopped session mutated data");
        TimerActionPolicy expired = ready();
        check(TimerCreation.create(permit(expired), new FakeStore(), () -> 109950).status == TimerCreation.Status.REJECTED, "Late executor ran");

        FakeStore full = new FakeStore(); full.capacity = 64;
        check(TimerCreation.create(permit(ready()), full, () -> 100000).status == TimerCreation.Status.REJECTED && full.timers.isEmpty(), "Receipt bound silently evicted data");
        FakeStore occupied = new FakeStore(); occupied.timers.put(TIMER, 777);
        check(TimerCreation.create(permit(ready()), occupied, () -> 100000).status == TimerCreation.Status.REJECTED
            && occupied.timers.get(TIMER) == 777 && !occupied.operations.contains("remove"), "Existing timer was overwritten/deleted");
        FakeStore duplicate = new FakeStore(); duplicate.receipts.put(TIMER, new TimerCreation.Receipt(TIMER, 120, TimerCreation.Status.CREATED));
        check(TimerCreation.create(permit(ready()), duplicate, () -> 100000).status == TimerCreation.Status.DUPLICATE
            && !duplicate.operations.contains("schedule"), "A completed/expired ID was rescheduled");
        duplicate.receipts.put(TIMER, new TimerCreation.Receipt(TIMER, 777, TimerCreation.Status.CREATED));
        check(TimerCreation.create(permit(ready()), duplicate, () -> 100000).status == TimerCreation.Status.REJECTED, "Same ID changed duration");

        for (String fail : new String[]{"persist", "schedule", "receipt_CREATED"}) {
            FakeStore broken = new FakeStore(); broken.failures.add(fail);
            TimerCreation.Receipt failed = TimerCreation.create(permit(ready()), broken, () -> 100000);
            check(failed.status == TimerCreation.Status.FAILED && broken.timers.isEmpty() && broken.scheduled.isEmpty(), "Failure falsely acknowledged or failed rollback: " + fail);
            check(broken.operations.contains("remove") && broken.operations.contains("cancel"), "Missing targeted rollback");
        }
        FakeStore diskFail = new FakeStore(); diskFail.failures.add("receipt_PREPARING");
        check(TimerCreation.create(permit(ready()), diskFail, () -> 100000).status == TimerCreation.Status.UNKNOWN
            && diskFail.timers.isEmpty() && !diskFail.operations.contains("persist"), "Failed journal caused a timer");
        FakeStore cleanupFail = new FakeStore(); cleanupFail.failures.addAll(Arrays.asList("schedule", "cancel"));
        check(TimerCreation.create(permit(ready()), cleanupFail, () -> 100000).status == TimerCreation.Status.UNKNOWN, "Uncertain rollback reported success/failure as certain");
        long[] clock = {100000}; FakeStore slow = new FakeStore(); slow.afterPersist = () -> clock[0] = 110000;
        check(TimerCreation.create(permit(ready()), slow, () -> clock[0]).status == TimerCreation.Status.FAILED
            && !slow.operations.contains("schedule") && slow.timers.isEmpty(), "Scheduling after execution deadline");
        TimerActionPolicy inFlight = ready(); FakeStore committed = new FakeStore(); committed.afterPersist = inFlight::revoke;
        check(TimerCreation.create(permit(inFlight), committed, () -> 100000).status == TimerCreation.Status.CREATED,
            "Silencing cancelled an already accepted timer instead of only voice");
        TimerActionPolicy.Proposal direct = new TimerActionPolicy.Proposal(id('e'), TIMER, id('f'), SESSION, CHAT, ORIGIN, 1, 120, 104000);
        TimerActionPolicy.Grant request = new TimerActionPolicy.Grant(direct, ORIGIN, 1, 99900, 104000);
        TimerActionPolicy explicit = new TimerActionPolicy(SESSION, CHAT);
        for (String text : new String[]{"sí", "confirmo temporizador", "no pongas un temporizador de dos minutos",
                "ayer dije pon un temporizador de dos minutos", "pon un temporizador", "pon un temporizador de dos minutos y dime un chiste"})
            check(explicit.claimRequest(request, text, ORIGIN, 1, 100000) == null, "Incomplete/negated/recalled request authorized without confirmation");
        check(explicit.claimRequest(request, REQUEST, CONFIRM, 1, 100000) == null, "Another turn used the original request");
        check(explicit.claimRequest(request, REQUEST, ORIGIN, 2, 100000) == null, "Another request sequence accepted");
        check(explicit.claimRequest(new TimerActionPolicy.Grant(direct, CONFIRM, 2, 99900, 104000), REQUEST, CONFIRM, 2, 100000) == null,
            "A later confirmation was treated as a complete request");
        check(explicit.claimRequest(new TimerActionPolicy.Grant(direct, ORIGIN, 1, 99900, 104001), REQUEST, ORIGIN, 1, 100000) == null,
            "Direct execution deadline extended");
        TimerActionPolicy.Permit directPermit = explicit.claimRequest(request, REQUEST, ORIGIN, 1, 100000);
        check(directPermit != null, "Complete request unnecessarily required a confirmation or TTS offer");
        check(explicit.claimRequest(request, REQUEST, ORIGIN, 1, 100000) == null, "Direct request consumed twice");
        TimerActionPolicy.Proposal changedId = new TimerActionPolicy.Proposal(id('9'), TIMER, id('f'), SESSION, CHAT, ORIGIN, 1, 120, 104000);
        check(explicit.claimRequest(new TimerActionPolicy.Grant(changedId, ORIGIN, 1, 99900, 104000), REQUEST, ORIGIN, 1, 100000) == null,
            "Same request authorized a second proposal ID");
        FakeStore directStore = new FakeStore();
        check(TimerCreation.create(directPermit, directStore, () -> 100000).status == TimerCreation.Status.CREATED, "Direct request failed actual choreography");
        check(TimerCreation.create(directPermit, directStore, () -> 100000).status == TimerCreation.Status.REJECTED, "Direct permit scheduled twice");
        ConversationTurnPolicy closing = new ConversationTurnPolicy();
        check(closing.begin(ORIGIN, 1, 99900, 129900, 100000), "Direct result turn rejected");
        check(!closing.acceptReply(ORIGIN, 1, 99900, 129900, 100000, "action_result"), "Action close accepted in default conversation");
        check(closing.acceptReply(ORIGIN, 1, 99900, 129900, 100000, "action_result", true), "Validated action result could not close");
        check(closing.acceptStart(ORIGIN, 100100) && closing.finishSpeech(ORIGIN, true) && closing.awaitingClose(), "Action closed before speech completion");
        check(closing.acceptClose(ORIGIN, 1, 99900, 129900, 100500), "Exact action result close rejected");
        System.out.println("Timer authorization/transactions: " + assertions + " assertions passed (fake storage/scheduler, no device/audio).");
    }
}
