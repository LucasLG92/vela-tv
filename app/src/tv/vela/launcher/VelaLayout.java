package tv.vela.launcher;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Small per-profile layout document. No images, device URLs or account data. */
final class VelaLayout {
    static final List<String> ROWS=Arrays.asList("resume","recent","library","inputs","tv","apps");
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-layout");}
    static JSONObject read(Context c){try{return validate(new JSONObject(prefs(c).getString("document","{}")));}catch(Exception bad){return new JSONObject();}}
    static JSONObject validate(JSONObject j)throws IOException,JSONException{
        for(Iterator<String> it=j.keys();it.hasNext();)if(!Arrays.asList("order","hidden","sizes","folders","appsOrder").contains(it.next()))throw new IOException("Unknown layout field");
        if(j.toString().length()>64*1024)throw new IOException("Layout size limit");JSONArray appOrder=j.optJSONArray("appsOrder");if(j.has("appsOrder")&&appOrder==null||appOrder!=null&&appOrder.length()>256)throw new IOException("App order limit");Set<String> ordered=new HashSet<>();for(int i=0;appOrder!=null&&i<appOrder.length();i++){String pkg=appOrder.getString(i);if(pkg.length()>256||!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||!ordered.add(pkg))throw new IOException("Invalid app order");}
        for(String field:Arrays.asList("order","hidden")){JSONArray a=j.optJSONArray(field);if(j.has(field)&&a==null||a!=null&&a.length()>6)throw new IOException("Invalid rows");Set<String> seen=new HashSet<>();for(int i=0;a!=null&&i<a.length();i++)if(!ROWS.contains(a.getString(i))||!seen.add(a.getString(i)))throw new IOException("Invalid row");}
        JSONObject sizes=j.optJSONObject("sizes");if(j.has("sizes")&&sizes==null)throw new IOException("Invalid sizes");if(sizes!=null)for(Iterator<String> it=sizes.keys();it.hasNext();){String key=it.next();Object n=sizes.get(key);if(!ROWS.contains(key)||!(n instanceof Integer)||(Integer)n<0||(Integer)n>2)throw new IOException("Invalid row size");}
        JSONArray folders=j.optJSONArray("folders");if(j.has("folders")&&folders==null||folders!=null&&folders.length()>12)throw new IOException("Folder limit");Set<String> names=new HashSet<>();
        for(int i=0;folders!=null&&i<folders.length();i++){JSONObject f=folders.getJSONObject(i);String name=f.getString("name");if(f.length()!=2||name.isEmpty()||name.length()>40||!name.equals(VelaWorkspace.clean(name,40))||name.contains("://")||!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Invalid folder");JSONArray apps=f.getJSONArray("apps");if(apps.length()>64)throw new IOException("Folder limit");Set<String> seen=new HashSet<>();for(int k=0;k<apps.length();k++){String app=apps.getString(k);if(!app.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||!seen.add(app))throw new IOException("Invalid app");}}
        return j;
    }
    static void save(Context c,JSONObject j)throws Exception{validate(j);if(!prefs(c).edit().putString("document",j.toString()).commit())throw new IOException("Layout write failed");}
    static List<String> order(Context c){List<String> out=new ArrayList<>();JSONArray a=read(c).optJSONArray("order");for(int i=0;a!=null&&i<a.length();i++)out.add(a.optString(i));for(String key:ROWS)if(!out.contains(key))out.add(key);return out;}
    static boolean visible(Context c,String key){JSONArray a=read(c).optJSONArray("hidden");for(int i=0;a!=null&&i<a.length();i++)if(key.equals(a.optString(i)))return false;return true;}
    static int size(Context c,String key,int fallback){JSONObject j=read(c).optJSONObject("sizes");int base=key.equals("inputs")||key.equals("apps")?VelaStyle.value(c,"size",fallback):fallback;return j==null?base:j.optInt(key,base);}
    static String label(String row){return row.equals("resume")?L10n.text("Continue watching"):row.equals("recent")?L10n.text("Recently added"):row.equals("inputs")?L10n.text("HDMI inputs"):row.equals("tv")?L10n.text("Television"):row.equals("apps")?L10n.text("Apps"):L10n.text("Library");}
    static JSONArray folders(Context c){JSONArray a=read(c).optJSONArray("folders");return a==null?new JSONArray():a;}
    static void move(Context c,String key,int direction)throws Exception{List<String> a=order(c);int from=a.indexOf(key),to=Math.max(0,Math.min(a.size()-1,from+direction));Collections.swap(a,from,to);save(c,read(c).put("order",new JSONArray(a)));}
    static void toggle(Context c,String key)throws Exception{JSONArray a=new JSONArray();for(String row:ROWS)if(visible(c,row)==row.equals(key))a.put(row);save(c,read(c).put("hidden",a));}
    static void cycle(Context c,String key)throws Exception{JSONObject j=read(c),sizes=j.optJSONObject("sizes");if(sizes==null)sizes=new JSONObject();sizes.put(key,(size(c,key,1)+1)%3);save(c,j.put("sizes",sizes));}
    static void folder(Context c,String name,String app)throws Exception{JSONObject j=read(c);JSONArray a=folders(c);JSONObject target=null;for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("name").equals(name))target=a.getJSONObject(i);if(target==null){target=new JSONObject().put("name",VelaWorkspace.clean(name,40)).put("apps",new JSONArray());a.put(target);}JSONArray next=new JSONArray(),old=target.getJSONArray("apps");boolean exists=false;for(int i=0;i<old.length();i++){String key=old.getString(i);if(key.equals(app))exists=true;else next.put(key);}if(!app.isEmpty()&&!exists)next.put(app);target.put("apps",next);save(c,j.put("folders",a));}
    static List<String> apps(Context c){List<String> out=new ArrayList<>();JSONArray a=read(c).optJSONArray("appsOrder");for(int i=0;a!=null&&i<a.length();i++)out.add(a.optString(i));return out;}
    static int rank(Context c,String pkg){int index=apps(c).indexOf(pkg);return index<0?10000:index;}
    static void moveApp(Context c,String pkg,int direction)throws Exception{if(Math.abs(direction)!=1)throw new IOException();List<String> order=apps(c);Set<String> seen=new HashSet<>(order);android.content.pm.PackageManager pm=c.getPackageManager();for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})for(android.content.pm.ResolveInfo item:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))if(item.activityInfo!=null&&seen.add(item.activityInfo.packageName))order.add(item.activityInfo.packageName);if(order.size()>256||!order.contains(pkg))throw new IOException();int at=order.indexOf(pkg);Collections.swap(order,at,Math.max(0,Math.min(order.size()-1,at+direction)));save(c,read(c).put("appsOrder",new JSONArray(order)));}
    static void deleteFolder(Context c,int index)throws Exception{JSONObject j=read(c);JSONArray a=folders(c);a.remove(index);save(c,j.put("folders",a));}
}
