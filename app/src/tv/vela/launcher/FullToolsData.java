package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Full edition adapter. Credentials stay inside the existing service clients. */
abstract class FullToolsData extends VelaToolsData {
    FullToolsData(Activity a){super(a);}
    public boolean hasCollections(){return new LibraryClient(activity).connected();}
    public List<VelaHub.Hit> collections(String source,int page,CancellationSignal signal)throws Exception{LibraryClient client=new LibraryClient(activity);VelaHub.Hits out=new VelaHub.Hits();if(source.isEmpty()){List<LibraryClient.SectionSource> roots=client.collectionSources();for(int i=page*12;i<Math.min(roots.size(),page*12+12);i++){LibraryClient.SectionSource root=roots.get(i);out.add(new VelaHub.Hit("collection-source",root.title,L10n.text("Choose a library"),root.key));}out.more=(page+1)*12<roots.size();}else for(LibraryCollection item:client.collections(source,page)){signal.throwIfCanceled();out.add(new VelaHub.Hit("collection",item.title,item.provider,item.json()));}return out;}
    public boolean collectionPinned(VelaHub.Hit hit){try{LibraryCollection item=LibraryCollection.read((org.json.JSONObject)hit.value);for(LibraryCollection p:LibraryCollection.pinned(activity))if(p.identity().equals(item.identity()))return true;}catch(Exception bad){}return false;}
    public void pinCollection(VelaHub.Hit hit)throws Exception{new LibraryClient(activity).toggleCollection(LibraryCollection.read((org.json.JSONObject)hit.value));}
    List<VelaHub.Hit> channels(String query,int page,CancellationSignal signal)throws Exception{
        String mode=LiveTvData.mode(activity);TvCatalogCache.Page result;
        if(mode.equals("antenna"))result=new AntennaClient(activity).page(query,null,page,signal);
        else result=new TvCatalogCache(activity).page(LiveTvData.source(activity),null,query,null,page,signal);
        VelaHub.Hits out=new VelaHub.Hits();if(result.page!=page||page*12>=result.total)return out;out.more=(page+1)*12<result.total;for(LiveTvData.Channel c:result.channels){signal.throwIfCanceled();out.add(new VelaHub.Hit(VelaWorkspace.t("Channel","Canal"),TvChannelNames.display(c.name),c.group,c));}return out;
    }
    List<VelaHub.Hit> media(String query,int page,CancellationSignal signal)throws Exception{return media(query,page,signal,"");}
    List<VelaHub.Hit> media(String query,int page,CancellationSignal signal,String provider)throws Exception{
        LibraryClient library=new LibraryClient(activity);if(!provider.isEmpty())library=library.home(provider);VelaHub.Hits out=new VelaHub.Hits();if(!library.connected())return out;
        LibraryClient.Search result=library.search(query,page,signal);out.more=result.more;out.partial=result.partial;
        for(PlexClient.Film film:result.films){signal.throwIfCanceled();out.add(new VelaHub.Hit(LibraryClient.jf(film)?"Jellyfin":"Plex",film.title,film.meta,film));}return out;
    }
    public List<VelaShows.Airing> airings(String query,int page,CancellationSignal signal)throws Exception{
        if(LiveTvData.mode(activity).equals("antenna")){String tuner=HomeApps.tunerInput(activity);if(tuner==null)return Collections.emptyList();Map<Long,String> names=VelaToolsData.nativeNames(activity,tuner,signal);Set<String> hidden=new TvPersonalization(activity,"antenna").hiddenKeys();names.entrySet().removeIf(e->hidden.contains("ant-"+e.getKey()));return VelaToolsData.merge(VelaToolsData.nativeAirings(activity,names,query,page,signal),new XmlTvGuide(activity).antennaAirings(names,query,page,signal));}
        return new TvCatalogCache(activity).airings(query,page,signal);
    }
    public List<VelaShows.Airing> discover(int mode,int genre,int page,CancellationSignal signal)throws Exception{if(!LiveTvData.mode(activity).equals("antenna"))return new TvCatalogCache(activity).discover(mode,genre,page,signal);String tuner=HomeApps.tunerInput(activity);if(tuner==null)return Collections.emptyList();Map<Long,String> names=VelaToolsData.nativeNames(activity,tuner,signal);Set<String> hidden=new TvPersonalization(activity,"antenna").hiddenKeys();names.entrySet().removeIf(e->hidden.contains("ant-"+e.getKey()));List<VelaShows.Airing> cached=new XmlTvGuide(activity).discover(names,mode,genre,page,signal);return !cached.isEmpty()?VelaDiscovery.nativeLogos(cached,names):VelaDiscovery.local(activity,names,mode,genre,page,signal);}
    public org.json.JSONObject mobileState(int page)throws Exception{return super.mobileState(page).put("pinned",new org.json.JSONArray(Arrays.asList(SidebarApps.slots(activity))));}
    void pin(org.json.JSONObject j)throws Exception{String pkg=j.getString("pkg");String[] before=SidebarApps.slots(activity);List<String> order=new ArrayList<>(Arrays.asList(before));int index=order.indexOf(pkg);if(j.getString("op").equals("pin")){if(index>=0)SidebarApps.remove(activity,before,pkg);else if(before.length<128)SidebarApps.add(activity,pkg);else throw new java.io.IOException();}else{int direction=j.getInt("direction");if(index<0||Math.abs(direction)!=1)throw new java.io.IOException();Collections.swap(order,index,Math.max(0,Math.min(order.size()-1,index+direction)));if(!SidebarApps.reorder(activity,before,order.toArray(new String[0])))throw new java.io.IOException();}}
    void source(org.json.JSONObject j)throws Exception{String expected=VelaWorkspace.active(activity);String url=j.getString("url").trim();if(j.getString("kind").equals("Xtream")){XtreamClient client=new XtreamClient(activity);XtreamClient.Account account=client.login(url,j.getString("user"),j.getString("password"),url.startsWith("http://"));synchronized(VelaWorkspace.class){if(!expected.equals(VelaWorkspace.active(activity)))throw new java.io.IOException("Profile changed");client.save(account);}}else if(j.getString("kind").equals("M3U"))new M3uClient(activity).configure(url,"",url.startsWith("http://"));else throw new java.io.IOException();}
    void favorite(LiveTvData.Channel c)throws Exception{if(!c.xtreamId.isEmpty())new XtreamClient(activity).toggleFavorite(c);else{Set<String> saved=LiveTvData.favorites(activity,LiveTvData.source(activity));Set<String> next=new HashSet<>(saved);if(!next.add(c.id))next.remove(c.id);LiveTvData.prefs(activity).edit().putStringSet("favorites-"+LiveTvData.hash(LiveTvData.source(activity)),next).commit();}}
    public org.json.JSONObject reference(VelaHub.Hit hit)throws Exception{if(hit.value instanceof PlexClient.Film)return new LibraryClient(activity).reference((PlexClient.Film)hit.value);if(hit.value instanceof VelaShows.Airing)return VelaWatchlist.airing((VelaShows.Airing)hit.value);return null;}
    public VelaHub.Hit resolve(org.json.JSONObject item,CancellationSignal signal)throws Exception{signal.throwIfCanceled();PlexClient.Film film=new LibraryClient(activity).resolve(item);signal.throwIfCanceled();return film==null?null:new VelaHub.Hit(film.provider,film.title,film.meta,film);}
    public void repair(){new XmlTvGuide(activity).invalidate();}
    public String scope(){return new LibraryClient(activity).home("both").identity()+"|"+LiveTvData.hash(VelaGuideFix.all(activity).toString())+"|"+LiveTvData.source(activity)+"|"+activity.getSharedPreferences("xmltv",0).getBoolean("native",false)+"|"+LiveTvData.hash(activity.getSharedPreferences("xmltv",0).getString("url","default"));}
    public void prepareBackup()throws Exception{XtreamClient client=new XtreamClient(activity);if(client.connected()){client.account();client.favoriteIds();new TvPersonalization(activity,client.source());}}
}
