package tv.vela.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import java.util.*;

/** Reads only the searchable cards that YouTube itself publishes to Android TV. */
final class TvSuggestions {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final String YOUTUBE = "com.google.android.youtube.tv";
    static List<PlexClient.Film> youtube(Context context) {
        List<PlexClient.Film> items=new ArrayList<>();
        if(context.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED)return items;
        Map<Long,String> channels=new HashMap<>();
        try(Cursor cursor=context.getContentResolver().query(uri(TvContract.Channels.CONTENT_URI),new String[]{"_id","display_name"},null,null,null)) {
            if(cursor!=null)while(cursor.moveToNext())channels.put(cursor.getLong(0),cursor.getString(1));
        }catch(RuntimeException ignored){}
        try(Cursor cursor=context.getContentResolver().query(uri(TvContract.PreviewPrograms.CONTENT_URI),
                new String[]{"_id","title","short_description","poster_art_uri","intent_uri","channel_id"},null,null,null)) {
            if(cursor!=null)while(cursor.moveToNext() && items.size()<24) {
                PlexClient.Film film=new PlexClient.Film();
                film.key="youtube:"+cursor.getLong(0);film.title=cursor.getString(1);film.summary=cursor.getString(2);
                film.thumb=cursor.getString(3);film.art=film.thumb;film.intent=cursor.getString(4);
                film.section=channels.getOrDefault(cursor.getLong(5),"Desde YouTube");
                film.provider="YouTube";film.packageName=YOUTUBE;film.tvSource=true;film.landscape=true;film.type="video";film.meta="YouTube · "+film.section;
                if(film.title!=null && film.thumb!=null && film.intent!=null)items.add(film);
            }
        }catch(RuntimeException ignored){}
        return items;
    }
    private static Uri uri(Uri base){return base.buildUpon().appendQueryParameter("package",YOUTUBE).build();}
}
