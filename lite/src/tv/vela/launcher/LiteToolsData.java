package tv.vela.launcher;

import android.content.Context;
import android.os.*;
import java.util.*;

/** Lite only consults its own tuner. No Full library or IPTV clients are linked. */
abstract class LiteToolsData extends VelaToolsData {
    private final LiteAntennaData tuner;
    LiteToolsData(LiteHomeActivity home){super(home);tuner=home.createAntennaData();}
    List<VelaHub.Hit> channels(String query,int page,CancellationSignal signal)throws Exception{
        VelaHub.Hits out=new VelaHub.Hits();List<LiveTvData.Channel> all=LiteChannelPersonal.visible(activity,tuner.channels(signal));int found=0;String exact=LiteXmlTvGuide.match(query);
        for(int pass=0;pass<2;pass++)for(LiveTvData.Channel ch:all){signal.throwIfCanceled();boolean same=!query.isEmpty()&&(LiteXmlTvGuide.match(ch.name).equals(exact)||query.equals(ch.number));if(ch.locked||same!=(pass==0)||!same&&!VelaWorkspace.normal(ch.name).contains(VelaWorkspace.normal(query)))continue;if(found>=page*12&&found<(page+1)*12)out.add(new VelaHub.Hit(VelaWorkspace.t("Channel","Canal"),ch.name,VelaWorkspace.t("Antenna","Antena"),ch));found++;}out.more=found>(page+1)*12;return out;
    }
    private static final class GuideChannels {
        final Map<Long,String> visible=new HashMap<>(),names=new HashMap<>(),keys=new HashMap<>();
        GuideChannels(Context context,List<LiveTvData.Channel> all){
            for(LiveTvData.Channel channel:all){
                names.put(channel.providerId,channel.name);
                keys.put(channel.providerId,channel.identity);
            }
            for(LiveTvData.Channel channel:LiteChannelPersonal.visible(context,all))
                if(!channel.locked)visible.put(channel.providerId,channel.name);
        }
        List<VelaShows.Airing> references(List<VelaShows.Airing> airings){
            return antennaReferences(airings,names,ANTENNA,keys::get);
        }
    }
    List<VelaShows.Airing> airings(VelaGuideSearch.Titles titles,CancellationSignal signal)throws Exception{
        if(!tuner.permitted())return Collections.emptyList();
        GuideChannels channels=new GuideChannels(activity,tuner.channels(signal));
        List<VelaShows.Airing> nativeGuide=VelaToolsData.nativeAirings(activity,channels.visible,titles,signal);
        List<VelaShows.Airing> onlineGuide=new LiteXmlTvGuide(activity).airings(channels.visible,titles,signal);
        return channels.references(VelaToolsData.merge(nativeGuide,onlineGuide,Math.max(64,titles.limit)));
    }
    public List<VelaShows.Airing> discover(int mode,int genre,int page,CancellationSignal signal)throws Exception{
        if(!tuner.permitted())return Collections.emptyList();
        GuideChannels channels=new GuideChannels(activity,tuner.channels(signal));
        List<VelaShows.Airing> cached=new LiteXmlTvGuide(activity).discover(channels.visible,mode,genre,page,signal);
        List<VelaShows.Airing> airings=cached.isEmpty()?VelaDiscovery.local(activity,channels.visible,mode,genre,page,signal)
                :VelaDiscovery.nativeLogos(cached,channels.visible);
        return channels.references(airings);
    }
    static final String ANTENNA="lite-antenna";
    @Override public String broadcastSource(){return ANTENNA;}
    @Override public VelaHub.Hit resolve(org.json.JSONObject item,CancellationSignal signal)throws Exception{
        String key=item.optString("channelKey");
        if(!"tv".equals(item.optString("kind"))||!ANTENNA.equals(item.optString("source"))||key.isEmpty())return null;
        if(!tuner.permitted())return null;
        LiveTvData.Channel found=null;
        for(LiveTvData.Channel channel:LiteChannelPersonal.visible(activity,tuner.channels(signal))){
            signal.throwIfCanceled();
            if(!channel.locked&&key.equals(channel.identity)){
                if(found!=null)return null;
                found=channel;
            }
        }
        return found==null?null:new VelaHub.Hit(L10n.text("Channel"),found.name,L10n.text("Antenna"),found);
    }
    void favorite(LiveTvData.Channel c)throws Exception{List<LiveTvData.Channel> all=tuner.channels(new CancellationSignal());new LiteTvUi.Favourites(activity).toggle(c,all);}
    public void repair(){new LiteXmlTvGuide(activity).invalidate();}
    public String scope(){return LiveTvData.hash(VelaGuideFix.all(activity).toString())+"|antenna:"+VelaWorkspace.prefs(activity,"lite_tv").getBoolean("online_guide",true);}
}
