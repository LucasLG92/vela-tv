package tv.vela.launcher;

import android.content.Context;

/** Small local choices; no bitmaps, polling, networking or extra animation. */
final class Appearance {
    private Appearance(){}
    private static final int[] COLORS={0xFFF7F7FA,0xFFC9DBFF,0xFFBEE9D4,0xFFDCCBFA,0xFFFFDDAB,0xFFF5C8D7};
    static int choice(Context c,String key,int fallback,int count){int value=Visuals.prefs(c).getInt("appearance_"+key,fallback);return value>=0&&value<count?value:fallback;}
    static int colorIndex(Context c){return choice(c,"color",0,COLORS.length);}
    static int sizeIndex(Context c){return choice(c,"size",1,3);}
    static int backdropIndex(Context c){return choice(c,"backdrop",1,3);}
    static int accent(Context c){return COLORS[colorIndex(c)];}
    static int tint(Context c,int alpha){return (accent(c)&0xFFFFFF)|(alpha<<24);}
    static int posterWidth(Context c){return new int[]{144,168,196}[sizeIndex(c)];}
    static int posterHeight(Context c){return posterWidth(c)*3/2;}
    static String signature(Context c){return colorIndex(c)+":"+sizeIndex(c)+":"+backdropIndex(c);}
    static void set(Context c,String key,int value){int count="color".equals(key)?COLORS.length:3;if(("color".equals(key)||"size".equals(key)||"backdrop".equals(key))&&value>=0&&value<count)Visuals.prefs(c).edit().putInt("appearance_"+key,value).apply();}
    static void reset(Context c){Visuals.prefs(c).edit().remove("appearance_color").remove("appearance_size").remove("appearance_backdrop").apply();}
    static String colorName(int index){return new String[]{Ui.tr("Neve","Snow","Nieve"),Ui.tr("Azul","Blue","Azul"),Ui.tr("Menta","Mint","Menta"),Ui.tr("Lavanda","Lavender","Lavanda"),Ui.tr("Areia","Sand","Arena"),Ui.tr("Rosa","Rose","Rosa")}[Math.max(0,Math.min(index,5))];}
    static String sizeName(int index){return new String[]{Ui.tr("Compactos","Compact","Compactos"),Ui.tr("Equilibrados","Balanced","Equilibrados"),Ui.tr("Grandes","Large","Grandes")}[Math.max(0,Math.min(index,2))];}
    static String backdropName(int index){return new String[]{Ui.tr("Mais escuro","Darker","Más oscuro"),Ui.tr("Equilibrado","Balanced","Equilibrado"),Ui.tr("Mais visível","More visible","Más visible")}[Math.max(0,Math.min(index,2))];}
}
