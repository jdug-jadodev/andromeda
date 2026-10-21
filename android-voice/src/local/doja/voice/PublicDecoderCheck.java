package local.doja.voice;

import android.content.*;
import android.app.ActivityManager;
import android.media.AudioManager;
import android.os.*;
import android.speech.tts.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.vosk.Recognizer;

/** Fixed public TTS -> native ASR benchmark. No microphone, playback, socket or action grant. */
final class PublicDecoderCheck {
    static final String[] PHRASES={"Silencio.","Andrómeda, silencio.","Andrómeda, no guardes silencio.","Ayer dije Andrómeda, silencio."};
    static boolean safe(Context context) {
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo(); context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
        Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temperature=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1);
        int mode=context.getSystemService(AudioManager.class).getMode();
        return memory.availMem/1024>=500000 && temperature>=0 && temperature<400
            && mode!=AudioManager.MODE_IN_CALL && mode!=AudioManager.MODE_IN_COMMUNICATION;
    }
    static boolean run(Context context,Handler main,BooleanSupplier wanted) throws Exception {
        long began=SystemClock.elapsedRealtime(),deadline=began+60000;
        TextToSpeech[] holder=new TextToSpeech[1]; CountDownLatch initialized=new CountDownLatch(1);
        AtomicInteger initStatus=new AtomicInteger(TextToSpeech.ERROR);
        AtomicReference<CountDownLatch> synthesized=new AtomicReference<>(); AtomicBoolean failed=new AtomicBoolean();
        AtomicBoolean closing=new AtomicBoolean(); AtomicReference<String> synthesisId=new AtomicReference<>("");
        long[] decode=new long[4],age=new long[4]; int[] matches=new int[4]; int passed=0,completed=0,error=0;
        BooleanSupplier allowed=() -> wanted.getAsBoolean() && SystemClock.elapsedRealtime()<deadline;
        try {
            if(!allowed.getAsBoolean() || !safe(context)) throw new IOException("Public decoder check not available");
            main.post(() -> {
                if(closing.get() || !wanted.getAsBoolean()) { initialized.countDown(); return; }
                holder[0]=new TextToSpeech(context,status -> {
                    initStatus.set(status); initialized.countDown();
                    if(closing.get() && holder[0]!=null) { holder[0].stop(); holder[0].shutdown(); }
                },"com.google.android.tts");
            });
            if(!initialized.await(10000,TimeUnit.MILLISECONDS) || initStatus.get()!=TextToSpeech.SUCCESS || !allowed.getAsBoolean())
                throw new IOException("Offline public synthesis not ready");
            TextToSpeech tts=holder[0]; List<Voice> voices=new ArrayList<>(tts.getVoices()); voices.sort(Comparator.comparing(Voice::getName));
            Voice selected=null;
            for(Voice voice:voices) if(!voice.isNetworkConnectionRequired() && "es".equals(voice.getLocale().getLanguage())) {
                if(selected==null) selected=voice;
                if("US".equals(voice.getLocale().getCountry())) { selected=voice; break; }
            }
            if(selected==null || tts.setVoice(selected)==TextToSpeech.ERROR) throw new IOException("No public offline Spanish synthesis");
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) {}
                public void onDone(String id) { CountDownLatch latch=synthesized.get(); if(id.equals(synthesisId.get()) && latch!=null) latch.countDown(); }
                public void onError(String id) { if(id.equals(synthesisId.get())) { failed.set(true); onDone(id); } }
            });
            try(SilenceModels.Lease lease=SilenceModels.acquire(context)) {
                for(int index=0;index<4;index++) {
                    if(!allowed.getAsBoolean() || !safe(context)) throw new IOException("Public decoder check stopped");
                    File output=File.createTempFile("andromeda-public-decoder-",".wav",context.getCacheDir());
                    byte[] pcm=null,wav=null,frame=new byte[640];
                    try {
                        CountDownLatch done=new CountDownLatch(1); synthesized.set(done); failed.set(false); synthesisId.set("public-decoder-"+index);
                        if(tts.synthesizeToFile(PHRASES[index],new Bundle(),output,synthesisId.get())==TextToSpeech.ERROR
                                || !done.await(Math.min(8000,Math.max(1,deadline-SystemClock.elapsedRealtime())),TimeUnit.MILLISECONDS)
                                || failed.get() || !allowed.getAsBoolean()) throw new IOException("Public synthesis incomplete");
                        if(Files.isSymbolicLink(output.toPath()) || output.length()>PublicSpeechPcm.MAX_WAV_BYTES) throw new IOException("Public synthesis size invalid");
                        wav=Files.readAllBytes(output.toPath()); pcm=PublicSpeechPcm.decode(wav); Arrays.fill(wav,(byte)0); wav=null;
                        try(Recognizer recognizer=new Recognizer(lease.value,16000)) {
                            recognizer.setWords(true); SilenceListener.warmDecoder(recognizer,allowed);
                            long replay=SystemClock.elapsedRealtime(),guard=replay;
                            for(int cursor=0;cursor<pcm.length+64000;cursor+=640) {
                                if(!allowed.getAsBoolean()) throw new IOException("Public replay stopped");
                                long now=SystemClock.elapsedRealtime();
                                if(now-guard>=500) { guard=now; if(!safe(context)) throw new IOException("Public replay guard"); }
                                long end=replay+(cursor+640)/32; SystemClock.sleep(Math.max(0,end-SystemClock.elapsedRealtime()));
                                Arrays.fill(frame,(byte)0); if(cursor<pcm.length) System.arraycopy(pcm,cursor,frame,0,Math.min(640,pcm.length-cursor));
                                long before=SystemClock.elapsedRealtime(); boolean endpoint=recognizer.acceptWaveForm(frame,frame.length);
                                decode[index]=Math.max(decode[index],SystemClock.elapsedRealtime()-before);
                                if(endpoint) {
                                    double confidence=SilenceListener.commandConfidence(recognizer.getResult());
                                    if(confidence>=.8) matches[index]++; // Measurement only: never a production ticket/callback/grant.
                                }
                                age[index]=Math.max(age[index],Math.max(0,SystemClock.elapsedRealtime()-end));
                            }
                        }
                        completed++;
                        if((index<2 ? matches[index]==1 : matches[index]==0) && age[index]<=InterruptionAudioClock.MAX_LAG_MS) passed++;
                    } finally {
                        Arrays.fill(frame,(byte)0); if(pcm!=null) Arrays.fill(pcm,(byte)0); if(wav!=null) Arrays.fill(wav,(byte)0);
                        if(!output.delete()) throw new IOException("Public synthesis cleanup unconfirmed");
                    }
                }
            }
            return passed==4;
        } catch(Exception failure) { error=VoiceHealthStore.kind(failure); throw failure; }
        finally {
            closing.set(true);
            if(holder[0]!=null) { holder[0].stop(); holder[0].shutdown(); }
            android.content.SharedPreferences.Editor result=context.getSharedPreferences("voice_public_decoder_health",Context.MODE_PRIVATE).edit().clear()
                .putInt("version",1).putLong("began_ms",began).putLong("ended_ms",SystemClock.elapsedRealtime())
                .putInt("frames_per_chunk",InterruptionModelProfile.FRAMES_PER_CHUNK).putInt("completed_cases",completed).putInt("passed_cases",passed).putInt("error",error);
            for(int index=0;index<4;index++) result.putLong("case"+index+"_decode_ms",decode[index]).putLong("case"+index+"_age_ms",age[index]).putInt("case"+index+"_matches",matches[index]);
            result.apply();
        }
    }
}
