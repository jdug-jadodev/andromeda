package local.doja.voice;

import java.util.*;

/** Deterministic RAM/lease/expiry tests; no Android, disk, network or audio. */
public final class ConversationMemoryTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static String id(int number) { return String.format(Locale.ROOT, "%032x", number); }
    public static void main(String[] args) {
        ConversationMemory memory = new ConversationMemory();
        ConversationMemory.Lease first = memory.acquire(100000);
        ConversationMemory.Snapshot empty = memory.snapshot(first, 100000);
        check(empty.turns.isEmpty() && empty.lastHumanMs == 0, "Fresh memory is not empty");
        check(empty.conversationId.matches("[0-9a-f]{32}"), "Invalid conversation ID");
        check(memory.human(first, id(1), "Pregunta pública", 100010, 100020), "Human turn rejected");
        check(memory.snapshot(first, 100020).turns.get(0).assistant == null, "Invented an assistant reply");
        check(memory.answer(first, id(1), "Respuesta pública", 101000), "Completed response rejected");
        check(!memory.answer(first, id(1), "Duplicate", 101001), "Duplicate response changed memory");
        check(memory.snapshot(first, 101001).lastHumanMs == 100010, "Assistant renewed human activity");
        memory.release(first);
        ConversationMemory.Lease resumed = memory.acquire(102000);
        ConversationMemory.Snapshot snapshot = memory.snapshot(resumed, 102000);
        check(snapshot.conversationId.equals(empty.conversationId), "Silence/reopen changed conversation");
        check(snapshot.turns.size() == 1 && snapshot.turns.get(0).assistant.equals("Respuesta pública"), "Reopen lost dialogue");
        check(!memory.human(first, id(2), "Late old session", 102001, 102002), "Old lease wrote a human turn");
        check(!memory.answer(first, id(1), "Late old answer", 102002), "Old lease wrote an answer");
        memory.release(first);
        check(memory.human(resumed, id(2), "Pregunta interrumpida", 102010, 102020), "Old release invalidated new lease");
        memory.release(resumed);
        ConversationMemory.Lease third = memory.acquire(103000);
        check(memory.snapshot(third, 103000).turns.size() == 2, "Interrupted human question lost");
        check(memory.snapshot(third, 103000).turns.get(1).assistant == null, "Unheard reply retained");
        try { snapshot.turns.clear(); throw new AssertionError("Mutable snapshot"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        for (int n = 3; n < 11; n++) {
            check(memory.human(third, id(n), "Public " + n, 103000 + n, 103000 + n), "Sequential turn rejected");
            if (n % 2 == 0) check(memory.answer(third, id(n), "Reply " + n, 103100 + n), "Reply rejected");
        }
        ConversationMemory.Snapshot bounded = memory.snapshot(third, 104000);
        check(bounded.turns.size() == 10 && bounded.turns.get(0).id.equals(id(1)), "Short history was arbitrarily cut at six turns");
        check(!memory.answer(third, id(8), "Out-of-order", 104001), "Out-of-order answer attached");
        check(!memory.human(third, id(10), "Duplicate", 104001, 104002), "Duplicate turn accepted");
        check(snapshot.turns.size() == 1, "Snapshot changed when live memory changed");
        long deadline = 103010 + ConversationMemory.INACTIVITY_MS;
        check(memory.nextExpiry(104000) == deadline, "Reading memory renewed expiry");
        check(memory.snapshot(third, deadline - 1).turns.size() == 10, "Expired early");
        check(memory.snapshot(third, deadline).turns.isEmpty(), "Active lease retained expired content");
        check(!memory.answer(third, id(10), "Too late", deadline), "Expired content revived by TTS");
        check(memory.human(third, id(11), "Nueva actividad", deadline, deadline), "Active channel cannot start a fresh history");
        check(memory.snapshot(third, deadline).turns.size() == 1, "Old expired history returned");
        memory.release(third);
        check(memory.list(deadline + ConversationMemory.INACTIVITY_MS).isEmpty(), "Inactive chat did not expire");

        ConversationMemory independent = new ConversationMemory();
        String a = independent.create(100000); ConversationMemory.Lease la = independent.acquire(100000);
        check(independent.human(la, id(20), "A", 100010, 100020), "Chat A rejected"); independent.release(la);
        String b = independent.create(200000); ConversationMemory.Lease lb = independent.acquire(200000);
        check(independent.human(lb, id(21), "B", 200010, 200020), "Chat B rejected"); independent.release(lb);
        check(independent.select(a, 300000), "Could not select A");
        List<ConversationMemory.Info> infos = independent.list(100010 + ConversationMemory.INACTIVITY_MS);
        check(infos.size() == 1 && infos.get(0).id.equals(b), "Another chat's human activity renewed A");
        check(independent.select(b, 100011 + ConversationMemory.INACTIVITY_MS), "Unexpired B missing");
        ConversationMemory.Lease active = independent.acquire(100011 + ConversationMemory.INACTIVITY_MS);
        check(independent.create(3700100) == null && !independent.select(b, 3700100), "Changed conversations while a transport was active");
        check(independent.forget(b, 3700100), "Forget failed");
        check(!independent.answer(active, id(21), "Forgotten answer", 3700101), "Forgotten chat revived");
        check(independent.list(3700101).isEmpty(), "Forget left content");

        ConversationMemory capacity = new ConversationMemory(); String remove = "";
        for (int n = 0; n < 4; n++) { String made = capacity.create(100000 + n); check(made != null, "Capacity too small"); if (n == 0) remove = made; }
        check(capacity.create(100100) == null && capacity.list(100100).size() == 4, "Capacity silently evicted another chat");
        check(capacity.forget(remove, 100101) && capacity.create(100102) != null, "Capacity not recovered after explicit forget");

        ConversationMemory bounds = new ConversationMemory(); ConversationMemory.Lease lease = bounds.acquire(100000);
        check(!bounds.human(lease, "wrong", "q", 100000, 100000), "Invalid turn ID accepted");
        check(!bounds.human(lease, id(30), " ", 100000, 100000), "Blank user accepted");
        check(!bounds.human(lease, id(30), String.join("", Collections.nCopies(251, "a")), 100000, 100000), "Long user accepted");
        check(!bounds.human(lease, id(30), "<|im_start|>", 100000, 100000), "Special token accepted");
        check(!bounds.human(lease, id(30), "q", 100101, 100000), "Future activity accepted");
        check(!bounds.human(lease, id(30), "q", 69999, 100000), "Stale activity accepted");
        check(bounds.human(lease, id(30), "q", 100000, 100000), "Valid activity rejected");
        check(!bounds.answer(lease, id(30), String.join("", Collections.nCopies(701, "a")), 100010), "Long assistant accepted");
        check(!bounds.answer(lease, id(30), "<|tool_call_start|>", 100010), "Tool-shaped history accepted");
        check(bounds.answer(lease, id(30), "a", 100010), "Valid answer rejected");
        check(bounds.snapshot(lease, 90000).turns.isEmpty(), "Clock rollback retained old content");

        ConversationMemory continuous = new ConversationMemory();
        ConversationMemory.Lease session = continuous.acquire(100000);
        String chatId = session.conversationId;
        for (int n = 1; n <= 28; n++) {
            check(continuous.human(session, id(100 + n), "q", 100000 + n, 100000 + n), "Short turn rejected");
            check(continuous.answer(session, id(100 + n), "a", 100100 + n), "Short answer rejected");
        }
        ConversationMemory.Snapshot capped = continuous.snapshot(session, 100200);
        check(capped.turns.size() == 24 && capped.turns.get(0).id.equals(id(105)), "Object-count safety cap failed");
        check(!continuous.end(session, id(127), 100200), "Out-of-order close ended chat");
        check(continuous.end(session, id(128), 100200), "Explicit close failed");
        check(!continuous.answer(session, id(128), "late", 100201), "Closed lease still writes");
        check(continuous.list(100201).get(0).closed && !continuous.list(100201).get(0).current, "Closed chat is still current");
        check(continuous.nextExpiry(100201) == 100028 + ConversationMemory.INACTIVITY_MS, "Closing renewed expiry");
        ConversationMemory.Lease fresh = continuous.acquire(100300);
        check(!fresh.conversationId.equals(chatId) && continuous.snapshot(fresh, 100300).turns.isEmpty(), "Next chat inherited closed history");
        continuous.release(fresh);
        check(continuous.select(chatId, 100400), "Closed chat cannot be selected for resuming");
        ConversationMemory.Lease continued = continuous.acquire(100400);
        check(continuous.snapshot(continued, 100400).turns.size() == 24 && !continuous.list(100400).get(0).closed,
            "Explicit resume lost closed history or kept closed flag");
        check(!continuous.end(session, id(128), 100400), "Old lease closed a resumed chat");

        ConversationMemory textBudget = new ConversationMemory();
        ConversationMemory.Lease budgetLease = textBudget.acquire(100000);
        String question = String.join("", Collections.nCopies(250, "q"));
        String answer = String.join("", Collections.nCopies(700, "a"));
        for (int n = 1; n <= 7; n++) {
            check(textBudget.human(budgetLease, id(200 + n), question, 100000 + n, 100000 + n), "Long question rejected");
            check(textBudget.answer(budgetLease, id(200 + n), answer, 100000 + n), "Long answer rejected");
        }
        ConversationMemory.Snapshot limited = textBudget.snapshot(budgetLease, 100100);
        check(limited.turns.size() == 6 && limited.turns.get(0).id.equals(id(202)), "Text budget grew past 0.5 maximum");
        int units = 0;
        for (ConversationMemory.Turn turn : limited.turns) units += turn.user.length() + turn.assistant.length();
        check(units == 5700, "Text budget not measured as UTF-16 units");
        check(!textBudget.human(budgetLease, id(208), String.join("", Collections.nCopies(126, "🚀")), 100200, 100200), "UTF-16 question limit exceeded");
        ConversationMemory entry = new ConversationMemory();
        List<ConversationMemory.Snapshot> noChats = entry.catalog(100000);
        check(noChats.isEmpty() && entry.list(100000).isEmpty(), "Opening voice entry created an empty chat");
        String explicit = id(400);
        ConversationMemory.Lease created = entry.acquireExact(explicit, true, 100000);
        check(created.conversationId.equals(explicit), "Client-proposed new identity changed");
        check(entry.human(created, id(401), "Luces", 100010, 100020), "New entry human turn rejected");
        check(entry.answer(created, id(401), "Respuesta", 100030), "New entry reply rejected");
        check(entry.end(created, id(401), 100040), "Could not close entry chat");
        List<ConversationMemory.Snapshot> catalog = entry.catalog(100100);
        check(catalog.size() == 1 && catalog.get(0).turns.size() == 1, "Closed chat missing from voice catalog");
        check(entry.nextExpiry(100100) == 3700010, "Exporting catalog renewed human inactivity");
        ConversationMemory.Lease exact = entry.acquireExact(explicit, false, 100200);
        check(entry.snapshot(exact, 100200).turns.size() == 1, "Exact resume lost history");
        entry.release(exact);
        try { entry.acquireExact(id(500), false, 100300); throw new AssertionError("Unknown resume silently created a chat"); }
        catch (IllegalStateException expected) { assertions++; }
        try { entry.acquireExact(explicit, true, 100300); throw new AssertionError("New entry reused old identity"); }
        catch (IllegalStateException expected) { assertions++; }
        try { entry.acquireExact(explicit, false, 3700010); throw new AssertionError("Expired resume silently created a chat"); }
        catch (IllegalStateException expected) { assertions++; }
        check(entry.list(3700010).isEmpty(), "Failed resume left a new empty chat");
        System.out.println("Conversation RAM: " + assertions + " assertions passed (no device/disk/network/audio).");
    }
}
