package tv.vela.launcher;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Deterministic guide boundaries and actual channel-card updates, without starting video. */
public class AndroidTvProgressTest extends Instrumentation {
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private void call(Object o,String name,Class<?>[] types,Object...args)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private interface Ready{boolean ready()throws Exception;}
    private void await(Ready condition,String why)throws Exception{long end=SystemClock.uptimeMillis()+10_000;while(SystemClock.uptimeMillis()<end){boolean[] ok={false};ui(()->{try{ok[0]=condition.ready();}catch(Exception e){throw new RuntimeException(e);}});if(ok[0])return;Thread.sleep(80);}throw new AssertionError(why);}
    private void update(LiveTvActivity a,long now){ui(()->{try{call(a,"updateProgrammes",new Class<?>[]{long.class},now);}catch(Exception e){throw new RuntimeException(e);}});}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();LiveTvActivity activity=null;
        try{
            Locale.setDefault(new Locale("es","ES"));
            Map<String,?> prefs=new HashMap<>(LiveTvData.prefs(c).getAll()),plex=new HashMap<>(new PlexClient(c).prefs.getAll());String[] sidebar=SidebarApps.slots(c);
            long base=System.currentTimeMillis()/60_000*60;LiveTvData.Programme sample=new LiveTvData.Programme(base-1500,base+1500,"Actual");
            check(sample.remainingMinutes(base)==25&&sample.progress(base)==500,"Incorrect duration or halfway progress");
            check(sample.remainingMinutes(base+1440)==1&&sample.remainingMinutes(base+1499)==1,"Last minute rounded down to zero");
            check(sample.remainingMinutes(base+1500)==0&&sample.progress(base+1500)==0,"Expired programme retained countdown");
            check(sample.remainingMinutes(base-1501)==0&&sample.progress(base-1501)==0,"Future programme displayed a countdown");
            check(new LiveTvData.Programme(base,base,"").remainingMinutes(base)==0,"Zero duration was accepted");
            check(new LiveTvData.Programme(base+1,base,"Invalid").progress(base)==0,"Invalid duration was accepted");
            activity=(LiveTvActivity)startActivitySync(new Intent(c,LiveTvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LiveTvActivity a=activity;
            await(()->a.hasWindowFocus(),"TV browser did not receive focus");
            List<LiveTvData.Channel> channels=new ArrayList<>();Map<String,LiveTvData.Guide> guide=new HashMap<>();
            for(int i=0;i<14;i++){
                channels.add(new LiveTvData.Channel("progress-"+i,"Canal "+i,"Prueba","",Collections.singletonList("https://example.org/live.m3u8")));
                LiveTvData.Guide entry=new LiveTvData.Guide();
                if(i==0){entry.add(sample,base);entry.add(new LiveTvData.Programme(base+1500,base+3300,"Siguiente"),base);}
                else if(i==2)entry.now=new LiveTvData.Programme(base-120,base-1,"Caducado");
                else if(i==3)entry.add(new LiveTvData.Programme(base+600,base+1200,"Futuro"),base);
                else if(i==4)entry.add(new LiveTvData.Programme(base,base,"Inválido"),base);
                else if(i==5)entry.add(new LiveTvData.Programme(base-120,base+1,"Finalizando"),base);
                else if(i==6){entry.now=new LiveTvData.Programme(base-120,base-1,"Anterior");entry.next=new LiveTvData.Programme(base+300,base+900,"Tras el hueco");}
                else if(i!=1)entry.add(sample,base);
                if(i!=1)guide.put("progress-"+i,entry);
            }
            ui(()->{try{
                set(a,"generation",((Integer)field(a,"generation"))+1);
                for(String job:new String[]{"catalogJob","guideJob"}){Future<?> pending=(Future<?>)field(a,job);if(pending!=null)pending.cancel(true);}
                set(a,"guideLoading",false);set(a,"guideAt",SystemClock.elapsedRealtime());set(a,"group","@all");
                set(a,"loading",false);set(a,"total",14);a.catalog=new LiveTvData.Catalog(LiveTvData.source(c),new ArrayList<>(channels.subList(0,12)),false);set(a,"guide",guide);call(a,"renderCards",new Class<?>[]{int.class,boolean.class},0,true);
            }catch(Exception e){throw new RuntimeException(e);}});
            update(a,base);
            List<TextView> labels=(List<TextView>)field(a,"remainingLabels");List<View> bars=(List<View>)field(a,"programmeBars"),cards=(List<View>)field(a,"cards");
            check(labels.size()==12&&bars.size()==12&&cards.size()==12,"Countdowns retained more than one channel page");
            check(labels.get(0).getText().toString().equals("Quedan 25 min")&&((Integer)field(bars.get(0),"progress"))==500,"Card countdown/progress incorrect");
            check(labels.get(5).getText().toString().equals("Queda 1 min"),"Singular/last minute incorrect");
            for(int i:new int[]{1,2,3,4,6})check(labels.get(i).getVisibility()!=View.VISIBLE&&bars.get(i).getVisibility()!=View.VISIBLE,"Unknown/invalid/expired/future/gap programme invented a time");
            check(labels.get(7).getVisibility()==View.VISIBLE,"A direct channel lost its guide countdown");
            ui(()->cards.get(0).requestFocus());sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();View focused=a.getCurrentFocus(),bar=bars.get(0);
            update(a,base+60);check(labels.get(0).getText().toString().equals("Quedan 24 min"),"Countdown did not decrease");
            check(focused==a.getCurrentFocus()&&((List<?>)field(a,"programmeBars")).get(0)==bar,"Minute update rebuilt cards or stole remote focus");
            check(labels.get(5).getText().length()==0&&bars.get(5).getVisibility()!=View.VISIBLE,"Expired card retained its time");
            update(a,base+1500);check(labels.get(0).getText().toString().equals("Quedan 30 min")&&((List<TextView>)field(a,"nowLabels")).get(0).getText().toString().equals("Siguiente")&&((Integer)field(bar,"progress"))==0,"Next programme did not replace countdown/progress together");
            update(a,base+3300);check(labels.get(0).getVisibility()!=View.VISIBLE&&labels.get(0).getText().length()==0&&((Integer)field(bar,"progress"))==0,"End of guide retained stale countdown");
            ui(()->{try{set(a,"page",1);a.catalog=new LiveTvData.Catalog(LiveTvData.source(c),new ArrayList<>(channels.subList(12,14)),false);call(a,"renderCards",new Class<?>[]{int.class,boolean.class},1,true);}catch(Exception e){throw new RuntimeException(e);}});update(a,base);
            check(((List<?>)field(a,"programmeBars")).size()==2&&((List<?>)field(a,"cards")).size()==2,"Paging retained countdown views from old page");
            check(a.playback==null,"Guide updates started playback");
            Handler main=(Handler)field(a,"main");Runnable tick=(Runnable)field(a,"tick");check(main.hasCallbacks(tick),"Minute refresh was not scheduled");
            ui(()->a.finish());await(()->(Boolean)field(a,"closed"),"TV browser did not close");
            check(!main.hasCallbacks(tick)&&((ExecutorService)field(a,"io")).isShutdown(),"Closed screen retained minute tick or worker");
            check(plex.equals(new PlexClient(c).prefs.getAll())&&Arrays.equals(sidebar,SidebarApps.slots(c)),"Countdown changed accounts, sources, favorites or shortcuts");
            result.putString("result","PASS minute rounding, progress bounds, missing/expired/future/invalid/gap EPG, final minute, automatic next-programme transition, direct-channel timing, unchanged card views and DPAD focus, bounded page count, no automatic playback, tick/worker release. Preferences and accounts preserved.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null){LiveTvActivity a=activity;ui(()->{if(!a.isFinishing())a.finish();});}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
