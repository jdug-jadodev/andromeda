package local.doja.voice;

/** Pure protocol12 lifecycle limits. No capture, network, storage or permissions. */
final class ContinuousVoicePolicy {
    static final int PROTOCOL = 12, CONSENT_VERSION = 4;
    static final int ACTION_QUEUE = 24, CONTROL_QUEUE = 64;
    static final String VOICE = "continuous_voice_v1", RESET = "client_fresh_v1";
    static final long ACTION_WINDOW_MS = 300000, RESET_MS = 4000, STREAM_START_MS = 15000, STREAM_IDLE_MS = 5000;
    static boolean ready(int protocol, int seconds, String voice, String reset, long action, long start, long idle) {
        return ready(protocol,seconds,voice,reset,action,start,idle,false);
    }
    static boolean ready(int protocol, int seconds, String voice, String reset, long action, long start, long idle,boolean lifecycle) {
        return protocol == (lifecycle ? 13 : PROTOCOL) && seconds == 0 && VOICE.equals(voice) && RESET.equals(reset)
            && action == ACTION_WINDOW_MS && start == STREAM_START_MS && idle == STREAM_IDLE_MS;
    }
    static long actionEnd(long advertised, long speechEnd, long now) {
        if (speechEnd <= 0 || speechEnd > now + 100 || speechEnd > Long.MAX_VALUE - ACTION_WINDOW_MS
                || advertised <= now + 5000 || advertised > speechEnd + ACTION_WINDOW_MS || advertised > now + ACTION_WINDOW_MS)
            throw new IllegalArgumentException("Invalid current-turn action window");
        return advertised;
    }
    static boolean reason(String value) {
        return "dialogue_closed".equals(value) || "turn_checkpoint".equals(value) || "human_inactivity".equals(value) || "audio_resume".equals(value);
    }
    static boolean id(String value) { return value != null && value.matches("[0-9a-f]{32}"); }
    static boolean resetTimeoutApplies(boolean running, boolean awaiting, int current, int scheduled) {
        return running && awaiting && current == scheduled;
    }
    static final class Reset {
        final String oldSession, oldContext, nonce, reason;
        final long before;
        private boolean consumed;
        Reset(String session, String context, String nonce, String reason, long now) {
            if (!id(session) || !id(context) || !id(nonce) || !reason(reason) || now <= 0 || now > Long.MAX_VALUE-RESET_MS)
                throw new IllegalArgumentException("Invalid context handoff");
            oldSession=session; oldContext=context; this.nonce=nonce; this.reason=reason; before=now+RESET_MS;
        }
        void claim(String previousSession, String previousContext, String nonce, String newSession, String newContext, String proposedContext, long now) {
            if (consumed || now >= before || !oldSession.equals(previousSession) || !oldContext.equals(previousContext)
                    || !this.nonce.equals(nonce) || !id(newSession) || oldSession.equals(newSession)
                    || !id(newContext) || !newContext.equals(proposedContext)
                    || ("turn_checkpoint".equals(reason) ? !oldContext.equals(newContext) : oldContext.equals(newContext)))
                throw new IllegalArgumentException("Unrelated/stale context handoff");
            consumed=true;
        }
    }
}
