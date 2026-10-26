package tv.vela.launcher;

import android.os.*;
import java.util.*;

/** One delayed callback owned by the current player. Never wakes a sleeping TV or survives playback. */
final class VelaSleepTimer implements AutoCloseable {
    private final Handler main=new Handler(Looper.getMainLooper());private final Runnable stop;private long deadline;
    VelaSleepTimer(Runnable action){stop=action;finish=()->{deadline=0;stop.run();};}
    private final Runnable finish;private boolean choosing;
    void after(long millis){close();if(millis<=0)return;deadline=SystemClock.elapsedRealtime()+Math.min(12*3600_000L,millis);main.postDelayed(finish,deadline-SystemClock.elapsedRealtime());}
    long remaining(){return Math.max(0,deadline-SystemClock.elapsedRealtime());}
    void items(List<VelaPlaybackPanel.Item> items,long programmeEnd){
        items.add(new VelaPlaybackPanel.Item(L10n.text("Sleep timer"),remaining()>0?String.format(Ui.locale(),L10n.text("Stops playback in %d min"),(remaining()+59_999)/60_000):L10n.text("Off · Vela playback only"),()->choosing=!choosing));
        if(!choosing)return;
        for(int n:new int[]{30,60})items.add(new VelaPlaybackPanel.Item(String.format(Ui.locale(),L10n.text("Stop in %d min"),n),"",()->after(n*60_000L)));
        long delay=programmeEnd*1000-System.currentTimeMillis();if(delay>0&&delay<=12*3600_000L)items.add(new VelaPlaybackPanel.Item(L10n.text("At the end of this programme"),L10n.text("Uses the current guide's end time"),()->after(programmeEnd*1000-System.currentTimeMillis())));
        if(remaining()>0)items.add(new VelaPlaybackPanel.Item(L10n.text("Cancel sleep timer"),"",this::close));
    }
    @Override public void close(){main.removeCallbacksAndMessages(null);deadline=0;choosing=false;}
}
