package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** RFC 6455 handshake, pure Java so it can be tested without Android. */
final class WebSocketHandshake {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    static String accept(String key) throws Exception {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("Missing key");
        byte[] digest = MessageDigest.getInstance("SHA-1")
            .digest((key + GUID).getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(digest);
    }

    private WebSocketHandshake() { }
}
