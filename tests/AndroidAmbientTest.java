package tv.vela.launcher;

import android.app.*;
import android.os.*;
import android.content.*;
import android.media.MediaPlayer;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.view.PixelCopy;
import java.util.concurrent.*;
import java.lang.reflect.*;
import java.util.*;

/** Real-stream and real-library test; does not change the TV's home or screensaver settings. */
public class AndroidAmbientTest extends Instrumentation {
    interface Condition {boolean ready()throws Exception;}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void await(Condition c,String why)throws Exception{
        long end=SystemClock.uptimeMillis()+30_000;
        while(SystemClock.uptimeMillis()<end){boolean[] ready={false};Throwable[] error={null};runOnMainSync(()->{try{ready[0]=c.ready();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);if(ready[0])return;Thread.sleep(150);}
        throw new AssertionError(why);
    }
    private void visiblePlex(SettingsActivity activity,AmbientView view)throws Exception{
        await(()->{ImageView image=(ImageView)field(view,"currentPhoto");return image!=null&&image.getAlpha()>.98f&&image.getImageAlpha()==255;},"Plex image remained transparent");
        Bitmap screenshot=Bitmap.createBitmap(96,54,Bitmap.Config.ARGB_8888);CountDownLatch ready=new CountDownLatch(1);int[] copied={-1};
        runOnMainSync(()->PixelCopy.request(activity.getWindow(),screenshot,result->{copied[0]=result;ready.countDown();},new Handler(Looper.getMainLooper())));
        check(ready.await(5,TimeUnit.SECONDS)&&copied[0]==PixelCopy.SUCCESS,"Could not inspect rendered Plex pixels");
        int visible=0;for(int y=5;y<45;y+=5)for(int x=5;x<90;x+=5)if((screenshot.getPixel(x,y)&0xFFFFFF)>0x080808)visible++;
        screenshot.recycle();check(visible>20,"Plex preview rendered black instead of its real image");
    }
    private void released(AmbientView view)throws Exception{
        check(!(Boolean)field(view,"active"),"Ambient remained active after exit");check(field(view,"player")==null,"MediaPlayer retained after exit");check(field(view,"images")==null,"Image loader retained after exit");check(field(view,"io")==null,"Worker retained after exit");
        check(((ImageView)field(view,"photoA")).getDrawable()==null&&((ImageView)field(view,"photoB")).getDrawable()==null,"Backdrop retained after exit");
    }
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();Map<String,?> saved=Collections.emptyMap();Context c=getTargetContext();SettingsActivity activity=null;
        try{
            saved=new HashMap<>(AmbientPrefs.get(c).getAll());
            AmbientPrefs.get(c).edit().putString("style","aerial").putString("theme","landscape").putBoolean("clock",false).commit();
            activity=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final SettingsActivity a=activity;
            runOnMainSync(()->a.showPreview());waitForIdleSync();AmbientView aerial=(AmbientView)field(a,"preview");
            await(()->(Boolean)field(aerial,"videoStarted"),"Aerial stream never rendered a frame");
            MediaPlayer initial=(MediaPlayer)field(aerial,"player");check(initial.isPlaying(),"Real aerial was not playing");
            check((Integer)field(aerial,"videoWidth")==1920,"Expected an actual 1080p film");
            int oldIndex=(Integer)field(aerial,"index");runOnMainSync(()->initial.seekTo(Math.max(0,initial.getDuration()-1200),MediaPlayer.SEEK_CLOSEST));
            await(()->(Integer)field(aerial,"index")!=oldIndex&&(Boolean)field(aerial,"videoStarted"),"Next aerial did not play after completion");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitForIdleSync();released(aerial);check(field(a,"preview")==null,"Preview did not close");
            AmbientPrefs.get(c).edit().putString("style","plex").putBoolean("clock",true).commit();runOnMainSync(()->a.showPreview());waitForIdleSync();AmbientView plex=(AmbientView)field(a,"preview");
            await(()->field(plex,"currentPhoto")!=null,"Plex did not supply a real backdrop");check(field(plex,"player")==null,"Plex mode allocated a video player");visiblePlex(a,plex);
            ImageView firstPhoto=(ImageView)field(plex,"currentPhoto");int first=(Integer)field(plex,"index");Method next=AmbientView.class.getDeclaredMethod("nextPhoto");next.setAccessible(true);runOnMainSync(()->{try{next.invoke(plex);}catch(Exception e){throw new RuntimeException(e);}});
            await(()->(Integer)field(plex,"index")!=first&&field(plex,"currentPhoto")!=firstPhoto,"Plex slideshow did not advance");visiblePlex(a,plex);
            runOnMainSync(()->{plex.stop();plex.start();});await(()->field(plex,"currentPhoto")!=null,"Screensaver did not restart after stopping");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();released(plex);
            result.putString("result","PASS real silent 1080p aerial playback, automatic next film, Back cleanup, real Plex backdrops, slideshow, restart and OK cleanup");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{
            SharedPreferences.Editor edit=AmbientPrefs.get(c).edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();if(v instanceof String)edit.putString(entry.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(entry.getKey(),(Integer)v);}
            edit.commit();if(activity!=null){SettingsActivity a=activity;runOnMainSync(()->a.finish());}
        }
        finish(result.containsKey("error")?1:-1,result);
    }
}
