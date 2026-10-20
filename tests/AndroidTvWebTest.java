package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Exercise the actual WebView and TV keys without waking or changing the user's TV. */
public class AndroidTvWebTest extends Instrumentation {
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void ui(Runnable run){runOnMainSync(run);waitForIdleSync();}
    private Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    private interface Ready{boolean get()throws Exception;}
    private void await(Ready ready,String message,long timeout)throws Exception{long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(ready.get())return;Thread.sleep(150);}throw new AssertionError(message);}
    private String js(LiveTvWebActivity a,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->a.web.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));check(done.await(5,TimeUnit.SECONDS),"JavaScript callback timed out");return result[0];}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void capture(Context c,String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"No screenshot");try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();LiveTvWebActivity activity=null;
        try{
            Context c=getTargetContext();
            check(LiveTvWebActivity.safeUrl("https://www.mediasetinfinity.es/directo/cuatro/")&&!LiveTvWebActivity.safeUrl("http://example.org")&&!LiveTvWebActivity.safeUrl("https://user:pass@example.org")&&!LiveTvWebActivity.safeUrl("javascript:alert(1)")&&!LiveTvWebActivity.safeUrl("file:///sdcard/private")&&!LiveTvWebActivity.safeUrl("https://example.org\\@evil.org/")&&!LiveTvWebActivity.safeUrl("https://example.org/\nsecret"),"Unsafe embedded navigation accepted");
            check(LiveTvWebActivity.sameSite("www.mediasetinfinity.es","player.mediasetinfinity.es")&&!LiveTvWebActivity.sameSite("mediasetinfinity.es.attacker.org","www.mediasetinfinity.es"),"Media permission origin boundary failed");
            ActivityInfo info=c.getPackageManager().getActivityInfo(new ComponentName(c,LiveTvWebActivity.class),0);check(!info.exported&&info.processName.equals(c.getPackageName()+":tvweb"),"Web player is exported or shares the home process");
            activity=(LiveTvWebActivity)startActivitySync(new Intent(c,LiveTvWebActivity.class).putExtra("url","https://www.mediasetinfinity.es/directo/cuatro/").putExtra("name","Cuatro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LiveTvWebActivity a=activity;
            await(()->a.web!=null,"WebView did not start",10_000);
            ui(()->{WebSettings s=a.web.getSettings();check(s.getJavaScriptEnabled()&&s.getDomStorageEnabled()&&!s.getMediaPlaybackRequiresUserGesture(),"Official player settings missing");check(!s.getAllowFileAccess()&&!s.getAllowContentAccess()&&s.getMixedContentMode()==WebSettings.MIXED_CONTENT_NEVER_ALLOW,"Unsafe WebView storage or transport settings");});
            await(()->js(a,"document.readyState").equals("\"complete\""),"Official Cuatro page did not finish loading",60_000);
            String title=js(a,"document.title");check(title.toLowerCase().contains("cuatro"),"Official channel page unavailable: "+title);result.putString("official_page_title",title);result.putBoolean("official_stream_verified",false);capture(c,"live-tv-web-cuatro-emulator.png");
            String fixture="<!doctype html><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{margin:0;background:#121e2e}video{width:100vw;height:100vh}button{position:fixed;left:calc(50% - 70px);top:calc(50% - 40px);width:140px;height:80px;z-index:2}body{height:200vh}</style><video loop playsinline src='https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4'></video><button onclick=\"document.body.dataset.clicks=+(document.body.dataset.clicks||0)+1;var v=document.querySelector('video');if(document.body.dataset.clicks==1)v.play();else v.requestFullscreen();\">Play / Fullscreen</button>";
            ui(()->a.web.loadDataWithBaseURL("https://vela-test.invalid/",fixture,"text/html","UTF-8",null));
            await(()->js(a,"document.querySelector('button')!==null").equals("true"),"Fixture did not load",10_000);Thread.sleep(400);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            await(()->js(a,"document.body.dataset.clicks").equals("\"1\""),"TV cursor and OK did not click the actual webpage",5000);
            await(()->js(a,"document.querySelector('video').videoWidth>0&&!document.querySelector('video').paused").equals("true"),"Actual HTML5 video did not play after the remote click",30_000);
            double before=Double.parseDouble(js(a,"document.querySelector('video').currentTime"));Thread.sleep(1500);check(Double.parseDouble(js(a,"document.querySelector('video').currentTime"))>before,"Web video did not advance");result.putBoolean("html5_video_decoded",true);
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);await(()->js(a,"document.querySelector('video').paused").equals("true"),"Media Play/Pause did not pause video",3000);key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);await(()->js(a,"!document.querySelector('video').paused").equals("true"),"Media Play/Pause did not resume video",3000);
            key(KeyEvent.KEYCODE_DPAD_CENTER);await(()->field(a,"customCallback")!=null,"Video fullscreen callback missing",5000);
            key(KeyEvent.KEYCODE_BACK);check(field(a,"customCallback")==null&&!a.isFinishing(),"Back failed to exit fullscreen without closing channels");
            key(KeyEvent.KEYCODE_MENU);check(((LinearLayout)field(a,"toolbar")).hasFocus(),"Menu did not focus native player controls");key(KeyEvent.KEYCODE_MENU);
            long down=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(down,down,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));sendKeySync(new KeyEvent(down,down+800,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,1,0,-1,0,KeyEvent.FLAG_LONG_PRESS));sendKeySync(new KeyEvent(down,down+810,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));waitForIdleSync();
            check(((LinearLayout)field(a,"toolbar")).hasFocus()&&!a.isFinishing(),"Holding OK clicked Back or failed to show controls");key(KeyEvent.KEYCODE_MENU);
            key(KeyEvent.KEYCODE_CHANNEL_DOWN);check(js(a,"window.scrollY>0").equals("true"),"Channel-down did not scroll a website");
            ui(()->a.web.loadUrl("https://example.org/"));Thread.sleep(300);key(KeyEvent.KEYCODE_BACK);await(()->a.isFinishing(),"Back did not return to Vela channels",3000);
            ui(()->{});check(a.web==null,"Closing the player retained a WebView");
            result.putString("result","PASS non-exported isolated process, safe HTTPS navigation, official Cuatro page loaded, real remote pointer and click, decoded/advancing HTML5 video, fullscreen and Back, Menu and long OK controls, webpage scroll, destroyed WebView on exit. Broadcaster live playback still requires real TV verification and any provider consent/login.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null&&!activity.isFinishing()){LiveTvWebActivity old=activity;ui(()->old.finish());}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
