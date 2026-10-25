package tv.vela.launcher;

import android.app.*;import android.os.*;import java.io.*;import java.util.*;import org.json.*;

/** Explicitly selected, user-authorized TV: configure automatic updates and verify a public download. No system settings or channels are changed. */
public final class LiteReleaseDeviceTest extends Instrumentation {
    private String stage,expected;
    public void onCreate(Bundle args){super.onCreate(args);stage=args.getString("stage","download");expected=args.getString("expected","");start();}
    public void onStart(){Bundle result=new Bundle();JSONObject report=new JSONObject();try{
        android.content.Context c=getTargetContext();if(!c.getPackageName().equals("tv.vela.launcher.lite"))throw new AssertionError("Wrong package");report.put("installed",LiteUpdateManager.version(c));
        if(stage.equals("download")){
            LiteUpdateManager.prefs(c).edit().putBoolean("automatic",true).putBoolean("download",true).remove("checked").remove("attempt").remove("downloadAttempt").commit();
            LiteUpdateManager manager=LiteUpdateManager.get(c);manager.automaticCheck();long deadline=SystemClock.elapsedRealtime()+150000;while(manager.busy()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(150);
            LiteReleaseFeed.Release release=manager.available();if(manager.busy()||release==null||!release.version.equals(expected)||!manager.ready())throw new AssertionError("Public automatic download not ready: "+manager.status());LiteUpdateManager.verify(c,LiteUpdateManager.apk(c),release);
            report.put("detected",release.version).put("tag",release.tag).put("sha256",release.sha256).put("bytes",release.size).put("automatic",manager.automatic()).put("automatic_download",manager.autoDownload());
            try(InputStream in=new FileInputStream(LiteUpdateManager.apk(c));OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"published-update.apk"))){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
        }else if(stage.equals("check")){
            if(!LiteUpdateManager.version(c).equals(expected))throw new AssertionError("Wrong installed release");
            LiteUpdateManager manager=LiteUpdateManager.get(c);long started=System.currentTimeMillis();manager.check();long deadline=SystemClock.elapsedRealtime()+150000;while(manager.busy()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(150);
            if(manager.busy()||LiteUpdateManager.prefs(c).getLong("checked",0)<started)throw new AssertionError("Public release check failed: "+manager.status());
            report.put("public_check",true).put("status",manager.status()).put("automatic",manager.automatic()).put("automatic_download",manager.autoDownload());
        }else{
            if(!LiteUpdateManager.version(c).equals(expected))throw new AssertionError("Published version not installed");boolean exists=new File(c.getFilesDir(),"lite-updates/update.apk").exists();report.put("installed_apk_cleaned",!exists).put("automatic",LiteUpdateManager.prefs(c).getBoolean("automatic",false)).put("automatic_download",LiteUpdateManager.prefs(c).getBoolean("download",false));if(exists)throw new AssertionError("Installed APK was not cleaned on launch");
        }
        try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"release-device-"+stage+".json"))){out.write(report.toString(2).getBytes("UTF-8"));}result.putString("result","PASS public Lite release "+stage);
    }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}finish(result.containsKey("error")?1:-1,result);}
}
