package tv.vela.launcher;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;
import java.util.*;
import java.util.function.Function;
import org.json.JSONObject;

/** Query an existing EPG database. Only known local channels can appear in results. */
final class VelaGuideSearch {
    /** Apply title matching before pagination so unrelated broadcasts never consume a result slot. */
    static final class Titles {
        private final String text,voice;
        private final Set<String> series;
        final int skip,limit;
        private Titles(String text,String voice,Set<String> series,int skip,int limit){
            this.text=text;this.voice=voice;this.series=series;this.skip=skip;this.limit=limit;
        }
        static Titles page(String query,int page){
            return new Titles(VelaWorkspace.normal(query),VelaVoice.keyText(query),null,page*24,24);
        }
        static Titles followed(Set<String> series){return new Titles("","",series,0,VelaShows.MAX_AIRINGS);}
        boolean matches(String title){
            return series==null?VelaWorkspace.normal(title).contains(text):series.contains(VelaShows.identity(title));
        }
        boolean matchesNative(String title){return matches(title)||!voice.isEmpty()&&VelaVoice.keyText(title).contains(voice);}
    }
    static String like(String q){return "%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    static List<VelaShows.Airing> read(SQLiteDatabase db,Map<String,String> names,String aliasColumn,String query,int page,CancellationSignal signal){
        long now=System.currentTimeMillis()/1000;return readWindow(db,names,aliasColumn,query,page,signal,now,now+48*3600,0);
    }
    static List<VelaShows.Airing> readWindow(SQLiteDatabase db,Map<String,String> names,String aliasColumn,String query,int page,CancellationSignal signal,long from,long to,int genre){
        return readWindow(null,db,names,aliasColumn,query,page,signal,from,to,genre);
    }
    static String uniqueChannel(SQLiteDatabase db,String column,String key){
        try(Cursor rows=db.rawQuery("SELECT channel FROM aliases WHERE "+column+"=? LIMIT 2",new String[]{key})){
            if(!rows.moveToFirst())return "";
            String id=rows.getString(0);
            return rows.moveToNext()?"":id;
        }
    }

    static List<VelaShows.Airing> readWindow(Context context,SQLiteDatabase db,Map<String,String> names,String aliasColumn,String query,int page,CancellationSignal signal,long from,long to,int genre){
        return readWindow(context,db,names,aliasColumn,null,Titles.page(query,page),signal,from,to,genre);
    }

    static List<VelaShows.Airing> readWindow(Context context,SQLiteDatabase db,Map<String,String> names,String aliasColumn,Function<String,String> fallback,Titles titles,CancellationSignal signal,long from,long to,int genre){
        signal.throwIfCanceled();
        List<VelaShows.Airing> out=new ArrayList<>();
        if(names.isEmpty()||genre>0&&!VelaDiscovery.categoryColumn(db,""))return out;
        JSONObject fixes=context==null?new JSONObject():VelaGuideFix.all(context);
        int skip=titles.skip;
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS vela_search_channels(alias TEXT PRIMARY KEY, name TEXT NOT NULL, fixed TEXT, fallback TEXT, offset INTEGER NOT NULL)");
        db.execSQL("DELETE FROM vela_search_channels");
        try(android.database.sqlite.SQLiteStatement insert=db.compileStatement("INSERT INTO vela_search_channels VALUES (?,?,?,?,?)")){
            for(Map.Entry<String,String> entry:names.entrySet()){
                signal.throwIfCanceled();
                String name=entry.getValue();
                if(name==null)continue;
                JSONObject fix=fixes.length()==0?null:fixes.optJSONObject(VelaGuideFix.key(context,name));
                String key=entry.getKey();
                insert.bindString(1,key);
                insert.bindString(2,name);
                insert.bindString(3,fix==null?"":fix.optString("alias"));
                insert.bindString(4,fallback==null?"":fallback.apply(key));
                insert.bindLong(5,fix==null?0:fix.optInt("offset")*60L);
                insert.executeInsert();
            }
        }
        // A fallback only applies when the primary spelling is absent, never when it is ambiguous.
        String lookup="CASE WHEN n.fallback<>'' AND NOT EXISTS (SELECT 1 FROM aliases WHERE "+aliasColumn+"=n.alias) THEN n.fallback ELSE n.alias END";
        String channels="WITH local_channels(channel,name,offset) AS ("
                +"SELECT fixed,name,offset FROM vela_search_channels WHERE fixed<>'' UNION ALL "
                +"SELECT MIN(a.channel),n.name,n.offset FROM vela_search_channels n JOIN aliases a ON a."+aliasColumn+"="+lookup
                +" WHERE n.fixed='' GROUP BY n.alias HAVING COUNT(*)=1) ";
        try(Cursor rows=db.rawQuery(channels+"SELECT p.start+n.offset,p.end+n.offset,p.title,n.name,"+(VelaDiscovery.eventColumn(db,"")?"p.event_id":"''")+","+VelaDiscovery.extra(db,"","description")+","+VelaDiscovery.extra(db,"","image")+" FROM local_channels n JOIN programmes p ON p.channel=n.channel WHERE p.end+n.offset>CAST(? AS INTEGER) AND p.start+n.offset<CAST(? AS INTEGER) "+VelaDiscovery.predicate(genre,"p.category")+" GROUP BY p.channel,p.start,p.title,n.name ORDER BY p.start+n.offset,n.name,p.channel,p.title LIMIT 100000",new String[]{Long.toString(from),Long.toString(to)},signal)){
            while(rows.moveToNext()){signal.throwIfCanceled();String title=rows.getString(2),name=rows.getString(3);if(!titles.matches(title))continue;if(skip-->0)continue;out.add(new VelaShows.Airing(title,name,rows.getLong(0),rows.getLong(1),rows.getString(4),rows.getString(5),rows.getString(6),""));if(out.size()>=titles.limit)break;}
        }return out;
    }
}
