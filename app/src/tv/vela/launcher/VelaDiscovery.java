package tv.vela.launcher;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.media.tv.TvContract;
import android.os.CancellationSignal;
import java.util.*;

/** Discover actual EPG metadata, never inferred movie/sports recommendations. */
final class VelaDiscovery {
    static long[] bounds(int mode){return bounds(mode,Calendar.getInstance());}
    static long[] bounds(int mode,Calendar local){
        long now=local.getTimeInMillis()/1000;
        if(mode==0)return new long[]{now,now+1};
        if(mode!=1)return new long[]{now,now+48*3600};

        // The evening guide continues after midnight until 04:00 local time.
        Calendar start=(Calendar)local.clone();
        if(local.get(Calendar.HOUR_OF_DAY)<4)start.add(Calendar.DAY_OF_MONTH,-1);
        start.set(Calendar.HOUR_OF_DAY,18);
        start.set(Calendar.MINUTE,0);
        start.set(Calendar.SECOND,0);
        start.set(Calendar.MILLISECOND,0);
        Calendar end=(Calendar)start.clone();
        end.add(Calendar.DAY_OF_MONTH,1);
        end.set(Calendar.HOUR_OF_DAY,4);
        return new long[]{Math.max(now,start.getTimeInMillis()/1000),end.getTimeInMillis()/1000};
    }
    static String eventId(String system,String value){return "dd_progid".equals(system)&&value!=null&&value.matches("(?:EP|SP|MV)[0-9]{6,14}\\.[0-9]{1,8}")?"dd:"+value:"";}
    static boolean eventColumn(SQLiteDatabase db,String schema){try(Cursor c=db.rawQuery("PRAGMA "+schema+"table_info(programmes)",null)){while(c.moveToNext())if("event_id".equals(c.getString(1)))return true;}return false;}
    static void ensureEvents(SQLiteDatabase db){if(!eventColumn(db,"")){db.execSQL("ALTER TABLE programmes ADD COLUMN event_id TEXT NOT NULL DEFAULT ''");db.execSQL("UPDATE meta SET updated=0");}}
    static boolean column(SQLiteDatabase db,String schema,String name){try(Cursor c=db.rawQuery("PRAGMA "+schema+"table_info(programmes)",null)){while(c.moveToNext())if(name.equals(c.getString(1)))return true;}return false;}
    static void ensureArtwork(SQLiteDatabase db){for(String column:new String[]{"description","image"})if(!column(db,"",column)){db.execSQL("ALTER TABLE programmes ADD COLUMN "+column+" TEXT NOT NULL DEFAULT ''");db.execSQL("UPDATE meta SET updated=0");}}
    static String extra(SQLiteDatabase db,String schema,String name){return column(db,schema,name)?"p."+name:"''";}
    static List<VelaShows.Airing> nativeLogos(List<VelaShows.Airing> values,Map<Long,String> names){List<VelaShows.Airing> out=new ArrayList<>();for(VelaShows.Airing p:values){String logo=p.logo;if(logo.isEmpty()){Long match=null;boolean ambiguous=false;for(Map.Entry<Long,String> e:names.entrySet())if(e.getValue().equals(p.channel)){if(match!=null){ambiguous=true;break;}match=e.getKey();}if(match!=null&&!ambiguous)logo=TvContract.buildChannelLogoUri(match).toString();}out.add(new VelaShows.Airing(p.title,p.channel,p.start,p.end,p.eventId,p.description,p.image,logo));}return out;}
    static boolean categoryColumn(SQLiteDatabase db,String schema){try(Cursor c=db.rawQuery("PRAGMA "+schema+"table_info(programmes)",null)){while(c.moveToNext())if("category".equals(c.getString(1)))return true;}return false;}
    static Cursor nativeProgrammes(Context c,String[] required,CancellationSignal signal){List<String> columns=new ArrayList<>(Arrays.asList(required));columns.add("short_description");columns.add("poster_art_uri");try{return c.getContentResolver().query(TvContract.Programs.CONTENT_URI,columns.toArray(new String[0]),null,null,"start_time_utc_millis ASC",signal);}catch(IllegalArgumentException unavailable){return c.getContentResolver().query(TvContract.Programs.CONTENT_URI,required,null,null,"start_time_utc_millis ASC",signal);}}
    static VelaShows.Airing nativeAiring(Cursor rows,String title,String name,long start,long end){int description=rows.getColumnIndex("short_description"),image=rows.getColumnIndex("poster_art_uri");return new VelaShows.Airing(title,name,start,end,"",description<0?"":rows.getString(description),image<0?"":rows.getString(image),TvContract.buildChannelLogoUri(rows.getLong(rows.getColumnIndexOrThrow("channel_id"))).toString());}
    static String predicate(int genre,String column){return XmlTvCategories.predicate(genre,column);}
    static List<VelaShows.Airing> local(Context c,Map<Long,String> names,int mode,int genre,int page,CancellationSignal signal){Map<String,Long> offsets=VelaGuideFix.offsets(c);List<VelaShows.Airing> out=new ArrayList<>();long[] w=bounds(mode);int skip=page*24;try(Cursor rows=nativeProgrammes(c,new String[]{"channel_id","start_time_utc_millis","end_time_utc_millis","title","canonical_genre"},signal)){for(int count=0;rows!=null&&rows.moveToNext()&&count++<20000;){signal.throwIfCanceled();String name=names.get(rows.getLong(0)),title=rows.getString(3);long offset=name==null?0:offsets.getOrDefault(TvChannelNames.match(name),0L);long start=rows.getLong(1)/1000+offset,end=rows.getLong(2)/1000+offset;if(name==null||title==null||start>=w[1]||end<=w[0])continue;String g=VelaWorkspace.normal(rows.getString(4)==null?"":rows.getString(4));boolean matches=genre==0||genre==1&&(g.contains("movie")||g.contains("film"))||genre==2&&g.contains("sport")||genre==3&&g.contains("documentary")||genre==4&&(g.contains("family")||g.contains("kids"));if(!matches||skip-->0)continue;out.add(nativeAiring(rows,title,name,start,end));if(out.size()>=24)break;}}catch(RuntimeException unavailable){}return out;}
}
