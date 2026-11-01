package tv.vela.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.widget.FrameLayout;

/** A short, bounded focus transition. No resident clock, blur layer, texture or extra artwork. */
final class VelaPosterCard extends FrameLayout {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rect=new RectF();
    private ValueAnimator motion;private float emphasis,shade;private boolean focused,dimmed;private final int pictureHeight,accent;private final boolean clear;
    VelaPosterCard(Context c,int height){super(c);pictureHeight=height;accent=VelaReading.accent(c);clear=VelaLayout.clearFocus(c)||VelaReading.prefs(c).getBoolean("contrast",false);setClipChildren(false);}
    void focus(boolean yes){
        focused=yes;animate().cancel();boolean reduced=VelaFocus.reduced(getContext());
        animate().scaleX(yes&&!reduced?1.055f:1).scaleY(yes&&!reduced?1.055f:1).translationY(0).translationZ(yes?12:0).setDuration(reduced?0:160).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();transition();
    }
    void context(boolean dim){if(dimmed==dim)return;dimmed=dim;transition();}
    private void transition(){
        if(motion!=null){motion.cancel();motion=null;}float target=focused?1:0,dark=dimmed&&!focused?1:0;
        if(VelaFocus.reduced(getContext())){emphasis=target;shade=dark;invalidate();return;}if(emphasis==target&&shade==dark)return;
        final float from=emphasis,fromShade=shade;motion=ValueAnimator.ofFloat(0,1);motion.setDuration(160);motion.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));motion.addUpdateListener(a->{float t=(Float)a.getAnimatedValue();emphasis=from+(target-from)*t;shade=fromShade+(dark-fromShade)*t;invalidate();});motion.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){if(motion==a)motion=null;}});motion.start();
    }
    protected void dispatchDraw(Canvas c){super.dispatchDraw(c);
        // Draw a small tint over existing pixels: no view-alpha layer or duplicate artwork bitmap.
        if(shade>0){rect.set(0,0,getWidth(),pictureHeight);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.BLACK);paint.setAlpha(Math.round(48*shade));c.drawRoundRect(rect,14,14,paint);}
        if(emphasis<=0)return;paint.setStyle(Paint.Style.STROKE);
        float inset=clear?3:1;rect.set(-inset,-inset,getWidth()+inset,pictureHeight+inset);paint.setColor(0xFF090A0D);paint.setAlpha(Math.round((clear?242:160)*emphasis));paint.setStrokeWidth(clear?11:5);c.drawRoundRect(rect,14+inset,14+inset,paint);
        paint.setColor(clear?Color.WHITE:accent);paint.setAlpha(Math.round((clear?255:220)*emphasis));paint.setStrokeWidth(clear?5:2.2f);c.drawRoundRect(rect,14+inset,14+inset,paint);
    }
    protected void onDetachedFromWindow(){if(motion!=null){motion.cancel();motion=null;}animate().cancel();super.onDetachedFromWindow();}
}
