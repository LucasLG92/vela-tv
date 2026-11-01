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

/** Shared Lite Home editor: actual D-pad navigation and an isolated, credential-free profile. */
public final class AndroidHomeEditorTest extends Instrumentation {
    private Context context;private LiteHomeActivity activity;private String previous,profile,mode,phase="setup",language;
    private final Bundle result=new Bundle();
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}
    private void ui(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void key(int key){sendKeyDownUpSync(key);waitForIdleSync();}
    private VelaHomeEditor editor()throws Exception{ui(activity::toolsLayout);long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();Object hub=field(activity,"hub");if(hub!=null){Object editor=field(hub,"homeEditor");if(editor!=null)return (VelaHomeEditor)editor;}Thread.sleep(40);}throw new AssertionError("Editor did not open");}
    private void shot(String name)throws Exception{Thread.sleep(200);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"Native screenshot unavailable");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"home-editor-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private String inputTitle(){return language.equals("en")?"My inputs":"Mis entradas";}
    public void onCreate(Bundle args){super.onCreate(args);language=args.getString("language","es");start();}
    public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Vela · Demo");VelaWorkspace.select(context,profile);L10n.choose(context,language);Ui.init(context);
        mode=VelaEdition.prefs(context).getString("mode","");VelaEdition.select(context,"lite");VelaWorkspace.prefs(context,"lite_updates").edit().putBoolean("automatic",false).commit();
        activity=(LiteHomeActivity)startActivitySync(new Intent(context,LiteHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        phase="lite-scope";VelaHomeEditor editor=editor();TextView[] tabs=(TextView[])field(editor,"tabs");check(tabs[1].getVisibility()==View.GONE&&tabs[2].getVisibility()==View.GONE,"Lite exposed inactive media-service settings");
        List<String> rows=(List<String>)field(editor,"keys");check(rows.size()==3&&rows.containsAll(Arrays.asList("inputs","tv","apps")),"Lite media destinations changed");
        phase="remote-focus";ui(editor::focusFirst);boolean enteringFromTouch=editor.list.getSelectedItemPosition()<0;key(KeyEvent.KEYCODE_DPAD_DOWN);if(enteringFromTouch)key(KeyEvent.KEYCODE_DPAD_DOWN);check(editor.list.getSelectedItemPosition()==1,"Down did not select the next row");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(!editor.list.hasFocus(),"Right did not enter the section controls");key(KeyEvent.KEYCODE_DPAD_LEFT);check(editor.list.hasFocus()&&editor.list.getSelectedItemPosition()==1,"Left lost the chosen section");
        phase="move-cancel";JSONArray before=new JSONArray(rows);ui(()->editor.list.getOnItemLongClickListener().onItemLongClick(editor.list,editor.list.getSelectedView(),1,1));key(KeyEvent.KEYCODE_DPAD_DOWN);check(!new JSONArray((List<?>)field(editor,"keys")).toString().equals(before.toString()),"Move did not update the Lite order");key(KeyEvent.KEYCODE_BACK);check(field(field(activity,"hub"),"homeEditor")==editor&&new JSONArray((List<?>)field(editor,"keys")).toString().equals(before.toString()),"Back did not undo only the move");
        LinearLayout details=(LinearLayout)field(editor,"detail");EditText name=(EditText)details.getChildAt(2);ui(()->name.setText(inputTitle()));ui(editor.cancel::performClick);check(!VelaLayout.read(context).has("titles"),"Cancelling persisted the edited name");
        phase="save-layout";VelaHomeEditor saved=editor();details=(LinearLayout)field(saved,"detail");EditText edit=(EditText)details.getChildAt(2);ui(()->edit.setText(inputTitle()));
        shot("01-lite-editor");ui(saved::focusFirst);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);check(saved.save.hasFocus(),"The remote cannot reach Save from the final section");key(KeyEvent.KEYCODE_DPAD_CENTER);check(VelaLayout.title(context,"inputs").equals(inputTitle()),"Save discarded the custom Lite name");ui(()->((VelaHub)unchecked(activity,"hub")).close());
        TextView[] labels=(TextView[])field(activity,"navigationLabels");check(labels[0].getText().toString().equals(inputTitle()),"The Lite sidebar ignored its saved destination name");
        phase="portable-and-profile";JSONObject backup=VelaWorkspace.snapshot(context);check(backup.toString().contains(inputTitle()),"Portable settings lost the Home title");VelaWorkspace.select(context,previous);check(!VelaLayout.title(context,"inputs").equals(inputTitle()),"The editor changed another profile");VelaWorkspace.select(context,profile);
        result.putString("result","PASS shared Lite Home editor: media controls absent, three destinations, real D-pad focus, reorder/Back undo, Cancel, Save, sidebar title, portable settings and profile isolation. Synthetic local profile; no TV or account contacted.");
    }catch(Throwable t){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(t));try{shot("failure");}catch(Exception ignored){}}
    finally{try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}if(mode!=null){if(mode.isEmpty())VelaEdition.prefs(context).edit().remove("mode").commit();else VelaEdition.select(context,mode);}}catch(Exception ignored){}}
    finish(result.containsKey("error")?1:-1,result);}
    private Object unchecked(Object o,String name){try{return field(o,name);}catch(Exception e){throw new RuntimeException(e);}}
}
