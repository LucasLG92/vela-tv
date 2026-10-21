package tv.vela.launcher;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import java.util.*;

/** Read-only Watch Next metadata. No account scraping and no invented playback times. */
final class ContinueWatching {
    static final int LIMIT=12;
    static final String[] COLUMNS={"_id","package_name","title","short_description","poster_art_uri","thumbnail_uri","intent_uri","duration_millis","last_playback_position_millis","last_engagement_time_utc_millis","watch_next_type","browsable","season_display_number","episode_display_number","type","poster_art_aspect_ratio","poster_thumbnail_aspect_ratio"};
    private ContinueWatching(){}
    static List<PlexClient.Film> read(Context context,boolean plexConnected){
        List<PlexClient.Film> result=new ArrayList<>();
        if(context.checkSelfPermission(TvSuggestions.PERMISSION)!=PackageManager.PERMISSION_GRANTED)return result;
        try(Cursor cursor=context.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,COLUMNS,null,null,"last_engagement_time_utc_millis DESC")){
            if(cursor==null)return result;Map<String,String> names=new HashMap<>();Set<String> seen=new HashSet<>();int scanned=0;
            while(cursor.moveToNext()&&scanned++<240){
                PlexClient.Film film=parse(cursor);if(film==null||film.packageName.equals(context.getPackageName()))continue;
                String pkg=film.packageName;if(plexConnected&&pkg.equals("com.plexapp.android"))continue;
                if(!names.containsKey(pkg))names.put(pkg,SidebarApps.launchable(context,pkg)?SidebarApps.label(context,pkg):"");
                if(names.get(pkg).isEmpty()||!seen.add(pkg+"|"+film.intent))continue;
                film.provider=names.get(pkg);film.meta=film.provider+(film.episodeLabel.isEmpty()?"":" · "+film.episodeLabel);result.add(film);
                if(result.size()==48)break;
            }
        }catch(RuntimeException unavailable){return Collections.emptyList();}
        result.sort((a,b)->Long.compare(b.lastPlayedMs,a.lastPlayedMs));
        return new ArrayList<>(result.subList(0,Math.min(LIMIT,result.size())));
    }
    static PlexClient.Film parse(Cursor cursor){
        if(cursor.getInt(10)!=TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE||cursor.getInt(11)!=1)return null;
        PlexClient.Film film=new PlexClient.Film();film.packageName=value(cursor,1);film.title=value(cursor,2);film.summary=value(cursor,3);film.thumb=value(cursor,4);boolean thumbnail=film.thumb.isEmpty();if(thumbnail)film.thumb=value(cursor,5);film.art=film.thumb;film.intent=value(cursor,6);
        if(!film.packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||film.title.isEmpty()||!imageUri(film.thumb)||film.intent.isEmpty())return null;
        film.durationMs=cursor.isNull(7)?0:Math.max(0,cursor.getLong(7));film.positionMs=cursor.isNull(8)?-1:Math.max(-1,cursor.getLong(8));film.lastPlayedMs=cursor.isNull(9)?0:Math.max(0,cursor.getLong(9));
        if(film.durationMs>0&&film.positionMs>=film.durationMs)return null;
        if(film.durationMs>0&&film.positionMs>=0)film.progress=Math.min(1f,(float)film.positionMs/film.durationMs);
        String season=value(cursor,12),episode=value(cursor,13);if(!season.isEmpty()&&!episode.isEmpty())film.episodeLabel="T"+season+" · E"+episode;
        film.key="watchnext:"+film.packageName+":"+cursor.getLong(0);film.type="video";film.tvSource=true;film.continuing=true;
        film.thumbAspect=aspect(cursor,thumbnail?16:15);
        if(film.thumbAspect==0)film.thumbAspect=film.packageName.equals(TvSuggestions.YOUTUBE)||cursor.getInt(14)==TvContract.WatchNextPrograms.TYPE_CLIP?16f/9:2f/3;
        film.landscape=film.thumbAspect>=1;
        film.section=Ui.tr("Continuar assistindo","Continue watching","Seguir viendo");return film;
    }
    private static float aspect(Cursor cursor,int column){
        if(cursor.isNull(column))return 0;
        switch(cursor.getInt(column)){
            case TvContract.WatchNextPrograms.ASPECT_RATIO_16_9:return 16f/9;
            case TvContract.WatchNextPrograms.ASPECT_RATIO_3_2:return 3f/2;
            case TvContract.WatchNextPrograms.ASPECT_RATIO_4_3:return 4f/3;
            case TvContract.WatchNextPrograms.ASPECT_RATIO_1_1:return 1;
            case TvContract.WatchNextPrograms.ASPECT_RATIO_2_3:return 2f/3;
            default:return 0;
        }
    }
    private static String value(Cursor cursor,int index){return cursor.isNull(index)?"":cursor.getString(index);}
    static boolean imageUri(String path){try{Uri uri=Uri.parse(path);return "content".equals(uri.getScheme())||("https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null);}catch(RuntimeException invalid){return false;}}
    static List<PlexClient.Film> merge(List<PlexClient.Film> plex,List<PlexClient.Film> shared,boolean plexConnected){
        List<PlexClient.Film> result=new ArrayList<>(plex);Set<String> seen=new HashSet<>();for(PlexClient.Film film:plex)seen.add(film.packageName+"|"+film.key);
        for(PlexClient.Film film:shared){if(plexConnected&&film.packageName.equals("com.plexapp.android"))continue;if(seen.add(film.packageName+"|"+film.key))result.add(film);}
        return result;
    }
    static String remaining(PlexClient.Film film){
        long ms=film.remainingMs();if(ms<=0)return Ui.tr("Continuar","Resume","Continuar");
        long minutes=1+(ms-1)/60_000;String duration=minutes>60?minutes/60+" h"+(minutes%60==0?"":" "+minutes%60+" min"):minutes+" min";
        return minutes==1?Ui.tr("Falta "+duration,duration+" left","Queda "+duration):Ui.tr("Faltam "+duration,duration+" left","Quedan "+duration);
    }
    static Intent launch(Context context,PlexClient.Film film){
        try{
            Intent supplied=Intent.parseUri(film.intent,Intent.URI_INTENT_SCHEME);Uri data=supplied.getData();
            if(data==null||data.getScheme()==null||Arrays.asList("file","content","javascript","data","intent").contains(data.getScheme().toLowerCase(Locale.ROOT)))return null;
            Intent safe=new Intent(Intent.ACTION_VIEW,data).setPackage(film.packageName);
            return safe.resolveActivity(context.getPackageManager())==null?null:safe;
        }catch(Exception invalid){return null;}
    }
}
