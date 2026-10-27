package tv.vela.launcher;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.*;

/** Focused HDMI UI coverage, with simulated hardware and intercepted passthrough intents. */
public final class LiteInterfaceInputsTest extends Instrumentation {
    static final class Source implements TvInputs.Source {
        final Context c;Runnable listener;final List<TvInputs.Raw> entries=new ArrayList<>();
        Source(Context c){this.c=c;entries.add(new TvInputs.Raw("p1",null,"HDMI 1","",TvInputs.CONNECTED));entries.add(new TvInputs.Raw("box","p1","MECOOL KM2 Plus","",TvInputs.CONNECTED));entries.add(new TvInputs.Raw("p2",null,"HDMI 2","",TvInputs.CONNECTED));entries.add(new TvInputs.Raw("switch","p2","Nintendo Switch","",TvInputs.CONNECTED));entries.add(new TvInputs.Raw("p3",null,"HDMI 3","",TvInputs.DISCONNECTED));}
        public List<TvInputs.Entry> read(){return TvInputs.resolve(c,entries);}public void start(Runnable r){listener=r;}public void stop(){listener=null;}
    }
    public static final class Home extends LiteHomeActivity {Source source;protected boolean allowAutomaticChecks(){return false;}protected TvInputs.Source createInputsSource(){source=new Source(this);return source;}}
    private Home activity;private Context context;private String old,profile;
    public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return name.equals(LiteHomeActivity.class.getName())?new Home():super.newActivity(loader,name,intent);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    Object field(Object value,String name)throws Exception{for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();ActivityMonitor monitor=null;context=getTargetContext();try{
        try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p)){check(in.read()=='1',"Owned emulator required");}
        old=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"HDMI · Demo");VelaWorkspace.select(context,profile);L10n.choose(context,"es");Ui.init(context);activity=(Home)startActivitySync(new Intent(context,LiteHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        List<View> tiles=(List<View>)field(activity,"tiles");View[] nav=(View[])field(activity,"navigation");check(tiles.size()==3,"CEC metadata duplicated physical inputs");List<TvInputs.Entry> entries=activity.source.read();check(entries.get(0).title.equals("MECOOL")&&entries.get(2).title.equals("HDMI 3"),"A disconnected port acquired a device identity");
        View name=(View)field(activity,"heroName"),art=(View)field(activity,"heroWell"),open=(View)field(activity,"heroOpen"),content=(View)field(activity,"content");check(name.getRight()<art.getLeft()&&art.getRight()<=content.getWidth(),"Featured device text and artwork overlap");check(open.getBottom()<386,"Primary action overlaps the input shelf");
        ui(tiles.get(0)::requestFocus);key(KeyEvent.KEYCODE_DPAD_LEFT);check(nav[0].hasFocus(),"Left edge did not reach the sidebar");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(tiles.get(0).hasFocus(),"Right did not return to the inputs");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(tiles.get(1).hasFocus(),"Device selection did not move with the remote");
        Thread.sleep(4200);Bitmap screenshot=getUiAutomation().takeScreenshot();check(screenshot!=null&&screenshot.getWidth()==3840,"Native 4K required");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"uiux-04-inputs.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
        ui(nav[0]::requestFocus);key(KeyEvent.KEYCODE_DPAD_DOWN);check(nav[1].hasFocus()&&((Integer)field(activity,"section"))==0,"Focus changed the selected destination before activation");Thread.sleep(200);Bitmap expanded=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"navigation-hdmi-expanded.png"))){expanded.compress(Bitmap.CompressFormat.PNG,100,out);}expanded.recycle();key(KeyEvent.KEYCODE_BACK);check(!(Boolean)field(activity,"sidebarOpen")&&tiles.get(1).hasFocus(),"Back did not restore the selected device without rebuilding its cards");

        IntentFilter filter=new IntentFilter(Intent.ACTION_VIEW);filter.addDataScheme("content");monitor=addMonitor(filter,new ActivityResult(Activity.RESULT_CANCELED,null),true);key(KeyEvent.KEYCODE_DPAD_CENTER);check(monitor.getHits()==1,"Selected device did not request native passthrough");
        result.putString("result","PASS Lite featured HDMI bounds, original device artwork, CEC grouping, disconnected identity, sidebar return, remote device selection and intercepted passthrough launch. Native 4K capture; no physical TV or input changed.");
    }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}finally{if(monitor!=null)removeMonitor(monitor);try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,old);VelaWorkspace.delete(context,profile);}}catch(Exception ignored){}}finish(result.containsKey("error")?1:-1,result);}
}
