package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Real framework decoder and private disk cache against an owned synthetic loopback fixture. */
public final class AndroidPlaybackExpansionTest extends Instrumentation {
    private final Bundle result=new Bundle();private String phase="setup",fixture="",profile,previous;private Activity activity;private Context context;private LiveTvPlayer player;private VelaHlsBuffer cache;private XtreamClient xtream;private int oldMatching;private boolean hadMatching;
    interface Ready {boolean get()throws Exception;}
    void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    void ui(Runnable run){runOnMainSync(run);waitForIdleSync();}
    void await(Ready ready)throws Exception{long until=SystemClock.elapsedRealtime()+35000;while(SystemClock.elapsedRealtime()<until){waitForIdleSync();if(ready.get())return;Thread.sleep(80);}throw new AssertionError("Timeout: "+phase);}
    String get(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(15000);try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];for(int n;(n=in.read(b))!=-1;){if(out.size()+n>256*1024)throw new IOException();out.write(b,0,n);}return out.toString("UTF-8");}finally{c.disconnect();}}
    void capture(String name)throws Exception{Thread.sleep(250);Bitmap b=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void checkM3uReplay() throws Exception {
        phase="m3u-replay-template";
        LiveTvData.Channel channel=new LiveTvData.Channel("owned-list","Canal Lista","Demo","",Collections.singletonList(fixture+"/live"));
        channel.archiveDays=1;
        long now=1700000000L;
        LiveTvData.Programme show=new LiveTvData.Programme(now-3600,now-1800,"Programa de demostración");
        channel.replayMode="append";
        channel.replayTemplate="?utc={utc}&duration={duration}";
        check(VelaReplay.url(context,channel,show,now).equals(fixture+"/live?utc="+show.start+"&duration=1800"),"M3U advertised template not respected");
        channel.replayTemplate="?start=${start}&end=${end}&duration=${duration}";
        check(VelaReplay.url(context,channel,show,now).equals(fixture+"/live?start="+show.start+"&end="+show.end+"&duration=1800"),"M3U archive duration must be a number of seconds");
        channel.replayMode="default";
        channel.replayTemplate=fixture+"/archive?now=${now}&timestamp=${timestamp}&lutc={lutc}";
        check(VelaReplay.url(context,channel,show,now).equals(fixture+"/archive?now="+now+"&timestamp="+now+"&lutc="+now),"M3U current-time aliases must use the playback request time");
        channel.replayTemplate="?x={unknown}";
        boolean invalid=false;
        try{VelaReplay.url(context,channel,show,now);}catch(IOException denied){invalid=true;}
        check(invalid,"Unknown provider template was guessed");
    }
    private void playImportedM3uArchive() throws Exception {
        phase="imported-m3u-archive";
        String source="m3u:"+LiveTvData.hash(fixture+"/archive-list.m3u");
        String playlist="#EXTM3U\n#EXTINF:-1 tvg-id=\"archive\" catchup=\"append\" catchup-days=\"2\""
                +" catchup-source=\"?start=${start}&end=${end}&duration=${duration}\",Archive TV\n"+fixture+"/m3u-archive.ts\n";
        TvCatalogCache cache=new TvCatalogCache(context);
        try(TvCatalogCache.Writer writer=cache.writer(source)){
            M3uClient.parse(new StringReader(playlist),true,writer);
            writer.commit("{}");
        }
        LiveTvData.Channel station=cache.page(source,null,"",null,0).channels.get(0);
        long now=System.currentTimeMillis()/1000;
        LiveTvData.Programme past=new LiveTvData.Programme(now-3600,now-1800,"Recorded show");
        String url=VelaReplay.url(context,station,past,now);
        ui(()->{player.selectReplay(station,past,url);player.resume();});
        await(()->player.prepared&&player.rendered);
        check(field(player,"buffer")==null,"An M3U archive started live buffering");
        ui(player::pause);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);fixture=args.getString("fixture","");start();}
    @Override public void onStart(){context=getTargetContext();oldMatching=VelaFrameRate.mode(context);hadMatching=context.getSharedPreferences("vela-video",0).contains("frame-rate");VelaFrameRate.mode(context,0);Locale.setDefault(new Locale("es","ES"));try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator only");}
        URI base=new URI(fixture);check("http".equals(base.getScheme())&&"127.0.0.1".equals(base.getHost())&&base.getPort()>0&&base.getRawPath().isEmpty()&&base.getUserInfo()==null&&base.getQuery()==null,"Owned loopback fixture only");check(context.getSharedPreferences("xtream",0).getString("account","").isEmpty(),"Personal Xtream accounts are not allowed");previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Vídeo · Demostración");VelaWorkspace.select(context,profile);
        checkM3uReplay();
        phase="hls-parser";VelaHlsBuffer.Playlist manifest=VelaHlsBuffer.parse("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:9\n#EXTINF:2,\nclip.ts\n",base);check(manifest.parts.size()==1&&manifest.parts.get(0).sequence==9,"HLS sequence or duration parse failed");for(String bad:new String[]{"#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:2,\nclip.ts\n","#EXTM3U\n#EXTINF:NaN,\nclip.ts\n","#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:-1\n#EXTINF:2,\nclip.ts\n"}){boolean refused=false;try{VelaHlsBuffer.parse(bad,base);}catch(IOException expected){refused=true;}check(refused,"Unsupported/protected HLS was accepted");}
        phase="actual-disk-buffer";cache=new VelaHlsBuffer(context,fixture+"/live.m3u8");String served=get(cache.url());check(served.startsWith("#EXTM3U")&&served.contains(".seg")&&!served.contains(fixture),"Loopback playlist leaks source URL or has no cached segments");check(cache.bytes()>0&&cache.bytes()<=VelaHlsBuffer.MAX_BYTES&&cache.seconds()<=VelaHlsBuffer.MAX_SECONDS,"Disk budget not enforced");File owned=(File)field(cache,"directory");cache.close();cache=null;await(()->!owned.exists());
        phase="native-live-decoder";context.getSharedPreferences("personalization",0).edit().putBoolean("initialized",true).putBoolean("complete",true).commit();context.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();activity=startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("velaTools",6));LiveTvData.Channel channel=new LiveTvData.Channel("owned-hls","Canal Demo FHD","Demostración","",Collections.singletonList(fixture+"/live.m3u8"));VelaPlaybackPanel.prefs(context).edit().putBoolean("timeshift",true).commit();ui(()->{player=new LiveTvPlayer(activity,new LiveTvPlayer.Actions(){public void back(){}public void zap(int n){}public void channels(int n){}public void choose(LiveTvData.Channel ch){}});activity.setContentView(player);player.select(channel);player.resume();});await(()->player.rendered&&player.prepared&&player.dvr!=null&&field(player,"buffer")!=null);VelaDvrPlayer decoder=player.dvr;final long[] position=new long[1],duration=new long[1];ui(()->{position[0]=decoder.position();duration[0]=decoder.duration();});check(duration[0]>0,"No seekable HLS DVR window: "+duration[0]);ui(()->player.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)));final boolean[] playing={true};ui(()->playing[0]=decoder.playing());check(!playing[0],"Pause did not stop the real decoder");ui(()->position[0]=decoder.position());long at=position[0];Thread.sleep(1400);ui(()->position[0]=decoder.position());check(Math.abs(position[0]-at)<200,"Paused decoder advanced: "+at+" to "+position[0]);int sought=decoder.seeks;ui(()->decoder.seek(0));await(()->decoder.seeks>sought);ui(()->position[0]=decoder.position());check(position[0]<200,"Actual seek did not move to window start");ui(()->player.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)));await(()->{ui(()->playing[0]=decoder.playing());return playing[0];});check(player.player==null,"A second framework decoder was created");ui(()->player.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU)));check(field(player,"controls")!=null,"Playback centre did not open");capture("expansion-08-reproductor-hls");VelaPlaybackTray tray=(VelaPlaybackTray)field(player,"controls");ui(tray::close);ui(player::pause);check(field(player,"buffer")==null&&player.player==null&&player.dvr==null,"Leaving playback retained decoder or buffer");
        playImportedM3uArchive();
        phase="real-xtream-archive-metadata";VelaPlaybackPanel.prefs(context).edit().putInt("engine",VelaPlaybackPolicy.SYSTEM).commit();xtream=new XtreamClient(context);XtreamClient.Account a=xtream.login(fixture,"fixture-user","fixture-password",true);xtream.save(a);XtreamClient.Page channels=xtream.page("*","",0,"",true);check(channels.channels.size()==1&&channels.channels.get(0).archiveDays==2,"Provider archive metadata was discarded");LiveTvData.Channel archived=channels.channels.get(0);long now=System.currentTimeMillis()/1000;LiveTvData.Programme show=new LiveTvData.Programme(now-3600,now-1800,"Programa de demostración");String replay=VelaReplay.url(context,archived,show,now);check(replay.startsWith(fixture+"/timeshift/")&&replay.endsWith("/101.ts"),"Xtream archive route invalid");check(!VelaReplay.supports(archived,new LiveTvData.Programme(now-4*86400,now-4*86400+1800,"Antiguo"),now),"Unavailable archive day was offered");ui(()->{player.selectReplay(archived,show,replay);player.resume();});await(()->player.rendered&&player.player!=null);check(field(player,"buffer")==null,"Replay started a second live timeshift buffer");ui(()->player.metadata(null,null));check(((android.widget.TextView)field(field(player,"panel"),"title")).getText().toString().equals(show.title),"Replay was labelled with the current live programme");capture("expansion-09-programa-anterior");ui(player::pause);
        result.putString("result","PASS clear HLS parser/protection refusal, real disk-backed loopback playback and storage limits, bounded Media3 decoding with real pause/seek/resume, one decoder, themed controls, teardown, actual owned Xtream login/catalog/archive metadata, historical programme playback, archive range checks and safe M3U templates. Synthetic media and owned loopback only; no physical TV or personal provider.");
    }catch(Throwable e){result.putString("error",phase+"; matching="+VelaFrameRate.mode(context)+"; player="+(player!=null&&player.player!=null)+"; optional="+(player!=null&&player.dvr!=null)+"; rendered="+(player!=null&&player.rendered)+"; prepared="+(player!=null&&player.prepared)+"\n"+android.util.Log.getStackTraceString(e));}finally{try{if(player!=null)ui(player::pause);if(activity!=null)ui(activity::finish);if(cache!=null)cache.close();if(xtream!=null&&xtream.connected())xtream.disconnect();android.content.SharedPreferences.Editor display=context.getSharedPreferences("vela-video",0).edit();if(hadMatching)display.putInt("frame-rate",oldMatching);else display.remove("frame-rate");display.commit();if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}}catch(Exception cleanup){result.putString("cleanup",cleanup.getClass().getSimpleName());}finish(result.containsKey("error")?1:Activity.RESULT_OK,result);}}
}
