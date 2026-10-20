package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;

/** Uses the linked profile read-only; never writes credentials, account data or server preferences. */
public class AndroidAccountHeaderTest extends Instrumentation {
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private interface Ready{boolean get()throws Exception;}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void await(Ready ready,String why)throws Exception{
        long until=SystemClock.uptimeMillis()+45_000;
        while(SystemClock.uptimeMillis()<until){boolean[] done={false};ui(()->{try{done[0]=ready.get();}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;Thread.sleep(150);}
        throw new AssertionError(why);
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();SettingsActivity settings=null;ActivityMonitor monitor=null;HomeActivity activity=null;
        try{
            Context c=getTargetContext();PlexClient plex=new PlexClient(c);check(plex.connected(),"Plex must already be linked");
            activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            CinemaHomeView home=(CinemaHomeView)field(activity,"home");ImageView avatar=(ImageView)field(home,"accountIcon");TextView title=(TextView)field(home,"accountTitle"),initial=(TextView)field(home,"accountInitial");View account=(View)field(home,"account");
            ui(()->{try{CinemaImages.Request request=(CinemaImages.Request)field(home,"avatarRequest");boolean pending=request!=null&&request.pending();home.resume();home.resume();if(pending)check(field(home,"avatarRequest")==request,"Repeated resume replaced a pending profile request");}catch(Exception e){throw new RuntimeException(e);}});
            try{await(()->avatar.getDrawable() instanceof BitmapDrawable&&initial.getVisibility()==View.GONE,"Real linked Plex profile avatar did not load");}
            catch(AssertionError error){CinemaImages.Request request=(CinemaImages.Request)field(home,"avatarRequest");throw new AssertionError("Avatar unavailable: active="+field(home,"active")+", released="+field(home,"artReleased")+", pending="+(request!=null&&request.pending())+", drawable="+(avatar.getDrawable()!=null)+", initial="+initial.getVisibility(),error);}
            Bitmap bitmap=((BitmapDrawable)avatar.getDrawable()).getBitmap();check(bitmap.getWidth()<=96&&bitmap.getHeight()<=96,"Avatar is not bounded to 96 pixels");check(avatar.getClipToOutline(),"Avatar is not circular");
            check(title.getText().toString().equals(plex.name())&&!title.getText().toString().equals("Tu espacio"),"Header does not identify connected server");
            check(!((TextView)field(home,"serverLabel")).getText().toString().equals(plex.name()),"Server name repeated in subtitle");
            ui(()->{home.resume();home.resume();});check(((BitmapDrawable)avatar.getDrawable()).getBitmap()==bitmap,"Repeated resume redecode or replaced current avatar");
            ui(()->account.requestFocus());await(()->title.getAlpha()>.99f,"Expanded sidebar hides server name");
            await(()->field(home,"heroBitmap")!=null,"Real Plex artwork unavailable for capture");
            Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"Screenshot unavailable");try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"account-plex-server.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            monitor=addMonitor(PlexLinkActivity.class.getName(),null,true);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check(monitor.getHits()==1,"Account action no longer opens server selection");removeMonitor(monitor);monitor=null;
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();check(!account.hasFocus(),"Right arrow failed to return to content");
            for(int i=0;i<3;i++){
                settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
                await(()->avatar.getDrawable()==null&&(Boolean)field(home,"artReleased"),"Stopped home retained the profile bitmap");
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();settings=null;
                await(()->avatar.getDrawable() instanceof BitmapDrawable&&initial.getVisibility()==View.GONE,"Profile image did not return from cache");
            }
            check(title.getText().toString().equals(plex.name()),"Server name changed after return");ui(()->account.requestFocus());
            result.putString("result","PASS real linked Plex avatar, bounded circular image, connected server title without repetition, cached reuse, remote navigation and existing server action, three stop/resume cycles releasing profile artwork. No account or server preferences changed.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{if(monitor!=null)removeMonitor(monitor);if(settings!=null){SettingsActivity old=settings;ui(()->old.finish());}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
