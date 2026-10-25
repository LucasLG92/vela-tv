package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Native settings captures on an owned, clean emulator. No accounts, location, tuning or update requests. */
public final class LiteSettingsGalleryTest extends Instrumentation {
    private Home activity;
    public static final class Home extends LiteHomeActivity {protected boolean allowAutomaticChecks(){return false;}}
    public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return name.equals(LiteHomeActivity.class.getName())?new Home():super.newActivity(loader,name,intent);}
    Object field(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    void key(int key){sendKeyDownUpSync(key);waitForIdleSync();}
    void shot(String name)throws Exception{Thread.sleep(250);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null&&bitmap.getWidth()==3840&&bitmap.getHeight()==2160,"Native 4K screenshot required");try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"lite-settings-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    void restore(SharedPreferences prefs,Map<String,?> saved){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();Map<SharedPreferences,Map<String,?>> saved=new LinkedHashMap<>();try{
        check(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"),"Owned emulator required");check(c.getPackageName().equals("tv.vela.launcher.lite"),"Wrong target package");
        for(SharedPreferences prefs:new SharedPreferences[]{c.getSharedPreferences("lite_home",0),c.getSharedPreferences("lite_tv",0),LiteUpdateManager.prefs(c)}){saved.put(prefs,new HashMap<>(prefs.getAll()));prefs.edit().clear().commit();}
        activity=(Home)startActivitySync(new Intent(c,LiteHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));View[] nav=(View[])field(activity,"navigation");ui(nav[3]::requestFocus);key(KeyEvent.KEYCODE_DPAD_RIGHT);Object settings=field(activity,"settingsView");check(settings!=null,"Settings not integrated");View[] categories=(View[])field(settings,"categories");
        int[] pages={0,1,2,3,4};String[] files={"04-ajustes","06-aspecto","05-actualizaciones","07-sistema","08-informacion"};
        for(int i=0;i<pages.length;i++){final View category=categories[pages[i]];ui(category::requestFocus);check(category.hasFocus(),"Category focus missing");shot(files[i]);key(KeyEvent.KEYCODE_DPAD_RIGHT);check(!category.hasFocus(),"Options could not be reached");key(KeyEvent.KEYCODE_DPAD_LEFT);check(category.hasFocus(),"Left skipped category");}
        key(KeyEvent.KEYCODE_DPAD_DOWN);check(categories[4].hasFocus(),"Category navigation overflowed");result.putString("result","PASS five native 4K settings captures, category/options remote navigation and bottom boundary. No account, network, TV provider or system changes.");
    }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}finally{if(activity!=null)ui(activity::finish);for(Map.Entry<SharedPreferences,Map<String,?>> entry:saved.entrySet())restore(entry.getKey(),entry.getValue());}finish(result.containsKey("error")?1:-1,result);}
}
