package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** First-run state in isolated local stores; real guide/navigation without modifying personal accounts. */
public class AndroidPersonalizationTest extends Instrumentation {
    private Object field(Object o,String n)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(n);}
    private void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void page(SetupActivity a,int page){ui(()->{try{Method m=SetupScreen.class.getDeclaredMethod("go",int.class);m.setAccessible(true);m.invoke(a.screen,page);}catch(Exception e){throw new RuntimeException(e);}});}
    private void capture(Context c,String name)throws Exception{Thread.sleep(250);Bitmap b=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){check(b!=null,"Missing screenshot");b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{if(b!=null)b.recycle();}}
    private static final class Store extends ContextWrapper {
        final String id="setup-test-"+UUID.randomUUID()+"-";final File folder;final Set<String> names=new HashSet<>();
        Store(Context c){super(c);folder=new File(c.getCacheDir(),id);folder.mkdirs();}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){return folder;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){names.add(name);return super.getSharedPreferences(id+name,mode);}
        void close(){for(String n:names)super.deleteSharedPreferences(id+n);File[] files=folder.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();folder.delete();}
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();Store first=null,second=null,legacy=null;SetupActivity setup=null;SettingsActivity settings=null;
        SharedPreferences personalization=Personalization.prefs(c);Map<String,?> setupBefore=new HashMap<>(personalization.getAll());Personalization.complete(c);
        Map<String,?> accountBefore=new HashMap<>(new PlexClient(c).prefs.getAll());String[] appsBefore=SidebarApps.slots(c);String cityBefore=AmbientWeather.name(c);
        try{
            check(!Personalization.needed(c),"Existing installation was forced through first-run setup");
            check(Config.CITY.isEmpty()&&Config.TMDB_KEY.isEmpty()&&Config.RECOVERY_HOME.isEmpty()&&!Config.ISOLATE_RECOVERY_HOME,"This must test the neutral distributable APK");
            first=new Store(c);second=new Store(c);legacy=new Store(c);
            check(Personalization.needed(first)&&Personalization.needed(second),"Fresh installations skip setup");
            for(String pkg:SidebarApps.slots(first))check(SidebarApps.launchable(first,pkg),"Fresh menu includes an unavailable app");
            PlexClient a=new PlexClient(first),b=new PlexClient(second);check(!a.clientId().equals(b.clientId()),"Installations share a Plex client identifier");
            a.prefs.edit().putString("accountToken","local-test-a").putString("serverToken","local-test-server-a").putString("serverId","same-server").putString("serverName","Test server A").commit();
            b.prefs.edit().putString("serverToken","local-test-server-b").putString("serverId","same-server").commit();
            check(!a.identity().equals(b.identity()),"Artwork keys mix accounts on the same server");
            Personalization.complete(first);check(!Personalization.needed(first)&&Personalization.needed(second),"Setup completion leaked between installations");
            check(AmbientWeather.manual(second)==null,"Fresh installation inherited a city from the APK");
            AmbientWeather.select(first,new AmbientWeather.Place("Local test city","",39.4,-.3,"Europe/Madrid"));check(AmbientWeather.manual(second)==null,"Location leaked between installations");
            new PlexClient(legacy).prefs.edit().putString("accountToken","local-test-existing").commit();SidebarApps.prefs(legacy).edit().putString("slots","[]").commit();
            check(!Personalization.needed(legacy)&&SidebarApps.slots(legacy).length==0,"Upgrade overwrote an intentionally empty menu");
            a.disconnect();check(!a.connected()&&!a.prefs.contains("accountToken")&&!a.prefs.contains("serverName")&&b.connected(),"Disconnect removed another installation's session or retained credentials");
            setup=(SetupActivity)startActivitySync(new Intent(c,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final SetupActivity guide=setup;waitForIdleSync();
            check(((Integer)field(guide,"page"))==0,"Guide does not start at welcome");capture(c,"setup-welcome.png");
            page(guide,1);check(((TextView)field(guide,"description")).getText().length()>0,"Missing account explanation");capture(c,"setup-accounts.png");
            page(guide,2);final java.util.List<View> buttons=(java.util.List<View>)field(guide,"buttons");ui(()->buttons.get(0).performClick());
            waitForIdleSync();check(guide.getWindow().getDecorView().getVisibility()==View.VISIBLE,"Guide lost its view");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();check(((Integer)field(guide,"page"))==2,"Returning from shortcut settings reset guide progress");
            page(guide,3);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();capture(c,"setup-weather.png");
            page(guide,4);capture(c,"setup-ready.png");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();check(((Integer)field(guide,"page"))==3,"Back did not return to weather");
            ui(()->guide.finish());setup=null;
            settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).putExtra("page",14).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final SettingsActivity screen=settings;waitForIdleSync();
            Object view=field(screen,"view");check(((Integer)field(view,"page"))==14,"Accounts deep link failed");capture(c,"accounts-services.png");
            ui(()->{try{Method m=view.getClass().getDeclaredMethod("render",int.class);m.setAccessible(true);m.invoke(view,16);}catch(Exception e){throw new RuntimeException(e);}});
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();check(((Integer)field(view,"page"))==15,"Cancel disconnect did not return to Plex");
            check(accountBefore.equals(new PlexClient(c).prefs.getAll()),"Review or cancel changed personal Plex credentials");
            check(Arrays.equals(appsBefore,SidebarApps.slots(c))&&cityBefore.equals(AmbientWeather.name(c)),"Review changed existing apps or location");
            result.putString("result","PASS neutral APK, fresh setup, installed-only app detection, independent client IDs/accounts/location/completion, isolated cache keys, local disconnect, legacy upgrade preserving empty menu, real guide navigation and return from settings, account hub and cancelled disconnect. Personal accounts, apps and location preserved.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{SharedPreferences.Editor setupRestore=personalization.edit().clear();for(Map.Entry<String,?> entry:setupBefore.entrySet()){if(entry.getValue() instanceof Boolean)setupRestore.putBoolean(entry.getKey(),(Boolean)entry.getValue());}setupRestore.commit();if(setup!=null){SetupActivity a=setup;ui(()->a.finish());}if(settings!=null){SettingsActivity a=settings;ui(()->a.finish());}if(first!=null)first.close();if(second!=null)second.close();if(legacy!=null)legacy.close();}
        finish(result.containsKey("error")?1:-1,result);
    }
}
