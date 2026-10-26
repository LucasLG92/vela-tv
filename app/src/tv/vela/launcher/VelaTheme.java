package tv.vela.launcher;

import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.*;
import android.widget.TextView;

/** The sidebar's graphite palette; providers keep their own artwork and logos. */
final class VelaTheme {
    private VelaTheme(){}
    static final int BACKGROUND=0xFF090A0D, PANEL=0xFF15181F, CARD=0xFF20242D, RAISED=0xFF2C323D;
    static final int WHITE=0xFFF7F7FA, SECONDARY=0xFFB0B2BC, MUTED=0xFF82848E, INK=0xFF17181E;
    static final int LINE=0x18FFFFFF, SELECTED=0x18FFFFFF;
    static final int CONTROL_RADIUS=16, PANEL_RADIUS=26;
    static GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    /** Rows share one quiet separator. Only the focused action becomes a filled surface. */
    static Drawable row(boolean focus,int accent){return new Row(focus,accent);}
    private static final class Row extends Drawable {
        final boolean focus;final int accent;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF rect=new RectF();
        Row(boolean f,int color){focus=f;accent=color;}
        public void draw(Canvas c){Rect b=getBounds();if(focus){rect.set(b);paint.setColor(accent);c.drawRoundRect(rect,CONTROL_RADIUS,CONTROL_RADIUS,paint);}else{paint.setColor(LINE);c.drawRect(b.left+20,b.bottom-1,b.right-20,b.bottom,paint);}}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    /** One text node keeps rows light while giving title and supporting text different weight. */
    static void rowText(TextView view,String title,String detail,boolean focus){
        String text=title+(detail.isEmpty()?"":"\n"+detail);SpannableString styled=new SpannableString(text);
        if(!detail.isEmpty()){int at=title.length()+1;styled.setSpan(new RelativeSizeSpan(.78f),at,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setSpan(new ForegroundColorSpan(focus?0xFF626773:VelaReading.prefs(view.getContext()).getBoolean("contrast",false)?0xFFE0E1E8:SECONDARY),at,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        view.setTextColor(focus?INK:WHITE);view.setText(styled);
    }
}
