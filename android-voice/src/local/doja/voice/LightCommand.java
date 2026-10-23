package local.doja.voice;

/** Validated intent, deliberately separate from BLE transport and voice/UI code. */
public final class LightCommand {
    public enum Kind { POWER, COLOR, BRIGHTNESS, PRESET, PATTERN }
    public final LightTarget target;
    public final Kind kind;
    public final LightColor color;
    public final String name;
    public final int brightnessPercent;
    public final boolean powerOn;

    private LightCommand(LightTarget target,Kind kind,LightColor color,String name,int brightnessPercent,boolean powerOn){
        if(target==null||kind==null)throw new IllegalArgumentException("Orden de luz incompleta");
        if(kind==Kind.COLOR&&color==null)throw new IllegalArgumentException("Falta el color");
        if((kind==Kind.PRESET||kind==Kind.PATTERN)&&(name==null||name.isEmpty()))throw new IllegalArgumentException("Falta el preset");
        if(brightnessPercent<-1||brightnessPercent>100)throw new IllegalArgumentException("Brillo fuera de rango");
        if(kind==Kind.POWER&&name!=null)throw new IllegalArgumentException("Encendido sin nombre");
        this.target=target;this.kind=kind;this.color=color;this.name=name;this.brightnessPercent=brightnessPercent;this.powerOn=powerOn;
    }

    public static LightCommand power(LightTarget target,boolean on){return new LightCommand(target,Kind.POWER,null,null,-1,on);}
    public static LightCommand color(LightTarget target,LightColor color){return color(target,color,-1);}
    public static LightCommand color(LightTarget target,LightColor color,int brightnessPercent){return new LightCommand(target,Kind.COLOR,color,null,brightnessPercent,false);}
    public static LightCommand brightness(LightTarget target,int percent){return new LightCommand(target,Kind.BRIGHTNESS,null,null,percent,false);}
    public static LightCommand preset(LightTarget target,String name){return preset(target,name,-1);}
    public static LightCommand preset(LightTarget target,String name,int brightnessPercent){return new LightCommand(target,Kind.PRESET,null,name,brightnessPercent,false);}
    public static LightCommand pattern(LightTarget target,String name){return pattern(target,name,-1);}
    public static LightCommand pattern(LightTarget target,String name,int brightnessPercent){return new LightCommand(target,Kind.PATTERN,null,name,brightnessPercent,false);}
}
