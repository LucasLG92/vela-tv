package tv.vela.launcher;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Bitmap;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Emulator/device instrumentation; requires a privately linked Plex library with real artwork. */
public class ArtworkLifecycleTest extends Instrumentation {
    private Object field(Object owner,String name)throws Exception{if(owner instanceof ScreenActivity)owner=((ScreenActivity)owner).screen;for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void awaitArt(CinemaHomeView home)throws Exception{
        long until=SystemClock.uptimeMillis()+20_000;
        while(SystemClock.uptimeMillis()<until){final boolean[] ready={false};runOnMainSync(()->{try{List<ImageView> posters=(List<ImageView>)field(home,"posterImages");ready[0]=field(home,"heroBitmap")!=null&&!posters.isEmpty()&&posters.get(0).getDrawable()!=null;}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(150);}
        throw new AssertionError("Real Plex artwork did not load within 20 seconds");
    }
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();CinemaImages[] loader={null};try{
        Context c=getTargetContext();
        HomeActivity a=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        CinemaHomeView home=(CinemaHomeView)field(a,"home");awaitArt(home);
        runOnMainSync(()->{try{List<ImageView> posters=(List<ImageView>)field(home,"posterImages");check(posters.size()>10,"Test library needs at least eleven posters");check(posters.get(posters.size()-1).getDrawable()==null,"Far-offscreen poster should not be decoded");}catch(Exception e){throw new RuntimeException(e);}});
        for(int round=0;round<3;round++){
            startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Thread.sleep(250);
            runOnMainSync(()->{try{
                check(field(home,"heroBitmap")==null,"Hidden home retained its backdrop");
                for(ImageView poster:(List<ImageView>)field(home,"posterImages"))check(poster.getDrawable()==null,"Hidden home retained a poster");
                CinemaImages cache=(CinemaImages)field(home,"images");check(((android.util.LruCache)field(cache,"images")).size()==0,"Hidden home retained its bitmap cache");
                check(((java.util.Map)field(cache,"pending")).isEmpty(),"Hidden home retained pending requests");
            }catch(Exception e){throw new RuntimeException(e);}});
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitForIdleSync();awaitArt(home);
        }
        PlexClient plex=new PlexClient(c);PlexClient.Film first=plex.cached().get(0);
        CountDownLatch delivered=new CountDownLatch(1);AtomicInteger cancelled=new AtomicInteger(),surviving=new AtomicInteger();
        runOnMainSync(()->{
            loader[0]=new CinemaImages(c,plex);
            CinemaImages.Request ticket=loader[0].load(first.thumb,181,272,b->cancelled.incrementAndGet());
            loader[0].load(first.thumb,181,272,b->{check(b.getConfig()==Bitmap.Config.HARDWARE,"Expected immutable hardware artwork");surviving.incrementAndGet();delivered.countDown();});ticket.cancel();
        });
        check(delivered.await(20,TimeUnit.SECONDS),"Coalesced artwork request timed out");check(cancelled.get()==0,"Cancelled listener received artwork");check(surviving.get()==1,"Surviving listener did not receive exactly one image");
        AtomicInteger stale=new AtomicInteger();runOnMainSync(()->{loader[0].load(first.thumb,183,274,b->stale.incrementAndGet());loader[0].clear();});Thread.sleep(400);check(stale.get()==0,"Artwork arrived after cache release");
        runOnMainSync(()->loader[0].close());
        result.putString("result","PASS lazy posters, three hide/return cycles, complete bitmap release, coalesced cancellation, hardware decoding and stale callback rejection");finish(-1,result);
    }catch(Throwable e){if(loader[0]!=null)runOnMainSync(()->loader[0].close());result.putString("error",android.util.Log.getStackTraceString(e));finish(1,result);}}
}
