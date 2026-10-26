package tv.vela.launcher;

import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Profile-bound clients share the existing worker and artwork cache. Home remains bounded to 24 cards. */
final class LibraryClient {
    private final Context context;private final PlexClient plex;private final JellyfinClient jellyfin;
    LibraryClient(Context c){this(c,new PlexClient(c));}
    LibraryClient(Context c,PlexClient p){context=VelaAccountScope.freeze(c);plex=p;jellyfin=new JellyfinClient(context);}
    static void activate(Context c,String provider){if(!Arrays.asList("Plex","Jellyfin","Both").contains(provider))throw new IllegalArgumentException();VelaAccountScope.prefs(c,"library").edit().putString("provider",provider).commit();}
    private String chosen(){return VelaAccountScope.prefs(context,"library").getString("provider","Plex");}
    boolean both(){return chosen().equals("Both")&&plex.connected()&&jellyfin.connected();}
    boolean isJellyfin(){return jellyfin.connected()&&(chosen().equals("Jellyfin")||!plex.connected());}
    boolean connected(){return both()||isJellyfin()?jellyfin.connected():plex.connected();}
    String provider(){return both()?"Plex + Jellyfin":isJellyfin()?"Jellyfin":"Plex";}
    String name(){return both()?plex.name()+" · "+jellyfin.name():isJellyfin()?jellyfin.name():plex.name();}
    String packageName(){return isJellyfin()?JellyfinClient.PACKAGE:"com.plexapp.android";}
    String identity(){return both()?"Both|"+plex.identity()+"|"+jellyfin.identity():provider()+"|"+(isJellyfin()?jellyfin.identity():plex.identity());}
    String profileKey(){return both()?identity():isJellyfin()?identity():PlexClient.profileKey(plex.prefs.getString("accountToken",""));}
    Class<?> linkActivity(){return isJellyfin()?JellyfinLinkActivity.class:PlexLinkActivity.class;}
    static boolean jf(PlexClient.Film f){return f.packageName.equals(JellyfinClient.PACKAGE);}
    private List<PlexClient.Film> decorate(List<PlexClient.Film> list){for(PlexClient.Film f:list){String prefix=jf(f)?"vela-jellyfin:":"vela-plex:";f.thumb=tag(prefix,f.thumb);f.art=tag(prefix,f.art);f.logo=tag(prefix,f.logo);if(f.alternative!=null){PlexClient.Film other=f.alternative;String alt=jf(other)?"vela-jellyfin:":"vela-plex:";other.thumb=tag(alt,other.thumb);other.art=tag(alt,other.art);other.logo=tag(alt,other.logo);}}return list;}
    private static String tag(String prefix,String path){return path==null||path.isEmpty()||path.startsWith("vela-")?path:prefix+path;}
    static List<PlexClient.Film> merge(List<PlexClient.Film> a,List<PlexClient.Film> b){
        List<PlexClient.Film> all=new ArrayList<>(a);all.addAll(b);all.sort((x,y)->{if(x.continuing!=y.continuing)return x.continuing?-1:1;return Long.compare(x.continuing?y.lastPlayedMs:y.addedAtMs,x.continuing?x.lastPlayedMs:x.addedAtMs);});
        List<PlexClient.Film> out=new ArrayList<>();Map<String,PlexClient.Film> seen=new HashMap<>();int resume=0,recent=0;
        for(PlexClient.Film f:all){String id=(f.continuing?"resume|":"recent|")+(f.externalId.isEmpty()?f.packageName+"|"+f.key:f.type+"|"+f.externalId);PlexClient.Film previous=seen.get(id);if(previous!=null){if(!previous.packageName.equals(f.packageName)&&previous.alternative==null)previous.alternative=f;continue;}if(f.continuing?resume>=12:recent>=12)continue;seen.put(id,f);out.add(f);if(f.continuing)resume++;else recent++;}return out;
    }
    List<PlexClient.Film> cached(){if(both())return decorate(merge(plex.cached(),jellyfin.cached()));return decorate(isJellyfin()?jellyfin.cached():plex.cached());}
    List<PlexClient.Film> library()throws Exception{if(!both())return decorate(isJellyfin()?jellyfin.library():plex.library());List<PlexClient.Film> a,b;Exception failure=null;try{a=plex.library();}catch(Exception error){if(Thread.currentThread().isInterrupted())throw error;failure=error;a=plex.cached();}try{b=jellyfin.library();}catch(Exception error){if(Thread.currentThread().isInterrupted())throw error;b=jellyfin.cached();if(failure!=null&&a.isEmpty()&&b.isEmpty())throw error;}return decorate(merge(a,b));}
    List<PlexClient.Section> sections()throws Exception{if(!both())return isJellyfin()?jellyfin.sections():plex.sections();List<PlexClient.Section> out=new ArrayList<>();Exception failure=null;try{for(PlexClient.Section s:plex.sections())out.add(new PlexClient.Section("plex:"+s.key,"Plex · "+s.title,s.type));}catch(Exception e){failure=e;}try{for(PlexClient.Section s:jellyfin.sections())out.add(new PlexClient.Section("jellyfin:"+s.key,"Jellyfin · "+s.title,s.type));}catch(Exception e){if(failure!=null)throw e;}return out;}
    private boolean sectionJf(PlexClient.Section s){return s.key.startsWith("jellyfin:")||!s.key.startsWith("plex:")&&isJellyfin();}
    private PlexClient.Section plain(PlexClient.Section s){int at=s.key.indexOf(':');return at<0?s:new PlexClient.Section(s.key.substring(at+1),s.title,s.type);}
    PlexClient.LibraryPage page(PlexClient.Section s,int offset)throws Exception{return page(s,offset,new CatalogFilters.Query());}
    PlexClient.LibraryPage page(PlexClient.Section s,int offset,CatalogFilters.Query q)throws Exception{PlexClient.LibraryPage p=sectionJf(s)?jellyfin.page(plain(s),offset,q):plex.page(plain(s),offset,q);return new PlexClient.LibraryPage(decorate(p.films),p.offset,p.total,p.received);}
    CatalogFilters.Options filters(PlexClient.Section s)throws Exception{return sectionJf(s)?jellyfin.filters(plain(s)):plex.filters(plain(s));}
    CatalogFilters.Choices choices(PlexClient.Section s,CatalogFilters.Facet f,int offset,String search)throws Exception{return sectionJf(s)?jellyfin.choices(plain(s),f,offset,search):plex.choices(plain(s),f,offset,search);}
    byte[] image(String path,int w,int h)throws IOException{return image(path,w,h,false);}
    byte[] image(String path,int w,int h,boolean transparent)throws IOException{if(path.startsWith("vela-jellyfin:"))return jellyfin.image(path.substring(14),w,h,transparent);if(path.startsWith("vela-plex:"))return plex.image(path.substring(10),w,h,transparent);return isJellyfin()?jellyfin.image(path,w,h,transparent):plex.image(path,w,h,transparent);}
    byte[] avatar()throws Exception{return isJellyfin()?jellyfin.avatar():plex.avatarImage(plex.prefs.getString("accountToken",""));}
    boolean canMarkWatched(PlexClient.Film f){return f!=null&&f.continuing&&!f.tvSource&&("movie".equals(f.type)||"episode".equals(f.type))&&(jf(f)?jellyfin.connected():f.packageName.equals("com.plexapp.android")&&plex.connected());}
    void markWatched(PlexClient.Film f,String expected)throws Exception{if(!canMarkWatched(f)||!expected.equals(identity()))throw new IOException("Library account changed");if(jf(f))jellyfin.markWatched(f.key,jellyfin.identity());else plex.markWatched(f.key,plex.identity());}
    JSONObject reference(PlexClient.Film f)throws Exception{if(f.tvSource||!Arrays.asList("com.plexapp.android",JellyfinClient.PACKAGE).contains(f.packageName))return null;String account=PlexClient.profileKey(jf(f)?jellyfin.identity():plex.identity());return new JSONObject().put("id","media:"+PlexClient.profileKey(f.packageName+"|"+account+"|"+f.key)).put("kind","media").put("provider",jf(f)?"Jellyfin":"Plex").put("key",f.key).put("account",account).put("title",VelaWorkspace.clean(f.title,320));}
    PlexClient.Film resolve(JSONObject ref)throws Exception{boolean jf=ref.optString("provider").equals("Jellyfin");String expected=PlexClient.profileKey(jf?jellyfin.identity():plex.identity());if(!ref.optString("account").equals(expected))return null;PlexClient.Film f=jf?jellyfin.item(ref.optString("key")):plex.item(ref.optString("key"));if(f!=null)decorate(Arrays.asList(f));return f;}
    Intent open(PlexClient.Film f){if(jf(f)){String id=JellyfinClient.guid(f.key);Intent i=HomeApps.launchIntent(context.getPackageManager(),JellyfinClient.PACKAGE);return id.isEmpty()||i==null?null:i.putExtra("ItemId",id).putExtra("HideSplash",true);}return new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(plex.openUri(f))).setPackage("com.plexapp.android");}
}
