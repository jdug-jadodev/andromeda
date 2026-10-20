package local.doja.voice;

/** Independent typed confirmation and sealed one-use deletion permission. No Android/IO. */
final class AlarmDeletion {
    static final String CANCELLED="No he eliminado ninguna alarma.";
    static final String UNKNOWN="No pude confirmar la eliminación. Revisa Alarmas antes de pedirla otra vez.";
    static boolean unsupportedClaim(String text) {
        String plain=TimerRequestGuard.normalize(text);
        return java.util.regex.Pattern.compile("\\b(?:alarma\\b.{0,45}(?:eliminad[oa]|borrad[oa]|pospuest[oa])|(?:he|hemos|ya|acabo de) (?:\\w+ )?(?:eliminado|borrado|pospuesto))\\b").matcher(plain).find();
    }
    static String selectionText(String status) {
        switch(status) {
            case "NOT_FOUND": return "No encontré esa alarma semanal.";
            case "AMBIGUOUS": return "Hay más de una alarma con ese horario. Elige la serie en Alarmas.";
            case "UNKNOWN": return "No pude comprobar las alarmas guardadas.";
            default: throw new IllegalArgumentException("Invalid selection status");
        }
    }
    static String resultText(String status) {
        switch(status) {
            case "REMOVED": return "Alarma eliminada, incluida toda su repetición semanal.";
            case "REJECTED": return CANCELLED;
            case "STALE": return "La alarma cambió. No la eliminé; revisa Alarmas.";
            case "UNKNOWN": return UNKNOWN;
            default: throw new IllegalArgumentException("Invalid deletion status");
        }
    }
    static String question(AgendaEntry selected) { return "¿Confirmas eliminar toda la serie de la alarma "+AlarmSchedule.label(selected.weekday,selected.minute)+"?"; }
    static final class Permit {
        final String operation; final AgendaEntry selected; final long before;
        private boolean used,revoked;
        private Permit(String operation,AgendaEntry selected,long before) { this.operation=operation; this.selected=selected; this.before=before; }
        synchronized boolean begin(long now) { if(used || revoked || now>=before) return false; used=true; return true; }
        synchronized void revoke() { if(!used) revoked=true; }
    }
    private AgendaEntry selected;
    private String origin="";
    private String finalTurn="";
    private long finalSequence;
    private long sequence,expires;
    private boolean armed,prepared;
    void clear() { selected=null; origin=finalTurn=""; finalSequence=0; armed=prepared=false; }
    boolean active() { return selected!=null; }
    void select(AgendaDialogue.Human human,AgendaEntry entry,long now) {
        int[] target=AlarmSchedule.deletionTarget(human.text);
        if(active() || entry==null || entry.kind!=AgendaEntry.Kind.ALARM || entry.weekday!=target[0] || entry.minute!=target[1]
                || entry.state==AgendaEntry.State.CANCEL_PENDING) throw new IllegalArgumentException("Unrelated alarm selection");
        selected=entry; origin=human.id; sequence=human.sequence; expires=now+120000; armed=prepared=false;
    }
    String question() { if(selected==null) throw new IllegalStateException("No selected alarm"); return question(selected); }
    void spoken(String turn,boolean completed) { if(origin.equals(turn)) { if(completed) armed=true; else clear(); } }
    boolean human(AgendaDialogue.Human human,long now) {
        if(!active()) return false;
        if(!armed || now>=expires || human.sequence<=sequence) { clear(); throw new IllegalArgumentException("Unheard/stale deletion question"); }
        String plain=TimerRequestGuard.normalize(AgendaRequestGuard.clean(human.text));
        prepared=plain.matches("si|confirmo|si elimina esa alarma|elimina esa alarma|confirmo eliminar esa alarma");
        armed=false;
        if(prepared) { finalTurn=human.id; finalSequence=human.sequence; }
        else clear();
        return prepared;
    }
    Permit claim(String operation,String origin,String eventId,long revision,long due,int weekday,int minute,AgendaDialogue.Human human,long before,long sessionEnd,long now) {
        if(!prepared || selected==null || now>=expires || !this.origin.equals(origin) || !finalTurn.equals(human.id) || finalSequence!=human.sequence || !AgendaEntry.validId(operation)
                || !selected.id.equals(eventId) || selected.revision!=revision || selected.due!=due || selected.weekday!=weekday || selected.minute!=minute
                || before<=now || before-now>4100 || before>human.speechEnd+26500 || before>sessionEnd-5000)
            throw new IllegalArgumentException("Unrelated deletion grant");
        Permit permit=new Permit(operation,selected,before); clear(); return permit;
    }
}
