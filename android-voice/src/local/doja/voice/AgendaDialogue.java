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
    static final String REMINDER_POLICY = "confirmed_reminder_intent_v1";
    static final String REMINDER_INTENT = "¿Quieres crear un recordatorio?";
    static final java.util.Set<String> REMINDER_YES = new java.util.HashSet<>(java.util.Arrays.asList(
        "si","si por favor","si gracias","si quiero","si quiero hacerlo","si quiero un recordatorio",
        "si quiero crear un recordatorio","quiero un recordatorio","quiero crear un recordatorio",
        "correcto","de acuerdo","claro","claro que si","vale","confirmo"));
    static final long MAX_AGE_MS = 300000;
    static final class Human {
        final String id, text;
        final long sequence, speechEnd, wall, boot;
        Human(String id, long sequence, String text, long speechEnd, long wall, long boot, long now) {
            this(id,sequence,text,speechEnd,wall,boot,now,24);
        }
        Human(String id, long sequence, String text, long speechEnd, long wall, long boot, long now, long maximumSequence) {
            if (id == null || !id.matches("[0-9a-f]{32}") || sequence < 1 || sequence > maximumSequence
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
    private java.time.LocalDate day; // Frozen literal date, never an assumed hour or history-derived payload.
    private final long maximumSequence;
    private final boolean lifecycle;
    private final boolean reminderClarification;
    private boolean needsIntent;
    private long heardAt,questionSpeechEnd;
    private int answers;
    AgendaDialogue() { this(24); }
    AgendaDialogue(long maximumSequence) {
        this(maximumSequence,false);
    }
    AgendaDialogue(long maximumSequence,boolean lifecycle) {
        this(maximumSequence,lifecycle,false);
    }
    AgendaDialogue(long maximumSequence,boolean lifecycle,boolean reminderClarification) {
        if (maximumSequence != 24 && maximumSequence != 64) throw new IllegalArgumentException("Invalid agenda epoch size");
        this.maximumSequence = maximumSequence;
        this.lifecycle=lifecycle;
        this.reminderClarification=reminderClarification;
    }
    boolean active() { return kind != null; }
    void clear() { kind = null; body = origin = questionTurn = ""; created = lastSequence = 0; answers = 0; armed = false; prepared = null; day=null; needsIntent=false; heardAt=questionSpeechEnd=0; }
    static String subject(String text) {
        String value = AgendaRequestGuard.clean(text), plain = TimerRequestGuard.normalize(value);
        if (value.isEmpty() || value.length() > 160 || Pattern.compile("[;.!?]|\\b(?:no|hoy|manana|pasado manana)\\b|\\b(?:en|dentro de) .+ (?:segundos?|minutos?|horas?|dias?)\\b").matcher(plain).find()
                || Pattern.compile("\\b(?:pon|ponme|crea|programa|inicia|recuerdame|busca|consulta|investiga|cancela|elimina|enciende|apaga|volumen)\\b").matcher(plain).find()
                || plain.matches("si|no|hazlo|eso|lo mismo")) throw new IllegalArgumentException("Invalid agenda subject");
        return value;
    }
    private static String literalOpening(AgendaEntry.Kind kind, String human) {
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
        if (!AgendaRequestGuard.parts(kind,raw).isEmpty())
            throw new IllegalArgumentException("Incomplete/ambiguous reminder time");
        return subject(tail.replaceFirst("(?iu)^para ", ""));
    }
    static boolean mayClarifyReminder(String human) {
        String plain=TimerRequestGuard.normalize(AgendaRequestGuard.clean(human));
        return !Pattern.compile("[`;]|\\b(?:no|nunca|jamas|ni|si|ayer|dije|dijiste|dijo|dijeron|ejemplo|imagina|supongamos|"
            +"busca|buscar|consulta|internet|web|google|whatsapp|alarma|alarmas|temporizador|volumen|"
            +"cancela|cancelar|borra|elimina|apaga|enciende|lista|listar|listame|muestra|muestrame|lee|leeme|leer)\\b").matcher(plain).find()
            && !Pattern.compile("^(?:que|cuales|cuantos)\\b").matcher(plain).find()
            && Pattern.compile("\\b(?:recordatorios?|recordar(?:me|lo)?|recuerdame|recuerdes|avisame|avisarme|avises)\\b").matcher(plain).find();
    }
    static String opening(AgendaEntry.Kind kind,String human) { return opening(kind,human,false); }
    static String opening(AgendaEntry.Kind kind,String human,boolean clarification) {
        try { return literalOpening(kind,human); }
        catch(IllegalArgumentException invalid) {
            if(clarification && kind==AgendaEntry.Kind.REMINDER && mayClarifyReminder(human)) return null;
            throw invalid;
        }
    }
    private Decision question(Human human, String text) {
        questionTurn = human.id; armed = false; prepared = null;
        heardAt=0; questionSpeechEnd=human.speechEnd;
        return new Decision("question", text != null ? text : needsIntent ? REMINDER_INTENT : kind == AgendaEntry.Kind.ALARM ? ALARM_WHEN : day!=null ? hourQuestion(day) : body.isEmpty() ? SUBJECT : WHEN,
            "", origin, human, kind, null);
    }
    static String hourQuestion(java.time.LocalDate day) {
        return "¿A qué hora quieres el recordatorio para el " + day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu",java.util.Locale.ROOT))
            + "? Indica mañana o tarde, o usa formato de 24 horas.";
    }
    Decision begin(AgendaEntry.Kind kind, Human human) {
        if (active() || human.sequence > maximumSequence) throw new IllegalArgumentException("Agenda form already active/invalid sequence");
        // Independently recognize only a date-only partial request, with the WHOLE request guard.
        String content = null; java.time.LocalDate partialDay = null; int matches = 0;
        if (kind == AgendaEntry.Kind.REMINDER) {
            for (String[] candidate : AgendaRequestGuard.parts(kind,AgendaRequestGuard.clean(human.text))) {
                try {
                    String literalBody = subject(candidate[1]);java.time.LocalDate literalDay = AgendaRequestGuard.day(candidate[0],human.wall);
                    content=literalBody;partialDay=literalDay;matches++;
                } catch (RuntimeException invalid) { /* A malformed/ambiguous date grants nothing. */ }
            }
        }
        if (matches!=1) { content=opening(kind,human.text,reminderClarification);partialDay=null; }
        this.kind = kind; body = content==null ? "" : content; needsIntent=content==null; origin = human.id; created = human.boot; lastSequence = human.sequence;
        day=partialDay;
        return question(human, null);
    }
    void spoken(String turn, boolean completed) {
        spoken(turn,completed,0);
    }
    void spoken(String turn,boolean completed,long now) {
        if (!questionTurn.equals(turn)) return;
        if (completed && (!reminderClarification || kind!=AgendaEntry.Kind.REMINDER || now>=questionSpeechEnd)) { armed=true; heardAt=now; }
        else clear();
    }
    Decision answer(Human human, long now) {
        if (!active()) return null;
        if (!armed || human.sequence <= lastSequence || human.sequence > maximumSequence || now - created >= MAX_AGE_MS
                || reminderClarification && kind==AgendaEntry.Kind.REMINDER && (human.id.equals(questionTurn) || human.speechEnd<heardAt)) {
            clear(); throw new IllegalArgumentException("Agenda question not heard/stale");
        }
        lastSequence = human.sequence; armed = false; prepared = null;
        answers++;
        if (answers > 8) { clear(); return new Decision("cancelled", CANCELLED, "", "", human, null, null); }
        String raw = AgendaRequestGuard.clean(human.text), plain = TimerRequestGuard.normalize(raw);
        if (plain.matches("cancela|cancelar|cancela el aviso|cancela el recordatorio|cancela la alarma|no|dejalo|olvidalo")) {
            clear(); return new Decision("cancelled", CANCELLED, "", "", human, null, null);
        }
        if(needsIntent) {
            String confirmation=plain.replaceAll("\\s*,\\s*"," ").replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$","");
            if(confirmation.matches("no gracias|no quiero|no quiero un recordatorio")) { clear(); return new Decision("cancelled",CANCELLED,"","",human,null,null); }
            if(!REMINDER_YES.contains(confirmation)) return question(human,null);
            needsIntent=false;origin=human.id;return question(human,null);
        }
        if (Pattern.compile("\\b(?:busca|consulta|investiga|pon|ponme|crea|programa|recuerdame|enciende|apaga|elimina|volumen)\\b").matcher(plain).find())
            return question(human, OTHER_REQUEST);
        if (kind == AgendaEntry.Kind.REMINDER && body.isEmpty()) {
            try { body = subject(human.text); } catch (IllegalArgumentException error) { return question(human, SUBJECT); }
            return question(human, null);
        }
        AgendaRequestGuard.Value value;
        try {
            if(lifecycle) value=AlarmSchedule.parse(kind,raw,body,human.wall);
            else { long[] due=AgendaRequestGuard.due(raw,human.wall); value=new AgendaRequestGuard.Value(kind,body,due[0],due[1]); }
        }
        catch (RuntimeException error) {
            if(kind!=AgendaEntry.Kind.REMINDER) return question(human,INVALID_TIME);
            java.time.LocalDate supplied=null;
            try { supplied=AgendaRequestGuard.day(raw,human.wall); } catch(RuntimeException invalid) { /* May be a clock answer. */ }
            if(supplied!=null) { day=supplied;return question(human,null); }
            if(day==null) return question(human,INVALID_TIME);
            try { long[] due=AgendaRequestGuard.dayDue(day,raw,human.wall);value=new AgendaRequestGuard.Value(kind,body,due[0],due[1]); }
            catch(AgendaRequestGuard.AmbiguousTime ambiguous) { return question(human,null); }
            catch(RuntimeException invalid) { day=null;return question(human,INVALID_TIME); }
        }
        prepared = new Decision("proposal", body, raw, origin, human, kind, value);
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
