package tv.vela.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Show the entire programme still within the guide header, including portrait artwork. */
final class VelaGuideBackdrop extends ImageView {
    private VelaProgrammeArt loader;
    private String requested = "";
    private final Runnable fetch = () -> {
        if (!isShown() || requested.isEmpty()) return;
        if (loader == null) loader = new VelaProgrammeArt(getContext(), this);
        loader.bind(this, requested);
    };

    VelaGuideBackdrop(Context context) {
        super(context);
        setScaleType(ScaleType.FIT_CENTER);
        setBackground(VelaTheme.shape(0xff15191b,12));
        getBackground().setAlpha(0);
        setClipToOutline(true);
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
        super.setImageDrawable(drawable);
        if(getBackground()!=null)getBackground().setAlpha(drawable==null?0:255);
    }
}
