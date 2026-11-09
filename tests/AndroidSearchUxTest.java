package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Real shared result views, remote navigation, paging and profile-scoped launch policy. */
public final class AndroidSearchUxTest extends Instrumentation {
    private Context context;private Activity activity;private VelaHub hub,anchor;private String previous,profile,mode,language,phase="setup";private Bundle args;
    private final AtomicInteger requests=new AtomicInteger(),opened=new AtomicInteger();private volatile java.util.List<VelaHub.Hit> values=Collections.emptyList();private volatile VelaHub.Hit selected;private volatile String scope="fixture";private volatile CountDownLatch gate;
    public static final class Lite extends LiteHomeActivity {protected boolean allowAutomaticChecks(){return false;}}
    public Activity newActivity(ClassLoader l,String n,Intent i)throws InstantiationException,IllegalAccessException,ClassNotFoundException{return n.equals(LiteHomeActivity.class.getName())?new Lite():super.newActivity(l,n,i);}
    interface Ready{boolean get()throws Exception;}
    void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    void await(Ready r)throws Exception{long until=SystemClock.elapsedRealtime()+18000;while(SystemClock.elapsedRealtime()<until){waitForIdleSync();if(r.get())return;Thread.sleep(50);}throw new AssertionError("Timeout "+phase);}
    VelaSearchView view()throws Exception{return (VelaSearchView)field(hub,"searchView");}
    java.util.List<View> tiles()throws Exception{return (java.util.List<View>)field(view(),"tiles");}
    void shot(String name)throws Exception{Thread.sleep(450);Bitmap b=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"voice-search-api"+Build.VERSION.SDK_INT+"-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    void fixture()throws Exception{if(hub!=null)ui(hub::close);requests.set(0);opened.set(0);selected=null;
        VelaHub.Host anchorHost=(VelaHub.Host)field(anchor,"host");ui(()->{hub=new VelaHub(activity,new VelaHub.Host(){
            public java.util.List<VelaHub.Hit> search(String q,int page,CancellationSignal signal)throws Exception{requests.incrementAndGet();CountDownLatch hold=gate;if(hold!=null)hold.await(10,TimeUnit.SECONDS);signal.throwIfCanceled();return values;}
            public java.util.List<VelaShows.Airing> airings(String q,int p,CancellationSignal s){return Collections.emptyList();}
            public void activate(VelaHub.Hit h){selected=h;opened.incrementAndGet();}public String diagnosis(CancellationSignal s){return "";}public void repair(){}public void closed(boolean changed){}public String scope(){return scope;}
            public boolean navigation(){return anchorHost.navigation();}public void title(String label){anchorHost.title(label);}public void contentFocused(){anchorHost.contentFocused();}
            public Runnable artwork(VelaHub.Hit hit,ImageView target,int width,int height){return anchorHost.artwork(hit,target,width,height);}
        },0,true);ViewGroup parent=(ViewGroup)anchor.getParent();parent.addView(hub,anchor.getLayoutParams());hub.focusNavigation();});
    }
    void voice(String phrase)throws Exception{ui(()->hub.voiceQuery(VelaVoice.parse(phrase)));}
    void search(String text)throws Exception{ui(()->{try{view().input.setText(text);view().input.onEditorAction(EditorInfo.IME_ACTION_SEARCH);}catch(Exception e){throw new RuntimeException(e);}});}
    void key(int code){ui(()->{activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));});}
    View text(View v,String title){if(v instanceof TextView&&((TextView)v).getText().toString().contains(title))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=text(((ViewGroup)v).getChildAt(i),title);if(found!=null)return found;}return null;}
    void press(String title){View v=text(hub,title);check(v!=null,"Missing "+title);ui(()->{View w=v;while(w!=null&&!w.isClickable())w=w.getParent() instanceof View?(View)w.getParent():null;if(w!=null)w.performClick();});}
    private volatile int providerCalls;private volatile boolean providerFixture,failMovies;
    byte[] providerReply(java.net.URL url)throws Exception{
        if(url.getPath().startsWith("/search-art/"))return RefinementHttp.asset(getContext(),url.getPath().endsWith("channel")?"antena-3-es.png":"backdrop-1.jpg");
        if(url.getPath().equals("/photo/:/transcode")||url.getPath().startsWith("/Items/"))return RefinementHttp.asset(getContext(),url.getPath().startsWith("/Items/")?"poster-2.jpg":"poster-1.jpg");
        if(!providerFixture)return null;String q=url.getQuery();if(url.getPath().equals("/library/sections"))throw new AssertionError("Search enumerated libraries");
        if(!url.getPath().equals("/library/all"))return null;providerCalls++;if(failMovies&&q.contains("type=1"))throw new IOException("Fixture unavailable");
        check(q.contains("title=Alien")&&q.contains("X-Plex-Container-Size=24"),"Unbounded or incorrect global search");boolean show=q.contains("type=2");int offset=q.contains("Start=24")?24:0;
        return RefinementHttp.text(new JSONObject().put("MediaContainer",new JSONObject().put("offset",offset).put("totalSize",50).put("Metadata",new JSONArray().put(new JSONObject().put("ratingKey",show?"202":"101").put("title",show?"Alien: Earth":"Alien").put("type",show?"show":"movie")))).toString());
    }
    ImageView resultImage(int index)throws Exception{return (ImageView)((ViewGroup)tiles().get(index)).getChildAt(0);}
    void artwork()throws Exception{
        phase="real result artwork";List<VelaHub.Hit> art=new ArrayList<>();
        if(!VelaEdition.lite(context)){
            PlexClient plex=new PlexClient(context);plex.prefs.edit().putString("serverUri","https://vela-fixture.invalid").putString("serverToken","emulator-fixture").putString("serverId","search-art").commit();
            JellyfinClient.prefs(context).edit().putString("base","https://vela-fixture.invalid").putString("serverId","search-art").putString("userId","00000000000000000000000000000001").putString("token","emulator-fixture").commit();
            for(int n=0;n<2;n++){PlexClient.Film film=new PlexClient.Film();film.title=n==0?"Superman":"Oppenheimer";film.key="art-"+n;film.type="movie";film.thumb=n==0?"vela-plex:/library/metadata/101/thumb":"vela-jellyfin:/Items/00000000000000000000000000000002/Images/Primary?tag=fixture";film.packageName=n==0?"com.plexapp.android":JellyfinClient.PACKAGE;art.add(new VelaHub.Hit(n==0?"Plex":"Jellyfin",film.title,(n==0?"2025":"2023")+" · Demo",film));}
        }
        art.add(new VelaHub.Hit(VelaWorkspace.t("Channel","Canal"),"Antena 3","Generalistas",new LiveTvData.Channel("search-art","Antena 3","Generalistas","https://vela-fixture.invalid/search-art/channel",Collections.singletonList("http://127.0.0.1:9/never-play"))));
        long now=System.currentTimeMillis()/1000;art.add(new VelaHub.Hit(VelaWorkspace.t("Programme","Programa"),"Superman","Antena 3 · Demo",new VelaShows.Airing("Superman","Antena 3",now-600,now+600,"art","Demo","https://vela-fixture.invalid/search-art/programme","")));
        try{
            values=art;fixture();voice("busca demo");await(()->tiles().size()==art.size());
            await(()->{for(int n=0;n<art.size();n++)if(!(resultImage(n).getDrawable() instanceof android.graphics.drawable.BitmapDrawable))return false;return true;});
            Object loader=field(view(),"programmeArt");check(((android.util.LruCache<?,?>)field(loader,"cache")).maxSize()==512*1024,"Search enlarged the shared TV artwork budget");
            check(((java.util.List<?>)field(view(),"imageRequests")).size()==(VelaEdition.lite(context)?0:2),"Library artwork did not use the cancellable Home loader");
            shot("05-real-artwork");ui(()->{try{view().pause();}catch(Exception e){throw new RuntimeException(e);}});
            for(int n=0;n<art.size();n++)check(resultImage(n).getDrawable()==null,"Pausing search retained a result image");
            check(field(view(),"programmeArt")==null&&((java.util.List<?>)field(view(),"imageRequests")).isEmpty(),"Pausing search retained artwork workers or subscriptions");
            fixture();voice("busca demo");await(()->{if(tiles().size()!=art.size())return false;for(int n=0;n<art.size();n++)if(!(resultImage(n).getDrawable() instanceof android.graphics.drawable.BitmapDrawable))return false;return true;});
        }finally{if(!VelaEdition.lite(context)){new PlexClient(context).prefs.edit().clear().commit();JellyfinClient.prefs(context).edit().clear().commit();}}
    }
    void voiceSurfaces()throws Exception{
        phase="voice on hidden navigation";try(ParcelFileDescriptor permission=getUiAutomation().executeShellCommand("pm grant "+context.getPackageName()+" android.permission.RECORD_AUDIO");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(permission)){while(in.read()!=-1){}}
        View rail=activity.findViewById(android.R.id.content).findViewWithTag("vela-sidebar");check(rail!=null&&rail.isShown(),"Real navigation missing from voice fixture");
        int visibility=rail.getVisibility(),width=rail.getLayoutParams().width,focusability=((ViewGroup)rail).getDescendantFocusability();float alpha=rail.getAlpha();
        final android.speech.RecognitionListener[] callback={null};final AtomicInteger destroyed=new AtomicInteger();
        VelaVoiceSearch voice=new VelaVoiceSearch(activity,new VelaVoiceSearch.Host(){public void phrase(String s){}public void keyboard(){}},a->new VelaVoiceSearch.Engine(){public void start(Intent i,android.speech.RecognitionListener listener){callback[0]=listener;}public void stop(){}public void cancel(){}public void destroy(){destroyed.incrementAndGet();}});
        try{
            ui(voice::start);ui(()->callback[0].onReadyForSpeech(new Bundle()));await(()->!(Boolean)field(field(field(voice,"panel"),"signal"),"compact"));
            for(boolean reduced:new boolean[]{false,true}){
                if(reduced){ui(voice::cancel);ui(()->Visuals.prefs(context).edit().putBoolean("reducedMotion",true).commit());ui(voice::start);ui(()->callback[0].onReadyForSpeech(new Bundle()));}
                for(int hidden:new int[]{View.GONE,View.INVISIBLE}){
                    phase="voice hidden="+hidden+" reduced="+reduced;
                    ui(()->rail.setVisibility(hidden));await(()->(Boolean)field(field(field(voice,"panel"),"signal"),"compact"));
                    View signal=(View)field(field(voice,"panel"),"signal");check(signal.getHeight()<=120&&signal.getWidth()<=160,"Invisible navigation left a full-height voice border");
                    if(!reduced&&hidden==View.GONE)shot("06-voice-without-sidebar");ui(()->rail.setVisibility(View.VISIBLE));await(()->!(Boolean)field(field(field(voice,"panel"),"signal"),"compact"));
                }
                phase="voice transparent reduced="+reduced;ui(()->{rail.setAlpha(0);rail.invalidate();});await(()->(Boolean)field(field(field(voice,"panel"),"signal"),"compact"));ui(()->rail.setAlpha(alpha));
                phase="voice modal reduced="+reduced;ui(()->{((ViewGroup)rail).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);rail.invalidate();});await(()->(Boolean)field(field(field(voice,"panel"),"signal"),"compact"));ui(()->((ViewGroup)rail).setDescendantFocusability(focusability));
                ui(()->{ViewGroup.LayoutParams p=rail.getLayoutParams();p.width=276;rail.setLayoutParams(p);});await(()->!(Boolean)field(field(field(voice,"panel"),"signal"),"compact")&&((View)field(field(voice,"panel"),"signal")).getWidth()>=300);
                ui(()->{ViewGroup.LayoutParams p=rail.getLayoutParams();p.width=width;rail.setLayoutParams(p);});
            }
            Object lastPanel=field(voice,"panel"),lastSignal=field(lastPanel,"signal");ui(voice::cancel);check(!voice.active()&&destroyed.get()==2,"Voice surface change retained a recogniser");check(field(lastPanel,"observer")==null&&field(lastSignal,"pulse")==null,"Closing voice retained a window observer or animation");
        }finally{ui(()->{voice.cancel();Visuals.prefs(context).edit().putBoolean("reducedMotion",false).commit();rail.setVisibility(visibility);rail.setAlpha(alpha);((ViewGroup)rail).setDescendantFocusability(focusability);ViewGroup.LayoutParams p=rail.getLayoutParams();p.width=width;rail.setLayoutParams(p);});}
    }
    void sources()throws Exception{
        VelaToolsData isolated=new VelaToolsData(activity){
            List<VelaHub.Hit> channels(String q,int p,CancellationSignal c)throws Exception{throw new IOException("Unavailable channels");}
            public List<VelaShows.Airing> airings(String q,int p,CancellationSignal c)throws Exception{throw new IOException("Unavailable guide");}
            List<VelaHub.Hit> media(String q,int p,CancellationSignal c){return Collections.singletonList(new VelaHub.Hit("Plex","Alien","Demo",new Object()));}
            public void activate(VelaHub.Hit h){}public void repair(){}public void closed(boolean changed){}
        };
        List<VelaHub.Hit> found=isolated.search("Alien",0,new CancellationSignal());check(found.size()==1&&found.get(0).title.equals("Alien")&&((VelaHub.Hits)found).partial,"Guide failure hid library results");
        CancellationSignal cancelled=new CancellationSignal();cancelled.cancel();try{isolated.search("Alien",0,cancelled);throw new AssertionError("Cancellation swallowed");}catch(OperationCanceledException expected){}
        if(VelaEdition.lite(context))return;
        providerFixture=true;providerCalls=0;PlexClient plex=new PlexClient(context);plex.prefs.edit().putString("serverUri","https://vela-fixture.invalid").putString("serverToken","emulator-fixture").putString("serverId","owned-fixture").commit();
        try{LibraryClient library=new LibraryClient(context).home("plex");LibraryClient.Search page=library.search("Alien",0,new CancellationSignal());check(page.films.size()==2&&page.more&&!page.partial&&providerCalls==2,"Global search missed movie/series libraries");library.search("Alien",1,new CancellationSignal());check(providerCalls==4,"Provider paging requested sections");failMovies=true;LibraryClient.Search partial=library.search("Alien",0,new CancellationSignal());check(partial.films.size()==1&&partial.partial,"One failed type hid another type");}
        finally{providerFixture=failMovies=false;plex.prefs.edit().clear().commit();}
        final long[] clock={0};InputStream wire=new ByteArrayInputStream(new byte[12]){public synchronized int read(byte[] b,int o,int l){clock[0]+=10;return super.read(b,o,Math.min(l,2));}};
        XtreamClient.NetworkBudget budget=new XtreamClient.NetworkBudget(wire,12,100,()->clock[0]);byte[] buffer=new byte[2];check(budget.read(buffer)==2,"Initial wire read failed");clock[0]+=1000000;check(budget.read(buffer)==2,"Indexing CPU time consumed wire budget");
        XtreamClient.NetworkBudget slow=new XtreamClient.NetworkBudget(wire,12,5,()->clock[0]);try{slow.read(buffer);throw new AssertionError("Slow wire escaped budget");}catch(InterruptedIOException expected){}
        XtreamClient.NetworkBudget large=new XtreamClient.NetworkBudget(new ByteArrayInputStream(new byte[4]),1,100,()->0);try{large.read(buffer);throw new AssertionError("Byte limit escaped");}catch(IOException expected){}
    }
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void onStart(){Bundle result=new Bundle();context=getTargetContext();try{
        try(ParcelFileDescriptor f=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(f)){check(in.read()=='1',"Owned emulator required");}
        RefinementHttp.install(this::providerReply);previous=VelaWorkspace.active(context);language=L10n.choice();mode=VelaEdition.prefs(context).getString("mode","");VelaEdition.select(context,Build.VERSION.SDK_INT<30||args.getString("fixture","").equals("unified-lite")?"lite":"full");profile=VelaWorkspace.create(context,"Search demo");VelaWorkspace.select(context,profile);L10n.choose(context,args.getString("language","en"));Ui.init(context);
        context.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();LiteUpdateManager.prefs(context).edit().putBoolean("automatic",false).commit();activity=startActivitySync(new Intent().setClassName(context.getPackageName(),VelaEdition.home(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->((VelaHub.Owner)activity).tools(0));anchor=(VelaHub)field(activity,"hub");ui(()->{anchor.pause();anchor.setVisibility(View.GONE);});
        phase="source-isolation-and-global-search";sources();
        artwork();voiceSurfaces();
        phase="defaults-and-ranking";check(!VelaVoice.firstResult(context),"Fresh profile auto-opens voice matches");
        ArrayList<VelaHub.Hit> movies=new ArrayList<>();String[] titles={"Alien: Romulus","Aliens","Alien","Alien³","Alien: Resurrection","Alien: Covenant","Alien vs. Predator","Alien: Earth","Aliens vs. Predator: Requiem","Alien anthology","Alien collection","Alien extras","Alien restored"};for(int i=0;i<titles.length;i++)movies.add(new VelaHub.Hit(i%2==0?"Plex":"Jellyfin",titles[i],i==2?"1979 · 1 h 57 min":L10n.text("Library")+" · Demo",new Object()));values=movies;fixture();voice("busca Alien");await(()->tiles().size()==6);check(requests.get()==1&&opened.get()==0,"Default result policy or query count failed");check(((java.util.List<VelaHub.Hit>)field(view(),"visible")).get(0).title.equals("Alien"),"Exact title was not ranked first");
        check(((FrameLayout)field(hub,"panel")).getVisibility()==View.GONE,"Search still shows the settings panel");shot("01-results");
        phase="remote-and-local-pages";ui(()->{try{tiles().get(0).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});key(KeyEvent.KEYCODE_DPAD_RIGHT);check(tiles().get(1).hasFocus(),"Right did not move across result cards");key(KeyEvent.KEYCODE_DPAD_DOWN);check(tiles().get(3).hasFocus(),"Down did not preserve result column");View next=(View)field(view(),"next");ui(next::performClick);check(requests.get()==1&&tiles().size()==6,"Local paging repeated a provider query");ui(next::performClick);check(requests.get()==1&&tiles().size()==1,"Last local page lost a result");
        phase="profile-and-portability";VelaVoice.firstResult(context,true);JSONObject copy=VelaWorkspace.snapshot(context);check(copy.getJSONObject("settings").getJSONObject("vela-search").getBoolean("voice_first"),"Preference excluded from portable settings");String other=VelaWorkspace.create(context,"Other search");VelaWorkspace.select(context,other);check(!VelaVoice.firstResult(context),"Launch policy leaked between profiles");VelaWorkspace.apply(context,copy,true);check(VelaVoice.firstResult(context),"Search preference was not restored");VelaWorkspace.undo(context);check(!VelaVoice.firstResult(context),"Undo did not restore the prior search policy");JSONObject invalid=new JSONObject(copy.toString());invalid.getJSONObject("settings").getJSONObject("vela-search").put("voice_first","true");boolean rejected=false;try{VelaWorkspace.apply(context,invalid,true);}catch(Exception expected){rejected=true;}check(rejected&&!VelaVoice.firstResult(context),"Malformed launch policy changed settings");VelaWorkspace.select(context,profile);VelaWorkspace.delete(context,other);
        phase="voice-first";fixture();voice("busca Alien");await(()->opened.get()==1);check(selected.title.equals("Alien")&&requests.get()==1,"First match did not use the displayed ranking exactly once");
        phase="literal-search-stays-selectable";fixture();ui(()->hub.voiceQuery(new VelaVoice.Request("Alien","Alien",VelaVoice.ANY,false,0,false,false)));await(()->tiles().size()==6);check(opened.get()==0,"Literal ACTION_SEARCH auto-launched");search("Aliens");await(()->requests.get()==2&&tiles().size()==6);check(opened.get()==0,"Keyboard search auto-launched");
        phase="scoped-and-offline";VelaHub.Hit app=new VelaHub.Hit("App","La 1","Demo",new Intent(Intent.ACTION_MAIN).setClassName("com.android.settings","com.android.settings.Settings"));VelaHub.Hit tv=new VelaHub.Hit("Channel","La 1","Demo",new LiveTvData.Channel("fixture","La 1","Demo","",Collections.singletonList("http://127.0.0.1:9/never-play")));values=Arrays.asList(app,tv);fixture();voice("pon el canal La 1");await(()->opened.get()==1);check(selected==tv,"Channel voice request selected an app");values=Collections.singletonList(new VelaHub.Hit("HDMI","HDMI 2","Demo",new TvInputs.Entry("fixture","fixture","HDMI 2","HDMI 2","hdmi",TvInputs.DISCONNECTED,false)));fixture();voice("abre HDMI 2");await(()->requests.get()==1&&!((Boolean)field(view(),"loading")));check(opened.get()==0&&tiles().isEmpty(),"Disconnected input auto-opened");shot("02-empty");
        phase="stale-scope";values=movies;gate=new CountDownLatch(1);fixture();voice("busca Alien");await(()->requests.get()==1);scope="changed-fixture";gate.countDown();Thread.sleep(350);check(opened.get()==0,"Stale source auto-opened");gate=null;
        phase="settings-and-reading";VelaVoice.firstResult(context,false);fixture();press(L10n.text("Voice and search"));check(hub.page()==VelaHub.SEARCH_SETTINGS,"Search settings cannot survive recreation");check(((View)((java.util.List<?>)field(hub,"controls")).get(0)).hasFocus(),"Opening voice preferences lost remote focus");shot("03-preference");press(L10n.text("Open first match"));check(VelaVoice.firstResult(context),"Settings did not persist first match");press(L10n.text("Show results"));check(!VelaVoice.firstResult(context),"Settings did not persist chooser");press(L10n.text("Back to search"));
        VelaReading.prefs(context).edit().putInt("text",2).commit();fixture();voice("busca Alien");await(()->tiles().size()==4);for(View tile:tiles()){Rect r=new Rect();check(tile.getGlobalVisibleRect(r)&&r.height()>0,"Large text result escaped the viewport");}shot("04-large-reading");
        result.putString("result","PASS native Plex/Jellyfin posters, channel logos and programme images, page-scoped artwork release; visible/hidden/transparent/modal/expanded rail geometry with normal and reduced motion, compact voice surface and observer/animator/recogniser teardown; native dedicated search, bounded six/four-card pages, exact-first ranking, remote columns, no provider calls for local paging, per-profile launch policy and portable settings, one-use first voice result, literal/keyboard chooser, typed target isolation, disconnected-input refusal, stale-source guard and integrated settings. Synthetic emulator data only.");
    }catch(Throwable e){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));try{shot("failure");}catch(Exception ignored){}}
    finally{try{if(gate!=null)gate.countDown();if(hub!=null)ui(hub::close);if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}if(language!=null)L10n.choose(context,language);if(mode!=null){if(mode.isEmpty())VelaEdition.prefs(context).edit().remove("mode").commit();else VelaEdition.select(context,mode);}}catch(Exception ignored){}}
    finish(result.containsKey("error")?1:-1,result);}
}
