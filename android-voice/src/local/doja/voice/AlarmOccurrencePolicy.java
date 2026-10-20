package local.doja.voice;

/** Only the currently delivering alarm. No series deletion, model, persistence or network. */
final class AlarmOccurrencePolicy {
    enum Action { SILENCE, SNOOZE, DISMISS }
    static Action command(String text) {
        if(text==null || text.length()>100) return null;
        String plain=TimerRequestGuard.normalize(text).trim().replaceFirst("^andromeda[, ]+","");
        if(plain.equals("silencio")) return Action.SILENCE;
        if(plain.matches("(?:pospon|posponla|posponer)(?: la alarma)? (?:cinco|5) minutos")) return Action.SNOOZE;
        if(plain.matches("descarta (?:la alarma|esta alarma|esta vez)")) return Action.DISMISS;
        return null;
    }
    static final class Permit {
        final AgendaEntry selected; final Action action; final String operation;
        final long before,wall,boot;
        private boolean used;
        private Permit(AgendaEntry selected,Action action,String operation,long now,long wall) {
            this.selected=selected; this.action=action; this.operation=operation; before=now+4000; this.wall=wall; boot=now;
        }
        synchronized boolean begin(long now,long nowWall) {
            if(used || now>=before || now<boot || Math.abs((nowWall-wall)-(now-boot))>1500) return false;
            used=true; return true;
        }
    }
    private final AgendaEntry selected;
    private final long expires;
    private boolean consumed;
    AlarmOccurrencePolicy(AgendaEntry selected,long expires,long now) {
        if(selected==null || selected.kind!=AgendaEntry.Kind.ALARM || selected.state!=AgendaEntry.State.DELIVERING
                || expires<=now || expires-now>30000) throw new IllegalArgumentException("No current sounding alarm");
        this.selected=selected; this.expires=expires;
    }
    synchronized Permit claim(Action action,String operation,long now,long wall) {
        if(consumed || now>=expires || action==null || action==Action.SILENCE || !AgendaEntry.validId(operation) || wall<=0) return null;
        consumed=true; return new Permit(selected,action,operation,now,wall);
    }
}
