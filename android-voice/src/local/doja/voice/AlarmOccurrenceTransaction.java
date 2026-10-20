package local.doja.voice;

/** Actual occurrence transaction choreography; injectable storage/scheduler for offline fault tests. */
final class AlarmOccurrenceTransaction {
    interface Clock { long boot(); long wall(); }
    interface Store {
        boolean reserve(); AgendaEntry current();
        boolean move(AgendaEntry selected,long due,long elapsed);
        boolean finish(AgendaEntry selected);
        void schedule(); void failed();
        boolean receipt(String status,long due);
    }
    static final class Result {
        final String status; final long due;
        Result(String status,long due) { this.status=status; this.due=due; }
    }
    static Result confirmed(Store store,String status,long due) {
        return store.receipt(status,due) ? new Result(status,due) : new Result("UNKNOWN",0);
    }
    static Result apply(AlarmOccurrencePolicy.Permit permit,Clock clock,Store store) {
        if(!permit.begin(clock.boot(),clock.wall())) return new Result("REJECTED",0);
        try {
            if(!store.reserve()) return new Result("UNKNOWN",0);
            AgendaEntry current=store.current();
            if(current==null || current.kind!=AgendaEntry.Kind.ALARM || current.state!=AgendaEntry.State.DELIVERING || current.revision!=permit.selected.revision
                    || current.due!=permit.selected.due || current.weekday!=permit.selected.weekday || current.minute!=permit.selected.minute || clock.boot()>=permit.before
                    || Math.abs((clock.wall()-permit.wall)-(clock.boot()-permit.boot))>1500)
                return confirmed(store,"STALE",0);
            if(permit.action==AlarmOccurrencePolicy.Action.DISMISS) return confirmed(store,store.finish(current)?"DISMISSED":"UNKNOWN",0);
            long due=Math.addExact(permit.wall,300000),elapsed=Math.addExact(permit.boot,300000);
            if(!store.move(current,due,elapsed)) return confirmed(store,"STALE",0);
            try { store.schedule(); }
            catch(RuntimeException failure) { store.failed(); return confirmed(store,"UNKNOWN",0); }
            return confirmed(store,"SNOOZED",due);
        } catch(RuntimeException failure) { return new Result("UNKNOWN",0); } // Never retry anything, including receipts.
    }
}
