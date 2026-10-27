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
    static final int WHITE=0xFFF7F7FA, SECONDARY=0xFFB8BCC6, MUTED=0xFF969BA7, INK=0xFF17181E;
    static final int LINE=0x18FFFFFF, SELECTED=0x18FFFFFF;
    static final int CONTROL_RADIUS=18, PANEL_RADIUS=30;
    static GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    /** Full-screen canvas stays behind the moving foreground, including embedded menus. */
    static GradientDrawable canvas(){GradientDrawable d=new GradientDrawable();d.setGradientType(GradientDrawable.RADIAL_GRADIENT);d.setGradientCenter(.3f,.3f);d.setGradientRadius(1680);d.setColors(new int[]{PANEL,0xFF121318,BACKGROUND});return d;}
    /** Keep port identifiers readable over every original device/brand colour. */
    static void inputPort(TextView label){label.setTextColor(WHITE);label.setBackground(shape(0xCC090A0D,8));label.setPadding(8,0,8,0);label.setGravity(android.view.Gravity.CENTER_VERTICAL);label.setSingleLine();label.setEllipsize(TextUtils.TruncateAt.END);}
    /** Passive vector surfaces, shared by both editions. No images or persistent layers. */
    static GradientDrawable panel(){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xFF191C24,0xFF111319});d.setCornerRadius(PANEL_RADIUS);return d;}
    /** Generous hit areas; focus has one meaning throughout the interface. */
    static Drawable row(boolean focus,int accent){return new Row(focus,accent);}
    private static final class Row extends Drawable {
        final boolean focus;final int accent;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF rect=new RectF();
        Row(boolean f,int color){focus=f;accent=color;}
        public void draw(Canvas c){Rect b=getBounds();if(focus){rect.set(b);paint.setColor(accent);c.drawRoundRect(rect,CONTROL_RADIUS,CONTROL_RADIUS,paint);}else{rect.set(b.left,b.top+2,b.right,b.bottom-2);paint.setColor(0x06FFFFFF);c.drawRoundRect(rect,CONTROL_RADIUS,CONTROL_RADIUS,paint);}}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    /** One text node keeps rows light while giving title and supporting text different weight. */
    static void rowText(TextView view,String title,String detail,boolean focus){
        String text=title+(detail.isEmpty()?"":"\n"+detail);SpannableString styled=new SpannableString(text);
        if(!detail.isEmpty()){int at=title.length()+1;styled.setSpan(new RelativeSizeSpan(.78f),at,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setSpan(new ForegroundColorSpan(focus?0xFF626773:VelaReading.prefs(view.getContext()).getBoolean("contrast",false)?0xFFE0E1E8:SECONDARY),at,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        view.setTextColor(focus?INK:WHITE);view.setText(styled);
    }
}
