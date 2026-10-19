package local.doja.voice;

/** Block/redact first, stop only the selected output, unschedule, then remove. */
final class AgendaCancellation {
    enum Stage { BLOCKED, NOT_FOUND, STALE }
    enum Result { REMOVED, NOT_FOUND, STALE, CLEANUP_PENDING, UNCONFIRMED }
    interface Store {
        Stage block(AgendaEntry selected) throws Exception;
        boolean removeBlocked(AgendaEntry selected) throws Exception;
    }
    interface Effects {
        boolean stopOutput(String id) throws Exception;
        void cancelSchedule(String id) throws Exception;
    }
    static Result remove(AgendaEntry selected, Store store, Effects effects) {
        if (selected == null || store == null || effects == null) return Result.UNCONFIRMED;
        try {
            Stage stage = store.block(selected);
            if (stage == Stage.NOT_FOUND) return Result.NOT_FOUND;
            if (stage == Stage.STALE) return Result.STALE;
            if (stage != Stage.BLOCKED) return Result.UNCONFIRMED;
        } catch (Exception failure) { return Result.UNCONFIRMED; }
        boolean stopped = false, unscheduled = false;
        try { stopped = effects.stopOutput(selected.id); } catch (Exception ignored) {}
        try { effects.cancelSchedule(selected.id); unscheduled = true; } catch (Exception ignored) {}
        if (!stopped || !unscheduled) return Result.CLEANUP_PENDING;
        try { return store.removeBlocked(selected) ? Result.REMOVED : Result.CLEANUP_PENDING; }
        catch (Exception failure) { return Result.CLEANUP_PENDING; }
    }
}
