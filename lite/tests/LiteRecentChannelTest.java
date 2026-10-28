package tv.vela.launcher;

import android.app.*;import android.content.*;import android.database.*;import android.os.*;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.*;

/** A native recent shortcut with duplicate tuner aliases, one hidden, and no physical signal. */
public final class LiteRecentChannelTest extends Instrumentation {
    public static final class Provider extends ContentProvider {public boolean onCreate(){return true;}public Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o){return new MatrixCursor(new String[]{"_id"});}public String getType(android.net.Uri u){return "vnd.android.cursor.dir/channel";}public android.net.Uri insert(android.net.Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(android.net.Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(android.net.Uri u,String s,String[] a){throw new UnsupportedOperationException();}}
    static final class Data extends LiteAntennaData {
        List<LiveTvData.Channel> values=Arrays.asList(new LiveTvData.Channel(91,"owned-fixture","91","Canal Demo",false,true,false),new LiveTvData.Channel(92,"owned-fixture","92","Canal Demo",false,true,false));
        Data(Context c){super(c);}boolean permitted(){return true;}List<LiveTvData.Channel> channels(CancellationSignal signal){return values;}boolean refreshGuide(List<LiveTvData.Channel> all,CancellationSignal signal){return false;}Map<String,List<LiveTvData.Programme>> programmes(List<LiveTvData.Channel> page,long from,long to,CancellationSignal signal){return Collections.emptyMap();}
    }
    public static final class Home extends LiteHomeActivity {Data data;protected boolean allowAutomaticChecks(){return false;}protected LiteAntennaData createAntennaData(){if(data==null)data=new Data(this);return data;}}
    public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return name.equals(LiteHomeActivity.class.getName())?new Home():super.newActivity(loader,name,intent);}
    private Context context;private Home activity;private String previous,profile,language,phase="setup";
    interface Ready {boolean value()throws Exception;}
    void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    Object field(Object o,String name)throws Exception{for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    void call(Object o,String name,Class<?>[] types,Object... args){try{for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{Method m=t.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);return;}catch(NoSuchMethodException next){}throw new NoSuchMethodException(name);}catch(Exception e){throw new RuntimeException(e);}}
    void ui(Runnable task){Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    void await(Ready ready)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(ready.value())return;Thread.sleep(70);}throw new AssertionError("Timeout: "+phase);}
    View action(View v){if(v instanceof VelaRecentActivity.Card)return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=action(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();context=getTargetContext();try{
        try(ParcelFileDescriptor f=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(f)){check(in.read()=='1',"Owned emulator required");}
        previous=VelaWorkspace.active(context);language=L10n.choice();profile=VelaWorkspace.create(context,"Recent Demo");VelaWorkspace.select(context,profile);L10n.choose(context,"en");Ui.init(context);VelaWorkspace.prefs(context,"lite_updates").edit().putBoolean("automatic",false).commit();
        List<LiveTvData.Channel> all=new Data(context).values;String key=LiteChannelPersonal.key(all.get(0),all);new TvPersonalization(context,"antenna").hide(LiteChannelPersonal.key(all.get(1),all),all.get(1).name,true);VelaRecentActivity.channel(context,key,all.get(0).name,"antenna");
        activity=(Home)startActivitySync(new Intent(context,LiteHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));phase="recent-shortcut-with-hidden-namesake";ui(()->call(activity,"show",new Class[]{int.class,boolean.class},2,false));await(()->action(activity.getWindow().getDecorView())!=null);View shortcut=action(activity.getWindow().getDecorView());ui(shortcut::requestFocus);await(activity::hasWindowFocus);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        await(()->field(activity,"player")!=null);Object player=field(activity,"player");check(((LiveTvData.Channel)field(player,"channel")).id.equals("ant-91"),"Shortcut did not resolve the original tuner identity");check(((List<?>)field(player,"channels")).size()==1,"Hidden namesake was exposed in playback");check((Integer)field(activity,"section")==1,"Reopened channel retained the Apps section");
        phase="hidden-and-locked-guards";new TvPersonalization(context,"antenna").hide(key,all.get(0).name,true);check(LiteChannelPersonal.resolveActivity(context,key,all)==null,"Hidden channel reopened");new TvPersonalization(context,"antenna").hide(key,all.get(0).name,false);LiveTvData.Channel locked=new LiveTvData.Channel(93,"owned-fixture","93","Locked demo",false,true,true);check(LiteChannelPersonal.resolveActivity(context,LiteChannelPersonal.key(locked,Collections.singletonList(locked)),Collections.singletonList(locked))==null,"Locked tuner channel reopened");
        result.putString("result","PASS real Lite Apps Recent activity OK resolves the same tuner channel with a hidden identical name; correct TV destination, hidden/locked checks and visible-only picker. Read-only fixture, no physical tuner or network.");
    }catch(Throwable error){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(error));}finally{try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}if(language!=null)L10n.choose(context,language);}catch(Exception ignored){}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
