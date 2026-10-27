package local.doja.voice;

import java.util.*;

/** No Android/native recognizer/audio: actual production policies with controlled evidence/time. */
public final class SilenceLifecycleTest {
    static int assertions;
    static void check(boolean value) { assertions++; if(!value) throw new AssertionError("Interruption assertion "+assertions); }
    interface Operation { void run(); }
    static void reject(Operation operation) { try { operation.run(); throw new AssertionError("Invalid capture accepted"); } catch(IllegalArgumentException expected) { assertions++; } }
    static SilenceWindowPolicy.Ticket arm(SilenceWindowPolicy policy,String id,long now) {
        SilenceWindowPolicy.Ticket ticket=policy.arm(id,now+60000,now+5000,now,60000); check(ticket!=null); return ticket;
    }
    static double recognition(String text,String[] words,double[] scores,double[] starts,double[] ends,boolean alarm) {
        return SilenceRecognition.confidence(text,words,scores,starts,ends,alarm);
    }
    public static void main(String[] args) {
        check(SilenceWindowPolicy.isSilence("silencio") && SilenceWindowPolicy.isSilence("Andrómeda, silencio"));
        check(SilenceWindowPolicy.isCancel("cancélalo") && SilenceWindowPolicy.isCancel("cancela eso")
            && SilenceWindowPolicy.isCancel("olvídalo") && SilenceWindowPolicy.isCancel("cancela lo")
            && SilenceWindowPolicy.isCancel("Andrómeda, cancélalo"));
        check(SilenceWindowPolicy.isWakeOnly("Andrómeda") && !SilenceWindowPolicy.isWakeOnly("Andrómeda, cancélalo"));
        check(SilenceWindowPolicy.isCommand("cancélalo") && SilenceWindowPolicy.isCommand("Andrómeda"));
        check(!SilenceWindowPolicy.isCommand("cancela todo") && !SilenceWindowPolicy.isCancel("cancela todo"));
        SilenceWindowPolicy policy=new SilenceWindowPolicy();
        SilenceWindowPolicy.Ticket old=arm(policy,"first",100000);
        check(!policy.accept(old,true,1,100010)); // Never authorize a command before output starts.
        check(!policy.start(old,100010)); check(policy.ready(old,100020));
        check(!policy.accept(old,true,1,100030)); check(policy.start(old,100040));
        check(!policy.start(old,100041)); check(policy.accept(old,true,.9,100050));
        check(!policy.accept(old,true,1,100051));
        SilenceWindowPolicy.Ticket current=arm(policy,"second",100060);
        check(!policy.claimCallback(old,100070)); check(!policy.fail(old)); policy.end(old);
        check(policy.ready(current,100080) && policy.start(current,100090)); check(policy.accept(current,true,1,100100));
        check(policy.claimCallback(current,100110)); check(!policy.claimCallback(current,100111));
        policy.end(current);
        // Thousands of replies, fixed RAM; old callbacks never become current after the recent-ID cache rotates.
        for(int index=0;index<5000;index++) {
            long now=101000+index*100L; SilenceWindowPolicy.Ticket ticket=arm(policy,"reply-"+index,now);
            check(policy.ready(ticket,now+1)); check(policy.start(ticket,now+2));
            check(!policy.claimCallback(old,now+3)); policy.end(old); check(policy.playing(ticket,now+3));
            check(policy.accept(ticket,true,.8,now+4)); check(policy.claimCallback(ticket,now+5)); policy.end(ticket);
            check(policy.retainedIds()<=32);
        }
        SilenceWindowPolicy.Ticket reused=arm(policy,"first",700000);
        check(!policy.claimCallback(old,700001)); check(!policy.start(old,700001));
        check(policy.ready(reused,700002)); check(policy.start(reused,700003));
        check(!policy.accept(old,true,1,700004));
        policy.end(reused); check(!policy.claimCallback(reused,700005));
        SilenceWindowPolicy.Ticket lateCallback=arm(policy,"late-callback",705000);
        check(policy.ready(lateCallback,705001) && policy.start(lateCallback,705002));
        check(policy.accept(lateCallback,true,1,705003));
        check(!policy.claimCallback(lateCallback,705504)); check(policy.fail(lateCallback)); check(policy.claimFailure(lateCallback));
        SilenceWindowPolicy.Ticket expired=arm(policy,"expired",710000);
        check(!policy.ready(expired,711500)); check(policy.fail(expired)); check(policy.claimFailure(expired)); check(!policy.claimFailure(expired));
        SilenceWindowPolicy.Ticket newer=arm(policy,"newer",712000); check(!policy.claimFailure(expired));
        check(policy.ready(newer,712001)); check(!policy.start(newer,717000));
        policy.close(); check(!policy.current(newer,712002)); check(!policy.fail(newer)); check(!policy.claimCallback(newer,712002));
        check(policy.arm("closed",713000,713000,712000,30000)==null);
        InterruptionAudioClock clock=new InterruptionAudioClock(100000);
        check(clock.observe(640,true,320,100020,100020)==100020);
        check(!InterruptionAudioClock.afterStart(100020,640,100010));
        check(InterruptionAudioClock.afterStart(100040,640,100020));
        check(!InterruptionAudioClock.afterStart(100040,-640,100020));
        check(clock.alive(100769)); check(!clock.alive(100770));
        reject(() -> clock.observe(640,true,640,100040,100400));
        reject(() -> new InterruptionAudioClock(100000).observe(641,true,320,100020,100020));
        reject(() -> new InterruptionAudioClock(100000).observe(640,true,50000,100020,100020));
        reject(() -> new InterruptionAudioClock(100000).observe(640,true,320,100500,100020));
        InterruptionAudioClock noTimestamp=new InterruptionAudioClock(100000);
        check(noTimestamp.observe(640,false,0,0,100020)==-1);
        check(noTimestamp.observe(640,false,0,0,102999)==-1);
        reject(() -> noTimestamp.observe(640,false,0,0,103000));
        InterruptionAudioClock noFrame=new InterruptionAudioClock(100000);
        check(noFrame.alive(100750)); check(noFrame.alive(102999)); check(!noFrame.alive(103000));
        String[] bare={"silencio"}; double[] score={.95},start={0},end={.8};
        check(recognition("silencio",bare,score,start,end,false)==.95);
        check(recognition("no silencio",bare,score,start,end,false)<0);
        check(recognition("ayer dije silencio",bare,score,start,end,false)<0);
        check(recognition("silencio no",bare,score,start,end,false)<0);
        check(recognition("silencio y cancela el timer",bare,score,start,end,false)<0);
        check(recognition("silencio",new String[]{"ruido"},score,start,end,false)<0);
        check(recognition("silencio",bare,new double[]{Double.NaN},start,end,false)<0);
        check(recognition("silencio",bare,score,new double[]{1},end,false)<0);
        check(recognition("silencio",bare,score,start,new double[]{4.1},false)<0);
        check(recognition("andromeda silencio",new String[]{"andromeda","silencio"},new double[]{.91,.92},new double[]{0,.3},new double[]{.5,.9},false)<0);
        check(recognition("andromeda silencio",new String[]{"andromeda","silencio"},new double[]{.91,.92},new double[]{0,.5},new double[]{.4,.9},false)==.91);
        check(recognition("andromeda silencio",new String[]{"andromeda","silencio"},new double[]{.91,.92},new double[]{0,.49},new double[]{.5,.9},false)==.91);
        String[] snooze={"pospon","cinco","minutos"}; double[] scores={.95,.96,.97},starts={0,.3,.6},ends={.2,.5,.9};
        check(recognition("pospon cinco minutos",snooze,scores,starts,ends,true)==.95);
        check(recognition("pospon cinco minutos",snooze,scores,starts,ends,false)<0);
        check(recognition("no pospon cinco minutos",snooze,scores,starts,ends,true)<0);
        check(recognition("elimina la alarma",new String[]{"elimina","la","alarma"},scores,starts,ends,true)<0);
        SilenceDiagnostics.Window diagnostic=SilenceDiagnostics.begin(SilenceDiagnostics.Owner.SPEECH,100000);
        diagnostic.capture(100020,7,true,true,false); diagnostic.ready(100040); diagnostic.outputStarted(100060);
        diagnostic.lag(40); diagnostic.lag(20); diagnostic.decoded(12); diagnostic.detected(100100); diagnostic.callback(100110); diagnostic.stopped(true,100125);
        Map<String,Object> receipt=diagnostic.snapshot();
        check(receipt.get("ready_after_ms").equals(40L)); check(receipt.get("output_after_ms").equals(60L));
        check(receipt.get("max_pcm_lag_ms").equals(40L)); check(receipt.get("detection_to_callback_ms").equals(10L));
        check(receipt.get("stop_return_accepted").equals(1)); check(receipt.get("callback_to_stop_ms").equals(15L));
        check(!receipt.containsKey("text") && !receipt.containsKey("pcm") && !receipt.containsKey("turn_id"));
        System.out.println("Interruption lifecycle: "+assertions+" assertions passed (pre-arm, 5000 outputs, stale callbacks/audio, strict endpoints; no device/audio/native model).");
    }
}
