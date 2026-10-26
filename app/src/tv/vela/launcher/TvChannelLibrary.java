package tv.vela.launcher;

import android.content.*;
import org.json.*;
import java.util.*;

/** Full-edition adapter. History is a bounded, device-encrypted shortlist, never a second catalog. */
final class TvChannelLibrary {
    static final String RECENT="@recent";
    static String key(LiveTvData.Channel c){return c.antenna()?c.id:TvChannelNames.family(c);}
    static List<TvPersonalization.Item> favoriteItems(Context c,String mode,String source)throws Exception{
        List<LiveTvData.Channel> channels;
        if(mode.equals("xtream"))channels=new XtreamClient(c).favoriteItems();
        else if(mode.equals("m3u"))channels=new TvCatalogCache(c).favoriteItems(source,LiveTvData.favorites(c,source));
        else{channels=new ArrayList<>();AntennaClient antenna=new AntennaClient(c);for(int page=0;page<11;page++){TvCatalogCache.Page result=antenna.page("",LiveTvData.favorites(c,source),page);channels.addAll(result.channels);if((page+1)*12>=result.total)break;}}
        TvPersonalization state=new TvPersonalization(c,source);Set<String> hidden=state.hiddenKeys();List<TvPersonalization.Item> values=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(LiveTvData.Channel channel:channels){String key=key(channel);if(!hidden.contains(key)&&seen.add(key))values.add(new TvPersonalization.Item(key,TvChannelNames.display(channel.name)));}state.sort(values);return values;
    }
    private static SharedPreferences history(Context c,String source){return VelaWorkspace.prefs(c,"tv-history-"+LiveTvData.hash(source));}
    private static List<LiveTvData.Channel> saved(Context c,String source){List<LiveTvData.Channel> result=new ArrayList<>();String encoded=history(c,source).getString("channels","");if(encoded.isEmpty()||encoded.length()>180*1024)return result;try{JSONArray a=new JSONArray(new TvPrivateData(c).decode(encoded));for(int i=0;i<Math.min(a.length(),TvPersonalization.MAX_RECENT);i++){JSONObject j=a.getJSONObject(i);result.add(TvCatalogCache.channel(j.getString("id"),j.getString("name"),j.getString("group"),j.getString("payload")));}}catch(Exception unavailable){}return result;}
    static void remember(Context c,String source,LiveTvData.Channel channel)throws Exception{synchronized(TvChannelLibrary.class){
        List<LiveTvData.Channel> values=saved(c,source);String key=key(channel);values.removeIf(v->key(v).equals(key));values.add(0,channel);JSONArray data=new JSONArray();for(int i=0;i<Math.min(values.size(),TvPersonalization.MAX_RECENT);i++){LiveTvData.Channel v=values.get(i);data.put(new JSONObject().put("id",v.id).put("name",v.name).put("group",v.group).put("payload",TvCatalogCache.json(v)));}String raw=data.toString();while(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>120*1024&&data.length()>1){data.remove(data.length()-1);raw=data.toString();}if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>120*1024)return;history(c,source).edit().putString("channels",new TvPrivateData(c).encode(raw)).apply();
    }}
    static List<LiveTvData.Channel> recent(Context c,String source){TvPersonalization state=new TvPersonalization(c,source);Set<String> hidden=state.hiddenKeys();List<LiveTvData.Channel> saved=saved(c,source),out=new ArrayList<>();for(TvPersonalization.Item item:state.recent())for(LiveTvData.Channel v:saved)if(key(v).equals(item.key)&&!hidden.contains(item.key)){out.add(v);break;}if(source.equals("antenna")){AntennaClient tuner=new AntennaClient(c);out.removeIf(ch->!tuner.matchesHistory(ch));}else try{return new TvCatalogCache(c).restoreHistory(source,out);}catch(Exception unavailable){}return out;}
    static TvCatalogCache.Page recentPage(Context c,String source){List<LiveTvData.Channel> values=recent(c,source);return new TvCatalogCache.Page(values,0,values.size());}
    static void clearRecent(Context c,String source){synchronized(TvChannelLibrary.class){new TvPersonalization(c,source).clearRecent();history(c,source).edit().remove("channels").apply();}}
}
