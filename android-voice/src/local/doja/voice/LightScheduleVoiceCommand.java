package local.doja.voice;

/** Validated voice request for the local light schedule. It never writes BLE. */
final class LightScheduleVoiceCommand {
    enum Action { SET, ENABLE, DISABLE, DELETE }

    final Action action;
    final LightTarget target;
    final int onMinute;   // -1 means preserve the existing value
    final int offMinute;  // -1 means preserve the existing value
    final int days;       // -1 means preserve the existing value; ALL_DAYS for a new schedule

    LightScheduleVoiceCommand(Action action, LightTarget target, int onMinute, int offMinute, int days) {
        if (action == null || target == null) throw new IllegalArgumentException("Horario de voz incompleto");
        if (onMinute < -1 || onMinute > 1439 || offMinute < -1 || offMinute > 1439)
            throw new IllegalArgumentException("Hora de voz fuera de rango");
        if (days == 0 || days < -1 || days > LightSchedule.ALL_DAYS)
            throw new IllegalArgumentException("Días de voz inválidos");
        if (action == Action.SET && onMinute == offMinute && onMinute >= 0)
            throw new IllegalArgumentException("Encendido y apagado no pueden coincidir");
        this.action = action; this.target = target; this.onMinute = onMinute;
        this.offMinute = offMinute; this.days = days;
    }
}
