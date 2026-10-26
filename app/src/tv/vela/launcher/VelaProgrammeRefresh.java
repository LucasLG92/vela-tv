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
        if(closed||job!=null&&!job.isDone()||VelaShows.rules(context).isEmpty())return;
        File file=new File(context.getCacheDir(),context.getPackageName().endsWith(".lite")?"antenna-epg/programmes.db":"tv-guide/xmltv.db");SharedPreferences prefs=VelaShows.prefs(context);String scope=provider.scope();if(!scope.equals(prefs.getString("scope",""))){VelaShows.invalidate(context);prefs.edit().putString("scope",scope).commit();}long now=System.currentTimeMillis(),last=prefs.getLong("scan",0);if(now-last>=0&&now-last<15*60_000&&prefs.getLong("guide",0)==file.lastModified())return;
        String profile=VelaWorkspace.active(context);List<String> rules=VelaShows.rules(context);signal=new CancellationSignal();CancellationSignal token=signal;
        job=worker.submit(()->{try{List<VelaShows.Airing> airings=new ArrayList<>();for(String title:rules){token.throwIfCanceled();for(VelaShows.Airing p:provider.airings(title,0,token))if(VelaShows.identity(title).equals(VelaShows.identity(p.title))&&airings.size()<VelaShows.MAX_AIRINGS)airings.add(p);}token.throwIfCanceled();synchronized(VelaWorkspace.class){if(profile.equals(VelaWorkspace.active(context))&&scope.equals(provider.scope())&&!token.isCanceled()){VelaShows.replace(context,airings);prefs.edit().putLong("scan",now).putLong("guide",file.lastModified()).apply();}}}catch(Exception unavailable){/* A stale/offline guide keeps the previous safe reminder schedule. */}});
    }
    void pause(){signal.cancel();if(job!=null)job.cancel(true);}
    void close(){closed=true;pause();worker.shutdownNow();}
}
