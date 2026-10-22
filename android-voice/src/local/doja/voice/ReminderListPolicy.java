package local.doja.voice;

import java.util.*;
import java.util.regex.*;

/** Read-only scope and local speech. No database, scheduler, model, files or network. */
final class ReminderListPolicy {
    static final String POLICY="android_reminder_read_v1";
    static final String ACK="Voy a leer el resultado de tus recordatorios guardados.";
    static final String UNAVAILABLE="No pude leer los recordatorios guardados. No he cambiado ninguno.";
    static final int MAX_SPOKEN=1800,MAX_SHOWN=3;
    static boolean requested(String human) {
        if(human==null || human.isEmpty() || human.length()>250
                || Pattern.compile("[\\x00-\\x1f\\x7f\"'«»<>]").matcher(human).find()) return false;
        String text=TimerRequestGuard.normalize(human);
        return Pattern.compile("\\brecordatorios?\\b").matcher(text).find()
            && Pattern.compile("\\b(?:lista|listar|listame|muestra|muestrame|mostrar|dime|dame|lee|leeme|leer|ver|consulta|consultar|que|cuales|cuantos|tengo|hay|tienes)\\b").matcher(text).find()
            && !Pattern.compile("\\b(?:no|nunca|ni|si|ayer|dije|ejemplo|imagina|supongamos|pon|ponme|crea|crear|programa|recuerdame|cancela|borra|elimina|quita|apaga|enciende|internet|web|google|whatsapp)\\b").matcher(text).find();
    }
    static final class Grant {
        final String session,context,turn,nonce; final long sequence,speechEnd,before;
        Grant(String session,String context,String turn,String nonce,long sequence,long speechEnd,long before) {
            this.session=session; this.context=context; this.turn=turn; this.nonce=nonce; this.sequence=sequence; this.speechEnd=speechEnd; this.before=before;
        }
    }
    static final class Permit {
        final Grant grant; private boolean used;
        Permit(Grant grant) { this.grant=grant; }
        synchronized boolean begin(long now) { if(used || now>=grant.before) return false; used=true; return true; }
    }
    static Permit claim(Grant g,String session,String context,String turn,String human,long sequence,long speechEnd,long sessionEnd,long now) {
        if(g==null || !requested(human) || !session.equals(g.session) || !context.equals(g.context)
                || !turn.equals(g.turn) || !g.nonce.matches("[0-9a-f]{32}") || !turn.matches("[0-9a-f]{32}")
                || sequence<1 || sequence>64 || sequence!=g.sequence || speechEnd!=g.speechEnd || speechEnd<=0
                || speechEnd>now+100 || now-speechEnd>=56500 || g.before<=now || g.before-now>4100
                || g.before>speechEnd+56500 || g.before>sessionEnd-5000) throw new IllegalArgumentException("Invalid reminder read scope");
        return new Permit(g);
    }
    static final class Receipt {
        final String status,text; final long total; final int shown;
        Receipt(String status,long total,int shown,String text) {
            if(!Arrays.asList("READ","UNAVAILABLE").contains(status) || total<0 || total>100000 || shown<0 || shown>Math.min(3,total)
                    || text==null || text.isEmpty() || text.length()>MAX_SPOKEN || !status.equals("READ") && (total!=0 || shown!=0 || !text.equals(UNAVAILABLE)))
                throw new IllegalArgumentException("Invalid reminder read receipt");
            this.status=status; this.total=total; this.shown=shown; this.text=text;
        }
    }
    static Receipt unavailable() { return new Receipt("UNAVAILABLE",0,0,UNAVAILABLE); }
    static Receipt render(List<AgendaEntry> entries,long total,long now) {
        if(entries==null || total<entries.size() || total>100000) throw new IllegalArgumentException("Invalid reminder snapshot");
        if(total==0) return new Receipt("READ",0,0,"No tienes recordatorios guardados en Andrómeda.");
        StringBuilder body=new StringBuilder(); int shown=0;
        for(AgendaEntry entry:entries) {
            if(entry.kind!=AgendaEntry.Kind.REMINDER) throw new IllegalArgumentException("Not a reminder snapshot");
            if(shown>=MAX_SHOWN) break;
            String line=(shown+1)+". "+entry.title()+". "+entry.when()+". "+entry.status(now)+". ";
            if(body.length()+line.length()>MAX_SPOKEN-220) break;
            body.append(line); shown++;
        }
        if(shown==0) throw new IllegalArgumentException("Reminder snapshot unavailable");
        String header="Tienes "+total+" recordatorio"+(total==1?"":"s")+" guardado"+(total==1?"":"s")+". ";
        if(total>shown) header+="Te leo los primeros "+shown+", por fecha. ";
        String tail=total>shown?"Hay "+(total-shown)+" más. Puedes ver la lista completa en Recordatorios.":"";
        return new Receipt("READ",total,shown,header+body+tail);
    }
}
