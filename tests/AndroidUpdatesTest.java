package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Real signed APK verification, bounded transfers, private provider and TV controls. */
public class AndroidUpdatesTest extends Instrumentation {
    private void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private void ui(Runnable job){runOnMainSync(job);waitForIdleSync();}
    private void idle()throws Exception{long end=SystemClock.uptimeMillis()+15_000;while(Updates.state()!=Updates.IDLE&&SystemClock.uptimeMillis()<end)Thread.sleep(20);waitForIdleSync();check(Updates.state()==Updates.IDLE,"Worker stuck");}
    private void rejected(Context c,File file,ReleaseFeed.Release release)throws Exception{boolean failed=false;try{Updates.verify(c,file,release);}catch(Exception expected){failed=true;}check(failed,"Invalid APK accepted: "+file.getName());}
    private String hash(File file)throws Exception{MessageDigest h=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)h.update(block,0,n);}StringBuilder s=new StringBuilder();for(byte b:h.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private File asset(Context c,String name)throws Exception{File target=new File(c.getCacheDir(),name);try(InputStream in=getContext().getAssets().open(name);OutputStream out=new FileOutputStream(target)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return target;}
    private ReleaseFeed.Release release(File file,String version)throws Exception{return new ReleaseFeed.Release("v"+version,version,ReleaseFeed.REPO+"/releases/download/v"+version+"/Vela-"+version+".apk",hash(file),file.length(),true);}
    private void restore(SharedPreferences p,Map<String,?> map){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:map.entrySet()){Object v=item.getValue();if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof String)e.putString(item.getKey(),(String)v);}e.commit();}
    private TextView control(UpdatesActivity a,String name)throws Exception{Field f=UpdatesActivity.class.getDeclaredField(name);f.setAccessible(true);return (TextView)f.get(a);}
    private UpdatesActivity recreate(UpdatesActivity a)throws Exception{ActivityMonitor monitor=addMonitor(UpdatesActivity.class.getName(),null,false);ui(()->a.recreate());Activity next=monitor.waitForActivityWithTimeout(8000);removeMonitor(monitor);check(next instanceof UpdatesActivity&&next!=a,"Activity was not recreated");waitForIdleSync();return (UpdatesActivity)next;}
    private void hits(ActivityMonitor monitor,int count)throws Exception{long end=SystemClock.uptimeMillis()+8000;while(monitor.getHits()<count&&SystemClock.uptimeMillis()<end)Thread.sleep(20);idle();check(monitor.getHits()==count,"Expected installer launches "+count+", got "+monitor.getHits());}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Context c=getTargetContext();Bundle result=new Bundle();UpdatesActivity activity=null;Updates.Transport original=Updates.transport;SharedPreferences p=Updates.prefs(c);Map<String,?> before=new HashMap<>(p.getAll());File future=null,wrongSigner=null,wrongPackage=null;ActivityMonitor installer=null;
        try{
            check(android.os.Build.MODEL.contains("sdk")||android.os.Build.FINGERPRINT.contains("generic"),"Run only on a clean emulator");check(!new PlexClient(c).connected(),"A private Plex account is linked");Locale.setDefault(new Locale("es","ES"));
            Map<String,?> plex=new HashMap<>(new PlexClient(c).prefs.getAll()),visuals=new HashMap<>(Visuals.prefs(c).getAll()),setup=new HashMap<>(Personalization.prefs(c).getAll());String[] apps=SidebarApps.slots(c);
            idle();p.edit().clear().commit();check(Updates.automaticEnabled(c)&&Updates.automaticDownload(c),"Fresh installs must check and download automatically");
            p.edit().putBoolean("automatic",false).putBoolean("download",false).putBoolean("previews",true).commit();check(!Updates.automaticEnabled(c)&&!Updates.automaticDownload(c),"Explicit opt-outs must override defaults");
            future=asset(c,"future.apk");wrongSigner=asset(c,"wrong-signer.apk");wrongPackage=asset(c,"wrong-package.apk");String version;try(InputStream in=getContext().getAssets().open("version.txt")){byte[] b=new byte[96];version=new String(b,0,in.read(b),"UTF-8");}
            ReleaseFeed.Release release=release(future,version);Updates.verify(c,future,release);
            rejected(c,wrongSigner,release(wrongSigner,version));rejected(c,wrongPackage,release(wrongPackage,version));File installed=new File(c.getApplicationInfo().sourceDir);rejected(c,installed,release(installed,Updates.version(c)));
            rejected(c,future,new ReleaseFeed.Release(release.tag,release.version,release.url,new String(new char[64]).replace('\0','0'),release.size,true));
            check(Updates.allowed(ReleaseFeed.API)&&Updates.allowed(release.url)&&Updates.allowed("https://release-assets.githubusercontent.com/example"),"Official endpoints blocked");
            for(String url:new String[]{"http://github.com/LucasLG92/vela-tv/releases/download/x/a.apk","https://github.com/evil/vela-tv/releases/download/a.apk","https://github.com.evil.example/a","https://user:pass@github.com/LucasLG92/vela-tv/releases/download/x/a","https://objects.githubusercontent.com:444/a"})check(!Updates.allowed(url),"Unsafe redirect allowed");
            byte[] feed=new JSONArray().put(release.json()).toString().getBytes("UTF-8");AtomicInteger requests=new AtomicInteger();Updates.transport=url->{requests.incrementAndGet();return new ByteArrayInputStream(feed);};
            Updates.automatic(c);check(requests.get()==0,"Opt-out ignored");Updates.check(c);idle();check(Updates.available(c).version.equals(version),"New release missing");
            p.edit().putBoolean("automatic",true).commit();Updates.automatic(c);check(requests.get()==1,"Repeated within daily interval");
            p.edit().putLong("checked",System.currentTimeMillis()-86_400_001L).putLong("attempt",0).commit();Updates.automatic(c);idle();check(requests.get()==2,"Daily check never retried");p.edit().putBoolean("automatic",false).commit();
            Updates.transport=url->new ByteArrayInputStream(new byte[32]);Updates.download(c);idle();check(!Updates.ready(c,release)&&!Updates.error().isEmpty(),"Truncated download accepted");check(!new File(Updates.apk(c).getParentFile(),"incoming.part").exists(),"Partial download leaked");
            Updates.transport=url->new ByteArrayInputStream(new byte[(int)release.size+1]);Updates.download(c);idle();check(!Updates.ready(c,release),"Oversized download accepted");
            Updates.transport=url->new InputStream(){public int read()throws IOException{try{Thread.sleep(5);}catch(InterruptedException e){throw new IOException(e);}return 0;}public int read(byte[] b,int offset,int count)throws IOException{read();b[offset]=0;return 1;}};Updates.download(c);Thread.sleep(40);Updates.cancel();idle();check(!Updates.ready(c,release)&&!new File(Updates.apk(c).getParentFile(),"incoming.part").exists(),"Cancelled transfer left an installable APK");
            File valid=future;Updates.transport=url->new FileInputStream(valid);Updates.download(c);idle();check(Updates.ready(c,release)&&Updates.error().isEmpty(),"Valid signed download rejected");
            // Cleanup may run in the receiver process: never touch a pending APK or a writer's partial.
            File untouched=new File(c.getCacheDir(),"not-an-update.apk");try(OutputStream out=new FileOutputStream(untouched)){out.write(7);}
            File partial=UpdateFiles.partial(c);try(OutputStream out=new FileOutputStream(partial)){out.write(1);}
            try(UpdateFiles.Lease lease=UpdateFiles.acquire(c,true)){check(!UpdateFiles.cleanup(c)&&partial.isFile()&&Updates.apk(c).isFile(),"Cleanup touched locked download");}
            check(UpdateFiles.cleanup(c)&&!partial.exists()&&Updates.ready(c,release)&&untouched.isFile(),"Cleanup removed future APK/unrelated file or left orphan partial");untouched.delete();
            // The current/older installed APK is obsolete even if release metadata or internet is absent.
            File staged=new File(c.getCacheDir(),"pending-fixture.apk");check(Updates.apk(c).renameTo(staged),"Cannot save pending fixture");
            try(InputStream in=new FileInputStream(installed);OutputStream out=new FileOutputStream(Updates.apk(c))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            check(UpdateFiles.cleanup(c)&&!Updates.apk(c).exists(),"Already installed APK was retained");check(staged.renameTo(Updates.apk(c)),"Cannot restore pending fixture");
            Uri uri=Updates.installIntent(c).getData();try(ParcelFileDescriptor descriptor=c.getContentResolver().openFileDescriptor(uri,"r")){check(descriptor!=null&&descriptor.getStatSize()==release.size,"Installer cannot read private APK");}
            boolean denied=false;try{c.getContentResolver().openFileDescriptor(uri,"rw");}catch(Exception expected){denied=true;}check(denied,"APK provider permits writing");denied=false;try{c.getContentResolver().openFileDescriptor(Uri.parse("content://"+c.getPackageName()+".updates/../shared_prefs/plex.xml"),"r");}catch(Exception expected){denied=true;}check(denied,"Provider allows traversal");
            ProviderInfo provider=c.getPackageManager().resolveContentProvider(c.getPackageName()+".updates",0);check(provider!=null&&!provider.exported&&provider.grantUriPermissions,"Provider exported or installer grant missing");Intent intent=Updates.installIntent(c);check((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&intent.getClipData()!=null&&intent.resolveActivity(c.getPackageManager())!=null,"System installer intent/grant unavailable");
            // Exercise the actual default flow: one metadata request, one verified download, no installer.
            Updates.apk(c).delete();requests.set(0);Updates.transport=url->{requests.incrementAndGet();return url.equals(ReleaseFeed.API)?new ByteArrayInputStream(feed):new FileInputStream(valid);};
            p.edit().remove("download").remove("downloaded").putBoolean("automatic",true).putLong("checked",0).putLong("attempt",0).commit();Updates.automatic(c);idle();check(requests.get()==2&&Updates.ready(c,release),"Default automatic check did not prepare a verified APK");
            Updates.automatic(c);check(requests.get()==2,"Prepared update was downloaded again");
            Updates.apk(c).delete();p.edit().putBoolean("download",false).remove("downloaded").putLong("checked",0).putLong("attempt",0).commit();Updates.automatic(c);idle();check(requests.get()==3&&!Updates.ready(c,release)&&!Updates.automaticDownload(c),"Explicit download opt-out was reset or ignored");p.edit().putBoolean("automatic",false).commit();
            activity=(UpdatesActivity)startActivitySync(new Intent(c,UpdatesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));UpdatesActivity a=activity;waitForIdleSync();Thread.sleep(500);
            Field f=UpdatesActivity.class.getDeclaredField("automatic");f.setAccessible(true);TextView automatic=(TextView)f.get(a);ui(()->automatic.requestFocus());sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check(Updates.automaticEnabled(c),"Remote toggle failed");ui(()->automatic.performClick());check(!Updates.automaticEnabled(c),"Remote toggle did not persist");
            Field df=UpdatesActivity.class.getDeclaredField("download");df.setAccessible(true);TextView download=(TextView)df.get(a);ui(()->download.requestFocus());sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check(Updates.automaticDownload(c),"Remote could not enable automatic downloads");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check(!Updates.automaticDownload(c),"Remote could not disable automatic downloads");
            // System screens are intercepted so this suite never installs the synthetic APK.
            // Recreating the real activity verifies that no saved Bundle is needed to resume.
            Updates.transport=url->new FileInputStream(valid);Updates.download(c);idle();check(Updates.ready(c,release)&&!Updates.installationRequested(c),"A download created an install request");
            check(Updates.requestInstallation(c)&&Updates.installationRequested(c),"Explicit install request not saved");
            p.edit().putLong("installRequestedAt",System.currentTimeMillis()-3_600_001L).commit();check(!Updates.installationRequested(c),"Expired install request accepted");
            Updates.requestInstallation(c);p.edit().putLong("installRequestedAt",System.currentTimeMillis()+60_000L).commit();check(!Updates.installationRequested(c),"Future-dated request accepted");
            Updates.requestInstallation(c);p.edit().putString("installRequest",release.tag+"\n"+new String(new char[64]).replace('\0','0')).commit();check(!Updates.installationRequested(c),"Request for different APK accepted");
            Updates.requestInstallation(c);p.edit().remove("downloaded").commit();check(!Updates.installationRequested(c),"Missing download accepted");p.edit().putString("downloaded",release.tag).commit();
            IntentFilter installFilter=new IntentFilter(Intent.ACTION_VIEW);installFilter.addDataType("application/vnd.android.package-archive");installFilter.addDataScheme("content");installer=addMonitor(installFilter,new ActivityResult(Activity.RESULT_CANCELED,null),true);
            check(c.getPackageManager().canRequestPackageInstalls(),"Runner must grant fixture install permission before instrumentation starts");
            Updates.requestInstallation(c);activity=recreate(activity);hits(installer,1);check(!Updates.installationRequested(c),"Install request not consumed before handing off to Android");
            activity=recreate(activity);idle();check(installer.getHits()==1,"Cancelled installation reopened automatically");
            UpdatesActivity retry=activity;ui(()->{try{control(retry,"action").performClick();}catch(Exception e){throw new RuntimeException(e);}});hits(installer,2);check(UpdateFiles.cleanup(c)&&Updates.ready(c,release),"Cleanup discarded cancelled installation instead of allowing retry");
            // Same-sized tampering must still fail after permission: ready() alone is not enough.
            try(RandomAccessFile file=new RandomAccessFile(Updates.apk(c),"rw")){file.seek(128);file.write(file.read()^0xff);}
            Updates.requestInstallation(c);activity=recreate(activity);idle();check(installer.getHits()==2&&!Updates.installationRequested(c)&&!Updates.error().isEmpty(),"Tampered APK resumed into installer");
            check(plex.equals(new PlexClient(c).prefs.getAll())&&visuals.equals(Visuals.prefs(c).getAll())&&setup.equals(Personalization.prefs(c).getAll())&&Arrays.equals(apps,SidebarApps.slots(c)),"Updater changed accounts, appearance or apps");
            result.putString("result","PASS real signed future APK accepted; wrong signer, package, downgrade, hash, truncation, oversize and cancellation rejected. Default automatic check/download, explicit opt-outs, daily limits, private read-only installer grant and remote controls verified. Explicit install survives activity recreation after permission; expiry, changed APK, cancellation, retry, duplicate launch and post-permission tampering checked. No system installation confirmed. Accounts, appearance, setup and apps preserved.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{if(installer!=null)removeMonitor(installer);if(activity!=null){UpdatesActivity a=activity;ui(()->a.finish());}Updates.cancel();try{idle();}catch(Exception ignored){}Updates.transport=original;restore(p,before);for(File file:new File[]{future,wrongSigner,wrongPackage,Updates.apk(c)})if(file!=null)file.delete();}
        finish(result.containsKey("error")?1:-1,result);
    }
}
