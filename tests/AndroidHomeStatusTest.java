package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.ExecutorService;

/** Checks real shared weather, readable native header and resource cleanup without changing preferences. */
public class AndroidHomeStatusTest extends Instrumentation {
    private Object field(Object object,String name)throws Exception{if(object instanceof ScreenActivity)object=((ScreenActivity)object).screen;for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void set(Object object,String name,Object value)throws Exception{if(object instanceof ScreenActivity)object=((ScreenActivity)object).screen;for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private interface Ready{boolean get()throws Exception;}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void await(Ready ready,String message)throws Exception{
        long until=SystemClock.uptimeMillis()+30_000;
        while(SystemClock.uptimeMillis()<until){boolean[] done={false};ui(()->{try{done[0]=ready.get();}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;Thread.sleep(150);}
        throw new AssertionError(message);
    }
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();SettingsActivity settings=null;CinemaHomeView home=null;HomeStatusView header=null;
        try{
            Context c=getTargetContext();String location=AmbientWeather.name(c);
            HomeActivity activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            home=(CinemaHomeView)field(activity,"home");header=(HomeStatusView)field(home,"headerStatus");final CinemaHomeView current=home;final HomeStatusView view=header;
            await(()->field(view,"snapshot")!=null,"Real weather did not arrive");
            AmbientWeather.Snapshot actual=(AmbientWeather.Snapshot)field(header,"snapshot");check(actual.usable(System.currentTimeMillis()),"Expired weather displayed as current");
            check(actual.city.equals(location),"Home did not reuse the user's weather city");
            check(((TextView)field(header,"temperature")).getText().toString().equals(actual.degrees()),"Real temperature missing");
            check(((TextView)field(header,"city")).getText().toString().equals(location),"Weather city missing");
            check(((TextView)field(header,"clock")).getText().toString().matches("[0-2]?[0-9]:[0-5][0-9]"),"Invalid clock");
            float clockSize=((TextView)field(header,"clock")).getTextSize();check(clockSize>=28&&clockSize<=34,"Clock must be readable and compact");
            check(header.getBackground()==null&&header.getRight()<=((View)header.getParent()).getWidth()&&header.getLayoutParams().height<=72,"Header exceeds its bounded canvas");
            check(((ImageView)field(header,"icon")).getDrawable()!=null,"Weather icon missing");
            check(!header.isFocusable()&&!header.hasFocus(),"Widget group stole initial remote focus");
            AmbientWeather first=(AmbientWeather)field(header,"weather");ui(()->{current.resume();current.resume();});check(field(header,"weather")==first,"Repeated resume duplicated weather clients");
            // Check stale/expired presentation in memory; never write invented values into the weather cache.
            ui(()->{try{set(view,"snapshot",new AmbientWeather.Snapshot(actual.city,actual.zone,actual.temperature,actual.code,actual.day,System.currentTimeMillis()-AmbientWeather.FRESH-1000));view.updateClock();check(((TextView)field(view,"city")).getText().toString().contains(Ui.tr("Últimos dados","Last update","Últimos datos")),"Stale reading not identified visibly");set(view,"snapshot",new AmbientWeather.Snapshot(actual.city,actual.zone,actual.temperature,actual.code,actual.day,System.currentTimeMillis()-AmbientWeather.MAX_AGE-1000));view.updateClock();check(((TextView)field(view,"temperature")).getText().toString().equals("—"),"Expired temperature displayed as real");set(view,"snapshot",actual);view.updateClock();}catch(Exception e){throw new RuntimeException(e);}});
            await(()->field(current,"heroBitmap")!=null,"Real Plex backdrop missing");
            Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"Screenshot unavailable");try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"home-clock-weather.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            for(int round=0;round<3;round++){
                AmbientWeather client=(AmbientWeather)field(header,"weather");
                settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
                check(field(header,"weather")==null&&(Boolean)field(client,"closed"),"Hidden home kept weather active");
                check(((ExecutorService)field(client,"io")).isShutdown(),"Hidden home kept weather worker");
                check(field(header,"snapshot")==null&&((ImageView)field(header,"icon")).getDrawable()==null,"Hidden home retained weather objects");
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();settings=null;
                await(()->field(view,"snapshot")!=null,"Weather missing after return");
            }
            check(AmbientWeather.name(c).equals(location),"Weather location changed during test");
            result.putString("city",location);result.putString("result","PASS real shared weather and city, compact readable clock without a panel, sky icon, preserved remote focus, single weather client, visible stale/expired states and three pause/resume cycles releasing workers and images. No preferences changed.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{if(settings!=null){SettingsActivity old=settings;ui(()->old.finish());}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
