package local.doja.voice;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.json.JSONException;

/** Private last-result receipt. Fixed numeric keys only; no UI/transcript diagnostics. */
final class VoiceHealthStore {
    static boolean interruption(Context context,SilenceListener.Handle handle) {
        if(handle==null) return false;
        java.util.Map<String,Object> value=handle.diagnostic.snapshot();
        long capture=((Number)value.get("capture_after_ms")).longValue();
        context.getSharedPreferences("voice_interrupt_health",Context.MODE_PRIVATE).edit().clear()
            .putInt("version",1).putInt("end",SilenceDiagnostics.End.valueOf((String)value.get("end")).ordinal())
            .putLong("capture_after_ms",capture).putLong("ready_after_ms",((Number)value.get("ready_after_ms")).longValue())
            .putLong("max_pcm_lag_ms",((Number)value.get("max_pcm_lag_ms")).longValue())
            .putLong("max_decode_ms",((Number)value.get("max_decode_ms")).longValue()).apply();
        return capture>=0;
    }
    static void finish(Context context,VoiceHealthPolicy health,int code,Throwable error) {
        VoiceHealthPolicy.Receipt value=health.finish(code,error,SystemClock.elapsedRealtime());
        context.getSharedPreferences("voice_health",Context.MODE_PRIVATE).edit().clear()
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
