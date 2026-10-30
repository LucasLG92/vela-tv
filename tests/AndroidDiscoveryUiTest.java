package tv.vela.launcher;
import android.app.*;import android.content.*;import android.view.*;import java.util.*;
public final class AndroidDiscoveryUiTest extends DiscoveryUiContract {
    private HomeActivity home;private CinemaHomeView scene;private final List<LiveTvData.Channel> stations=new ArrayList<>();
    protected Activity launch()throws Exception{Personalization.complete(context);Updates.prefs(context).edit().putBoolean("automatic",false).commit();new M3uClient(context).configure("https://vela-fixture.invalid/discovery.m3u","",false);String source=LiveTvData.source(context);try(TvCatalogCache.Writer w=new TvCatalogCache(context).writer(source)){for(int i=0;i<CHANNELS.length;i++){LiveTvData.Channel c=new LiveTvData.Channel("discovery-"+i,CHANNELS[i],"Demo","",Collections.singletonList("http://127.0.0.1:9/owned-"+i+".ts"));stations.add(c);w.add(c,false);}w.commit("{}");}home=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));scene=(CinemaHomeView)field(home,"home");return home;}
    protected void television(){scene.navigateSection(6);}
    protected View discoverButton()throws Exception{Object tv=field(scene,"liveTv");return tv==null?null:(View)field(tv,"discoverAction");}
    protected View timeline()throws Exception{return (View)field(field(scene,"liveTv"),"timeline");}
    protected VelaWidgets.Strip widgets()throws Exception{return (VelaWidgets.Strip)field(scene,"headerStatus");}
    protected VelaHub currentHub()throws Exception{return (VelaHub)field(home,"hub");}
    protected void installFixture(VelaHub hub){scene.pause();set(home,"hub",hub);scene.mountTools(hub);}
    protected void removeFixture(){set(home,"hub",null);scene.unmountTools();scene.resume();}
    protected List<VelaHub.Hit> channels(String query){List<VelaHub.Hit> out=new ArrayList<>();for(LiveTvData.Channel c:stations)if(c.name.equals(query))out.add(new VelaHub.Hit("Channel",c.name,"Demo",c));return out;}
}
