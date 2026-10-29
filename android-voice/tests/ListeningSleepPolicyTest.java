package local.doja.voice;

public final class ListeningSleepPolicyTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Listening sleep assertion " + checks); }
    public static void main(String[] args) {
        check(ListeningSleepPolicy.DROWSY_AFTER_MS == 15000 && ListeningSleepPolicy.ASLEEP_AFTER_MS == 25000
            && ListeningSleepPolicy.CLOSE_AFTER_MS == 30000);
        ListeningSleepPolicy policy = new ListeningSleepPolicy();
        // Awake for 15 s, drowsy for 10 s, asleep for the last 5 s of the 30 s window.
        check(policy.present(FaceState.LISTENING, 1000) == FaceState.LISTENING);
        check(policy.present(FaceState.LISTENING, 15999) == FaceState.LISTENING);
        check(policy.present(FaceState.LISTENING, 16000) == FaceState.DROWSY);
        check(policy.present(FaceState.LISTENING, 25999) == FaceState.DROWSY);
        check(policy.present(FaceState.LISTENING, 26000) == FaceState.ASLEEP);
        check(policy.present(FaceState.LISTENING, 30999) == FaceState.ASLEEP);
        // Any other service state restarts the silence clock and is shown unchanged.
        check(policy.present(FaceState.THINKING, 31000) == FaceState.THINKING);
        check(policy.present(FaceState.LISTENING, 32000) == FaceState.LISTENING);
        check(policy.present(FaceState.LISTENING, 46999) == FaceState.LISTENING);
        check(policy.present(FaceState.LISTENING, 47000) == FaceState.DROWSY);
        // A clock that goes backwards never leaves her asleep.
        check(policy.present(FaceState.LISTENING, 100) == FaceState.LISTENING);
        // Only a voice (thinking/speaking) startles her, and only from drowsy or asleep.
        check(ListeningSleepPolicy.startles(FaceState.DROWSY, FaceState.THINKING));
        check(ListeningSleepPolicy.startles(FaceState.ASLEEP, FaceState.SPEAKING));
        check(!ListeningSleepPolicy.startles(FaceState.LISTENING, FaceState.THINKING));
        check(!ListeningSleepPolicy.startles(FaceState.ASLEEP, FaceState.READY));
        check(!ListeningSleepPolicy.startles(FaceState.ASLEEP, FaceState.MUTED));
        check(!ListeningSleepPolicy.startles(FaceState.DROWSY, FaceState.LISTENING));
        // Drowsy and asleep keep the face on screen (only idle, ready and muted show the clock).
        check(UiPolicy.showsFace(FaceState.DROWSY) && UiPolicy.showsFace(FaceState.ASLEEP));
        check(FaceState.parse("ASLEEP") == FaceState.ASLEEP && FaceState.parse("DROWSY") == FaceState.DROWSY);
        System.out.println("ListeningSleepPolicyTest ok checks=" + checks);
    }
}
