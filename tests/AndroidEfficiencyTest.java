package tv.vela.launcher;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Owned-emulator regressions with synthetic artwork. No account or network is used. */
public final class AndroidEfficiencyTest extends Instrumentation {
    private Context context;
    private boolean baseline;
    private final Bundle result=new Bundle();
    private final List<CinemaImages> loaders=new ArrayList<>();
    private final CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
    private final CountDownLatch concurrent=new CountDownLatch(2);
    private final Map<String,AtomicInteger> calls=new ConcurrentHashMap<>();
    private byte[] poster,panorama,alpha;
    private String phase="setup";
    interface Condition {boolean ready()throws Exception;}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    void await(Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(condition.ready())return;Thread.sleep(15);}throw new AssertionError("Timeout: "+phase);}
    static byte[] png(int width,int height,boolean transparent)throws Exception{
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.drawColor(transparent?Color.TRANSPARENT:Color.BLUE);
        android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(Color.WHITE);
        canvas.drawRect(width/4f,height/4f,width*3/4f,height*3/4f,paint);
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){image.compress(Bitmap.CompressFormat.PNG,100,out);return out.toByteArray();}finally{image.recycle();}
    }
    String url(String path){return "https://vela-fixture.invalid/efficiency/"+path;}
    CinemaImages loader(){CinemaImages images=new CinemaImages(context,new LibraryClient(context));loaders.add(images);return images;}
    Bitmap load(CinemaImages images,String path,int width,int height)throws Exception{
        final Bitmap[] delivered={null};ui(()->images.loadTv(url(path),width,height,b->delivered[0]=b));
        await(()->delivered[0]!=null);return delivered[0];
    }
    int count(String path){AtomicInteger n=calls.get("/efficiency/"+path);return n==null?0:n.get();}
    void sizing()throws Exception{
        phase="bounded decode";CinemaImages images=loader();
        Bitmap p=load(images,"poster",180,270),wide=load(images,"panorama",352,198),logo=load(images,"alpha",650,182);
        result.putString("poster",p.getWidth()+"x"+p.getHeight()+" / "+p.getAllocationByteCount()+" bytes");
        result.putString("panorama",wide.getWidth()+"x"+wide.getHeight()+" / "+wide.getAllocationByteCount()+" bytes");
        result.putString("alpha",logo.getWidth()+"x"+logo.getHeight()+" / "+logo.getAllocationByteCount()+" bytes");
        check(p.getConfig()==Bitmap.Config.HARDWARE&&logo.hasAlpha(),"Hardware storage or logo transparency lost");
        Bitmap pixels=logo.copy(Bitmap.Config.ARGB_8888,false);try{check(Color.alpha(pixels.getPixel(0,0))==0&&pixels.getPixel(pixels.getWidth()/2,pixels.getHeight()/2)==Color.WHITE,"Logo's original alpha/pixels changed");}finally{pixels.recycle();}
        if(!baseline){check(p.getWidth()==180&&p.getHeight()==270,"Poster exceeds its display size");check(wide.getWidth()<=352&&wide.getHeight()<=198,"Panorama escapes its decode budget");check(Math.abs((double)wide.getWidth()/wide.getHeight()-16)<0.1,"Artwork aspect ratio changed");check(logo.getWidth()<=650&&logo.getHeight()<=182,"Logo exceeds its display size");}
        ui(images::close);
        check(!p.isRecycled()&&!logo.isRecycled(),"Closing the cache recycled a bitmap already delivered to a view");
    }
    void cancellation()throws Exception{
        phase="cancelled queue";CinemaImages images=loader();AtomicInteger cancelled=new AtomicInteger(),visible=new AtomicInteger();
        ui(()->{images.loadTv(url("block/1"),64,96,b->{});images.loadTv(url("block/2"),64,96,b->{});});
        check(entered.await(10,TimeUnit.SECONDS),"Workers did not enter the controlled download");
        ui(()->{List<CinemaImages.Request> requests=new ArrayList<>();for(int i=0;i<32;i++)requests.add(images.loadTv(url("abandoned/"+i),64,96,b->cancelled.incrementAndGet()));for(CinemaImages.Request r:requests)r.cancel();});
        ThreadPoolExecutor pool=(ThreadPoolExecutor)field(images,"io");int queued=pool.getQueue().size();
        result.putInt("cancelled_queue",queued);
        ui(()->images.loadTv(url("now-visible"),64,96,b->visible.incrementAndGet()));
        release.countDown();await(()->pool.getActiveCount()==0&&pool.getQueue().isEmpty());waitForIdleSync();
        result.putInt("visible_deliveries",visible.get());check(cancelled.get()==0,"Cancelled poster reached the UI");
        if(!baseline){check(queued==0,"Cancelled requests still occupy the bounded queue");check(visible.get()==1,"Visible poster rejected after abandoned requests");}
        phase="coalesced cancellation";AtomicInteger survivor=new AtomicInteger();int before=count("coalesced");
        ui(()->{CinemaImages.Request first=images.loadTv(url("coalesced"),64,96,b->cancelled.incrementAndGet());images.loadTv(url("coalesced"),64,96,b->survivor.incrementAndGet());first.cancel();});
        await(()->survivor.get()==1);check(count("coalesced")==before+1&&cancelled.get()==0,"Cancelling one consumer broke the shared request");
        phase="replace a decoded pending delivery";AtomicInteger replacement=new AtomicInteger();
        ui(()->{
            long completed=pool.getCompletedTaskCount();
            CinemaImages.Request old=images.loadTv(url("replace-delivery"),64,96,b->cancelled.incrementAndGet());
            long until=SystemClock.elapsedRealtime()+10000;
            while(pool.getCompletedTaskCount()==completed&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(5);
            check(pool.getCompletedTaskCount()>completed,"Decode did not finish while UI delivery was held");
            old.cancel();images.loadTv(url("replace-delivery"),64,96,b->replacement.incrementAndGet());
        });
        if(!baseline){await(()->replacement.get()==1);check(cancelled.get()==0,"An abandoned decoded delivery reached its former view");}
        phase="clear during pending delivery";AtomicInteger stale=new AtomicInteger();
        ui(()->{images.loadTv(url("clear"),64,96,b->stale.incrementAndGet());images.clear();});
        await(()->pool.getActiveCount()==0);waitForIdleSync();check(stale.get()==0,"Cleared image reached the UI");
        check(((Map<?,?>)field(images,"pending")).isEmpty(),"Clear retained image listeners");ui(images::close);
    }
    File cached(String path,int width,int height)throws Exception{
        String key="tv:"+url(path)+":"+width+"x"+height;StringBuilder name=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8")))name.append(String.format(Locale.ROOT,"%02x",b&255));return new File(new File(context.getCacheDir(),"plex-art"),name+".img");
    }
    void corruptCache()throws Exception{
        phase="corrupt cache recovery";CinemaImages images=loader();File file=cached("corrupt",64,96);file.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3,4});}
        AtomicInteger delivered=new AtomicInteger();ui(()->images.loadTv(url("corrupt"),64,96,b->delivered.incrementAndGet()));
        ThreadPoolExecutor pool=(ThreadPoolExecutor)field(images,"io");await(()->pool.getActiveCount()==0&&pool.getQueue().isEmpty());waitForIdleSync();
        result.putInt("corrupt_recovery",delivered.get());if(!baseline)check(delivered.get()==1&&count("corrupt")==1,"Fresh corrupt cache prevents bounded re-download");
        ui(images::close);
    }
    void sharedDisk()throws Exception{
        phase="concurrent disk writers";CinemaImages first=loader(),second=loader();AtomicInteger delivered=new AtomicInteger();
        ui(()->{first.loadTv(url("concurrent"),64,96,b->delivered.incrementAndGet());second.loadTv(url("concurrent"),64,96,b->delivered.incrementAndGet());});
        await(()->delivered.get()==2);check(count("concurrent")==2,"Fixture did not exercise independent disk writers");
        ui(()->{first.close();second.close();});
        File[] parts=new File(context.getCacheDir(),"plex-art").listFiles((folder,name)->name.endsWith(".part"));check(parts!=null&&parts.length==0,"An image writer left temporary files behind");
    }
    void programmeRebind()throws Exception{
        phase="shared programme rebind";final boolean[] cleared={false,false},reused={false};
        ui(()->{FrameLayout root=new FrameLayout(context);ImageView view=new ImageView(context);root.addView(view);VelaProgrammeArt art=new VelaProgrammeArt(context,root);Bitmap previous=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);
            try{view.setImageBitmap(previous);art.bind(view,"");cleared[0]=view.getDrawable()==null;
                view.setImageBitmap(previous);art.bind(view,url("new-programme"));cleared[1]=view.getDrawable()==null;
                try{Object binding=((Map<?,?>)field(art,"bindings")).get(view);view.setImageBitmap(previous);art.bind(view,url("new-programme"));reused[0]=binding==((Map<?,?>)field(art,"bindings")).get(view)&&view.getDrawable()!=null;}catch(Exception e){throw new RuntimeException(e);}
            }finally{art.close();previous.recycle();}
        });
        result.putBoolean("empty_programme_cleared",cleared[0]);result.putBoolean("changed_programme_cleared",cleared[1]);
        result.putBoolean("same_programme_reused",reused[0]);if(!baseline)check(cleared[0]&&cleared[1]&&reused[0],"New/empty programme retained old artwork or an unchanged binding was restarted");
    }
    void clockFit()throws Exception{
        phase="clock format bounds";String document=VelaWidgets.prefs(context).getString("document",null),language=L10n.choice();int clipped=0;
        try{
            for(String locale:Arrays.asList("en","es","pt","fr","de","it")){
                L10n.choose(context,locale);Ui.init(context);VelaWidgets.State state=new VelaWidgets.State();state.items.add("clock");state.format=2;VelaWidgets.save(context,state);
                final VelaWidgets.Strip[] strip={null};ui(()->strip[0]=new VelaWidgets.Strip(context));
                TextView clock=(TextView)field(strip[0],"clock");
                for(String suffix:new java.text.DateFormatSymbols(L10n.locale()).getAmPmStrings()){
                    final boolean[] fits={false};ui(()->{
                        clock.setText("11:59 "+suffix);strip[0].measure(View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(72,View.MeasureSpec.EXACTLY));strip[0].layout(0,0,500,72);
                        fits[0]=clock.getLayout()!=null&&clock.getLayout().getEllipsisCount(0)==0&&clock.getPaint().measureText(clock.getText().toString())<=clock.getWidth()-clock.getCompoundPaddingLeft()-clock.getCompoundPaddingRight()+1;
                    });if(!fits[0])clipped++;
                }
                ui(strip[0]::stop);
            }
        }finally{if(document==null)VelaWidgets.prefs(context).edit().remove("document").commit();else VelaWidgets.prefs(context).edit().putString("document",document).commit();L10n.choose(context,language);}
        result.putInt("clipped_clock_locales",clipped);if(!baseline)check(clipped==0,"12-hour clock is ellipsized in a supported language");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);baseline="baseline".equals(args.getString("fixture"));start();}
    @Override public void onStart(){context=getTargetContext();try{
        if(android.os.Build.VERSION.SDK_INT<30){programmeRebind();clockFit();result.putString("result",baseline?"BASELINE shared programme/clock behaviour recorded":"PASS shared programme rebinding and six-language clock bounds on Android "+android.os.Build.VERSION.SDK_INT);finish(-1,result);return;}
        check(!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"Synthetic test requires an account-free emulator");
        CinemaImages.purgeDisk(context);poster=png(2048,3072,false);panorama=png(4096,256,false);alpha=png(2000,400,true);
        RefinementHttp.install(u->{String path=u.getPath();calls.computeIfAbsent(path,k->new AtomicInteger()).incrementAndGet();if(path.contains("/block/")){entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Controlled download timed out");}if(path.endsWith("/concurrent")){concurrent.countDown();if(!concurrent.await(10,TimeUnit.SECONDS))throw new IOException("Independent writers did not overlap");}return path.endsWith("panorama")?panorama:path.endsWith("alpha")?alpha:poster;});
        sizing();cancellation();corruptCache();if(!baseline)sharedDisk();programmeRebind();clockFit();result.putString("result",baseline?"BASELINE recorded":"PASS bounded hardware decode, alpha/aspect, queue cancellation, coalesced consumers, clear isolation, corrupt-cache recovery, independent disk writers, shared programme rebinding and six-language clock bounds");
    }catch(Throwable error){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(error));}finally{release.countDown();for(CinemaImages images:loaders)ui(images::close);}
        finish(result.containsKey("error")?1:-1,result);
    }
}
