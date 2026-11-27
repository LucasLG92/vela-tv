package tv.vela.launcher;

import android.content.*;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** While Vela is visible, occasionally match followed titles against the existing on-disk EPG. */
final class VelaProgrammeRefresh {
    private final Context context;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"vela-programmes");t.setPriority(Thread.MIN_PRIORITY);return t;});
    private Future<?> job;private CancellationSignal signal=new CancellationSignal();private boolean closed;
    VelaProgrammeRefresh(Context c){context=c.getApplicationContext();}
    void check(VelaHub.Host provider){
        if(closed||job!=null&&!job.isDone())return;
        synchronized(VelaWorkspace.class){
            List<String> rules=VelaShows.rules(context);
            if(rules.isEmpty())return;
            String profile=VelaWorkspace.active(context),scope=provider.scope();
            SharedPreferences prefs=VelaShows.prefs(context);
            if(!scope.equals(prefs.getString("scope",""))){
                VelaShows.invalidate(context);
                prefs.edit().putString("scope",scope).commit();
            }
            File guide=new File(context.getCacheDir(),VelaEdition.lite(context)?"antenna-epg/programmes.db":"tv-guide/xmltv.db");
            long guideVersion=guide.lastModified(),now=System.currentTimeMillis(),last=prefs.getLong("scan",0);
            if(now>=last&&now-last<15*60_000&&prefs.getLong("guide",0)==guideVersion)return;
            CancellationSignal token=new CancellationSignal();
            signal=token;
            job=worker.submit(()->{
                try{
                    List<VelaShows.Airing> airings=matching(rules,provider,token);
                    synchronized(VelaWorkspace.class){
                        if(token.isCanceled()||!profile.equals(VelaWorkspace.active(context))
                                ||!scope.equals(provider.scope())||guideVersion!=guide.lastModified()
                                ||!rules.equals(VelaShows.rules(context)))return;
                        VelaShows.replace(context,airings);
                        prefs.edit().putLong("scan",now).putLong("guide",guideVersion).apply();
                    }
                }catch(Exception unavailable){/* Keep the previous schedule until the guide can be read. */}
            });
        }
    }
    static List<VelaShows.Airing> matching(List<String> rules,VelaHub.Host provider,CancellationSignal signal)throws Exception{
        Set<String> series=new HashSet<>();
        for(String rule:rules){
            String key=VelaShows.identity(rule);
            if(!key.isEmpty())series.add(key);
        }
        signal.throwIfCanceled();
        return series.isEmpty()?Collections.emptyList():provider.followedAirings(series,signal);
    }
    void pause(){signal.cancel();if(job!=null)job.cancel(true);}
    void close(){closed=true;pause();worker.shutdownNow();}
}
