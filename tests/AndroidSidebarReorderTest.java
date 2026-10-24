package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real held OK events and remote moves; restores user choices and intercepts external app launches. */
public class AndroidSidebarReorderTest extends Instrumentation {
    private CinemaHomeView home;
    private Object field(Object owner,String name)throws Exception{if(owner instanceof ScreenActivity)owner=((ScreenActivity)owner).screen;for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private List<FrameLayout> buttons()throws Exception{return (List<FrameLayout>)field(home,"appButtons");}
    private String[] draft()throws Exception{return (String[])field(home,"sidePackages");}
    private boolean editing()throws Exception{return field(home,"reorderOriginal")!=null;}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private int position(String pkg)throws Exception{return Arrays.asList(draft()).indexOf(pkg);}
    private void focus(String pkg)throws Exception{FrameLayout button=buttons().get(position(pkg));ui(()->button.requestFocus());}
    private void holdOK()throws Exception{
        long start=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(start,start,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));
        Thread.sleep(ViewConfiguration.getLongPressTimeout()+180);waitForIdleSync();
        check(field(home,"menuPackage")!=null&&!editing(),"Held OK did not open shortcut actions");
        sendKeySync(new KeyEvent(start,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,1));
        sendKeySync(new KeyEvent(start,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));waitForIdleSync();
        check(field(home,"menuPackage")!=null&&!editing(),"Releasing held OK selected an action");
        key(KeyEvent.KEYCODE_DPAD_CENTER);check(editing(),"Move action did not start reorder");
    }
    private void visible(String pkg)throws Exception{
        Thread.sleep(180);Rect rect=new Rect();FrameLayout button=buttons().get(position(pkg));
        check(button.hasFocus()&&button.getGlobalVisibleRect(rect)&&rect.height()>=50,"Moving app lost focus or scrolled out of view");
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=SidebarApps.prefs(c);
        Map<String,?> saved=new HashMap<>(prefs.getAll());HomeActivity activity=null;SettingsActivity settings=null;ActivityMonitor monitor=null;
        try{
            SidebarApps.reset(c);for(SidebarApps.Choice choice:SidebarApps.choices(c)){if(SidebarApps.slots(c).length>=10)break;SidebarApps.add(c,choice.pkg);}
            String[] original=SidebarApps.slots(c);check(original.length>4,"Need additional installed apps to test scrolling");
            String[] invalid=original.clone();invalid[1]=invalid[0];check(!SidebarApps.reorder(c,original,invalid)&&Arrays.equals(original,SidebarApps.slots(c)),"Invalid permutation changed saved choices");
            String app=original[0];Intent launch=HomeApps.launchIntent(c.getPackageManager(),app);check(launch!=null&&launch.getComponent()!=null,"Missing monitored launch intent");
            monitor=addMonitor(launch.getComponent().getClassName(),null,true);
            activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");
            focus(app);android.graphics.drawable.Drawable icon=((List<ImageView>)field(home,"appIcons")).get(0).getDrawable();holdOK();check(monitor.getHits()==0,"Long press launched app");
            key(KeyEvent.KEYCODE_DPAD_UP);check(position(app)==0,"Move wrapped past first shortcut");
            for(int i=1;i<original.length;i++){key(KeyEvent.KEYCODE_DPAD_DOWN);check(position(app)==i,"Held app did not move one position");check(Arrays.equals(original,SidebarApps.slots(c)),"Draft saved before confirmation");}
            visible(app);check(((ScrollView)field(home,"appScroll")).getScrollY()>0,"Draft did not scroll to last position");
            check(((List<ImageView>)field(home,"appIcons")).get(position(app)).getDrawable()==icon,"Move unnecessarily decoded artwork");
            key(KeyEvent.KEYCODE_DPAD_DOWN);check(position(app)==original.length-1,"Move wrapped past last shortcut");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_LEFT);check(editing(),"Horizontal arrow unexpectedly exited reorder");
            Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null,"No native screenshot");try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"sidebar-reorder.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(!editing()&&monitor.getHits()==0,"Confirm opened an app or failed to save");
            List<String> expected=new ArrayList<>(Arrays.asList(original));expected.add(expected.remove(0));String[] confirmed=expected.toArray(new String[0]);check(Arrays.equals(confirmed,SidebarApps.slots(c)),"Confirmed order not saved correctly");
            ui(()->home.resume());check(Arrays.equals(confirmed,draft()),"Saved order lost on resume");
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(monitor.getHits()==1,"Short OK no longer opens the moved app");
            focus(app);holdOK();key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_BACK);
            check(!editing()&&Arrays.equals(confirmed,SidebarApps.slots(c))&&Arrays.equals(confirmed,draft()),"Back did not restore confirmed order");visible(app);
            holdOK();key(KeyEvent.KEYCODE_DPAD_UP);
            settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            check(!editing()&&Arrays.equals(confirmed,SidebarApps.slots(c))&&Arrays.equals(confirmed,draft()),"Leaving home saved an unfinished draft");
            SettingsActivity stop=settings;ui(()->stop.finish());Thread.sleep(200);waitForIdleSync();
            SidebarApps.assign(c,confirmed.length-2,"");SidebarApps.assign(c,confirmed.length-3,"com.vela.fixture.absent");String[] hidden=SidebarApps.slots(c);ui(()->home.resume());
            focus(app);holdOK();key(KeyEvent.KEYCODE_DPAD_UP);check(position(app)==confirmed.length-4,"Move did not skip unavailable/hidden entries");key(KeyEvent.KEYCODE_BACK);
            check(Arrays.equals(hidden,SidebarApps.slots(c))&&Arrays.equals(hidden,draft()),"Cancel changed hidden positions");
            focus(app);holdOK();key(KeyEvent.KEYCODE_DPAD_UP);SidebarApps.add(c,"com.vela.fixture.concurrent");String[] changed=SidebarApps.slots(c);key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(!editing()&&Arrays.equals(changed,SidebarApps.slots(c))&&Arrays.equals(changed,draft()),"Stale draft overwrote another settings change");
            check(monitor.getHits()==1,"Moving, canceling or saving triggered an app launch");
            result.putString("result","PASS real held OK with action menu and repeat/release suppression, Move action, move/scroll across ten installed apps, boundaries, no saving until OK, drawable reuse, save/resume, short OK launch, Back/pause cancel, hidden/unavailable entries and stale-draft protection. User selection restored.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            if(home!=null)ui(()->home.pause());
            SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet())if(entry.getValue() instanceof String)edit.putString(entry.getKey(),(String)entry.getValue());edit.commit();
            if(monitor!=null)removeMonitor(monitor);
            if(settings!=null){SettingsActivity a=settings;ui(()->a.finish());}
            if(activity!=null){HomeActivity a=activity;ui(()->a.finish());}
        }
        finish(result.containsKey("error")?1:-1,result);
    }
}
