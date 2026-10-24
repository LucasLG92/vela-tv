package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual remote navigation and poster geometry; reset must preserve unrelated preferences. */
public class AndroidAppearanceTest extends Instrumentation {
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private Object field(Object o,String name)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void set(Object o,String name,Object value)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void call(Object o,String name){final Object target=o instanceof ScreenActivity?((ScreenActivity)o).screen:o;ui(()->{try{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}catch(Exception e){throw new RuntimeException(e);}});}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void focused(Activity a)throws Exception{Thread.sleep(700);long end=SystemClock.uptimeMillis()+10_000;while(!a.hasWindowFocus()&&SystemClock.uptimeMillis()<end){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null&&"com.android.systemui".contentEquals(root.getPackageName()))for(String label:new String[]{"Got it","Entendido"})for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(100);waitForIdleSync();}check(a.hasWindowFocus(),"Activity did not receive window focus");}
    private List<View> rows(Object view)throws Exception{return (List<View>)field(view,"rows");}
    private void restore(SharedPreferences p,Map<String,?> map){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> entry:map.entrySet()){Object v=entry.getValue();if(v instanceof Integer)e.putInt(entry.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(entry.getKey(),(Boolean)v);else if(v instanceof String)e.putString(entry.getKey(),(String)v);else if(v instanceof Long)e.putLong(entry.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(entry.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(entry.getKey(),new HashSet<>((Set<String>)v));}e.commit();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=Visuals.prefs(c);Map<String,?> saved=new HashMap<>(prefs.getAll());HomeActivity homeActivity=null;SettingsActivity settings=null;
        try{
            Locale.setDefault(new Locale("es","ES"));Personalization.prepare(c);Personalization.complete(c);PlexClient client=new PlexClient(c);Map<String,?> account=new HashMap<>(client.prefs.getAll());String[] apps=SidebarApps.slots(c);
            Appearance.reset(c);check(Appearance.colorIndex(c)==0&&Appearance.sizeIndex(c)==1&&Appearance.backdropIndex(c)==1,"Fresh appearance changes the existing design");
            homeActivity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));HomeActivity a=homeActivity;focused(a);CinemaHomeView home=(CinemaHomeView)field(a,"home");
            List<PlexClient.Film> films=new ArrayList<>();for(int i=0;i<12;i++){PlexClient.Film f=new PlexClient.Film();f.key="appearance-test-"+i;f.title="Test "+i;f.type="movie";f.thumb="";f.art="";f.meta="";f.summary="A long summary that must never overlap the main action or library controls.";films.add(f);}ui(()->{try{set(home,"films",films);}catch(Exception e){throw new RuntimeException(e);}});call(home,"showFilms");
            int budget=Visuals.cacheKb(c);
            for(int size=0;size<3;size++){
                int choice=size;ui(()->Appearance.set(c,"size",choice));call(home,"applyAppearance");Thread.sleep(250);waitForIdleSync();
                List<View> cards=(List<View>)field(home,"cards");check(cards.size()==12,"Changing size retained a second poster collection");View first=cards.get(0);Rect box=new Rect();check(first.getGlobalVisibleRect(box),"Poster is not visible");check(box.height()>=first.getHeight()-1,"Poster is clipped at the bottom for size "+size);check(first.getWidth()*3==first.getHeight()*2,"Poster aspect ratio changed");
                FrameLayout.LayoutParams pages=(FrameLayout.LayoutParams)((View)field(home,"pager")).getLayoutParams(),shelf=(FrameLayout.LayoutParams)((View)field(home,"shelvesScroll")).getLayoutParams();check(pages.topMargin+pages.height<=shelf.topMargin+11,"Library controls overlap the poster heading");
                for(ImageView image:(List<ImageView>)field(home,"posterImages"))check(image.getScaleType()==ImageView.ScaleType.FIT_CENTER,"Poster artwork is cropped");
                key(KeyEvent.KEYCODE_DPAD_RIGHT);ui(()->((View)get(home,"play")).requestFocus());key(KeyEvent.KEYCODE_DPAD_DOWN);check(cards.contains(a.getCurrentFocus()),"Remote cannot enter posters after changing size "+size+"; window="+a.hasWindowFocus()+"; focus="+a.getCurrentFocus()+"; next="+((View)field(home,"play")).focusSearch(View.FOCUS_DOWN)+"; shown="+first.isShown()+"; focusable="+first.isFocusable());key(KeyEvent.KEYCODE_DPAD_RIGHT);check(a.getCurrentFocus()==cards.get(1),"Remote cannot move between resized posters");
            }
            check(Visuals.cacheKb(c)==budget,"Appearance silently expanded the image cache");
            settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).putExtra("page",17).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));focused(settings);Object view=field(settings,"view");check((Integer)field(view,"page")==17,"Appearance deep link failed");key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);check(Appearance.colorIndex(c)==2,"Color chooser did not save Mint");key(KeyEvent.KEYCODE_BACK);check((Integer)field(view,"page")==17,"Back from color chooser failed");
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_CENTER);check(Appearance.sizeIndex(c)==1,"Size chooser did not restore balanced size");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_CENTER);check(Appearance.backdropIndex(c)==0,"Backdrop chooser did not save darker background");key(KeyEvent.KEYCODE_BACK);
            Map<String,Object> unrelated=new HashMap<>();for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(!e.getKey().startsWith("appearance_"))unrelated.put(e.getKey(),e.getValue());
            ui(()->{try{rows(view).get(3).performClick();}catch(Exception e){throw new RuntimeException(e);}});check(Appearance.colorIndex(c)==0&&Appearance.sizeIndex(c)==1&&Appearance.backdropIndex(c)==1,"Reset did not restore original design");for(Map.Entry<String,Object> e:unrelated.entrySet())check(e.getValue().equals(prefs.getAll().get(e.getKey())),"Reset changed performance preferences");
            SettingsActivity s=settings;ui(()->s.finish());settings=null;focused(a);check(field(home,"appearanceKey").equals(Appearance.signature(c)),"Returning home did not apply saved appearance");check(account.equals(client.prefs.getAll())&&Arrays.equals(apps,SidebarApps.slots(c)),"Appearance altered accounts or apps");
            result.putString("result","PASS original defaults, three poster sizes fully visible with original aspect ratio, full artwork, bounded card count and unchanged cache, actual DPAD navigation, native color/size/backdrop choices, Back navigation, live return to home, reset preserving performance, accounts and apps.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));try{Thread.sleep(1000);android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),"appearance-failure.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}catch(Exception ignored){}}
        finally{if(settings!=null){SettingsActivity s=settings;ui(()->s.finish());}if(homeActivity!=null){HomeActivity a=homeActivity;ui(()->a.finish());}restore(prefs,saved);}
        finish(result.containsKey("error")?1:-1,result);
    }
    private Object get(Object o,String name){try{return field(o,name);}catch(Exception e){throw new RuntimeException(e);}}
}
