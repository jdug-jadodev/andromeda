package local.doja.voice;

import android.content.Context;
import android.util.Log;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * Gateway TLS de casa. Solo acepta orígenes del tailnet (100.64.0.0/10 o
 * fd7a:115c:a1e0::/48), nunca la Wi-Fi local. No habla con el residente todavía:
 * esta fase expone salud, capacidades y emparejamiento.
 */
final class HomeGatewayServer {
    interface PairingListener {
        void onPairing(String code);
        void onClosed();
    }

    static final int PORT = 11440;
    private static final int MAX_HEADER = 8192;
    private static final int MAX_BODY = 65536;
    private static final int MAX_MESSAGE_BODY = VoiceMessagePolicy.MAX_BYTES + 1024;
    private static final int MAX_WORKERS = 4;
    private static final int RATE_MAX = 30;
    private static final long RATE_WINDOW_MS = 60000L;

    private final Context context;
    private final PairingListener pairing;
    private final DeviceStore devices;
    private final PairingManager pairingManager;
    private final Map<String, long[]> rateLimits = new ConcurrentHashMap<>();
    private final AtomicBoolean cameraViewer = new AtomicBoolean();
    private final AtomicBoolean cameraTearingDown = new AtomicBoolean();
    private volatile SSLSocket cameraSocket;
    private volatile boolean cameraCancelled;
    private static final long CAMERA_MAX_MS = 10 * 60 * 1000L;
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_WORKERS);
    private final ScheduledExecutorService messageMaintenance = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;
    private SSLServerSocket server;
    private Thread acceptThread;

    HomeGatewayServer(Context context, PairingListener pairing) {
        this.context = context.getApplicationContext();
        this.pairing = pairing;
        this.devices = new DeviceStore(this.context);
        this.pairingManager = new PairingManager(
            new java.io.File(this.context.getFilesDir(), "gateway-pin.txt"), pairing::onClosed);
    }

    void start() throws Exception {
        SSLContext tls = GatewayKeystore.context(context);
        SSLServerSocket socket = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0", PORT), 8);
        server = socket;
        running = true;
        VoiceMessageStore.get(context).purgeExpired();
        messageMaintenance.scheduleAtFixedRate(
            () -> VoiceMessageStore.get(context).purgeExpired(), 1, 1, TimeUnit.HOURS);
        acceptThread = new Thread(this::acceptLoop, "andromeda-gateway");
        acceptThread.setDaemon(true);
        acceptThread.start();
        Log.i("AndromedaGateway", "listening on " + PORT + " pin " + GatewayKeystore.pin(context));
    }

    String openPairing() {
        String code = pairingManager.open();
        pairing.onPairing(code);
        return code;
    }

    void stop() {
        running = false;
        close(server);
        workers.shutdownNow();
        messageMaintenance.shutdownNow();
        pairingManager.shutdown();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket accepted = server.accept();
                if (!(accepted instanceof SSLSocket) || !tailnet(accepted.getInetAddress())) {
                    close(accepted);
                    continue;
                }
                workers.execute(() -> handle((SSLSocket) accepted));
            } catch (SocketException stopped) {
                break;
            } catch (Exception error) {
                if (running) Log.w("AndromedaGateway", "accept " + error.getClass().getSimpleName());
            }
        }
    }

    private void handle(SSLSocket socket) {
        SSLSocket peer = socket;
        try {
            peer.setSoTimeout(30000);
            peer.startHandshake();
            peer.setSoTimeout(30000);
            Request request = read(peer.getInputStream());
            if ("GET".equals(request.method) && "/v1/camera/stream".equals(request.path)) {
                streamCamera(peer, request);
                return;
            }
            write(peer.getOutputStream(), route(request));
        } catch (PayloadTooLarge large) {
            try {
                write(peer.getOutputStream(), Response.error(413, "too_large", "El mensaje supera 2 MB."));
            } catch (IOException ignored) { }
        } catch (Exception error) {
            // Nunca se registran cuerpos, tokens ni códigos de emparejamiento.
        } finally {
            close(peer);
        }
    }

    private Response route(Request request) {
        if (!"/v1/pair".equals(request.path)) {
            String contract = request.header("x-andromeda-contract");
            if (contract != null && !"1".equals(contract)) {
                return Response.error(426, "unsupported_version", "La app es mas nueva que la casa.");
            }
        }
        if ("POST".equals(request.method) && "/v1/pair".equals(request.path)) return pair(request);
        if ("GET".equals(request.method) && "/v1/health".equals(request.path)) return health(request);
        if ("GET".equals(request.method) && "/v1/capabilities".equals(request.path)) return capabilities(request);
        if ("POST".equals(request.method) && "/v1/command".equals(request.path)) return command(request);
        if ("GET".equals(request.method) && request.path.startsWith("/v1/speech/")
                && request.path.endsWith(".wav")) return speech(request);
        if ("GET".equals(request.method) && "/v1/messages/history".equals(request.path)) return messageHistory(request);
        if ("GET".equals(request.method) && request.path.matches("/v1/messages/[A-Za-z0-9_-]{8,64}/audio")) return messageAudio(request);
        if ("POST".equals(request.method) && request.path.matches("/v1/messages/[A-Za-z0-9_-]{8,64}/played")) return markMessagePlayed(request);
        if ("PUT".equals(request.method) && request.path.startsWith("/v1/messages/")) return putMessage(request);
        if ("PUT".equals(request.method) && request.path.startsWith("/v1/command/audio/")) return commandAudio(request);
        if ("GET".equals(request.method) && request.path.startsWith("/v1/messages/")) return messageStatus(request);
        if ("GET".equals(request.method) && "/v1/messages".equals(request.path)) return messages(request);
        return Response.error(404, "not_found", "Ruta desconocida.");
    }

    private Response pair(Request request) {
        try {
            JSONObject body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
            if (body.optInt("contract", 0) != 1) {
                return Response.error(426, "unsupported_version", "Contrato no soportado.");
            }
            String name = body.optString("device_name", "").trim();
            if (name.isEmpty() || name.length() > 40) {
                return Response.error(400, "invalid_request", "Nombre invalido.");
            }
            if (!pairingManager.verify(body.optString("code", ""))) {
                return Response.error(403, "forbidden", "Codigo invalido o vencido.");
            }
            DeviceStore.Device device = devices.create(name, Arrays.asList("voice", "messages", "camera"));
            JSONObject answer = new JSONObject()
                .put("device_id", device.id)
                .put("token", device.token)
                .put("capabilities", new JSONArray(device.capabilities))
                .put("server_time_ms", System.currentTimeMillis());
            return Response.json(200, answer);
        } catch (Exception error) {
            return Response.error(400, "invalid_request", "Peticion invalida.");
        }
    }

    private Response health(Request request) {
        if (authenticate(request) == null) {
            return Response.error(401, "unauthenticated", "Token invalido.");
        }
        try {
            return Response.json(200, new JSONObject()
                .put("status", "ok").put("contract", 1).put("gateway", "0.1.0")
                .put("resident", VoiceService.active ? "running" : "offline")
                .put("remote_voice", HomeGatewayConfig.remoteVoiceEnabled(context))
                .put("camera", cameraStatus())
                .put("messages", VoiceMessageStore.get(context).queuedCount()).put("tailscale", true)
                .put("server_time_ms", System.currentTimeMillis()));
        } catch (Exception error) {
            return Response.error(500, "internal", "Error interno.");
        }
    }

    private Response capabilities(Request request) {
        if (authenticate(request) == null) {
            return Response.error(401, "unauthenticated", "Token invalido.");
        }
        try {
            boolean voice = HomeGatewayConfig.remoteVoiceEnabled(context);
            return Response.json(200, new JSONObject()
                .put("contract", 1).put("voice", voice)
                .put("messages", HomeGatewayConfig.messagesEnabled(context))
                .put("camera", HomeGatewayConfig.cameraEnabled(context))
                .put("wav_replies", voice).put("max_command_chars", RemoteVoicePolicy.MAX_TEXT)
                .put("max_message_bytes", 2097152).put("max_message_ms", 60000)
                .put("camera_max_minutes", 10).put("camera_max_viewers", 1));
        } catch (Exception error) {
            return Response.error(500, "internal", "Error interno.");
        }
    }

    /** POST /v1/command: validated, authenticated, idempotent phone order. */
    private Response command(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("voice")) {
            return Response.error(403, "forbidden", "Este dispositivo no tiene voz.");
        }
        if (!HomeGatewayConfig.remoteVoiceEnabled(context)) {
            return Response.error(403, "forbidden", "Las ordenes remotas estan apagadas.");
        }
        if (!allow(device.id)) {
            return Response.error(429, "rate_limited", "Demasiadas ordenes; espera un momento.", true, 1000);
        }
        JSONObject body;
        try {
            body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
        } catch (Exception malformed) {
            return Response.error(400, "invalid_request", "Orden invalida.");
        }
        if (!validCommandBody(body)) return Response.error(400, "invalid_request", "Orden invalida.");
        String id = body.optString("id");
        String text = body.optString("text").trim();
        long clientTs = ((Number) body.opt("client_ts_ms")).longValue();
        boolean wantWav = !body.has("want_wav") || Boolean.TRUE.equals(body.opt("want_wav"));
        try {
            JSONObject frame = new JSONObject()
                .put("action", "remote_command").put("id", id).put("text", text)
                .put("client_ts_ms", clientTs).put("want_wav", wantWav);
            RemoteCommandBus.Reply reply = RemoteCommandBus.submit(frame, RemoteVoicePolicy.WAIT_MS);
            JSONObject payload;
            try {
                payload = new JSONObject(reply.body);
            } catch (Exception malformed) {
                return Response.error(500, "internal", "Respuesta remota invalida.");
            }
            if (reply.status != 200) return Response.json(reply.status, errorPayload(payload));
            return commandAnswer(payload, id, wantWav);
        } catch (Exception error) {
            return Response.error(500, "internal", "No se pudo preparar la orden.");
        }
    }

    /** PUT /v1/command/audio/{id}: a spoken order recorded by an authenticated phone. */
    private Response commandAudio(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("voice")) {
            return Response.error(403, "forbidden", "Este dispositivo no tiene voz.");
        }
        if (!HomeGatewayConfig.remoteVoiceEnabled(context)) {
            return Response.error(403, "forbidden", "Las ordenes remotas estan apagadas.");
        }
        if (!HomeGatewayConfig.voiceOrdersEnabled(context)) {
            return Response.error(403, "forbidden", "Las ordenes por voz estan suspendidas.");
        }
        if (!allow(device.id)) {
            return Response.error(429, "rate_limited", "Demasiadas ordenes; espera un momento.", true, 1000);
        }
        String type = request.header("content-type");
        if (type == null || !type.toLowerCase(Locale.US).startsWith("audio/wav")) {
            return Response.error(415, "unsupported_media", "Se esperaba audio/wav.");
        }
        String id = request.path.substring("/v1/command/audio/".length());
        if (!RemoteAudioPolicy.id(id)) {
            return Response.error(400, "invalid_request", "Orden invalida.");
        }
        long duration;
        try {
            duration = Long.parseLong(request.header("x-andromeda-duration-ms"));
        } catch (NumberFormatException missing) {
            return Response.error(400, "invalid_request", "Falta la duracion del audio.");
        }
        if (!RemoteAudioPolicy.duration(duration)) {
            return Response.error(400, "invalid_request", "Duracion fuera de rango.");
        }
        if (request.body.length == 0) return Response.error(400, "invalid_request", "Audio vacio.");
        if (request.body.length > RemoteAudioPolicy.MAX_BYTES) {
            return Response.error(413, "too_large", "El audio supera 2 MB.");
        }
        byte[] pcm;
        try {
            pcm = WavAudio.pcm(request.body);
        } catch (WavAudio.Fault fault) {
            return Response.error(400, "invalid_request", fault.getMessage());
        }
        RemoteAudioBus.Session session = RemoteAudioBus.session();
        if (session == null || !session.ready()) {
            return Response.error(503, "offline", "Andromeda no esta escuchando.", true, 3000);
        }
        if (session.busy() || !session.play(id, pcm, duration)) {
            return Response.error(409, "busy", "Andromeda esta ocupada; vuelve a intentarlo cuando termine.", true, 3000);
        }
        try {
            RemoteCommandBus.Reply reply = RemoteCommandBus.await(id,
                Math.min(45000L, duration + 20000L));
            JSONObject payload;
            try {
                payload = new JSONObject(reply.body);
            } catch (Exception malformed) {
                return Response.error(500, "internal", "Respuesta remota invalida.");
            }
            if (reply.status != 200) return Response.json(reply.status, errorPayload(payload));
            return commandAnswer(payload, id, true);
        } catch (Exception error) {
            return Response.error(500, "internal", "No se pudo preparar la orden.");
        }
    }

    private Response commandAnswer(JSONObject payload, String id, boolean wantWav) throws Exception {
        JSONObject answer = new JSONObject()
            .put("id", payload.optString("id", id))
            .put("say", payload.optString("say"))
            .put("say_wav", JSONObject.NULL)
            .put("receipts", payload.optJSONArray("receipts") == null
                ? new JSONArray() : payload.getJSONArray("receipts"))
            .put("origin", "remote")
            .put("server_time_ms", payload.optLong("server_time_ms", System.currentTimeMillis()));
        if (payload.has("heard")) answer.put("heard", payload.optString("heard"));
        // A scene answer (Santa, self-destruct) starts speaking several seconds
        // after the text arrives, so 5 s was not enough and the phone got no
        // audio for Christmas. Wait past the longest scene delay.
        if (wantWav && awaitSpeech(id, 20000L)) {
            answer.put("say_wav", "/v1/speech/" + id + ".wav");
        }
        return Response.json(200, answer);
    }

    /** GET /v1/speech/{command_id}.wav: the same voice the house just heard. */
    private Response speech(Request request) {
        if (authenticate(request) == null) {
            return Response.error(401, "unauthenticated", "Token invalido.");
        }
        String name = request.path.substring("/v1/speech/".length());
        if (!name.endsWith(".wav")) return Response.error(404, "not_found", "Audio no encontrado.");
        String id = name.substring(0, name.length() - 4);
        File file = RemoteSpeechCache.find(context, id);
        if (file == null) return Response.error(404, "not_found", "Ese audio ya no esta disponible.");
        try {
            return Response.binary(200, "audio/wav", Files.readAllBytes(file.toPath()));
        } catch (IOException error) {
            return Response.error(500, "internal", "No pude leer el audio.");
        }
    }

    private boolean awaitSpeech(String id, long maxMs) {
        long end = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < end) {
            if (RemoteSpeechCache.find(context, id) != null) return true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return RemoteSpeechCache.find(context, id) != null;
    }

    private static boolean validCommandBody(JSONObject body) {
        java.util.Set<String> allowed = new java.util.HashSet<>(
            Arrays.asList("id", "text", "client_ts_ms", "want_wav"));
        for (java.util.Iterator<String> keys = body.keys(); keys.hasNext(); ) {
            if (!allowed.contains(keys.next())) return false;
        }
        Object id = body.opt("id");
        Object text = body.opt("text");
        Object stamp = body.opt("client_ts_ms");
        if (!(id instanceof String) || !(text instanceof String)) return false;
        if (!(stamp instanceof Integer || stamp instanceof Long)) return false;
        if (body.has("want_wav") && !(body.opt("want_wav") instanceof Boolean)) return false;
        long clientTs = ((Number) stamp).longValue();
        return clientTs > 0 && clientTs <= 9_999_999_999_999L
            && RemoteVoicePolicy.command((String) id, (String) text, clientTs);
    }

    private boolean allow(String deviceId) {
        long now = System.currentTimeMillis();
        long[] window = rateLimits.computeIfAbsent(deviceId, key -> new long[]{now, 0});
        synchronized (window) {
            if (now - window[0] >= RATE_WINDOW_MS) {
                window[0] = now;
                window[1] = 0;
            }
            if (window[1] >= RATE_MAX) return false;
            window[1]++;
            return true;
        }
    }

    private static JSONObject errorPayload(JSONObject payload) throws Exception {
        JSONObject answer = new JSONObject()
            .put("error", payload.optString("error", "internal"))
            .put("message", payload.optString("message", "No se pudo completar la orden."))
            .put("retryable", payload.optBoolean("retryable", false));
        if (payload.has("retry_after_ms")) answer.put("retry_after_ms", payload.optLong("retry_after_ms"));
        return answer;
    }

    /** PUT /v1/messages/{id}: the phone leaves a voice note for the house. */
    private Response putMessage(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("messages")) {
            return Response.error(403, "forbidden", "Este dispositivo no puede dejar mensajes.");
        }
        if (!HomeGatewayConfig.messagesEnabled(context)) {
            return Response.error(403, "forbidden", "Los mensajes estan apagados.");
        }
        String type = VoiceMessagePolicy.type(request.header("content-type"));
        if (type == null) {
            return Response.error(415, "unsupported_media", "Formato de audio no soportado.");
        }
        long duration;
        try {
            duration = Long.parseLong(request.header("x-andromeda-duration-ms"));
        } catch (NumberFormatException missing) {
            return Response.error(400, "invalid_request", "Falta la duracion del mensaje.");
        }
        String id = request.path.substring("/v1/messages/".length());
        try {
            JSONObject status = VoiceMessageStore.get(context).put(
                device.id, device.name, id, type, duration, request.body);
            return Response.json(201, status);
        } catch (VoiceMessageStore.Fault fault) {
            return Response.error(fault.http, fault.code, fault.getMessage(),
                fault.http == 429, fault.retryAfterMs);
        }
    }

    /** GET /v1/messages/{id}: queued, playing, played, expired or failed. */
    private Response messageStatus(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("messages")) {
            return Response.error(403, "forbidden", "Este dispositivo no puede dejar mensajes.");
        }
        String id = request.path.substring("/v1/messages/".length());
        JSONObject status = VoiceMessageStore.get(context).status(device.id, id);
        if (status == null) return Response.error(404, "not_found", "Mensaje desconocido.");
        return Response.json(200, status);
    }

    /** GET /v1/messages: recent messages left by this device. */
    private Response messages(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("messages")) {
            return Response.error(403, "forbidden", "Este dispositivo no puede dejar mensajes.");
        }
        try {
            return Response.json(200, new JSONObject()
                .put("messages", VoiceMessageStore.get(context).list(device.id)));
        } catch (Exception error) {
            return Response.error(500, "internal", "No pude leer los mensajes.");
        }
    }

    /** GET /v1/messages/history: metadata for all family senders, no transcript/audio. */
    private Response messageHistory(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("messages")) {
            return Response.error(403, "forbidden", "Este dispositivo no puede consultar mensajes.");
        }
        try {
            return Response.json(200, new JSONObject()
                .put("messages", VoiceMessageStore.get(context).history()));
        } catch (Exception error) {
            return Response.error(500, "internal", "No pude leer el historial.");
        }
    }

    /** GET /v1/messages/{id}/audio: authenticated original audio within its 24 h TTL. */
    private Response messageAudio(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("messages")) {
            return Response.error(403, "forbidden", "Este dispositivo no puede descargar mensajes.");
        }
        String id = request.path.substring("/v1/messages/".length(), request.path.length() - "/audio".length());
        VoiceMessageStore store = VoiceMessageStore.get(context);
        try {
            byte[] audio = store.audio(id);
            String type = store.audioType(id);
            if (audio == null || type == null) return Response.error(404, "not_found", "Audio vencido o desconocido.");
            return Response.binary(200, type, audio);
        } catch (IOException failure) {
            return Response.error(500, "internal", "No pude leer el audio.");
        }
    }

    /** POST /v1/messages/{id}/played: the app reports completed playback, audio remains retained. */
    private Response markMessagePlayed(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("messages")) {
            return Response.error(403, "forbidden", "Este dispositivo no puede actualizar mensajes.");
        }
        String id = request.path.substring("/v1/messages/".length(), request.path.length() - "/played".length());
        JSONObject status = VoiceMessageStore.get(context).markPlayedFromClient(id);
        if (status == null) return Response.error(404, "not_found", "Mensaje vencido o desconocido.");
        return Response.json(200, status);
    }

    /**
     * GET /v1/camera/stream: handshake WebSocket y fotogramas JPEG en vivo.
     * Un solo espectador, maximo 10 minutos y nada se guarda en disco.
     */
    private void streamCamera(SSLSocket peer, Request request) {
        OutputStream out;
        DeviceStore.Device device;
        try {
            out = peer.getOutputStream();
            String contract = request.header("x-andromeda-contract");
            if (contract != null && !"1".equals(contract)) {
                write(out, Response.error(426, "unsupported_version", "La app es mas nueva que la casa."));
                return;
            }
            device = authenticate(request);
            if (device == null) {
                write(out, Response.error(401, "unauthenticated", "Token invalido."));
                return;
            }
            if (!device.capabilities.contains("camera")) {
                write(out, Response.error(403, "forbidden", "Este dispositivo no puede ver la camara."));
                return;
            }
            if (!HomeGatewayConfig.cameraEnabled(context)) {
                write(out, Response.error(403, "forbidden", "La camara esta apagada."));
                return;
            }
            if (!CameraStream.permitted(context)) {
                write(out, Response.error(403, "forbidden", "Falta el permiso de camara en el Moto."));
                return;
            }
            if (!"13".equals(request.header("sec-websocket-version"))
                    || request.header("sec-websocket-key") == null) {
                write(out, Response.error(400, "invalid_request", "Falta el saludo WebSocket."));
                return;
            }
            if (!cameraViewer.compareAndSet(false, true)) {
                write(out, Response.error(409, "camera_busy", "La camara ya tiene un espectador.", true, 5000));
                return;
            }
            // A previous stream may still be releasing the camera.
            for (int wait = 0; wait < 30 && cameraTearingDown.get(); wait++) Thread.sleep(100L);
        } catch (IOException error) {
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cameraViewer.set(false);
            return;
        }

        final OutputStream stream = out;
        CameraStream camera = new CameraStream();
        CountDownLatch ended = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<byte[]> latest = new java.util.concurrent.atomic.AtomicReference<>();
        cameraSocket = peer;
        cameraCancelled = false;
        try {
            // The client sends nothing after the handshake: waiting forever for
            // its close must not be mistaken for an idle failure.
            peer.setSoTimeout(0);
            // Live video: never let the OS queue seconds of stale frames.
            peer.setTcpNoDelay(true);
            peer.setSendBufferSize(64 * 1024);
            String accept = WebSocketHandshake.accept(request.header("sec-websocket-key"));
            stream.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            stream.flush();
            notifyCamera(device.name);

            long deadline = System.currentTimeMillis() + CAMERA_MAX_MS;
            Thread expiry = new Thread(() -> {
                try {
                    while (System.currentTimeMillis() < deadline && !cameraCancelled) Thread.sleep(500L);
                } catch (InterruptedException ignored) { }
                ended.countDown();
                close(peer);
            }, "andromeda-camera-expiry");
            expiry.setDaemon(true);
            expiry.start();

            // The client sends nothing after the handshake, so this read blocks
            // until it closes the socket. That is how the house frees the camera
            // immediately instead of waiting for a failed write.
            Thread reader = new Thread(() -> {
                try {
                    InputStream input = peer.getInputStream();
                    byte[] discard = new byte[256];
                    while (input.read(discard) >= 0) { }
                } catch (Exception ignored) { }
                ended.countDown();
                close(peer);
            }, "andromeda-camera-reader");
            reader.setDaemon(true);
            reader.start();

            boolean started = camera.start(context, latest::set, () -> {
                ended.countDown();
                close(peer);
            });
            if (!started) {
                webSocketClose(stream);
                ended.countDown();
            }
            // The camera thread only publishes the newest frame; writing to a
            // slow or closed phone never blocks the capture pipeline.
            while (ended.getCount() > 0) {
                byte[] jpeg = latest.getAndSet(null);
                if (jpeg == null) {
                    ended.await(15, java.util.concurrent.TimeUnit.MILLISECONDS);
                    continue;
                }
                try {
                    webSocketBinary(stream, jpeg);
                } catch (IOException closed) {
                    break;
                }
            }
        } catch (Exception error) {
            // Cierre del cliente o fallo de la camara: se limpia abajo.
        } finally {
            cameraSocket = null;
            cameraCancelled = false;
            // Free the viewer immediately; stopping the camera may wait on a
            // blocked write, so it is done off the worker thread.
            cameraViewer.set(false);
            cancelCameraNotice();
            cameraTearingDown.set(true);
            Thread cleanup = new Thread(() -> {
                try {
                    camera.stop();
                } finally {
                    cameraTearingDown.set(false);
                }
            }, "andromeda-camera-close");
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }

    /** The notification action on the Moto stops the live view immediately. */
    void stopCamera() {
        cameraCancelled = true;
        close(cameraSocket);
    }

    boolean cameraBusy() {
        return cameraViewer.get();
    }

    private static void webSocketBinary(OutputStream out, byte[] payload) throws IOException {
        out.write(0x82);
        int length = payload.length;
        if (length < 126) {
            out.write(length);
        } else if (length < 65536) {
            out.write(126);
            out.write(length >> 8);
            out.write(length & 0xff);
        } else {
            out.write(127);
            long size = length;
            for (int shift = 56; shift >= 0; shift -= 8) out.write((int) (size >> shift) & 0xff);
        }
        out.write(payload);
        out.flush();
    }

    private static void webSocketClose(OutputStream out) {
        try {
            out.write(new byte[]{(byte) 0x88, 0x00});
            out.flush();
        } catch (IOException ignored) { }
    }

    private void notifyCamera(String deviceName) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(
            "camera_live", "Camara en vivo", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(context, 2,
            new Intent(context, HomeGatewayService.class).setAction(HomeGatewayService.ACTION_STOP_CAMERA),
            PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(context, "camera_live")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Camara en vivo")
            .setContentText((deviceName.isEmpty() ? "Un telefono" : deviceName)
                + " esta viendo. Maximo 10 minutos.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop)
            .build();
        manager.notify(21, notice);
    }

    private void cancelCameraNotice() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(21);
    }

    private String cameraStatus() {
        if (cameraViewer.get()) return "busy";
        return HomeGatewayConfig.cameraEnabled(context) && CameraStream.permitted(context)
            ? "idle" : "unavailable";
    }

    private DeviceStore.Device authenticate(Request request) {
        String header = request.header("authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return devices.find(header.substring(7).trim());
    }

    private static boolean tailnet(InetAddress address) {
        byte[] value = address.getAddress();
        if (value.length == 4) {
            int first = value[0] & 0xff;
            int second = value[1] & 0xff;
            return first == 100 && second >= 64 && second <= 127;
        }
        if (value.length == 16) {
            return (value[0] & 0xff) == 0xfd && (value[1] & 0xff) == 0x7a
                && (value[2] & 0xff) == 0x11 && (value[3] & 0xff) == 0x5c
                && (value[4] & 0xff) == 0xa1 && (value[5] & 0xff) == 0xe0;
        }
        return false;
    }

    private static Request read(InputStream input) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (head.size() < MAX_HEADER) {
            int value = input.read();
            if (value < 0) throw new EOFException();
            head.write(value);
            if (value == '\r' && (state == 0 || state == 2)) state += 1;
            else if (value == '\n' && (state == 1 || state == 3)) state += 1;
            else state = 0;
            if (state == 4) break;
        }
        if (state != 4) throw new IOException("Header too large");

        String text = new String(head.toByteArray(), StandardCharsets.UTF_8);
        String[] lines = text.split("\r\n");
        String[] first = lines.length == 0 ? new String[0] : lines[0].split(" ");
        if (first.length < 2) throw new IOException("Bad request line");
        String method = first[0];
        String path = first[1];
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);

        Map<String, String> headers = new HashMap<>();
        for (int index = 1; index < lines.length; index++) {
            int colon = lines[index].indexOf(':');
            if (colon <= 0) continue;
            headers.put(lines[index].substring(0, colon).trim().toLowerCase(Locale.US),
                lines[index].substring(colon + 1).trim());
        }

        int length = 0;
        String contentLength = headers.get("content-length");
        if (contentLength != null) {
            try {
                length = Integer.parseInt(contentLength);
            } catch (NumberFormatException error) {
                throw new IOException("Bad content length");
            }
        }
        if (length < 0 || length > ("PUT".equals(method) && (path.startsWith("/v1/messages/")
                || path.startsWith("/v1/command/audio/"))
                ? MAX_MESSAGE_BODY : MAX_BODY)) throw new PayloadTooLarge();
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(body, offset, length - offset);
            if (count < 0) throw new EOFException();
            offset += count;
        }
        return new Request(method, path, headers, body);
    }

    private static void write(OutputStream output, Response response) throws IOException {
        byte[] body = response.data != null
            ? response.data : response.body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + response.status + " " + reason(response.status) + "\r\n"
            + "Content-Type: " + response.type + "\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Cache-Control: no-store\r\n"
            + "Connection: close\r\n\r\n";
        output.write(head.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 413: return "Payload Too Large";
            case 426: return "Upgrade Required";
            case 429: return "Too Many Requests";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return "Internal Server Error";
        }
    }

    private static void close(Closeable value) {
        if (value != null) {
            try {
                value.close();
            } catch (Exception ignored) { }
        }
    }

    /** The upload is bigger than this route allows; answered with 413. */
    private static final class PayloadTooLarge extends IOException {
        PayloadTooLarge() { super("Body too large"); }
    }

    private static final class Request {
        final String method;
        final String path;
        final Map<String, String> headers;
        final byte[] body;

        Request(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }
    }

    private static final class Response {
        final int status;
        final String body;
        final byte[] data;
        final String type;

        Response(int status, String body) {
            this(status, body, null, "application/json; charset=utf-8");
        }

        Response(int status, String body, byte[] data, String type) {
            this.status = status;
            this.body = body;
            this.data = data;
            this.type = type;
        }

        static Response json(int status, JSONObject body) {
            return new Response(status, body.toString());
        }

        static Response binary(int status, String type, byte[] data) {
            return new Response(status, null, data, type);
        }

        static Response error(int status, String code, String message) {
            return error(status, code, message, false, 0);
        }

        static Response error(int status, String code, String message,
                boolean retryable, long retryAfterMs) {
            JSONObject body = new JSONObject();
            try {
                body.put("error", code).put("message", message).put("retryable", retryable);
                if (retryAfterMs > 0) body.put("retry_after_ms", retryAfterMs);
            } catch (Exception ignored) { }
            return new Response(status, body.toString());
        }
    }
}
