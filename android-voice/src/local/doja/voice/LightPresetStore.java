package local.doja.voice;

import android.content.Context;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Local-only named RGB presets. It never stores transcripts or contacts BLE. */
public final class LightPresetStore {
    private static final String PREFS="light_presets_v1";
    private static final String PREFIX="color_";
    private LightPresetStore(){}

    public static void save(Context context,String name,LightColor color){
        if(context==null||color==null)throw new IllegalArgumentException("Color incompleto");
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(PREFIX+key(name),color.hex()).apply();
    }

    public static LightColor get(Context context,String name){
        if(context==null)return null;
        String value=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(PREFIX+key(name),null);
        return value==null?null:LightColor.parseHex(value);
    }

    public static Map<String,LightColor> list(Context context){
        Map<String,LightColor> result=new TreeMap<>();
        if(context==null)return result;
        for(Map.Entry<String,?> entry:context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getAll().entrySet()){
            if(!entry.getKey().startsWith(PREFIX)||!(entry.getValue() instanceof String))continue;
            try{result.put(entry.getKey().substring(PREFIX.length()),LightColor.parseHex((String)entry.getValue()));}catch(IllegalArgumentException ignored){}
        }
        return result;
    }

    private static String key(String name){
        if(name==null)throw new IllegalArgumentException("Escribe un nombre");
        String value=Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");
        value=value.replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");
        if(value.isEmpty()||value.length()>24)throw new IllegalArgumentException("Nombre de 1 a 24 caracteres");
        return value;
    }
}
