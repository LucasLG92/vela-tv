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
    private Object field(Object owner,String name)throws Exception{if(owner instanceof ScreenActivity)owner=((ScreenActivity)owner).screen;for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private Object unchecked(Object owner,String name){try{return field(owner,name);}catch(Exception failure){throw new RuntimeException(failure);}}
    private InstalledAppsGrid grid(CinemaHomeView home)throws Exception{HomeScreenHost host=(HomeScreenHost)field(home,"panels");return host!=null&&host.screen instanceof AppsScreen?(InstalledAppsGrid)field(host.screen,"grid"):null;}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private interface Ready{boolean ready()throws Exception;}
    private void await(Ready condition,String why)throws Exception{long end=SystemClock.uptimeMillis()+15_000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->{try{ready[0]=condition.ready();}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(80);}throw new AssertionError(why);}
    private void show(CinemaHomeView home,View from){ui(()->{try{Method m=CinemaHomeView.class.getDeclaredMethod("showApps",View.class);m.setAccessible(true);m.invoke(home,from);}catch(Exception e){throw new RuntimeException(e);}});}
    private void restore(SharedPreferences p,Map<String,?> saved){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:saved.entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);}e.commit();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences preferences=Personalization.prefs(c);Map<String,?> saved=new HashMap<>(preferences.getAll());SharedPreferences sidebar=SidebarApps.prefs(c),visuals=Visuals.prefs(c);Map<String,?> savedSidebar=new HashMap<>(sidebar.getAll()),savedVisuals=new HashMap<>(visuals.getAll());HomeActivity activity=null;ActivityMonitor monitor=null;
        try{
            Locale.setDefault(new Locale("es","ES"));Personalization.prepare(c);Personalization.complete(c);
            Map<String,?> account=new HashMap<>(new PlexClient(c).prefs.getAll());
            List<SidebarApps.Choice> all=SidebarApps.choices(c);List<String> pinned=new ArrayList<>();for(int i=Math.min(7,all.size())-1;i>=0;i--)pinned.add(all.get(i).pkg);
            sidebar.edit().putString("slots",new org.json.JSONArray(pinned).toString()).commit();visuals.edit().putBoolean("light",false).commit();String[] shortcuts=SidebarApps.slots(c);
            activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));HomeActivity a=activity;
            await(()->a.hasWindowFocus(),"Home did not receive focus");CinemaHomeView home=(CinemaHomeView)field(a,"home");
            View from=((List<View>)field(home,"sideButtons")).get(4);show(home,from);
            InstalledAppsGrid grid=grid(home);
            await(()->grid.getCount()>0&&grid.getChildCount()>0,"Installed apps did not appear");
            await(()->{for(int i=0;i<grid.getChildCount();i++)if(((ImageView)field(grid.getChildAt(i),"image")).getDrawable()==null)return false;return true;},"Visible installed apps did not receive their icons");
            check(grid.getChildCount()<=24,"App panel retained an entire large icon grid");
            for(int i=0;i<grid.getChildCount();i++){
                ImageView image=(ImageView)field(grid.getChildAt(i),"image");Bitmap bitmap=((BitmapDrawable)image.getDrawable()).getBitmap();
                check(bitmap.getWidth()==192&&bitmap.getHeight()==192,"Icon exceeds the memory bound");
                check(image.getColorFilter()==null,"Original app icon was tinted");
                check(android.graphics.Color.alpha(bitmap.getPixel(0,0))==0,"Icon is missing its rounded square mask");
                check(grid.getChildAt(i).getBackground()==null,"App cell reintroduced a full gray card");
            }
            check(grid.getCount()==all.size(),"Favorites duplicated or removed installed apps");
            for(int i=0;i<pinned.size();i++)check(((SidebarApps.Choice)grid.getItemAtPosition(i)).pkg.equals(pinned.get(i)),"Favorite order was lost beyond the fourth app");
            View close=(View)field(grid,"close");ui(()->{grid.requestFocus();grid.setSelection(0);});Thread.sleep(180);key(KeyEvent.KEYCODE_DPAD_UP);check(a.getCurrentFocus()==close,"Up did not reach Close; selected="+grid.getSelectedItemPosition()+" focus="+a.getCurrentFocus());key(KeyEvent.KEYCODE_DPAD_RIGHT);check(grid.hasFocus(),"Right did not return from navigation");
            ui(()->grid.setSelection(0));Thread.sleep(120);
            int count=grid.getCount();
            for(int i=0;i<(count+5)/6;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);
            for(int i=0;i<6;i++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(grid.getSelectedItemPosition()==count-1,"Remote did not reach the final installed app");
            check(grid.hasFocus(),"Navigation escaped the app grid at its lower/right boundary");
            if(count>6&&count%6!=0){ui(()->grid.setSelection(count-count%6-1));Thread.sleep(100);key(KeyEvent.KEYCODE_DPAD_DOWN);check(grid.getSelectedItemPosition()==count-1,"Down could not enter an incomplete final row");}
            check(grid.getChildCount()<=24,"Scrolling retained offscreen app cells");
            check(((LruCache<?,?>)field(grid,"icons")).size()<=1024*1024,"Icon cache grew beyond its budget");
            ui(()->{grid.requestFocus();grid.setSelection(0);});Thread.sleep(180);
            ui(()->{grid.requestFocus();grid.setSelection(0);});Thread.sleep(180);
            SidebarApps.Choice chosen=(SidebarApps.Choice)grid.getItemAtPosition(0);Intent launch=HomeApps.launchIntent(c.getPackageManager(),chosen.pkg);check(launch!=null&&launch.getComponent()!=null,"Selected app has no launch activity");
            monitor=addMonitor(launch.getComponent().getClassName(),null,true);ActivityMonitor blocked=monitor;
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(blocked.getHits()==1,"OK did not open the selected app");removeMonitor(monitor);monitor=null;
            key(KeyEvent.KEYCODE_BACK);
            check(a.getCurrentFocus()==from,"Back did not focus app navigation");ui(()->((List<View>)unchecked(home,"sideButtons")).get(0).performClick());
            check(grid(home)==null,"Leaving Apps retained its grid");
            check(((FrameLayout)field(home,"overlay")).getChildCount()==0,"Closed panel retained icon views");
            check(((ThreadPoolExecutor)field(grid,"worker")).isShutdown()&&((LruCache<?,?>)field(grid,"icons")).size()==0,"Closing the panel kept its icon loader/cache");
            visuals.edit().putBoolean("light",true).commit();show(home,from);InstalledAppsGrid reopened=grid(home);await(()->reopened.getCount()>0&&reopened.getChildCount()>0&&((ImageView)field(reopened.getChildAt(0),"image")).getDrawable()!=null,"App panel did not reopen");
            ui(()->{try{Bitmap small=((BitmapDrawable)((ImageView)field(reopened.getChildAt(0),"image")).getDrawable()).getBitmap();check(small.getWidth()==128,"Light mode did not reduce artwork");check(((LruCache<?,?>)field(reopened,"icons")).maxSize()==512*1024,"Light cache budget was not reduced");reopened.trim();check(((LruCache<?,?>)field(reopened,"icons")).size()==0,"Memory pressure did not clear cache");}catch(Exception e){throw new RuntimeException(e);}});
            ui(()->home.pause());check(grid(home)==null,"Pause retained the apps grid");ui(()->home.resume());
            check(account.equals(new PlexClient(c).prefs.getAll())&&Arrays.equals(shortcuts,SidebarApps.slots(c)),"App grid changed accounts or shortcuts");
            result.putString("result","PASS rounded original square icons, no full gray cards, one library without duplicate favorites, seven favorites preserve order, 192px normal / 128px light artwork, 1MiB / 512KiB caches, recycled cells, actual remote navigation and selected-app launch, Back/reopen/pause cleanup, trim and worker shutdown. Accounts and shortcuts preserved.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(monitor!=null)removeMonitor(monitor);if(activity!=null){HomeActivity a=activity;ui(()->a.finish());}restore(preferences,saved);restore(sidebar,savedSidebar);restore(visuals,savedVisuals);}
        finish(result.containsKey("error")?1:-1,result);
    }
}
