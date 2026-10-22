package local.doja.voice;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Root bridge for the explicit user-facing activation button.
 * Fixed commands only; no arbitrary shell, network, transcript or audio data. */
final class AndromedaEngine {
    private static final String STATUS="/system/bin/sh /data/local/doha-voice/andromeda-resident-control.sh status";
    private static final String START="/data/adb/magisk/busybox nohup /system/bin/sh /data/local/doha-voice/start-andromeda-resident.sh continuous-voice guided alarms >/dev/null 2>&1 &";
    private static final long START_TIMEOUT_MS=120000L;
    private AndromedaEngine(){}

    static Result startAndWait(){
        try{
            String state=status();
            if("running".equals(state))return new Result(true,"running");
            if(!"preparing".equals(state)&&!"not_running".equals(state))return new Result(false,state);
            if("not_running".equals(state)&&!launch())return new Result(false,"launch_failed");
            long until=System.currentTimeMillis()+START_TIMEOUT_MS;
            while(System.currentTimeMillis()<until){
                Thread.sleep(750L); state=status();
                if("running".equals(state))return new Result(true,"running");
                if("not_running".equals(state))return new Result(false,"stopped_during_start");
            }
            return new Result(false,"startup_timeout");
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return new Result(false,"cancelled");}
        catch(Exception failure){return new Result(false,"bridge_unavailable");}
    }

    private static String status() throws Exception{
        String output=runRoot(STATUS,5000L);
        String state=new JSONObject(output).optString("state","");
        if("running".equals(state))return "running";
        if("preparing".equals(state))return "preparing";
        if("not_running".equals(state))return "not_running";
        return "unknown";
    }

    private static boolean launch() throws Exception{
        Process process=new ProcessBuilder("su","-c",START).redirectErrorStream(true).start();
        boolean ended=process.waitFor(5000L,TimeUnit.MILLISECONDS);
        if(!ended){process.destroy();return false;}
        return process.exitValue()==0;
    }

    private static String runRoot(String command,long timeoutMs) throws Exception{
        Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        boolean ended=process.waitFor(timeoutMs,TimeUnit.MILLISECONDS);
        if(!ended){process.destroy();throw new IllegalStateException("root_timeout");}
        String output=read(process.getInputStream());
        if(process.exitValue()!=0)throw new IllegalStateException("root_refused");
        return output;
    }

    private static String read(InputStream input) throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[512];int count;
        while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    static final class Result{
        final boolean ready;final String state;
        Result(boolean ready,String state){this.ready=ready;this.state=state;}
    }
}
