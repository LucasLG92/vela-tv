package tv.vela.launcher;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** At most 64 references per local profile. No media, artwork, URLs or authentication data. */
final class VelaWatchlist {
    static final int MAX=64;
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-watchlist");}
    static List<JSONObject> read(Context c){List<JSONObject> list=new ArrayList<>();String raw=prefs(c).getString("items","[]");if(raw.length()>64*1024)return list;try{JSONArray a=new JSONArray(raw);for(int i=0;i<Math.min(MAX,a.length());i++){JSONObject p=a.getJSONObject(i);if(p.optString("id").length()<=256&&p.optString("title").length()<=320)list.add(p);}}catch(Exception bad){list.clear();}return list;}
    static boolean contains(Context c,String id){for(JSONObject p:read(c))if(id.equals(p.optString("id")))return true;return false;}
    static void toggle(Context c,JSONObject item)throws Exception{
        String id=item.getString("id");if(id.isEmpty()||id.length()>256||item.toString().length()>2048)throw new IOException("Invalid reference");
        List<JSONObject> list=read(c);boolean removed=list.removeIf(p->id.equals(p.optString("id")));if(!removed){if(list.size()>=MAX)throw new IOException("Watch later is full");list.add(0,item);}
        String encoded=new JSONArray(list).toString();if(encoded.length()>64*1024)throw new IOException("Watch later size limit");
        if(!prefs(c).edit().putString("items",encoded).commit())throw new IOException("Watch later write failed");
    }
    static String airingId(VelaShows.Airing p){
        String channel=p.channelKey.isEmpty()?TvChannelNames.match(p.channel):p.channelKey;
        if(!p.source.isEmpty())channel=p.source+"|"+channel;
        return "tv:"+LiveTvData.hash(channel+"|"+p.start+"|"+p.end+"|"+VelaShows.identity(p.title));
    }
    static JSONObject broadcastFields(VelaShows.Airing p)throws JSONException{
        JSONObject item=new JSONObject().put("title",p.title).put("channel",p.channel).put("start",p.start).put("end",p.end);
        if(!p.source.isEmpty())item.put("source",p.source).put("channelKey",p.channelKey);
        return item;
    }
    static JSONObject airing(VelaShows.Airing p)throws Exception{return broadcastFields(p).put("id",airingId(p)).put("kind","tv");}
    static VelaShows.Airing asAiring(JSONObject p){return new VelaShows.Airing(p.optString("title"),p.optString("channel"),p.optLong("start"),p.optLong("end"),"","","","",p.optString("source"),p.optString("channelKey"));}
}
