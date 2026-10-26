package tv.vela.launcher;

import android.content.*;
import org.json.*;
import java.util.*;

/** Small source-scoped identifiers only. No catalog, artwork, account or playback URLs. */
final class TvPersonalization {
    static final int MAX_HIDDEN=256,MAX_GROUPS=512,MAX_ORDER=128,MAX_RECENT=12;
    static final class Item {
        final String key,label;final boolean hidden;
        Item(String key,String label){this(key,label,false);}
        Item(String key,String label,boolean hidden){this.key=key;this.label=label;this.hidden=hidden;}
    }
    private final SharedPreferences prefs;
    TvPersonalization(Context c,String source){prefs=VelaWorkspace.channelPrefs(c,source);}
    private List<Item> read(String name,int limit){
        List<Item> out=new ArrayList<>();String raw=prefs.getString(name,"");if(raw.length()>192*1024)return out;
        try{JSONArray a=new JSONArray(raw);Set<String> seen=new HashSet<>();for(int i=0;i<Math.min(a.length(),limit);i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;String k=j.optString("k"),n=j.optString("n");if(!k.isEmpty()&&k.length()<=256&&seen.add(k))out.add(new Item(k,clean(n)));}}catch(JSONException invalid){}return out;
    }
    private static String clean(String value){return value.replaceAll("[\\p{Cntrl}]"," ").substring(0,Math.min(160,value.length()));}
    private void write(String name,List<Item> values,int limit){JSONArray a=new JSONArray();Set<String> seen=new HashSet<>();try{for(Item v:values)if(a.length()<limit&&!v.key.isEmpty()&&v.key.length()<=256&&seen.add(v.key))a.put(new JSONObject().put("k",v.key).put("n",clean(v.label)));}catch(JSONException impossible){throw new IllegalStateException(impossible);}prefs.edit().putString(name,a.toString()).apply();}
    List<Item> hidden(){return read("hidden",MAX_HIDDEN);}
    List<Item> groups(){return read("groups",MAX_GROUPS);}
    List<Item> recent(){return read("recent",MAX_RECENT);}
    List<String> order(){List<String> result=new ArrayList<>();for(Item i:read("order",MAX_ORDER))result.add(i.key);return result;}
    Set<String> hiddenKeys(){Set<String> keys=new HashSet<>();for(Item i:hidden())keys.add(i.key);return keys;}
    boolean hidden(String key){return hiddenKeys().contains(key);}
    boolean groupHidden(String key){for(Item i:groups())if(i.key.equals(key))return true;return false;}
    boolean hide(String key,String label,boolean hide){synchronized(TvPersonalization.class){return toggle("hidden",MAX_HIDDEN,key,label,hide);}}
    boolean hideGroup(String key,String label,boolean hide){synchronized(TvPersonalization.class){return toggle("groups",MAX_GROUPS,key,label,hide);}}
    private boolean toggle(String field,int limit,String key,String label,boolean hide){List<Item> values=read(field,limit);values.removeIf(i->i.key.equals(key));if(hide){if(values.size()>=limit)return false;values.add(new Item(key,label));}write(field,values,limit);return true;}
    void order(List<String> keys){synchronized(TvPersonalization.class){List<Item> values=new ArrayList<>();for(String key:keys)values.add(new Item(key,""));write("order",values,MAX_ORDER);}}
    void played(String key,String label){synchronized(TvPersonalization.class){List<Item> values=recent();if(!values.isEmpty()&&values.get(0).key.equals(key))return;values.removeIf(i->i.key.equals(key));values.add(0,new Item(key,label));write("recent",values,MAX_RECENT);}}
    String previous(String current){for(Item i:recent())if(!i.key.equals(current)&&!hidden(i.key))return i.key;return "";}
    void clearRecent(){prefs.edit().remove("recent").apply();}
    void sort(List<Item> values){Map<String,Integer> rank=new HashMap<>();List<String> order=order();for(int i=0;i<order.size();i++)rank.put(order.get(i),i);values.sort(Comparator.comparingInt(i->rank.getOrDefault(i.key,MAX_ORDER)));}
}
