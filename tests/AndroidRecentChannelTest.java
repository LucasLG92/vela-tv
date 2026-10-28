package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Reopens a real Apps shortcut beyond the first search page, using owned synthetic streams. */
public final class AndroidRecentChannelTest extends Instrumentation {
    private Context context;private HomeActivity activity;private String profile,previous,language,phase="setup",source;
    interface Ready {boolean value()throws Exception;}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    Object field(Object o,String name)throws Exception{for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    void call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    void ui(Runnable task){Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    void await(Ready ready)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(ready.value())return;Thread.sleep(60);}throw new AssertionError("Timeout: "+phase);}
    View action(View root){if(root instanceof VelaRecentActivity.Card)return root;if(root instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)root).getChildCount();i++){View found=action(((android.view.ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
    void openApps(Object home){ui(()->call(home,"showApps",new Class[]{View.class},activity.getCurrentFocus()));}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();context=getTargetContext();Map<String,?> before=new HashMap<>(LiveTvData.prefs(context).getAll());try{
        try(ParcelFileDescriptor f=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(f)){check(in.read()=='1',"Owned emulator required");}
        RefinementHttp.install(url->null);language=L10n.choice();previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Recent Demo");VelaWorkspace.select(context,profile);L10n.choose(context,"en");Ui.init(context);Personalization.complete(context);Updates.prefs(context).edit().putBoolean("automatic",false).commit();
        M3uClient client=new M3uClient(context);client.configure("https://vela-fixture.invalid/recent-channels.m3u","",false);source=M3uClient.identity(context);
        LiveTvData.Channel target=new LiveTvData.Channel("recent-target","[ES] Canal Demo FHD","Demo","",Collections.singletonList("http://127.0.0.1:9/never-play/target.ts"));
        try(TvCatalogCache.Writer writer=new TvCatalogCache(context).writer(source)){for(int i=1;i<=30;i++)writer.add(new LiveTvData.Channel("recent-"+i,"[ES] Canal Demo "+i+" HD","Demo","",Collections.singletonList("http://127.0.0.1:9/never-play/"+i+".ts")),false);writer.add(target,false);writer.commit("{}");}
        String title=TvChannelNames.display(target.name);check(client.page(null,title,null,0).total==31,"Fixture must span multiple pages of similarly named channels");
        check(client.page(null,title,null,0).channels.get(0).id.equals(target.id),"Exact channel match was buried behind partial names");
        check(client.page(null,"CanalDemo",null,0).total==1,"Formatting differences prevented the canonical channel match");
        Set<String> pageIds=new HashSet<>();for(int p=0;p<3;p++)for(LiveTvData.Channel ch:client.page(null,title,null,p).channels)check(pageIds.add(ch.id),"Exact-first search repeated a channel across pages");check(pageIds.size()==31,"Exact-first search lost channels between pages");
        TvPersonalization order=new TvPersonalization(context,source);order.order(Arrays.asList(TvChannelLibrary.key(client.page(null,title,null,1).channels.get(0)),TvChannelLibrary.key(target)));Set<String> chosen=new LinkedHashSet<>(Arrays.asList("recent-1",target.id));check(client.page(null,title,chosen,0).channels.get(0).id.equals(target.id),"Favourite search ordering or SQL bind positions failed");
        CancellationSignal cancelled=new CancellationSignal();cancelled.cancel();boolean stopped=false;try{new TvCatalogCache(context).page(source,null,title,null,0,cancelled);}catch(OperationCanceledException expected){stopped=true;}check(stopped,"Cancelled search still queried the catalogue");
        // Legacy reference with no shortlist: reproduces the original identity-vs-search bug.
        VelaRecentActivity.channel(context,target.id,title,source);
        activity=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Object home=field(activity,"home");phase="actual-recent-shortcut";openApps(home);await(()->action(activity.getWindow().getDecorView())!=null);
        android.widget.GridView grid=(android.widget.GridView)field(field(field(home,"panels"),"screen"),"grid");await(()->grid.getCount()>0&&grid.getChildCount()>0);
        View shortcut=action(activity.getWindow().getDecorView());ui(shortcut::requestFocus);await(()->activity.hasWindowFocus()&&shortcut.hasFocus());sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        await(()->field(home,"liveTv")!=null&&field(field(home,"liveTv"),"player")!=null);Object player=field(field(home,"liveTv"),"player");check(((LiveTvData.Channel)field(player,"selected")).id.equals(target.id),"Shortcut opened another channel or failed to open the player");
        check(TvChannelLibrary.resolveActivity(context,source,target.id,"A different channel")==null,"Reused channel ID ignored the original title");
        phase="bounded-history-variants-and-visibility";new TvPersonalization(context,source).played(TvChannelLibrary.key(target),title);TvChannelLibrary.remember(context,source,target);
        LiveTvData.Channel replacement=new LiveTvData.Channel("recent-replacement","[ES] Canal Demo HD","Demo","",Collections.singletonList("http://127.0.0.1:9/never-play/replacement.ts"));
        try(TvCatalogCache.Writer writer=new TvCatalogCache(context).writer(source)){writer.add(replacement,false);writer.commit("{}");}
        check(TvChannelLibrary.resolveActivity(context,source,target.id,title).id.equals(replacement.id),"Channel family did not recover after its quality/ID changed");
        TvPersonalization personal=new TvPersonalization(context,source);personal.hide(TvChannelLibrary.key(target),title,true);check(TvChannelLibrary.resolveActivity(context,source,target.id,title)==null,"Hidden channel reopened from the shortcut");personal.hide(TvChannelLibrary.key(target),title,false);
        check(TvChannelLibrary.resolveActivity(context,"another-source",target.id,title)==null,"Activity crossed source scope");
        phase="xtream-category-cache";String xc="xtream:owned-demo",prefix="xc:owned-demo:7";try(TvCatalogCache.Writer writer=new TvCatalogCache(context).writer(prefix)){writer.add(target,false);writer.add(replacement,false);writer.commit("{}");}
        check(new TvCatalogCache(context).resolveHistory(xc,target.id,"")!=null,"Xtream category-only cache could not resolve an exact identity");check(new TvCatalogCache(context).page(xc,null,"CanalDemo",null,0).total==1,"Cached Xtream category search did not group its channel family");check(new TvCatalogCache(context).resolveHistory("xtream:other-demo",target.id,"")==null,"Xtream account scopes crossed");new TvCatalogCache(context).remove(prefix);
        result.putString("result","PASS real Apps Recent activity OK opens the correct channel beyond 30 similar titles; legacy references without history, changed quality IDs, hidden/source checks and bounded Xtream category-cache resolution. Owned emulator and unreachable loopback streams only.");
    }catch(Throwable error){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(error));}finally{try{if(activity!=null)ui(activity::finish);if(source!=null)new TvCatalogCache(context).remove(source);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}if(language!=null)L10n.choose(context,language);SharedPreferences.Editor edit=LiveTvData.prefs(context).edit().clear();for(Map.Entry<String,?> e:before.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);else if(v instanceof Set)edit.putStringSet(e.getKey(),new HashSet<>((Set<String>)v));}edit.commit();}catch(Exception ignored){}}
        finish(result.containsKey("error")?1:-1,result);
    }
}
