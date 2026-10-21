package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Shared human fields and fake media only. No Android, model, voice or real grants. */
public final class VolumeDialogueTest {
    static int checks;
    static final String A=String.join("",Collections.nCopies(32,"a")),B=A.replace('a','b'),C=A.replace('a','c'),D=A.replace('a','d');
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("Volume dialogue "+checks); }
    static void invalid(Runnable work) { try { work.run(); throw new AssertionError("Invalid confirmation accepted"); } catch(IllegalArgumentException expected) { checks++; } }
    static VolumeDialogue.Human human(String id,long seq,String text,long end,long now) { return new VolumeDialogue.Human(id,seq,text,end,now,64); }
    static VolumeDialogue form(String text) {
        VolumeDialogue value=new VolumeDialogue(64); VolumeDialogue.Decision result=value.begin(human(A,1,text,99900,100000));
        check(result.kind.equals("volume_question")); check(!result.kind.equals("confirmed_volume_proposal")); return value;
    }
    public static void main(String[] args) throws Exception {
        List<String> rows=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);
        for(String row:rows.subList(1,rows.size())) {
            String[] cells=row.split("\t");
            if(cells[1].equals("INVALID")) invalid(()->VolumeDialogue.candidate(cells[0]));
            else check(Objects.equals(VolumeDialogue.candidate(cells[0]),cells[1].equals("NONE")?null:Integer.valueOf(cells[1])));
        }
        VolumeDialogue value=form("¿Me puedes bajar el volumen al veinte porciento?");
        check(value.answer(human(B,2,"sí",100200,100220),100220).kind.equals("volume_cancelled"));
        value=form("ponlo al veinte porciento"); value.spoken(A,true,100100);
        VolumeDialogue.Human yes=human(B,2,"sí por favor",100200,100220);
        check(value.answer(yes,100220).kind.equals("confirmed_volume_proposal"));
        final VolumeDialogue verified=value;
        invalid(()->verified.claim(A,A,yes,100,100220)); invalid(()->verified.claim(C,A,yes,20,100220));
        invalid(()->verified.claim(A,C,yes,20,100220));
        PhoneVolumePolicy policy=new PhoneVolumePolicy(A,C,60000);
        PhoneVolumePolicy.Grant grant=new PhoneVolumePolicy.Grant(A,C,B,D,2,100200,104220,20);
        PhoneVolumePolicy.Permit permit=policy.claimConfirmed(grant,value,A,A,yes,400000,100220);
        PhoneVolumePolicyTest.Media media=new PhoneVolumePolicyTest.Media();
        PhoneVolumePolicy.Receipt receipt=PhoneVolumePolicy.execute(permit,media,()->100221);
        check(receipt.status==PhoneVolumePolicy.Status.APPLIED && receipt.actual==20 && media.writes==1 && media.value==3);
        check(PhoneVolumePolicy.execute(permit,media,()->100222).status==PhoneVolumePolicy.Status.REJECTED && media.writes==1);
        invalid(()->policy.claimConfirmed(grant,verified,A,A,yes,400000,100222));
        value=form("bájalo un poco"); value.spoken(A,true,100100);
        check(value.answer(human(B,2,"sí",100200,100220),100220).text.equals(VolumeDialogue.ASK));
        value.spoken(B,true,100300);
        check(value.answer(human(C,3,"veinte por ciento",100400,100420),100420).text.equals(VolumeDialogue.confirm(20)));
        value=form("ponlo al veinte porciento"); value.spoken(A,true,100100);
        check(value.answer(human(B,2,"treinta por ciento",100200,100220),100220).text.equals(VolumeDialogue.confirm(30)));
        value.spoken(A,true,100300);
        check(value.answer(human(C,3,"sí",100400,100420),100420).kind.equals("volume_cancelled"));
        value=form("ponlo al veinte porciento"); value.spoken(A,true,100100);
        check(value.answer(human(B,2,"no gracias",100200,100220),100220).kind.equals("volume_cancelled"));
        value=form("ponlo al veinte porciento"); value.spoken(A,true,100100);
        check(value.answer(human(B,2,"busca en Internet nebulosas",100200,100220),100220)==null && !value.active());
        value=form("ponlo al veinte porciento"); value.spoken(A,true,100100);
        check(value.answer(human(B,2,"sí",100050,100220),100220).kind.equals("volume_cancelled"));
        value=form("ponlo al veinte porciento"); value.spoken(A,true,100100);
        check(value.answer(human(B,2,"sí",400000,400001),400001).kind.equals("volume_cancelled"));
        value=form("ponlo al veinte porciento"); value.spoken(A,false,100100);
        check(value.answer(yes,100220)==null);
        System.out.println("Confirmed volume: "+checks+" assertions passed (shared human fields, heard confirmation, fake media only).");
    }
}
