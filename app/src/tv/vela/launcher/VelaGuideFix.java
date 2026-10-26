package tv.vela.launcher;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;
import org.json.*;
import java.io.*;
import java.util.*;

/** Explicit channel-name corrections survive quality variants and tuner renumbering. */
final class VelaGuideFix {
    static String key(String name){String n=TvChannelNames.match(name);return LiveTvData.hash(n.isEmpty()?VelaWorkspace.normal(name):n);}

    static JSONObject all(Context c){try{return validate(new JSONObject(VelaWorkspace.prefs(c,"vela-epg-fixes").getString("entries","{}")));}catch(Exception bad){return new JSONObject();}}
    static JSONObject validate(JSONObject j)throws Exception{if(j.length()>256)throw new IOException("Correction limit");for(Iterator<String> it=j.keys();it.hasNext();){String k=it.next();JSONObject v=j.getJSONObject(k);if(!k.matches("[a-f0-9]{64}")||(v.length()<2||v.length()>3)||!(v.get("offset") instanceof Integer)||(v.getInt("offset")< -720||v.getInt("offset")>840))throw new IOException("Invalid correction");if(v.has("name")&&(!(v.get("name") instanceof String)||!v.getString("name").matches("[a-z0-9+]{0,256}")))throw new IOException("Invalid guide name");String a=v.getString("alias");if(a.length()>256||a.contains("://")||!a.equals(VelaWorkspace.clean(a,256)))throw new IOException("Invalid guide identity");}return j;}
    static String alias(Context c,String name){JSONObject j=all(c).optJSONObject(key(name));return j==null?"":j.optString("alias","");}
    static long offset(Context c,String name){JSONObject j=all(c).optJSONObject(key(name));return j==null?0:j.optInt("offset",0)*60L;}
    static void set(Context c,String name,String alias,int minutes)throws Exception{JSONObject j=all(c);j.put(key(name),new JSONObject().put("alias",alias).put("offset",minutes).put("name",TvChannelNames.match(name)));validate(j);if(!VelaWorkspace.prefs(c,"vela-epg-fixes").edit().putString("entries",j.toString()).commit())throw new IOException("Write failed");}
    static Map<String,Long> offsets(Context c){Map<String,Long> out=new HashMap<>();JSONObject all=all(c);for(Iterator<String> keys=all.keys();keys.hasNext();){JSONObject v=all.optJSONObject(keys.next());if(v!=null&&v.has("name"))out.put(v.optString("name"),v.optInt("offset")*60L);}return out;}
    static void sql(Context c,SQLiteDatabase db){db.execSQL("CREATE TEMP TABLE IF NOT EXISTS vela_guide_fixes(name TEXT PRIMARY KEY,alias TEXT,offset INTEGER)");db.execSQL("DELETE FROM vela_guide_fixes");JSONObject all=all(c);try(android.database.sqlite.SQLiteStatement insert=db.compileStatement("INSERT OR REPLACE INTO vela_guide_fixes VALUES (?,?,?)")){for(Iterator<String> keys=all.keys();keys.hasNext();){JSONObject v=all.optJSONObject(keys.next());if(v==null||!v.has("name")||v.optString("name").isEmpty())continue;insert.bindString(1,v.optString("name"));String alias=v.optString("alias");insert.bindString(2,alias.isEmpty()?"":"id:"+VelaWorkspace.normal(alias));insert.bindLong(3,v.optInt("offset")*60L);insert.executeInsert();}}}
    static void reset(Context c,String name)throws Exception{JSONObject j=all(c);j.remove(key(name));VelaWorkspace.prefs(c,"vela-epg-fixes").edit().putString("entries",j.toString()).commit();}
    static File file(Context c){return new File(c.getCacheDir(),c.getPackageName().endsWith(".lite")?"antenna-epg/programmes.db":"tv-guide/xmltv.db");}
    static List<String> candidates(Context c,String query,int page,CancellationSignal signal){List<String> out=new ArrayList<>();File f=file(c);if(!f.isFile())return out;boolean lite=c.getPackageName().endsWith(".lite");String column=lite?"name":"alias",table=lite?"guide_options":"aliases";try(SQLiteDatabase db=SQLiteDatabase.openDatabase(f.getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor rows=db.rawQuery("SELECT DISTINCT channel FROM "+table+" WHERE "+column+" LIKE ? ESCAPE '\\' ORDER BY channel LIMIT 13 OFFSET ?",new String[]{VelaGuideSearch.like(VelaWorkspace.normal(query).replace(" ","")),Integer.toString(Math.max(0,page)*12)})){while(rows.moveToNext()){signal.throwIfCanceled();out.add(rows.getString(0));}}return out;}
    static List<VelaShows.Airing> preview(Context c,String id,CancellationSignal signal){List<VelaShows.Airing> out=new ArrayList<>();File f=file(c);if(!f.isFile())return out;try(SQLiteDatabase db=SQLiteDatabase.openDatabase(f.getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor rows=db.rawQuery("SELECT start,end,title FROM programmes WHERE channel=? AND end>? ORDER BY start LIMIT 3",new String[]{id,Long.toString(System.currentTimeMillis()/1000)})){while(rows.moveToNext()){signal.throwIfCanceled();out.add(new VelaShows.Airing(rows.getString(2),id,rows.getLong(0),rows.getLong(1)));}}return out;}
}
