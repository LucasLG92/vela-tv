package tv.vela.launcher;

import android.content.Context;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

/** Replay only where the configured source explicitly advertises an archive. */
final class VelaReplay {
    static boolean available(LiveTvData.Channel c){return c!=null&&!c.antenna()&&c.archiveDays>0&&(!c.xtreamId.isEmpty()||!c.replayTemplate.isEmpty());}
    static boolean supports(LiveTvData.Channel c,LiveTvData.Programme p,long now){return available(c)&&p!=null&&p.start<now&&p.end<=now&&p.end>p.start&&p.end-p.start<=12*3600&&p.start>=now-Math.min(7,c.archiveDays)*86400L;}
    static String url(Context context,LiveTvData.Channel c,LiveTvData.Programme p,long now)throws Exception{
        if(!supports(c,p,now))throw new IOException("No provider archive for this programme");long duration=(p.end-p.start+59)/60;
        if(!c.xtreamId.isEmpty()){XtreamClient.Account a=new XtreamClient(context).account();if(!c.xtreamAccount.equals(a.id)||!XtreamClient.id(c.xtreamId))throw new IOException("Account changed");SimpleDateFormat stamp=new SimpleDateFormat("yyyy-MM-dd:HH-mm",Locale.ROOT);stamp.setTimeZone(TimeZone.getTimeZone(a.zone));return a.base+"/timeshift/"+XtreamClient.enc(a.user)+"/"+XtreamClient.enc(a.password)+"/"+duration+"/"+stamp.format(new Date(p.start*1000))+"/"+c.xtreamId+".ts";}
        String template=c.replayTemplate;String start=Long.toString(p.start),end=Long.toString(p.end),seconds=Long.toString(p.end-p.start);String value=template.replace("${start}",start).replace("${end}",end).replace("{utc}",start).replace("{utcend}",end).replace("{lutc}",Long.toString(now)).replace("{start}",start).replace("{end}",end).replace("{duration}",seconds);
        if(value.contains("{")||value.contains("}"))throw new IOException("Unsupported replay template");if(c.streams.isEmpty())throw new IOException();String original=c.streams.get(0);if(c.replayMode.equals("append"))value=original+value;else value=new URI(original).resolve(value).toString();if(!LiveTvData.httpUrl(value))throw new IOException("Invalid replay URL");return value;
    }
}
