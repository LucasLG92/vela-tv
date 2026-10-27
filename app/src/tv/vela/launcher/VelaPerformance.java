package tv.vela.launcher;

import android.app.Activity;
import android.content.Context;
import android.os.*;
import android.view.*;
import java.io.File;
import java.util.*;

/** Explicit, temporary measurements of this process. Never a background monitor. */
final class VelaPerformance {
    private static volatile long lastReady=-1;
    static void firstFrame(View root,long created){ViewTreeObserver.OnDrawListener listener=new ViewTreeObserver.OnDrawListener(){boolean done;public void onDraw(){if(done)return;done=true;lastReady=Math.max(0,(SystemClock.elapsedRealtimeNanos()-created)/1_000_000);root.post(()->{if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnDrawListener(this);});}};root.getViewTreeObserver().addOnDrawListener(listener);}
    static final class Session implements AutoCloseable {
        private final Window window;private final HandlerThread thread=new HandlerThread("vela-frame-measurement",android.os.Process.THREAD_PRIORITY_BACKGROUND);private final Handler main=new Handler(Looper.getMainLooper());
        private long frames,slow,total,max;private boolean closed,listening;private long threshold;
        private final Runnable stop=this::close;private final Window.OnFrameMetricsAvailableListener listener=(w,f,dropped)->{long n=f.getMetric(FrameMetrics.TOTAL_DURATION);if(n<0||f.getMetric(FrameMetrics.FIRST_DRAW_FRAME)==1)return;synchronized(this){if(closed)return;frames++;total+=n;max=Math.max(max,n);if(n>threshold*1.5)slow++;}};
        Session(Activity a){window=a.getWindow();float hz=window.getDecorView().getDisplay()==null?60:window.getDecorView().getDisplay().getRefreshRate();threshold=(long)(1_000_000_000d/Math.max(24,hz));thread.start();try{window.addOnFrameMetricsAvailableListener(listener,new Handler(thread.getLooper()));listening=true;main.postDelayed(stop,30000);}catch(RuntimeException unavailable){close();}}
        synchronized String frames(){return frames==0?L10n.text("Navigate to collect frame timings"):String.format(Ui.locale(),L10n.text("%d frames · %.1f ms average · %.1f ms maximum · %d slow"),frames,total/(double)frames/1_000_000,max/1_000_000d,slow);}
        synchronized boolean active(){return !closed;}
        public synchronized void close(){if(closed)return;closed=true;main.removeCallbacks(stop);if(listening)window.removeOnFrameMetricsAvailableListener(listener);thread.quitSafely();}
    }
    static String sample(Context c,CancellationSignal cancel){cancel.throwIfCanceled();float pss=Debug.getPss()/1024f;Runtime runtime=Runtime.getRuntime();float heap=(runtime.totalMemory()-runtime.freeMemory())/1048576f,nativeHeap=Debug.getNativeHeapAllocatedSize()/1048576f;long size=0;int visited=0;ArrayDeque<File> pending=new ArrayDeque<>();pending.add(c.getCacheDir());while(!pending.isEmpty()&&visited++<5000){cancel.throwIfCanceled();File f=pending.removeFirst();if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)if(pending.size()<5000)pending.add(child);}else size+=Math.max(0,f.length());}return String.format(Ui.locale(),L10n.text("This process: %.1f MiB PSS\nJava heap: %.1f MiB · Native heap: %.1f MiB\nDisk cache: %.1f MiB"),pss,heap,nativeHeap,size/1048576f)+"\n"+L10n.text("Last home first draw")+": "+(lastReady<0?"—":lastReady+" ms");}
}
