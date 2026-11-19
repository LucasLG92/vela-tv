package tv.vela.launcher;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.*;
import android.net.Uri;
import android.os.CancellationSignal;
import java.io.InterruptedIOException;
import java.util.*;

/** Read-only tuner metadata. Never scans, changes favourites or reorders the manufacturer's database. */
class LiteAntennaData {
    static final int MAX_CHANNELS=2000,MAX_PROGRAMMES=32;
    private final Context context;
    LiteAntennaData(Context context){this.context=context.getApplicationContext();}
    boolean permitted(){return context.checkSelfPermission("android.permission.READ_TV_LISTINGS")==PackageManager.PERMISSION_GRANTED;}
    void invalidateGuide(){new LiteXmlTvGuide(context).invalidate();}
    boolean refreshGuide(List<LiveTvData.Channel> channels,CancellationSignal cancel)throws Exception{return new LiteXmlTvGuide(context).refresh(channels,cancel);}
    List<LiveTvData.Channel> channels(CancellationSignal cancel)throws Exception {
        if(!permitted())throw new SecurityException("TV listings permission required");
        TvInputManager manager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);Set<String> tuners=new HashSet<>();
        if(manager!=null)for(TvInputInfo input:manager.getTvInputList())if(input.getType()==TvInputInfo.TYPE_TUNER&&!input.isPassthroughInput()&&input.getServiceInfo()!=null&&(input.getServiceInfo().applicationInfo.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0&&!input.getServiceInfo().packageName.startsWith("com.mitv.tvhome."))tuners.add(input.getId());
        List<LiveTvData.Channel> result=new ArrayList<>();
        String[] columns={"_id","input_id","display_number","display_name","browsable","locked","service_type","type","video_format","original_network_id","transport_stream_id","service_id"};
        try(Cursor cursor=context.getContentResolver().query(TvContract.Channels.CONTENT_URI,columns,null,null,null,cancel)){
            for(int count=0;cursor!=null&&cursor.moveToNext()&&count++<MAX_CHANNELS;){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();String input=cursor.getString(1),name=cursor.getString(3);if(!tuners.contains(input)||name==null||name.trim().isEmpty()||cursor.getInt(4)!=1)continue;
                boolean radio=TvContract.Channels.SERVICE_TYPE_AUDIO.equals(cursor.getString(6));String format=cursor.getString(8);boolean hd=(format!=null&&(format.contains("720")||format.contains("1080")||format.contains("2160")||format.contains("4320")))||name.toUpperCase(Locale.ROOT).matches(".*\\b(UHD|HD|4K|8K)\\b.*");
                int network=cursor.getInt(9),transport=cursor.getInt(10),service=cursor.getInt(11);String identity=network>0&&transport>0&&service>0?cursor.getString(7)+":"+network+":"+transport+":"+service:"";
                result.add(new LiveTvData.Channel(cursor.getLong(0),input,TvInputs.clean(cursor.getString(2)),TvInputs.clean(name),radio,hd,cursor.getInt(5)==1,identity));
            }
        }
        result.sort((a,b)->{int radio=Boolean.compare(a.radio,b.radio);if(radio!=0)return radio;int order=Long.compare(channelOrder(a.number),channelOrder(b.number));return order!=0?order:a.name.compareToIgnoreCase(b.name);});return result;
    }
    static long channelOrder(String number){try{String[] p=number.split("[.\\-]",2);long major=Long.parseLong(p[0]),minor=p.length>1?Long.parseLong(p[1]):0;if(major>=0&&major<100000&&minor>=0&&minor<1000)return major*1000+minor;}catch(RuntimeException ignored){}return Long.MAX_VALUE;}
    Map<String,List<LiveTvData.Programme>> programmes(List<LiveTvData.Channel> channels,long from,long to,CancellationSignal cancel)throws Exception {
        Map<String,List<LiveTvData.Programme>> result=new HashMap<>();
        for(LiveTvData.Channel channel:channels){
            if(cancel.isCanceled()||Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            long offset=VelaGuideFix.offset(context,channel.name);
            try{
                result.put(channel.id,VelaAntennaListings.read(context.getContentResolver(),channel.providerId,from,to,offset,MAX_PROGRAMMES,cancel));
            }catch(SecurityException unavailable){
                // Some TVs allow channel tuning but restrict programme metadata.
            }
        }
        Map<String,List<LiveTvData.Programme>> online=new LiteXmlTvGuide(context).read(channels,from,to,cancel);
        for(LiveTvData.Channel channel:channels){
            List<LiveTvData.Programme> extra=online.get(channel.id);
            if(extra!=null&&!extra.isEmpty())result.put(channel.id,VelaAntennaListings.merge(extra,result.getOrDefault(channel.id,Collections.emptyList()),MAX_PROGRAMMES));
        }
        return result;
    }
    private static String trim(String value,int limit){if(value==null)return "";return value.substring(0,Math.min(value.length(),limit)).replaceAll("[\\p{Cntrl}&&[^\\n]]"," ").trim();}
}
