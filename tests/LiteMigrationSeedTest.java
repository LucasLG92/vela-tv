package tv.vela.launcher;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;

/** Only a disposable owned emulator; no physical television data is read. */
public final class LiteMigrationSeedTest extends Instrumentation {
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle out=new Bundle();try{
        Context c=getTargetContext();try(ParcelFileDescriptor f=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(f)){if(in.read()!='1')throw new AssertionError("Owned emulator required");}
        if(!c.getPackageName().equals("tv.vela.launcher.lite"))throw new AssertionError("Legacy package required");
        L10n.choose(c,"en");VelaWorkspace.prefs(c,"lite_home").edit().putInt("start_section",2).putBoolean("reduce_motion",true).commit();
        VelaWorkspace.prefs(c,"vela-remote").edit().putInt("voice_key",android.view.KeyEvent.KEYCODE_F6).commit();
        c.getSharedPreferences("plex",0).edit().putString("accountToken","synthetic-never-export").commit();
        VelaGuideFix.set(c,"TDP HD","Teledeporte.es",60);
        String data=VelaWorkspace.snapshot(c).toString();if(data.contains("synthetic-never-export"))throw new AssertionError("Private account entered export");
        out.putString("result","PASS synthetic legacy profile ready; portable export excludes account material.");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finish(out.containsKey("error")?1:-1,out);}
}
