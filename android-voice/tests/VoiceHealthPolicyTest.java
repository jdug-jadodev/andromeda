package local.doja.voice;

public final class VoiceHealthPolicyTest {
    static int checks;
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("Health policy "+checks); }
    public static void main(String[] args) {
        VoiceHealthPolicy policy=new VoiceHealthPolicy(); policy.begin(100000); policy.phase(VoiceHealthPolicy.SILENCE);
        Exception secret=new Exception("PRIVATE_FIXTURE_NEVER_STORE") { public String getMessage() { throw new AssertionError("Message accessed"); } };
        secret.setStackTrace(new StackTraceElement[]{new StackTraceElement("private.Unknown","private","PRIVATE_FIXTURE",2),
            new StackTraceElement("local.doja.voice.VoiceService","captureConversation","VoiceService.java",123)});
        VoiceHealthPolicy.Receipt failed=policy.finish(VoiceHealthPolicy.IO,secret,100100);
        check(failed.phase==VoiceHealthPolicy.SILENCE && failed.error==VoiceHealthPolicy.IO);
        check(failed.source==1 && failed.line==123 && !failed.microphone);
        check(failed.began==100000 && failed.ended==100100);
        policy.phase(VoiceHealthPolicy.STOP); policy.microphoneStarted();
        check(policy.finish(VoiceHealthPolicy.NONE,null,100200)==failed);
        VoiceHealthPolicy ready=new VoiceHealthPolicy(); ready.begin(200000); ready.phase(VoiceHealthPolicy.PREPARED);
        VoiceHealthPolicy.Receipt prepared=ready.finish(VoiceHealthPolicy.NONE,null,200100);
        check(prepared.phase==VoiceHealthPolicy.PREPARED && prepared.error==0 && !prepared.microphone);
        VoiceHealthPolicy capture=new VoiceHealthPolicy(); capture.begin(300000); capture.microphoneStarted(); capture.phase(VoiceHealthPolicy.PCM_CLOCK);
        check(capture.finish(VoiceHealthPolicy.IO,secret,300100).microphone);
        for(java.lang.reflect.Field field:VoiceHealthPolicy.Receipt.class.getDeclaredFields())
            check(field.getType()==int.class || field.getType()==long.class || field.getType()==boolean.class);
        System.out.println("Voice health: "+checks+" assertions passed; numeric terminal receipts, no messages/audio/device.");
    }
}
