package tv.vela.launcher;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Seeds/cleans a disposable fixture; the host tests real Settings and process death. */
public final class AndroidUpdatePermissionTest extends Instrumentation {
    private String phase;
    public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","");start();}
    private void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();
        try{
            check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"),"Clean emulator required");
            check(!new PlexClient(c).connected()&&!new JellyfinClient(c).connected(),"Never run with personal accounts");
            SharedPreferences p=Updates.prefs(c);File backup=new File(c.getCacheDir(),"permission-test-preferences.json");
            if(phase.equals("prepare")){
                File personal=new File(c.getCacheDir(),"permission-test-personalization.json");if(!personal.exists())try(OutputStream out=new FileOutputStream(personal)){out.write(new JSONObject(Personalization.prefs(c).getAll()).toString().getBytes("UTF-8"));}Personalization.complete(c);
                if(!backup.exists())try(OutputStream out=new FileOutputStream(backup)){out.write(new JSONObject(p.getAll()).toString().getBytes("UTF-8"));}
                File apk=Updates.apk(c);apk.getParentFile().mkdirs();
                try(InputStream in=getContext().getAssets().open("future.apk");OutputStream out=new FileOutputStream(apk)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
                MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(apk)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
                String version;try(InputStream in=getContext().getAssets().open("version.txt")){byte[] b=new byte[96];version=new String(b,0,in.read(b),"UTF-8");}
                ReleaseFeed.Release release=new ReleaseFeed.Release("v"+version,version,ReleaseFeed.REPO+"/releases/download/v"+version+"/Vela-"+version+".apk",hash.toString(),apk.length(),true);
                Updates.verify(c,apk,release);check(p.edit().clear().putBoolean("automatic",false).putBoolean("download",false).putBoolean("previews",true).putString("release",release.json().toString()).putString("downloaded",release.tag).commit(),"Cannot save fixture");
            }else if(phase.equals("queue")){
                check(Updates.requestInstallation(c),"Cannot persist explicit request");
            }else if(phase.equals("cleanup")){
                check(backup.isFile(),"Missing backup");byte[] bytes=new byte[(int)backup.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(backup))){in.readFully(bytes);}
                JSONObject previous=new JSONObject(new String(bytes,"UTF-8"));SharedPreferences.Editor edit=p.edit().clear();
                for(Iterator<String> keys=previous.keys();keys.hasNext();){String key=keys.next();Object value=previous.get(key);if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Number)edit.putLong(key,((Number)value).longValue());else if(value instanceof String)edit.putString(key,(String)value);else throw new AssertionError("Unexpected preference type");}
                check(edit.commit(),"Cannot restore preferences");backup.delete();Updates.apk(c).delete();
                File personal=new File(c.getCacheDir(),"permission-test-personalization.json");if(personal.isFile()){byte[] data=new byte[(int)personal.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(personal))){in.readFully(data);}JSONObject old=new JSONObject(new String(data,"UTF-8"));SharedPreferences.Editor restore=Personalization.prefs(c).edit().clear();for(Iterator<String> keys=old.keys();keys.hasNext();){String key=keys.next();Object value=old.get(key);if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);else if(value instanceof Number)restore.putLong(key,((Number)value).longValue());else if(value instanceof String)restore.putString(key,(String)value);}check(restore.commit(),"Cannot restore setup preferences");personal.delete();}
            }else throw new AssertionError("Unknown test phase");
            result.putString("result","PASS permission fixture "+phase);
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finish(result.containsKey("error")?1:-1,result);
    }
}
