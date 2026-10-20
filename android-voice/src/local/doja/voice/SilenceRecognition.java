package local.doja.voice;

/** Complete native ASR endpoint evidence only. No substring, grammar forcing or partial grants. */
final class SilenceRecognition {
    static double confidence(String text,String[] words,double[] scores,double[] starts,double[] ends,boolean alarm) {
        if(text==null || words==null || scores==null || starts==null || ends==null || words.length<1 || words.length>(alarm?8:2)
                || words.length!=scores.length || words.length!=starts.length || words.length!=ends.length
                || !(alarm ? AlarmOccurrencePolicy.command(text)!=null : SilenceWindowPolicy.isCommand(text))) return -1;
        StringBuilder recognized=new StringBuilder(); double confidence=1,previous=-1;
        for(int index=0;index<words.length;index++) {
            if(words[index]==null || words[index].isEmpty() || words[index].matches(".*\\s.*") || !Double.isFinite(scores[index]) || scores[index]<0 || scores[index]>1
                    || !Double.isFinite(starts[index]) || !Double.isFinite(ends[index]) || starts[index]<0 || ends[index]<=starts[index]
                    || starts[index]+.02<previous || ends[index]-starts[0]>4) return -1;
            if(index>0) recognized.append(' '); recognized.append(words[index]); previous=ends[index]; confidence=Math.min(confidence,scores[index]);
        }
        return text.trim().equals(recognized.toString()) ? confidence : -1;
    }
}
