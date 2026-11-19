package tv.vela.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Debug;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/** Repeatable Home / TV route with a fixed local EPG, synthetic profile and bundled art. */
public final class AndroidNavigationCostTest extends AndroidDiscoveryUiTest {
    private static final int SAMPLES=5, DRAWS=60;

    private long allocated(){return Long.parseLong(Debug.getRuntimeStat("art.gc.bytes-allocated"));}

    private long settledMemory(){
        System.gc();System.runFinalization();SystemClock.sleep(350);
        return Debug.getPss();
    }

    private void settled()throws Exception{
        java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);
        ui(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(frame::countDown)));
        check(frame.await(4,java.util.concurrent.TimeUnit.SECONDS),"No rendered frame");
    }

    private void sampleDraws(TvTimelineView grid, long[] cpu, long[] bytes, int slot){
        ui(()->{
            Bitmap target=Bitmap.createBitmap(grid.getWidth(),grid.getHeight(),Bitmap.Config.ARGB_8888);
            try{
                Canvas canvas=new Canvas(target);
                for(int i=0;i<8;i++)grid.draw(canvas);
                long start=Debug.threadCpuTimeNanos(),before=allocated();
                for(int i=0;i<DRAWS;i++)grid.draw(canvas);
                bytes[slot]=(allocated()-before)/DRAWS;
                cpu[slot]=(Debug.threadCpuTimeNanos()-start)/DRAWS;
            }finally{target.recycle();}
        });
    }

    private void metric(Bundle out,String key,long[] values){
        long[] sorted=values.clone();Arrays.sort(sorted);
        out.putString(key,sorted[sorted.length/2]+" median; samples "+Arrays.toString(values));
    }

    @Override public void onStart(){
        Bundle out=new Bundle();
        context=getTargetContext();
        L10n.init(context,"auto");
        String oldLanguage=L10n.choice();
        try{
            try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
            previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Navigation benchmark");VelaWorkspace.select(context,profile);
            L10n.choose(context,"en");
            Map<String,byte[]> media=new java.util.HashMap<>();
            media.put("/art/sintel.jpg",RefinementHttp.asset(getContext(),"sintel-guide.jpg"));
            StringBuilder playlist=new StringBuilder("#EXTM3U\n");
            for(int i=0;i<CHANNELS.length;i++){
                media.put("/logo/"+LOGOS[i],RefinementHttp.asset(getContext(),LOGOS[i]));
                playlist.append("#EXTINF:-1 tvg-id=\"discovery-").append(i).append("\" tvg-logo=\"https://vela-fixture.invalid/logo/").append(LOGOS[i])
                    .append("\" group-title=\"Generalistas\",").append(CHANNELS[i]).append("\nhttps://vela-fixture.invalid/live/").append(i).append(".ts\n");
            }
            media.put("/discovery.m3u",RefinementHttp.text(playlist.toString()));
            RefinementHttp.install(url->media.get(url.getPath()));
            activity=launch();settled();
            CinemaHomeView home=(CinemaHomeView)field(activity,"home");
            Method brand=CinemaHomeView.class.getDeclaredMethod("brand",int.class);brand.setAccessible(true);
            ui(()->{try{brand.invoke(home,1);brand.invoke(home,2);}catch(Exception e){throw new AssertionError(e);}});
            long logoBytes=0;for(Bitmap b:(Bitmap[])field(home,"brandBitmaps")){check(b!=null,"Missing provider mark");logoBytes+=b.getAllocationByteCount();}
            out.putLong("home_provider_bitmap_bytes",logoBytes);
            long[] enter=new long[SAMPLES],back=new long[SAMPLES],cpu=new long[SAMPLES],bytes=new long[SAMPLES],pss=new long[SAMPLES],homePss=new long[SAMPLES];
            for(int i=-1;i<SAMPLES;i++){
                phase="guide entry "+i;
                long start=SystemClock.elapsedRealtimeNanos();ui(this::television);
                try{await(()->timeline()!=null&&!((Map<?,?>)field(timeline(),"programmes")).isEmpty());}
                catch(AssertionError timeout){
                    Object tv=field(home,"liveTv");
                    if(tv!=null)out.putString("guide_state","mode="+field(tv,"mode")+", guide="+field(tv,"guideMode")+", rows="+((java.util.List<?>)field(tv,"channels")).size()+", busy="+field(tv,"loading")+", status="+((android.widget.TextView)field(tv,"status")).getText());
                    try{
                        M3uClient m=new M3uClient(context);m.refresh(false);
                        out.putInt("fixture_groups",m.groups().size());
                        out.putInt("fixture_channels",TvPlayerCatalog.page(context,"m3u","","","",0).total);
                    }catch(Exception e){out.putString("fixture_problem",android.util.Log.getStackTraceString(e));}
                    throw timeout;
                }
                settled();long elapsed=(SystemClock.elapsedRealtimeNanos()-start)/1000000;
                TvTimelineView grid=(TvTimelineView)timeline();ui(grid::requestFocus);
                phase="programme image "+i;
                await(()->((android.widget.ImageView)field(field(home,"liveTv"),"guideBackdrop")).getDrawable()!=null);
                if(i>=0){enter[i]=elapsed;pss[i]=settledMemory();}
                start=SystemClock.elapsedRealtimeNanos();ui(()->home.navigateSection(0));settled();
                check(field(home,"liveTv")==null,"Guide not released on return");
                if(i>=0){back[i]=(SystemClock.elapsedRealtimeNanos()-start)/1000000;homePss[i]=settledMemory();}
            }
            // Software raster targets for the draw benchmark must not affect the navigation PSS samples.
            ui(this::television);
            await(()->timeline()!=null&&!((Map<?,?>)field(timeline(),"programmes")).isEmpty());
            TvTimelineView measuredGrid=(TvTimelineView)timeline();
            ui(measuredGrid::requestFocus);
            await(()->((android.widget.ImageView)field(field(home,"liveTv"),"guideBackdrop")).getDrawable()!=null);
            for(int i=0;i<SAMPLES;i++)sampleDraws(measuredGrid,cpu,bytes,i);
            metric(out,"guide_entry_ms",enter);metric(out,"home_return_ms",back);
            metric(out,"guide_draw_cpu_ns",cpu);metric(out,"guide_draw_allocated_bytes",bytes);
            metric(out,"guide_process_pss_kib",pss);metric(out,"home_process_pss_kib",homePss);
            out.putString("result","PASS measured five warm Home / guide round trips, a six-channel guide and 60 repeated draws per sample");
        }catch(Throwable t){out.putString("error",phase+" "+android.util.Log.getStackTraceString(t));}
        finally{try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}L10n.choose(context,oldLanguage);}catch(Exception e){out.putString("cleanup",e.toString());}}
        finish(out.containsKey("error")||out.containsKey("cleanup")?1:-1,out);
    }
}
