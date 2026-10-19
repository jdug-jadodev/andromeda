package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class AgendaGuardTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        int rows = 0;
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.startsWith("kind\t") || line.isEmpty()) continue;
            String[] fields = line.split("\t", -1); rows++;
            AgendaRequestGuard.Value value = null;
            try { value = AgendaRequestGuard.parse(AgendaEntry.Kind.valueOf(fields[0]), fields[1], fields[2], fields[3], Long.parseLong(fields[4])); }
            catch (RuntimeException invalid) { if (!fields[5].equals("INVALID")) throw new AssertionError("Unexpected calendar rejection at fixture " + rows, invalid); }
            if (fields[5].equals("INVALID")) check(value == null, "Unsafe current request accepted at fixture " + rows);
            else {
                check(value != null && value.due == Long.parseLong(fields[5]), "Calendar due mismatch at fixture " + rows);
                check(value.delay == Long.parseLong(fields[6]) && value.text.equals(fields[3]), "Literal payload changed at fixture " + rows);
            }
        }
        long wall = 1792328400000L, boot = 100000;
        AgendaPermit permit = AgendaPermit.validated("00000000-0000-4000-8000-000000000001", AgendaEntry.Kind.REMINDER,
            "recuérdame beber agua en dos minutos", "en dos minutos", "beber agua", wall + 120000, 120000, boot + 4000, wall, boot, boot, wall);
        check(permit.begin(boot + 10, wall + 10), "Valid permit not accepted");
        check(!permit.begin(boot + 20, wall + 20), "Permit replayed");
        check(!permit.current(boot + 4000, wall + 4000), "Expired permit alive");
        check(!permit.current(boot + 20, wall + 5000), "Clock jump accepted");
        permit.revoke(); check(permit.current(boot + 20, wall + 20), "Closing chat revoked an already accepted action");
        AgendaPermit revoked = AgendaPermit.validated("00000000-0000-4000-8000-000000000002", AgendaEntry.Kind.REMINDER,
            "recuérdame beber agua en dos minutos", "en dos minutos", "beber agua", wall + 120000, 120000, boot + 4000, wall, boot, boot, wall);
        revoked.revoke(); check(!revoked.begin(boot + 10, wall + 10), "Revoked unaccepted grant executed");
        System.out.println("Agenda literal/calendar permits: " + checks + " assertions passed (shared public fixtures; no Android/audio/network).");
    }
}
