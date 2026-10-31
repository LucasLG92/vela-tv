package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.os.Build;

/** Device-level mode. Changing it never deletes a profile or linked account. */
final class VelaEdition {
    private VelaEdition(){}
    static boolean unified(Context c){return !c.getPackageName().endsWith(".lite");}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("vela-edition",0);}
    static boolean fullAvailable(){return Build.VERSION.SDK_INT>=30;}
    static String mode(Context c){
        if(!unified(c))return "lite";
        String saved=prefs(c).getString("mode","");if(saved.equals("lite")||saved.equals("full")&&fullAvailable())return saved;
        // Keep configured Full installs on their original mode during an upgrade.
        if(fullAvailable()&&c.getSharedPreferences("personalization",0).getBoolean("initialized",false)){prefs(c).edit().putString("mode","full").commit();screensaver(c,true);return "full";}
        return "";
    }
    static boolean lite(Context c){return mode(c).equals("lite");}
    static void select(Context c,String mode){if(VelaSessions.blocked(c))throw new IllegalStateException("Settings locked");if(!mode.equals("lite")&&!(mode.equals("full")&&fullAvailable()))throw new IllegalArgumentException("Unsupported mode");if(!prefs(c).edit().putString("mode",mode).commit())throw new IllegalStateException("Could not save mode");screensaver(c,mode.equals("full"));}
    private static void screensaver(Context c,boolean enabled){if(!unified(c))return;try{c.getPackageManager().setComponentEnabledSetting(new ComponentName(c.getPackageName(),"tv.vela.launcher.Screensaver"),enabled?android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED:android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,android.content.pm.PackageManager.DONT_KILL_APP);}catch(RuntimeException ignored){}}
    static String home(Context c){return lite(c)?"tv.vela.launcher.LiteHomeActivity":"tv.vela.launcher.HomeActivity";}
    static boolean route(Activity a,String expected){
        if(!unified(a))return false;String chosen=mode(a);if(chosen.equals(expected))return false;
        Intent next=new Intent(a.getIntent());next.setClassName(a.getPackageName(),chosen.isEmpty()?"tv.vela.launcher.EditionActivity":home(a));next.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);a.startActivity(next);a.finish();return true;
    }
    static void chooser(Context c){c.startActivity(new Intent().setClassName(c.getPackageName(),"tv.vela.launcher.EditionActivity").putExtra("changeMode",true));}
    static void legacyUpgrade(Context c){
        Intent next=new Intent().setClassName("tv.vela.launcher","tv.vela.launcher.EditionActivity").putExtra("importLegacy",true);
        try{c.startActivity(next);}catch(ActivityNotFoundException missing){try{c.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/LucasLG92/vela-tv/releases/tag/v2.0.0-preview")));}catch(ActivityNotFoundException unavailable){android.widget.Toast.makeText(c,L10n.text("Open GitHub on another device to install unified Vela."),android.widget.Toast.LENGTH_LONG).show();}}
    }
}
