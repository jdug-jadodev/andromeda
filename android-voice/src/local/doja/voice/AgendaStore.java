package local.doja.voice;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.SystemClock;
import java.io.Closeable;
import java.util.*;

/** Android alone persists/schedules events; no conversation or request transcript in the database. */
final class AgendaStore implements Closeable, AgendaCancellation.Store {
    static final String DATABASE = "agenda.db";
    static final Object COORDINATOR = new Object();
    private static final String COLUMNS = "id,kind,text,due_ms,zone,state,revision,elapsed_due,weekday,minute_of_day";
    private final Context context;
    interface Scheduler { void schedule(AgendaEntry entry); void cancel(String id); }
    private final Scheduler scheduler;
    enum Status { CREATED, DUPLICATE, REJECTED, FAILED, UNKNOWN }
    static final class AlarmReceipt {
        final String operation,event,status; final long due;
        AlarmReceipt(String operation,String event,String status,long due) { this.operation=operation; this.event=event; this.status=status; this.due=due; }
    }
    private final SQLiteOpenHelper helper;
    static final class Page {
        final List<AgendaEntry> entries;
        final long total;
        Page(List<AgendaEntry> entries, long total) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries)); this.total = total;
        }
    }
    AgendaStore(Context context) { this(context, DATABASE); }
    // Explicit isolated database name for future Android contract tests, not a user path.
    AgendaStore(Context context, String database) { this(context, database, null); }
    AgendaStore(Context context, String database, Scheduler scheduler) {
        if (!DATABASE.equals(database) && !"agenda-isolated-test.db".equals(database)) throw new IllegalArgumentException("Invalid agenda database");
        this.context = context.getApplicationContext();
        if (scheduler != null && !"agenda-isolated-test.db".equals(database)) throw new IllegalArgumentException("Fake scheduler restricted to isolated test database");
        this.scheduler = scheduler;
        helper = new SQLiteOpenHelper(this.context, database, null, 3) {
            public void onConfigure(SQLiteDatabase db) {
                // This PRAGMA returns a row on Android SQLite. execSQL() rejects
                // row-returning statements even though desktop SQLite accepts it.
                try (Cursor configured = db.rawQuery("PRAGMA secure_delete=ON", null)) {
                    if (!configured.moveToFirst() || configured.getInt(0) != 1)
                        throw new SQLiteException("Agenda secure deletion unavailable");
                }
            }
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE agenda (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('ALARM','REMINDER')), "
                    + "text TEXT NOT NULL, due_ms INTEGER NOT NULL CHECK(due_ms > 0), zone TEXT NOT NULL CHECK(zone='America/Bogota'), "
                    + "state TEXT NOT NULL CHECK(state IN ('SCHEDULED','PENDING','DELIVERING','FAILED','CANCEL_PENDING')), "
                    + "revision INTEGER NOT NULL CHECK(revision > 0), elapsed_due INTEGER NOT NULL DEFAULT 0 CHECK(elapsed_due>=0), "
                    + "weekday INTEGER NOT NULL DEFAULT 0 CHECK(weekday BETWEEN 0 AND 7 AND (kind='ALARM' OR weekday=0)), "
                    + "minute_of_day INTEGER NOT NULL DEFAULT -1 CHECK((weekday=0 AND minute_of_day=-1) OR (weekday BETWEEN 1 AND 7 AND minute_of_day BETWEEN 0 AND 1439)), CHECK(length(text)<=160), "
                    + "CHECK(kind!='ALARM' OR text=''), CHECK(state!='CANCEL_PENDING' OR text=''), "
                    + "CHECK(kind!='REMINDER' OR state='CANCEL_PENDING' OR length(trim(text))>0))");
                db.execSQL("CREATE INDEX agenda_kind_due ON agenda(kind,due_ms,id)");
                receipts(db);
                actionReceipts(db);
            }
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                if (oldVersion == 1) {
                    db.execSQL("ALTER TABLE agenda ADD COLUMN elapsed_due INTEGER NOT NULL DEFAULT 0 CHECK(elapsed_due>=0)"); receipts(db);
                }
                if (oldVersion >= 1 && oldVersion <= 2 && newVersion == 3) {
                    db.execSQL("ALTER TABLE agenda ADD COLUMN weekday INTEGER NOT NULL DEFAULT 0 CHECK(weekday BETWEEN 0 AND 7 AND (kind='ALARM' OR weekday=0))");
                    db.execSQL("ALTER TABLE agenda ADD COLUMN minute_of_day INTEGER NOT NULL DEFAULT -1 CHECK((weekday=0 AND minute_of_day=-1) OR (weekday BETWEEN 1 AND 7 AND minute_of_day BETWEEN 0 AND 1439))");
                    actionReceipts(db);
                } else throw new SQLiteException("Agenda migration required"); // Never drop or reinterpret user records.
            }
            private void receipts(SQLiteDatabase db) { db.execSQL("CREATE TABLE agenda_receipts (id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL, expires_ms INTEGER NOT NULL)"); }
            private void actionReceipts(SQLiteDatabase db) { db.execSQL("CREATE TABLE agenda_actions (id TEXT PRIMARY KEY NOT NULL, event_id TEXT NOT NULL, operation TEXT NOT NULL, status TEXT NOT NULL, due_ms INTEGER NOT NULL, expires_ms INTEGER NOT NULL)"); }
        };
        helper.setWriteAheadLoggingEnabled(false);
    }
    private static AgendaEntry entry(Cursor cursor) {
        return new AgendaEntry(cursor.getString(0), AgendaEntry.Kind.valueOf(cursor.getString(1)), cursor.getString(2),
            cursor.getLong(3), cursor.getString(4), AgendaEntry.State.valueOf(cursor.getString(5)), cursor.getLong(6), cursor.getLong(7),cursor.getInt(8),cursor.getInt(9));
    }
    Page list(AgendaEntry.Kind kind) {
        if (kind == null) throw new IllegalArgumentException("Missing agenda kind");
        SQLiteDatabase db = helper.getReadableDatabase(); List<AgendaEntry> entries = new ArrayList<>(); long total;
        db.beginTransaction();
        try {
            try (Cursor count = db.rawQuery("SELECT COUNT(*) FROM agenda WHERE kind=?", new String[]{kind.name()})) {
                if (!count.moveToFirst()) throw new SQLiteException("Agenda count unavailable"); total = count.getLong(0);
            }
            try (Cursor rows = db.rawQuery("SELECT "+COLUMNS+" FROM agenda WHERE kind=? ORDER BY due_ms,id LIMIT 50", new String[]{kind.name()})) {
                while (rows.moveToNext()) entries.add(entry(rows));
            }
            db.setTransactionSuccessful(); return new Page(entries, total);
        } finally { db.endTransaction(); }
    }
    public AgendaCancellation.Stage block(AgendaEntry selected) {
        synchronized (COORDINATOR) { return blockLocked(selected); }
    }
    private AgendaCancellation.Stage blockLocked(AgendaEntry selected) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            try (Cursor row = db.rawQuery("SELECT "+COLUMNS+" FROM agenda WHERE id=? AND kind=?", new String[]{selected.id, selected.kind.name()})) {
                if (!row.moveToFirst()) return AgendaCancellation.Stage.NOT_FOUND;
                AgendaEntry current = entry(row);
                if (current.revision != selected.revision || current.due != selected.due || !current.text.equals(selected.text)
                        || current.state != selected.state || current.elapsedDue != selected.elapsedDue || current.weekday!=selected.weekday || current.minute!=selected.minute) return AgendaCancellation.Stage.STALE;
                if (current.state != AgendaEntry.State.CANCEL_PENDING) {
                    if (current.revision >= Long.MAX_VALUE - 1) throw new SQLiteException("Agenda revision exhausted");
                    ContentValues values = new ContentValues(); values.put("text", ""); values.put("state", "CANCEL_PENDING");
                    values.put("revision", current.revision + 1);
                    if (db.update("agenda", values, "id=? AND kind=? AND revision=?", new String[]{selected.id, selected.kind.name(), Long.toString(selected.revision)}) != 1)
                        throw new SQLiteException("Agenda selection changed");
                }
            }
            db.setTransactionSuccessful(); return AgendaCancellation.Stage.BLOCKED;
        } finally { db.endTransaction(); }
    }
    public boolean removeBlocked(AgendaEntry selected) {
        if (selected == null) return false;
        long revision = selected.state == AgendaEntry.State.CANCEL_PENDING ? selected.revision : selected.revision + 1;
        synchronized (COORDINATOR) {
            return helper.getWritableDatabase().delete("agenda", "id=? AND kind=? AND revision=? AND state='CANCEL_PENDING' AND text=''",
                new String[]{selected.id, selected.kind.name(), Long.toString(revision)}) == 1;
        }
    }
    AgendaEntry find(String id) {
        if (!AgendaEntry.validId(id)) return null;
        try (Cursor row = helper.getReadableDatabase().rawQuery("SELECT "+COLUMNS+" FROM agenda WHERE id=?", new String[]{id})) {
            return row.moveToFirst() ? entry(row) : null;
        }
    }
    Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AgendaEntry.Kind kind : AgendaEntry.Kind.values()) counts.put(kind.name(), 0L);
        try (Cursor rows = helper.getReadableDatabase().rawQuery("SELECT kind,COUNT(*) FROM agenda GROUP BY kind", null)) {
            while (rows.moveToNext()) {
                String kind = rows.getString(0);
                if (!counts.containsKey(kind)) throw new SQLiteException("Unknown agenda kind");
                counts.put(kind, rows.getLong(1));
            }
        }
        return counts; // Counts only: never IDs, text, dates or content fingerprints.
    }
    private void receipt(SQLiteDatabase db, String id, String status) {
        ContentValues values = new ContentValues(); values.put("id", id); values.put("status", status);
        values.put("expires_ms", System.currentTimeMillis() + 48 * 3600000L);
        db.replaceOrThrow("agenda_receipts", null, values);
    }
    Status create(AgendaPermit permit) {
        if (permit == null || !permit.begin(SystemClock.elapsedRealtime(), System.currentTimeMillis())) return Status.REJECTED;
        synchronized (COORDINATOR) {
            SQLiteDatabase db = helper.getWritableDatabase(); boolean persisted = false;
            try {
                db.beginTransaction();
                try {
                    db.delete("agenda_receipts", "expires_ms<? AND id NOT IN (SELECT id FROM agenda)", new String[]{Long.toString(System.currentTimeMillis())});
                    try (Cursor previous = db.rawQuery("SELECT id FROM agenda_receipts WHERE id=?", new String[]{permit.id})) {
                        if (previous.moveToFirst()) return Status.DUPLICATE;
                    }
                    try (Cursor count = db.rawQuery("SELECT (SELECT COUNT(*) FROM agenda),(SELECT COUNT(*) FROM agenda_receipts)", null)) {
                        if (!count.moveToFirst() || count.getLong(0) >= 20 || count.getLong(1) >= 64) return Status.REJECTED;
                    }
                    if (!permit.current(SystemClock.elapsedRealtime(), System.currentTimeMillis())) return Status.REJECTED;
                    receipt(db, permit.id, "PREPARING");
                    ContentValues values = new ContentValues(); values.put("id", permit.id); values.put("kind", permit.value.kind.name());
                    values.put("text", permit.value.text); values.put("due_ms", permit.value.due); values.put("zone", AgendaEntry.ZONE);
                    values.put("state", "SCHEDULED"); values.put("revision", 1);
                    values.put("elapsed_due", permit.value.delay == 0 ? 0 : permit.anchorBoot + permit.value.delay);
                    values.put("weekday",permit.value.weekday); values.put("minute_of_day",permit.value.minute);
                    db.insertOrThrow("agenda", null, values); db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                persisted = true;
                if (!permit.current(SystemClock.elapsedRealtime(), System.currentTimeMillis())) throw new SQLiteException("Grant expired");
                schedule(find(permit.id)); receipt(db, permit.id, "CREATED"); return Status.CREATED;
            } catch (Exception failure) {
                if (!persisted) return Status.UNKNOWN;
                boolean cleaned = true;
                try { if (scheduler == null) effects(context).cancelSchedule(permit.id); else scheduler.cancel(permit.id); } catch (Exception ignored) { cleaned = false; }
                try {
                    ContentValues values = new ContentValues(); values.put("text", ""); values.put("state", "CANCEL_PENDING"); values.put("revision", 2);
                    db.update("agenda", values, "id=?", new String[]{permit.id});
                    if (cleaned) db.delete("agenda", "id=?", new String[]{permit.id});
                    receipt(db, permit.id, cleaned ? "FAILED" : "UNKNOWN");
                } catch (Exception ignored) { cleaned = false; }
                return cleaned ? Status.FAILED : Status.UNKNOWN;
            }
        }
    }
    void schedule(AgendaEntry entry) {
        if (entry == null || entry.state != AgendaEntry.State.SCHEDULED) throw new IllegalArgumentException("Not scheduled");
        if (scheduler != null) { scheduler.schedule(entry); return; }
        PendingIntent operation = PendingIntent.getBroadcast(context, 0, deliveryIntent(context, entry.id), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        context.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(entry.elapsedDue == 0 ? AlarmManager.RTC_WAKEUP : AlarmManager.ELAPSED_REALTIME_WAKEUP,
            entry.elapsedDue == 0 ? entry.due : entry.elapsedDue, operation);
    }
    AgendaEntry claim(String id) {
        synchronized (COORDINATOR) {
            AgendaEntry entry = find(id);
            if (entry == null || entry.state != AgendaEntry.State.SCHEDULED) return null;
            if(unsettled(id)) { changeState(entry,AgendaEntry.State.PENDING); return null; }
            if (!created(id)) { changeState(entry, AgendaEntry.State.PENDING); return null; }
            long lateness = entry.elapsedDue == 0 ? System.currentTimeMillis() - entry.due : SystemClock.elapsedRealtime() - entry.elapsedDue;
            if (lateness < 0) { schedule(entry); return null; }
            if (lateness > 300000) {
                if(entry.weekday!=0) advanceWeekly(entry); else changeState(entry, AgendaEntry.State.PENDING);
                return null;
            }
            return changeState(entry, AgendaEntry.State.DELIVERING) ? find(id) : null;
        }
    }
    private boolean created(String id) {
        try (Cursor row = helper.getReadableDatabase().rawQuery("SELECT status FROM agenda_receipts WHERE id=?", new String[]{id})) {
            return row.moveToFirst() && "CREATED".equals(row.getString(0));
        }
    }
    private boolean changeState(AgendaEntry entry, AgendaEntry.State state) {
        if(entry==null || entry.revision>=Long.MAX_VALUE-1) return false;
        ContentValues values = new ContentValues(); values.put("state", state.name()); values.put("revision", entry.revision + 1);
        return helper.getWritableDatabase().update("agenda", values, "id=? AND revision=? AND state=?",
            new String[]{entry.id, Long.toString(entry.revision), entry.state.name()}) == 1;
    }
    boolean finish(AgendaEntry entry, boolean delivered) {
        synchronized (COORDINATOR) {
            AgendaEntry current = find(entry.id);
            if (current == null || current.revision != entry.revision || current.state != AgendaEntry.State.DELIVERING) return false;
            if(current.weekday!=0) return advanceWeekly(current); // Retire this occurrence, NEVER the series.
            if (!delivered) return changeState(current, AgendaEntry.State.FAILED);
            SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
            try {
                if (db.delete("agenda", "id=? AND revision=? AND state='DELIVERING'", new String[]{entry.id, Long.toString(entry.revision)}) != 1) return false;
                receipt(db, entry.id, "DELIVERED"); db.setTransactionSuccessful(); return true;
            } finally { db.endTransaction(); }
        }
    }
    private boolean advanceWeekly(AgendaEntry entry) {
        if(entry.revision>=Long.MAX_VALUE-1) return false;
        ContentValues values=new ContentValues(); values.put("due_ms",AlarmSchedule.next(entry.weekday,entry.minute,System.currentTimeMillis()));
        values.put("elapsed_due",0); values.put("state","SCHEDULED"); values.put("revision",entry.revision+1);
        if(helper.getWritableDatabase().update("agenda",values,"id=? AND revision=? AND state=?",new String[]{entry.id,Long.toString(entry.revision),entry.state.name()})!=1) return false;
        AgendaEntry next=find(entry.id);
        try { schedule(next); return true; }
        catch(Exception failure) { changeState(next,AgendaEntry.State.FAILED); return false; }
    }
    private void alarmReceipt(SQLiteDatabase db,String operation,String event,String action,String status,long due) {
        ContentValues values=new ContentValues(); values.put("id",operation); values.put("event_id",event); values.put("operation",action);
        values.put("status",status); values.put("due_ms",due); values.put("expires_ms",System.currentTimeMillis()+48*3600000L);
        db.replaceOrThrow("agenda_actions",null,values);
    }
    private boolean reserveAction(String operation,String event,String action) {
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("agenda_actions","expires_ms<?",new String[]{Long.toString(System.currentTimeMillis())});
            try(Cursor count=db.rawQuery("SELECT COUNT(*) FROM agenda_actions",null)) { if(!count.moveToFirst() || count.getInt(0)>=64) return false; }
            try(Cursor old=db.rawQuery("SELECT id FROM agenda_actions WHERE id=?",new String[]{operation})) { if(old.moveToFirst()) return false; }
            alarmReceipt(db,operation,event,action,"PREPARING",0); db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    private AlarmReceipt confirmAction(String operation,String event,String action,String status,long due) {
        SQLiteDatabase db=helper.getWritableDatabase(); alarmReceipt(db,operation,event,action,status,due);
        try(Cursor row=db.rawQuery("SELECT event_id,status,due_ms FROM agenda_actions WHERE id=?",new String[]{operation})) {
            if(!row.moveToFirst() || !event.equals(row.getString(0)) || !status.equals(row.getString(1)) || due!=row.getLong(2)) return new AlarmReceipt(operation,event,"UNKNOWN",0);
            return new AlarmReceipt(operation,event,status,due);
        }
    }
    AlarmReceipt deleteAlarm(AlarmDeletion.Permit permit) {
        if(permit==null) throw new IllegalArgumentException("Missing deletion permit");
        String operation=permit.operation,event=permit.selected.id;
        if(!permit.begin(SystemClock.elapsedRealtime())) return new AlarmReceipt(operation,event,"REJECTED",0);
        synchronized(COORDINATOR) {
            try {
                if(!reserveAction(operation,event,"DELETE")) return new AlarmReceipt(operation,event,"UNKNOWN",0);
                if(SystemClock.elapsedRealtime()>=permit.before) return confirmAction(operation,event,"DELETE","REJECTED",0);
                AgendaCancellation.Result result=AgendaCancellation.remove(permit.selected,this,effects(context));
                String status=result==AgendaCancellation.Result.REMOVED ? "REMOVED" : result==AgendaCancellation.Result.STALE || result==AgendaCancellation.Result.NOT_FOUND ? "STALE" : "UNKNOWN";
                return confirmAction(operation,event,"DELETE",status,0);
            } catch(Exception failure) { return new AlarmReceipt(operation,event,"UNKNOWN",0); }
        }
    }
    AlarmReceipt applyOccurrence(AlarmOccurrencePolicy.Permit permit) {
        if(permit==null) throw new IllegalArgumentException("Missing occurrence permit");
        String operation=permit.operation,event=permit.selected.id,action=permit.action.name();
        synchronized(COORDINATOR) {
            AlarmOccurrenceTransaction.Result result=AlarmOccurrenceTransaction.apply(permit,new AlarmOccurrenceTransaction.Clock() {
                public long boot() { return SystemClock.elapsedRealtime(); }
                public long wall() { return System.currentTimeMillis(); }
            },new AlarmOccurrenceTransaction.Store() {
                public boolean reserve() { return reserveAction(operation,event,action); }
                public AgendaEntry current() { return find(event); }
                public boolean move(AgendaEntry selected,long due,long elapsed) {
                    ContentValues values=new ContentValues(); values.put("due_ms",due); values.put("elapsed_due",elapsed); values.put("state","SCHEDULED"); values.put("revision",selected.revision+1);
                    return helper.getWritableDatabase().update("agenda",values,"id=? AND revision=? AND state='DELIVERING'",new String[]{event,Long.toString(selected.revision)})==1;
                }
                public boolean finish(AgendaEntry selected) { return AgendaStore.this.finish(selected,true); }
                public void schedule() { AgendaStore.this.schedule(find(event)); }
                public void failed() { changeState(find(event),AgendaEntry.State.FAILED); }
                public boolean receipt(String status,long due) {
                    AlarmReceipt value=confirmAction(operation,event,action,status,due); return status.equals(value.status) && due==value.due;
                }
            });
            return new AlarmReceipt(operation,event,result.status,result.due);
        }
    }
    static void restore(Context context) {
        synchronized (COORDINATOR) {
            try (AgendaStore store = new AgendaStore(context)) {
                for (AgendaEntry.Kind kind : AgendaEntry.Kind.values()) for (AgendaEntry entry : store.list(kind).entries) {
                    if(entry.state!=AgendaEntry.State.CANCEL_PENDING && store.unsettled(entry.id)) {
                        store.changeState(entry,AgendaEntry.State.PENDING); continue; // Do not retry an uncertain mutation on boot.
                    }
                    if(entry.weekday!=0 && store.created(entry.id) && (entry.state==AgendaEntry.State.DELIVERING
                            || entry.state==AgendaEntry.State.SCHEDULED && entry.due<=System.currentTimeMillis())) {
                        store.advanceWeekly(entry); continue; // No overdue audio burst, do not replay a snoozed occurrence after reboot.
                    }
                    if (entry.state == AgendaEntry.State.DELIVERING || entry.state == AgendaEntry.State.SCHEDULED && (entry.due <= System.currentTimeMillis() || !store.created(entry.id))) {
                        store.changeState(entry, AgendaEntry.State.PENDING);
                    } else if (entry.state == AgendaEntry.State.SCHEDULED) {
                        ContentValues values = new ContentValues(); values.put("elapsed_due", 0); // Boot changes monotonic epoch; recover from persisted wall due.
                        store.helper.getWritableDatabase().update("agenda", values, "id=? AND revision=?", new String[]{entry.id, Long.toString(entry.revision)});
                        try { store.schedule(store.find(entry.id)); } catch (Exception failure) { store.changeState(store.find(entry.id), AgendaEntry.State.FAILED); }
                    }
                }
            } catch (Exception ignored) { /* No content/error logs and no success announcement. */ }
        }
    }
    private boolean unsettled(String id) {
        try(Cursor row=helper.getReadableDatabase().rawQuery("SELECT id FROM agenda_actions WHERE event_id=? AND status IN ('PREPARING','UNKNOWN') LIMIT 1",new String[]{id})) {
            return row.moveToFirst();
        }
    }
    static Intent deliveryIntent(Context context, String id) {
        if (!AgendaEntry.validId(id)) throw new IllegalArgumentException("Invalid agenda ID");
        return new Intent().setClassName(context, context.getPackageName() + ".AgendaReceiver")
            .setAction("local.doja.voice.AGENDA." + id).putExtra("id", id);
    }
    static AgendaCancellation.Effects effects(Context context) {
        return new AgendaCancellation.Effects() {
            public boolean stopOutput(String id) { return AgendaOutputs.stop(id); }
            public void cancelSchedule(String id) {
                PendingIntent operation = PendingIntent.getBroadcast(context, 0, deliveryIntent(context, id),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (operation != null) {
                    context.getSystemService(AlarmManager.class).cancel(operation); operation.cancel();
                }
            }
        };
    }
    public void close() { helper.close(); }
}
