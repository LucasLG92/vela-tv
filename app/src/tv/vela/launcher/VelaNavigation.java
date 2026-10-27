package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.view.ViewGroup;

/** Shared navigation chrome. Opaque vector surfaces; no scene bitmap, blur or worker. */
final class VelaNavigation {
    static final int RAIL=76,DRAWER=332,ROW=60,OPEN_ROW=316,LABEL=238;
    private VelaNavigation(){}
    static GradientDrawable surface(){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xFF20222B,0xFF12141A});d.setCornerRadius(32);return d;}
    static void width(View row,boolean expanded){ViewGroup.LayoutParams p=row.getLayoutParams();int width=expanded?OPEN_ROW:ROW;if(p.width!=width){p.width=width;row.setLayoutParams(p);}}
    static void reveal(View label,boolean expanded){label.animate().cancel();if(VelaFocus.reduced(label.getContext()))label.setAlpha(expanded?1f:0f);else label.animate().alpha(expanded?1f:0f).setDuration(expanded?120:80).start();}
    /** Keep the complete inactive screen visible beside the drawer, without relayout or a captured bitmap. */
    static void preview(View content,boolean expanded){
        if(content==null)return;ViewGroup.LayoutParams bounds=content.getLayoutParams();if(bounds==null)return;
        int width=bounds.width>0?bounds.width:content.getWidth(),height=bounds.height>0?bounds.height:content.getHeight();
        if(width<=0||height<=0)return;float scale=expanded?.84f:1f,offset=expanded?width*(1f-scale):0f;
        content.animate().cancel();content.setPivotX(0);content.setPivotY(height/2f);
        if(VelaFocus.reduced(content.getContext())){content.setScaleX(scale);content.setScaleY(scale);content.setTranslationX(offset);}
        else content.animate().scaleX(scale).scaleY(scale).translationX(offset).setDuration(expanded?140:120).start();
    }
    static int foreground(boolean focused,boolean active){return focused?VelaTheme.INK:active?VelaTheme.WHITE:VelaTheme.SECONDARY;}
    static void style(View row,boolean focused,boolean active,boolean expanded){Item d=row.getBackground() instanceof Item?(Item)row.getBackground():new Item();if(row.getBackground()!=d)row.setBackground(d);d.update(focused,active,expanded,Appearance.accent(row.getContext()));}
    private static final class Item extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rect=new RectF();private boolean focused,active,expanded;private int accent,alpha=255;
        void update(boolean f,boolean a,boolean e,int color){if(f==focused&&a==active&&e==expanded&&color==accent)return;focused=f;active=a;expanded=e;accent=color;invalidateSelf();}
        public void draw(Canvas c){if(!focused&&!active)return;Rect b=getBounds();rect.set(b);paint.setColor(focused?accent:0x20FFFFFF);paint.setAlpha(Color.alpha(paint.getColor())*alpha/255);float radius=expanded?18:28;c.drawRoundRect(rect,radius,radius,paint);if(active&&!focused&&expanded){paint.setColor(VelaTheme.WHITE);paint.setAlpha(alpha);float middle=b.exactCenterY();rect.set(b.left+5,middle-9,b.left+8,middle+9);c.drawRoundRect(rect,1.5f,1.5f,paint);}}
        public void setAlpha(int value){alpha=value;invalidateSelf();}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        public void getOutline(Outline out){out.setRoundRect(getBounds(),expanded?18:28);out.setAlpha(focused?1f:0f);}
    }
}
