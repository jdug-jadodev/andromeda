package local.doja.voice;

/** Activity lifecycle boundary: explicit continuous mode owns its foreground service. */
final class VoiceLifecyclePolicy {
    private VoiceLifecyclePolicy(){}
    static boolean stopVoiceWhenActivityStops(boolean continuousVoice){return !continuousVoice;}
    static boolean holdCpuWhileScreenOff(boolean continuousVoice){return continuousVoice;}
}
