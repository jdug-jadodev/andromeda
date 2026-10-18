package local.doja.voice;

/** Transaction choreography, tested with fake storage/scheduler. Not connected to VoiceService. */
final class TimerCreation {
    enum Status { PREPARING, CREATED, DUPLICATE, REJECTED, FAILED, UNKNOWN }
    static final class Receipt {
        final String id;
        final int seconds;
        final Status status;
        Receipt(String id, int seconds, Status status) { this.id = id; this.seconds = seconds; this.status = status; }
    }
    interface Clock { long now(); }
    interface Store {
        Receipt receipt(String id) throws Exception;
        int activeCount() throws Exception;
        int receiptCount() throws Exception;
        void receipt(Receipt receipt) throws Exception;
        void persist(String id, int seconds) throws Exception;
        void schedule(String id, int seconds) throws Exception;
        void remove(String id) throws Exception;
        void cancel(String id) throws Exception;
    }
    static Receipt create(TimerActionPolicy.Permit permit, Store store, Clock clock) {
        if (permit == null) return new Receipt("", 0, Status.REJECTED);
        String id = permit.timerId; int seconds = permit.seconds;
        if (!permit.begin(clock.now())) return new Receipt(id, seconds, Status.REJECTED);
        boolean journaled = false, touchedTimer = false;
        try {
            Receipt previous = store.receipt(id);
            if (previous != null) return new Receipt(id, seconds, previous.seconds == seconds ? Status.DUPLICATE : Status.REJECTED);
            if (store.activeCount() >= 5 || store.receiptCount() >= 64 || !permit.withinDeadline(clock.now()))
                return new Receipt(id, seconds, Status.REJECTED);
            store.receipt(new Receipt(id, seconds, Status.PREPARING)); journaled = true;
            if (!permit.withinDeadline(clock.now())) throw new IllegalStateException("Plazo vencido");
            touchedTimer = true; store.persist(id, seconds);
            if (!permit.withinDeadline(clock.now())) throw new IllegalStateException("Plazo vencido");
            store.schedule(id, seconds);
            Receipt created = new Receipt(id, seconds, Status.CREATED); store.receipt(created);
            return created; // Only after both durable record and actual scheduler returned.
        } catch (Exception failure) {
            boolean cleaned = true;
            if (touchedTimer) {
                try { store.remove(id); } catch (Exception ignored) { cleaned = false; }
                try { store.cancel(id); } catch (Exception ignored) { cleaned = false; }
            }
            Status status = journaled && cleaned ? Status.FAILED : Status.UNKNOWN;
            if (journaled) try { store.receipt(new Receipt(id, seconds, status)); }
            catch (Exception ignored) { status = Status.UNKNOWN; }
            return new Receipt(id, seconds, status);
        }
    }
}
