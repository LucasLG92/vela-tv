package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Native, account-free Home integration: real feed routes, profile choices, focus and motion. */
public final class AndroidHomePersonalizationTest extends Instrumentation {
    private Context context;private HomeActivity activity;private CinemaHomeView home;
    private String previousMode;private String profile,previous,phase="setup";private JSONArray catalog;private final Bundle result=new Bundle();
    private final AtomicInteger rowRequests=new AtomicInteger();private boolean overrun;private final AtomicInteger jellyfinRequests=new AtomicInteger();private Bundle args;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    Object field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    interface Ready{boolean get()throws Exception;}
    void await(Ready ready)throws Exception{long end=SystemClock.elapsedRealtime()+22000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(ready.get())return;Thread.sleep(60);}throw new AssertionError("Timeout: "+phase);}
    void shot(String name)throws Exception{Thread.sleep(240);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null&&b.getWidth()==3840,"Owned native 4K required");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"personal-home-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    JSONObject item(int id,String type,boolean resume)throws Exception{
        JSONObject art=catalog.getJSONObject((id-101)%catalog.length());
        JSONObject j=new JSONObject().put("ratingKey",Integer.toString(id)).put("type",type).put("title",art.getString("title")).put("summary",art.optString("summary")).put("year",art.optInt("year",2025)).put("duration",art.optInt("duration",120)*60000L).put("thumb","/library/metadata/"+id+"/thumb").put("art","/library/metadata/"+id+"/art").put("addedAt",1000+id).put("audienceRating",8.5);
        if(resume)j.put("viewOffset",35*60000L).put("lastViewedAt",5000);return j;
    }
    byte[] container(JSONArray values)throws Exception{return RefinementHttp.text(new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",values).put("offset",0).put("totalSize",500)).toString());}
    byte[] reply(URL url)throws Exception{
        String path=url.getPath();String query=url.getQuery()==null?"":URLDecoder.decode(url.getQuery(),"UTF-8");
        check(!query.toLowerCase(Locale.ROOT).contains("token="),"Credentials leaked into a feed query");
        if(path.startsWith("/photo/")){java.util.regex.Matcher m=java.util.regex.Pattern.compile("/metadata/([0-9]+)/").matcher(query);int id=m.find()?Integer.parseInt(m.group(1)):101;return RefinementHttp.asset(getContext(),(query.contains("/art")?"backdrop-":"poster-")+((id-101)%catalog.length()+1)+".jpg");}
        if(path.equals("/hubs"))return RefinementHttp.text("{\"MediaContainer\":{\"Hub\":[]}}");
        if(path.equals("/hubs/continueWatching"))return container(new JSONArray().put(item(101,"movie",true)));
        if(path.equals("/library/recentlyAdded")){JSONArray a=new JSONArray();for(int i=102;i<113;i++)a.put(item(i,"movie",false));return container(a);}
        if(path.equals("/library/all")){
            rowRequests.incrementAndGet();check(query.contains("X-Plex-Container-Start=0")&&query.contains("X-Plex-Container-Size=12"),"Home performed an unbounded query");
            String type=query.contains("type=2")?"show":"movie";int start=type.equals("show")?121:query.contains("unwatched=1")?141:query.contains("audienceRating")?161:181;
            JSONArray a=new JSONArray();for(int i=0;i<(overrun?13:12);i++)a.put(item(start+i,type,false));return container(a);
        }
        if(path.equals("/Items")){
            jellyfinRequests.incrementAndGet();check(query.contains("userId=00000000000000000000000000000001")&&query.contains("startIndex=0")&&query.contains("limit=12")&&query.contains("recursive=true"),"Jellyfin Home was unscoped or unbounded");
            String type=query.contains("includeItemTypes=Series")?"Series":"Movie";JSONArray a=new JSONArray();
            for(int i=0;i<(overrun?13:12);i++)a.put(new JSONObject().put("Id",String.format(Locale.ROOT,"%032x",300+i)).put("Type",type).put("Name","Jellyfin Demo "+i).put("CommunityRating",9-i*.1).put("DateCreated","2026-10-30T12:00:00Z").put("UserData",new JSONObject().put("Played",false)));
            return RefinementHttp.text(new JSONObject().put("Items",a).put("StartIndex",0).put("TotalRecordCount",500).toString());
        }
        return null;
    }
    List<View> cards()throws Exception{return (List<View>)field(home,"cards");}
    List<?> shelves()throws Exception{return (List<?>)field(home,"shelves");}
    void launch()throws Exception{activity=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");await(()->!cards().isEmpty()&&field(home,"heroBitmap")!=null);}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        check(!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"Personal accounts are not allowed");previousMode=VelaEdition.prefs(context).getString("mode","");VelaEdition.select(context,"full");
        catalog=new JSONArray(new String(RefinementHttp.asset(getContext(),"catalog.json"),java.nio.charset.StandardCharsets.UTF_8));RefinementHttp.install(this::reply);
        previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Home · Demo");VelaWorkspace.select(context,profile);L10n.choose(context,args.getString("language","es"));Ui.init(context);Personalization.prepare(context);Personalization.complete(context);Updates.prefs(context).edit().putBoolean("automatic",false).commit();AmbientWeather.select(context,new AmbientWeather.Place("Reikiavik · Demo","",64.14,-21.94,""));AmbientWeather.prefs(context).edit().putString("cacheKey","64.14,-21.94").putString("cache","{\"timezone\":\"Atlantic/Reykjavik\",\"current\":{\"temperature_2m\":4,\"weather_code\":3,\"is_day\":1}}").putLong("cacheAt",System.currentTimeMillis()).commit();
        phase="old-layout-migration";VelaLayout.save(context,new JSONObject().put("order",new JSONArray(Arrays.asList("recent","resume","library"))));
        for(String row:VelaLayout.EXTRA_ROWS)check(!VelaLayout.visible(context,row),"An old profile silently enabled a remote feed");check(VelaLayout.order(context).get(0).equals("recent"),"Migration changed an existing order");
        PlexClient plex=new PlexClient(context);plex.prefs.edit().putString("serverUri","https://vela-fixture.invalid").putString("serverId","home-fixture-"+SystemClock.elapsedRealtime()).putString("serverToken","fixture-session").putString("serverName","Plex · Demo").commit();LibraryClient.activate(context,"Plex");
        phase="disabled-row-network";launch();Thread.sleep(400);check(rowRequests.get()==0,"Disabled feeds requested the catalogue");ui(activity::finish);
        phase="profile-draft";JSONObject draft=VelaLayout.read(context);draft.put("librarySource","plex").put("appResume",false).put("hidden",new JSONArray());VelaLayout.save(context,draft);
        check(!VelaLayout.appResume(context)&&VelaLayout.source(context).equals("plex"),"Source/Watch Next choices were not saved");
        String other=VelaWorkspace.create(context,"Other Home");VelaWorkspace.select(context,other);check(VelaLayout.appResume(context)&&VelaLayout.source(context).equals("current")&&!VelaLayout.visible(context,"top_rated"),"Home choices crossed profiles");VelaWorkspace.select(context,profile);VelaWorkspace.delete(context,other);
        JSONObject portable=VelaWorkspace.snapshot(context);check(!portable.toString().contains("fixture-session")&&portable.getJSONObject("settings").getJSONObject("vela-layout").toString().contains("librarySource"),"Home choices were not portable or exported account data");
        phase="home-feeds";launch();await(()->((Map<?,?>)field(home,"homeRows")).size()==4);check(rowRequests.get()==4,"A row made redundant feed requests");check(shelves().size()==6,"The configured Home rows are missing");
        List<PlexClient.Film> films=(List<PlexClient.Film>)field(home,"posterFilms");check(films.size()<=72,"Home retained an entire catalogue");
        for(String row:VelaLayout.EXTRA_ROWS){List<?> values=((Map<String,List<?>>)field(home,"homeRows")).get(row);check(values.size()==12,"Home row exceeded twelve titles");}
        overrun=true;boolean refused=false;try{new LibraryClient(context).homeRow("recent_movies");}catch(IOException expected){refused=true;}overrun=false;check(refused,"The server ignored the Home response limit");
        await(()->{for(ImageView v:(List<ImageView>)field(home,"posterImages"))if(v.getGlobalVisibleRect(new Rect())&&v.getDrawable()==null)return false;return true;});shot("01-home");
        phase="scroll-motion";ScrollView scroll=(ScrollView)field(home,"shelvesScroll");Object next=shelves().get(1);View nextCard=((List<View>)field(next,"items")).get(0);int target=((View)field(next,"root")).getTop();int initial=scroll.getScrollY();
        runOnMainSync(nextCard::requestFocus);Thread.sleep(45);int middle=scroll.getScrollY();await(()->scroll.getScrollY()==target);check(target>initial&&middle>initial&&middle<target,"Moving down still jumps instead of transitioning");shot("02-next-row");
        phase="reduced-motion";ui(()->Visuals.prefs(context).edit().putBoolean("reducedMotion",true).commit());
        Object nextAgain=shelves().get(2);View third=((List<View>)field(nextAgain,"items")).get(0);ui(third::requestFocus);await(()->scroll.getScrollY()==((View)field(nextAgain,"root")).getTop());
        phase="home-editor";ui(()->activity.toolsLayout());await(()->field(activity,"hub")!=null);shot("03-editor");View sourceOption=activity.findViewById(android.R.id.content).findViewWithTag("@source");check(sourceOption!=null,"Home source is not accessible in the draft editor");ui(sourceOption::requestFocus);shot("03b-home-source");
        phase="voice-in-home";Visuals.prefs(context).edit().putBoolean("reducedMotion",false).commit();ui(()->((VelaHub)unchecked(activity,"hub")).close());getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.RECORD_AUDIO");
        final RecognitionListener[] callback={null};final int[] destroyed={0};VelaVoiceSearch voice=new VelaVoiceSearch(activity,new VelaVoiceSearch.Host(){public void phrase(String s){}public void keyboard(){}},a->new VelaVoiceSearch.Engine(){public void start(Intent i,RecognitionListener l){callback[0]=l;}public void stop(){}public void cancel(){}public void destroy(){destroyed[0]++;}});
        ui(voice::start);runOnMainSync(()->{callback[0].onReadyForSpeech(new Bundle());Bundle partial=new Bundle();partial.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList(args.getString("language","es").equals("en")?"Find science fiction films":"Busca películas de ciencia ficción")));callback[0].onPartialResults(partial);callback[0].onRmsChanged(5);});shot("04-listening");
        // Optional native recording fixture: simulate RMS only, never open a physical microphone.
        String clip=args.getString("fixture","");if(clip.matches("voice-glow-[0-9]+")){
            try(OutputStream marker=new FileOutputStream(new File(context.getExternalFilesDir(null),clip+".ready"))){marker.write(1);}
            for(int i=0;i<130;i++){final float rms=(i/28)%2==0?(float)(3+6*Math.sin(i*.3)): -3;runOnMainSync(()->callback[0].onRmsChanged(rms));if(i==45)shot("05-voice-glow");Thread.sleep(90);}
        }
        ui(voice::cancel);check(destroyed[0]==1&&!voice.active(),"Voice UI retained the recognizer after closing");
        if(clip.matches("voice-glow-[0-9]+")){
            Visuals.prefs(context).edit().putBoolean("reducedMotion",true).commit();ui(voice::start);ui(()->callback[0].onReadyForSpeech(new Bundle()));shot("06-static-glow");ui(voice::cancel);check(destroyed[0]==2&&!voice.active(),"Reduced-motion voice kept its recognizer after closing");
        }
        phase="home-provider-isolation";ui(activity::finish);check(!new LibraryClient(context).home("jellyfin").connected(),"Explicit disconnected Home source silently fell back to Plex");
        JellyfinClient.prefs(context).edit().putString("base","https://vela-fixture.invalid").putString("serverId","jellyfin-home-fixture").putString("serverName","Jellyfin Demo").putString("userId","00000000000000000000000000000001").putString("token","fixture-session").commit();
        LibraryClient current=new LibraryClient(context),jf=current.home("jellyfin");check(jf.isJellyfin()&&jf.connected()&&current.provider().equals("Plex"),"Home source changed catalogue browsing");
        for(String key:VelaLayout.EXTRA_ROWS){List<PlexClient.Film> a=jf.homeRow(key);check(a.size()==12&&a.get(0).packageName.equals(JellyfinClient.PACKAGE)&&a.get(0).type.equals(key.equals("recent_series")?"show":"movie"),"Jellyfin Home row lost its provider, type or bound");}
        check(jellyfinRequests.get()==4,"Jellyfin Home made redundant row requests");check(current.home("both").homeRow("recent_movies").size()==12,"Combined Home retained both entire row responses");
        String artworkPath="vela-jellyfin:/Items/00000000000000000000000000000300/Images/Primary?tag=fixture",priorArt=current.imageIdentity(artworkPath),priorCatalogue=current.identity();JellyfinClient.prefs(context).edit().putString("token","fixture-session-replaced").commit();check(!priorArt.equals(current.imageIdentity(artworkPath))&&priorCatalogue.equals(current.identity()),"Home artwork key followed the catalogue provider instead of its own account");
        overrun=true;refused=false;try{jf.homeRow("recent_movies");}catch(IOException expected){refused=true;}overrun=false;check(refused,"Jellyfin ignored the Home response limit");
        result.putString("result","PASS old-layout migration without background feed calls; profile-specific source, external resume switch and portable settings without accounts; four real bounded Plex/Jellyfin feeds, independent Home source, combined limit and oversized-response rejection; real native six-row Home, intermediate scroll motion and cancellation; integrated microphone UI and destruction. Synthetic media/account only.");
    }catch(Throwable e){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));try{if(activity!=null)shot("failure");}catch(Exception ignored){}}finally{try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}if(previousMode!=null){if(previousMode.isEmpty())VelaEdition.prefs(context).edit().remove("mode").commit();else VelaEdition.select(context,previousMode);}}catch(Exception ignored){}}finish(result.containsKey("error")?1:-1,result);}
    Object unchecked(Object o,String name){try{return field(o,name);}catch(Exception e){throw new RuntimeException(e);}}
}
