package local.doja.voice;

import java.util.*;

/** Bounded, process-local dialogue. No Android, disk, model, microphone or timers. */
final class ConversationMemory {
    static final long INACTIVITY_MS = 3600000;
    static final int MAX_CONVERSATIONS = 4, MAX_TURNS = 24, MAX_TEXT_UNITS = 5700;
    static final class Turn {
        final String id, user, assistant, webAnswer;
        final boolean webFollowup;
        final long humanMs;
        Turn(String id, String user, String assistant, long humanMs) {
            this(id, user, assistant, humanMs, null);
        }
        Turn(String id, String user, String assistant, long humanMs, String webAnswer) {
            this(id, user, assistant, humanMs, webAnswer, false);
        }
        Turn(String id, String user, String assistant, long humanMs, String webAnswer, boolean webFollowup) {
            this.id = id; this.user = user; this.assistant = assistant; this.humanMs = humanMs;
            this.webAnswer = webAnswer;
            this.webFollowup = webFollowup;
        }
    }
    static final class Lease {
        final String conversationId;
        final long generation;
        Lease(String id, long generation) { this.conversationId = id; this.generation = generation; }
    }
    static final class Snapshot {
        final String conversationId;
        final long lastHumanMs;
        final List<Turn> turns;
        Snapshot(String id, long lastHumanMs, List<Turn> turns) {
            this.conversationId = id; this.lastHumanMs = lastHumanMs;
            this.turns = Collections.unmodifiableList(new ArrayList<>(turns));
        }
    }
    static final class Info {
        final String id, label;
        final int turns;
        final long remainingMs;
        final boolean current, closed;
        Info(Chat chat, long now, boolean current) {
            id = chat.id; label = "Charla " + chat.number; turns = chat.turns.size();
            remainingMs = Math.max(0, INACTIVITY_MS - (now - chat.activity())); this.current = current;
            closed = chat.closed;
        }
    }
    private static final class Chat {
        final String id;
        final int number;
        long createdMs, lastHumanMs;
        boolean closed;
        final List<Turn> turns = new ArrayList<>();
        Chat(int number, long now) { this(number, now, newId()); }
        Chat(int number, long now, String id) { this.number = number; createdMs = now; this.id = id; }
        long activity() { return lastHumanMs > 0 ? lastHumanMs : createdMs; }
    }
    private final LinkedHashMap<String, Chat> chats = new LinkedHashMap<>();
    private String currentId = "";
    private Lease active;
    private long generation;
    private int number;

