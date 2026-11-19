package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** One programme still, softly faded into the existing TV canvas. */
final class VelaGuideBackdrop extends ImageView {
    // A shared 8 KiB alpha mask feathers the four edges without an offscreen TV-sized layer.
    private static final Bitmap MASK = mask();
    private static Bitmap mask() {
        int width=128,height=64;
        Bitmap mask=Bitmap.createBitmap(width,height,Bitmap.Config.ALPHA_8);
        java.nio.ByteBuffer pixels=java.nio.ByteBuffer.allocate(mask.getRowBytes()*height);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            float u=(float)x/(width-1),v=(float)y/(height-1);
            float opacity=fade(u/.32f)*fade((1-u)/.09f)*fade(v/.17f)*fade((1-v)/.29f);
            pixels.put(y*mask.getRowBytes()+x,(byte)Math.round(255*opacity));
        }
        mask.copyPixelsFromBuffer(pixels);
        return mask;
    }
    private static float fade(float value) {
        float t=Math.min(1f,value);
        return t*t*(3f-2f*t);
    }

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix transform = new Matrix();
    private VelaProgrammeArt loader;
    private Bitmap shown;
    private String requested = "";
    private final Runnable fetch = () -> {
        if (!isShown() || requested.isEmpty()) return;
        if (loader == null) loader = new VelaProgrammeArt(getContext(), this);
        loader.bind(this, requested);
    };

    VelaGuideBackdrop(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
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

    @Override public void setImageDrawable(Drawable drawable) {
        shown = null;
        if (paint != null) paint.setShader(null);
        super.setImageDrawable(drawable);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        shown = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!(getDrawable() instanceof BitmapDrawable)) return;
        Bitmap bitmap = ((BitmapDrawable)getDrawable()).getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;
        int w = getWidth(), h = getHeight();
        if (shown != bitmap) {
            float scale = Math.max((float)w / bitmap.getWidth(), (float)h / bitmap.getHeight());
            BitmapShader picture = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            transform.setScale(scale, scale);
            transform.postTranslate((w - bitmap.getWidth()*scale)/2f, (h - bitmap.getHeight()*scale)*.40f);
            picture.setLocalMatrix(transform);
            Shader edge;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                edge = new BitmapShader(MASK, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                transform.setScale((float)w/MASK.getWidth(),(float)h/MASK.getHeight());
                edge.setLocalMatrix(transform);
            } else {
                // Older GPU drivers can compose a bitmap and a gradient but not two bitmaps.
                edge = new LinearGradient(0,0,w,0,new int[]{0x00ffffff,0xffffffff,0xffffffff,0x00ffffff},
                        new float[]{0,.30f,.90f,1},Shader.TileMode.CLAMP);
            }
            paint.setShader(new ComposeShader(picture,edge,PorterDuff.Mode.DST_IN));
            shown = bitmap;
        }
        canvas.drawRect(0,0,w,h,paint);
    }
}
