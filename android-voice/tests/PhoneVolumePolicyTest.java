package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Public request fixtures, fake media only. Never loads Android or changes sound. */
public final class PhoneVolumePolicyTest {
    private static int checks;
    private static final String A=String.join("", Collections.nCopies(32,"a")), B=A.replace('a','b'), C=A.replace('a','c'), D=A.replace('a','d');
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Volume check " + checks); }
    static void invalid(Runnable work) { try { work.run(); throw new AssertionError("Invalid volume accepted"); } catch (IllegalArgumentException expected) { checks++; } }
    static PhoneVolumePolicy.Grant grant(int percent) { return new PhoneVolumePolicy.Grant(A,B,C,D,1,99900,104000,percent); }
    static PhoneVolumePolicy.Permit permit(PhoneVolumePolicy p,int percent) {
        return p.claim(grant(percent),"pon el volumen al " + percent,C,1,99900,190000,100000);
    }
    static final class Media implements PhoneVolumePolicy.Media {
        int value=12, max=15, writes, clamp=-1; boolean fail;
        public int minimum() { return 0; } public int maximum() { return max; } public int current() { return value; }
        public void set(int index) { writes++; if (fail) throw new SecurityException("synthetic"); value=clamp>=0?clamp:index; }
    }
    public static void main(String[] args) throws Exception {
        List<String> requests=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);
        for (String line:requests.subList(1,requests.size())) {
            String[] cells=line.split("\t");
            if (cells[1].equals("INVALID")) invalid(()->PhoneVolumePolicy.requested(cells[0]));
            else check(PhoneVolumePolicy.requested(cells[0])==Integer.parseInt(cells[1]));
        }
        for (int percent=0;percent<=100;percent++) {
            int index=PhoneVolumePolicy.index(percent,15); check(index>=0 && index<=15);
        }
        check(PhoneVolumePolicy.index(0,15)==0); check(PhoneVolumePolicy.index(100,15)==15);
        check(PhoneVolumePolicy.index(50,15)==8); check(PhoneVolumePolicy.percent(8,15)==53);
        for(int bad:new int[]{-1,101}) invalid(()->PhoneVolumePolicy.index(bad,15));
        PhoneVolumePolicy policy=new PhoneVolumePolicy(A,B); PhoneVolumePolicy.Permit permit=permit(policy,50); Media media=new Media();
        PhoneVolumePolicy.Receipt r=PhoneVolumePolicy.execute(permit,media,()->100000);
        check(r.status==PhoneVolumePolicy.Status.APPLIED && r.actual==53 && media.writes==1 && media.value==8);
        check(PhoneVolumePolicy.execute(permit,media,()->100001).status==PhoneVolumePolicy.Status.REJECTED && media.writes==1);
        final PhoneVolumePolicy consumed=policy;
        invalid(()->permit(consumed,50));
        for (int percent:new int[]{0,100}) {
            media=new Media(); r=PhoneVolumePolicy.execute(permit(new PhoneVolumePolicy(A,B),percent),media,()->100000);
            check(r.actual==percent && media.writes==1);
        }
        media=new Media(); media.value=8; r=PhoneVolumePolicy.execute(permit(new PhoneVolumePolicy(A,B),50),media,()->100000);
        check(r.status==PhoneVolumePolicy.Status.UNCHANGED && r.actions()==0 && media.writes==0);
        media=new Media(); media.clamp=11; r=PhoneVolumePolicy.execute(permit(new PhoneVolumePolicy(A,B),100),media,()->100000);
        check(r.status==PhoneVolumePolicy.Status.LIMITED && r.actual==73 && media.writes==1);
        media=new Media(); media.fail=true; r=PhoneVolumePolicy.execute(permit(new PhoneVolumePolicy(A,B),50),media,()->100000);
        check(r.status==PhoneVolumePolicy.Status.UNKNOWN && r.actions()==0 && media.writes==1);
        policy=new PhoneVolumePolicy(A,B); permit=permit(policy,50); policy.close(); media=new Media();
        check(PhoneVolumePolicy.execute(permit,media,()->100000).status==PhoneVolumePolicy.Status.REJECTED && media.writes==0);
        media=new Media(); r=PhoneVolumePolicy.execute(permit(new PhoneVolumePolicy(A,B),50),media,()->104000);
        check(r.status==PhoneVolumePolicy.Status.REJECTED && media.writes==0);
        invalid(()->new PhoneVolumePolicy(A,B).claim(grant(50),"no bajes el volumen al 50",C,1,99900,190000,100000));
        invalid(()->new PhoneVolumePolicy(A,B).claim(grant(50),"pon el volumen al 60",C,1,99900,190000,100000));
        invalid(()->new PhoneVolumePolicy(A,B).claim(grant(50),"pon el volumen al 50",D,1,99900,190000,100000));
        invalid(()->new PhoneVolumePolicy(A,B).claim(grant(50),"pon el volumen al 50",C,2,99900,190000,100000));
        invalid(()->new PhoneVolumePolicy(A,B).claim(grant(50),"pon el volumen al 50",C,1,99900,190000,126500));
        if(args.length>1) for(String line:Files.readAllLines(Paths.get(args[1]),StandardCharsets.UTF_8)) {
            String[] cells=line.split("\t");
            check(PhoneVolumePolicy.resultText(new PhoneVolumePolicy.Receipt(PhoneVolumePolicy.Status.valueOf(cells[0]),Integer.parseInt(cells[2])),Integer.parseInt(cells[1])).equals(cells[3]));
        }
        System.out.println("Phone media volume: " + checks + " assertions passed (fake audio, no Android/device/network).");
    }
}
