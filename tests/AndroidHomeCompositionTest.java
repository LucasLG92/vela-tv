package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/** Owned-emulator composition checks. Real artwork stays in the disposable test APK. */
public final class AndroidHomeCompositionTest extends Instrumentation {
    private Context context; private HomeActivity activity; private CinemaHomeView home;
    private JSONArray catalog; private String profile,previous,phase="setup",fixture,captureLanguage="es";
    private final Bundle result=new Bundle();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    void set(Object o,String name,Object value)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    interface Ready{boolean ready()throws Exception;}
    void await(Ready r)throws Exception{long end=SystemClock.elapsedRealtime()+22000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(r.ready())return;Thread.sleep(80);}throw new AssertionError("Timeout: "+phase);}
    void call(String name,Class<?>[] types,Object...args){try{Method m=home.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(home,args);}catch(Exception e){throw new RuntimeException(e);}}
    void key(int k){sendKeyDownUpSync(k);waitForIdleSync();}
    TextView label(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=label(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    void compositionChecks()throws Exception{
        List<View> cards=(List<View>)field(home,"cards");List<PlexClient.Film> films=(List<PlexClient.Film>)field(home,"posterFilms");List<ImageView> images=(List<ImageView>)field(home,"posterImages");
        ScrollView viewport=(ScrollView)field(home,"shelvesScroll");View play=(View)field(home,"play");check(play.getBottom()+16<viewport.getTop(),"Hero action overlaps the first heading");check(((View)field(home,"providerMark")).getTop()<170,"Home retains the empty header gap");
        TextView latest=label((View)field(home,"shelfList"),L10n.text("Recently added"));Rect visible=new Rect();check(latest!=null&&latest.getGlobalVisibleRect(visible)&&visible.height()>=latest.getHeight()*1.95f,"Latest uploads is not discoverable without scrolling");
        check(cards.get(3).getGlobalVisibleRect(visible)&&visible.height()>100,"The next row has no visible poster preview");
        for(int i=0;i<3;i++){View picture=(View)images.get(i).getParent();TextView time=label(cards.get(i),ContinueWatching.remaining(films.get(i)));check(time!=null&&time.getParent()==picture,"Playback time is outside the image");FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)time.getLayoutParams();check(p.bottomMargin>=8&&p.bottomMargin<=16,"Playback time is not adjacent to progress");check(cards.get(i).getHeight()-picture.getHeight()<=60,"Card retained the external provider/time spacer");}
    }
    void shot(String name)throws Exception{Thread.sleep(500);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null&&b.getWidth()==3840,"Native 4K required");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"composition-"+fixture+"-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    JSONObject item(int id,boolean resume)throws Exception{JSONObject art=catalog.getJSONObject((id-101)%catalog.length());JSONObject j=new JSONObject().put("ratingKey",""+id).put("type",art.optString("type","movie")).put("title",art.getString("title")).put("summary",art.optString("summary")).put("year",art.optInt("year",2025)).put("duration",art.optInt("duration",120)*60000L).put("thumb","/library/metadata/"+id+"/thumb").put("art","/library/metadata/"+id+"/art").put("addedAt",1000+id);if(resume)j.put("viewOffset",35*60000L).put("lastViewedAt",5000);return j;}
    byte[] container(JSONArray a)throws Exception{return RefinementHttp.text(new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",a)).toString());}
    byte[] reply(URL u)throws Exception{String path=u.getPath();if(path.startsWith("/image/"))return RefinementHttp.asset(getContext(),path.substring(7));if(path.startsWith("/photo/")){String q=URLDecoder.decode(u.getQuery(),"UTF-8");java.util.regex.Matcher m=java.util.regex.Pattern.compile("/metadata/([0-9]+)/").matcher(q);int id=m.find()?Integer.parseInt(m.group(1)):101;return RefinementHttp.asset(getContext(),(q.contains("/art")?"backdrop-":"poster-")+((id-101)%catalog.length()+1)+".jpg");}if(path.equals("/hubs"))return RefinementHttp.text("{\"MediaContainer\":{\"Hub\":[]}}");if(path.equals("/hubs/continueWatching"))return container(new JSONArray().put(item(101,true)));if(path.equals("/library/recentlyAdded")){JSONArray a=new JSONArray();for(int i=102;i<113;i++)a.put(item(i,false));return container(a);}return null;}
    PlexClient.Film external(String title,String provider,String pkg,String image,long duration,long position,long played){PlexClient.Film f=new PlexClient.Film();f.key="demo-"+pkg;f.title=title;f.provider=provider;f.packageName=pkg;f.section=L10n.text("Continue watching");f.summary="";f.meta="";f.type="video";f.thumb=f.art="https://vela-fixture.invalid/image/"+image;f.durationMs=duration;f.positionMs=position;f.progress=(float)position/duration;f.lastPlayedMs=played;f.continuing=f.tvSource=f.landscape=true;f.thumbAspect=16f/9;return f;}
    public void onCreate(Bundle b){super.onCreate(b);fixture=b.getString("fixture","after");captureLanguage=b.getString("language","es");start();}
    public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        check(!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"No personal accounts allowed");
        catalog=new JSONArray(new String(RefinementHttp.asset(getContext(),"catalog.json"),java.nio.charset.StandardCharsets.UTF_8));RefinementHttp.install(this::reply);
        previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Demo");VelaWorkspace.select(context,profile);L10n.choose(context,captureLanguage);Ui.init(context);Personalization.prepare(context);Personalization.complete(context);Updates.prefs(context).edit().putBoolean("automatic",false).commit();VelaStyle.apply(context,VelaStyle.CINEMA);
        if(fixture.equals("widgets")){VelaWidgets.State widgets=VelaWidgets.read(context);widgets.items.clear();widgets.items.addAll(Arrays.asList("clock","weather","programme","shortcut"));widgets.format=1;widgets.shortcut="tv";VelaWidgets.save(context,widgets);long now=System.currentTimeMillis()/1000;VelaShows.prefs(context).edit().putString("rules","[\"Cine de noche\"]").putString("airings",new JSONArray().put(new JSONObject().put("title","Cine de noche").put("channel","La 1").put("start",now+1800).put("end",now+7200)).toString()).commit();}
        PlexClient plex=new PlexClient(context);plex.prefs.edit().putString("serverUri","https://vela-fixture.invalid").putString("serverId","composition-"+SystemClock.elapsedRealtime()).putString("serverToken","fixture-session").putString("serverName","Biblioteca Demo").commit();LibraryClient.activate(context,"Plex");
        phase="home";activity=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");await(()->((List<?>)field(home,"posterFilms")).size()>=10);await(()->field(home,"heroBitmap")!=null);
        PlexClient.Film youtube=external(captureLanguage.equals("en")?"Dune: Part Two · Official trailer":"Dune: Parte dos · Tráiler oficial","YouTube",TvSuggestions.YOUTUBE,"youtube-dune.jpg",180000,60000,4000000),prime=external("Reacher","Prime Video","com.amazon.amazonvideo.livingroom","prime-reacher.jpg",3000000,1020000,3000000);prime.episodeLabel=captureLanguage.equals("en")?"S1 · E2":"T1 · E2";
        ui(()->{try{set(home,"watchGeneration",(Integer)field(home,"watchGeneration")+1);set(home,"watchNext",Arrays.asList(youtube,prime));call("showFilms",new Class[]{});HomeStatusView status=(HomeStatusView)field(home,"headerStatus");set(status,"snapshot",new AmbientWeather.Snapshot(captureLanguage.equals("en")?"Reykjavik · Demo":"Reikiavik · Demo","",4,3,true,System.currentTimeMillis()));status.updateClock();}catch(Exception e){throw new RuntimeException(e);}});
        if(fixture.equals("widgets")){long now=System.currentTimeMillis()/1000;ui(()->VelaShows.replace(context,Collections.singletonList(new VelaShows.Airing("Cine de noche","La 1",now+1800,now+7200))));}
        await(()->{for(ImageView v:(List<ImageView>)field(home,"posterImages"))if(v.getGlobalVisibleRect(new Rect())&&v.getDrawable()==null)return false;return true;});
        ui(()->{try{((View)field(home,"play")).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});if(!fixture.equals("before"))compositionChecks();shot("home");
        List<View> cards=(List<View>)field(home,"cards");ui(cards.get(0)::requestFocus);shot("continue");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);check(activity.getCurrentFocus()==cards.get(1),"Right did not reach the next provider");if(!fixture.equals("before"))check(!((TextView)field(home,"meta")).getText().toString().trim().startsWith("·"),"Empty metadata left a leading separator");shot("youtube");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);check(activity.getCurrentFocus()==cards.get(2),"Right did not reach Prime Video");shot("prime");
        key(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(250);check(activity.getCurrentFocus()==cards.get(5),"Down skipped matching latest poster");shot("latest");
        key(KeyEvent.KEYCODE_DPAD_UP);check(activity.getCurrentFocus()==cards.get(2),"Up did not return to the resume row");
        ui(()->{try{((List<View>)field(home,"sideButtons")).get(0).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});shot("sidebar");
        result.putString("result","PASS native mixed-provider home, original artwork, reserved HTTPS fixture, real D-pad traversal between providers and shelves, sidebar. No personal account or external network.");
    }catch(Throwable e){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));try{shot("failure");}catch(Exception ignored){}}finally{try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}}catch(Exception ignored){}}finish(result.containsKey("error")?1:-1,result);}
}
