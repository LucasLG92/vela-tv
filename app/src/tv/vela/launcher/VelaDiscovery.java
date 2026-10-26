package tv.vela.launcher;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.media.tv.TvContract;
import android.os.CancellationSignal;
import java.util.*;

/** Discover actual EPG metadata, never inferred movie/sports recommendations. */
final class VelaDiscovery {
    static long[] bounds(int mode){long now=System.currentTimeMillis()/1000;if(mode==0)return new long[]{now,now+1};if(mode==1){Calendar a=Calendar.getInstance();a.set(Calendar.HOUR_OF_DAY,18);a.set(Calendar.MINUTE,0);a.set(Calendar.SECOND,0);a.set(Calendar.MILLISECOND,0);Calendar b=(Calendar)a.clone();b.add(Calendar.DAY_OF_MONTH,1);b.set(Calendar.HOUR_OF_DAY,4);if(now>=b.getTimeInMillis()/1000){a.add(Calendar.DAY_OF_MONTH,1);b.add(Calendar.DAY_OF_MONTH,1);}return new long[]{Math.max(now,a.getTimeInMillis()/1000),b.getTimeInMillis()/1000};}return new long[]{now,now+48*3600};}
    static boolean categoryColumn(SQLiteDatabase db,String schema){try(Cursor c=db.rawQuery("PRAGMA "+schema+"table_info(programmes)",null)){while(c.moveToNext())if("category".equals(c.getString(1)))return true;}return false;}
    static String predicate(int genre,String column){String[][] keys={{},{"movie","film","cine","película","pelicula"},{"sport","deporte","football","soccer"},{"documentary","documental"},{"children","kids","infantil","child"}};if(genre<1||genre>=keys.length)return "";StringBuilder sql=new StringBuilder(" AND (");for(String k:keys[genre]){if(sql.length()>6)sql.append(" OR ");sql.append("lower(").append(column).append(") LIKE '%").append(k).append("%'");}return sql.append(")").toString();}
    static List<VelaShows.Airing> local(Context c,Map<Long,String> names,int mode,int genre,int page,CancellationSignal signal){Map<String,Long> offsets=VelaGuideFix.offsets(c);List<VelaShows.Airing> out=new ArrayList<>();long[] w=bounds(mode);int skip=page*24;try(Cursor rows=c.getContentResolver().query(TvContract.Programs.CONTENT_URI,new String[]{"channel_id","start_time_utc_millis","end_time_utc_millis","title","canonical_genre"},null,null,"start_time_utc_millis ASC",signal)){for(int count=0;rows!=null&&rows.moveToNext()&&count++<20000;){signal.throwIfCanceled();String name=names.get(rows.getLong(0)),title=rows.getString(3);long offset=name==null?0:offsets.getOrDefault(TvChannelNames.match(name),0L);long start=rows.getLong(1)/1000+offset,end=rows.getLong(2)/1000+offset;if(name==null||title==null||start>=w[1]||end<=w[0])continue;String g=VelaWorkspace.normal(rows.getString(4)==null?"":rows.getString(4));boolean matches=genre==0||genre==1&&(g.contains("movie")||g.contains("film"))||genre==2&&g.contains("sport")||genre==3&&g.contains("documentary")||genre==4&&(g.contains("family")||g.contains("kids"));if(!matches||skip-->0)continue;out.add(new VelaShows.Airing(title,name,start,end));if(out.size()>=24)break;}}catch(RuntimeException unavailable){}return out;}
}
