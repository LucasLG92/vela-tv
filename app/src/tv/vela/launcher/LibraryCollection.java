package tv.vela.launcher;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Account-bound collection references; no artwork, sessions or provider URLs in preferences. */
final class LibraryCollection {
    static final int MAX_PINNED=6,HOME_ITEMS=8,PAGE=12;
    final String provider,key,title,account;
    LibraryCollection(String provider,String key,String title,String account)throws IOException{if(!Arrays.asList("Plex","Jellyfin").contains(provider)||!(provider.equals("Plex")?key.matches("[0-9]{1,20}"):key.matches("[a-f0-9]{32}"))||account.isEmpty()||account.length()>128)throw new IOException("Invalid collection");this.provider=provider;this.key=key;String clean=title==null?"":title.replaceAll("[\\p{Cntrl}]"," ").trim();this.title=clean.substring(0,Math.min(160,clean.length()));this.account=account;}
    String identity(){return provider+"|"+account+"|"+key;}
    JSONObject json()throws JSONException{return new JSONObject().put("provider",provider).put("key",key).put("title",title).put("account",account);}
    static LibraryCollection read(JSONObject j)throws Exception{return new LibraryCollection(j.getString("provider"),j.getString("key"),j.getString("title"),j.getString("account"));}
    static List<LibraryCollection> pinned(Context c){List<LibraryCollection> out=new ArrayList<>();try{JSONArray list=new JSONArray(VelaAccountScope.prefs(c,"vela-collections").getString("pins","[]"));if(list.length()>MAX_PINNED)return out;for(int i=0;i<list.length();i++)out.add(read(list.getJSONObject(i)));}catch(Exception bad){out.clear();}return out;}
    static void toggle(Context c,LibraryCollection item)throws Exception{List<LibraryCollection> list=pinned(c);boolean removed=list.removeIf(p->p.identity().equals(item.identity()));if(!removed){if(list.size()>=MAX_PINNED)throw new IOException("Collection limit");list.add(item);}JSONArray saved=new JSONArray();for(LibraryCollection p:list)saved.put(p.json());if(!VelaAccountScope.prefs(c,"vela-collections").edit().putString("pins",saved.toString()).commit())throw new IOException("Could not save collection");}
}
