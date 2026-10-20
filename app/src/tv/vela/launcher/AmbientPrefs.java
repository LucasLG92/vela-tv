package tv.vela.launcher;

import android.content.Context;
import android.content.SharedPreferences;

final class AmbientPrefs {
    private AmbientPrefs() {}
    static SharedPreferences get(Context c){return c.getSharedPreferences("ambient",Context.MODE_PRIVATE);}
    static boolean plex(Context c){return "plex".equals(get(c).getString("style","aerial"));}
    static String styleName(Context c){return plex(c)?"Biblioteca de Plex":"Aéreos";}
    static void infoDefaults(Context c){SharedPreferences p=get(c);if(!p.getBoolean("info_v2",false))p.edit().putBoolean("info_v2",true).putBoolean("clock",true).putBoolean("weather",true).apply();}
    static boolean clock(Context c){infoDefaults(c);return get(c).getBoolean("clock",true);}
    static boolean weather(Context c){infoDefaults(c);return get(c).getBoolean("weather",true);}
    static boolean captions(Context c){return get(c).getBoolean("captions",true);}
    static int seconds(Context c){int n=get(c).getInt("seconds",60);return n==30||n==120?n:60;}
    static String theme(Context c){return get(c).getString("theme","all");}
    static String themeName(Context c){String s=theme(c);return "landscape".equals(s)?"Paisajes":"city".equals(s)?"Ciudades":"Todos";}
}
