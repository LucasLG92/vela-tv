package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Real public catalog/EPG/playback, bounded pages and resource release. Existing preferences are restored. */
public class AndroidLiveTvTest extends Instrumentation {
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private interface Ready{boolean get()throws Exception;}
    private void await(Ready ready,String message,long timeout)throws Exception{long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] done={false};ui(()->{try{done[0]=ready.get();}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;Thread.sleep(150);}throw new AssertionError(message);}
    private void key(int key){sendKeyDownUpSync(key);waitForIdleSync();}
    private void holdOK()throws Exception{long down=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(down,down,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));Thread.sleep(750);sendKeySync(new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,1,0,-1,0,KeyEvent.FLAG_LONG_PRESS));sendKeySync(new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));waitForIdleSync();}
    private void frame(Context c,LiveTvPlayer video)throws Exception{Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);CountDownLatch done=new CountDownLatch(1);int[] status={-1};ui(()->PixelCopy.request(video.video,bitmap,value->{status[0]=value;done.countDown();},new Handler(Looper.getMainLooper())));check(done.await(5,TimeUnit.SECONDS),"Frame copy timed out");if(status[0]==PixelCopy.SUCCESS){try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"live-tv-decoded-frame.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}bitmap.recycle();}
    private void capture(Context c,String name)throws Exception{Thread.sleep(250);Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"Missing UI screenshot");try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}}
    private void call(LiveTvActivity a,String name,Class<?> type,Object value){ui(()->{try{Method m=type==null?LiveTvActivity.class.getDeclaredMethod(name):LiveTvActivity.class.getDeclaredMethod(name,type);m.setAccessible(true);if(type==null)m.invoke(a);else m.invoke(a,value);}catch(Exception e){throw new RuntimeException(e);}});}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=LiveTvData.prefs(c);Map<String,?> saved=new HashMap<>(prefs.getAll());Map<String,?> plexBefore=new HashMap<>(new PlexClient(c).prefs.getAll());String[] sidebarBefore=SidebarApps.slots(c);LiveTvActivity activity=null;SettingsActivity settings=null;ActivityMonitor monitor=null;
        try{
            check(LiveTvData.https("https://example.org/list.m3u?token=personal")&&!LiveTvData.https("http://example.org/list.m3u")&&!LiveTvData.https("https://user:password@example.org/list.m3u")&&!LiveTvData.https("file:///tmp/list.m3u"),"Unsafe playlist URL accepted");
            List<LiveTvData.Channel> m3u=LiveTvData.parseM3u("#EXTM3U\n#EXTINF:-1 tvg-id=\"one\" tvg-name=\"A, B\" tvg-logo=\"https://example.org/logo.png\" group-title=\"Test\",Channel one\nhttps://example.org/live.m3u8\n#EXTINF:-1 tvg-id=\"one\",Duplicate\nhttps://example.org/backup.m3u8\n#EXTINF:-1 tvg-id=\"two\",Official web\n#EXTV-streamtype: \"geturl\"\nhttps://example.org/channel\n");
            check(m3u.size()==2&&m3u.get(0).streams.size()==2&&m3u.get(0).name.equals("Channel one")&&!m3u.get(1).playable()&&m3u.get(1).web.equals("https://example.org/channel"),"M3U parsing, deduplication or unsupported extraction handling failed");
            long now=System.currentTimeMillis()/1000;String json="[{\"events\":[{\"hi\":"+(now-500)+",\"hf\":"+(now+500)+",\"t\":\"Current\",\"d\":\"Discarded\"},{\"hi\":"+(now+500)+",\"hf\":"+(now+900)+",\"t\":\"Next\"},{\"hi\":1,\"hf\":2,\"t\":\"Old\"}],\"name\":\"wanted\"},{\"name\":\"unwanted\",\"events\":[]}]";
            Map<String,LiveTvData.Guide> parsed=LiveTvData.parseGuide(new StringReader(json),Collections.singleton("wanted"),now);check(parsed.size()==1&&parsed.get("wanted").now.title.equals("Current")&&parsed.get("wanted").next.title.equals("Next")&&parsed.get("wanted").current(now+600).title.equals("Next")&&parsed.get("wanted").current(now+1000)==null,"Streamed EPG retains expired data or misses now/next");
            LiveTvData.prefs(c).edit().remove("source").commit();
            activity=(LiveTvActivity)startActivitySync(new Intent(c,LiveTvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final LiveTvActivity a=activity;
            await(()->a.catalog!=null&&((List<?>)field(a,"cards")).size()==12,"Real public catalog did not load",40_000);
            check(a.catalog.channels.size()>100,"Unexpected catalog size");check(a.playback==null,"Catalog started video without selection");
            await(()->!((Map<?,?>)field(a,"guide")).isEmpty(),"Real programme guide did not load",40_000);
            await(()->{for(ImageView image:(List<ImageView>)field(a,"images"))if(image.getDrawable()!=null)return true;return false;},"No real channel logos loaded",30_000);
            capture(c,"live-tv-channels.png");result.putInt("catalog_channels",a.catalog.channels.size());result.putInt("epg_channels",((Map<?,?>)field(a,"guide")).size());
            LiveTvData.Channel website=null;for(LiveTvData.Channel ch:a.catalog.channels)if(!ch.playable()&&LiveTvWebActivity.safeUrl(ch.web)){website=ch;break;}check(website!=null,"Official web channel missing");
            monitor=addMonitor(LiveTvWebActivity.class.getName(),null,true);final LiveTvData.Channel official=website;ui(()->a.play(official));check(monitor.getHits()==1&&a.playback==null,"Web-only channel did not route to Vela's internal player");removeMonitor(monitor);monitor=null;
            ui(()->a.play(official));await(()->!((Boolean)field(a,"active")),"In-app website did not open above the channel browser",10_000);Thread.sleep(2000);key(KeyEvent.KEYCODE_BACK);await(()->((Boolean)field(a,"active")),"Back from the real web process did not return to channels",5000);
            ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);await(()->{for(ActivityManager.RunningAppProcessInfo process:manager.getRunningAppProcesses())if(process.processName.equals(c.getPackageName()+":tvweb"))return false;return true;},"Web player process retained memory after closing",5000);
            List<View> cards=(List<View>)field(a,"cards");ui(()->cards.get(0).requestFocus());LiveTvData.Channel first=a.selected;check(first!=null,"Card focus did not select channel");
            holdOK();
            check(a.playback==null&&LiveTvData.favorites(c,LiveTvData.CATALOG).contains(first.id),"Favorite action launched video or was not saved");
            ui(()->((TextView)getField(a,"next")).performClick());check(((Integer)field(a,"page"))==1&&((List<?>)field(a,"cards")).size()<=12,"Paging keeps more than twelve cards or fails to advance");
            ui(()->((TextView)getField(a,"previous")).performClick());
            ui(()->{for(int i=0;i<7;i++)((TextView)getField(a,"next")).performClick();});check(((Integer)field(a,"page"))==7&&((List<?>)field(a,"cards")).size()<=12,"Rapid paging failed or retained multiple pages");
            ui(()->{for(int i=0;i<7;i++)((TextView)getField(a,"previous")).performClick();});
            List<LiveTvData.Channel> channels=a.catalog.channels;LiveTvData.Channel direct=null;for(LiveTvData.Channel ch:channels)if(ch.id.equals("La1.TV")&&ch.playable())direct=ch;check(direct!=null,"Public RTVE channel missing");final LiveTvData.Channel real=direct;
            ui(()->a.play(real));await(()->a.playback!=null,"Player did not open",5000);
            await(()->a.playback.rendered,"No decoded live video rendered",30_000);LiveTvPlayer video=a.playback;check(video.prepared&&video.player.isPlaying()&&video.player.getVideoWidth()>0,"Player is not decoding real video");
            int position=video.player.getCurrentPosition();Thread.sleep(1800);check(video.player.getCurrentPosition()>position,"Live video does not advance");capture(c,"live-tv-player.png");frame(c,video);result.putInt("video_width",video.player.getVideoWidth());
            MediaPlayer oldPlayer=video.player;key(KeyEvent.KEYCODE_CHANNEL_UP);await(()->a.playback!=null&&a.playback.rendered&&!a.selected.id.equals(real.id),"Channel-up did not zap to another real channel",30_000);check(a.playback.player!=oldPlayer,"Zapping reused the released player");
            key(KeyEvent.KEYCODE_BACK);check(a.playback==null&&video.player==null&&!video.active,"Back retained decoder or closed the channel browser");check(!a.isFinishing(),"Back from playback exited instead of showing channels");
            ui(()->a.play(real));await(()->a.playback!=null&&a.playback.rendered,"Second playback did not resume",30_000);LiveTvPlayer stopped=a.playback;
            settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->stopped.player==null&&!stopped.active,"Leaving TV retained player/audio",5000);
            SettingsActivity s=settings;ui(()->s.finish());settings=null;await(()->a.playback!=null&&a.playback.rendered,"Returning from another activity failed to restore selected video",30_000);key(KeyEvent.KEYCODE_BACK);
            call(a,"showSources",null,null);capture(c,"live-tv-settings.png");key(KeyEvent.KEYCODE_BACK);check(((FrameLayout)field(a,"dialog")).getVisibility()!=View.VISIBLE,"Back failed to close source settings");
            if(SidebarApps.launchable(c,"com.tv.tdtspain")){Intent original=HomeApps.launchIntent(c.getPackageManager(),"com.tv.tdtspain");monitor=addMonitor(original.getComponent().getClassName(),null,true);call(a,"showSources",null,null);FrameLayout dialog=(FrameLayout)field(a,"dialog");ui(()->{for(int i=0;i<dialog.getChildCount();i++){View v=dialog.getChildAt(i);if(v instanceof TextView&&((TextView)v).getText().toString().contains("TDT España")){v.performClick();return;}}throw new AssertionError("Original app fallback absent");});check(monitor.getHits()==1,"Original TDT España is no longer available");removeMonitor(monitor);monitor=null;key(KeyEvent.KEYCODE_BACK);}
            check(plexBefore.equals(new PlexClient(c).prefs.getAll())&&Arrays.equals(sidebarBefore,SidebarApps.slots(c)),"TV integration altered Plex account or sidebar");
            result.putString("result","PASS live public channel catalog, real bounded logos, streamed now/next EPG and expiry, standard M3U and duplicate signals, explicit playback, favorites, twelve-card paging, decoded and advancing official live video, Back to channels, pause/release and resume, source settings, original TDT España fallback. Personal Plex and sidebar preserved; TV preferences restored.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{
            if(monitor!=null)removeMonitor(monitor);if(settings!=null){SettingsActivity old=settings;ui(()->old.finish());}if(activity!=null){LiveTvActivity old=activity;ui(()->old.finish());}
            SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();if(v instanceof String)edit.putString(entry.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)v);else if(v instanceof Long)edit.putLong(entry.getKey(),(Long)v);else if(v instanceof Set)edit.putStringSet(entry.getKey(),new HashSet<>((Set<String>)v));}edit.commit();
        }finish(result.containsKey("error")?1:-1,result);
    }
    private Object getField(Object o,String name){try{return field(o,name);}catch(Exception e){throw new RuntimeException(e);}}
}
