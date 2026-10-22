package tv.vela.launcher;

import android.content.*;
import android.database.Cursor;
import android.media.tv.*;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Reads the TV's existing tuner catalog. Never scans, writes or reorders system channels. */
final class AntennaClient {
    static final int MAX_CHANNELS=5000;
    private final Context context;
    AntennaClient(Context c){context=c.getApplicationContext();}
    boolean available(){return HomeApps.tunerInput(context)!=null;}
    Intent setup(){try{TvInputManager manager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);TvInputInfo info=manager.getTvInputInfo(HomeApps.tunerInput(context));return info==null?null:info.createSetupIntent();}catch(RuntimeException unavailable){return null;}}
    private static long order(String number,int position){try{String[] p=number.split("[.\\-]",2);long main=Long.parseLong(p[0]),sub=p.length>1?Long.parseLong(p[1]):0;if(main>=0&&main<100000&&sub>=0&&sub<1000)return ((main*1000+sub)<<16)|position;}catch(Exception ignored){}return (1_000_000_000L<<16)|position;}
    TvCatalogCache.Page page(String query,Set<String> favorites,int requested)throws Exception{
        String input=HomeApps.tunerInput(context);if(input==null)return new TvCatalogCache.Page(new ArrayList<>(),0,0);
        String[] columns={"_id","display_number","display_name","browsable","locked","service_type","type"};
        try(Cursor c=context.getContentResolver().query(TvContract.buildChannelsUriForInput(input),columns,null,null,null)){
            if(c==null)return new TvCatalogCache.Page(new ArrayList<>(),0,0);long[] order=new long[Math.min(MAX_CHANNELS,c.getCount())];int total=0;String normalized=TvCatalogCache.normalize(query);
            for(int scanned=0;c.moveToNext()&&scanned<MAX_CHANNELS;scanned++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();String name=c.getString(2),id="ant-"+c.getLong(0);
                if(c.getInt(3)!=1||c.getInt(4)!=0||TvContract.Channels.TYPE_PREVIEW.equals(c.getString(6))||name==null||!TvCatalogCache.normalize(name).contains(normalized)||favorites!=null&&!favorites.contains(id))continue;
                order[total++]=order(c.getString(1)==null?"":c.getString(1),c.getPosition());
            }
            Arrays.sort(order,0,total);int page=Math.max(0,Math.min(requested,Math.max(0,(total-1)/LiveTvData.PAGE_SIZE)));List<LiveTvData.Channel> out=new ArrayList<>();
            for(int i=page*LiveTvData.PAGE_SIZE;i<Math.min(total,(page+1)*LiveTvData.PAGE_SIZE);i++){
                c.moveToPosition((int)(order[i]&65535));long id=c.getLong(0);String number=c.getString(1)==null?"":c.getString(1);
                out.add(new LiveTvData.Channel("ant-"+id,c.getString(2),Ui.tr("Antena","Antenna","Antena"),TvContract.buildChannelLogoUri(id).toString(),Collections.emptyList(),"","",input,number,Long.toString(id)));
            }return new TvCatalogCache.Page(out,page,total);
        }
    }
    static boolean localLogo(String value){try{Uri uri=Uri.parse(value);return "content".equals(uri.getScheme())&&"android.media.tv".equals(uri.getAuthority())&&uri.getPath()!=null&&uri.getPath().matches("/channel/[0-9]+/logo")&&uri.getQuery()==null;}catch(Exception bad){return false;}}
    Map<String,LiveTvData.Guide> guide(List<LiveTvData.Channel> channels){
        Map<String,LiveTvData.Guide> result=new HashMap<>();long now=System.currentTimeMillis()/1000;int count=0;
        for(LiveTvData.Channel channel:channels){if(++count>LiveTvData.PAGE_SIZE||Thread.currentThread().isInterrupted())break;
            try{Uri uri=TvContract.buildProgramsUriForChannel(TvContract.buildChannelUri(Long.parseLong(channel.epgId)),now*1000,(now+24*3600)*1000);
                try(Cursor c=context.getContentResolver().query(uri,new String[]{"start_time_utc_millis","end_time_utc_millis","title"},null,null,"start_time_utc_millis ASC")){
                    LiveTvData.Guide entry=new LiveTvData.Guide();int scanned=0;while(c!=null&&c.moveToNext()&&scanned++<64){String title=c.getString(2);if(title!=null)entry.add(new LiveTvData.Programme(c.getLong(0)/1000,c.getLong(1)/1000,title.substring(0,Math.min(512,title.length()))),now);}
                    if(entry.now!=null||entry.next!=null)result.put(channel.id,entry);
                }
            }catch(Exception unavailable){/* A missing EPG never prevents tuning. */}
        }return result;
    }
    Map<String,List<LiveTvData.Programme>> window(List<LiveTvData.Channel> visible,long start){
        Map<String,List<LiveTvData.Programme>> result=new HashMap<>();int count=0;for(LiveTvData.Channel channel:visible){if(++count>TvTimelineView.ROWS||Thread.currentThread().isInterrupted())break;
            try{Uri uri=TvContract.buildProgramsUriForChannel(TvContract.buildChannelUri(Long.parseLong(channel.epgId)),start*1000,(start+TvTimelineView.SECONDS)*1000);
                try(Cursor c=context.getContentResolver().query(uri,new String[]{"start_time_utc_millis","end_time_utc_millis","title"},null,null,"start_time_utc_millis ASC")){
                    List<LiveTvData.Programme> list=new ArrayList<>();int scanned=0;while(c!=null&&scanned++<64&&list.size()<TvTimelineView.MAX_PROGRAMMES&&!Thread.currentThread().isInterrupted()&&c.moveToNext()){String title=c.getString(2);long from=c.getLong(0)/1000,to=c.getLong(1)/1000;if(title!=null&&to>start&&from<start+TvTimelineView.SECONDS&&to>from)list.add(new LiveTvData.Programme(from,to,title.substring(0,Math.min(512,title.length()))));}if(!list.isEmpty())result.put(channel.id,list);
                }
            }catch(Exception unavailable){}
        }return result;
    }
}
