package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.FutureTask;
import org.json.*;

/** Actual first run and reversible native controllers, including the Android 8 boundary. */
public final class AndroidUnifiedTest extends Instrumentation {
    private Context context;private final Bundle result=new Bundle();private String phase="setup";
    private final List<Activity> opened=new ArrayList<>();
    static TvInputs.Source fixtureInputs(){return new TvInputs.Source(){
            public List<TvInputs.Entry> read(){return Arrays.asList(new TvInputs.Entry("fixture-hdmi-1","fixture-hdmi-1","HDMI 1","Nintendo Switch","switch",TvInputs.CONNECTED,false),new TvInputs.Entry("fixture-hdmi-2","fixture-hdmi-2","HDMI 2","Computer","pc",TvInputs.CONNECTED,false));}
            public void start(Runnable changed){}public void stop(){}
        };}
    public static final class Lite extends LiteHomeActivity {
        protected boolean allowAutomaticChecks(){return false;}
        protected TvInputs.Source createInputsSource(){return fixtureInputs();}
    }
    public static final class Full extends HomeActivity {
        @Override TvInputs.Source createInputsSource(){return fixtureInputs();}
    }
    @Override public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{
        Activity a=name.equals(LiteHomeActivity.class.getName())?new Lite():name.equals(HomeActivity.class.getName())?new Full():super.newActivity(loader,name,intent);opened.add(a);return a;
    }
    interface Ready{boolean get()throws Exception;}
    void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    void openSearch(Activity home)throws Exception{
        context.startActivity(new Intent(Intent.ACTION_SEARCH).setClass(context,HomeActivity.class)
                .putExtra(SearchManager.QUERY,"HDMI").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->home.hasWindowFocus()&&field(home,"hub")!=null);
    }
    void verifyRecoveryEntry(Activity full,Intent icon)throws Exception{
        context.startActivity(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity standalone=foreground(SettingsActivity.class.getName());
        Path crashes=new File(context.getFilesDir(),"home-crashes.txt").toPath();
        byte[] original=Files.exists(crashes)?Files.readAllBytes(crashes):null;
        try{
            String now=Long.toString(System.currentTimeMillis());
            Files.write(crashes,Arrays.asList(now,now,now));
            context.startActivity(icon);
            await(()->standalone.hasWindowFocus()&&standalone.getIntent().hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER));
        }finally{
            if(original==null)Files.deleteIfExists(crashes);else Files.write(crashes,original);
        }
        context.startActivity(icon);
        await(()->standalone.isFinishing()&&full.hasWindowFocus()
                &&((CinemaHomeView)field(full,"home")).navigationDestination()==7);
    }
    void await(Ready ready)throws Exception{
        long end=SystemClock.elapsedRealtime()+18000;
        while(SystemClock.elapsedRealtime()<end){
            FutureTask<Boolean> check=new FutureTask<>(ready::get);
            runOnMainSync(check);
            if(check.get())return;
            Thread.sleep(80);
        }
        throw new AssertionError("Timeout "+phase);
    }
    Object field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    Activity foreground(String name)throws Exception{final Activity[] found={null};await(()->{for(Activity a:opened)if(a.getClass().getName().equals(name)||a instanceof Full&&name.equals(HomeActivity.class.getName())||a instanceof Lite&&name.equals(LiteHomeActivity.class.getName()))if(a.hasWindowFocus()&&!a.isFinishing()){found[0]=a;return true;}return false;});return found[0];}
    View title(View root,String text){if(root instanceof TextView){String value=((TextView)root).getText().toString();if(value.equals(text)||value.startsWith(text+"\n"))return root;}if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=title(((ViewGroup)root).getChildAt(i),text);if(v!=null)return v;}return null;}
    void card(Activity a,String title)throws Exception{View label=title(a.findViewById(android.R.id.content),title);check(label!=null,"Missing card "+title);ui(()->((View)label.getParent()).performClick());}
    void press(Activity a,String label){
        View control=title(a.findViewById(android.R.id.content),label);
        check(control!=null,"Missing option "+label);
        while(!control.isClickable()&&control.getParent() instanceof View)control=(View)control.getParent();
        View target=control;check(target.isClickable(),"Inactive option "+label);
        ui(target::requestFocus);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
    }
    void chooseHdmi(Activity activity,TvInputs.Layout target)throws Exception{
        View root=activity.findViewById(android.R.id.content);
        check(title(root,"HDMI layout")==null,"The old Inputs layout button is still visible");
        boolean lite=activity instanceof LiteHomeActivity;
        CinemaHomeView home=lite?null:(CinemaHomeView)field(activity,"home");
        Method show=LiteHomeActivity.class.getDeclaredMethod("show",int.class,boolean.class);show.setAccessible(true);
        if(lite)ui(()->{try{show.invoke(activity,3,false);}catch(Exception e){throw new RuntimeException(e);}});
        else ui(home::settings);
        Object page=lite?field(activity,"settingsView"):field(((HomeScreenHost)field(home,"panels")).screen,"view");
        View[] categories=(View[])field(page,"categories");
        ui(categories[1]::requestFocus);
        for(int key:new int[]{KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_CENTER}){
            sendKeyDownUpSync(key);waitForIdleSync();
        }
        int at=TvInputs.Layout.selected(context).ordinal(),goal=target.ordinal();
        for(int i=0;i<Math.abs(at-goal);i++)sendKeyDownUpSync(goal>at?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
        check(TvInputs.Layout.selected(context)==target,"Choosing a layout from Appearance did not save");
        check(title(activity.getCurrentFocus(),"HDMI layout")!=null,"Focus did not return to the HDMI setting");
        if(lite)ui(()->{try{show.invoke(activity,0,false);}catch(Exception e){throw new RuntimeException(e);}});
        else ui(()->home.navigateSection(5));
    }
    void verifyLiveStyle(Activity activity,VelaHdmiBrowser browser,boolean cinema)throws Exception{
        check(browser!=null&&browser.rows.size()==2,"Missing HDMI layout");
        ui(browser.rows.get(0)::requestFocus);
        sendKeyDownUpSync(cinema?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();
        check(browser.rows.get(1).hasFocus(),"Input navigation lost focus");
        await(()->title(activity.findViewById(android.R.id.content),"Preview unavailable")!=null);
        check(field(browser,"video")==null,"Unsupported input held a session");
        sendKeyDownUpSync(cinema?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();
        check(browser.rows.get(0).hasFocus(),"Cannot return to first input");
        await(()->title(activity.findViewById(android.R.id.content),"Preview unavailable")!=null);
        shot(cinema?"hdmi-cinema":"hdmi-monitor");
        final Intent[] launched={null};
        ActivityMonitor open=new ActivityMonitor(){
            @Override public ActivityResult onStartActivity(Intent intent){
                launched[0]=intent;return new ActivityResult(Activity.RESULT_CANCELED,null);
            }
        };
        addMonitor(open);
        try{
            check(browser.rows.get(0).hasFocus(),"Focus left selected HDMI after preview failure");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
            await(()->launched[0]!=null);
            check(Intent.ACTION_VIEW.equals(launched[0].getAction())&&android.media.tv.TvContract.buildChannelUriForPassthroughInput("fixture-hdmi-1").equals(launched[0].getData()),"OK opened the wrong HDMI");
        }finally{removeMonitor(open);}
    }
    void shot(String name)throws Exception{Thread.sleep(350);Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"No native screenshot");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"unified-api"+Build.VERSION.SDK_INT+"-"+name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor f=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(f)){check(in.read()=='1',"Owned emulator required");}
        check(context.getPackageName().equals("tv.vela.launcher"),"Unified package required");
        check(!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"Personal accounts forbidden");
        RefinementHttp.install(url->null);L10n.choose(context,"en");Ui.init(context);
        VelaEdition.prefs(context).edit().clear().commit();context.getSharedPreferences("personalization",0).edit().clear().commit();context.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();LiteUpdateManager.prefs(context).edit().putBoolean("automatic",false).commit();
        boolean priorArt=new File(context.getCacheDir(),"plex-art").exists();phase="first-launch";context.startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity chooser=foreground(EditionActivity.class.getName());check(VelaEdition.mode(context).isEmpty(),"Fresh install silently selected a mode");shot("01-choose");
        phase="lite-mode";card(chooser,"Lite");
        View devices=title(chooser.findViewById(android.R.id.content),"HDMI and apps");
        check(devices!=null&&devices.isFocusable()&&VelaEdition.mode(context).isEmpty(),"Preview committed a mode early");
        check(title(chooser.findViewById(android.R.id.content),"Library")==null,"Lite offered a media library");
        ui(devices::requestFocus);shot("01b-layout");
        ui(devices::performClick);check(VelaEdition.mode(context).isEmpty(),"HDMI layout step committed the install early");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();press(chooser,"HDMI and apps");
        check(title(chooser.findViewById(android.R.id.content),"Device cards")!=null,"Classic layout missing in setup");
        shot("hdmi-choose");press(chooser,"Preview window");check(VelaLayout.DEVICES.equals(VelaLayout.start(context)),"Layout not persisted");
        Activity lite=foreground(LiteHomeActivity.class.getName());check(VelaEdition.lite(context)&&field(lite,"shell")!=null,"Lite controller did not start");
        check(priorArt||!new File(context.getCacheDir(),"plex-art").exists(),"Lite initialized multimedia artwork");for(Activity a:opened)if(a instanceof HomeActivity)check(field(a,"home")==null,"Lite constructed the Full home controller");
        Method preview=LiteHomeActivity.class.getDeclaredMethod("show",int.class,boolean.class);preview.setAccessible(true);final Activity previewLite=lite;ui(()->{try{preview.invoke(previewLite,0,false);}catch(Exception e){throw new RuntimeException(e);}});shot("02-lite");
        phase="hdmi-window";verifyLiveStyle(lite,(VelaHdmiBrowser)field(lite,"hdmi"),false);
        chooseHdmi(lite,TvInputs.Layout.CINEMA);
        phase="hdmi-cinema";verifyLiveStyle(lite,(VelaHdmiBrowser)field(lite,"hdmi"),true);
        check(TvInputs.Layout.selected(context)==TvInputs.Layout.CINEMA,"HDMI layout did not persist");
        VelaWorkspace.prefs(context,"lite_home").edit().putInt("start_section",2).commit();VelaWorkspace.prefs(context,"vela-remote").edit().putInt("voice_key",KeyEvent.KEYCODE_F6).commit();
        // Same DVB alias and correction keys as the old Lite package.
        LiveTvData.Channel channel=new LiveTvData.Channel(37,"fixture-input","7","TDP HD",false,true,false,"network:service");
        check(channel.identity.equals("dvb:"+LiveTvData.hash("t:network:service")),"Antenna identity changed");VelaGuideFix.set(context,"TDP HD","Teledeporte.es",60);check(VelaGuideFix.alias(context,"TDP HD").equals("Teledeporte.es"),"Lite EPG correction lost its key");
        phase="settings-mode-entry";final Activity liteSettings=lite;Method show=LiteHomeActivity.class.getDeclaredMethod("show",int.class,boolean.class);show.setAccessible(true);ui(()->{try{show.invoke(liteSettings,3,false);}catch(Exception e){throw new RuntimeException(e);}});Object settings=field(lite,"settingsView");check(settings!=null,"Lite settings are not embedded");View[] categories=(View[])field(settings,"categories");ui(categories[3]::requestFocus);shot("03-settings");
        View change=title(lite.findViewById(android.R.id.content),"Launcher mode");check(change!=null,"Mode switch is missing from System");ui(()->{View v=change;while(v!=null&&!v.isClickable())v=v.getParent() instanceof View?(View)v.getParent():null;if(v!=null)v.performClick();});chooser=foreground(EditionActivity.class.getName());
        if(VelaEdition.fullAvailable()){
            context.getSharedPreferences("plex",0).edit().putString("accountToken","synthetic-retained-session").commit();
            phase="full-mode";card(chooser,"Full");Activity full=foreground(HomeActivity.class.getName());check(!VelaEdition.lite(context)&&field(full,"home")!=null,"Full controller did not start");
            CinemaHomeView cinema=(CinemaHomeView)field(full,"home");
            await(()->title(full.findViewById(android.R.id.content),"All apps")!=null);
            check(cinema.navigationDestination()==0,"Full HDMI preset did not stay under Home");
            phase="full-hdmi";InputsScreen input=(InputsScreen)((HomeScreenHost)field(cinema,"panels")).screen;
            check(field(input,"hdmi")!=null,"Mode switch lost the selected HDMI layout");
            verifyLiveStyle(full,(VelaHdmiBrowser)field(input,"hdmi"),true);
            chooseHdmi(full,TvInputs.Layout.MONITOR);
            CinemaHomeView windowHome=(CinemaHomeView)field(full,"home");
            InputsScreen window=(InputsScreen)((HomeScreenHost)field(windowHome,"panels")).screen;
            verifyLiveStyle(full,(VelaHdmiBrowser)field(window,"hdmi"),false);
            chooseHdmi(full,TvInputs.Layout.CARDS);
            CinemaHomeView restored=(CinemaHomeView)field(full,"home");
            InputsScreen classic=(InputsScreen)((HomeScreenHost)field(restored,"panels")).screen;
            check(field(classic,"hdmi")==null&&TvInputs.Layout.selected(context)==TvInputs.Layout.CARDS,"Cards kept a preview session");
            shot("04-full");
            VelaLayout.save(context,VelaLayout.preset(VelaLayout.read(context),VelaLayout.TELEVISION),true);
            LiveTvData.guideView(context,false);
            ui(restored::reset);
            View guide=title(full.findViewById(android.R.id.content),"Programme guide");
            check(guide!=null&&restored.navigationDestination()==0,"Television Home has no guide shortcut");
            ui(guide::performClick);
            check(restored.navigationDestination()==6&&(Boolean)field(field(restored,"liveTv"),"guideMode"),"Guide shortcut did not open the guide");
            check(!LiveTvData.guideView(context),"Guide shortcut changed the TV display preference");
            VelaLayout.save(context,VelaLayout.previous(context),true);
            check(context.getSharedPreferences("plex",0).getString("accountToken","").equals("synthetic-retained-session"),"Mode switch deleted an account");
            phase="full-icon-after-search";
            openSearch(full);
            Intent icon=context.getPackageManager().getLeanbackLaunchIntentForPackage(context.getPackageName());
            check(icon!=null,"Vela has no TV launcher icon");
            context.startActivity(icon);
            await(()->full.hasWindowFocus()&&field(full,"hub")==null
                    &&((CinemaHomeView)field(full,"home")).navigationDestination()==7);
            phase="reused-settings-icon";
            verifyRecoveryEntry(full,icon);
            ui(()->VelaEdition.chooser(full));chooser=foreground(EditionActivity.class.getName());card(chooser,"Lite");lite=foreground(LiteHomeActivity.class.getName());
            check(VelaWorkspace.prefs(context,"lite_home").getInt("start_section",-1)==2&&VelaWorkspace.prefs(context,"vela-remote").getInt("voice_key",0)==KeyEvent.KEYCODE_F6,"Mode switch reset profile settings");
            check(context.getSharedPreferences("plex",0).getString("accountToken","").equals("synthetic-retained-session"),"Lite removed a saved Full account");
            context.getSharedPreferences("plex",0).edit().clear().commit();
        }else{
            View label=title(chooser.findViewById(android.R.id.content),"Full");check(label!=null&&!((View)label.getParent()).isEnabled(),"Android 8 offered an unsupported Full controller");card(chooser,"Lite");lite=foreground(LiteHomeActivity.class.getName());
        }
        phase="preset-rollback";
        org.json.JSONObject original=VelaLayout.read(context);
        VelaLayout.save(context,VelaLayout.preset(original,VelaLayout.TELEVISION),true);
        check(LiteHomeActivity.startSection(context,2)==1,"Television preset did not route to TV");
        check(VelaLayout.previous(context).toString().equals(original.toString()),"Previous Home lost");
        VelaLayout.save(context,VelaLayout.previous(context),true);
        check(LiteHomeActivity.startSection(context,2)==0,"Restored HDMI Home did not route to inputs");
        phase="lite-home-after-search";
        final Activity activeLite=lite;
        View[] navigation=(View[])field(activeLite,"navigation");
        ui(navigation[3]::performClick);
        check(field(activeLite,"settingsView")!=null,"Lite Settings did not open");
        openSearch(activeLite);
        context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        // Android TV can open HOME in a distinct task from the activity that handled SEARCH.
        await(()->{
            for(Activity a:opened){
                if(a instanceof LiteHomeActivity&&a.hasWindowFocus()&&a.getIntent().hasCategory(Intent.CATEGORY_HOME))
                    return field(a,"hub")==null&&((Integer)field(a,"section"))==0;
            }
            return false;
        });
        check(VelaEdition.lite(context),"HOME changed selected mode");
        phase="settings-pin";VelaSessions.setPin(context,"2468");VelaSessions.lock();boolean locked=false;try{VelaEdition.select(context,"lite");}catch(IllegalStateException expected){locked=true;}check(locked,"Mode selection bypassed the settings PIN");VelaSessions.unlock(context,"2468");VelaSessions.prefs(context).edit().clear().commit();
        phase="unified-update-channel";String hash=String.join("",Collections.nCopies(64,"a"));JSONObject a=new JSONObject().put("tag_name","v2.0.1-preview").put("draft",false).put("prerelease",true).put("assets",new JSONArray().put(new JSONObject().put("name","Vela-2.0.1-preview.apk").put("browser_download_url",LiteReleaseFeed.REPO+"/releases/download/v2.0.1-preview/Vela-2.0.1-preview.apk").put("state","uploaded").put("size",2000).put("digest","sha256:"+hash)));
        LiteReleaseFeed.Release release=LiteReleaseFeed.newest(new JSONArray().put(a),"2.0.0-preview",true,true);check(release!=null&&release.json().getJSONArray("assets").getJSONObject(0).getString("name").equals("Vela-2.0.1-preview.apk"),"Unified Lite uses the legacy APK");check(LiteReleaseFeed.newest(new JSONArray().put(a),"0.18.0-preview",true)==null,"Legacy Lite accepted another package's feed");check(LiteUpdateManager.authority(context).equals("tv.vela.launcher.lite-updates"),"Installer provider collides with Full");
        result.putString("result","PASS three HDMI layouts, remote selection, preview failure and native passthrough, per-profile mode persistence, native first-run choices and saved presets, Home-to-guide routing, Lite controller without multimedia artwork, embedded mode settings, Android 8 Full refusal / modern reversible Full-Lite flow, saved profile/account retention, unchanged antenna identities/EPG fixes, search and HOME routing across mode changes, launcher-icon and recovery routing, and the unified update channel. Owned emulator, synthetic data only.");
    }catch(Throwable e){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));try{shot("failure");}catch(Exception ignored){}}
    finally{try{for(Activity a:opened)if(!a.isFinishing())ui(a::finish);VelaSessions.prefs(context).edit().clear().commit();context.getSharedPreferences("plex",0).edit().remove("accountToken").commit();VelaEdition.prefs(context).edit().clear().commit();context.getSharedPreferences("personalization",0).edit().clear().commit();TvInputs.prefs(context).edit().remove("hdmi_layout").commit();VelaLayout.prefs(context).edit().remove("document").remove("previous").commit();}catch(Exception ignored){}}
    finish(result.containsKey("error")?1:-1,result);}
}
