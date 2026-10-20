package local.doja.voice;

/** Sealed single-use permit; calendar payload is independently checked against current human turn. */
final class AgendaPermit {
    final String id;
    final AgendaRequestGuard.Value value;
    final long before, anchorWall, anchorBoot;
    private boolean begun, revoked;
    private AgendaPermit(String id, AgendaRequestGuard.Value value, long before, long wall, long boot) {
        this.id = id; this.value = value; this.before = before; anchorWall = wall; anchorBoot = boot;
    }
    static AgendaPermit validated(String id, AgendaEntry.Kind kind, String human, String when, String text,
            long due, long delay, long before, long wall, long boot, long now, long nowWall) {
        if (!AgendaEntry.validId(id) || before <= now || before - now > 4100 || boot <= 0 || boot > now + 100
                || now - boot > 26500 || Math.abs((nowWall - wall) - (now - boot)) > 1500) throw new IllegalArgumentException("Invalid agenda clock/grant");
        AgendaRequestGuard.Value value = AgendaRequestGuard.parse(kind, human, when, text, wall);
        if (value.due != due || value.delay != delay || due <= nowWall + 1000) throw new IllegalArgumentException("Agenda payload mismatch/past");
        return new AgendaPermit(id, value, before, wall, boot);
    }
    static AgendaPermit validatedDialogue(String id, AgendaEntry.Kind kind, AgendaDialogue dialogue, String origin,
            String turn, long sequence, String when, String text, long due, long delay, long before,
            long wall, long boot, long now, long nowWall) {
        if (!AgendaEntry.validId(id) || before <= now || before - now > 4100 || boot <= 0 || boot > now + 100
                || now - boot > 26500 || Math.abs((nowWall - wall) - (now - boot)) > 1500 || due <= nowWall + 1000)
            throw new IllegalArgumentException("Invalid agenda clock/grant");
        AgendaRequestGuard.Value value = dialogue.claim(kind, origin, turn, sequence, when, text, due, delay, now);
        return new AgendaPermit(id, value, before, wall, boot);
    }
    synchronized boolean begin(long now, long wall) {
        if (begun || revoked || now >= before || value.due <= wall + 1000 || Math.abs((wall - anchorWall) - (now - anchorBoot)) > 1500) return false;
        begun = true; return true;
    }
    synchronized boolean current(long now, long wall) { return begun && now < before && value.due > wall + 1000 && Math.abs((wall - anchorWall) - (now - anchorBoot)) <= 1500; }
    synchronized void revoke() { if (!begun) revoked = true; } // Never erase already accepted durable work.
}
