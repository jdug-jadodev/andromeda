package local.doja.voice;

import java.text.SimpleDateFormat;
import java.util.*;

/** Durable event/weekly alarm definition, never a request transcript or conversation. */
final class AgendaEntry {
    enum Kind { ALARM, REMINDER }
    enum State { SCHEDULED, PENDING, DELIVERING, FAILED, CANCEL_PENDING }
    static final String ZONE = "America/Bogota";
    static final int MAX_TEXT = 160, PAGE_SIZE = 50;
    final String id, text, zone;
    final Kind kind;
    final State state;
    final long due, revision, elapsedDue;
    final int weekday, minute;
    AgendaEntry(String id, Kind kind, String text, long due, String zone, State state, long revision) {
        this(id, kind, text, due, zone, state, revision, 0);
    }
    AgendaEntry(String id, Kind kind, String text, long due, String zone, State state, long revision, long elapsedDue) {
        this(id,kind,text,due,zone,state,revision,elapsedDue,0,-1);
    }
    AgendaEntry(String id, Kind kind, String text, long due, String zone, State state, long revision, long elapsedDue,int weekday,int minute) {
        if(weekday!=0) { AlarmSchedule.validate(weekday,minute); if(kind!=Kind.ALARM) throw new IllegalArgumentException("Only alarms repeat"); }
        else if(minute>=0) AlarmSchedule.validate(0,minute); // Daily recurrence: alarms and reminders.
        else if(minute!=-1) throw new IllegalArgumentException("Invalid one-shot schedule");
        if (!validId(id) || kind == null || state == null || text == null || due <= 0 || !ZONE.equals(zone)
                || revision < 1 || revision == Long.MAX_VALUE || elapsedDue < 0 || text.length() > MAX_TEXT || text.indexOf('\u0000') >= 0
                || text.contains("<|") || kind == Kind.ALARM && !text.isEmpty()
                || kind == Kind.REMINDER && state != State.CANCEL_PENDING && text.trim().isEmpty()
                || state == State.CANCEL_PENDING && !text.isEmpty()) throw new IllegalArgumentException("Invalid agenda entry");
        this.id = id; this.kind = kind; this.text = text; this.due = due; this.zone = zone; this.state = state; this.revision = revision; this.elapsedDue = elapsedDue;
        this.weekday=weekday; this.minute=minute;
    }
    static boolean validId(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    boolean recurs() { return weekday != 0 || minute >= 0; }
    String title() { return kind == Kind.ALARM ? "Alarma" : text.isEmpty() ? "Recordatorio sin contenido · eliminación pendiente" : text; }
    String when() {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("es", "CO"));
        format.setTimeZone(TimeZone.getTimeZone(zone));
        return format.format(new Date(due)) + " · Bogotá" + (recurs() ? " · " + AlarmSchedule.label(weekday, minute) : "");
    }
    String status(long now) {
        switch (state) {
            case SCHEDULED: return due <= now ? "Hora vencida · entrega no confirmada" : "Programado";
            case DELIVERING: return "Entregándose · sin finalización confirmada";
            case FAILED: return "Entrega fallida · conservado";
            case CANCEL_PENDING: return "Bloqueado · eliminación pendiente";
            default: return "Pendiente de entrega";
        }
    }
    String label(long now) { return title() + "\n" + when() + " · " + status(now) + " · " + id.substring(0, 8); }
}
