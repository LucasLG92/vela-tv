package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;

/** Explicit master/follower pairing; one pull on foreground entry, no permanent service. */
final class VelaSync implements AutoCloseable {
    private final Activity activity;private VelaConnect host;private final Handler main=new Handler(Looper.getMainLooper());private ExecutorService worker;private Future<?> job;private volatile boolean closed;
    VelaSync(Activity a){activity=a;}
    static String key(Context c,String name){return VelaWorkspace.active(c)+"-"+name;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("vela-sync-private",0);}
    static String hash(JSONObject copy){return LiveTvData.hash(canonical(copy.optJSONObject("settings")));}
    private static String canonical(Object value){if(value instanceof JSONObject){JSONObject j=(JSONObject)value;java.util.TreeSet<String> keys=new java.util.TreeSet<>();for(java.util.Iterator<String> it=j.keys();it.hasNext();)keys.add(it.next());StringBuilder out=new StringBuilder("{");for(String k:keys)out.append(JSONObject.quote(k)).append(':').append(canonical(j.opt(k))).append(',');return out.append('}').toString();}if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<a.length();i++)out.append(canonical(a.opt(i))).append(',');return out.append(']').toString();}return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);}
    static void pair(Context c,String link,JSONObject copy)throws Exception{synchronized(VelaWorkspace.class){VelaWorkspace.apply(c,copy,true);VelaConnect.secret(c,key(c,"peer"),link);prefs(c).edit().putBoolean(key(c,"receive"),true).putString(key(c,"last-local"),hash(VelaWorkspace.snapshot(c))).putString(key(c,"last-remote"),hash(copy)).commit();}}
    static void forget(Context c)throws Exception{VelaConnect.secret(c,key(c,"peer"),"");prefs(c).edit().remove(key(c,"receive")).remove(key(c,"last-local")).remove(key(c,"last-remote")).remove(key(c,"conflict")).commit();}
    void resume(Runnable updated){
        if(closed)return;if(prefs(activity).getBoolean(key(activity,"host"),false))try{host=new VelaConnect(activity,null,false);}catch(Exception unavailable){prefs(activity).edit().putBoolean(key(activity,"unavailable"),true).apply();}
        if(!prefs(activity).getBoolean(key(activity,"receive"),false))return;String profile=VelaWorkspace.active(activity);worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"vela-sync-on-open");t.setPriority(Thread.MIN_PRIORITY);return t;});job=worker.submit(()->{try{String link=VelaConnect.secret(activity,key(activity,"peer"));JSONObject copy=VelaConnect.receive(link);synchronized(VelaWorkspace.class){if(closed||!profile.equals(VelaWorkspace.active(activity))||Thread.currentThread().isInterrupted())return;String remote=hash(copy),local=hash(VelaWorkspace.snapshot(activity));if(remote.equals(prefs(activity).getString(key(activity,"last-remote"),"")))return;if(!local.equals(prefs(activity).getString(key(activity,"last-local"),""))){prefs(activity).edit().putBoolean(key(activity,"conflict"),true).apply();return;}VelaWorkspace.apply(activity,copy,true);prefs(activity).edit().putString(key(activity,"last-remote"),remote).putString(key(activity,"last-local"),hash(VelaWorkspace.snapshot(activity))).putBoolean(key(activity,"conflict"),false).apply();main.post(()->{if(!closed)updated.run();});}}catch(Exception offline){/* Keep local settings and retry next foreground visit. */}finally{ExecutorService w=worker;if(w!=null)w.shutdown();}});
    }
    public void close(){closed=true;if(host!=null){host.close();host=null;}if(job!=null)job.cancel(true);if(worker!=null)worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
