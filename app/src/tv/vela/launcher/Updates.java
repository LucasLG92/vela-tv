package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** One bounded foreground-triggered job; no service, alarm or permanent polling. */
final class Updates {
    interface Listener {void changed();}
    interface Transport {InputStream open(String url)throws Exception;}
    private interface Job {void run()throws Exception;}
    static final int IDLE=0,CHECKING=1,DOWNLOADING=2,VERIFYING=3;
    private static final long DAY=86400_000L,HOUR=3600_000L;
    private static final ExecutorService worker=Visuals.worker(1);
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final List<WeakReference<Listener>> listeners=new CopyOnWriteArrayList<>();
    private static volatile int state,progress;
    private static volatile boolean cancelled;
    private static volatile String error="";
    private static volatile HttpURLConnection connection;
    static Transport transport=Updates::open;
    private Updates(){}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("updates",Context.MODE_PRIVATE);}
    static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception absent){return "0.0.0";}}
    static boolean automaticEnabled(Context c){return prefs(c).getBoolean("automatic",true);}
    static boolean automaticDownload(Context c){return prefs(c).getBoolean("download",true);}
    static boolean previews(Context c){return prefs(c).getBoolean("previews",version(c).contains("-"));}
    static File apk(Context c){return UpdateFiles.apk(c);}
    private static boolean installedTag(String tag,String installed){String v=tag.startsWith("v")?tag.substring(1):tag;return ReleaseFeed.validVersion(v)&&ReleaseFeed.validVersion(installed)&&ReleaseFeed.compare(v,installed)<=0;}
    // The receiver only removes files; preference reconciliation stays in the :home process.
    static void cleanupInstalled(Context c){
        if(!UpdateFiles.cleanupOnce(c))return;
        SharedPreferences p=prefs(c);String installed=version(c);SharedPreferences.Editor edit=p.edit();boolean stale=false;
        try{if(installedTag(new JSONObject(p.getString("release","{}")).optString("tag_name"),installed)){edit.remove("release");stale=true;}}catch(JSONException ignored){}
        if(installedTag(p.getString("downloaded",""),installed)){edit.remove("downloaded");stale=true;}
        if(installedTag(p.getString("installRequest","").split("\n",2)[0],installed)){edit.remove("installRequest").remove("installRequestedAt");stale=true;}
        if(stale)edit.apply();
    }
    static int state(){return state;}
    static int progress(){return progress;}
    static String error(){return error;}
    static void observe(Listener listener){forget(listener);listeners.add(new WeakReference<>(listener));}
    static void forget(Listener listener){for(WeakReference<Listener> item:listeners)if(item.get()==null||item.get()==listener)listeners.remove(item);}
    private static void changed(){main.post(()->{for(WeakReference<Listener> item:listeners){Listener listener=item.get();if(listener==null)listeners.remove(item);else listener.changed();}});}
    static ReleaseFeed.Release available(Context c){try{return ReleaseFeed.newest(new JSONArray().put(new JSONObject(prefs(c).getString("release","{}"))),version(c),previews(c));}catch(Exception absent){return null;}}
    static boolean ready(Context c,ReleaseFeed.Release release){return release!=null&&release.tag.equals(prefs(c).getString("downloaded",""))&&apk(c).isFile()&&apk(c).length()==release.size;}
    // Settings can destroy the caller. Persist only an explicit, short-lived install request,
    // bound to the exact downloaded release; automatic downloads never create one.
    private static String identity(ReleaseFeed.Release release){return release.tag+"\n"+release.sha256;}
    static boolean requestInstallation(Context c){
        ReleaseFeed.Release release=available(c);return ready(c,release)&&prefs(c).edit().putString("installRequest",identity(release)).putLong("installRequestedAt",System.currentTimeMillis()).commit();
    }
    static boolean installationRequested(Context c){
        SharedPreferences p=prefs(c);String requested=p.getString("installRequest","");if(requested.isEmpty())return false;
        ReleaseFeed.Release release=available(c);long age=System.currentTimeMillis()-p.getLong("installRequestedAt",0);
        if(age>=0&&age<HOUR&&ready(c,release)&&requested.equals(identity(release)))return true;
        clearInstallationRequest(c);return false;
    }
    static boolean clearInstallationRequest(Context c){return prefs(c).edit().remove("installRequest").remove("installRequestedAt").commit();}
    static boolean resumeInstallation(Activity activity){
        cleanupInstalled(activity);
        if(!installationRequested(activity)||!activity.getPackageManager().canRequestPackageInstalls())return false;
        activity.startActivity(new Intent(activity,UpdatesActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));return true;
    }
    private static synchronized boolean begin(int next,Job job){
        if(state!=IDLE)return false;state=next;progress=0;error="";cancelled=false;changed();
        worker.execute(()->{try{job.run();}catch(Exception failure){if(!cancelled)error=Ui.tr("Não foi possível verificar a atualização. Tente novamente.","Could not verify the update. Please try again.","No se ha podido verificar la actualización. Vuelve a intentarlo.");}finally{HttpURLConnection current=connection;if(current!=null)current.disconnect();connection=null;state=IDLE;changed();}});return true;
    }
    static void automatic(Context context){
        Context c=context.getApplicationContext();cleanupInstalled(c);if(!automaticEnabled(c))return;SharedPreferences p=prefs(c);long now=System.currentTimeMillis();
        long checked=p.getLong("checked",0),attempt=p.getLong("attempt",0);
        if((now<checked||now-checked>=DAY)&&(now<attempt||now-attempt>=HOUR))check(c);
        else {ReleaseFeed.Release release=available(c);long last=p.getLong("downloadAttempt",0);if(automaticDownload(c)&&release!=null&&!ready(c,release)&&(now<last||now-last>=HOUR))download(c);}
    }
    static void check(Context context){
        Context c=context.getApplicationContext();begin(CHECKING,()->{
            prefs(c).edit().putLong("attempt",System.currentTimeMillis()).apply();
            byte[] bytes;try(InputStream in=transport.open(ReleaseFeed.API);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out,1024*1024,-1);bytes=out.toByteArray();}
            ReleaseFeed.Release release=ReleaseFeed.newest(new JSONArray(new String(bytes,"UTF-8")),version(c),previews(c));
            SharedPreferences.Editor edit=prefs(c).edit().putLong("checked",System.currentTimeMillis());if(release==null)edit.remove("release");else edit.putString("release",release.json().toString());if(!edit.commit())throw new IOException("Cannot save release");
            if(release==null){apk(c).delete();prefs(c).edit().remove("downloaded").apply();}
            else if(automaticDownload(c)&&!ready(c,release))downloadInside(c,release);
        });
    }
    static void download(Context context){Context c=context.getApplicationContext();ReleaseFeed.Release release=available(c);if(release!=null)begin(DOWNLOADING,()->downloadInside(c,release));}
    private static void downloadInside(Context c,ReleaseFeed.Release release)throws Exception{
        try(UpdateFiles.Lease lease=UpdateFiles.acquire(c,true)){
        state=DOWNLOADING;progress=0;changed();prefs(c).edit().putLong("downloadAttempt",System.currentTimeMillis()).remove("downloaded").apply();
        File target=apk(c),part=UpdateFiles.partial(c);
        try{
            try(InputStream in=transport.open(release.url);FileOutputStream out=new FileOutputStream(part)){copy(in,out,ReleaseFeed.MAX_APK,release.size);out.getFD().sync();}
            verify(c,part,release);checkCancelled();if(target.exists()&&!target.delete())throw new IOException();if(!part.renameTo(target))throw new IOException();
            if(!prefs(c).edit().putString("downloaded",release.tag).commit())throw new IOException("Cannot save download");
        }finally{part.delete();}
        }
    }
    static void verify(Context c,File file,ReleaseFeed.Release release)throws Exception{
        if(file.length()!=release.size)throw new IOException("Size mismatch");MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] block=new byte[8192];int count;while((count=in.read(block))!=-1){checkCancelled();hash.update(block,0,count);}}
        StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(Locale.ROOT,"%02x",b&255));if(!digest.toString().equals(release.sha256))throw new IOException("Hash mismatch");
        PackageManager pm=c.getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNING_CERTIFICATES),installed=pm.getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive==null||!c.getPackageName().equals(archive.packageName)||archive.getLongVersionCode()<=installed.getLongVersionCode()||!release.version.equals(archive.versionName)||archive.signingInfo==null||installed.signingInfo==null)throw new IOException("Package mismatch");
        Set<Signature> before=new HashSet<>(Arrays.asList(installed.signingInfo.getApkContentsSigners())),after=new HashSet<>(Arrays.asList(archive.signingInfo.getApkContentsSigners()));
        if(before.isEmpty()||!before.equals(after))throw new IOException("Signer mismatch");
    }
    static Intent installIntent(Context c){Uri uri=Uri.parse("content://"+c.getPackageName()+".updates/update.apk");Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);intent.setClipData(ClipData.newRawUri("Vela update",uri));return intent;}
    static void install(Activity activity){
        Context c=activity.getApplicationContext();ReleaseFeed.Release release=available(c);if(!installationRequested(c))return;WeakReference<Activity> owner=new WeakReference<>(activity);
        begin(VERIFYING,()->{
            try{verify(c,apk(c),release);}catch(Exception invalid){clearInstallationRequest(c);throw invalid;}
            main.post(()->{
                Activity target=owner.get();if(target==null||target.isFinishing()||target.isDestroyed()||(target instanceof UpdatesActivity&&!((UpdatesActivity)target).canShowInstaller()))return;
                if(!installationRequested(c))return;
                if(!clearInstallationRequest(c)){error=Ui.tr("Não foi possível preparar a instalação. Tente novamente.","Could not prepare installation. Please try again.","No se ha podido preparar la instalación. Vuelve a intentarlo.");changed();return;}
                try{target.startActivityForResult(installIntent(target),902);}catch(ActivityNotFoundException|SecurityException missing){error=Ui.tr("Não foi possível abrir o instalador. Verifique a permissão de instalação e tente novamente.","Could not open the installer. Check install permission and try again.","No se ha podido abrir el instalador. Comprueba el permiso de instalación e inténtalo de nuevo.");changed();}
            });
        });
    }
    static void cancel(){cancelled=true;HttpURLConnection current=connection;if(current!=null)current.disconnect();}
    private static void checkCancelled()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    private static void copy(InputStream in,OutputStream out,long limit,long expected)throws Exception{
        byte[] block=new byte[8192];long total=0,start=SystemClock.elapsedRealtime(),notified=0;int count;
        while((count=in.read(block))!=-1){checkCancelled();if(SystemClock.elapsedRealtime()-start>120_000||total+count>limit||(expected>0&&total+count>expected))throw new IOException("Transfer limit");out.write(block,0,count);total+=count;
            if(expected>0&&SystemClock.elapsedRealtime()-notified>=200){progress=(int)(total*100/expected);notified=SystemClock.elapsedRealtime();changed();}}
        if(expected>0&&total!=expected)throw new IOException("Incomplete download");
    }
    static boolean allowed(String address){
        try{URL url=new URL(address);if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))return false;
            String host=url.getHost();if(host.equals("api.github.com"))return address.equals(ReleaseFeed.API);
            if(host.equals("github.com"))return url.getPath().startsWith("/LucasLG92/vela-tv/releases/download/");
            return host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")||host.equals("github-releases.githubusercontent.com");
        }catch(Exception invalid){return false;}
    }
    private static InputStream open(String address)throws Exception{
        for(int redirect=0;redirect<6;redirect++){
            checkCancelled();if(!allowed(address))throw new IOException("Untrusted update origin");HttpURLConnection current=(HttpURLConnection)new URL(address).openConnection();connection=current;
            current.setConnectTimeout(8000);current.setReadTimeout(10000);current.setInstanceFollowRedirects(false);current.setRequestProperty("User-Agent","Vela-Updater");current.setRequestProperty("Accept",address.equals(ReleaseFeed.API)?"application/vnd.github+json":"application/octet-stream");
            try{int status=current.getResponseCode();if(status>=300&&status<400){String location=current.getHeaderField("Location");current.disconnect();if(location==null)throw new IOException();address=new URL(new URL(address),location).toString();continue;}
            if(status!=200){current.disconnect();throw new IOException("Update request unavailable");}
            return new FilterInputStream(current.getInputStream()){@Override public void close()throws IOException{try{super.close();}finally{current.disconnect();}}};}catch(Exception failure){current.disconnect();throw failure;}
        }
        throw new IOException("Too many update redirects");
    }
}
