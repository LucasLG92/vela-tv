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
import org.json.*;

/** Actual first run and reversible native controllers, including the Android 8 boundary. */
public final class AndroidUnifiedTest extends Instrumentation {
    private Context context;private final Bundle result=new Bundle();private String phase="setup";
    private final List<Activity> opened=new ArrayList<>();
    public static final class Lite extends LiteHomeActivity {
        protected boolean allowAutomaticChecks(){return false;}
        protected TvInputs.Source createInputsSource(){return new TvInputs.Source(){
            public List<TvInputs.Entry> read(){return Arrays.asList(new TvInputs.Entry("fixture-hdmi-1","fixture-hdmi-1","HDMI 1","Nintendo Switch","switch",TvInputs.CONNECTED,false),new TvInputs.Entry("fixture-hdmi-2","fixture-hdmi-2","HDMI 2","Computer","pc",TvInputs.CONNECTED,false));}
            public void start(Runnable changed){}public void stop(){}
        };}
    }
    @Override public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{
        Activity a=name.equals(LiteHomeActivity.class.getName())?new Lite():super.newActivity(loader,name,intent);opened.add(a);return a;
    }
    interface Ready{boolean get()throws Exception;}
    void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    void await(Ready ready)throws Exception{long end=SystemClock.elapsedRealtime()+18000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(ready.get())return;Thread.sleep(80);}throw new AssertionError("Timeout "+phase);}
    Object field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    Activity foreground(String name)throws Exception{final Activity[] found={null};await(()->{for(Activity a:opened)if(a.getClass().getName().equals(name)||a instanceof Lite&&name.equals(LiteHomeActivity.class.getName()))if(a.hasWindowFocus()&&!a.isFinishing()){found[0]=a;return true;}return false;});return found[0];}
    View title(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=title(((ViewGroup)root).getChildAt(i),text);if(v!=null)return v;}return null;}
    void card(Activity a,String title)throws Exception{View label=title(a.findViewById(android.R.id.content),title);check(label!=null,"Missing card "+title);ui(()->((View)label.getParent()).performClick());}
    void shot(String name)throws Exception{Thread.sleep(350);Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"No native screenshot");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"unified-api"+Build.VERSION.SDK_INT+"-"+name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor f=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(f)){check(in.read()=='1',"Owned emulator required");}
        check(context.getPackageName().equals("tv.vela.launcher"),"Unified package required");
        check(!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"Personal accounts forbidden");
        RefinementHttp.install(url->null);L10n.choose(context,"en");Ui.init(context);
        VelaEdition.prefs(context).edit().clear().commit();context.getSharedPreferences("personalization",0).edit().clear().commit();context.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();LiteUpdateManager.prefs(context).edit().putBoolean("automatic",false).commit();
        boolean priorArt=new File(context.getCacheDir(),"plex-art").exists();phase="first-launch";getContext().startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity chooser=foreground(EditionActivity.class.getName());check(VelaEdition.mode(context).isEmpty(),"Fresh install silently selected a mode");shot("01-choose");
        phase="lite-mode";card(chooser,"Lite");Activity lite=foreground(LiteHomeActivity.class.getName());check(VelaEdition.lite(context)&&field(lite,"shell")!=null,"Lite controller did not start");
        check(priorArt||!new File(context.getCacheDir(),"plex-art").exists(),"Lite initialized multimedia artwork");for(Activity a:opened)if(a instanceof HomeActivity)check(field(a,"home")==null,"Lite constructed the Full home controller");
        Method preview=LiteHomeActivity.class.getDeclaredMethod("show",int.class,boolean.class);preview.setAccessible(true);final Activity previewLite=lite;ui(()->{try{preview.invoke(previewLite,0,false);}catch(Exception e){throw new RuntimeException(e);}});shot("02-lite");
        VelaWorkspace.prefs(context,"lite_home").edit().putInt("start_section",2).commit();VelaWorkspace.prefs(context,"vela-remote").edit().putInt("voice_key",KeyEvent.KEYCODE_F6).commit();
        // Same DVB alias and correction keys as the old Lite package.
        LiveTvData.Channel channel=new LiveTvData.Channel(37,"fixture-input","7","TDP HD",false,true,false,"network:service");
        check(channel.identity.equals("dvb:"+LiveTvData.hash("t:network:service")),"Antenna identity changed");VelaGuideFix.set(context,"TDP HD","Teledeporte.es",60);check(VelaGuideFix.alias(context,"TDP HD").equals("Teledeporte.es"),"Lite EPG correction lost its key");
        phase="settings-mode-entry";final Activity liteSettings=lite;Method show=LiteHomeActivity.class.getDeclaredMethod("show",int.class,boolean.class);show.setAccessible(true);ui(()->{try{show.invoke(liteSettings,3,false);}catch(Exception e){throw new RuntimeException(e);}});Object settings=field(lite,"settingsView");check(settings!=null,"Lite settings are not embedded");View[] categories=(View[])field(settings,"categories");ui(categories[3]::requestFocus);shot("03-settings");
        View change=title(lite.findViewById(android.R.id.content),"Launcher mode");check(change!=null,"Mode switch is missing from System");ui(()->{View v=change;while(v!=null&&!v.isClickable())v=v.getParent() instanceof View?(View)v.getParent():null;if(v!=null)v.performClick();});chooser=foreground(EditionActivity.class.getName());
        if(VelaEdition.fullAvailable()){
            context.getSharedPreferences("plex",0).edit().putString("accountToken","synthetic-retained-session").commit();
            phase="full-mode";card(chooser,"Full");Activity full=foreground(HomeActivity.class.getName());check(!VelaEdition.lite(context)&&field(full,"home")!=null,"Full controller did not start");shot("04-full");
            check(context.getSharedPreferences("plex",0).getString("accountToken","").equals("synthetic-retained-session"),"Mode switch deleted an account");
            ui(()->VelaEdition.chooser(full));chooser=foreground(EditionActivity.class.getName());card(chooser,"Lite");lite=foreground(LiteHomeActivity.class.getName());
            check(VelaWorkspace.prefs(context,"lite_home").getInt("start_section",-1)==2&&VelaWorkspace.prefs(context,"vela-remote").getInt("voice_key",0)==KeyEvent.KEYCODE_F6,"Mode switch reset profile settings");
            check(context.getSharedPreferences("plex",0).getString("accountToken","").equals("synthetic-retained-session"),"Lite removed a saved Full account");
            context.getSharedPreferences("plex",0).edit().clear().commit();
        }else{
            View label=title(chooser.findViewById(android.R.id.content),"Full");check(label!=null&&!((View)label.getParent()).isEnabled(),"Android 8 offered an unsupported Full controller");card(chooser,"Lite");lite=foreground(LiteHomeActivity.class.getName());
        }
        phase="home-key-routing";getContext().startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));foreground(LiteHomeActivity.class.getName());check(VelaEdition.lite(context),"HOME changed selected mode");
        phase="settings-pin";VelaSessions.setPin(context,"2468");VelaSessions.lock();boolean locked=false;try{VelaEdition.select(context,"lite");}catch(IllegalStateException expected){locked=true;}check(locked,"Mode selection bypassed the settings PIN");VelaSessions.unlock(context,"2468");VelaSessions.prefs(context).edit().clear().commit();
        phase="unified-update-channel";String hash=String.join("",Collections.nCopies(64,"a"));JSONObject a=new JSONObject().put("tag_name","v2.0.1-preview").put("draft",false).put("prerelease",true).put("assets",new JSONArray().put(new JSONObject().put("name","Vela-2.0.1-preview.apk").put("browser_download_url",LiteReleaseFeed.REPO+"/releases/download/v2.0.1-preview/Vela-2.0.1-preview.apk").put("state","uploaded").put("size",2000).put("digest","sha256:"+hash)));
        LiteReleaseFeed.Release release=LiteReleaseFeed.newest(new JSONArray().put(a),"2.0.0-preview",true,true);check(release!=null&&release.json().getJSONArray("assets").getJSONObject(0).getString("name").equals("Vela-2.0.1-preview.apk"),"Unified Lite uses the legacy APK");check(LiteReleaseFeed.newest(new JSONArray().put(a),"0.18.0-preview",true)==null,"Legacy Lite accepted another package's feed");check(LiteUpdateManager.authority(context).equals("tv.vela.launcher.lite-updates"),"Installer provider collides with Full");
        result.putString("result","PASS native first-run choice, Lite controller without multimedia artwork, embedded mode settings, Android 8 Full refusal / modern reversible Full-Lite flow, saved profile/account retention, unchanged antenna identities/EPG fixes, HOME routing and unified-vs-legacy update channel. Owned emulator, synthetic data only.");
    }catch(Throwable e){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));try{shot("failure");}catch(Exception ignored){}}
    finally{try{for(Activity a:opened)if(!a.isFinishing())ui(a::finish);VelaSessions.prefs(context).edit().clear().commit();context.getSharedPreferences("plex",0).edit().remove("accountToken").commit();VelaEdition.prefs(context).edit().clear().commit();context.getSharedPreferences("personalization",0).edit().clear().commit();}catch(Exception ignored){}}
    finish(result.containsKey("error")?1:-1,result);}
}
