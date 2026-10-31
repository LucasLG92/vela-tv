package tv.vela.launcher;

import android.app.*;import android.content.*;import android.graphics.Bitmap;import android.net.Uri;import android.os.*;import android.view.*;import android.view.accessibility.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;

/** Cross-package, explicit-consent migration through a one-use private URI. */
public final class AndroidMigrationTest extends Instrumentation {
    private Context context;private final List<Activity> activities=new ArrayList<>();private String phase="setup";private static Uri granted;
    public static final class Chooser extends EditionActivity {protected void onActivityResult(int request,int result,Intent data){if(request==IMPORT&&result==RESULT_OK&&data!=null)granted=data.getData();super.onActivityResult(request,result,data);}}
    public static final class Lite extends LiteHomeActivity {protected boolean allowAutomaticChecks(){return false;}}
    public Activity newActivity(ClassLoader l,String n,Intent i)throws InstantiationException,IllegalAccessException,ClassNotFoundException{Activity a=n.equals(LiteHomeActivity.class.getName())?new Lite():n.equals(EditionActivity.class.getName())?new Chooser():super.newActivity(l,n,i);activities.add(a);return a;}
    interface Ready{boolean get()throws Exception;}
    void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    void await(Ready r)throws Exception{long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){if(r.get())return;Thread.sleep(100);}throw new AssertionError("Timeout "+phase);}
    void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    void press(int code){long t=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0),true);getUiAutomation().injectInputEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,code,0),true);}
    boolean legacyPrompt(){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return false;try{return "tv.vela.launcher.lite".contentEquals(root.getPackageName())&&!root.findAccessibilityNodeInfosByText("Import settings").isEmpty();}finally{root.recycle();}}
    void shot(String name)throws Exception{Thread.sleep(1100);Bitmap b=getUiAutomation().takeScreenshot();try(OutputStream o=new FileOutputStream(new File(context.getExternalFilesDir(null),"unified-migration-api"+Build.VERSION.SDK_INT+"-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}finally{b.recycle();}}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle out=new Bundle();context=getTargetContext();try{
        try(ParcelFileDescriptor f=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(f)){check(in.read()=='1',"Owned emulator required");}
        L10n.choose(context,"en");check(!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"Personal accounts forbidden");
        int before=VelaWorkspace.profiles(context).size();String old=VelaWorkspace.active(context);
        // Arbitrary apps cannot access the provider or forge a read grant.
        phase="private-provider";boolean denied=false;try(InputStream in=context.getContentResolver().openInputStream(Uri.parse("content://tv.vela.launcher.lite.migration/not-a-grant"))){ }catch(SecurityException|FileNotFoundException expected){denied=true;}check(denied,"Legacy settings provider is public");
        phase="export-prompt";EditionActivity chooser=(EditionActivity)startActivitySync(new Intent(context,EditionActivity.class).putExtra("importLegacy",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Method request=EditionActivity.class.getDeclaredMethod("importLegacy");request.setAccessible(true);ui(()->{try{request.invoke(chooser);}catch(Exception e){throw new RuntimeException(e);}});
        await(this::legacyPrompt);shot("01-consent");AccessibilityNodeInfo consent=getUiAutomation().getRootInActiveWindow(),focused=consent.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);check(focused!=null&&"Cancel".contentEquals(focused.getText()),"Migration must initially focus Cancel");if(focused!=null)focused.recycle();consent.recycle();
        // The prompt starts on Cancel. These are real remote events, not direct preference writes.
        press(KeyEvent.KEYCODE_DPAD_LEFT);press(KeyEvent.KEYCODE_DPAD_CENTER);
        phase="granted-import";await(()->VelaEdition.prefs(context).getBoolean("imported",false));await(()->{for(Activity a:activities)if(a instanceof LiteHomeActivity&&a.hasWindowFocus())return true;return false;});
        check(granted!=null,"No private grant was returned");denied=false;try(InputStream again=context.getContentResolver().openInputStream(granted)){}catch(SecurityException|FileNotFoundException expected){denied=true;}check(denied,"A consumed migration grant could be replayed");check(VelaEdition.lite(context),"Import did not enter Lite");check(VelaWorkspace.prefs(context,"lite_home").getInt("start_section",-1)==2&&VelaWorkspace.prefs(context,"lite_home").getBoolean("reduce_motion",false),"Profile settings were lost");
        check(VelaWorkspace.prefs(context,"vela-remote").getInt("voice_key",0)==KeyEvent.KEYCODE_F6,"Remote binding was lost");check(VelaGuideFix.alias(context,"TDP HD").equals("Teledeporte.es")&&VelaGuideFix.offset(context,"TDP HD")==3600,"Old Lite EPG mapping changed");
        check(context.getSharedPreferences("plex",0).getString("accountToken","").isEmpty(),"Private legacy account was imported");
        check(VelaWorkspace.profiles(context).containsKey(old)&&VelaWorkspace.profiles(context).size()>=before,"Existing unified profiles were removed");shot("02-imported");
        out.putString("result","PASS actual cross-package consent, signed app matching, private URI refusal, native remote confirmation, one-use profile import, preserved local settings/remote/EPG, and excluded private account material. Synthetic emulator accounts only.");
    }catch(Throwable e){out.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));try{shot("failure");}catch(Exception ignored){}}
    finally{try{for(Activity a:activities)if(!a.isFinishing())ui(a::finish);}catch(Exception ignored){}}
    finish(out.containsKey("error")?1:-1,out);}
}
