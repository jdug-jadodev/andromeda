package local.doja.voice;

import java.text.SimpleDateFormat;
import java.util.*;

/** Fixed receipt-derived speech, pure Java for shared Python/Android checks. */
final class AgendaSpeechPolicy {
    static String text(String status, AgendaRequestGuard.Value value, boolean detailed) {
        if ("CREATED".equals(status)) {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", new Locale("es", "CO"));
            format.setTimeZone(TimeZone.getTimeZone(AgendaEntry.ZONE));
            if(value.weekday!=0) return "Alarma semanal guardada: "+AlarmSchedule.label(value.weekday,value.minute)+". Próxima: "+format.format(new Date(value.due))+".";
            if (detailed && value.kind == AgendaEntry.Kind.REMINDER) {
                String prefix = "";
                if (value.delay > 0) {
                    long scale = value.delay % 3600000 == 0 ? 3600000 : value.delay % 60000 == 0 ? 60000 : 1000;
                    String unit = scale == 3600000 ? "hora" : scale == 60000 ? "minuto" : "segundo";
                    long count = value.delay / scale; prefix = "En " + count + " " + unit + (count != 1 ? "s" : "") + ", ";
                }
                format.applyPattern("dd/MM/yyyy 'a las' HH:mm:ss");
                String scheduled = prefix.isEmpty() ? "Para el " : prefix + "para el ";
                return "Recordatorio guardado: " + value.text + ". " + scheduled + format.format(new Date(value.due)) + ", hora de Bogotá.";
            }
            return (value.kind == AgendaEntry.Kind.ALARM ? "Alarma programada" : "Recordatorio programado") + " para el " + format.format(new Date(value.due)) + ", hora de Bogotá.";
        }
        switch (status) {
            case "DUPLICATE": return "Esa orden ya tenía un registro. No la repetí; revisa la agenda manual.";
            case "REJECTED": return "Android rechazó la programación. No he confirmado un aviso nuevo.";
            case "FAILED": return "La programación falló y se retiró ese intento. Revisa la agenda manual.";
            case "UNKNOWN": return "No pude confirmar el resultado. Revisa Alarmas o Recordatorios antes de pedirlo otra vez.";
            default: throw new IllegalArgumentException("Unknown agenda receipt status");
        }
    }
}