    private static String newId() { return UUID.randomUUID().toString().replace("-", ""); }
    private static boolean validId(String value) { return value != null && value.matches("[0-9a-f]{32}"); }
    private static boolean validText(String value, int limit) {
        return value != null && !value.trim().isEmpty() && value.length() <= limit && !value.contains("<|");
    }
    private boolean owns(Lease lease) {
        return lease != null && active != null && active.generation == lease.generation
            && active.conversationId.equals(lease.conversationId) && chats.containsKey(lease.conversationId);
    }
    synchronized void prune(long now) {
        Iterator<Chat> items = chats.values().iterator();
        while (items.hasNext()) {
            Chat chat = items.next();
            if (now < chat.activity() || now - chat.activity() >= INACTIVITY_MS) {
                if (active != null && active.conversationId.equals(chat.id)) {
                    // Do not retain expired text, or let a late answer revive it.
                    // An active bounded channel can accept a new human turn.
                    chat.turns.clear(); chat.lastHumanMs = 0; chat.createdMs = now;
                } else {
                    items.remove(); if (currentId.equals(chat.id)) currentId = "";
                }
            }
        }
    }
    synchronized String create(long now) {
        prune(now);
        if (active != null || chats.size() >= MAX_CONVERSATIONS) return null;
        Chat chat = new Chat(++number, now); chats.put(chat.id, chat); currentId = chat.id;
        return chat.id;
    }
    synchronized boolean select(String id, long now) {
        prune(now);
        if (active != null || !chats.containsKey(id)) return false;
        currentId = id; return true;
    }
    synchronized boolean forget(String id, long now) {
        prune(now);
        if (active != null && active.conversationId.equals(id)) active = null;
        boolean removed = chats.remove(id) != null;
        if (currentId.equals(id)) currentId = "";
        return removed;
    }
    synchronized Lease acquire(long now) {
        prune(now);
        // A new transport invalidates callbacks from any previous one.
        active = null;
        if (!chats.containsKey(currentId) && create(now) == null) throw new IllegalStateException("Selecciona una charla existente");
        chats.get(currentId).closed = false;
        active = new Lease(currentId, ++generation);
        return active;
    }
    synchronized List<Snapshot> catalog(long now) {
        prune(now);
        if (active != null) throw new IllegalStateException("Silencia la charla antes de abrir otra");
        List<Snapshot> result = new ArrayList<>();
        for (Chat chat : chats.values()) result.add(new Snapshot(chat.id, chat.lastHumanMs, chat.turns));
        return Collections.unmodifiableList(result);
    }
    synchronized Lease acquireExact(String id, boolean isNew, long now) {
        prune(now);
        if (active != null || !validId(id)) throw new IllegalStateException("Selección de charla no válida");
        if (isNew) {
            if (chats.containsKey(id) || chats.size() >= MAX_CONVERSATIONS) throw new IllegalStateException("No se puede crear otra charla");
            Chat chat = new Chat(++number, now, id);
            // Keep the client-proposed fresh identity, never a model-generated ID.
            chats.put(id, chat);
        } else if (!chats.containsKey(id)) throw new IllegalStateException("Esa charla ya no está disponible");
        currentId = id; chats.get(id).closed = false;
        active = new Lease(id, ++generation); return active;
    }
    synchronized void release(Lease lease) { if (owns(lease)) active = null; }
    synchronized boolean end(Lease lease, String turnId, long now) {
        prune(now);
        if (!owns(lease)) return false;
        Chat chat = chats.get(lease.conversationId);
        if (chat.turns.isEmpty() || !chat.turns.get(chat.turns.size() - 1).id.equals(turnId)) return false;
        chat.closed = true; active = null;
        if (currentId.equals(chat.id)) currentId = "";
        return true; // Closed is resumable, not forgotten. Does not renew expiry.
    }
    private void trim(Chat chat) {
        int units = 0;
        for (Turn turn : chat.turns) units += turn.user.length() + (turn.assistant == null ? 0 : turn.assistant.length()) + (turn.webAnswer == null ? 0 : turn.webAnswer.length());
        while (chat.turns.size() > MAX_TURNS || units > MAX_TEXT_UNITS) {
            Turn removed = chat.turns.remove(0);
            units -= removed.user.length() + (removed.assistant == null ? 0 : removed.assistant.length()) + (removed.webAnswer == null ? 0 : removed.webAnswer.length());
        }
    }
    synchronized Snapshot snapshot(Lease lease, long now) {
        prune(now);
        if (!owns(lease)) throw new IllegalStateException("Contexto de sesión cancelado");
        Chat chat = chats.get(lease.conversationId);
        return new Snapshot(chat.id, chat.lastHumanMs, chat.turns);
    }
    synchronized boolean human(Lease lease, String turnId, String text, long at, long now) {
        prune(now);
        if (!owns(lease) || !validId(turnId) || !validText(text, 250) || at <= 0
                || at > now + 100 || now - at > 30000) return false;
        Chat chat = chats.get(lease.conversationId);
        if (at < chat.lastHumanMs || chat.turns.stream().anyMatch(t -> t.id.equals(turnId))) return false;
        chat.turns.add(new Turn(turnId, text, null, at)); chat.lastHumanMs = at;
        trim(chat);
        return true;
    }
    synchronized boolean answer(Lease lease, String turnId, String text, long now) {
        return answer(lease, turnId, text, now, false);
    }
    synchronized boolean answer(Lease lease, String turnId, String text, long now, boolean webFollowup) {
        prune(now);
        if (!owns(lease) || !validText(text, 700)) return false;
        Chat chat = chats.get(lease.conversationId);
        if (chat.turns.isEmpty()) return false;
        int last = chat.turns.size() - 1; Turn turn = chat.turns.get(last);
        if (!turn.id.equals(turnId) || turn.assistant != null || turn.webAnswer != null) return false;
        chat.turns.set(last, new Turn(turn.id, turn.user, text, turn.humanMs, null, webFollowup));
        trim(chat);
        return true; // Assistant activity never renews human inactivity.
    }
    synchronized boolean webAnswer(Lease lease, String turnId, String text, long now) {
        prune(now);
        if (!owns(lease)) return false;
        try { WebAnswerPolicy.validateServiceAnswer(text); } catch (IllegalArgumentException invalid) { return false; }
        Chat chat = chats.get(lease.conversationId);
        if (chat.turns.isEmpty()) return false;
        int last = chat.turns.size() - 1; Turn turn = chat.turns.get(last);
        if (!turn.id.equals(turnId) || turn.assistant != null || turn.webAnswer != null) return false;
        chat.turns.set(last, new Turn(turn.id, turn.user, null, turn.humanMs, text)); trim(chat);
        return true;
    }
    synchronized List<Info> list(long now) {
        prune(now); List<Info> items = new ArrayList<>();
        for (Chat chat : chats.values()) items.add(new Info(chat, now, chat.id.equals(currentId)));
        return items;
    }
    synchronized long nextExpiry(long now) {
        prune(now); long next = Long.MAX_VALUE;
        for (Chat chat : chats.values()) next = Math.min(next, chat.activity() + INACTIVITY_MS);
        return next == Long.MAX_VALUE ? 0 : next;
    }
    static Snapshot emptySnapshot() { return new Snapshot(newId(), 0, Collections.emptyList()); }
}
