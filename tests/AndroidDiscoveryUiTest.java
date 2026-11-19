package tv.vela.launcher;
import android.widget.ListView;import android.app.*;import android.content.*;import android.view.*;import java.util.*;
public final class AndroidDiscoveryUiTest extends DiscoveryUiContract {
    private String priorMode;private HomeActivity home;private CinemaHomeView scene;private final List<LiveTvData.Channel> stations=new ArrayList<>();
    public void finish(int code,android.os.Bundle results){if("lite".equals(priorMode))VelaEdition.select(context,"lite");super.finish(code,results);}
    protected Activity launch()throws Exception{priorMode=VelaEdition.mode(context);VelaEdition.select(context,"full");Personalization.complete(context);LiveTvData.guideView(context,true);Updates.prefs(context).edit().putBoolean("automatic",false).commit();new M3uClient(context).configure("https://vela-fixture.invalid/discovery.m3u","",false);String source=LiveTvData.source(context);try(TvCatalogCache.Writer w=new TvCatalogCache(context).writer(source)){for(int i=0;i<CHANNELS.length;i++){LiveTvData.Channel c=new LiveTvData.Channel("discovery-"+i,CHANNELS[i],"Generalistas","https://vela-fixture.invalid/logo/"+LOGOS[i],Collections.singletonList("http://127.0.0.1:9/owned-"+i+".ts"));stations.add(c);w.add(c,false);}w.commit("{}");}seedSchedule();home=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));scene=(CinemaHomeView)field(home,"home");return home;}
    private void seedSchedule()throws Exception{
        String[][] shows={{"Sintel","Noticias 24h","La tarde en 24h"},{"La ruleta de la suerte","Antena 3 Noticias","Deportes"},{"Callejeros viajeros","Todo es mentira","Noticias Cuatro"},{"Al rojo vivo","laSexta Noticias","Zapeando"},{"Vamos a ver","Informativos Telecinco","El tiempo"},{"Ciudad Real Madrid","Conecta informativo","Historias del Real Madrid"}};
        java.text.SimpleDateFormat clock=new java.text.SimpleDateFormat("yyyyMMddHHmmss Z",java.util.Locale.ROOT);
        long now=System.currentTimeMillis()/1000,start=TvTimelineView.present(now)-1800;
        StringBuilder xml=new StringBuilder("<tv>");
        for(int i=0;i<stations.size();i++){
            LiveTvData.Channel channel=stations.get(i);
            xml.append("<channel id='").append(channel.id).append("'><display-name>").append(channel.name).append("</display-name></channel>");
            long at=start;
            for(int j=0;j<3;j++){
                long end=at+(j==0?4200+i*180:3600+i*120);
                xml.append("<programme channel='").append(channel.id).append("' start='").append(clock.format(new java.util.Date(at*1000))).append("' stop='").append(clock.format(new java.util.Date(end*1000))).append("'><title>").append(shows[i][j]).append("</title>");
                if(i==0&&j==0)xml.append("<icon src='https://vela-fixture.invalid/art/sintel.jpg'/>");
                xml.append("</programme>");
                at=end;
            }
        }
        XmlTvGuide guide=new XmlTvGuide(context);guide.configure("https://vela-fixture.invalid/guia.xml");
        try(java.io.InputStream data=new java.io.ByteArrayInputStream(xml.append("</tv>").toString().getBytes("UTF-8"))){guide.importGuide(data,LiveTvData.hash(guide.url()),now);}
        check(guide.window(stations,TvTimelineView.present(now)).size()==stations.size(),"Preview guide import lost a channel");
    }
    @Override protected void captureGuideViews(View entry)throws Exception{
        View guide=timeline();
        VelaWidgets.State ambient=new VelaWidgets.State();
        ambient.items.add("clock");ambient.format=1;
        ui(()->VelaWidgets.save(context,ambient));
        await(()->!((Map<?,?>)field(guide,"programmes")).isEmpty());
        ui(guide::requestFocus);
        android.widget.ImageView art=(android.widget.ImageView)field(field(scene,"liveTv"),"guideBackdrop");
        await(()->art.getDrawable()!=null);
        View tv=(View)field(scene,"liveTv");
        check(art.getLeft()==0&&art.getTop()==0&&art.getWidth()==tv.getWidth(),"Guide artwork does not cover the header");
        check(rect(art).bottom<=rect(guide).top&&((android.view.ViewGroup)tv).indexOfChild(art)==0,"Artwork covers the schedule or toolbar");
        shot("guia");
        TvTimelineView grid=(TvTimelineView)guide;LiveTvData.Programme current=grid.programme();
        check(current!=null,"Missing live selection");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(()->grid.programme()!=null&&grid.programme().start>=current.end);await(()->art.getDrawable()==null);shot("guia-despues");
        View nowButton=(View)field(field(scene,"liveTv"),"present");
        ui(nowButton::performClick);await(()->grid.programme()!=null&&grid.programme().start==current.start&&grid.followsNow());
        ui(()->galleryTvMode(false));await(()->!guide.isShown());shot("canales");
        ui(()->galleryTvMode(true));await(guide::isShown);ui(entry::requestFocus);
    }
    private void galleryTvMode(boolean guide){LiveTvData.guideView(context,guide);try{((HomeLiveTvView)field(scene,"liveTv")).resume();}catch(Exception error){throw new RuntimeException(error);}}
    protected void television(){scene.navigateSection(6);}
    protected void verifyGuideEntry()throws Exception{
        ListView categories=(ListView)field(field(scene,"liveTv"),"categoryList");
        check(categories.hasFocus(),"Guide stole focus from categories during startup");
        key(KeyEvent.KEYCODE_DPAD_DOWN);check(categories.hasFocus(),"Cannot browse categories");
        key(KeyEvent.KEYCODE_DPAD_UP);
    }
    protected View discoverButton()throws Exception{Object tv=field(scene,"liveTv");return tv==null?null:(View)field(tv,"discoverAction");}
    protected View timeline()throws Exception{return (View)field(field(scene,"liveTv"),"timeline");}
    protected VelaWidgets.Strip widgets()throws Exception{return (VelaWidgets.Strip)field(scene,"headerStatus");}
    protected VelaHub currentHub()throws Exception{return (VelaHub)field(home,"hub");}
    protected void installFixture(VelaHub hub){scene.pause();set(home,"hub",hub);scene.mountTools(hub);}
    protected void removeFixture(){set(home,"hub",null);scene.unmountTools();scene.resume();}
    protected List<VelaHub.Hit> channels(String query){List<VelaHub.Hit> out=new ArrayList<>();for(LiveTvData.Channel c:stations)if(c.name.equals(query))out.add(new VelaHub.Hit("Channel",c.name,"Demo",c));return out;}
}
