package tv.vela.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** A single selected EPG still behind the guide controls. */
final class VelaGuideBackdrop extends ImageView {
    private static final int INK=0xff090a0d;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF picture=new RectF();
    private Shader leftShade,topShade,bottomShade,ambient,portrait;
    private Bitmap prepared;
    private int preparedWidth,preparedHeight;
    private VelaProgrammeArt loader;
    private String requested="";
    private final Runnable fetch=()->{
        if(!isShown()||requested.isEmpty())return;
        if(loader==null)loader=new VelaProgrammeArt(getContext(),this);
        loader.bindBackdrop(this,requested);
    };

    VelaGuideBackdrop(Context context){
        super(context);
        setScaleType(ScaleType.CENTER_CROP);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        if(w<=0||h<=0)return;
        leftShade=new LinearGradient(0,0,w,0,new int[]{0xf7090a0d,0xe5090a0d,0xc5090a0d,0x40090a0d,0x08090a0d},
            new float[]{0,.21f,.47f,.70f,1},Shader.TileMode.CLAMP);
        topShade=new LinearGradient(0,0,0,h*.38f,0xb0090a0d,Color.TRANSPARENT,Shader.TileMode.CLAMP);
        bottomShade=new LinearGradient(0,h*.75f,0,h,Color.TRANSPARENT,INK,Shader.TileMode.CLAMP);
    }

    private static BitmapShader texture(Bitmap source,float scale,float left,float top){
        BitmapShader shader=new BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        Matrix matrix=new Matrix();
        matrix.setScale(scale,scale);
        matrix.postTranslate(left,top);
        shader.setLocalMatrix(matrix);
        return shader;
    }

    private void frame(Bitmap image,int w,int h){
        prepared=image;preparedWidth=w;preparedHeight=h;
        ambient=null;portrait=null;
        int iw=image.getWidth(),ih=image.getHeight();
        if(iw>=ih*1.3f){
            float scale=Math.max((float)w/iw,(float)h/ih);
            float width=iw*scale,height=ih*scale;
            // Place the upper third of the still in the upper third of the header.
            float left=(w-width)*.5f,top=(h-height)*.32f;
            picture.set(left,top,left+width,top+height);
            return;
        }
        // Give a poster its full height. A tiny, filtered copy colours the surrounding space.
        int sampleW=Math.min(16,iw),sampleH=Math.max(1,Math.min(24,Math.round((float)sampleW*ih/iw)));
        Bitmap sample=Bitmap.createScaledBitmap(image,sampleW,sampleH,true);
        float ambientScale=Math.max(w/(float)sampleW,h/(float)sampleH);
        ambient=texture(sample,ambientScale,(w-sampleW*ambientScale)*.5f,(h-sampleH*ambientScale)*.32f);
        float scale=(float)h/ih,width=iw*scale,left=w*.79f-width*.5f;
        picture.set(left,0,left+width,h);
        Shader fade=new LinearGradient(left,0,left+width,0,new int[]{0,Color.WHITE,Color.WHITE,0},
            new float[]{0,.10f,.90f,1},Shader.TileMode.CLAMP);
        portrait=new ComposeShader(texture(image,scale,left,0),fade,PorterDuff.Mode.DST_IN);
    }

    @Override protected void onDraw(Canvas canvas){
        Drawable drawable=getDrawable();
        if(drawable==null)return;
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        int checkpoint=canvas.save();
        canvas.clipRect(0,0,w,h);
        if(drawable instanceof BitmapDrawable&&((BitmapDrawable)drawable).getBitmap()!=null){
            Bitmap image=((BitmapDrawable)drawable).getBitmap();
            if(image!=prepared||w!=preparedWidth||h!=preparedHeight)frame(image,w,h);
            if(portrait==null){
                canvas.drawBitmap(image,null,picture,paint);
            }else{
                paint.setShader(ambient);canvas.drawRect(0,0,w,h,paint);
                paint.setShader(portrait);canvas.drawRect(picture,paint);
            }
        }else super.onDraw(canvas);
        paint.setShader(leftShade);canvas.drawRect(0,0,w,h,paint);
        paint.setShader(topShade);canvas.drawRect(0,0,w,h,paint);
        paint.setShader(bottomShade);canvas.drawRect(0,0,w,h,paint);
        paint.setShader(null);canvas.restoreToCount(checkpoint);
    }

    void show(String url) {
        String next = VelaProgrammeArt.url(url);
        if (next.equals(requested)) return;
        requested = next;
        removeCallbacks(fetch);
        if (loader != null) loader.clear();
        if (!next.isEmpty()) postDelayed(fetch, 140);
    }

    void release() {
        requested = "";
        removeCallbacks(fetch);
        if (loader != null) { loader.close(); loader = null; }
        setImageDrawable(null);
    }

    @Override protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    @Override public void setImageDrawable(Drawable drawable){
        prepared=null;ambient=null;portrait=null;
        super.setImageDrawable(drawable);
    }
}
