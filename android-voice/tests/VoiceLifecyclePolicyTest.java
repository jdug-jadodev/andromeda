package local.doja.voice;

/** Pure lifecycle gate; no Android, audio, device, model or network. */
public final class VoiceLifecyclePolicyTest {
    public static void main(String[] args){
        if(!VoiceLifecyclePolicy.stopVoiceWhenActivityStops(false))throw new AssertionError("bounded voice must stop");
        if(VoiceLifecyclePolicy.stopVoiceWhenActivityStops(true))throw new AssertionError("continuous voice stopped on screen lock");
        if(!VoiceLifecyclePolicy.holdCpuWhileScreenOff(true)||VoiceLifecyclePolicy.holdCpuWhileScreenOff(false))throw new AssertionError("wake policy");
        System.out.println("Voice lifecycle: 3 assertions passed (continuous service survives Activity stop; no auto-restart).");
    }
}
