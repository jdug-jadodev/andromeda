package local.doja.voice;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import org.json.JSONObject;

/** Pinned TLS authenticates the server before the separate client token is sent. */
final class VoiceConnection implements Closeable {
    final SSLSocket socket;
    final DataInputStream input;
    final DataOutputStream output;
    final JSONObject ready;

    private VoiceConnection(SSLSocket socket, DataInputStream input, DataOutputStream output, JSONObject ready) {
        this.socket = socket; this.input = input; this.output = output; this.ready = ready;
    }
    static VoiceConnection open(Context context, String source) throws Exception {
        if (!"microphone".equals(source) && !"synthetic_pcm".equals(source)) throw new IOException("Fuente inválida");
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException { throw new CertificateException("Solo servidor local"); }
            public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                try {
                    if (chain == null || chain.length == 0) throw new CertificateException("Sin certificado");
                    chain[0].checkValidity();
                    byte[] actual = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                    byte[] expected = new byte[32];
                    for (int i = 0; i < expected.length; i++) expected[i] = (byte)Integer.parseInt(AssistantConfig.CONVERSATION_CERT_SHA256.substring(i * 2, i * 2 + 2), 16);
                    if (!MessageDigest.isEqual(actual, expected)) throw new CertificateException("Servidor no reconocido");
                } catch (GeneralSecurityException | IllegalArgumentException error) { throw new CertificateException("No se verificó el servidor local", error); }
            }
        }}, new SecureRandom());
        SSLSocket socket = (SSLSocket)tls.getSocketFactory().createSocket();
        try {
            socket.setSoTimeout(5000); socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress("127.0.0.1", 11436), 1500);
            socket.startHandshake();
            String token = new String(Files.readAllBytes(new File(context.getFilesDir(), "voice-conversation-token").toPath()), StandardCharsets.UTF_8).trim();
            if (token.length() < 32) throw new IOException("Falta credencial de conversación");
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            JSONObject hello = new JSONObject().put("protocol", 2).put("mode", "conversation_only").put("source", source).put("token", token);
            writeFrame(output, 1, hello.toString().getBytes(StandardCharsets.UTF_8));
            DataInputStream input = new DataInputStream(socket.getInputStream());
            JSONObject ready = readEvent(input);
            if (!"ready".equals(ready.optString("type")) || !VoiceSessionPolicy.isConversationReady(
                    ready.optInt("protocol"), ready.optBoolean("conversation_only"), ready.optBoolean("wake_only"),
                    ready.optBoolean("wake_enabled"), ready.optBoolean("ai_enabled"), ready.optBoolean("actions_enabled", true))
                    || !source.equals(ready.optString("input_source")) || !"boottime_ms".equals(ready.optString("clock"))
                    || ready.optInt("response_start_ms") != 30000
                    || Math.abs(SystemClock.elapsedRealtime() - ready.optLong("server_time_ms")) > 1000)
                throw new IOException("El backend no es la conversación restringida o el reloj no coincide");
            return new VoiceConnection(socket, input, output, ready);
        } catch (Exception error) { socket.close(); throw error; }
    }
    static JSONObject readEvent(DataInputStream input) throws Exception {
        int size = input.readInt();
        if (size < 1 || size > 16384) throw new IOException("Evento inválido");
        byte[] data = new byte[size]; input.readFully(data);
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }
    static void writeFrame(DataOutputStream output, int kind, byte[] data) throws IOException {
        synchronized (output) { output.writeByte(kind); output.writeInt(data.length); output.write(data); output.flush(); }
    }
    public void close() throws IOException { socket.close(); }
}
