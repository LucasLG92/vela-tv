package tv.vela.launcher;

import android.content.Context;
import java.util.*;

/** A twelve-record channel picker. Uses the same indexed sources without opening another player. */
final class TvPlayerCatalog {
    static TvCatalogCache.Page page(Context context,String mode,String group,String groupName,String query,int page)throws Exception{
        if(mode.equals("xtream")){
            XtreamClient client=new XtreamClient(context);
            XtreamClient.Page result=group.equals("@favorites")?client.favoritePage(page,query):client.page(group.startsWith("@xc:")?group.substring(4):"*",groupName,page,query,false);
            return new TvCatalogCache.Page(result.channels,result.page,result.total);
        }
        Set<String> favorites=group.equals("@favorites")?LiveTvData.favorites(context,LiveTvData.source(context)):null;
        if(mode.equals("antenna"))return new AntennaClient(context).page(query,favorites,page);
        M3uClient client=new M3uClient(context);client.refresh(false);
        return client.page(group.startsWith("@m3u:")?group.substring(5):null,query,favorites,page);
    }
    static Map<String,LiveTvData.Guide> guide(Context context,String mode,List<LiveTvData.Channel> channels){
        Map<String,LiveTvData.Guide> result=new HashMap<>();
        try{
            try{result.putAll(new XmlTvGuide(context).guide(channels));}catch(Exception unavailable){if(Thread.currentThread().isInterrupted())return result;}
            List<LiveTvData.Channel> missing=new ArrayList<>();long now=System.currentTimeMillis()/1000;
            for(LiveTvData.Channel ch:channels)if(!result.containsKey(ch.id)||result.get(ch.id).current(now)==null)missing.add(ch);
            if(!missing.isEmpty()){
                Map<String,LiveTvData.Guide> fallback=mode.equals("xtream")?new XtreamClient(context).guide(missing):mode.equals("antenna")?new AntennaClient(context).guide(missing):new M3uClient(context).guide(missing);
                for(Map.Entry<String,LiveTvData.Guide> entry:fallback.entrySet())if(!result.containsKey(entry.getKey())||entry.getValue().current(now)!=null)result.put(entry.getKey(),entry.getValue());
            }
        }catch(Exception unavailable){/* Programme information is optional; playback continues. */}
        return result;
    }
}
