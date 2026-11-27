package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.*;

/** Explicit master/follower pairing; one pull on foreground entry, no permanent service. */
final class VelaSync implements AutoCloseable {
    private final Activity activity;private VelaConnect host;private final Handler main=new Handler(Looper.getMainLooper());private ExecutorService worker;private Future<?> job;private volatile boolean closed;
    VelaSync(Activity a){activity=a;}
    static String key(Context c,String name){return VelaWorkspace.active(c)+"-"+name;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("vela-sync-private",0);}
    static String hash(JSONObject copy){return LiveTvData.hash(canonical(copy.optJSONObject("settings")));}
    private static String canonical(Object value){if(value instanceof JSONObject){JSONObject j=(JSONObject)value;java.util.TreeSet<String> keys=new java.util.TreeSet<>();for(java.util.Iterator<String> it=j.keys();it.hasNext();)keys.add(it.next());StringBuilder out=new StringBuilder("{");for(String k:keys)out.append(JSONObject.quote(k)).append(':').append(canonical(j.opt(k))).append(',');return out.append('}').toString();}if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<a.length();i++)out.append(canonical(a.opt(i))).append(',');return out.append(']').toString();}return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);}
    private static String sharedHash(JSONObject copy)throws JSONException{
        JSONObject settings=copy.getJSONObject("settings"),shared=new JSONObject();
        for(Iterator<String> keys=settings.keys();keys.hasNext();){
            String name=keys.next();
            if(VelaWorkspace.sharedSection(name)&&settings.getJSONObject(name).length()>0)shared.put(name,settings.get(name));
        }
        return LiveTvData.hash(canonical(shared));
    }
    private static void record(Context c,JSONObject local,JSONObject remote)throws Exception{
        boolean saved=prefs(c).edit().putBoolean(key(c,"receive"),true)
                .putString(key(c,"last-local"),hash(local)).putString(key(c,"last-remote"),hash(remote))
                .putString(key(c,"last-shared"),sharedHash(remote)).remove(key(c,"conflict")).commit();
        if(!saved)throw new IOException("Could not save sync settings");
    }
    static void pair(Context c,String link,JSONObject copy)throws Exception{
        synchronized(VelaWorkspace.class){
            VelaWorkspace.apply(c,copy,true);
            VelaConnect.secret(c,key(c,"peer"),link);
            record(c,VelaWorkspace.snapshot(c),copy);
        }
    }
    static void forget(Context c)throws Exception{
        synchronized(VelaWorkspace.class){
            VelaConnect.secret(c,key(c,"peer"),"");
            prefs(c).edit().remove(key(c,"receive")).remove(key(c,"last-local")).remove(key(c,"last-remote"))
                    .remove(key(c,"last-shared")).remove(key(c,"conflict")).commit();
        }
    }
    // Caller holds the workspace lock for the active profile.
    private static boolean receiveChanges(Context c,JSONObject copy)throws Exception{
        SharedPreferences state=prefs(c);
        String sharedKey=key(c,"last-shared"),conflictKey=key(c,"conflict");
        boolean crossEdition=!copy.getString("edition").equals(VelaEdition.lite(c)?"lite":"full");
        String sharedBase=state.getString(sharedKey,null);
        boolean compareShared=crossEdition&&sharedBase!=null;
        String incoming=crossEdition?sharedHash(copy):null;
        String remote=compareShared?incoming:hash(copy);
        String previousRemote=compareShared?sharedBase:state.getString(key(c,"last-remote"),"");
        if(remote.equals(previousRemote)){
            // Older pairings only stored full hashes. An unchanged source gives a safe shared baseline.
            boolean bootstrap=crossEdition&&sharedBase==null;
            if(bootstrap||state.getBoolean(conflictKey,false)){
                SharedPreferences.Editor edit=state.edit().remove(conflictKey);
                if(bootstrap)edit.putString(sharedKey,incoming);
                edit.apply();
            }
            return false;
        }
        JSONObject local=VelaWorkspace.snapshot(c);
        String localShared=crossEdition?sharedHash(local):null;
        String localDigest=compareShared?localShared:hash(local);
        boolean sameSettings=crossEdition?incoming.equals(localShared):
                remote.equals(localDigest)&&canonical(copy.optJSONObject("antenna")).equals(canonical(local.optJSONObject("antenna")));
        if(sameSettings){
            record(c,local,copy);
            return false;
        }
        String previousLocal=compareShared?sharedBase:state.getString(key(c,"last-local"),"");
        if(!previousLocal.equals(localDigest)){
            state.edit().putBoolean(conflictKey,true).apply();
            return false;
        }
        VelaWorkspace.apply(c,copy,true);
        record(c,VelaWorkspace.snapshot(c),copy);
        return true;
    }
    void resume(Runnable updated){
        if(closed)return;if(prefs(activity).getBoolean(key(activity,"host"),false))try{host=new VelaConnect(activity,null,false);}catch(Exception unavailable){prefs(activity).edit().putBoolean(key(activity,"unavailable"),true).apply();}
        if(!prefs(activity).getBoolean(key(activity,"receive"),false))return;
        String profile=VelaWorkspace.active(activity),peerKey=profile+"-peer";
        worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"vela-sync-on-open");t.setPriority(Thread.MIN_PRIORITY);return t;});
        ExecutorService executor=worker;
        job=executor.submit(()->{
            try{
                String link=VelaConnect.secret(activity,peerKey);
                JSONObject copy=VelaConnect.receive(link);
                synchronized(VelaWorkspace.class){
                    if(closed||!profile.equals(VelaWorkspace.active(activity))||Thread.currentThread().isInterrupted())return;
                    if(!prefs(activity).getBoolean(key(activity,"receive"),false)||!link.equals(VelaConnect.secret(activity,peerKey)))return;
                    if(receiveChanges(activity,copy))main.post(()->{if(!closed)updated.run();});
                }
            }catch(Exception offline){/* Keep local settings and retry next foreground visit. */}
            finally{executor.shutdown();}
        });
    }
    public void close(){closed=true;if(host!=null){host.close();host=null;}if(job!=null)job.cancel(true);if(worker!=null)worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
