package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/** Actual icon presentation, remote navigation, launch routing and panel lifetime. */
public class AndroidAppsGridTest extends Instrumentation {
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private interface Ready{boolean ready()throws Exception;}
    private void await(Ready condition,String why)throws Exception{long end=SystemClock.uptimeMillis()+15_000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->{try{ready[0]=condition.ready();}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(80);}throw new AssertionError(why);}
    private void show(CinemaHomeView home,View from){ui(()->{try{Method m=CinemaHomeView.class.getDeclaredMethod("showApps",View.class);m.setAccessible(true);m.invoke(home,from);}catch(Exception e){throw new RuntimeException(e);}});}
    private void restore(SharedPreferences p,Map<String,?> saved){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:saved.entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);}e.commit();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences preferences=Personalization.prefs(c);Map<String,?> saved=new HashMap<>(preferences.getAll());HomeActivity activity=null;ActivityMonitor monitor=null;
        try{
            Locale.setDefault(new Locale("es","ES"));Personalization.prepare(c);Personalization.complete(c);
            Map<String,?> account=new HashMap<>(new PlexClient(c).prefs.getAll());String[] shortcuts=SidebarApps.slots(c);
            activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));HomeActivity a=activity;
            await(()->a.hasWindowFocus(),"Home did not receive focus");CinemaHomeView home=(CinemaHomeView)field(a,"home");
            View from=((List<View>)field(home,"sideButtons")).get(4);show(home,from);
            InstalledAppsGrid grid=(InstalledAppsGrid)field(home,"installedApps");
            await(()->grid.getCount()>0&&grid.getChildCount()>0,"Installed apps did not appear");
            await(()->{for(int i=0;i<grid.getChildCount();i++)if(((ImageView)field(grid.getChildAt(i),"image")).getDrawable()==null)return false;return true;},"Visible installed apps did not receive their icons");
            check(grid.getChildCount()<=20,"App panel retained an entire large icon grid");
            for(int i=0;i<grid.getChildCount();i++){
                ImageView image=(ImageView)field(grid.getChildAt(i),"image");Bitmap bitmap=((BitmapDrawable)image.getDrawable()).getBitmap();
                check(bitmap.getWidth()<=96&&bitmap.getHeight()<=96,"Icon exceeds the memory bound");
                check(image.getColorFilter()==null,"Original app icon was tinted");
            }
            List<View> favorites=(List<View>)field(grid,"above");
            if(!favorites.isEmpty()){ui(()->favorites.get(0).requestFocus());key(KeyEvent.KEYCODE_DPAD_DOWN);check(grid.hasFocus(),"Down did not enter the app grid");}
            else ui(()->grid.requestFocus());
            ui(()->grid.setSelection(0));Thread.sleep(120);
            int count=grid.getCount();
            for(int i=0;i<(count+4)/5;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);
            for(int i=0;i<5;i++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(grid.getSelectedItemPosition()==count-1,"Remote did not reach the final installed app");
            check(grid.hasFocus(),"Navigation escaped the app grid at its lower/right boundary");
            if(count>5&&count%5!=0){ui(()->grid.setSelection(count-count%5-1));Thread.sleep(100);key(KeyEvent.KEYCODE_DPAD_DOWN);check(grid.getSelectedItemPosition()==count-1,"Down could not enter an incomplete final row");}
            check(grid.getChildCount()<=20,"Scrolling retained offscreen app cells");
            check(((LruCache<?,?>)field(grid,"icons")).size()<=512*1024,"Icon cache grew beyond its budget");
            ui(()->{grid.requestFocus();grid.setSelection(0);});Thread.sleep(180);
            if(!favorites.isEmpty()){key(KeyEvent.KEYCODE_DPAD_UP);check(favorites.contains(a.getCurrentFocus()),"Up did not return to favorite apps; selected="+grid.getSelectedItemPosition()+" focus="+a.getCurrentFocus()+" first="+favorites.get(0)+" shown="+favorites.get(0).isShown());key(KeyEvent.KEYCODE_DPAD_DOWN);}
            ui(()->{grid.requestFocus();grid.setSelection(0);});Thread.sleep(180);
            SidebarApps.Choice chosen=(SidebarApps.Choice)grid.getItemAtPosition(0);Intent launch=HomeApps.launchIntent(c.getPackageManager(),chosen.pkg);check(launch!=null&&launch.getComponent()!=null,"Selected app has no launch activity");
            monitor=addMonitor(launch.getComponent().getClassName(),null,true);ActivityMonitor blocked=monitor;
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(blocked.getHits()==1,"OK did not open the selected app");removeMonitor(monitor);monitor=null;
            key(KeyEvent.KEYCODE_BACK);
            check(field(home,"installedApps")==null&&((View)field(home,"overlay")).getVisibility()==View.GONE,"Back did not close the app panel");
            check(((FrameLayout)field(home,"overlay")).getChildCount()==0,"Closed panel retained icon views");
            check(((ThreadPoolExecutor)field(grid,"worker")).isShutdown()&&((LruCache<?,?>)field(grid,"icons")).size()==0,"Closing the panel kept its icon loader/cache");
            show(home,from);InstalledAppsGrid reopened=(InstalledAppsGrid)field(home,"installedApps");await(()->reopened.getCount()>0,"App panel did not reopen");
            ui(()->home.pause());check(field(home,"installedApps")==null,"Pause retained the app panel");ui(()->home.resume());
            check(account.equals(new PlexClient(c).prefs.getAll())&&Arrays.equals(shortcuts,SidebarApps.slots(c)),"App grid changed accounts or shortcuts");
            result.putString("result","PASS original untinted bounded icons, recycled app cells, real DPAD entry/scroll/return, final app reachable, exact selected-app launch, Back/reopen/pause cleanup, bounded cache and worker shutdown. Accounts and shortcuts preserved.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(monitor!=null)removeMonitor(monitor);if(activity!=null){HomeActivity a=activity;ui(()->a.finish());}restore(preferences,saved);}
        finish(result.containsKey("error")?1:-1,result);
    }
}
