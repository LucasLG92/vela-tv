package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Run on a disposable emulator with adb reverse tcp:38421 and the local demonstration film. */
public final class AndroidBroadcastDesignTest extends Instrumentation {
    private LiveTvPlayer player;
    private Activity activity;
    private boolean tour;
    private String previousLanguage,previousMode;
    private final List<LiveTvData.Channel> lineup=new ArrayList<>();
    private final Map<String,LiveTvData.Guide> listings=new HashMap<>();
    private static final String origin="http://127.0.0.1:38421/";
    private static void check(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
    private void key(int code){runOnMainSync(()->{for(int phase:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}){KeyEvent event=new KeyEvent(phase,code);if(!player.key(event))player.dispatchKeyEvent(event);}});waitForIdleSync();}
    private View view(String field)throws Exception{Field f=LiveTvPlayer.class.getDeclaredField(field);f.setAccessible(true);return (View)f.get(player);}
    private void photo(String name)throws Exception{
        waitForIdleSync();Thread.sleep(520);
        Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"Display capture failed");
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"broadcast-"+name+".jpg"))){b.compress(Bitmap.CompressFormat.JPEG,93,out);}finally{b.recycle();}
    }
    public void onCreate(Bundle args){super.onCreate(args);tour="true".equals(args.getString("tour"));start();}
    public void onStart(){
        Bundle result=new Bundle();
        try{
            check(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"),"Use an emulator");
            setInTouchMode(false);Context c=getTargetContext();previousMode=VelaEdition.mode(c);VelaEdition.select(c,"full");Ui.init(c);previousLanguage=L10n.choice();L10n.choose(c,"es");Ui.init(c);
            String[] names={"La 1","La 2","Antena 3","Cuatro","laSexta","24h"},logos={"tve-1-es","tve-2-es","antena-3-es","cuatro-es","lasexta-es","24h-es"};
            String[] titles={"El viaje del agua","Sintel","Una noche en el museo","Horizontes","El mundo en imágenes","Diario 24"};
            long now=System.currentTimeMillis()/1000;
            for(int i=0;i<names.length;i++){
                LiveTvData.Channel ch=new LiveTvData.Channel("demo-"+i,names[i],"Televisión",origin+logos[i]+".png",Collections.singletonList(origin+"cine.mp4"));lineup.add(ch);
                LiveTvData.Guide guide=new LiveTvData.Guide();guide.now=new LiveTvData.Programme(now-420-i*60,now+360+i*60,titles[i]);guide.next=new LiveTvData.Programme(guide.now.end,guide.now.end+1800,"El arte de la animación");listings.put(ch.id,guide);
            }
            activity=startActivitySync(new Intent(c,SettingsActivity.class).putExtra("fromSetup",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{L10n.choose(activity,"es");Ui.init(activity);FrameLayout root=new FrameLayout(activity);activity.setContentView(root);player=new LiveTvPlayer(activity,new LiveTvPlayer.Actions(){
                public void back(){}public void zap(int n){}public void choose(LiveTvData.Channel ch){}
                public void channels(int page){player.channels(lineup,listings,"Todos los canales",0,lineup.size());}
            });root.addView(player,new FrameLayout.LayoutParams(-1,-1));player.select(lineup.get(1));LiveTvData.Guide g=listings.get(lineup.get(1).id);player.metadata(g.now,g.next);player.channels(lineup,listings,"Todos los canales",0,lineup.size());player.resume();});
            long until=SystemClock.elapsedRealtime()+22000;while(!player.rendered&&SystemClock.elapsedRealtime()<until){waitForIdleSync();Thread.sleep(60);}
            check(player.rendered,"The fixture must render a video frame");
            Thread.sleep(420);
            ParcelFileDescriptor recorder=null;
            if(tour)recorder=getUiAutomation().executeShellCommand("screenrecord --size 1280x720 --bit-rate 4500000 --time-limit 19 /sdcard/Android/data/tv.vela.launcher/files/vela-player-tour.mp4");
            photo("01-entrada");
            until=SystemClock.elapsedRealtime()+7500;while(view("panel").isShown()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
            check(!view("panel").isShown()&&!view("intro").isShown(),"The video should be clear");photo("02-emision");if(tour)Thread.sleep(1400);
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(view("panel").isShown(),"OK should restore programme information");photo("03-informacion");if(tour)Thread.sleep(2400);
            key(KeyEvent.KEYCODE_DPAD_LEFT);check(view("controls")!=null&&!view("panel").isShown(),"Playback controls should replace the programme HUD");photo("04-controles");if(tour)Thread.sleep(2200);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            VelaPlaybackTray activeTray=(VelaPlaybackTray)view("controls");
            if(activeTray.findFocus()!=null&&activeTray.findFocus().getContentDescription()!=null&&!activeTray.findFocus().getContentDescription().toString().contains(",")){
                photo("06-subtitulos");key(KeyEvent.KEYCODE_BACK);
            }
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            VelaPlaybackTray tray=(VelaPlaybackTray)view("controls");
            check(tray!=null&&tray.findFocus()!=null,"Missing picture choices");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(VelaPlaybackPanel.prefs(getTargetContext()).getInt(VelaPlaybackPanel.key(player.selected.name,"aspect"),0)==1,"Picture option did not apply");
            key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_BACK);
            check(view("controls")!=null,"Back from advanced must restore the strip");
            key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);check(view("quickDrawer").isShown(),"Right should open channel surfing");key(KeyEvent.KEYCODE_DPAD_DOWN);android.widget.ListView rows=(android.widget.ListView)view("quickList");check(rows.hasFocus()&&rows.getSelectedItemPosition()==2&&player.selected==lineup.get(1),"Browsing should move focus without changing the film");photo("05-canales");if(tour)Thread.sleep(2400);key(KeyEvent.KEYCODE_BACK);
            check(!view("quickDrawer").isShown()&&player.rendered,"Back should return to the same playing channel");
            if(recorder!=null){try(InputStream stdout=new ParcelFileDescriptor.AutoCloseInputStream(recorder)){byte[] buffer=new byte[256];while(stdout.read(buffer)!=-1){}}}
            result.putString("result","PASS native opening, clean video, OK info, left controls, right channel list, return to a decoded local film.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null)runOnMainSync(()->{if(player!=null)player.pause();activity.finish();});if(previousLanguage!=null)L10n.choose(getTargetContext(),previousLanguage);if("lite".equals(previousMode))VelaEdition.select(getTargetContext(),"lite");finish(result.containsKey("error")?1:-1,result);}
    }
}
