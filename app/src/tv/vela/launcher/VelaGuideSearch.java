package tv.vela.launcher;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;
import java.util.*;

/** Query an existing EPG database. Only known local channels can appear in results. */
final class VelaGuideSearch {
    static String like(String q){return "%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    static List<VelaShows.Airing> read(SQLiteDatabase db,Map<String,String> names,String aliasColumn,String query,int page,CancellationSignal signal){
        long now=System.currentTimeMillis()/1000;return readWindow(db,names,aliasColumn,query,page,signal,now,now+48*3600,0);
    }
    static List<VelaShows.Airing> readWindow(SQLiteDatabase db,Map<String,String> names,String aliasColumn,String query,int page,CancellationSignal signal,long from,long to,int genre){
        return readWindow(null,db,names,aliasColumn,query,page,signal,from,to,genre);
    }
    static List<VelaShows.Airing> readWindow(android.content.Context context,SQLiteDatabase db,Map<String,String> names,String aliasColumn,String query,int page,CancellationSignal signal,long from,long to,int genre){
        Map<String,Long> offsets=context==null?Collections.emptyMap():VelaGuideFix.offsets(context);List<VelaShows.Airing> out=new ArrayList<>();int skip=page*24;if(genre>0&&!VelaDiscovery.categoryColumn(db,""))return out;
        db.execSQL("DROP TABLE IF EXISTS temp.vela_local_names");db.execSQL("CREATE TEMP TABLE vela_local_names(alias TEXT PRIMARY KEY,name TEXT NOT NULL,offset INTEGER NOT NULL)");db.execSQL("DELETE FROM vela_local_names");
        try(android.database.sqlite.SQLiteStatement insert=db.compileStatement("INSERT OR IGNORE INTO vela_local_names(alias,name,offset) VALUES (?,?,?)")){for(Map.Entry<String,String> name:names.entrySet()){signal.throwIfCanceled();if(name.getValue()==null)continue;insert.bindString(1,name.getKey());insert.bindString(2,name.getValue());insert.bindLong(3,offsets.getOrDefault(TvChannelNames.match(name.getValue()),0L));insert.executeInsert();}}
        String normalized=VelaWorkspace.normal(query);
        try(Cursor rows=db.rawQuery("SELECT p.start+n.offset,p.end+n.offset,p.title,n.name FROM programmes p JOIN aliases a ON p.channel=a.channel JOIN vela_local_names n ON a."+aliasColumn+"=n.alias WHERE p.end+n.offset>CAST(? AS INTEGER) AND p.start+n.offset<CAST(? AS INTEGER) "+VelaDiscovery.predicate(genre,"p.category")+" GROUP BY p.channel,p.start,p.title,n.name ORDER BY p.start+n.offset LIMIT 100000",new String[]{Long.toString(from),Long.toString(to)})){
            while(rows.moveToNext()){signal.throwIfCanceled();String title=rows.getString(2),name=rows.getString(3);if(!VelaWorkspace.normal(title).contains(normalized))continue;if(skip-->0)continue;out.add(new VelaShows.Airing(title,name,rows.getLong(0),rows.getLong(1)));if(out.size()>=24)break;}
        }return out;
    }
}
