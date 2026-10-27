package tv.vela.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;

/** One short focus treatment; geometry and the surrounding layout never move. */
final class VelaFocus {
    static boolean reduced(Context c){return !ValueAnimator.areAnimatorsEnabled()||VelaWorkspace.prefs(c,"visuals").getBoolean("reducedMotion",false)||c.getPackageName().endsWith(".lite")&&VelaWorkspace.prefs(c,"lite_home").getBoolean("reduce_motion",false);}
    static void card(View v,boolean focused){v.animate().cancel();float scale=focused&&!reduced(v.getContext())?1.028f:1;float y=focused&&!reduced(v.getContext())?-2:0;v.animate().scaleX(scale).scaleY(scale).translationY(y).translationZ(focused?10:0).setDuration(reduced(v.getContext())?0:130).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();}
}
