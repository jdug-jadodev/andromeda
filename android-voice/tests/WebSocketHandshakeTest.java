package local.doja.voice;

/** Plain JVM test: RFC 6455 example, no Android, no network. */
public final class WebSocketHandshakeTest {
    public static void main(String[] args) throws Exception {
        // RFC 6455 section 1.3.
        String accept = WebSocketHandshake.accept("dGhlIHNhbXBsZSBub25jZQ==");
        check("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=".equals(accept), "rfc example");

        boolean rejected = false;
        try {
            WebSocketHandshake.accept("");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "empty key");

        System.out.println("WebSocket handshake tests passed.");
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    private WebSocketHandshakeTest() { }
}
