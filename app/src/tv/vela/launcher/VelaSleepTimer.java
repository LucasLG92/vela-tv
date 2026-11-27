package tv.vela.launcher;

import android.os.*;
import java.util.*;
import java.util.function.LongSupplier;

/** One delayed callback owned by the current player. Never wakes a sleeping TV or survives playback. */
final class VelaSleepTimer implements AutoCloseable {
    private static final long MAX_DELAY=12*3600_000L;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LongSupplier wallTime;
    private final Runnable finish;
    private long deadline;
    private boolean choosing;

    VelaSleepTimer(Runnable action){this(action,System::currentTimeMillis);}
    VelaSleepTimer(Runnable action,LongSupplier wallTime){
        this.wallTime=wallTime;
        finish=()->{deadline=0;action.run();};
    }
    void after(long millis){
        if(millis<=0)return;
        close();
        long delay=Math.min(MAX_DELAY,millis);
        deadline=SystemClock.elapsedRealtime()+delay;
        main.postDelayed(finish,delay);
    }
    private long programmeDelay(long endSeconds){
        long now=wallTime.getAsLong();
        if(endSeconds<=now/1000 || endSeconds>(now+MAX_DELAY)/1000)return 0;
        return endSeconds*1000-now;
    }
    long remaining(){return Math.max(0,deadline-SystemClock.elapsedRealtime());}
    void items(List<VelaPlaybackPanel.Item> items,long programmeEnd){
        items.add(new VelaPlaybackPanel.Item(L10n.text("Sleep timer"),remaining()>0?String.format(Ui.locale(),L10n.text("Stops playback in %d min"),(remaining()+59_999)/60_000):L10n.text("Off · Vela playback only"),()->choosing=!choosing));
        if(!choosing)return;
        for(int n:new int[]{30,60})items.add(new VelaPlaybackPanel.Item(String.format(Ui.locale(),L10n.text("Stop in %d min"),n),"",()->after(n*60_000L)));
        if(programmeDelay(programmeEnd)>0)items.add(new VelaPlaybackPanel.Item(L10n.text("At the end of this programme"),L10n.text("Uses the current guide's end time"),()->after(programmeDelay(programmeEnd))));
        if(remaining()>0)items.add(new VelaPlaybackPanel.Item(L10n.text("Cancel sleep timer"),"",this::close));
    }
    @Override public void close(){main.removeCallbacksAndMessages(null);deadline=0;choosing=false;}
}
