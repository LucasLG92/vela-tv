package tv.vela.launcher;

import android.app.*;import android.content.*;import android.media.MediaPlayer;import android.os.*;import android.view.*;
import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;

/** Actual failed HTTP signals and decoded HLS/TS on an owned loopback emulator, never a subscription. */
public final class AndroidTvFallbackTest extends Instrumentation {
    private interface Ready {boolean get()throws Exception;}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private Object field(Object o,String name)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    private void await(Ready r,String why)throws Exception{long end=SystemClock.elapsedRealtime()+40_000;while(SystemClock.elapsedRealtime()<end){boolean[] ready={false};ui(()->{try{ready[0]=r.get();}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(60);}throw new AssertionError(why);}
    private void invoke(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);ui(()->{try{m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}});}
    private TvSignalFallback plan(LiveTvPlayer p)throws Exception{return (TvSignalFallback)field(p,"signals");}
    private TvSignalFallback.Signal signal(LiveTvPlayer p)throws Exception{return (TvSignalFallback.Signal)field(p,"signal");}
    private LiveTvData.Channel ch(String id,String name,String...urls){return new LiveTvData.Channel(id,name,"General","",Arrays.asList(urls));}
    private JSONObject get(String address)throws Exception{try(XtreamClient.Response response=new XtreamClient.Response(address,64*1024);ByteArrayOutputStream out=new ByteArrayOutputStream()){LiveTvData.copy(response.in,out,64*1024);return new JSONObject(out.toString("UTF-8"));}}
    private void restore(SharedPreferences p,Map<String,?> saved){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();}
    private void policy(){
        String root="https://fixture.invalid/";LiveTvData.Channel fhd=ch("fhd","ES| Canal FHD",root+"a",root+"a",root+"b"),hd=ch("hd","ES| Canal HD",root+"c"),sd=ch("sd","ES| Canal SD",root+"d"),uhd=ch("uhd","ES| Canal UHD",root+"e"),same=ch("same","ES| Canal FHD (AV)",root+"f");
        fhd.variants=Arrays.asList(sd,ch("uk","UK| Canal HD",root+"foreign"),ch("plus","ES| Canal +1 HD",root+"plus"),ch("region","ES| Canal Canarias HD",root+"region"),uhd,hd,same);
        TvSignalFallback p=new TvSignalFallback(fhd);List<String> found=new ArrayList<>();TvSignalFallback.Signal s;while((s=p.next(c->c.streams))!=null)found.add(s.channel.id);
        check(found.equals(Arrays.asList("fhd","fhd","same","hd","sd","uhd"))&&p.attempts()==6,"Order, URL deduplication or channel identity");
        LiveTvData.Channel x=new LiveTvData.Channel("x","ES| Canal FHD","General","",Collections.emptyList(),"1","account-a"),alien=new LiveTvData.Channel("y","ES| Canal HD","General","",Collections.emptyList(),"2","account-b");x.variants=Arrays.asList(alien,hd);p=new TvSignalFallback(x);check(p.next(c->Collections.singletonList(root+"xc"))!=null&&p.next(c->c.streams)==null,"Cross-account or cross-source fallback");
        List<LiveTvData.Channel> many=new ArrayList<>();for(int n=0;n<20;n++)many.add(ch("bounded-"+n,"ES| Canal HD",root+n+"/1",root+n+"/2",root+n+"/3",root+n+"/4",root+n+"/5"));LiveTvData.Channel head=many.get(0);head.variants=many;p=new TvSignalFallback(head);int count=0;while(p.next(c->c.streams)!=null)count++;check(count==32&&p.next(c->c.streams)==null,"Attempts not bounded");
        TvSignalFallback.Progress clock=new TvSignalFallback.Progress();clock.reset(0,100);check(!clock.stalled(19_999,100,false)&&clock.stalled(20_000,100,false),"Short buffering or stalled threshold");clock.reset(0,100);check(!clock.stalled(30_000,100,true)&&!clock.stalled(49_999,100,false),"Explicit pause considered failed");check(!clock.stalled(50_000,101,false),"Progress did not reset watchdog");
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=LiveTvData.prefs(c),epg=c.getSharedPreferences("xmltv",0),secret=c.getSharedPreferences("xtream",0);Map<String,?> before=new HashMap<>(prefs.getAll()),epgBefore=new HashMap<>(epg.getAll()),secretBefore=new HashMap<>(secret.getAll());LiveTvActivity activity=null;String source="";
        try{
            check(Build.MODEL.contains("sdk")||Build.FINGERPRINT.contains("generic"),"Owned emulator required");check(!new PlexClient(c).connected()&&!new JellyfinClient(c).connected()&&!new XtreamClient(c).connected(),"Personal account linked");policy();
            JSONObject fixture=new JSONObject(new String(LiveTvData.read(new File(c.getExternalFilesDir(null),"xtream-fixture.json"),8192),"UTF-8"));String base=fixture.getString("base");check(base.startsWith("http://127.0.0.1:"),"Loopback only");String path=base+"/fallback/";
            new M3uClient(c).configure(path+"list.m3u","",true);source=LiveTvData.source(c);epg.edit().putBoolean("native",true).commit();
            LiveTvData.Channel preferred=ch("fhd","ES| Prueba FHD",path+"failed-fhd.m3u8",path+"failed-fhd.ts"),hd=ch("hd","ES| Prueba HD",path+"good.m3u8"),sd=ch("sd","ES| Prueba SD",path+"sd.m3u8"),other=ch("other","ES| Otro FHD",path+"good.m3u8");
            try(TvCatalogCache.Writer writer=new TvCatalogCache(c).writer(source)){for(LiveTvData.Channel item:Arrays.asList(preferred,hd,sd,other))writer.add(item,false);writer.commit("{}");}
            TvChannelNames.prefer(c,source,preferred);String preference="quality-"+LiveTvData.hash(source+TvChannelNames.family(preferred));
            activity=(LiveTvActivity)startActivitySync(new Intent(c,LiveTvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LiveTvActivity tv=activity;
            await(()->tv.catalog!=null&&!(Boolean)field(tv,"loading")&&tv.catalog.channels.size()==2,"Fixture catalog unavailable");LiveTvData.Channel selected=tv.catalog.channels.get(0);check(selected.id.equals("fhd")&&selected.variants.size()==3,"Missing grouped qualities");
            ui(()->tv.play(selected));await(()->tv.playback!=null&&tv.playback.rendered,"Failed FHD did not fall back to HD");LiveTvPlayer player=tv.playback;
            check(signal(player).channel.id.equals("hd")&&plan(player).attempts()==3&&player.player.getVideoWidth()==320&&player.player.isPlaying(),"No actual decoded fallback or wrong attempts");check(tv.selected==selected&&player.selected==selected&&prefs.getString(preference,"").equals("fhd"),"Fallback changed channel/quality preference");
            int position=player.player.getCurrentPosition();Thread.sleep(700);check(player.player.getCurrentPosition()>position,"Fallback video not advancing");
            MediaPlayer decoder=player.player;ui(()->player.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)));Runnable watch=(Runnable)field(player,"watch");TvSignalFallback.Progress clock=(TvSignalFallback.Progress)field(player,"progress");Handler main=(Handler)field(player,"main");
            ui(()->{clock.reset(SystemClock.elapsedRealtime()-30_000,decoder.getCurrentPosition());main.removeCallbacks(watch);watch.run();});check(player.player==decoder&&!decoder.isPlaying(),"User pause triggered fallback");
            ui(()->player.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)));check(player.player==decoder&&decoder.isPlaying(),"Resume replaced decoder");
            ui(()->{decoder.pause();clock.reset(SystemClock.elapsedRealtime()-30_000,decoder.getCurrentPosition());main.removeCallbacks(watch);watch.run();});
            await(()->player.rendered&&signal(player).channel.id.equals("sd"),"Frozen playback did not select SD");check(player.player!=decoder&&prefs.getString(preference,"").equals("fhd"),"Freeze kept old player or saved downgrade");
            MediaPlayer sdDecoder=player.player;ui(()->tv.play(other));await(()->tv.playback.rendered,"Channel switch failed");check(tv.selected==other&&tv.playback==player&&player.player!=sdDecoder,"Channel switch retained previous decoder");
            LiveTvData.Channel dead=ch("dead","ES| Muerto FHD",path+"dead-one.m3u8",path+"dead-two.ts");ui(()->tv.play(dead));LiveTvPlayer deadPlayer=tv.playback;await(()->deadPlayer.player==null&&plan(deadPlayer).attempts()==2&&signal(deadPlayer)==null,"All failed signals not exhausted");int requests=get(base+"/metrics").optInt("fallback-dead-one.m3u8");Thread.sleep(900);check(get(base+"/metrics").optInt("fallback-dead-one.m3u8")==requests,"Infinite automatic retry");
            ui(()->deadPlayer.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)));await(()->deadPlayer.player==null&&plan(deadPlayer).attempts()==2&&signal(deadPlayer)==null,"OK did not explicitly retry exhausted signals without a manual signal button");check(get(base+"/metrics").optInt("fallback-dead-one.m3u8")>requests,"Explicit retry did not request the source again");
            // A queued failover must die with Back/pause, even before its 250 ms retry starts.
            ui(()->tv.play(selected));LiveTvPlayer cancel=tv.playback;ui(()->{try{Method failed=LiveTvPlayer.class.getDeclaredMethod("failed");failed.setAccessible(true);failed.invoke(cancel);}catch(Exception e){throw new RuntimeException(e);}cancel.pause();});Thread.sleep(600);check(cancel.player==null&&!cancel.active,"Queued retry survived pause");
            ui(cancel::resume);await(()->cancel.rendered,"Resume did not restart preferred signal plan");check(plan(cancel).attempts()==3,"Resume skipped preferred quality");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();check(tv.playback==null&&cancel.player==null,"Back retained player or retries");
            // Xtream resolves credentials lazily for this account and tries HLS/TS before the lower quality.
            XtreamClient xc=new XtreamClient(c);xc.save(xc.login(base,fixture.getString("username"),fixture.getString("password"),true));XtreamClient.Account account=xc.account();
            LiveTvData.Channel xf=new LiveTvData.Channel("xf","ES| Xtream FHD","General","",Collections.emptyList(),"9001",account.id),xh=new LiveTvData.Channel("xh","ES| Xtream HD","General","",Collections.emptyList(),"9002",account.id);xf.variants=Arrays.asList(xf,xh);
            // Keep the browsing source stable: only the native player's resolver uses the owned account.
            LiveTvData.select(c,"m3u");ui(()->tv.play(xf));await(()->tv.playback.rendered,"Xtream fallback did not decode MPEG-TS");LiveTvPlayer xp=tv.playback;check(plan(xp).attempts()==4&&signal(xp).channel==xh&&signal(xp).url.endsWith("9002.ts")&&xp.player.getVideoWidth()==320,"Wrong Xtream format/quality fallback");
            ui(()->{xp.pause();tv.finish();});activity=null;
            result.putString("result","PASS bounded/deduplicated same-channel plan, lower-quality ordering, country/region/+1/account/source isolation, 20s startup/stall policy and brief-buffer/pause protection; native HTTP failures FHD to decoded HD, frozen HD to SD, unchanged preferred quality/logical channel, one decoder, cancellation on zap/pause/Back, restart preferred on resume, exhausted signals stop, Xtream HLS/TS to decoded lower quality MPEG-TS. OK explicitly retries exhausted signals after the manual signal control was removed. Owned loopback only.");
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{if(activity!=null){LiveTvActivity a=activity;ui(a::finish);}if(!source.isEmpty())new TvCatalogCache(c).remove(source);restore(prefs,before);restore(epg,epgBefore);restore(secret,secretBefore);}
        finish(result.containsKey("error")?1:-1,result);
    }
}
