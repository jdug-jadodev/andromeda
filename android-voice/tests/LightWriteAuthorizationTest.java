package local.doja.voice;

/** Confirmation gate test: one use, bounded lifetime, no Android/I/O. */
public final class LightWriteAuthorizationTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Light authorization check " + checks); }

    public static void main(String[] args) {
        LightWriteAuthorization permit = LightWriteAuthorization.issueForUser(1000L);
        check(permit.consume(1000L));
        check(!permit.consume(1001L));
        LightWriteAuthorization expired = LightWriteAuthorization.issueForUser(1000L);
        check(!expired.consume(1000L + LightWriteAuthorization.TTL_MS + 1L));
        check(LightCommandExecutionPolicy.canExecute(
            LightCommand.color(LightTarget.MR_STAR, LightColor.parseHex("#FF0000"), 25)));
        check(LightCommandExecutionPolicy.canExecute(
            LightCommand.color(LightTarget.MR_STAR, LightColor.parseHex("#FFFFFF"), 25)));
        check(LightCommandExecutionPolicy.canExecute(
            LightCommand.color(LightTarget.MR_STAR, LightColor.parseHex("#FF8000"), 25)));
        check(!LightCommandExecutionPolicy.canExecute(
            LightCommand.color(LightTarget.MR_STAR, LightColor.parseHex("#000000"), 25)));
        check(LightCommandExecutionPolicy.canExecute(
            LightCommand.color(LightTarget.BOTH, LightColor.parseHex("#FF0000"), 25)));
        System.out.println("Light authorization: " + checks + " assertions passed (single-use confirmation, no I/O).");
    }
}
