package local.doja.voice;

import android.Manifest;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.json.*;
import org.vosk.Recognizer;

/** Output-only offline interruption. No LFM, network, persisted audio or general actions. */
final class SilenceListener implements AutoCloseable {
    private static final AtomicInteger microphones = new AtomicInteger();
    private final Context context;
    private final Runnable changed;
    private final SilenceDiagnostics.Owner owner;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(2));
    private final CountDownLatch cleaned = new CountDownLatch(1);
    private final SilenceWindowPolicy policy = new SilenceWindowPolicy();
    private final AtomicInteger jobs = new AtomicInteger();
    private final Object micOwner = new Object();
    private final Object captureLock = new Object();
    private volatile Handle activeHandle;
    private SilenceModels.Lease lease;
    private Recognizer preparedDecoder;
    private Future<?> preparation;
    private volatile boolean closed, ready, preparationFailed;
    volatile String error = "";
    volatile boolean echoCancellation;
    static final class Handle {
        final SilenceWindowPolicy.Ticket ticket;
        final SilenceDiagnostics.Window diagnostic;
        final BooleanSupplier wanted;
        final Consumer<Handle> ready;
        final Runnable unavailable, command;
        final Consumer<String> alarmCommand;
        final AtomicBoolean stopRequested=new AtomicBoolean();
        volatile AudioRecord recorder;
        Handle(SilenceWindowPolicy.Ticket ticket, SilenceDiagnostics.Window diagnostic, BooleanSupplier wanted,
                Consumer<Handle> ready,Runnable unavailable,Runnable command,Consumer<String> alarmCommand) {
            this.ticket=ticket; this.diagnostic=diagnostic; this.wanted=wanted; this.ready=ready; this.unavailable=unavailable;
            this.command=command; this.alarmCommand=alarmCommand;
        }
    }
    SilenceListener(Context context, Runnable changed) { this(context, changed, SilenceDiagnostics.Owner.SYNTHETIC); }
    SilenceListener(Context context, Runnable changed, SilenceDiagnostics.Owner owner) {
        this.context = context.getApplicationContext(); this.changed = changed; this.owner = owner;
    }
    static boolean recording() { return microphones.get() > 0; }
    boolean busy() { return jobs.get() > 0; }
    boolean ready() { return ready && !closed; }
    private void notifyState() {
        main.post(() -> {
            changed.run();
            context.sendBroadcast(new Intent(VoiceService.STATE).setPackage(context.getPackageName()));
        });
    }
    private boolean safe(long availableKiB, int maxTemperature) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false;
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temperature = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int mode = context.getSystemService(AudioManager.class).getMode();
        return memory.availMem / 1024 >= availableKiB && temperature >= 0 && temperature < maxTemperature
            && mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_IN_COMMUNICATION;
    }
    synchronized void prepare() {
        if (closed || ready || preparationFailed || preparation != null && !preparation.isDone()) return;
        preparation = worker.submit(() -> {
            Recognizer decoder=null;
            try {
                if (!safe(lease==null?1200000:500000,lease==null?380:400)) throw new IllegalStateException("Guardia de recursos/permisos");
                if(lease==null) lease = SilenceModels.acquire(context);
                decoder=new Recognizer(lease.value,16000); decoder.setWords(true);
                DecoderWarmup.Result result=warmDecoder(decoder,() -> !closed);
                VoiceHealthStore.decoderWarmup(context,result);
                synchronized(SilenceListener.this) {
                    if(!closed) { preparedDecoder=decoder; decoder=null; ready=true; }
                }
            } catch (Exception | LinkageError failure) { preparationFailed=true; error = "Interrupción local no disponible"; }
            finally { if(decoder!=null) decoder.close(); }
            notifyState();
        });
    }
    static DecoderWarmup.Result warmDecoder(Recognizer decoder,BooleanSupplier wanted) throws Exception {
        return DecoderWarmup.prepare(new DecoderWarmup.Decoder() {
            public boolean accept(byte[] pcm,int length) { return decoder.acceptWaveForm(pcm,length); }
            public boolean empty(boolean endpoint) throws JSONException {
                return new JSONObject(decoder.getResult()).getString("text").isEmpty();
            }
        },SystemClock::elapsedRealtime,wanted);
    }
    boolean awaitReady(long deadline) {
        prepare();
        try { preparation.get(Math.max(1, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS); }
        catch (Exception failure) { return false; }
        return ready();
    }
    Handle arm(String id,long deadline,long startBefore,BooleanSupplier wanted,Runnable ready,Runnable unavailable,Runnable command) {
        return arm(id,deadline,startBefore,wanted,ignored -> ready.run(),unavailable,command,null);
    }
    Handle armAlarm(String id,long deadline,long startBefore,BooleanSupplier wanted,Runnable ready,Runnable unavailable,Consumer<String> command) {
        return arm(id,deadline,startBefore,wanted,ignored -> ready.run(),unavailable,() -> {},command);
    }
    private synchronized Handle arm(String id,long deadline,long startBefore,BooleanSupplier wanted,Consumer<Handle> ready,Runnable unavailable,Runnable command,Consumer<String> alarmCommand) {
        if(closed || jobs.get()>=2) return null;
        long now=SystemClock.elapsedRealtime();
        SilenceWindowPolicy.Ticket ticket=policy.arm(id,deadline,startBefore,now,owner==SilenceDiagnostics.Owner.SPEECH ? 60000 : 30000);
        if(ticket==null) return null;
        Handle handle=new Handle(ticket,SilenceDiagnostics.begin(owner,now),wanted,ready,unavailable,command,alarmCommand);
        stopCaptureAsync(activeHandle); activeHandle=handle;
        prepare(); jobs.incrementAndGet();
        try { worker.execute(() -> listen(handle)); }
        catch(RejectedExecutionException stopping) { jobs.decrementAndGet(); policy.end(ticket); handle.diagnostic.finish(SilenceDiagnostics.End.CLOSED,now); return null; }
        return handle;
    }
    boolean outputStarted(Handle handle) {
        if(handle==null || !handle.wanted.getAsBoolean() || !policy.start(handle.ticket,SystemClock.elapsedRealtime())) return false;
        handle.diagnostic.outputStarted(SystemClock.elapsedRealtime()); return true;
    }
    private void stopCaptureAsync(Handle handle) {
        if(handle==null || !handle.stopRequested.compareAndSet(false,true)) return;
        Thread stop=new Thread(() -> {
            synchronized(captureLock) {
                try { if(handle.recorder!=null) handle.recorder.stop(); } catch(RuntimeException ignored) { /* Worker owns release and reports final state. */ }
            }
        },"andromeda-interrupt-capture-stop");
        stop.setDaemon(true); stop.start();
    }
    synchronized void end(Handle handle) {
        if(handle!=null) { policy.end(handle.ticket); stopCaptureAsync(handle); if(activeHandle==handle) activeHandle=null; }
    }
    private void unavailable(Handle handle) {
        if(policy.fail(handle.ticket)) main.post(() -> {
            if(!closed && handle.wanted.getAsBoolean() && policy.claimFailure(handle.ticket)) handle.unavailable.run();
        });
    }
    static double commandConfidence(String raw) throws JSONException {
        return commandConfidence(raw,false);
    }
    static double commandConfidence(String raw,boolean alarm) throws JSONException {
        if(raw==null || raw.length()>16384) return -1;
        JSONObject result = new JSONObject(raw); JSONArray words = result.optJSONArray("result");
        String text = result.optString("text");
        if(words==null || words.length()<1 || words.length()>(alarm?8:2)) return -1;
        String[] recognized=new String[words.length()]; double[] scores=new double[words.length()],starts=new double[words.length()],ends=new double[words.length()];
        for (int i = 0; i < words.length(); i++) {
            JSONObject word=words.getJSONObject(i); recognized[i]=word.getString("word"); scores[i]=word.getDouble("conf");
            starts[i]=word.getDouble("start"); ends[i]=word.getDouble("end");
        }
        return SilenceRecognition.confidence(text,recognized,scores,starts,ends,alarm);
    }
    private void listen(Handle handle) {
        SilenceWindowPolicy.Ticket ticket=handle.ticket; SilenceDiagnostics.Window diagnostic=handle.diagnostic;
        AudioRecord recorder = null; AcousticEchoCanceler echo = null; boolean started = false, acquired = false, counted=false;
        SilenceDiagnostics.End reason = SilenceDiagnostics.End.OUTPUT_ENDED;
        byte[] pcm=new byte[640]; boolean announced=false;
        Recognizer claimed=null;
        try {
            if (!ready()) { reason = SilenceDiagnostics.End.NOT_READY; return; }
            if (!safe(500000, 400)) { reason = SilenceDiagnostics.End.GUARD; return; }
            synchronized(this) { claimed=preparedDecoder; preparedDecoder=null; ready=false; }
            if(claimed==null) { reason=SilenceDiagnostics.End.NOT_READY; return; }
            while (!closed && policy.preparing(ticket,SystemClock.elapsedRealtime()) && handle.wanted.getAsBoolean()) {
                if (AudioInputGate.acquire(micOwner)) { acquired = true; break; }
                SystemClock.sleep(10);
            }
            if (!acquired || closed || !policy.preparing(ticket,SystemClock.elapsedRealtime()) || !handle.wanted.getAsBoolean()) { reason=SilenceDiagnostics.End.WAIT_MIC; return; }
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            // The Moto config applies its AEC/NS preprocessing to this source.
            // Do not change global AudioManager mode, speaker route or volume.
            int source = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
            recorder = new AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 6400));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord no disponible");
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echo = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                    echoCancellation = echo != null && echo.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS && echo.getEnabled();
                } catch (RuntimeException unsupported) { echoCancellation = false; }
            }
            try (Recognizer recognizer = claimed) {
                claimed=null;
                synchronized(captureLock) {
                    if(closed || handle.stopRequested.get() || !policy.current(ticket,SystemClock.elapsedRealtime())) return;
                    handle.recorder=recorder; recorder.startRecording(); started = true;
                }
                if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Recording not active");
                diagnostic.capture(SystemClock.elapsedRealtime(), source, AcousticEchoCanceler.isAvailable(), echoCancellation,
                    context.getSystemService(AudioManager.class).isMicrophoneMute());
                microphones.incrementAndGet(); counted=true; notifyState(); long checked = 0;
                InterruptionAudioClock clock=new InterruptionAudioClock(SystemClock.elapsedRealtime()); AudioTimestamp timestamp=new AudioTimestamp();
                while (!closed && policy.current(ticket,SystemClock.elapsedRealtime()) && handle.wanted.getAsBoolean()) {
                    long now = SystemClock.elapsedRealtime();
                    if(!clock.alive(now)) { reason=SilenceDiagnostics.End.STALE_AUDIO; break; }
                    if(!announced && now>=ticket.readyBefore) { reason=SilenceDiagnostics.End.ARM_TIMEOUT; break; }
                    if (now - checked >= 500) {
                        checked = now;
                        if (!safe(500000, 400)) { reason = SilenceDiagnostics.End.GUARD; break; }
                        if(context.getSystemService(AudioManager.class).isMicrophoneMute()) { reason=SilenceDiagnostics.End.CLIENT_SILENCED; break; }
                        try {
                            AudioRecordingConfiguration configuration = recorder.getActiveRecordingConfiguration();
                            diagnostic.silenced(configuration == null ? -1 : configuration.isClientSilenced() ? 1 : 0);
                            if(configuration!=null && configuration.isClientSilenced()) { reason=SilenceDiagnostics.End.CLIENT_SILENCED; break; }
                        } catch (RuntimeException unavailable) { diagnostic.silenced(-1); } // Diagnosis must not disable the command.
                    }
                    int count = recorder.read(pcm, 0, pcm.length, AudioRecord.READ_NON_BLOCKING);
                    if (count < 0 || count % 2 != 0) throw new IllegalStateException("Captura de interrupción fallida");
                    if (count == 0) { SystemClock.sleep(10); continue; }
                    diagnostic.frame(pcm, count);
                    long end;
                    try {
                        boolean stamped=recorder.getTimestamp(timestamp,AudioTimestamp.TIMEBASE_BOOTTIME)==AudioRecord.SUCCESS;
                        end=clock.observe(count,stamped,timestamp.framePosition,timestamp.nanoTime/1000000L,SystemClock.elapsedRealtime());
                    } catch(IllegalArgumentException stale) { reason=SilenceDiagnostics.End.STALE_AUDIO; break; }
                    if(end<0) continue;
                    diagnostic.lag(Math.max(0,SystemClock.elapsedRealtime()-end));
                    if(!announced) {
                        if(!policy.ready(ticket,SystemClock.elapsedRealtime())) { reason=SilenceDiagnostics.End.ARM_TIMEOUT; break; }
                        announced=true; diagnostic.ready(SystemClock.elapsedRealtime());
                        main.post(() -> { if(!closed && policy.current(ticket,SystemClock.elapsedRealtime()) && handle.wanted.getAsBoolean()) handle.ready.accept(handle); });
                    }
                    if(!policy.playing(ticket,SystemClock.elapsedRealtime()) || !InterruptionAudioClock.afterStart(end,count,policy.started(ticket))) continue;
                    long decode=SystemClock.elapsedRealtime(); boolean endpoint=recognizer.acceptWaveForm(pcm, count);
                    diagnostic.decoded(SystemClock.elapsedRealtime()-decode);
                    diagnostic.processed(Math.max(0,SystemClock.elapsedRealtime()-end));
                    if(SystemClock.elapsedRealtime()-end>InterruptionAudioClock.MAX_LAG_MS) { reason=SilenceDiagnostics.End.STALE_AUDIO; break; }
                    if (endpoint) {
                        String result = recognizer.getResult();
                        double confidence = commandConfidence(result,handle.alarmCommand!=null);
                        JSONObject parsed = new JSONObject(result);
                        String text=parsed.optString("text");
                        boolean explicit=handle.alarmCommand==null ? SilenceWindowPolicy.isCommand(text) : AlarmOccurrencePolicy.command(text)!=null;
                        diagnostic.processed(Math.max(0,SystemClock.elapsedRealtime()-end));
                        if(SystemClock.elapsedRealtime()-end>InterruptionAudioClock.MAX_LAG_MS) { reason=SilenceDiagnostics.End.STALE_AUDIO; break; }
                        boolean accepted = policy.accept(ticket,explicit,confidence,SystemClock.elapsedRealtime());
                        diagnostic.endpoint(parsed.optJSONArray("result") == null ? 0 : parsed.getJSONArray("result").length(), accepted);
                        if (accepted) {
                            reason = SilenceDiagnostics.End.DETECTED;
                            diagnostic.detected(SystemClock.elapsedRealtime());
                            diagnostic.finish(reason,SystemClock.elapsedRealtime());
                            main.post(() -> {
                                if (!closed && handle.wanted.getAsBoolean() && policy.claimCallback(ticket,SystemClock.elapsedRealtime())) {
                                    diagnostic.callback(SystemClock.elapsedRealtime()); if(handle.alarmCommand==null) handle.command.run(); else handle.alarmCommand.accept(text);
                                } else if(!closed && handle.wanted.getAsBoolean()) unavailable(handle);
                            });
                            break;
                        }
                    }
                }
            }
        } catch (Exception | LinkageError failure) { reason = SilenceDiagnostics.End.ERROR; error = "Escucha de silencio detenida"; }
        finally {
            if (recorder != null) {
                synchronized(captureLock) {
                    try { if (started) recorder.stop(); } catch (Exception ignored) {}
                    try { recorder.release(); } catch (Exception ignored) {}
                    handle.recorder=null;
                }
            }
            if (echo != null) try { echo.release(); } catch (Exception ignored) {}
            if(claimed!=null) claimed.close(); // Early cancellation before capture never retains a decoder.
            // startRecording may succeed before a later initialization check throws.
            if (counted) microphones.decrementAndGet();
            if (acquired) AudioInputGate.release(micOwner);
            if (reason == SilenceDiagnostics.End.OUTPUT_ENDED) {
                if (closed) reason = SilenceDiagnostics.End.CLOSED;
                else if (SystemClock.elapsedRealtime() >= ticket.expires) reason = SilenceDiagnostics.End.EXPIRED;
            }
            java.util.Arrays.fill(pcm,(byte)0);
            diagnostic.finish(reason, SystemClock.elapsedRealtime());
            VoiceHealthStore.interruption(context,handle);
            if(reason!=SilenceDiagnostics.End.DETECTED && reason!=SilenceDiagnostics.End.CLOSED && reason!=SilenceDiagnostics.End.OUTPUT_ENDED) {
                error="Escucha de interrupción no disponible"; unavailable(handle);
            }
            jobs.decrementAndGet(); notifyState();
            // A fresh, public-only decoder for the next output. Never reset or
            // pool the one that saw human audio; the single worker closes it first.
            if(!closed && (reason==SilenceDiagnostics.End.OUTPUT_ENDED || reason==SilenceDiagnostics.End.DETECTED)) prepare();
        }
    }
    synchronized void end() { policy.end(); stopCaptureAsync(activeHandle); activeHandle=null; }
    public synchronized void close() {
        if (closed) return;
        closed = true; ready = false; policy.close(); end();
        worker.shutdown();
        Thread cleanup=new Thread(() -> {
            try { while(!worker.awaitTermination(1,TimeUnit.SECONDS)) { /* No restart; wait for owned native work to release. */ } }
            catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            finally { if(worker.isTerminated()) { if(preparedDecoder!=null) { preparedDecoder.close(); preparedDecoder=null; } if(lease!=null) { lease.close(); lease=null; } cleaned.countDown(); } }
        },"andromeda-interrupt-close");
        cleanup.setDaemon(true); cleanup.start();
    }
    boolean awaitClosed(long milliseconds) throws InterruptedException { return closed && cleaned.await(milliseconds,TimeUnit.MILLISECONDS); }
}
