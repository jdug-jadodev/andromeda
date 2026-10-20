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
import org.json.JSONArray;
import java.util.List;

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
        return open(context, source, ConversationMemory.emptySnapshot());
    }
    static VoiceConnection open(Context context, String source, ConversationMemory.Snapshot snapshot) throws Exception {
        return open(context, source, snapshot, null);
    }
    static VoiceConnection open(Context context, String source, ConversationMemory.Snapshot snapshot,
            List<ConversationMemory.Snapshot> catalog) throws Exception {
        return open(context, source, snapshot, catalog, false);
    }
    static VoiceConnection open(Context context, String source, ConversationMemory.Snapshot snapshot,
             List<ConversationMemory.Snapshot> catalog, boolean timerTrial) throws Exception {
        return open(context, source, snapshot, catalog, timerTrial, false);
    }
    static VoiceConnection open(Context context, String source, ConversationMemory.Snapshot snapshot,
              List<ConversationMemory.Snapshot> catalog, boolean timerTrial, boolean assistantTools) throws Exception {
        return open(context,source,snapshot,catalog,timerTrial,assistantTools,false);
    }
    static VoiceConnection open(Context context, String source, ConversationMemory.Snapshot snapshot,
              List<ConversationMemory.Snapshot> catalog, boolean timerTrial, boolean assistantTools, boolean continuousVoice) throws Exception {
        if (timerTrial && assistantTools) throw new IOException("Modos incompatibles");
        boolean guidedAgenda = assistantTools && AssistantConfig.GUIDED_AGENDA;
        boolean lifecycle=continuousVoice && AssistantConfig.ALARM_LIFECYCLE;
        if (continuousVoice && (!guidedAgenda || catalog == null || timerTrial)) throw new IOException("Modo continuo no habilitado");
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
            try { socket.connect(new InetSocketAddress("127.0.0.1", 11436), 1500); }
            catch (java.net.ConnectException error) { throw new IOException("Motor local no preparado; hace falta preparar otra prueba. No es un fallo de Internet.", error); }
            socket.startHandshake();
            String token = new String(Files.readAllBytes(new File(context.getFilesDir(), "voice-conversation-token").toPath()), StandardCharsets.UTF_8).trim();
            if (token.length() < 32) throw new IOException("Falta credencial de conversación");
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            JSONObject hello = new JSONObject().put("protocol", lifecycle ? 13 : continuousVoice ? 12 : guidedAgenda ? 11 : assistantTools ? 10 : timerTrial ? 8 : 6).put("mode", assistantTools ? "assistant_tools" : timerTrial ? "timer_trial" : "conversation_only").put("source", source)
                .put("token", token).put("context", ConversationChannel.encode(snapshot));
            if (assistantTools) hello.put("web_response_mode", "worker_first_v2_medium").put("phone_volume_policy", "absolute_media_v1");
            if (guidedAgenda) hello.put("agenda_dialogue_policy", "guided_agenda_v1").put("action_completion_policy", "continue_v1");
            if (continuousVoice) hello.put("voice_lifetime_policy",ContinuousVoicePolicy.VOICE).put("dialogue_reset_policy",ContinuousVoicePolicy.RESET);
            if(lifecycle) hello.put("alarm_lifecycle_policy",AlarmSchedule.POLICY);
            if (catalog != null) {
                JSONArray chats = new JSONArray();
                for (ConversationMemory.Snapshot chat : catalog) chats.put(ConversationChannel.encode(chat));
                hello.put("voice_entry", true).put("catalog", chats);
            }
            byte[] helloBytes = hello.toString().getBytes(StandardCharsets.UTF_8);
            if (helloBytes.length > 131072) throw new IOException("Contexto fuera del límite de transporte");
            writeFrame(output, 1, helloBytes);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            JSONObject ready = readEvent(input);
            String rejection = VoiceStartPolicy.rejection(ready.optString("type"), ready.optString("reason"));
            if (rejection != null) throw new IOException(rejection);
            if ("context_expired".equals(ready.optString("reason"))) throw new IOException("El contexto caducó; vuelve a elegir la prueba");
            boolean validMode = assistantTools ? ready.optInt("protocol") == (lifecycle ? 13 : continuousVoice ? 12 : guidedAgenda ? 11 : 10) && ready.optBoolean("assistant_tools") && ready.optBoolean("actions_enabled")
                    && !ready.optBoolean("conversation_only", true) && !ready.optBoolean("wake_only") && ready.optBoolean("wake_enabled") && ready.optBoolean("ai_enabled")
                : timerTrial ? VoiceSessionPolicy.isTimerReady(ready.optInt("protocol"), ready.optBoolean("timer_trial"),
                    ready.optBoolean("conversation_only", true), ready.optBoolean("wake_only"), ready.optBoolean("wake_enabled"),
                    ready.optBoolean("ai_enabled"), ready.optBoolean("actions_enabled"))
                : VoiceSessionPolicy.isConversationReady(
                    ready.optInt("protocol"), ready.optBoolean("conversation_only"), ready.optBoolean("wake_only"),
                    ready.optBoolean("wake_enabled"), ready.optBoolean("ai_enabled"), ready.optBoolean("actions_enabled", true));
            if (!"ready".equals(ready.optString("type")) || !validMode
                    || timerTrial && !"request_authorized_close".equals(ready.optString("timer_policy"))
                    || timerTrial && (ready.optJSONArray("allowed_tools") == null || ready.getJSONArray("allowed_tools").length() != 1
                         || !"create_timer".equals(ready.getJSONArray("allowed_tools").optString(0)))
                    || assistantTools && !"[\"create_timer\",\"create_alarm\",\"create_reminder\",\"search_web\",\"set_phone_volume\"]".equals(String.valueOf(ready.optJSONArray("allowed_tools")))
                    || !source.equals(ready.optString("input_source")) || !"boottime_ms".equals(ready.optString("clock"))
                    || "microphone".equals(source) && ready.optBoolean("test_fixture")
                    || ready.optInt("response_start_ms") != (assistantTools ? 60000 : 30000) || ready.optInt("context_version") != 1
                    || assistantTools && !"worker_first_v2_medium".equals(ready.optString("web_response_mode"))
                    || assistantTools && !"absolute_media_v1".equals(ready.optString("phone_volume_policy"))
                    || guidedAgenda && (!"guided_agenda_v1".equals(ready.optString("agenda_dialogue_policy")) || !"continue_v1".equals(ready.optString("action_completion_policy")))
                    || continuousVoice && !ContinuousVoicePolicy.ready(ready.optInt("protocol"),ready.optInt("seconds",-1),ready.optString("voice_lifetime_policy"),
                        ready.optString("dialogue_reset_policy"),ready.optLong("action_window_ms"),ready.optLong("stream_start_ms"),ready.optLong("stream_idle_ms"),lifecycle)
                    || lifecycle && !AlarmSchedule.POLICY.equals(ready.optString("alarm_lifecycle_policy"))
                    || !ready.optBoolean("continuous_chat") || ready.optInt("listen_idle_ms") != 50000
                    || !ready.optBoolean("followup_offer") || !ready.optBoolean("closing_farewell")
                    || catalog != null && !ready.optBoolean("voice_entry")
                    || !snapshot.conversationId.equals(ready.optString("context_id"))
                    || !ready.optString("session_id").matches("[0-9a-f]{32}")
                    || ready.optInt("restored_turns", -1) != snapshot.turns.size()
                    || Math.abs(SystemClock.elapsedRealtime() - ready.optLong("server_time_ms")) > 1000)
                throw new IOException("El backend no es la conversación restringida o el reloj no coincide");
            return new VoiceConnection(socket, input, output, ready);
        } catch (Exception error) { try { socket.close(); } catch (Exception ignored) {} throw error; }
    }
    static JSONObject readEvent(DataInputStream input) throws Exception {
        int size = input.readInt();
        if (size < 1 || size > 65536) throw new IOException("Evento inválido");
        byte[] data = new byte[size]; input.readFully(data);
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }
    static void writeFrame(DataOutputStream output, int kind, byte[] data) throws IOException {
        synchronized (output) { output.writeByte(kind); output.writeInt(data.length); output.write(data); output.flush(); }
    }
    public void close() throws IOException { socket.close(); }
}
