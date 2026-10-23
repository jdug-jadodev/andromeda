package local.doja.voice;

/** Parser-only checks for voice schedule editing; no model, backend, audio or BLE. */
public final class LightScheduleVoiceTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static LightScheduleVoiceCommand set(String text) {
        LightScheduleVoiceCommand value = LightScheduleVoiceParser.parse(text);
        check(value.action == LightScheduleVoiceCommand.Action.SET, "Expected SET for: " + text);
        return value;
    }
    private static void invalid(String text) {
        checks++;
        try { LightScheduleVoiceParser.parse(text); throw new AssertionError("Accepted: " + text); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        LightScheduleVoiceCommand value = set("Programa las luces a las 5:30 y a las 10 de la noche.");
        check(value.target == LightTarget.BOTH, "Default target must be both");
        check(value.onMinute == 1050 && value.offMinute == 1320, "Expected 17:30/22:00");
        check(value.days == -1, "Unspecified days must preserve the stored value");

        value = set("Programa las dos luces a las 5:30 de la tarde y a las 10 de la noche todos los días.");
        check(value.onMinute == 1050 && value.offMinute == 1320 && value.days == LightSchedule.ALL_DAYS,
            "Explicit afternoon/night and all days");

        value = set("Enciende la luz mueble a las 6 de la tarde y apágala a las 11 de la noche");
        check(value.target == LightTarget.LOTUS && value.onMinute == 1080 && value.offMinute == 1380,
            "Furniture light order");

        value = set("Apaga la luz del televisor a las 11 y enciéndela a las 5:30 de la tarde");
        check(value.target == LightTarget.MR_STAR && value.onMinute == 1050 && value.offMinute == 1380,
            "TV light order must be reordered by action");

        value = set("Programa las luces a las cinco y media de la tarde y a las diez de la noche");
        check(value.onMinute == 1050 && value.offMinute == 1320, "Word-number times");

        value = set("Programa las luces a las 5:30 y 10");
        check(value.onMinute == 1050 && value.offMinute == 1320, "Evening inference for ambiguous light hours");

        value = set("Cambia el encendido a las 6 de la tarde");
        check(value.onMinute == 1080 && value.offMinute == -1, "One-ended on edit must preserve off");
        value = set("Cambia el apagado a las 11 de la noche");
        check(value.onMinute == -1 && value.offMinute == 1380, "One-ended off edit must preserve on");

        value = set("Programa las luces a las 17:30 y 22:00");
        check(value.onMinute == 1050 && value.offMinute == 1320, "24-hour schedule");

        value = set("Programa las luces de lunes a viernes a las 18:00 y 23:00");
        check(value.days == LightSchedule.WEEKDAYS && value.onMinute == 1080 && value.offMinute == 1380,
            "Weekday schedule");

        value = set("Programa las luces de fin de semana a las 7:00 de la noche y 11:00 de la noche");
        check(value.days == LightSchedule.WEEKEND && value.onMinute == 1140 && value.offMinute == 1380,
            "Weekend schedule");

        check(LightScheduleVoiceParser.parse("Desactiva el horario de las luces").action
            == LightScheduleVoiceCommand.Action.DISABLE, "Disable schedule");
        check(LightScheduleVoiceParser.parse("Activa la programación de luces").action
            == LightScheduleVoiceCommand.Action.ENABLE, "Enable schedule");
        check(LightScheduleVoiceParser.parse("Borra el horario de luces").action
            == LightScheduleVoiceCommand.Action.DELETE, "Delete schedule");
        check(LightScheduleVoiceParser.parse("Desactiva el horario de la luz mueble").target
            == LightTarget.LOTUS, "Disable target");

        invalid("Enciende las luces");
        invalid("Programa las luces a las 10 y a las 10 de la noche");
        invalid("Borra la luz del televisor");
        invalid("Programa las luces a las 25:00 y 26:00");
        invalid("Programa las luces manana");
        System.out.println("Light schedule voice parser: " + checks + " assertions passed (no BLE/backend/audio).");
    }
}
