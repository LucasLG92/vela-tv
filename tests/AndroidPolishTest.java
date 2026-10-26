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

/** Integration coverage for the shared shell, focus boundaries and enlarged tools, on an owned emulator. */
public final class AndroidPolishTest extends Instrumentation {
    private Activity activity;private Context context;private String profile,previous,phase="setup";private boolean lite;
    private final Bundle result=new Bundle();
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private Object get(Object o,String key)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(key);}
    private void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    private interface Ready{boolean ready()throws Exception;}
    private void await(Ready r)throws Exception{long end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(r.ready())return;Thread.sleep(40);}throw new AssertionError("Timeout: "+phase);}
    private VelaHub hub()throws Exception{return (VelaHub)get(activity,"hub");}
    private Object home()throws Exception{return lite?activity:get(activity,"home");}
    private View nav(int full,int small)throws Exception{return lite?((View[])get(activity,"navigation"))[small]:((java.util.List<View>)get(home(),"sideButtons")).get(full);}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void shot(String name)throws Exception{Thread.sleep(250);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null&&b.getWidth()==3840,"Native 4K screenshot required");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"polish-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    private void bounds(View view)throws Exception{Rect rect=new Rect();check(view.getGlobalVisibleRect(rect)&&rect.width()>0&&rect.height()>0,"Invisible control");Rect window=new Rect();activity.getWindow().getDecorView().getGlobalVisibleRect(window);check(window.contains(rect),"Control outside window");}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){super.onStart();context=getTargetContext();lite=context.getPackageName().endsWith(".lite");try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Vela · Demo");VelaWorkspace.select(context,profile);VelaSessions.prefs(context).edit().clear().commit();VelaSessions.lock();L10n.choose(context,"es");Ui.init(context);if(!lite){Class<?> setup=Class.forName("tv.vela.launcher.Personalization");for(String method:new String[]{"prepare","complete"}){Method m=setup.getDeclaredMethod(method,Context.class);m.setAccessible(true);m.invoke(null,context);}}
        activity=startActivitySync(new Intent().setClassName(context.getPackageName(),"tv.vela.launcher."+(lite?"LiteHomeActivity":"HomeActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("velaTools",2));await(()->hub()!=null&&activity.hasWindowFocus());
        phase="embedded-shell";VelaHub tools=hub();View[] categories=(View[])get(tools,"categories");View gear=nav(7,3);check(tools.getParent()!=activity.findViewById(android.R.id.content),"Tools replaced the launcher shell");bounds(gear);bounds(categories[9]);
        ui(categories[2]::requestFocus);key(KeyEvent.KEYCODE_DPAD_LEFT);check(gear.hasFocus()&&hub()==tools&&(Boolean)get(home(),"sidebarOpen"),"Left did not reach the retained launcher rail");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(categories[2].hasFocus()&&!(Boolean)get(home(),"sidebarOpen"),"Right did not restore the selected tools category");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(((java.util.List<View>)get(tools,"controls")).get(0).hasFocus(),"Right did not enter tools controls");key(KeyEvent.KEYCODE_DPAD_LEFT);check(categories[2].hasFocus(),"Left skipped tool categories");shot("01-reading");
        phase="async-does-not-steal-rail";ui(()->((VelaHub.Owner)activity).tools(0));tools=hub();EditText input=(EditText)get(tools,"input");VelaHub search=tools;ui(()->{input.setText("No matching demo programme");try{Method m=VelaHub.class.getDeclaredMethod("search");m.setAccessible(true);m.invoke(search);}catch(Exception e){throw new RuntimeException(e);}});gear=nav(7,3);ui(gear::requestFocus);Thread.sleep(1600);waitForIdleSync();check(gear.hasFocus()&&hub()==search,"Background result stole focus from the launcher rail");
        phase="navigate-from-tools";View apps=nav(4,2);ui(apps::requestFocus);key(KeyEvent.KEYCODE_DPAD_RIGHT);await(()->hub()==null);check(lite?(Integer)get(activity,"section")==2:(Integer)get(home(),"filter")==0&&get(home(),"panels")!=null,"Apps did not open from the retained rail");await(()->{if(lite)return !((java.util.List<?>)get(activity,"tiles")).isEmpty();Object host=get(home(),"panels");Object screen=get(host,"screen");Object grid=get(screen,"grid");return grid!=null&&((GridView)grid).getChildCount()>0;});shot("02-apps");
        phase="settings-return-after-change";gear=nav(7,3);ui(gear::performClick);ui(()->((VelaHub.Owner)activity).tools(2));tools=hub();VelaReading.cycle(context,"density",3);ui(tools::close);check(hub()==null,"Tools failed to close after a preference change");check(lite?(Integer)get(activity,"section")==3:get(home(),"panels")!=null,"Changed tools lost the Settings destination");shot("03-settings");
        phase="large-text-tools";VelaReading.prefs(context).edit().putInt("text",2).putInt("margin",3).commit();ui(()->((VelaHub.Owner)activity).tools(2));tools=hub();categories=(View[])get(tools,"categories");for(int i=0;i<categories.length;i++){View cat=categories[i];ui(cat::requestFocus);bounds(cat);if(i==2)shot("04-large-reading");}key(KeyEvent.KEYCODE_DPAD_LEFT);check(nav(7,3).hasFocus(),"Large layout lost main-rail access");
        result.putString("result","PASS embedded Full/Lite tools, retained launcher navigation, left/right focus boundaries, async results do not steal rail focus, Apps navigation, Settings restored after preference rebuild, 130% text and screen margins, native demo screenshots. Owned emulator only.");
    }catch(Throwable e){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));}finally{if(activity!=null)ui(activity::finish);try{if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}}catch(Exception ignored){}}finish(result.containsKey("error")?1:-1,result);}
}
