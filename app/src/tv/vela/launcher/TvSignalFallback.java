package tv.vela.launcher;

import java.util.*;

/** A playback session only: at most eight variants, four URLs each, no network probes or players. */
final class TvSignalFallback {
    interface Resolver {List<String> streams(LiveTvData.Channel channel)throws Exception;}
    static final int MAX_SIGNALS=TvChannelNames.MAX_VARIANTS*4;
    static final long START_TIMEOUT_MS=20_000,STALL_TIMEOUT_MS=20_000,CHECK_INTERVAL_MS=5_000;
    static final class Signal {
        final LiveTvData.Channel channel;final String url;
        Signal(LiveTvData.Channel channel,String url){this.channel=channel;this.url=url;}
    }
    private final List<LiveTvData.Channel> channels=new ArrayList<>();
    private final Set<String> tried=new HashSet<>();
    private List<String> urls=Collections.emptyList();
    private LiveTvData.Channel current;
    private int variant,index,attempts;
    TvSignalFallback(LiveTvData.Channel selected){
        if(selected==null||selected.antenna())return;
        channels.add(selected);String family=TvChannelNames.family(selected);Set<String> ids=new HashSet<>();ids.add(selected.id);
        for(LiveTvData.Channel ch:TvChannelNames.variants(selected)){
            if(channels.size()>=TvChannelNames.MAX_VARIANTS)break;
            if(!ch.antenna()&&ch.playable()&&family.equals(TvChannelNames.family(ch))&&selected.xtreamAccount.equals(ch.xtreamAccount)&&selected.xtreamId.isEmpty()==ch.xtreamId.isEmpty()&&ids.add(ch.id))channels.add(ch);
        }
        final int quality=height(selected.name);
        channels.subList(1,channels.size()).sort((a,b)->Integer.compare(order(a.name,quality),order(b.name,quality)));
    }
    /** Try the other format of this variant first, then the nearest lower quality. */
    private static int order(String name,int selected){int h=height(name);if(selected==0)return 100-TvChannelNames.rank(name);if(h==selected)return 0;if(h>0&&h<selected)return 10_000-h;if(h==0)return 20_000;return 30_000+h;}
    private static int height(String name){String q=TvChannelNames.quality(name);return q.equals("8K")?4320:q.equals("4K")||q.equals("UHD")?2160:q.equals("FHD")||q.startsWith("1080")?1080:q.equals("HD")||q.startsWith("720")?720:q.equals("SD")?480:0;}
    Signal next(Resolver resolver){
        while(attempts<MAX_SIGNALS){
            if(index>=urls.size()){
                if(variant>=channels.size())return null;
                current=channels.get(variant++);index=0;urls=Collections.emptyList();
                try{List<String> values=resolver.streams(current);if(values!=null)urls=new ArrayList<>(values.subList(0,Math.min(4,values.size())));}catch(Exception unavailable){/* Skip unavailable variants without extra network probes. */}
                if(urls.isEmpty())continue;
            }
            String url=urls.get(index++);if(!LiveTvData.httpUrl(url)||!tried.add(LiveTvData.hash(url)))continue;
            attempts++;return new Signal(current,url);
        }
        return null;
    }
    int attempts(){return attempts;}

    /** Monotonic media position checks, suspended by explicit pause; no frame-by-frame polling. */
    static final class Progress {
        private long position,advancedAt;
        void reset(long now,long value){position=value;advancedAt=now;}
        boolean stalled(long now,long value,boolean paused){if(paused||value!=position){reset(now,value);return false;}return now-advancedAt>=STALL_TIMEOUT_MS;}
    }
}
