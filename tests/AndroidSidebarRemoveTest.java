package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real context actions only modify launcher preferences; verifies installed apps and restores user choices. */
public class AndroidSidebarRemoveTest extends Instrumentation {
    private CinemaHomeView home;
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private boolean menu()throws Exception{return field(home,"menuPackage")!=null;}
    private List<View> apps()throws Exception{return (List<View>)field(home,"appButtons");}
    private List<View> fixed()throws Exception{return (List<View>)field(home,"sideButtons");}
    private String[] displayed()throws Exception{return (String[])field(home,"sidePackages");}
    private void focus(String pkg)throws Exception{View button=apps().get(Arrays.asList(displayed()).indexOf(pkg));ui(()->button.requestFocus());}
    private void holdOK()throws Exception{
        long start=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(start,start,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));Thread.sleep(ViewConfiguration.getLongPressTimeout()+160);waitForIdleSync();
        check(menu()&&field(home,"reorderOriginal")==null,"Held OK did not show actions");
        sendKeySync(new KeyEvent(start,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,1));
        sendKeySync(new KeyEvent(start,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));waitForIdleSync();
        check(menu()&&field(home,"reorderOriginal")==null,"Initial held release activated an action");
    }
    private void chooseRemove()throws Exception{key(KeyEvent.KEYCODE_DPAD_RIGHT);check((Integer)field(home,"menuAction")==1&&((View)field(home,"shortcutRemove")).hasFocus(),"Remove action cannot be selected");}
    private boolean tile(View view,String label){if(view.isClickable()&&label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(tile(((ViewGroup)view).getChildAt(i),label))return true;return false;}
    private void installed(Context c,String pkg,String path,boolean enabled)throws Exception{ApplicationInfo info=c.getPackageManager().getApplicationInfo(pkg,0);check(info.sourceDir.equals(path)&&info.enabled==enabled&&HomeApps.launchIntent(c.getPackageManager(),pkg)!=null,"Removing a shortcut changed or uninstalled the app");}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=SidebarApps.prefs(c);Map<String,?> saved=new HashMap<>(prefs.getAll());HomeActivity activity=null;SettingsActivity settings=null;ActivityMonitor monitor=null;
        try{
            SidebarApps.reset(c);for(SidebarApps.Choice choice:SidebarApps.choices(c)){if(SidebarApps.slots(c).length>=10)break;SidebarApps.add(c,choice.pkg);}
            String[] original=SidebarApps.slots(c);String app=original[0];ApplicationInfo info=c.getPackageManager().getApplicationInfo(app,0);String source=info.sourceDir;boolean enabled=info.enabled;
            Intent launch=HomeApps.launchIntent(c.getPackageManager(),app);check(launch!=null&&launch.getComponent()!=null,"Missing monitored app");monitor=addMonitor(launch.getComponent().getClassName(),null,true);
            activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");
            focus(app);holdOK();check(Arrays.equals(original,SidebarApps.slots(c))&&monitor.getHits()==0,"Opening menu changed settings or launched app");
            key(KeyEvent.KEYCODE_BACK);check(!menu()&&Arrays.equals(original,SidebarApps.slots(c))&&apps().get(0).hasFocus(),"Back failed to close actions and restore focus");
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(monitor.getHits()==1,"Short OK no longer launches app");
            focus(app);holdOK();key(KeyEvent.KEYCODE_DPAD_DOWN);check(!menu()&&field(home,"reorderOriginal")!=null&&displayed()[1].equals(app),"Direct hold-and-arrow move gesture was lost");key(KeyEvent.KEYCODE_BACK);check(Arrays.equals(original,SidebarApps.slots(c)),"Canceling direct move changed saved order");
            focus(app);holdOK();chooseRemove();
            Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"No context action screenshot");try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"sidebar-remove-menu.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            key(KeyEvent.KEYCODE_DPAD_CENTER);List<String> expected=new ArrayList<>(Arrays.asList(original));expected.remove(0);
            check(!menu()&&Arrays.equals(expected.toArray(new String[0]),SidebarApps.slots(c))&&Arrays.equals(SidebarApps.slots(c),displayed()),"Removing first shortcut changed another entry or did not refresh home");check(apps().get(0).hasFocus(),"Focus did not move to next shortcut");installed(c,app,source,enabled);
            View all=fixed().get(4);ui(()->all.performClick());check(tile((View)field(home,"overlay"),"Plex"),"Removed app is missing from All Apps");key(KeyEvent.KEYCODE_BACK);
            SidebarApps.add(c,app);ui(()->home.resume());focus(app);holdOK();chooseRemove();key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(Arrays.equals(expected.toArray(new String[0]),SidebarApps.slots(c))&&apps().get(apps().size()-1).hasFocus(),"Removing last shortcut lost order or previous focus");installed(c,app,source,enabled);
            prefs.edit().putString("slots",new org.json.JSONArray(Collections.singletonList(app)).toString()).commit();ui(()->home.resume());focus(app);holdOK();chooseRemove();key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(SidebarApps.slots(c).length==0&&displayed().length==0&&fixed().get(4).hasFocus()&&fixed().get(7).getVisibility()==View.VISIBLE,"Removing only shortcut trapped focus or hid Settings");installed(c,app,source,enabled);
            prefs.edit().putString("slots",new org.json.JSONArray(Arrays.asList(original)).toString()).commit();ui(()->home.resume());focus(app);holdOK();SidebarApps.add(c,"com.vela.fixture.concurrent");String[] changed=SidebarApps.slots(c);chooseRemove();key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(!menu()&&Arrays.equals(changed,SidebarApps.slots(c))&&Arrays.equals(changed,displayed()),"Stale menu removed a different selection");
            focus(app);holdOK();settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(!menu()&&Arrays.equals(changed,SidebarApps.slots(c)),"Pausing with menu open changed shortcuts");
            installed(c,app,source,enabled);check(monitor.getHits()==1,"Context actions accidentally launched app");
            result.putString("result","PASS real long OK/repeat/release, menu cancel, short OK, existing direct arrow move, remove first/last/only shortcut, exact remaining order, next/previous/empty focus, app still installed/enabled/launchable and in All Apps, stale-menu guard, pause cleanup. User selection restored.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            if(home!=null)ui(()->home.pause());SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet())if(entry.getValue() instanceof String)edit.putString(entry.getKey(),(String)entry.getValue());edit.commit();
            if(monitor!=null)removeMonitor(monitor);if(settings!=null){SettingsActivity a=settings;ui(()->a.finish());}if(activity!=null){HomeActivity a=activity;ui(()->a.finish());}
        }
        finish(result.containsKey("error")?1:-1,result);
    }
}
