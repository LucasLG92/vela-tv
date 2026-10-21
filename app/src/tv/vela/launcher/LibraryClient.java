package tv.vela.launcher;

import android.content.*;
import java.io.*;
import java.util.*;

/** One active personal library; both authorizations remain independent. No extra worker or image cache. */
final class LibraryClient {
    private final Context context;
    private final PlexClient plex;
    private JellyfinClient jellyfin;
    LibraryClient(Context c){this(c,new PlexClient(c));}
    LibraryClient(Context c,PlexClient plex){context=c.getApplicationContext();this.plex=plex;}
    static void activate(Context c,String provider){if(!provider.equals("Plex")&&!provider.equals("Jellyfin"))throw new IllegalArgumentException();c.getSharedPreferences("library",0).edit().putString("provider",provider).commit();}
    boolean isJellyfin(){boolean jf=!JellyfinClient.prefs(context).getString("token","").isEmpty();return jf&&(context.getSharedPreferences("library",0).getString("provider","Plex").equals("Jellyfin")||!plex.connected());}
    private JellyfinClient jf(){if(jellyfin==null)jellyfin=new JellyfinClient(context);return jellyfin;}
    boolean connected(){return isJellyfin()?jf().connected():plex.connected();}
    String provider(){return isJellyfin()?"Jellyfin":"Plex";}
    String name(){return isJellyfin()?jf().name():plex.name();}
    String packageName(){return isJellyfin()?JellyfinClient.PACKAGE:"com.plexapp.android";}
    String identity(){return provider()+"|"+(isJellyfin()?jf().identity():plex.identity());}
    String profileKey(){return isJellyfin()?identity():PlexClient.profileKey(plex.prefs.getString("accountToken",""));}
    Class<?> linkActivity(){return isJellyfin()?JellyfinLinkActivity.class:PlexLinkActivity.class;}
    List<PlexClient.Film> cached(){return isJellyfin()?jf().cached():plex.cached();}
    List<PlexClient.Film> library()throws Exception{return isJellyfin()?jf().library():plex.library();}
    List<PlexClient.Section> sections()throws Exception{return isJellyfin()?jf().sections():plex.sections();}
    PlexClient.LibraryPage page(PlexClient.Section section,int offset)throws Exception{return isJellyfin()?jf().page(section,offset):plex.page(section,offset);}
    byte[] image(String path,int width,int height)throws IOException{return image(path,width,height,false);}
    byte[] image(String path,int width,int height,boolean transparent)throws IOException{return isJellyfin()?jf().image(path,width,height,transparent):plex.image(path,width,height,transparent);}
    byte[] avatar()throws Exception{return isJellyfin()?jf().avatar():plex.avatarImage(plex.prefs.getString("accountToken",""));}
    boolean canMarkWatched(PlexClient.Film film){return film!=null&&film.continuing&&!film.tvSource&&("movie".equals(film.type)||"episode".equals(film.type))&&connected()&&film.packageName.equals(packageName());}
    void markWatched(PlexClient.Film film,String expectedIdentity)throws Exception{
        if(!canMarkWatched(film)||!expectedIdentity.equals(identity()))throw new IOException("Library account changed");
        if(film.packageName.equals(JellyfinClient.PACKAGE))jf().markWatched(film.key,expectedIdentity.substring("Jellyfin|".length()));else plex.markWatched(film.key,expectedIdentity.substring("Plex|".length()));
    }
    Intent open(PlexClient.Film film){
        if(film.packageName.equals(JellyfinClient.PACKAGE)){String id=JellyfinClient.guid(film.key);Intent intent=HomeApps.launchIntent(context.getPackageManager(),JellyfinClient.PACKAGE);return id.isEmpty()||intent==null?null:intent.putExtra("ItemId",id).putExtra("HideSplash",true);}
        return new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(plex.openUri(film))).setPackage("com.plexapp.android");
    }
}
