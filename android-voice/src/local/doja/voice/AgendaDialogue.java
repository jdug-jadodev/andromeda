package local.doja.voice;

import java.util.regex.*;

/** Typed RAM-only form, reconstructed from authenticated human turns and TTS onDone. */
final class AgendaDialogue {
    static final String SUBJECT = "¿Para qué es el recordatorio?";
    static final String WHEN = "¿Cuándo quieres que te lo recuerde?";
    static final String ALARM_WHEN = "¿Para qué día y a qué hora quieres la alarma?";
    static final String INVALID_TIME = "Necesito un momento inequívoco y futuro. Puedes decir una fecha y hora, o en cuántos minutos.";
    static final String OTHER_REQUEST = "Hay un aviso pendiente de completar. Di «cancela el aviso» para cambiar de petición.";
    static final String CANCELLED = "He descartado el aviso pendiente. No he guardado ningún evento.";
    static final long MAX_AGE_MS = 300000;
    static final class Human {
        final String id, text;
        final long sequence, speechEnd, wall, boot;
        Human(String id, long sequence, String text, long speechEnd, long wall, long boot, long now) {
            if (id == null || !id.matches("[0-9a-f]{32}") || sequence < 1 || sequence > 24
                    || speechEnd <= 0 || speechEnd > now + 100 || now - speechEnd >= 60000
                    || wall <= 0 || boot <= 0 || boot > now + 100 || now - boot > 1500)
                throw new IllegalArgumentException("Invalid agenda human turn");
            AgendaRequestGuard.clean(text);
            this.id = id; this.sequence = sequence; this.text = text; this.speechEnd = speechEnd; this.wall = wall; this.boot = boot;
        }
    }
    static final class Decision {
        final String kind, text, when, origin, turn;
        final long sequence;
        final AgendaEntry.Kind eventKind;
        final AgendaRequestGuard.Value value;
        Decision(String kind, String text, String when, String origin, Human human, AgendaEntry.Kind eventKind, AgendaRequestGuard.Value value) {
            this.kind = kind; this.text = text; this.when = when; this.origin = origin; this.turn = human.id;
            this.sequence = human.sequence; this.eventKind = eventKind; this.value = value;
        }
    }
    private AgendaEntry.Kind kind;
    private String body = "", origin = "", questionTurn = "";
    private long created, lastSequence;
    private boolean armed;
    private Decision prepared;
    boolean active() { return kind != null; }
    void clear() { kind = null; body = origin = questionTurn = ""; created = lastSequence = 0; armed = false; prepared = null; }
    static String subject(String text) {
        String value = AgendaRequestGuard.clean(text), plain = TimerRequestGuard.normalize(value);
        if (value.isEmpty() || value.length() > 160 || Pattern.compile("[;.!?]|\\b(?:no|hoy|manana|pasado manana)\\b|\\ben .+ (?:segundos?|minutos?|horas?)\\b").matcher(plain).find()
                || Pattern.compile("\\b(?:pon|ponme|crea|programa|inicia|recuerdame|busca|consulta|investiga|cancela|elimina|enciende|apaga|volumen)\\b").matcher(plain).find()
                || plain.matches("si|no|hazlo|eso|lo mismo")) throw new IllegalArgumentException("Invalid agenda subject");
        return value;
    }
    static String opening(AgendaEntry.Kind kind, String human) {
        String raw = AgendaRequestGuard.clean(human);
        String expression = kind == AgendaEntry.Kind.REMINDER
            ? "(?:(?:pon|ponme|crea|programa) (?:un |el )?recordatorio|recu[eé]rdame)(?: (.+))?"
            : "(?:(?:pon|ponme|crea|programa) (?:una |la )?alarma|alarma)(?: (.+))?";
        if (kind == null) throw new IllegalArgumentException("Invalid agenda kind");
        Matcher match = Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(raw);
        if (!match.matches()) throw new IllegalArgumentException("Explicit agenda opening required");
        String tail = match.group(1);
        if (tail == null) return "";
        if (kind == AgendaEntry.Kind.ALARM) throw new IllegalArgumentException("Incomplete/ambiguous alarm time");
        // Reject time-shaped tails instead of treating date/clock as reminder content.
        if (Pattern.compile("(?iu) (?:en |hoy |ma[ñn]ana |pasado ma[ñn]ana |el [0-9]|[0-9]{4}-|[0-9]{2}/)").matcher(tail).find()
                || Pattern.compile("(?iu).+? (?:que|para) .+").matcher(tail).matches())
            throw new IllegalArgumentException("Incomplete/ambiguous reminder time");
        return subject(tail.replaceFirst("(?iu)^para ", ""));
    }
    private Decision question(Human human, String text) {
        questionTurn = human.id; armed = false; prepared = null;
        return new Decision("question", text != null ? text : kind == AgendaEntry.Kind.ALARM ? ALARM_WHEN : body.isEmpty() ? SUBJECT : WHEN,
            "", origin, human, kind, null);
    }
    Decision begin(AgendaEntry.Kind kind, Human human) {
        if (active()) throw new IllegalArgumentException("Agenda form already active");
        String content = opening(kind, human.text);
        this.kind = kind; body = content; origin = human.id; created = human.boot; lastSequence = human.sequence;
        return question(human, null);
    }
    void spoken(String turn, boolean completed) {
        if (!questionTurn.equals(turn)) return;
        if (completed) armed = true; else clear();
    }
    Decision answer(Human human, long now) {
        if (!active()) return null;
        if (!armed || human.sequence <= lastSequence || now - created >= MAX_AGE_MS) {
            clear(); throw new IllegalArgumentException("Agenda question not heard/stale");
        }
        lastSequence = human.sequence; armed = false; prepared = null;
        String raw = AgendaRequestGuard.clean(human.text), plain = TimerRequestGuard.normalize(raw);
        if (plain.matches("cancela|cancelar|cancela el aviso|cancela el recordatorio|cancela la alarma|no|dejalo|olvidalo")) {
            clear(); return new Decision("cancelled", CANCELLED, "", "", human, null, null);
        }
        if (Pattern.compile("\\b(?:busca|consulta|investiga|pon|ponme|crea|programa|recuerdame|enciende|apaga|elimina|volumen)\\b").matcher(plain).find())
            return question(human, OTHER_REQUEST);
        if (kind == AgendaEntry.Kind.REMINDER && body.isEmpty()) {
            try { body = subject(human.text); } catch (IllegalArgumentException error) { return question(human, SUBJECT); }
            return question(human, null);
        }
        long[] due;
        try { due = AgendaRequestGuard.due(raw, human.wall); }
        catch (RuntimeException error) { return question(human, INVALID_TIME); }
        prepared = new Decision("proposal", body, raw, origin, human, kind, new AgendaRequestGuard.Value(kind, body, due[0], due[1]));
        return prepared;
    }
    AgendaRequestGuard.Value claim(AgendaEntry.Kind kind, String origin, String turn, long sequence, String when, String text,
            long due, long delay, long now) {
        Decision value = prepared;
        if (value == null || now - created >= MAX_AGE_MS || value.eventKind != kind || !value.origin.equals(origin)
                || !value.turn.equals(turn) || value.sequence != sequence || !value.when.equals(when) || !value.text.equals(text)
                || value.value.due != due || value.value.delay != delay)
            throw new IllegalArgumentException("Unrelated/stale guided agenda proposal");
        AgendaRequestGuard.Value result = value.value; clear(); return result;
    }
}
