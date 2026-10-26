package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Full edition adapter. Credentials stay inside the existing service clients. */
abstract class FullToolsData extends VelaToolsData {
    private String mediaQuery="";private final Map<String,Boolean> mediaMore=new HashMap<>();
    FullToolsData(Activity a){super(a);}
    List<VelaHub.Hit> channels(String query,int page,CancellationSignal signal)throws Exception{
        String mode=LiveTvData.mode(activity);TvCatalogCache.Page result;
        if(mode.equals("antenna"))result=new AntennaClient(activity).page(query,null,page);
        else if(mode.equals("xtream")){XtreamClient.Page p=new XtreamClient(activity).page("*","",page,query,false);result=new TvCatalogCache.Page(p.channels,p.page,p.total);}
        else result=new TvCatalogCache(activity).page(M3uClient.identity(activity),null,query,null,page);
        VelaHub.Hits out=new VelaHub.Hits();if(result.page!=page||page*12>=result.total)return out;out.more=(page+1)*12<result.total;for(LiveTvData.Channel c:result.channels){signal.throwIfCanceled();out.add(new VelaHub.Hit(VelaWorkspace.t("Channel","Canal"),TvChannelNames.display(c.name),c.group,c));}return out;
    }
    List<VelaHub.Hit> media(String query,int page,CancellationSignal signal)throws Exception{
        LibraryClient library=new LibraryClient(activity);VelaHub.Hits out=new VelaHub.Hits();if(!library.connected())return out;if(page==0||!query.equals(mediaQuery)){mediaQuery=query;mediaMore.clear();}
        List<PlexClient.Section> sections=library.sections();int groups=Math.max(1,(sections.size()+5)/6),group=page%groups,offset=(page/groups)*4;CatalogFilters.Query q=new CatalogFilters.Query();q.search=query;
        for(int i=group*6;i<Math.min(sections.size(),group*6+6);i++){signal.throwIfCanceled();PlexClient.Section section=sections.get(i);if(offset>0&&Boolean.FALSE.equals(mediaMore.get(section.key)))continue;PlexClient.LibraryPage result=library.page(section,offset,q);int taken=0;for(PlexClient.Film film:result.films){if(taken++>=4)break;out.add(new VelaHub.Hit(library.provider(),film.title,section.title,film));}mediaMore.put(section.key,result.total<0?result.received>4:offset+4<result.total);}
        for(PlexClient.Section section:sections)if(!Boolean.FALSE.equals(mediaMore.get(section.key))){out.more=true;break;}return out;
    }
    public List<VelaShows.Airing> airings(String query,int page,CancellationSignal signal)throws Exception{
        if(LiveTvData.mode(activity).equals("antenna")){String tuner=HomeApps.tunerInput(activity);if(tuner==null)return Collections.emptyList();Map<Long,String> names=VelaToolsData.nativeNames(activity,tuner,signal);Set<String> hidden=new TvPersonalization(activity,"antenna").hiddenKeys();names.entrySet().removeIf(e->hidden.contains("ant-"+e.getKey()));return VelaToolsData.merge(VelaToolsData.nativeAirings(activity,names,query,page,signal),new XmlTvGuide(activity).antennaAirings(names,query,page,signal));}
        return new TvCatalogCache(activity).airings(query,page,signal);
    }
    public List<VelaShows.Airing> discover(int mode,int genre,int page,CancellationSignal signal)throws Exception{if(!LiveTvData.mode(activity).equals("antenna"))return new TvCatalogCache(activity).discover(mode,genre,page,signal);String tuner=HomeApps.tunerInput(activity);if(tuner==null)return Collections.emptyList();Map<Long,String> names=VelaToolsData.nativeNames(activity,tuner,signal);Set<String> hidden=new TvPersonalization(activity,"antenna").hiddenKeys();names.entrySet().removeIf(e->hidden.contains("ant-"+e.getKey()));List<VelaShows.Airing> cached=new XmlTvGuide(activity).discover(names,mode,genre,page,signal);return !cached.isEmpty()?cached:VelaDiscovery.local(activity,names,mode,genre,page,signal);}
    public org.json.JSONObject mobileState(int page)throws Exception{return super.mobileState(page).put("pinned",new org.json.JSONArray(Arrays.asList(SidebarApps.slots(activity))));}
    void pin(org.json.JSONObject j)throws Exception{String pkg=j.getString("pkg");String[] before=SidebarApps.slots(activity);List<String> order=new ArrayList<>(Arrays.asList(before));int index=order.indexOf(pkg);if(j.getString("op").equals("pin")){if(index>=0)SidebarApps.remove(activity,before,pkg);else if(before.length<128)SidebarApps.add(activity,pkg);else throw new java.io.IOException();}else{int direction=j.getInt("direction");if(index<0||Math.abs(direction)!=1)throw new java.io.IOException();Collections.swap(order,index,Math.max(0,Math.min(order.size()-1,index+direction)));if(!SidebarApps.reorder(activity,before,order.toArray(new String[0])))throw new java.io.IOException();}}
    void source(org.json.JSONObject j)throws Exception{String expected=VelaWorkspace.active(activity);String url=j.getString("url").trim();if(j.getString("kind").equals("Xtream")){XtreamClient client=new XtreamClient(activity);XtreamClient.Account account=client.login(url,j.getString("user"),j.getString("password"),url.startsWith("http://"));synchronized(VelaWorkspace.class){if(!expected.equals(VelaWorkspace.active(activity)))throw new java.io.IOException("Profile changed");client.save(account);}}else if(j.getString("kind").equals("M3U"))new M3uClient(activity).configure(url,"",url.startsWith("http://"));else throw new java.io.IOException();}
    void favorite(LiveTvData.Channel c)throws Exception{if(!c.xtreamId.isEmpty())new XtreamClient(activity).toggleFavorite(c);else{Set<String> saved=LiveTvData.favorites(activity,LiveTvData.source(activity));Set<String> next=new HashSet<>(saved);if(!next.add(c.id))next.remove(c.id);LiveTvData.prefs(activity).edit().putStringSet("favorites-"+LiveTvData.hash(LiveTvData.source(activity)),next).commit();}}
    public void repair(){new XmlTvGuide(activity).invalidate();}
    public String scope(){return LiveTvData.hash(VelaGuideFix.all(activity).toString())+"|"+LiveTvData.source(activity)+"|"+activity.getSharedPreferences("xmltv",0).getBoolean("native",false)+"|"+LiveTvData.hash(activity.getSharedPreferences("xmltv",0).getString("url","default"));}
    public void prepareBackup()throws Exception{XtreamClient client=new XtreamClient(activity);if(client.connected()){client.account();client.favoriteIds();new TvPersonalization(activity,client.source());}}
}
