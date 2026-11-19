package tv.vela.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
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
    private Shader leftShade,topShade,bottomShade,ambient,photo,edges;
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
        bottomShade=new LinearGradient(0,h*.60f,0,h,new int[]{0x00090a0d,0x32090a0d,0xba090a0d,INK},
            new float[]{0,.40f,.79f,1},Shader.TileMode.CLAMP);
    }

    private static int edgeTone(Bitmap image, int x) {
        // The space around the still picks up the edge colours without keeping a second bitmap.
        if (image.getConfig() == Bitmap.Config.HARDWARE) return INK;
        int red = 0, green = 0, blue = 0;
        for (int i = 0; i < 4; i++) {
            int y = (image.getHeight() - 1) * (2 * i + 1) / 8;
            int pixel = image.getPixel(x, y);
            red += Color.red(pixel);
            green += Color.green(pixel);
            blue += Color.blue(pixel);
        }
        return Color.rgb((red + Color.red(INK) * 3) / 7,
            (green + Color.green(INK) * 3) / 7, (blue + Color.blue(INK) * 3) / 7);
    }

    private void frame(Bitmap image, int w, int h) {
        prepared = image;
        preparedWidth = w;
        preparedHeight = h;
        int iw = image.getWidth(), ih = image.getHeight();
        float scale = Math.min(h * .96f / ih, (float) w / iw);
        float width = Math.min(w, iw * scale);
        float left = Math.max(0, Math.min(w - width, w * .76f - width / 2));
        float top = (h - ih * scale) * .5f;
        picture.set(left, 0, left + width, h);

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, top);
        BitmapShader texture = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        texture.setLocalMatrix(matrix);
        photo = texture;

        int start = edgeTone(image, 0), end = edgeTone(image, iw - 1);
        ambient = new LinearGradient(left + width * .16f, 0, left + width * .90f, 0,
            start, end, Shader.TileMode.CLAMP);
        edges = new LinearGradient(left, 0, left + width, 0,
            new int[]{start, start & 0xffffff, end & 0xffffff, picture.right < w - 1 ? end : end & 0xffffff},
            new float[]{0, .16f, .90f, 1}, Shader.TileMode.CLAMP);
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
            paint.setShader(ambient);canvas.drawRect(0,0,w,h,paint);
            paint.setShader(photo);canvas.drawRect(picture,paint);
            paint.setShader(edges);canvas.drawRect(picture,paint);
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

    @Override protected void onVisibilityChanged(android.view.View changed,int visibility){
        super.onVisibilityChanged(changed,visibility);
        if(fetch!=null&&visibility==VISIBLE&&requested!=null&&!requested.isEmpty()&&getDrawable()==null){
            removeCallbacks(fetch);postDelayed(fetch,140);
        }
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
        prepared=null;ambient=null;photo=null;edges=null;
        super.setImageDrawable(drawable);
    }
}
