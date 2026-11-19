package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

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

}
