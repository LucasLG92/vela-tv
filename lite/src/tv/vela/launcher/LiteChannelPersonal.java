package tv.vela.launcher;

import android.content.Context;
import java.util.*;

/** Uses DVB service identity, or an unambiguous name fallback, across antenna rescans. */
final class LiteChannelPersonal {
    static String key(LiveTvData.Channel c,List<LiveTvData.Channel> all){if(c.identity.startsWith("dvb:"))return c.identity;int count=0;for(LiveTvData.Channel v:all)if(v.alias.equals(c.alias))count++;return count==1?c.alias:c.identity;}
    static Map<String,String> keys(List<LiveTvData.Channel> all){Map<String,Integer> counts=new HashMap<>();for(LiveTvData.Channel v:all)counts.put(v.alias,counts.getOrDefault(v.alias,0)+1);Map<String,String> keys=new HashMap<>();for(LiveTvData.Channel v:all)keys.put(v.id,v.identity.startsWith("dvb:")||counts.get(v.alias)!=1?v.identity:v.alias);return keys;}
    static List<LiveTvData.Channel> visible(Context c,List<LiveTvData.Channel> all){Set<String> hidden=new TvPersonalization(c,"antenna").hiddenKeys();List<LiveTvData.Channel> out=new ArrayList<>();Map<String,String> keys=keys(all);for(LiveTvData.Channel v:all)if(!hidden.contains(keys.get(v.id)))out.add(v);return out;}
    static void sort(Context c,List<LiveTvData.Channel> values,List<LiveTvData.Channel> all){List<String> order=new TvPersonalization(c,"antenna").order();Map<String,Integer> ranks=new HashMap<>();for(int i=0;i<order.size();i++)ranks.put(order.get(i),i);Map<String,Integer> channelRanks=new HashMap<>();Map<String,String> keys=keys(all);for(LiveTvData.Channel v:values)channelRanks.put(v.id,ranks.getOrDefault(keys.get(v.id),128));values.sort(Comparator.comparingInt(v->channelRanks.get(v.id)));}
    static List<LiveTvData.Channel> recent(Context c,List<LiveTvData.Channel> all){TvPersonalization state=new TvPersonalization(c,"antenna");Set<String> hidden=state.hiddenKeys();Map<String,LiveTvData.Channel> byKey=new HashMap<>();Map<String,String> keys=keys(all);for(LiveTvData.Channel v:all)byKey.put(keys.get(v.id),v);List<LiveTvData.Channel> out=new ArrayList<>();for(TvPersonalization.Item item:state.recent())if(!hidden.contains(item.key)&&byKey.containsKey(item.key))out.add(byKey.get(item.key));return out;}
}
