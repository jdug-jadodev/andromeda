package local.doja.voice;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable six-digit RGB color. No BLE or UI side effects. */
public final class LightColor {
    private static final Pattern HEX=Pattern.compile("^#?([0-9a-fA-F]{6})$");
    public final int red,green,blue;

    private LightColor(int red,int green,int blue){this.red=red;this.green=green;this.blue=blue;}

    public static LightColor of(int red,int green,int blue){
        if(red<0||red>255||green<0||green>255||blue<0||blue>255)throw new IllegalArgumentException("RGB fuera de rango");
        return new LightColor(red,green,blue);
    }

    public static LightColor parseHex(String text){
        if(text==null)throw new IllegalArgumentException("Usa #RRGGBB, por ejemplo #F2A3F6");
        Matcher match=HEX.matcher(text.trim());
        if(!match.matches())throw new IllegalArgumentException("Usa #RRGGBB, por ejemplo #F2A3F6");
        int value=Integer.parseInt(match.group(1),16);
        return of((value>>16)&255,(value>>8)&255,value&255);
    }

    public String hex(){return String.format(Locale.ROOT,"#%02X%02X%02X",red,green,blue);}
    public int argb(){return 0xff000000|(red<<16)|(green<<8)|blue;}
    @Override public boolean equals(Object other){
        if(!(other instanceof LightColor))return false;LightColor value=(LightColor)other;
        return red==value.red&&green==value.green&&blue==value.blue;
    }
    @Override public int hashCode(){return (red<<16)|(green<<8)|blue;}
    @Override public String toString(){return hex();}
}
