package tv.vela.launcher;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** At most 64 references per local profile. No media, artwork, URLs or authentication data. */
final class VelaWatchlist {
    static final int MAX=64;
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-watchlist");}
    private static String currentId(JSONObject item){
        String id=item.optString("id");
        if(id.startsWith("tv:")&&"tv".equals(item.optString("kind"))&&item.optLong("start")>0){
            return airingId(asAiring(item));
        }
        return id;
    }
    static List<JSONObject> read(Context c){
        List<JSONObject> list=new ArrayList<>();
        String raw=prefs(c).getString("items","[]");
        if(raw.length()>64*1024)return list;
        Set<String> broadcasts=new HashSet<>();
        try{
            JSONArray saved=new JSONArray(raw);
            for(int i=0;i<Math.min(MAX,saved.length());i++){
                JSONObject item=saved.optJSONObject(i);
                if(item==null||item.optString("title").length()>320)continue;
                String id=currentId(item);
                if(id.isEmpty()||id.length()>256)continue;
                // Watch later is newest first. Older copies can differ only in their EPG end time.
                if("tv".equals(item.optString("kind"))&&!broadcasts.add(id))continue;
                list.add(item.put("id",id));
            }
        }catch(JSONException invalid){list.clear();}
        return list;
    }
    static boolean contains(Context c,String id){for(JSONObject p:read(c))if(id.equals(p.optString("id")))return true;return false;}
    static void toggle(Context c,JSONObject item)throws Exception{
        String id=currentId(item);if(id.isEmpty()||id.length()>256||item.toString().length()>2048)throw new IOException("Invalid reference");
        List<JSONObject> list=read(c);boolean removed=list.removeIf(p->id.equals(p.optString("id")));if(!removed){if(list.size()>=MAX)throw new IOException("Watch later is full");list.add(0,id.equals(item.getString("id"))?item:new JSONObject(item.toString()).put("id",id));}
        String encoded=new JSONArray(list).toString();if(encoded.length()>64*1024)throw new IOException("Watch later size limit");
        if(!prefs(c).edit().putString("items",encoded).commit())throw new IOException("Watch later write failed");
    }
    static String airingId(VelaShows.Airing p){
        String channel=p.channelKey.isEmpty()?TvChannelNames.match(p.channel):p.channelKey;
        if(!p.source.isEmpty())channel=p.source+"|"+channel;
        return "tv2:"+LiveTvData.hash(channel+"|"+p.start+"|"+VelaShows.identity(p.title));
    }
    static JSONObject broadcastFields(VelaShows.Airing p)throws JSONException{
        JSONObject item=new JSONObject().put("title",p.title).put("channel",p.channel).put("start",p.start).put("end",p.end);
        if(!p.source.isEmpty())item.put("source",p.source).put("channelKey",p.channelKey);
        return item;
    }
    static JSONObject airing(VelaShows.Airing p)throws Exception{return broadcastFields(p).put("id",airingId(p)).put("kind","tv");}
    static VelaShows.Airing asAiring(JSONObject p){return new VelaShows.Airing(p.optString("title"),p.optString("channel"),p.optLong("start"),p.optLong("end"),"","","","",p.optString("source"),p.optString("channelKey"));}
}
