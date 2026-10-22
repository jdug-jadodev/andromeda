package local.doja.voice;

import java.util.*;
import java.util.regex.*;

/** RAM-only selection/clarification. Agenda contents never leave Android. No IO. */
final class ReminderDeletion {
    static final String POLICY="confirmed_reminder_delete_v1";
    static final String QUESTION="Voy a preguntarte qué recordatorio quieres eliminar.";
    static final String TARGET="¿Qué recordatorio quieres eliminar? Dime de qué trata.";
    static final String CANCELLED="No he eliminado ningún recordatorio.";
    static final String NOT_FOUND="No encontré un recordatorio con ese contenido. No he eliminado ninguno.";
    static final String UNAVAILABLE="No pude comprobar los recordatorios. No he eliminado ninguno.";
    static final String UNKNOWN="No pude confirmar la eliminación. Revisa Recordatorios antes de pedirla otra vez.";
    static final long MAX_AGE_MS=300000;
    static final int MAX_SPOKEN=1800;
    private static final Set<String> YES=new HashSet<>(Arrays.asList("si","si por favor","si gracias","si correcto",
        "si elimina ese recordatorio","elimina ese recordatorio","confirmo","confirmo eliminar ese recordatorio",
        "correcto","de acuerdo","adelante","hazlo","si hazlo","si adelante"));
    static boolean requested(String human) {
        String text=TimerRequestGuard.normalize(AgendaRequestGuard.clean(human));
        return Pattern.compile("\\brecordatorio\\b").matcher(text).find()
            && Pattern.compile("\\b(?:elimina|eliminar|elimines|borra|borrar|borres|quita|quitar|quites|cancela|cancelar)\\b").matcher(text).find()
            && !Pattern.compile("[`;]|\\b(?:no|nunca|jamas|ni|si|ayer|dije|dijiste|dijo|ejemplo|imagina|supongamos|"
                +"todos|todas|recordatorios|alarma|alarmas|temporizador|volumen|busca|internet|web|enciende|apaga)\\b").matcher(text).find();
    }
    static String plain(String text) { return TimerRequestGuard.normalize(AgendaRequestGuard.clean(text)).replaceAll("\\s*,\\s*"," "); }
    static boolean affirmative(String human) { return YES.contains(plain(human)); }
    static boolean negative(String text) {
        return plain(text).matches("no|no gracias|no quiero|cancela|cancelar|cancela el borrado|cancela la eliminacion|dejalo|olvidalo");
    }
    static String resultText(String status) {
        switch(status) {
            case "REMOVED":return "Recordatorio eliminado y su aviso cancelado.";
            case "REJECTED":return CANCELLED;
            case "STALE":return "El recordatorio cambió o ya no está. No he confirmado su eliminación.";
            case "UNKNOWN":return UNKNOWN;
            default:throw new IllegalArgumentException("Invalid reminder deletion receipt");
        }
    }
    static final class Step {
        final String status,stage,text; final AgendaEntry selected;
        Step(String status,String stage,String text,AgendaEntry selected) {
            if(text==null || text.isEmpty() || text.length()>MAX_SPOKEN) throw new IllegalArgumentException("Invalid local reminder speech");
            this.status=status;this.stage=stage;this.text=text;this.selected=selected;
        }
    }
    static final class Permit {
        final String operation;final AgendaEntry selected;final long before;
        private boolean used,revoked;
        private Permit(String operation,AgendaEntry selected,long before) { this.operation=operation;this.selected=selected;this.before=before; }
        synchronized boolean begin(long now) { if(used || revoked || now>=before) return false;used=true;return true; }
        synchronized void revoke() { if(!used) revoked=true; }
    }
    private String origin="",questionTurn="",stage="";
    private long created,lastSequence,heardAt,questionSpeechEnd;
    private int answers;
    private boolean armed;
    private AgendaEntry selected;
    private List<AgendaEntry> choices=Collections.emptyList();
    boolean active() { return !origin.isEmpty(); }
    String origin() { return origin; }
    void clear() { origin=questionTurn=stage="";created=lastSequence=heardAt=questionSpeechEnd=0;answers=0;armed=false;selected=null;choices=Collections.emptyList(); }
    private void fresh(AgendaDialogue.Human h,long now) {
        if(!armed || h.sequence<=lastSequence || h.id.equals(questionTurn) || h.speechEnd<heardAt || now-created>=MAX_AGE_MS) {
            clear();throw new IllegalArgumentException("Unheard/stale reminder deletion question");
        }
    }
    private Step question(AgendaDialogue.Human h) {
        questionTurn=h.id;questionSpeechEnd=h.speechEnd;lastSequence=h.sequence;armed=false;
        String text;
        if(stage.equals("TARGET")) text=TARGET;
        else if(stage.equals("CONFIRM")) text="¿Confirmas eliminar este recordatorio: "+selected.title()+". "+selected.when()+"?";
        else {
            StringBuilder body=new StringBuilder("Hay varios recordatorios con ese contenido. Elige el número de uno de estos: ");
            for(int i=0;i<choices.size();i++) body.append(i+1).append(". ").append(choices.get(i).title()).append(". ").append(choices.get(i).when()).append(". ");
            body.append("Si no es ninguno, cancela el borrado y revisa Recordatorios.");text=body.toString();
        }
        return new Step("QUESTION",stage,text,stage.equals("CONFIRM")?selected:null);
    }
    private Step terminal(String status,String text) { clear();return new Step(status,"",text,null); }
    static String content(String human) {
        String value=AgendaRequestGuard.clean(human).replaceFirst("(?iu)^(?:(?:el|ese) recordatorio (?:de|sobre|que dice) |el de )","");
        if(value.isEmpty() || value.length()>160 || affirmative(value) || negative(value)
                || Pattern.compile("[`;]|\\b(?:elimina|eliminar|borra|borrar|cancela|cancelar|busca|internet|web|volumen|alarma|alarmas|todos|todas)\\b").matcher(plain(value)).find()
                || plain(value).matches("ese|eso|el anterior|el de antes|lo mismo")) return null;
        return value;
    }
    static String openingContent(String human) {
        Matcher match=Pattern.compile("(?iu)\\brecordatorio\\b(.*)$").matcher(AgendaRequestGuard.clean(human));
        if(!match.find()) return null;
        String tail=match.group(1).trim().replaceFirst("(?iu)^(?:de|del|sobre|para|que dice)\\s+","").replaceFirst("(?iu)\\s+por favor$","");
        return tail.isEmpty()?null:content(tail);
    }
    static int choice(String human) {
        String value=plain(human).replaceFirst("^(?:el numero |numero |el |la )","");
        if(value.matches("1|uno|primero|primera"))return 0;
        if(value.matches("2|dos|segundo|segunda"))return 1;
        if(value.matches("3|tres|tercero|tercera"))return 2;
        return -1;
    }
    Step step(AgendaDialogue.Human h,List<AgendaEntry> entries,long total,long now) {
        String target=null;
        if(!active()) {
            if(!requested(h.text)) throw new IllegalArgumentException("Explicit reminder deletion required");
            origin=h.id;created=now;lastSequence=h.sequence;stage="TARGET";target=openingContent(h.text);
        } else {
            fresh(h,now);answers++;
            if(answers>8 || negative(h.text))return terminal("CANCELLED",CANCELLED);
            if(stage.equals("CONFIRM"))return question(h); // A read-only step never authorizes a deletion, even for yes.
            if(stage.equals("CHOOSE")) {
                int index=choice(h.text);
                if(index<0 || index>=choices.size())return question(h);
                selected=choices.get(index);choices=Collections.emptyList();stage="CONFIRM";return question(h);
            }
            target=content(h.text);
        }
        if(target==null)return question(h);
        if(entries==null || total!=entries.size() || total>50)return terminal("UNAVAILABLE",UNAVAILABLE);
        List<AgendaEntry> matches=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(AgendaEntry entry:entries) {
            if(entry==null || entry.kind!=AgendaEntry.Kind.REMINDER || !seen.add(entry.id))return terminal("UNAVAILABLE",UNAVAILABLE);
            if(entry.state!=AgendaEntry.State.CANCEL_PENDING && TimerRequestGuard.normalize(entry.text).equals(plain(target)))matches.add(entry);
        }
        if(matches.isEmpty())return terminal("NOT_FOUND",NOT_FOUND);
        if(matches.size()==1) { selected=matches.get(0);stage="CONFIRM"; }
        else { choices=Collections.unmodifiableList(new ArrayList<>(matches.subList(0,Math.min(3,matches.size()))));stage="CHOOSE"; }
        return question(h);
    }
    void spoken(String turn,boolean completed,long now) {
        if(!active() || !questionTurn.equals(turn))return;
        if(completed && now>=questionSpeechEnd) { armed=true;heardAt=now; } else clear();
    }
    Permit claim(String operation,String origin,String event,long revision,long due,AgendaDialogue.Human h,long before,long sessionEnd,long now) {
        fresh(h,now);
        if(!stage.equals("CONFIRM") || selected==null || !this.origin.equals(origin) || !affirmative(h.text)
                || answers>=8 || !AgendaEntry.validId(operation) || !selected.id.equals(event) || selected.revision!=revision || selected.due!=due
                || before<=now || before-now>4100 || before>h.speechEnd+26500 || before>sessionEnd-5000)
            throw new IllegalArgumentException("Unrelated reminder deletion grant");
        Permit permit=new Permit(operation,selected,before);clear();return permit;
    }
}
