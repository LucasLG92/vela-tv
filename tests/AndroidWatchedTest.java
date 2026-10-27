package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.Certificate;
import java.lang.reflect.*;
import java.util.*;

/** Disposable emulator only. Intercepts HTTPS in this test process; never contacts a Plex account. */
public final class AndroidWatchedTest extends Instrumentation {
    private volatile int writes,stage;
    private volatile boolean reject;
    private volatile String writtenKey="";
    private CinemaHomeView home;
    private HomeActivity activity;
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private Object field(Object o,String n)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(n);}
    private void set(Object o,String n,Object value)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(n);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException next){}throw new NoSuchFieldException(n);}
    private void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    private interface Ready{boolean ready()throws Exception;}
    private void await(Ready ready,String why)throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){boolean[] ok={false};ui(()->{try{ok[0]=ready.ready();}catch(Exception e){throw new RuntimeException(e);}});if(ok[0])return;Thread.sleep(50);}throw new AssertionError(why);}
    private List<View> cards()throws Exception{return (List<View>)field(home,"cards");}
    private boolean menu()throws Exception{return ((View)field(home,"overlay")).getVisibility()==View.VISIBLE;}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void hold()throws Exception{View card=cards().get(0);ui(()->card.requestFocus());long time=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));Thread.sleep(ViewConfiguration.getLongPressTimeout()+200);waitForIdleSync();check(menu(),"Held OK did not show watched actions");sendKeySync(new KeyEvent(time,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,1));sendKeySync(new KeyEvent(time,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));waitForIdleSync();}
    private void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> v:values.entrySet()){Object x=v.getValue();if(x instanceof String)e.putString(v.getKey(),(String)x);else if(x instanceof Boolean)e.putBoolean(v.getKey(),(Boolean)x);else if(x instanceof Integer)e.putInt(v.getKey(),(Integer)x);else if(x instanceof Long)e.putLong(v.getKey(),(Long)x);else if(x instanceof Float)e.putFloat(v.getKey(),(Float)x);else if(x instanceof Set)e.putStringSet(v.getKey(),new HashSet<>((Set<String>)x));}e.commit();}
    private String feed(){return "{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\""+(101+stage)+"\",\"type\":\"episode\",\"grandparentRatingKey\":\"100\",\"grandparentTitle\":\"Serie de prueba\",\"title\":\"Episodio\",\"parentIndex\":1,\"index\":"+(1+stage)+",\"duration\":2700000,\"viewOffset\":"+(stage==0?600000:0)+"}]}}";}
    private final class FakeHttps extends HttpsURLConnection {
        private String response="{}";private int status=-1;
        FakeHttps(URL url){super(url);}
        public void connect(){} public void disconnect(){} public boolean usingProxy(){return false;}
        public String getCipherSuite(){return "test";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
        public int getResponseCode()throws IOException{
            if(status!=-1)return status;if(!url.getHost().equals("vela-fixture.invalid"))throw new IOException("External network disabled in this test");status=200;
            if(url.getPath().equals("/:/scrobble")){
                check(getRequestMethod().equals("PUT"),"Plex watched action must use PUT");check("fixture-session".equals(getRequestProperty("X-Plex-Token")),"Wrong watched account header");check(url.getQuery().equals("identifier=com.plexapp.plugins.library&key="+(101+stage)),"Wrong watched item or token leaked into URL");writes++;writtenKey=String.valueOf(101+stage);try{Thread.sleep(250);}catch(InterruptedException e){throw new InterruptedIOException();}if(reject)status=503;else stage++;
            }else if(url.getPath().equals("/hubs/continueWatching"))response=feed();else if(url.getPath().equals("/hubs"))response="{\"MediaContainer\":{\"Hub\":[]}}";else if(url.getPath().equals("/library/recentlyAdded"))response="{\"MediaContainer\":{\"Metadata\":[]}}";
            return status;
        }
        public InputStream getInputStream()throws IOException{getResponseCode();return new ByteArrayInputStream(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();Map<SharedPreferences,Map<String,?>> saved=new LinkedHashMap<>();
        try{
            check(Build.MODEL.contains("sdk")||Build.FINGERPRINT.contains("generic"),"Clean emulator required");
            Locale.setDefault(new Locale("es","ES"));L10n.choose(c,"es");PlexClient plex=new PlexClient(c);check(!plex.connected()&&!new JellyfinClient(c).connected(),"Refusing a personal library");
            for(SharedPreferences p:new SharedPreferences[]{plex.prefs,JellyfinClient.prefs(c),c.getSharedPreferences("library",0),Updates.prefs(c),Personalization.prefs(c),Visuals.prefs(c),SidebarApps.prefs(c),AmbientWeather.prefs(c)})saved.put(p,new HashMap<>(p.getAll()));
            URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new FakeHttps(url);}}:null);
            plex.prefs.edit().putString("serverUri","https://vela-fixture.invalid").putString("serverId","fixture").putString("serverToken","fixture-session").putString("serverName","Plex Demo").commit();LibraryClient.activate(c,"Plex");c.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();Personalization.prepare(c);Personalization.complete(c);
            List<PlexClient.Film> before=plex.library();check(before.size()==1&&!before.get(0).nextEpisode,"Initial resume fixture missing");int cache=Visuals.cacheKb(c);
            activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");await(()->cards().size()==1,"Home resume missing");
            View first=cards().get(0);hold();check(writes==0&&menu(),"Initial held release submitted a watched action");key(KeyEvent.KEYCODE_BACK);check(!menu()&&activity.getCurrentFocus()==first&&writes==0,"Cancel modified state or lost focus");
            hold();key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_CENTER);await(()->!((Boolean)field(home,"watchedBusy"))&&!menu()&&stage==1,"Watched action did not complete");check(writes==1&&writtenKey.equals("101"),"Repeated OK wrote twice or marked an entire series");
            List<PlexClient.Film> entries=(List<PlexClient.Film>)field(home,"posterFilms");StringBuilder described=new StringBuilder();for(PlexClient.Film item:entries)described.append(item.key).append(":").append(item.nextEpisode).append(":").append(item.continuing).append(";");check(entries.size()==1&&entries.get(0).key.equals("102")&&entries.get(0).nextEpisode,"Next episode did not replace the watched episode: "+described);check(plex.cached().get(0).key.equals("102"),"Cache resurrected the watched episode");
            reject=true;hold();key(KeyEvent.KEYCODE_DPAD_CENTER);await(()->!((Boolean)field(home,"watchedBusy")),"Failed write did not finish");check(menu()&&stage==1&&writes==2&&((TextView)field(home,"watchedStatus")).getText().toString().contains("confirmar"),"Failed write removed the item or claimed success");key(KeyEvent.KEYCODE_BACK);
            hold();plex.prefs.edit().putString("serverId","changed-account").commit();key(KeyEvent.KEYCODE_DPAD_CENTER);await(()->!((Boolean)field(home,"watchedBusy")),"Account switch guard hung");check(writes==2,"Menu sent a watched action after an account switch");key(KeyEvent.KEYCODE_BACK);plex.prefs.edit().putString("serverId","fixture").commit();
            PlexClient.Film external=new PlexClient.Film();external.key="watchnext:test";external.type="video";external.title="Vídeo de otra app";external.provider="YouTube";external.packageName=TvSuggestions.YOUTUBE;external.tvSource=true;external.continuing=true;external.thumb="";external.art="";external.summary="";external.meta="";
            ui(()->{try{set(home,"films",Collections.emptyList());set(home,"watchNext",Collections.singletonList(external));Method render=home.getClass().getDeclaredMethod("showFilms");render.setAccessible(true);render.invoke(home);}catch(Exception e){throw new RuntimeException(e);}});hold();check(field(home,"watchedMark")==null&&writes==2,"Unsupported app offered a fake server watched action");key(KeyEvent.KEYCODE_BACK);check(Visuals.cacheKb(c)==cache,"Watched actions increased the image budget");
            result.putString("result","PASS native held OK and repeat/release guard, cancel/focus, single per-episode Plex PUT with token header, immediate next episode/cache, failed write, account switch isolation, unsupported app honesty and unchanged image budget. No Plex account contacted.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null)ui(()->activity.finish());for(Map.Entry<SharedPreferences,Map<String,?>> entry:saved.entrySet())restore(entry.getKey(),entry.getValue());}
        finish(result.containsKey("error")?1:-1,result);
    }
}
