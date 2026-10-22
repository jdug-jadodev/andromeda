package local.doja.voice;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.json.JSONException;

/** Private last-result receipt. Fixed numeric keys only; no UI/transcript diagnostics. */
final class VoiceHealthStore {
    static void reminderRead(Context context,ReminderListPolicy.Receipt receipt,long readMs) {
        context.getSharedPreferences("voice_reminder_list_health",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("status",receipt.status.equals("READ")?1:2).putLong("total",receipt.total)
            .putInt("shown",receipt.shown).putLong("read_ms",Math.max(0,readMs)).putLong("at_ms",SystemClock.elapsedRealtime()).apply();
    }
    static void turnFailure(Context context,int reason) {
        context.getSharedPreferences("voice_turn_health",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("reason",reason).putLong("at_ms",SystemClock.elapsedRealtime())
            .putBoolean("recovered_before_execution",true).apply();
    }
    static void volume(Context context,int requested,PhoneVolumePolicy.Receipt receipt) {
        context.getSharedPreferences("voice_volume_health",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("requested_percent",requested).putInt("actual_percent",receipt.actual)
            .putInt("status",receipt.status.ordinal()).putLong("at_ms",SystemClock.elapsedRealtime()).apply();
    }
    static void outputReadiness(Context context,int completed,long decode,long lag,long ready,int end,boolean started,boolean done) {
        context.getSharedPreferences("voice_output_health",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("completed_windows",completed).putInt("target_windows",3)
            .putLong("max_decode_ms",decode).putLong("max_pcm_lag_ms",lag).putLong("max_ready_after_ms",ready)
            .putInt("last_end",end).putBoolean("tts_started",started).putBoolean("tts_done",done).apply();
    }
    static void decoderWarmup(Context context,DecoderWarmup.Result value) {
        context.getSharedPreferences("voice_decoder_health",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("public_zero_chunks",DecoderWarmup.CHUNKS)
            .putLong("began_ms",value.began).putLong("duration_ms",value.duration)
            .putLong("max_chunk_ms",value.maxChunk).putLong("max_tail_chunk_ms",value.maxTail).apply();
    }
    static boolean interruption(Context context,SilenceListener.Handle handle) {
        if(handle==null) return false;
        java.util.Map<String,Object> value=handle.diagnostic.snapshot();
        long capture=((Number)value.get("capture_after_ms")).longValue();
        context.getSharedPreferences("voice_interrupt_health",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("end",SilenceDiagnostics.End.valueOf((String)value.get("end")).ordinal())
            .putLong("capture_after_ms",capture).putLong("ready_after_ms",((Number)value.get("ready_after_ms")).longValue())
            .putLong("max_pcm_lag_ms",((Number)value.get("max_pcm_lag_ms")).longValue())
            .putLong("max_decode_ms",((Number)value.get("max_decode_ms")).longValue()).apply();
        android.content.SharedPreferences.Editor detail=context.getSharedPreferences("voice_interrupt_detail",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("end",SilenceDiagnostics.End.valueOf((String)value.get("end")).ordinal());
        for(String key:new String[]{"began_ms","ended_ms","max_processed_age_ms","endpoints","rejected_segments","callbacks_sent","detection_to_callback_ms","stop_return_accepted","callback_to_stop_ms"})
            detail.putLong(key,((Number)value.get(key)).longValue());
        detail.apply();
        return capture>=0;
    }
    static void finish(Context context,VoiceHealthPolicy health,int code,Throwable error) {
        VoiceHealthPolicy.Receipt value=health.finish(code,error,SystemClock.elapsedRealtime());
        store(context,"voice_health",value);
        // Later taps on Activar must not erase the first useful failure with a
        // ConnectException caused by the already-closed backend.
        if(value.error!=VoiceHealthPolicy.NONE && value.phase!=VoiceHealthPolicy.CONNECT) store(context,"voice_failure_origin",value);
    }
    private static void store(Context context,String name,VoiceHealthPolicy.Receipt value) {
        context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("phase",value.phase).putInt("error",value.error)
            .putInt("source",value.source).putInt("line",value.line)
            .putLong("began_ms",value.began).putLong("ended_ms",value.ended)
            .putBoolean("microphone_started",value.microphone).putBoolean("human_content_recorded",false).apply();
    }
    static int kind(Throwable error) {
        return error instanceof TimeoutException?VoiceHealthPolicy.TIMEOUT:error instanceof IOException?VoiceHealthPolicy.IO:
            error instanceof JSONException?VoiceHealthPolicy.JSON:error instanceof SecurityException?VoiceHealthPolicy.SECURITY:
            error instanceof IllegalStateException?VoiceHealthPolicy.STATE:error instanceof IllegalArgumentException?VoiceHealthPolicy.ARGUMENT:VoiceHealthPolicy.OTHER;
    }
}
