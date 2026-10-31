package tv.vela.launcher;

import android.app.*;
import android.os.*;
import java.util.*;

/** Lite only consults its own tuner. No Full library or IPTV clients are linked. */
abstract class LiteToolsData extends VelaToolsData {
    LiteToolsData(Activity a){super(a);}
    List<VelaHub.Hit> channels(String query,int page,CancellationSignal signal)throws Exception{
        VelaHub.Hits out=new VelaHub.Hits();List<LiveTvData.Channel> all=LiteChannelPersonal.visible(activity,new LiteAntennaData(activity).channels(signal));int found=0;String exact=LiteXmlTvGuide.match(query);
        for(int pass=0;pass<2;pass++)for(LiveTvData.Channel ch:all){signal.throwIfCanceled();boolean same=!query.isEmpty()&&(LiteXmlTvGuide.match(ch.name).equals(exact)||query.equals(ch.number));if(ch.locked||same!=(pass==0)||!same&&!VelaWorkspace.normal(ch.name).contains(VelaWorkspace.normal(query)))continue;if(found>=page*12&&found<(page+1)*12)out.add(new VelaHub.Hit(VelaWorkspace.t("Channel","Canal"),ch.name,VelaWorkspace.t("Antenna","Antena"),ch));found++;}out.more=found>(page+1)*12;return out;
    }
    public List<VelaShows.Airing> airings(String query,int page,CancellationSignal signal)throws Exception{
        LiteAntennaData data=new LiteAntennaData(activity);if(!data.permitted())return Collections.emptyList();Map<Long,String> names=new HashMap<>();for(LiveTvData.Channel ch:LiteChannelPersonal.visible(activity,data.channels(signal)))if(!ch.locked)names.put(ch.providerId,ch.name);return VelaToolsData.merge(VelaToolsData.nativeAirings(activity,names,query,page,signal),new LiteXmlTvGuide(activity).airings(names,query,page,signal));
    }
    public List<VelaShows.Airing> discover(int mode,int genre,int page,CancellationSignal signal)throws Exception{LiteAntennaData data=new LiteAntennaData(activity);if(!data.permitted())return Collections.emptyList();Map<Long,String> names=new HashMap<>();for(LiveTvData.Channel c:LiteChannelPersonal.visible(activity,data.channels(signal)))if(!c.locked)names.put(c.providerId,c.name);List<VelaShows.Airing> cached=new LiteXmlTvGuide(activity).discover(names,mode,genre,page,signal);return !cached.isEmpty()?VelaDiscovery.nativeLogos(cached,names):VelaDiscovery.local(activity,names,mode,genre,page,signal);}
    void favorite(LiveTvData.Channel c)throws Exception{List<LiveTvData.Channel> all=new LiteAntennaData(activity).channels(new CancellationSignal());new LiteTvUi.Favourites(activity).toggle(c,all);}
    public void repair(){new LiteXmlTvGuide(activity).invalidate();}
    public String scope(){return LiveTvData.hash(VelaGuideFix.all(activity).toString())+"|antenna:"+VelaWorkspace.prefs(activity,"lite_tv").getBoolean("online_guide",true);}
}
