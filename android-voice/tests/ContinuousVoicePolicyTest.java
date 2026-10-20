package local.doja.voice;

public final class ContinuousVoicePolicyTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Continuous policy assertion " + checks); }
    interface Action { void run(); }
    static void reject(Action action) { try { action.run(); throw new AssertionError("Unsafe continuous state accepted"); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; } }
    public static void main(String[] args) {
        check(ContinuousVoicePolicy.ready(12,0,"continuous_voice_v1","client_fresh_v1",300000,15000,5000));
        check(!ContinuousVoicePolicy.ready(11,300,"continuous_voice_v1","client_fresh_v1",300000,15000,5000));
        check(!ContinuousVoicePolicy.ready(12,0,"continuous_voice_v1","other",300000,15000,5000));
        check(ContinuousVoicePolicy.resetTimeoutApplies(true,true,2,2));
        check(!ContinuousVoicePolicy.resetTimeoutApplies(true,true,3,2));
        check(!ContinuousVoicePolicy.resetTimeoutApplies(true,false,2,2));
        check(!ContinuousVoicePolicy.resetTimeoutApplies(false,true,2,2));
        check(ContinuousVoicePolicy.actionEnd(399900,99900,100000)==399900);
        reject(() -> ContinuousVoicePolicy.actionEnd(500000,99900,100000));
        reject(() -> ContinuousVoicePolicy.actionEnd(104000,99900,100000));
        String a="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",b="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",c="cccccccccccccccccccccccccccccccc",d="dddddddddddddddddddddddddddddddd";
        ContinuousVoicePolicy.Reset reset=new ContinuousVoicePolicy.Reset(a,b,c,"dialogue_closed",100000);
        reject(() -> reset.claim(a,b,d,d,d,d,100100));
        reject(() -> reset.claim(a,b,c,d,b,b,100100));
        reset.claim(a,b,c,d,d,d,100100); checks++;
        reject(() -> reset.claim(a,b,c,d,d,d,100100));
        ContinuousVoicePolicy.Reset expired=new ContinuousVoicePolicy.Reset(a,b,c,"turn_checkpoint",100000);
        reject(() -> expired.claim(a,b,c,d,b,b,104000));
        ConversationTurnPolicy turn=new ConversationTurnPolicy();
        check(turn.begin(a,20,99900,159900,100000,60000)); reject(turn::resetScope);
        check(turn.acceptReply(a,20,99900,159900,100100)); check(turn.acceptStart(a,100200)); check(turn.finishSpeech(a,true));
        turn.resetScope(); check(turn.begin(b,1,100900,160900,101000,60000)); check(!turn.acceptStart(a,101100));
        ConversationMemory memory=new ConversationMemory(); String oldest="";
        for (int index=0; index<4; index++) {
            long at=100000+index*1000; String id=memory.create(at); if(index==0) oldest=id;
            ConversationMemory.Lease lease=memory.acquireExact(id,false,at);
            String human=String.format("%032x",index+1); check(memory.human(lease,human,"public marker "+index,at,at)); check(memory.end(lease,human,at+100));
        }
        check(memory.catalog(104000).size()==4); check(memory.catalogForContinuous(104000).size()==3);
        for (ConversationMemory.Snapshot item:memory.catalog(104000)) check(!item.conversationId.equals(oldest));
        ConversationMemory.Lease active=memory.acquire(104000); reject(() -> memory.catalogForContinuous(104000)); memory.release(active);
        check(memory.catalogForContinuous(4004000).isEmpty());
        System.out.println("Continuous voice: "+checks+" assertions passed (fake time, scopes, current-turn windows and bounded RAM; no device/audio).");
    }
}
