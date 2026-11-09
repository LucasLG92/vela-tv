package tv.vela.launcher;

import android.content.*;
import java.util.*;
import org.json.*;

/** Full network playback only. Tiny source/profile-scoped success hints; never stores a URL or account secret. */
final class VelaPlaybackPolicy {
    static final int AUTO=0,MEDIA3=1,SYSTEM=2,FAST=0,BALANCED=1,STABLE=2,MAX_HINTS=32;
    static final long HINT_TTL=7*86400_000L;
    static final class Buffer {
        final int minMs,maxMs,startMs,rebufferMs,bytes;final long timeoutMs,stallMs;
        Buffer(int profile,boolean light){
            minMs=new int[]{1000,2000,5000}[profile];maxMs=new int[]{3000,6000,12000}[profile];
            startMs=new int[]{300,700,1200}[profile];rebufferMs=new int[]{700,1500,3000}[profile];
            bytes=Math.min(new int[]{2,4,8}[profile],light?4:8)*1024*1024;
            timeoutMs=new int[]{12000,18000,25000}[profile];stallMs=new int[]{8000,12000,18000}[profile];
        }
    }
    static int engine(Context c){return Math.max(0,Math.min(2,VelaPlaybackPanel.prefs(c).getInt("engine",AUTO)));}
    static int profile(Context c){return Math.max(0,Math.min(2,VelaPlaybackPanel.prefs(c).getInt("buffer",BALANCED)));}
    static Buffer buffer(Context c){return new Buffer(profile(c),Visuals.light(c)||((android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice());}
    static String engineName(int n){return n==MEDIA3?"Media3":L10n.text(n==SYSTEM?"System player":"Automatic");}
    static String profileName(int n){return L10n.text(n==FAST?"Fast":n==STABLE?"Stable":"Balanced");}
    static String profileDetail(int n){return L10n.text(n==FAST?"Quicker start · Less protection from network variation":n==STABLE?"More buffering · More tolerance for network variation":"Balanced startup and buffering");}
    private static SharedPreferences hints(Context c){return VelaWorkspace.prefs(c,"vela-playback-hints");}
    private static String key(Context c,LiveTvData.Channel channel){return LiveTvData.hash(LiveTvData.source(c)+"|"+channel.id);}
    private static JSONObject document(Context c){try{String raw=hints(c).getString("items","{}");return raw.length()<=16384?new JSONObject(raw):new JSONObject();}catch(JSONException|ClassCastException invalid){return new JSONObject();}}
    private static JSONObject hint(Context c,LiveTvData.Channel channel){JSONObject value=document(c).optJSONObject(key(c,channel));long now=System.currentTimeMillis(),at=value==null?0:value.optLong("at");return at>0&&at<=now&&now-at<HINT_TTL?value:null;}
    static int chooseEngine(Context c,LiveTvData.Channel channel){int mode=engine(c);if(mode!=AUTO)return mode;JSONObject hint=hint(c,channel);return hint!=null&&hint.optInt("engine")==SYSTEM?SYSTEM:MEDIA3;}
    static List<String> order(Context c,LiveTvData.Channel selected,LiveTvData.Channel variant,List<String> urls){
        if(urls==null||urls.size()<2)return urls;JSONObject hint=hint(c,selected);if(hint==null)return urls;
        String previous=hint.optString("signal"),format=hint.optString("format");List<String> out=new ArrayList<>(urls);
        out.sort(Comparator.comparingInt(u->LiveTvData.hash(u).equals(previous)?0:format(u).equals(format)&&!format.isEmpty()?1:2));return out;
    }
    static String format(String url){try{String path=new java.net.URI(url).getPath().toLowerCase(Locale.ROOT);return path.endsWith(".m3u8")?"hls":path.endsWith(".ts")?"ts":"";}catch(Exception invalid){return "";}}
    static void success(Context c,LiveTvData.Channel channel,String url,int engine){
        try{JSONObject values=document(c);long now=System.currentTimeMillis();List<String> remove=new ArrayList<>();for(Iterator<String> keys=values.keys();keys.hasNext();){String k=keys.next();JSONObject v=values.optJSONObject(k);long at=v==null?0:v.optLong("at");if(at<=0||at>now||now-at>=HINT_TTL)remove.add(k);}for(String k:remove)values.remove(k);
            String wanted=key(c,channel);while(values.length()>=MAX_HINTS&&!values.has(wanted)){String oldest=null;long at=Long.MAX_VALUE;for(Iterator<String> keys=values.keys();keys.hasNext();){String k=keys.next();long t=values.getJSONObject(k).optLong("at");if(t<at){at=t;oldest=k;}}if(oldest==null)break;values.remove(oldest);}
            values.put(wanted,new JSONObject().put("signal",LiveTvData.hash(url)).put("format",format(url)).put("engine",engine).put("at",now));hints(c).edit().putString("items",values.toString()).apply();
        }catch(JSONException ignored){}
    }
}
