package tv.vela.launcher;

import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Small per-profile layout document. No images, device URLs or account data. */
final class VelaLayout {
    static final String LIBRARY="library",TELEVISION="television",DEVICES="devices";
    static final String PLEX="com.plexapp.android",JELLYFIN="org.jellyfin.androidtv";
    static final class Service {final String key,title,detail;final boolean selected,linked;Service(String k,String t,String d,boolean s){this(k,t,d,s,s);}Service(String k,String t,String d,boolean s,boolean l){key=k;title=t;detail=d;selected=s;linked=l;}}
    static final List<String> EXTRA_ROWS=Arrays.asList("recent_movies","recent_series","unwatched","top_rated");
    static final List<String> ROWS=Arrays.asList("resume","recent","recent_movies","recent_series","unwatched","top_rated","library","inputs","tv","apps");
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-layout");}
    private static String document(Context c){
        try{return prefs(c).getString("document","{}");}catch(ClassCastException invalid){return "{}";}
    }
    static JSONObject read(Context c){return parse(document(c));}
    // Editors own the mutable object returned by read(). Only scalar and copied values escape this snapshot.
    private static final class Snapshot{
        final String document;
        final JSONObject layout;
        Snapshot(String document){this.document=document;layout=parse(document);}
    }
    private static volatile Snapshot lastLayout;
    private static JSONObject settings(Context c){
        String raw=document(c);
        Snapshot cached=lastLayout;
        if(cached==null||!raw.equals(cached.document)){cached=new Snapshot(raw);lastLayout=cached;}
        return cached.layout;
    }
    private static JSONObject parse(String raw){
        JSONObject j;try{j=validate(new JSONObject(raw));}catch(Exception bad){j=new JSONObject();}
        // Old layouts keep their exact visible rows; extra feeds require an explicit choice.
        if(!j.optBoolean("extraRowsConfigured",false))try{JSONArray hidden=j.optJSONArray("hidden");if(hidden==null)hidden=new JSONArray();for(String key:EXTRA_ROWS)if(!contains(hidden,key))hidden.put(key);j.put("hidden",hidden).put("extraRowsConfigured",true);}catch(JSONException impossible){}
        return j;
    }
    private static boolean contains(JSONArray a,String key){for(int i=0;a!=null&&i<a.length();i++)if(key.equals(a.optString(i)))return true;return false;}
    static String source(JSONObject j){return j.optString("librarySource","current");}
    static String source(Context c){return source(settings(c));}
    static String sourceLabel(String source){return source.equals("plex")?"Plex":source.equals("jellyfin")?"Jellyfin":source.equals("both")?"Plex + Jellyfin":L10n.text("Current library");}
    static boolean appResume(Context c){JSONObject j=settings(c);JSONArray a=j.optJSONArray("resumeServices");if(a==null)return j.optBoolean("appResume",true);for(int i=0;i<a.length();i++)if(!Arrays.asList(PLEX,JELLYFIN).contains(a.optString(i)))return true;return false;}
    static boolean service(JSONObject j,String key,boolean fallback){JSONArray a=j.optJSONArray("resumeServices");return a==null?fallback:contains(a,key);}
    static boolean service(Context c,String key,boolean fallback){return service(settings(c),key,fallback);}
    static String title(Context c,String key){return title(settings(c),key);}
    static String title(JSONObject j,String key){JSONObject titles=j.optJSONObject("titles");return titles==null?label(key):titles.optString(key,label(key));}
    static String rowSource(Context c,String key){JSONObject sources=settings(c).optJSONObject("sources");String choice=sources==null?"home":sources.optString(key,"home");return choice.equals("home")?source(c):choice;}
    static final List<String> PRESENTATIONS=Arrays.asList("night","cinema","compact","rows");
    static String hero(JSONObject layout){return layout.optString("hero","night");}
    static String hero(Context c){return hero(settings(c));}
    static String presentationName(String key){
        switch(key){
            case "night": return L10n.text("Night cinema");
            case "compact": return L10n.text("Compact");
            case "rows": return L10n.text("Rows only");
            default: return L10n.text("Cinema");
        }
    }
    static boolean clearFocus(Context c){return settings(c).optString("focus","soft").equals("clear");}
    static JSONObject validate(JSONObject j)throws IOException,JSONException{
        for(Iterator<String> it=j.keys();it.hasNext();)if(!Arrays.asList("order","hidden","sizes","folders","appsOrder","extraRowsConfigured","librarySource","appResume","resumeServices","titles","sources","hero","focus","start").contains(it.next()))throw new IOException("Unknown layout field");
        if(j.has("hero")&&(!(j.get("hero") instanceof String)||!PRESENTATIONS.contains(j.getString("hero")))||j.has("focus")&&(!(j.get("focus") instanceof String)||!Arrays.asList("soft","clear").contains(j.getString("focus"))))throw new IOException("Invalid home appearance");
        if(j.has("start")&&(!(j.get("start") instanceof String)||!Arrays.asList(LIBRARY,TELEVISION,DEVICES).contains(j.getString("start"))))throw new IOException("Invalid Home layout");
        JSONArray services=j.optJSONArray("resumeServices");if(j.has("resumeServices")&&services==null||services!=null&&services.length()>128)throw new IOException("Invalid resume services");Set<String> serviceKeys=new HashSet<>();for(int i=0;services!=null&&i<services.length();i++){String key=services.getString(i);if(key.length()>256||!key.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||!serviceKeys.add(key))throw new IOException("Invalid service");}
        for(String field:Arrays.asList("titles","sources")){JSONObject values=j.optJSONObject(field);if(j.has(field)&&values==null)throw new IOException("Invalid section preferences");if(values!=null)for(Iterator<String> it=values.keys();it.hasNext();){String key=it.next();if(!ROWS.contains(key)||!(values.get(key) instanceof String))throw new IOException("Invalid section preference");String value=values.getString(key);if(field.equals("titles")?(value.isEmpty()||value.length()>40||!value.equals(VelaWorkspace.clean(value,40))||value.contains("://")):!Arrays.asList("home","current","plex","jellyfin","both").contains(value))throw new IOException("Invalid section preference");}}
        if(j.has("librarySource")&&(!(j.get("librarySource") instanceof String)||!Arrays.asList("current","plex","jellyfin","both").contains(j.getString("librarySource"))))throw new IOException("Invalid home library");
        for(String key:Arrays.asList("extraRowsConfigured","appResume"))if(j.has(key)&&!(j.get(key) instanceof Boolean))throw new IOException("Invalid home choice");
        if(j.toString().length()>64*1024)throw new IOException("Layout size limit");JSONArray appOrder=j.optJSONArray("appsOrder");if(j.has("appsOrder")&&appOrder==null||appOrder!=null&&appOrder.length()>256)throw new IOException("App order limit");Set<String> ordered=new HashSet<>();for(int i=0;appOrder!=null&&i<appOrder.length();i++){String pkg=appOrder.getString(i);if(pkg.length()>256||!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||!ordered.add(pkg))throw new IOException("Invalid app order");}
        for(String field:Arrays.asList("order","hidden")){JSONArray a=j.optJSONArray(field);if(j.has(field)&&a==null||a!=null&&a.length()>ROWS.size())throw new IOException("Invalid rows");Set<String> seen=new HashSet<>();for(int i=0;a!=null&&i<a.length();i++)if(!ROWS.contains(a.getString(i))||!seen.add(a.getString(i)))throw new IOException("Invalid row");}
        JSONObject sizes=j.optJSONObject("sizes");if(j.has("sizes")&&sizes==null)throw new IOException("Invalid sizes");if(sizes!=null)for(Iterator<String> it=sizes.keys();it.hasNext();){String key=it.next();Object n=sizes.get(key);if(!ROWS.contains(key)||!(n instanceof Integer)||(Integer)n<0||(Integer)n>2)throw new IOException("Invalid row size");}
        JSONArray folders=j.optJSONArray("folders");if(j.has("folders")&&folders==null||folders!=null&&folders.length()>12)throw new IOException("Folder limit");Set<String> names=new HashSet<>();
        for(int i=0;folders!=null&&i<folders.length();i++){JSONObject f=folders.getJSONObject(i);String name=f.getString("name");if(f.length()!=2||name.isEmpty()||name.length()>40||!name.equals(VelaWorkspace.clean(name,40))||name.contains("://")||!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Invalid folder");JSONArray apps=f.getJSONArray("apps");if(apps.length()>64)throw new IOException("Folder limit");Set<String> seen=new HashSet<>();for(int k=0;k<apps.length();k++){String app=apps.getString(k);if(!app.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||!seen.add(app))throw new IOException("Invalid app");}}
        return j;
    }
    static String start(Context c){return settings(c).optString("start");}
    static JSONObject previous(Context c){
        String saved=prefs(c).getString("previous","");
        try{return saved.isEmpty()?null:validate(new JSONObject(saved));}catch(Exception invalid){return null;}
    }
    static JSONObject preset(JSONObject from,String key)throws Exception{
        JSONObject next=new JSONObject(from.toString()).put("start",key);
        if(LIBRARY.equals(key)){
            List<String> featured=Arrays.asList("resume","recent","recent_movies","recent_series","unwatched","top_rated","library");
            List<String> hidden=new ArrayList<>(EXTRA_ROWS);
            for(String row:Arrays.asList("inputs","tv","apps"))if(contains(from.optJSONArray("hidden"),row))hidden.add(row);
            if(!next.has("librarySource"))next.put("librarySource","both");
            next.put("order",new JSONArray(featured)).put("hidden",new JSONArray(hidden))
                .put("hero",hero(from)).put("extraRowsConfigured",true);
        }
        return validate(next);
    }
    static void save(Context c,JSONObject j)throws Exception{save(c,j,false);}
    static void save(Context c,JSONObject j,boolean remember)throws Exception{
        validate(j);
        SharedPreferences.Editor update=prefs(c).edit().putString("document",j.toString());
        if(remember)update.putString("previous",read(c).toString());
        if(!update.commit())throw new IOException("Layout write failed");
    }
    static List<String> order(Context c){return order(settings(c));}
    static List<String> order(JSONObject layout){List<String> out=new ArrayList<>();JSONArray a=layout.optJSONArray("order");for(int i=0;a!=null&&i<a.length();i++)out.add(a.optString(i));for(String key:ROWS)if(!out.contains(key))out.add(key);return out;}
    static boolean visible(Context c,String key){return visible(settings(c),key);}
    static boolean visible(JSONObject layout,String key){return !contains(layout.optJSONArray("hidden"),key);}
    static int size(Context c,String key,int fallback){JSONObject j=settings(c).optJSONObject("sizes");int base=key.equals("inputs")||key.equals("apps")?VelaStyle.value(c,"size",fallback):fallback;return j==null?base:j.optInt(key,base);}
    static String label(String row){return row.equals("recent_movies")?L10n.text("Recently added movies"):row.equals("recent_series")?L10n.text("Recently added series"):row.equals("unwatched")?L10n.text("Unwatched movies"):row.equals("top_rated")?L10n.text("Top-rated movies"):row.equals("resume")?L10n.text("Continue watching"):row.equals("recent")?L10n.text("Recently added"):row.equals("inputs")?L10n.text("HDMI inputs"):row.equals("tv")?L10n.text("Television"):row.equals("apps")?L10n.text("Apps"):L10n.text("Library");}
    static JSONArray folders(Context c){JSONArray a=read(c).optJSONArray("folders");return a==null?new JSONArray():a;}
    static void move(Context c,String key,int direction)throws Exception{List<String> a=order(c);int from=a.indexOf(key),to=Math.max(0,Math.min(a.size()-1,from+direction));Collections.swap(a,from,to);save(c,read(c).put("order",new JSONArray(a)));}
    static void toggle(Context c,String key)throws Exception{JSONArray a=new JSONArray();for(String row:ROWS)if(visible(c,row)==row.equals(key))a.put(row);save(c,read(c).put("hidden",a));}
    static void cycle(Context c,String key)throws Exception{JSONObject j=read(c),sizes=j.optJSONObject("sizes");if(sizes==null)sizes=new JSONObject();sizes.put(key,(size(c,key,1)+1)%3);save(c,j.put("sizes",sizes));}
    static void folder(Context c,String name,String app)throws Exception{JSONObject j=read(c);JSONArray a=folders(c);JSONObject target=null;for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("name").equals(name))target=a.getJSONObject(i);if(target==null){target=new JSONObject().put("name",VelaWorkspace.clean(name,40)).put("apps",new JSONArray());a.put(target);}JSONArray next=new JSONArray(),old=target.getJSONArray("apps");boolean exists=false;for(int i=0;i<old.length();i++){String key=old.getString(i);if(key.equals(app))exists=true;else next.put(key);}if(!app.isEmpty()&&!exists)next.put(app);target.put("apps",next);save(c,j.put("folders",a));}
    static List<String> apps(Context c){List<String> out=new ArrayList<>();JSONArray a=settings(c).optJSONArray("appsOrder");for(int i=0;a!=null&&i<a.length();i++)out.add(a.optString(i));return out;}
    static int rank(Context c,String pkg){int index=apps(c).indexOf(pkg);return index<0?10000:index;}
    static void moveApp(Context c,String pkg,int direction)throws Exception{
        if(direction!=-1&&direction!=1)throw new IOException("Invalid move direction");
        List<String> order=apps(c);
        Set<String> saved=new HashSet<>(order),installed=new HashSet<>();
        PackageManager pm=c.getPackageManager();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            for(ResolveInfo item:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                if(item.activityInfo==null)continue;
                String name=item.activityInfo.packageName;
                if(installed.add(name)&&!saved.contains(name))order.add(name);
            }
        }
        if(order.size()>256||!installed.contains(pkg))throw new IOException("Cannot move app");
        // A restored or uninstalled app keeps its slot, but is absent from the editor.
        int from=order.indexOf(pkg);
        for(int to=from+direction;to>=0&&to<order.size();to+=direction){
            if(installed.contains(order.get(to))){
                Collections.swap(order,from,to);
                save(c,read(c).put("appsOrder",new JSONArray(order)));
                return;
            }
        }
    }
    static void deleteFolder(Context c,int index)throws Exception{JSONObject j=read(c);JSONArray a=folders(c);a.remove(index);save(c,j.put("folders",a));}
}
