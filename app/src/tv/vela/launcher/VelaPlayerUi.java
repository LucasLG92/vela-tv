package tv.vela.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** Shared passive player styling. No timer, bitmap cache, request or decoder. */
final class VelaPlayerUi {
    static final int INFO_MS=5000,INTRO_MS=3000;
    private VelaPlayerUi(){}
    static GradientDrawable shade(){return shade(android.os.Build.VERSION.SDK_INT);}
    static GradientDrawable shade(int api){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00090A0D,0xD9090A0D,0xFA090A0D,0xFF090A0D});if(api>=29)d.setColors(new int[]{0x00090A0D,0xD9090A0D,0xFA090A0D,0xFF090A0D},new float[]{0,.18f,.42f,1});else d.setColors(new int[]{0x00090A0D,0xD9090A0D,0xFA090A0D,0xFF090A0D,0xFF090A0D,0xFF090A0D});return d;}
    static String hint(){return L10n.text("← Controls · → Channels · OK Hide · Hold OK ★");}
    static String clock(){return time(System.currentTimeMillis()/1000);}
    static String time(long seconds){return new java.text.SimpleDateFormat("HH:mm",Ui.locale()).format(new java.util.Date(seconds*1000));}
    static Drawable row(Context c,boolean focus,boolean playing){return focus?VelaTheme.shape(VelaReading.accent(c),VelaTheme.CONTROL_RADIUS):playing?VelaTheme.shape(VelaTheme.SELECTED,VelaTheme.CONTROL_RADIUS):VelaTheme.row(false,VelaReading.accent(c));}
    static void tab(TextView v,boolean selected){boolean focus=v.hasFocus();v.setTextColor(focus?VelaTheme.INK:selected?VelaTheme.WHITE:VelaTheme.SECONDARY);v.setBackground(VelaTheme.shape(focus?VelaReading.accent(v.getContext()):selected?VelaTheme.SELECTED:android.graphics.Color.TRANSPARENT,VelaTheme.CONTROL_RADIUS));}
}
