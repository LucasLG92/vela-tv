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
import java.util.concurrent.*;
import org.json.JSONObject;

/** Native Plex linking UI with a test-only client. No Plex account, requests or real PINs. Emulator only. */
public final class AndroidPlexLinkTest extends Instrumentation {
    static volatile String mode="pin";static volatile boolean fail;static volatile int selections;
    final List<Activity> opened=new ArrayList<>();
    public static class Screen extends PlexLinkActivity {@Override PlexClient createClient(){return new Fixture(this);}}
    static final class Fixture extends PlexClient {
        Fixture(Context c){super(c);}
        @Override JSONObject createPin()throws Exception{return new JSONObject().put("id",1).put("code","DEMO").put("expiresIn",900);}
        @Override boolean checkPin(long id,String code){if(mode.equals("authorized-error")){prefs.edit().putString("accountToken","test-only-account").commit();return true;}return false;}
        @Override List<Server> servers()throws Exception{if(mode.equals("error")||mode.equals("authorized-error"))throw new IOException("Test-only error");List<Server> list=new ArrayList<>();int count=mode.equals("empty")?0:mode.equals("single")?1:mode.equals("gallery")?3:9;for(int i=0;i<count;i++){Server s=new Server();s.id="demo-"+i;s.name=i==0?"Cine en casa":i==1?"Biblioteca familiar":i==2?"Series y documentales":i==8?"Mi colección de películas y series con un nombre de servidor muy largo":"Biblioteca "+(i+1);list.add(s);}return list;}
        @Override void select(Server server)throws Exception{selections++;if(fail)throw new IOException("Test-only connection failure");prefs.edit().putString("serverId",server.id).putString("serverName",server.name).putString("serverToken","test-only-demo").commit();}
    }
    @Override public Activity newActivity(ClassLoader cl,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return name.equals(PlexLinkActivity.class.getName())?new Screen():super.newActivity(cl,name,intent);}
    void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    Object field(Object target,String name)throws Exception{if(target instanceof ScreenActivity)target=((ScreenActivity)target).screen;for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    interface Ready{boolean ready()throws Exception;}
    void await(Ready ready,String why)throws Exception{long end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){boolean[] yes={false};ui(()->{try{yes[0]=ready.ready();}catch(Exception e){throw new RuntimeException(e);}});if(yes[0])return;Thread.sleep(70);}throw new AssertionError(why);}
    PlexLinkActivity open()throws Exception{PlexLinkActivity a=(PlexLinkActivity)startActivitySync(new Intent(getTargetContext(),PlexLinkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));opened.add(a);return a;}
    List<View> cards(PlexLinkActivity a)throws Exception{return (List<View>)field(a,"cards");}
    void capture(String name)throws Exception{waitForIdleSync();Thread.sleep(300);Bitmap b=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){check(b!=null,"No native screenshot");b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{if(b!=null)b.recycle();}}
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=new PlexClient(c).prefs,library=c.getSharedPreferences("library",0);Map<String,?> before=new HashMap<>(prefs.getAll()),source=new HashMap<>(library.getAll());try{
        check(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"),"Clean emulator required");
        prefs.edit().clear().putString("client","ui-fixture").commit();mode="pin";PlexLinkActivity pin=open();await(()->((TextView)field(pin,"code")).getText().toString().equals("DEMO"),"PIN not shown");capture("43-vincular-plex");
        Field expires=PlexLinkScreen.class.getDeclaredField("expiresAt");expires.setAccessible(true);expires.setLong(pin.screen,0);Method poll=PlexLinkScreen.class.getDeclaredMethod("check");poll.setAccessible(true);poll.invoke(pin.screen);await(()->((View)field(pin,"retry")).getVisibility()==View.VISIBLE,"Expired PIN lacks retry");ui(pin::finish);await(pin::isDestroyed,"PIN activity did not close");check(((ScheduledThreadPoolExecutor)field(pin,"io")).isShutdown(),"PIN worker leaked");
        mode="authorized-error";PlexLinkActivity authorized=open();await(()->((View)field(authorized,"retry")).getVisibility()==View.VISIBLE,"Server lookup after authorization lacks retry");check(!prefs.getString("accountToken","").isEmpty(),"Server lookup discarded authorization");ui(authorized::finish);await(authorized::isDestroyed,"Authorized error did not close");
        prefs.edit().putString("accountToken","test-only-account").putString("serverId","demo-0").commit();mode="gallery";PlexLinkActivity gallery=open();await(()->cards(gallery).size()==3&&cards(gallery).get(0).hasFocus(),"Gallery servers missing");capture("42-servidores-plex");ui(gallery::finish);await(gallery::isDestroyed,"Gallery did not close");mode="servers";PlexLinkActivity screen=open();await(()->cards(screen).size()==9&&cards(screen).get(0).hasFocus(),"Servers or selected focus missing");
        for(int i=0;i<8;i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();}await(()->cards(screen).get(8).hasFocus(),"Remote cannot reach last server");await(()->((ScrollView)field(screen,"serversScroll")).getScrollY()>0,"Server list did not scroll");Thread.sleep(300);
        int[] bottom=new int[2],footer=new int[2];ui(()->{try{View last=cards(screen).get(8),hint=(View)field(screen,"hint");last.getLocationOnScreen(bottom);bottom[1]+=Math.round(last.getHeight()*last.getScaleY()*((View)field(screen,"stage")).getScaleY());hint.getLocationOnScreen(footer);}catch(Exception e){throw new RuntimeException(e);}});check(bottom[1]<=footer[1],"Server overflow covers footer");capture("plex-servers-scroll-check");
        fail=true;selections=0;sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);await(()->selections==1&&!(Boolean)field(screen,"busy"),"Failed selection not recovered");check(cards(screen).get(8).isEnabled(),"Failed server remains disabled");check(prefs.getString("serverId","").equals("demo-0"),"Failure replaced selected server");
        fail=false;sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);await(screen::isDestroyed,"Successful selection did not finish");check(selections==2&&prefs.getString("serverId","").equals("demo-8"),"Selection was duplicated or lost");check(library.getString("provider","").equals("Plex"),"Plex not activated");
        for(String state:new String[]{"empty","error","single"}){mode=state;fail=state.equals("single");PlexLinkActivity a=open();if(state.equals("single")){await(()->cards(a).size()==1&&!(Boolean)field(a,"busy"),"Single unreachable server cannot be retried");}else await(()->((View)field(a,"retry")).getVisibility()==View.VISIBLE,"Empty/error state lacks retry");ui(a::finish);await(a::isDestroyed,"Activity did not close");check(((ScheduledThreadPoolExecutor)field(a,"io")).isShutdown(),"Closed worker retained");}
        result.putString("result","PASS native Plex linking: demo PIN and expiry, selected server focus, nine-server D-pad scroll bounded above footer, failed selection preserves session and retries, successful selection activates Plex once, empty/offline/single-server recovery and worker release. Screenshots use fictional servers and a nonfunctional demo code; no Plex requests or credentials.");
    }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}finally{for(Activity a:opened)ui(()->{if(!a.isFinishing())a.finish();});restore(prefs,before);restore(library,source);}finish(result.containsKey("error")?1:-1,result);}
}
