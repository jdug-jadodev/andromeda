package local.doja.voice;

import java.util.*;
import java.util.regex.*;

/** RAM-only volume proposal, independently rebuilt from human turns and heard TTS. */
final class VolumeDialogue {
    static final String ASK="¿A qué porcentaje, de cero a cien, quieres dejar el volumen multimedia del teléfono?";
    static final String CANCELLED="He descartado el ajuste pendiente. No he cambiado el volumen.";
    static final long MAX_AGE_MS=300000;
    private static final Set<String> YES=new HashSet<>(Arrays.asList("si","si por favor","si gracias","si hazlo","si adelante","si correcto","correcto","exacto","adelante","hazlo","de acuerdo","confirmo","dale"));
    private static final Set<String> NO=new HashSet<>(Arrays.asList("no","no gracias","cancela","cancelar","cancela el ajuste","cancela el volumen","dejalo","olvidalo"));
    private static final String NAMES="cero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintiun|veintidos|veintitres|veinticuatro|veinticinco|veintiseis|veintiocho|veintinueve|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien|ciento";
    private static final Pattern NUMERIC;
    static {
        List<String> names=new ArrayList<>(Arrays.asList(NAMES.split("\\|")));
        names.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        NUMERIC=Pattern.compile("\\b(?:[0-9]+|(?:"+String.join("|",names)+")(?: y (?:uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve))?)\\b");
    }
    static String plain(String text) {
        if(text==null || text.isEmpty() || text.length()>250 || Pattern.compile("[\\x00-\\x1f\\x7f\"'«»<>]").matcher(text).find())
            throw new IllegalArgumentException("Invalid volume text");
        return TimerRequestGuard.normalize(text).replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$","");
    }
    static Integer candidate(String text) {
        String value=plain(text);
        if(Pattern.compile("\\b(?:no|nunca|jamas|ni|sin|si|ayer|dije|ejemplo|imagina|supongamos|manana|cuando|tv|televisor|television|alarma|alarmas|llamada|llamadas|timbre|notificaciones|busca|internet|recordatorio|temporizador|enciende|apaga|elimina|borra)\\b").matcher(value).find()
                || Pattern.compile("[0-9][.,][0-9]|-\\s*[0-9]").matcher(value).find()
                || value.codePoints().anyMatch(c->Character.isDigit(c) && (c<'0' || c>'9')))
            throw new IllegalArgumentException("Volume scope not authorized");
        value=value.replaceAll("\\bun(?=\\s*(?:por (?:ciento|cien)\\b|porciento\\b|%))","uno");
        if(value.equals("un") || value.equals("una")) value="uno";
        value=value.replaceAll("\\bpor (?:ciento|cien)\\b|\\bporciento\\b|%"," ");
        List<String> found=new ArrayList<>(); Matcher numbers=NUMERIC.matcher(value);
        while(numbers.find()) found.add(numbers.group());
        Matcher half=Pattern.compile("\\bmitad\\b").matcher(value); int halves=0; while(half.find()) halves++;
        if(halves>0) return halves==1 && found.isEmpty() && !Pattern.compile("\\bactual\\b").matcher(value).find()?50:null;
        if(found.size()!=1) return null;
        try {
            int percent=found.get(0).equals("cien") || found.get(0).equals("ciento")?100:TimerRequestGuard.amount(found.get(0));
            return percent>=0 && percent<=100?percent:null;
        } catch(IllegalArgumentException error) { return null; }
    }
    static String confirm(int percent) { return "¿Quieres que deje el volumen multimedia del teléfono al "+percent+" por ciento?"; }
    static final class Human {
        final String id,text; final long sequence,speechEnd;
        Human(String id,long sequence,String text,long speechEnd,long now,long maximumSequence) {
            if(id==null || !id.matches("[0-9a-f]{32}") || sequence<1 || sequence>maximumSequence
                    || speechEnd<=0 || speechEnd>now+100 || now-speechEnd>=60000 || text==null || text.isEmpty() || text.length()>250)
                throw new IllegalArgumentException("Invalid volume human turn");
            this.id=id; this.sequence=sequence; this.text=text; this.speechEnd=speechEnd;
        }
    }
    static final class Decision {
        final String kind,text,origin,question,turn; final long sequence; final int percent;
        Decision(String kind,String text,String origin,String question,Human human,int percent) {
            this.kind=kind; this.text=text; this.origin=origin; this.question=question; turn=human.id; sequence=human.sequence; this.percent=percent;
        }
    }
    private final long maximumSequence;
    private boolean active,armed;
    private String origin="",questionTurn="";
    private long created,lastSequence,heardAt;
    private int answers,percent=-1;
    private Decision prepared;
    VolumeDialogue(long maximumSequence) {
        if(maximumSequence!=24 && maximumSequence!=64) throw new IllegalArgumentException("Invalid volume epoch");
        this.maximumSequence=maximumSequence;
    }
    boolean active() { return active; }
    void clear() { active=armed=false; origin=questionTurn=""; created=lastSequence=heardAt=0; answers=0; percent=-1; prepared=null; }
    private Decision cancelled(Human human) { clear(); return new Decision("volume_cancelled",CANCELLED,"","",human,-1); }
    private Decision question(Human human) {
        questionTurn=human.id; armed=false; prepared=null;
        return new Decision("volume_question",percent<0?ASK:confirm(percent),origin,questionTurn,human,percent);
    }
    Decision begin(Human human) {
        if(active || human.sequence>maximumSequence) throw new IllegalArgumentException("Volume form already active");
        Integer value;
        try { value=candidate(human.text); } catch(IllegalArgumentException error) { return cancelled(human); }
        active=true; created=human.speechEnd; lastSequence=human.sequence; percent=value==null?-1:value;
        origin=value==null?"":human.id; return question(human);
    }
    void spoken(String turn,boolean completed,long now) {
        if(!active || !questionTurn.equals(turn)) return;
        if(completed) { armed=true; heardAt=now; } else clear();
    }
    Decision answer(Human human,long now) {
        if(!active) return null;
        if(!armed || human.sequence<=lastSequence || human.sequence>maximumSequence || human.id.equals(questionTurn)
                || human.speechEnd<heardAt || now-created>=MAX_AGE_MS) return cancelled(human);
        lastSequence=human.sequence; armed=false; answers++;
        if(answers>6) return cancelled(human);
        String text;
        try { text=plain(human.text).replaceAll("\\s*,\\s*"," "); } catch(IllegalArgumentException error) { return cancelled(human); }
        if(NO.contains(text)) return cancelled(human);
        if(percent>=0 && YES.contains(text)) {
            prepared=new Decision("confirmed_volume_proposal","",origin,questionTurn,human,percent); return prepared;
        }
        if(YES.contains(text)) return question(human);
        if(Pattern.compile("\\b(?:busca|consulta|investiga|recordatorio|recuerdame|alarma|temporizador|enciende|apaga|elimina|borra)\\b").matcher(text).find()) {
            clear(); return null;
        }
        Integer value;
        try { value=candidate(human.text); } catch(IllegalArgumentException error) { return cancelled(human); }
        if(value==null) {
            if(percent>=0) { clear(); return null; }
            return question(human);
        }
        percent=value; origin=human.id; return question(human);
    }
    int claim(String origin,String question,Human human,int requested,long now) {
        Decision value=prepared;
        if(value==null || now-created>=MAX_AGE_MS || !value.origin.equals(origin) || !value.question.equals(question)
                || !value.turn.equals(human.id) || value.sequence!=human.sequence || value.percent!=requested)
            throw new IllegalArgumentException("Unrelated volume confirmation");
        int result=value.percent; clear(); return result;
    }
}
