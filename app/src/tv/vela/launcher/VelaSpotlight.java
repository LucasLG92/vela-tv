package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** A wash of reflected colour behind the selected source; no bitmap layer or animation. */
final class VelaSpotlight extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tint;
    VelaSpotlight(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void tint(int value){if(tint!=value){tint=value;shade();}}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){shade();}
    private void shade(){
        if(getWidth()==0||getHeight()==0)return;
        int rgb=tint&0x00ffffff;
        RadialGradient light=new RadialGradient(0,0,1,new int[]{rgb|0xa6000000,rgb|0x59000000,rgb|0x1c000000,rgb},new float[]{0,.35f,.7f,1},Shader.TileMode.CLAMP);
        Matrix transform=new Matrix();transform.setScale(getWidth()*.285f,getHeight()*.5f);
        transform.postTranslate(getWidth()*.715f,getHeight()*.5f);light.setLocalMatrix(transform);
        paint.setShader(light);invalidate();
    }
    @Override protected void onDraw(Canvas c){c.drawRect(0,0,getWidth(),getHeight(),paint);}

    /** Only sample the already decoded launcher icon when the selected bitmap changes. */
    static int iconColor(Bitmap icon){
        if(icon==null)return 0xFF72817D;
        int best=0xFF72817D,highest=0;
        for(int y=1;y<9;y++)for(int x=1;x<9;x++){
            int p=icon.getPixel((icon.getWidth()-1)*x/9,(icon.getHeight()-1)*y/9);
            if(Color.alpha(p)<200)continue;
            int r=Color.red(p),g=Color.green(p),b=Color.blue(p);
            int hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b));
            int score=(hi-lo)*hi;
            if(score>highest){highest=score;best=p;}
        }
        return best;
    }
    static final class App extends FrameLayout {
        private final VelaSpotlight light;
        private final TextView name,caption;
        private final ImageView art;
        private Bitmap shown;
        App(Context c){
            super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            light=new VelaSpotlight(c);light.tint(iconColor(null));addView(light,new LayoutParams(-1,-1));
            name=label(c,78,VelaTheme.WHITE);name.setTypeface(Ui.displayMedium);
            name.setAutoSizeTextTypeUniformWithConfiguration(42,78,2,TypedValue.COMPLEX_UNIT_PX);
            put(name,22,78,910,110);caption=label(c,22,VelaTheme.SECONDARY);put(caption,26,200,850,44);
            art=new ImageView(c);art.setScaleType(ImageView.ScaleType.FIT_CENTER);put(art,1108,15,270,270);
            setVisibility(INVISIBLE);
        }
        private TextView label(Context c,int size,int color){
            TextView v=new TextView(c);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);
            v.setTypeface(Ui.medium);v.setGravity(Gravity.CENTER_VERTICAL);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);return v;
        }
        private void put(View v,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;addView(v,p);}
        void bind(String title,boolean pinned,Bitmap bitmap){
            if(!TextUtils.equals(name.getText(),title)){name.setText(title);caption.setText(pinned?L10n.text("Favorite"):"");}
            if(bitmap!=shown){shown=bitmap;art.setImageBitmap(bitmap);light.tint(iconColor(bitmap));}
            setVisibility(VISIBLE);
        }
    }
}
