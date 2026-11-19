package tv.vela.launcher;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.CancellationSignal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read one channel at a time. Some vendor TV providers expose only the mandatory columns. */
final class VelaAntennaListings {
    private static final String[] BASIC={"start_time_utc_millis","end_time_utc_millis","title"};
    private static final String[] DESCRIPTION={BASIC[0],BASIC[1],BASIC[2],"short_description"};
    private static final String[] ARTWORK={BASIC[0],BASIC[1],BASIC[2],"short_description","poster_art_uri","thumbnail_uri"};

    private static Cursor query(ContentResolver resolver,Uri uri,CancellationSignal cancel){
        for(String[] columns:new String[][]{ARTWORK,DESCRIPTION,BASIC}){
            try{
                Cursor cursor=resolver.query(uri,columns,null,null,"start_time_utc_millis ASC",cancel);
                if(cursor!=null)return cursor;
            }catch(IllegalArgumentException | android.database.sqlite.SQLiteException unsupported){/* Try a smaller projection. */}
        }
        return null;
    }

    private static String string(Cursor cursor,int index,int max){
        if(index<0||cursor.isNull(index))return "";
        String text=cursor.getString(index).trim();
        return text.substring(0,Math.min(text.length(),max));
    }

    static List<LiveTvData.Programme> read(ContentResolver resolver,long channel,long from,long to,long offset,int limit,CancellationSignal cancel){
        List<LiveTvData.Programme> result=new ArrayList<>();
        Uri uri=TvContract.buildProgramsUriForChannel(TvContract.buildChannelUri(channel),(from-offset)*1000,(to-offset)*1000);
        try(Cursor c=query(resolver,uri,cancel)){
            if(c==null)return result;
            int desc=c.getColumnIndex("short_description"),poster=c.getColumnIndex("poster_art_uri"),thumb=c.getColumnIndex("thumbnail_uri");
            for(int i=0;i<128&&result.size()<limit&&!Thread.currentThread().isInterrupted()&&c.moveToNext();i++){
                long start=c.getLong(0)/1000+offset,end=c.getLong(1)/1000+offset;
                String title=string(c,2,512);
                if(start>=to||end<=from||end<=start||title.isEmpty())continue;
                String image=VelaProgrammeArt.url(string(c,poster,4096));
                if(image.isEmpty())image=VelaProgrammeArt.url(string(c,thumb,4096));
                result.add(new LiveTvData.Programme(start,end,title,string(c,desc,1600),image));
            }
        }
        return result;
    }

    static List<LiveTvData.Programme> merge(List<LiveTvData.Programme> listings,List<LiveTvData.Programme> fallback,int limit){
        List<LiveTvData.Programme> result=new ArrayList<>(listings);
        for(LiveTvData.Programme candidate:fallback){
            boolean overlaps=false;
            for(int i=0;i<result.size();i++){
                LiveTvData.Programme item=result.get(i);
                if(item.end<=candidate.start||item.start>=candidate.end)continue;
                overlaps=true;
                if(item.image.isEmpty()&&!candidate.image.isEmpty()&&item.title.equalsIgnoreCase(candidate.title)
                    &&Math.abs(item.start-candidate.start)<=300){
                    result.set(i,new LiveTvData.Programme(item.start,item.end,item.title,item.description,candidate.image));
                    break;
                }
            }
            if(!overlaps)result.add(candidate);
        }
        result.sort(Comparator.comparingLong(p->p.start));
        if(result.size()>limit)result.subList(limit,result.size()).clear();
        return result;
    }
}
