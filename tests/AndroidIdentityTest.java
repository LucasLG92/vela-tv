package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual remote access and launch routing after moving the recent shortcut into Apps. */
public final class AndroidIdentityTest extends Instrumentation {
    private Context context;private Activity activity;private String previous,profile,language,phase="setup";private boolean baseline;private String captureLanguage="es";
    interface Ready{boolean value()throws Exception;}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    Object field(Object owner,String name)throws Exception{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    Object call(Object owner,String name,Class<?>[] types,Object... args){try{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}catch(NoSuchMethodException next){}throw new NoSuchMethodException(name);}catch(Exception e){throw new RuntimeException(e);}}
    void ui(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    void key(int key){sendKeyDownUpSync(key);waitForIdleSync();}
    void await(Ready ready)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(ready.value())return;Thread.sleep(70);}throw new AssertionError("Timeout: "+phase);}
    View recent(View root){if(root instanceof VelaRecentActivity.Card)return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=recent(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
    int brands(View root){if(!root.isShown())return 0;int count=root instanceof ImageView&&"Vela".contentEquals(root.getContentDescription()==null?"":root.getContentDescription())?1:0;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)count+=brands(((ViewGroup)root).getChildAt(i));return count;}
    void shot(String name)throws Exception{Thread.sleep(250);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null&&b.getWidth()==3840,"Native 4K capture required");save(b,"identity-"+name+".png");b.recycle();}
    void save(Bitmap b,String name)throws Exception{try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    void logo()throws Exception{Bitmap image=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Drawable mark=context.getDrawable(R.drawable.vela_mark);mark.setBounds(0,0,512,512);mark.draw(new Canvas(image));save(image,"identity-logo-"+(baseline?"before":"after")+".png");image.recycle();}
    @Override public void onCreate(Bundle args){super.onCreate(args);baseline="before".equals(args.getString("fixture"));captureLanguage=args.getString("language","es");start();}
    @Override public void onStart(){Bundle result=new Bundle();context=getTargetContext();ActivityMonitor monitor=null;try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        logo();if(baseline){result.putString("result","PASS native original vector render");finish(-1,result);return;}
        boolean lite=context.getPackageName().endsWith(".lite");previous=VelaWorkspace.active(context);language=L10n.choice();profile=VelaWorkspace.create(context,"Alex");VelaWorkspace.select(context,profile);L10n.choose(context,captureLanguage);Ui.init(context);
        context.getSharedPreferences("personalization",0).edit().putBoolean("initialized",true).putBoolean("complete",true).commit();VelaWorkspace.prefs(context,lite?"lite_updates":"updates").edit().putBoolean("automatic",false).commit();VelaRecentActivity.save(context,"app","com.android.settings",context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo("com.android.settings",0)).toString(),"");
        activity=startActivitySync(new Intent().setClassName(context.getPackageName(),"tv.vela.launcher."+(lite?"LiteHomeActivity":"HomeActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Object owner=lite?activity:field(activity,"home");
        check(recent(activity.getWindow().getDecorView())==null,"Recent shortcut still overlays Home/Inputs");check(brands(activity.getWindow().getDecorView())==0,"Brand mark remains outside Settings");
        phase="apps";if(lite)ui(()->call(activity,"show",new Class[]{int.class,boolean.class},2,false));else ui(()->call(owner,"showApps",new Class[]{View.class},activity.getCurrentFocus()));
        await(()->recent(activity.getWindow().getDecorView())!=null);View action=recent(activity.getWindow().getDecorView());
        if(lite)await(()->((List<?>)field(activity,"tiles")).size()>=3);else await(()->((GridView)field(field(field(owner,"panels"),"screen"),"grid")).getCount()>0);
        Thread.sleep(500);shot("01-apps");
        if(lite){List<View> apps=(List<View>)field(activity,"tiles");ui(apps.get(2)::requestFocus);}else{GridView grid=(GridView)field(field(field(owner,"panels"),"screen"),"grid");ui(grid::requestFocus);}
        await(()->activity.hasWindowFocus());key(KeyEvent.KEYCODE_DPAD_UP);check(action.hasFocus(),"Up from Apps cannot reach Recent activity: focus="+activity.getCurrentFocus()+" action="+action.getId());key(KeyEvent.KEYCODE_DPAD_LEFT);check(!action.hasFocus(),"Left did not reach folders");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(action.hasFocus(),"Right did not return to Recent activity");shot("02-recent-focused");key(KeyEvent.KEYCODE_DPAD_DOWN);check(!action.hasFocus(),"Down did not return to app icons");
        phase="launch-routing";Intent target=VelaRecentActivity.intent(context,VelaRecentActivity.read(context));check(target!=null&&target.getComponent()!=null,"Known app cannot resolve");monitor=addMonitor(target.getComponent().getClassName(),null,true);ui(action::performClick);check(monitor.getHits()==1,"Recent shortcut did not launch its original app");removeMonitor(monitor);monitor=null;
        phase="settings";if(lite)ui(()->call(activity,"show",new Class[]{int.class,boolean.class},3,false));else ui(()->call(owner,"openPanel",new Class[]{Intent.class},new Intent().setClassName(context.getPackageName(),"tv.vela.launcher.SettingsActivity")));
        check(brands(activity.getWindow().getDecorView())==1,"Settings must contain a single brand mark");shot("03-settings");
        View settingsNav=lite?((View[])field(activity,"navigation"))[3]:((List<View>)field(owner,"sideButtons")).get(7);ui(settingsNav::requestFocus);Thread.sleep(180);check(brands(activity.getWindow().getDecorView())==1,"Expanded rail duplicates Settings branding");shot("04-settings-navigation");key(KeyEvent.KEYCODE_DPAD_RIGHT);
        phase="tools";ui(()->((VelaHub.Owner)activity).tools(2));check(brands(activity.getWindow().getDecorView())==0,"Personalisation repeats the Settings mark");shot("05-tools");ui(settingsNav::requestFocus);Thread.sleep(180);shot("06-tools-navigation");
        VelaRecentActivity.save(context,"app","org.example.missing","Unavailable","");Method available=VelaRecentActivity.class.getDeclaredMethod("available",Context.class);available.setAccessible(true);org.json.JSONObject unavailable=(org.json.JSONObject)available.invoke(null,context);check(unavailable.length()==0,"Removed app leaves a broken recent action");
        result.putString("result","PASS clean Home/Inputs, Apps shortcut D-pad routing and original app launch, single Settings identity, unbranded personalisation and native 4K screenshots.");
    }catch(Throwable failure){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(failure));}finally{try{if(monitor!=null)removeMonitor(monitor);if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}if(language!=null)L10n.choose(context,language);}catch(Exception ignored){}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
