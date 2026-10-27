package tv.vela.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import java.util.concurrent.*;

/** Visual preferences and workers that retire after ten idle seconds. */
final class Visuals {
    private Visuals() {}
    static SharedPreferences prefs(Context c) { return VelaWorkspace.prefs(c,"visuals"); }
    static boolean light(Context c) {
        ActivityManager m=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        return prefs(c).getBoolean("light",m!=null&&m.isLowRamDevice());
    }
    static boolean reducedMotion(Context c) { return prefs(c).getBoolean("reducedMotion",false); }
    static int duration(Context c,int millis) {
        return reducedMotion(c)||!android.animation.ValueAnimator.areAnimatorsEnabled()?0:millis;
    }
    static void filterShader(android.graphics.BitmapShader shader,boolean linear) {
        if(android.os.Build.VERSION.SDK_INT>=33)Api33.filterShader(shader,linear);
    }
    private static final class Api33 {
        static void filterShader(android.graphics.BitmapShader shader,boolean linear) {
            shader.setFilterMode(linear?android.graphics.BitmapShader.FILTER_MODE_LINEAR:android.graphics.BitmapShader.FILTER_MODE_NEAREST);
        }
    }
    static int cacheKb(Context c) { return light(c)?3072:6144; }
    static ThreadPoolExecutor worker(int threads) {
        ThreadPoolExecutor pool=new ThreadPoolExecutor(threads,threads,10,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),r->new Thread(()->{Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);r.run();},"vela-io"));
        pool.allowCoreThreadTimeOut(true);return pool;
    }
}
