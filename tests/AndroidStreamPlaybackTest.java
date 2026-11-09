package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/** Real HTTP/HLS/TS decoding on an explicitly owned emulator; never a subscriber's account. */
public final class AndroidStreamPlaybackTest extends Instrumentation {
    private Context context;private Activity activity;private LiveTvPlayer player;private Bundle args;
    private String base,profile,previous,mode,phase="setup";private final Bundle result=new Bundle();
    interface Ready {boolean ready()throws Exception;}
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    Object field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    void ui(Runnable work){runOnMainSync(work);waitForIdleSync();}
    void await(Ready work)throws Exception{long until=SystemClock.elapsedRealtime()+35000;while(SystemClock.elapsedRealtime()<until){waitForIdleSync();if(work.ready())return;Thread.sleep(60);}throw new AssertionError("Timeout: "+phase);}
    JSONObject metrics()throws Exception{try(InputStream in=new URL(base+"/metrics").openStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[4096];for(int n;(n=in.read(block))!=-1;)out.write(block,0,n);return new JSONObject(out.toString("UTF-8"));}}
    LiveTvData.Channel channel(String id,String url){return new LiveTvData.Channel(id,"Fixture FHD","Fixture",base+"/logo.png",Collections.singletonList(base+url));}
    void tune(LiveTvData.Channel channel){ui(()->{player.pause();player.select(channel);player.resume();});}
    void decoded()throws Exception{await(()->player.rendered);check((player.dvr==null)!=(player.player==null),"Two playback engines are active");}
    void stop(){ui(player::pause);check(player.dvr==null&&player.player==null,"Pause retained a decoder");}
    void policy()throws Exception{
        phase="bounded recovery policy";
        for(int p=0;p<3;p++)for(boolean light:new boolean[]{false,true}){VelaPlaybackPolicy.Buffer b=new VelaPlaybackPolicy.Buffer(p,light);check(b.bytes<=(light?4:8)*1024*1024&&b.startMs<=b.minMs&&b.rebufferMs<=b.minMs&&b.minMs<=b.maxMs,"Buffer profile escaped its bounds");}
        VelaPlaybackRecovery r=new VelaPlaybackRecovery();check(r.start("a",1,0),"Initial start refused");check(r.failed(VelaPlaybackRecovery.Failure.NETWORK,1,true)==VelaPlaybackRecovery.Action.RETRY,"Network fault changed decoder immediately");r.start("a",1,1000);check(r.failed(VelaPlaybackRecovery.Failure.NETWORK,1,true)==VelaPlaybackRecovery.Action.NEXT_SIGNAL,"Network retries are unbounded");
        r=new VelaPlaybackRecovery();r.start("a",1,0);check(r.failed(VelaPlaybackRecovery.Failure.DECODER,1,true)==VelaPlaybackRecovery.Action.OTHER_ENGINE,"Codec failure did not offer compatibility");r.start("a",2,1);check(r.failed(VelaPlaybackRecovery.Failure.DECODER,2,true)==VelaPlaybackRecovery.Action.NEXT_SIGNAL,"Codec fallback loops");
        r=new VelaPlaybackRecovery();r.start("a",1,0);r.failed(VelaPlaybackRecovery.Failure.ACCESS,1,true);check(r.sameVariantOnly(),"Denied access would probe other channel qualities");check(!r.start("b",1,90000),"Recovery time budget escaped");r.stable(1);check(r.start("b",1,90001),"A later interruption cannot recover after stable playback");
        r=new VelaPlaybackRecovery();for(int i=0;i<10;i++)check(r.start("a"+i,1,i),"Early attempt budget");check(!r.start("overflow",1,11),"Attempt budget escaped");
    }
    void m3u()throws Exception{
        phase="M3U headers and encrypted catalogue";String source="m3u:"+LiveTvData.hash(base+"/owned.m3u");
        String list="#EXTM3U\n#EXTINF:-1 tvg-id=\"guarded\",Guarded FHD\n#EXTVLCOPT:http-user-agent=Vela-Stream-Fixture\n#EXTVLCOPT:http-referrer=https://vela-fixture.invalid/watch\n"+base+"/protected/master.m3u8\n#EXTINF:-1 tvg-id=\"guarded\",Guarded FHD\n"+base+"/protected2/live.m3u8|User-Agent=Vela-Stream-Fixture-2&Referer=https%3A%2F%2Fvela-fixture.invalid%2Fwatch\n#EXTINF:-1 tvg-id=\"plain\",Plain\n"+base+"/plain.ts\n#EXTINF:-1 tvg-id=\"kodi\",Passive HLS\n#KODIPROP:inputstream=inputstream.adaptive\n#KODIPROP:inputstream.adaptive.manifest_type=hls\n#KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Vela-Stream-Fixture&Referer=https%3A%2F%2Fvela-fixture.invalid%2Fwatch\n"+base+"/protected/live.m3u8\n#EXTINF:-1,Unsupported DRM\n#KODIPROP:inputstream.adaptive.license_type=unsupported\n"+base+"/never.m3u8\n";
        TvCatalogCache cache=new TvCatalogCache(context);try(TvCatalogCache.Writer writer=cache.writer(source)){M3uClient.parse(new StringReader(list),true,writer);writer.commit("{}");}
        TvCatalogCache.Page page=cache.page(source,null,"",null,0);check(page.total==3,"Header support dropped ordinary channels or accepted executable/DRM options");
        LiveTvData.Channel protectedChannel=null;for(LiveTvData.Channel c:page.channels)if(c.id.equals("guarded"))protectedChannel=c;
        check(protectedChannel!=null&&protectedChannel.streams.size()==2,"Same-channel alternatives did not merge");
        check("Vela-Stream-Fixture".equals(VelaStreamHeaders.forSignal(protectedChannel,protectedChannel.streams.get(0)).get("User-Agent")),"VLC header not retained");check("Vela-Stream-Fixture-2".equals(VelaStreamHeaders.forSignal(protectedChannel,protectedChannel.streams.get(1)).get("User-Agent")),"Per-URL headers crossed alternatives");
        for(LiveTvData.Channel c:page.channels)if(c.id.equals("plain"))check(c.requestHeaders.isEmpty(),"M3U headers leaked into the next entry");
        Map<String,String> invalid=new HashMap<>();invalid.put("User-Agent","Bad\r\nInjected: value");invalid.put("Authorization","not-allowed");invalid.put("Referer","file:///not-allowed");check(VelaStreamHeaders.clean(invalid).isEmpty(),"Unsafe header accepted");
        new M3uClient(context).configure(base+"/owned.m3u","",true);VelaPlaybackPanel.prefs(context).edit().putInt("engine",VelaPlaybackPolicy.AUTO).commit();
        tune(protectedChannel);long at=SystemClock.elapsedRealtime();decoded();check(player.dvr!=null,"Automatic HLS did not use Media3");result.putLong("protected_hls_first_frame_ms",SystemClock.elapsedRealtime()-at);check(metrics().getJSONObject("denied").length()==0,"Headers were lost on the master, media playlist or segment");stop();
        phase="headers through local pause";VelaPlaybackPanel.prefs(context).edit().putBoolean("timeshift",true).commit();tune(protectedChannel);decoded();check(field(player,"buffer")!=null,"Local pause did not preserve the source headers");check(metrics().getJSONObject("denied").length()==0,"Disk-ring fetch lost headers");stop();VelaPlaybackPanel.prefs(context).edit().putBoolean("timeshift",false).commit();
        phase="explicit system compatibility";VelaPlaybackPanel.prefs(context).edit().putInt("engine",VelaPlaybackPolicy.SYSTEM).commit();tune(channel("system","/plain.ts"));decoded();check(player.player!=null&&player.dvr==null,"Explicit compatibility mode opened Media3");stop();
        VelaPlaybackPanel.prefs(context).edit().putInt("engine",VelaPlaybackPolicy.AUTO).commit();
    }
    void xtream()throws Exception{
        phase="Xtream format recovery";XtreamClient client=new XtreamClient(context);XtreamClient.Account account=client.login(base,"demo","fixture",true);client.save(account);
        LiveTvData.Channel c=new LiveTvData.Channel("format-fixture","Fixture FHD","Fixture",base+"/logo.png",Collections.emptyList(),"101",account.id);
        int before=metrics().getJSONObject("hits").optInt("/live/demo/fixture/101.m3u8");tune(c);decoded();check(((TvSignalFallback.Signal)field(player,"signal")).url.endsWith(".ts"),"Missing HLS did not fall back to TS");
        await(()->VelaWorkspace.prefs(context,"vela-playback-hints").getString("items","{}").contains("\"ts\""));stop();
        phase="remember successful format";int tried=metrics().getJSONObject("hits").optInt("/live/demo/fixture/101.m3u8");check(tried==before+1,"Missing HLS retried unnecessarily");tune(c);decoded();check(metrics().getJSONObject("hits").optInt("/live/demo/fixture/101.m3u8")==tried,"Learned TS still retried failed HLS");stop();
        phase="access refusal stays bounded";LiveTvData.Channel denied=new LiveTvData.Channel("denied","Denied FHD","Fixture",base+"/logo.png",Collections.emptyList(),"401",account.id);tune(denied);await(()->player.player==null&&player.dvr==null&&((TvSignalFallback)field(player,"signals")).attempts()==2&&((android.widget.TextView)field(player,"message")).getText().toString().equals(L10n.text("The source refused access. Check the source settings.")));check(((android.widget.TextView)field(player,"message")).getText().toString().equals(L10n.text("The source refused access. Check the source settings.")),"Denied source did not explain access failure");stop();
        phase="transient network recovery";tune(channel("transient","/transient.m3u8"));decoded();check(metrics().getJSONObject("hits").optInt("/transient.m3u8")>=2,"Temporary server failure was not retried");stop();
    }
    void hints()throws Exception{
        phase="hint limits and portable preferences";for(int i=0;i<40;i++)VelaPlaybackPolicy.success(context,channel("bounded-"+i,"/plain.ts"),base+"/plain.ts?fixture="+i,VelaPlaybackPolicy.MEDIA3);
        String raw=VelaWorkspace.prefs(context,"vela-playback-hints").getString("items","");check(new JSONObject(raw).length()==32&&raw.length()<=16384&&!raw.contains(base)&&!raw.contains("fixture="),"Playback learning retained a catalogue, URL or secret");
        VelaPlaybackPanel.prefs(context).edit().putInt("engine",1).putInt("buffer",2).commit();JSONObject portable=VelaWorkspace.snapshot(context);check(portable.getJSONObject("settings").getJSONObject("vela-playback").getInt("buffer")==2&&!portable.toString().contains("vela-playback-hints")&&!portable.toString().contains(base),"Backup omitted choices or included connection data");
        JSONObject malformed=new JSONObject(portable.toString());malformed.getJSONObject("settings").getJSONObject("vela-playback").put("engine",true);boolean refused=false;try{VelaWorkspace.apply(context,malformed,true);}catch(IOException expected){refused=true;}check(refused,"Invalid playback setting type accepted");
        String other=VelaWorkspace.create(context,"Other playback");VelaWorkspace.select(context,other);check(VelaPlaybackPolicy.engine(context)==0&&VelaPlaybackPolicy.profile(context)==1&&VelaWorkspace.prefs(context,"vela-playback-hints").getString("items","").isEmpty(),"Playback choices or hints crossed profiles");VelaWorkspace.select(context,profile);VelaWorkspace.delete(context,other);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor file=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(file)){check(in.read()=='1',"Owned emulator required");}
        base=args.getString("fixture","");URI uri=new URI(base);check("http".equals(uri.getScheme())&&"127.0.0.1".equals(uri.getHost())&&uri.getPort()>0&&uri.getRawPath().isEmpty(),"Owned loopback fixture required");
        check(!new XtreamClient(context).connected()&&!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"Personal accounts are not allowed");previous=VelaWorkspace.active(context);mode=VelaEdition.prefs(context).getString("mode","");VelaEdition.select(context,"full");profile=VelaWorkspace.create(context,"Playback fixture");VelaWorkspace.select(context,profile);Personalization.prepare(context);Personalization.complete(context);Updates.prefs(context).edit().putBoolean("automatic",false).commit();new XmlTvGuide(context).configure("");Ui.init(context);VelaFrameRate.mode(context,0);VelaPlaybackPanel.prefs(context).edit().putInt("buffer",0).commit();policy();
        activity=startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->{player=new LiveTvPlayer(activity,new LiveTvPlayer.Actions(){public void back(){}public void zap(int d){}public void channels(int p){}public void choose(LiveTvData.Channel c){}});activity.setContentView(player);});
        m3u();xtream();hints();result.putString("result","PASS real protected HLS master/media/segments, disk-ring headers, explicit system TS, single-engine lifecycle, Xtream HLS-to-TS learning, bounded access/network recovery, buffer policy, per-signal headers, profile isolation and credential-free portable choices. Synthetic loopback media only.");
    }catch(Throwable failure){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(failure));}finally{try{if(player!=null)ui(player::pause);if(profile!=null)new XtreamClient(context).disconnect();if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}if(mode!=null){if(mode.isEmpty())VelaEdition.prefs(context).edit().remove("mode").commit();else VelaEdition.select(context,mode);}}catch(Exception ignored){}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
