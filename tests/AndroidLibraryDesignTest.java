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

/** Full-only library fixtures: real client routes, account isolation, paging and native detail controls. */
public final class AndroidLibraryDesignTest extends Instrumentation {
    private String previousMode;private boolean previewSections;private String captureLanguage="es";private Context context;private HomeActivity activity;private CinemaHomeView home;private JSONArray catalog;private int seriesIndex,requests;private boolean overrun;private String profile,previous,phase="setup";private final Bundle result=new Bundle();
    private boolean browsePages;
    private int collectionCount=13,serverPageSize=12;
    private boolean mixedCollectionTypes,switchOnCollectionRead;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    Object field(Object o,String key)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(key);}
    void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}interface Ready {boolean ready()throws Exception;}
    void await(Ready r)throws Exception{long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(r.ready())return;Thread.sleep(70);}throw new AssertionError("Timeout: "+phase);}
    void key(int k){sendKeyDownUpSync(k);waitForIdleSync();}
    void call(String method,Class<?>[] types,Object... values){try{Method m=home.getClass().getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(home,values);}catch(Exception e){throw new RuntimeException(e);}}
    VelaContentDetails sheet()throws Exception{return (VelaContentDetails)field(home,"detailSheet");}
    TextView action(String label)throws Exception{for(int i=0;i<sheet().actions.getChildCount();i++){View v=sheet().actions.getChildAt(i);if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(label))return (TextView)v;}throw new AssertionError("Missing action: "+label);}
    void navigationPhotos()throws Exception{
        FrameLayout foreground=(FrameLayout)field(home,"surface");for(int i=0;i<foreground.getChildCount();i++)check(!(foreground.getChildAt(i) instanceof VelaRecentActivity.Card),"Recent activity overlays cinematic artwork");List<View> sections=(List<View>)field(home,"sideButtons");ui(sections.get(0)::requestFocus);Thread.sleep(200);View wallpaper=(View)field(home,"wallpaper");check(wallpaper.getScaleX()==1f&&wallpaper.getTranslationX()==0&&((View)field(home,"backdrop")).getParent()==wallpaper,"Cinematic wallpaper shrinks into a rectangle with the content");shot("15-sidebar-cinema");View rail=(View)field(home,"sidebar");Bitmap first=Bitmap.createBitmap(rail.getWidth(),rail.getHeight(),Bitmap.Config.ARGB_8888);ui(()->rail.draw(new Canvas(first)));List<PlexClient.Film> films=(List<PlexClient.Film>)field(home,"posterFilms");PlexClient.Film other=films.get(1);ui(()->call("selectFilm",new Class[]{PlexClient.Film.class},other));await(()->(other.provider+other.key).equals(field(home,"heroKey")));Thread.sleep(200);Bitmap second=Bitmap.createBitmap(rail.getWidth(),rail.getHeight(),Bitmap.Config.ARGB_8888);ui(()->rail.draw(new Canvas(second)));try{for(int x=40;x<rail.getWidth()-40;x+=32)for(int y=120;y<rail.getHeight()-40;y+=40)check(first.getPixel(x,y)==second.getPixel(x,y),"Selecting another movie repainted the navigation");}finally{first.recycle();second.recycle();}shot("16-sidebar-other-title");key(KeyEvent.KEYCODE_DPAD_DOWN);check(!(Boolean)field(home,"livePage"),"Focus opened a different section without activation");shot("17-sidebar-focused-destination");key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_RIGHT);check(!(Boolean)field(home,"sidebarOpen"),"Navigation did not collapse back to content");TextView play=(TextView)field(home,"play");String old=play.getText().toString();ui(()->{play.setText("Continuar en una plataforma de vídeo conectada");call("layoutPlaybackAction",new Class[]{});});View options=(View)field(home,"link"),details=(View)field(home,"detailsButton");check(play.getRight()<options.getLeft()&&options.getRight()<details.getLeft(),"Variable provider action text overlaps Details");ui(()->{play.setText(old);call("layoutPlaybackAction",new Class[]{});});
    }
    private void checkPlaybackFocus()throws Exception{
        TextView action=(TextView)field(home,"play");
        View nav=(View)((List<?>)field(home,"sideButtons")).get(0);
        ui(nav::requestFocus);await(()->action.getAlpha()<.01f);
        ui(action::requestFocus);await(()->action.hasFocus()&&action.getAlpha()>.99f);
        check(action.isShown()&&action.getText().length()>0,"Playback action missing");
        android.graphics.drawable.Drawable icon=action.getCompoundDrawables()[0];
        check(icon!=null&&!icon.getBounds().isEmpty(),"Playback glyph missing");
        View neighbour=(View)field(home,"link");
        ui(neighbour::requestFocus);Thread.sleep(170);
        check(action.getAlpha()>.99f,"Playback action disappeared when moving away");
    }
    private void captureSections()throws Exception{
        checkPlaybackFocus();
        for(int section:new int[]{1,2}){
            phase=section==1?"peliculas":"series";
            ui(()->home.navigateSection(section));
            await(()->(Boolean)field(home,"sectionArtwork")&&field(home,"heroBitmap")!=null&&!((List<?>)field(home,"cards")).isEmpty());
            List<?> cards=(List<?>)field(home,"cards");View card=(View)cards.get(Math.min(1,cards.size()-1));
            ui(card::requestFocus);await(()->field(home,"selected")!=null);
            Thread.sleep(650);
            View play=(View)field(home,"play"),toolbar=(View)field(home,"pager"),shelf=(View)field(home,"shelvesScroll");
            checkPlaybackFocus();ui(card::requestFocus);
            check(play.getBottom()<toolbar.getTop()&&toolbar.getBottom()<=shelf.getTop(),"Library controls overlap the posters");
            check(!((TextView)field(home,"meta")).getText().toString().startsWith("Plex"),"Provider is shown twice");
            shot(phase);
            if(section==1){
                PlexClient.Film selected=(PlexClient.Film)field(home,"selected");
                await(()->(selected.provider+selected.key).equals(field(home,"heroKey"))&&field(home,"heroBitmap")!=null);
                await(()->((ImageView)((List<?>)field(home,"posterImages")).get(cards.indexOf(card))).getDrawable()!=null);
                ui(()->call("showWatchedMenu",new Class[]{PlexClient.Film.class,View.class},selected,card));
                await(()->sheet()!=null&&sheet().image.getDrawable()!=null);
                shot("ficha-cine");key(KeyEvent.KEYCODE_BACK);await(()->sheet()==null);
            }
        }
        for(int section:new int[]{4,5}){
            ui(()->home.navigateSection(section));await(()->field(home,"panels")!=null);
            if(section==4){
                Object host=field(home,"panels");
                InstalledAppsGrid grid=(InstalledAppsGrid)field(field(host,"screen"),"grid");
                await(()->grid.getCount()>0);ui(()->{grid.requestFocus();grid.setSelection(0);});
                key(KeyEvent.KEYCODE_DPAD_RIGHT);ui(()->{grid.requestFocus();grid.setSelection(0);});
                await(()->grid.hasFocus()&&grid.getSelectedView()!=null&&((ImageView)field(grid.getSelectedView(),"image")).getDrawable()!=null);
            }
            shot(section==4?"aplicaciones":"entradas");
        }
        ui(()->home.navigateSection(0));
        await(()->field(home,"heroBitmap")!=null&&!((List<?>)field(home,"cards")).isEmpty());
    }
    void backdropVisible()throws Exception{ImageView image=(ImageView)field(home,"backdrop");check(image.getImageAlpha()==255&&image.getAlpha()==1f&&image.isShown(),"Artwork loaded but backdrop opacity is incorrect");}
    void shot(String name)throws Exception{Thread.sleep(500);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null&&b.getWidth()==3840,"Native 4K required");try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"refinement-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    static String guid(int n){return String.format(Locale.ROOT,"%032x",n);}
    static Map<String,String> query(URL url)throws Exception{Map<String,String> out=new HashMap<>();String raw=url.getQuery();if(raw!=null)for(String p:raw.split("&")){String[] v=p.split("=",2);out.put(URLDecoder.decode(v[0],"UTF-8"),v.length<2?"":URLDecoder.decode(v[1],"UTF-8"));}return out;}
    JSONObject plexItem(int id,String type)throws Exception{int index=type.equals("show")||type.equals("season")||type.equals("episode")?(type.equals("show")&&id>=601?id-601:seriesIndex):Math.floorMod(id-101,catalog.length());JSONObject art=catalog.getJSONObject(index);String title=type.equals("season")?"Temporada "+(id-409):type.equals("episode")?"Episodio "+(id-500):art.getString("title");JSONObject j=new JSONObject().put("ratingKey",Integer.toString(id)).put("type",type).put("title",title).put("summary",art.optString("summary","Información de demostración")).put("year",art.optInt("year",2025)).put("duration",art.optInt("duration",50)*60000L).put("thumb","/library/metadata/"+id+"/thumb").put("art","/library/metadata/"+id+"/art");if(type.equals("episode"))j.put("grandparentTitle",art.getString("title")).put("grandparentRatingKey","301").put("grandparentThumb","/library/metadata/301/thumb").put("parentIndex",1).put("index",id-500);return j;}
    JSONObject jfItem(int id,String type)throws Exception{JSONObject art=catalog.getJSONObject(type.equals("Season")||type.equals("Series")||type.equals("Episode")?seriesIndex:Math.floorMod(id-1101,catalog.length()));JSONObject j=new JSONObject().put("Id",guid(id)).put("Type",type).put("Name",type.equals("Season")?"Temporada "+(id-3409):type.equals("Episode")?"Episodio "+(id-3500):art.getString("title")).put("Overview",art.optString("summary")).put("ProductionYear",art.optInt("year",2025)).put("RunTimeTicks",art.optInt("duration",50)*60000L*10000).put("ImageTags",new JSONObject().put("Primary","fixture")).put("BackdropImageTags",new JSONArray().put("fixture")).put("UserData",new JSONObject().put("Played",false));if(type.equals("Episode")||type.equals("Season"))j.put("SeriesId",guid(3301)).put("SeriesName",catalog.getJSONObject(seriesIndex).getString("title")).put("ParentIndexNumber",1).put("IndexNumber",type.equals("Episode")?id-3500:id-3409);return j;}
    byte[] reply(URL url)throws Exception{requests++;check(url.getQuery()==null||!url.getQuery().toLowerCase(Locale.ROOT).contains("token="),"Account token leaked into query");String path=url.getPath();Map<String,String> q=query(url);int offset=Integer.parseInt(q.getOrDefault("X-Plex-Container-Start",q.getOrDefault("startIndex","0"))),limit=Integer.parseInt(q.getOrDefault("X-Plex-Container-Size",q.getOrDefault("limit","24")));
        if(path.startsWith("/photo/")||path.contains("/Images/")){String original=q.getOrDefault("url",path);java.util.regex.Matcher match=java.util.regex.Pattern.compile("/metadata/([0-9]+)/").matcher(original);int id=match.find()?Integer.parseInt(match.group(1)):101,index=id>=601?id-601:id>=300?seriesIndex:Math.floorMod(id-101,catalog.length());return RefinementHttp.asset(getContext(),(original.contains("art")||path.contains("Backdrop")?"backdrop-":"poster-")+(index+1)+".jpg");}
        if(path.equals("/library/sections"))return RefinementHttp.text(new JSONObject().put("MediaContainer",new JSONObject().put("Directory",new JSONArray().put(new JSONObject().put("key","1").put("type","movie").put("title","Películas")).put(new JSONObject().put("key","2").put("type","show").put("title","Series")))).toString());
        if(path.matches("/library/sections/[12]/all")){
            boolean shows=path.contains("/2/");JSONArray items=new JSONArray();
            for(int i=0;i<catalog.length();i++)if(shows==catalog.getJSONObject(i).optString("type").equals("show"))
                items.put(plexItem((shows?601:101)+i,shows?"show":"movie"));
            return container(items,0,items.length());
        }
        if(path.matches("/library/sections/[12]/collections")){
            if(switchOnCollectionRead)new PlexClient(context).prefs.edit().putString("serverId","replacement-server").commit();
            JSONArray items=new JSONArray();for(int i=offset;i<Math.min(collectionCount,offset+serverPageSize);i++)items.put(new JSONObject().put("ratingKey",Integer.toString(900+i)).put("type",mixedCollectionTypes&&i==0?"movie":"collection").put("title",i==0?"Cine esencial":i==1?"Grandes historias":"Colección "+(i+1)));return container(items,offset,collectionCount);}
        if(path.equals("/hubs/continueWatching"))return container(new JSONArray(),0,0);if(path.equals("/hubs"))return RefinementHttp.text("{\"MediaContainer\":{\"Hub\":[]}}");
        if(path.equals("/library/recentlyAdded")){JSONArray a=new JSONArray();for(int i=0;i<8;i++)a.put(plexItem(101+i,"movie"));a.put(plexItem(301,"show"));return container(a,0,a.length());}
        if(path.matches("/library/metadata/[0-9]+/children")){int id=Integer.parseInt(path.split("/")[3]);JSONArray a=new JSONArray();int total=id==301?2:id==410?26:12,size=overrun?24:limit;for(int i=offset;i<Math.min(total,offset+size);i++)a.put(id==301?plexItem(410+i,"season"):id==410?plexItem(501+i,"episode"):plexItem(101+i,"movie"));return container(a,offset,total);}
        if(path.matches("/library/metadata/[0-9]+")){int id=Integer.parseInt(path.split("/")[3]);return container(new JSONArray().put(plexItem(id,id==301?"show":id>=500?"episode":id>=410?"season":"movie")),0,1);}
        if(path.equals("/Items")){String types=q.getOrDefault("includeItemTypes","");JSONArray a=new JSONArray();if(types.equals("BoxSet")){for(int i=offset;i<Math.min(collectionCount,offset+serverPageSize);i++)a.put(new JSONObject().put("Id",guid(700+i)).put("Type",mixedCollectionTypes&&i==0?"Movie":"BoxSet").put("Name","Jellyfin · Colección "+(i+1)));}else{int total=types.equals("Season")?2:types.equals("Episode")?26:12;for(int i=offset;i<Math.min(total,offset+limit);i++)a.put(jfItem(types.equals("Season")?3410+i:types.equals("Episode")?3501+i:1101+i,types.equals("Season")?"Season":types.equals("Episode")?"Episode":"Movie"));}return RefinementHttp.text(new JSONObject().put("Items",a).put("StartIndex",offset).put("TotalRecordCount",types.equals("BoxSet")?collectionCount:types.equals("Season")?2:types.equals("Episode")?26:12).toString());}
        if(path.matches("/Items/[a-f0-9]{32}")){int id=Integer.parseInt(path.substring(7),16);return RefinementHttp.text(jfItem(id,id==3301?"Series":id>=3500?"Episode":id>=3410?"Season":"Movie").toString());}
        if(path.contains("/Resume")||path.equals("/Shows/NextUp"))return RefinementHttp.text("{\"Items\":[]}");if(path.equals("/Items/Latest"))return RefinementHttp.text("[]");return null;
    }
    byte[] container(JSONArray items,int offset,int total)throws Exception{return RefinementHttp.text(new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",items).put("offset",offset).put("totalSize",total)).toString());}
    View collectionButton(VelaHub hub,String title)throws Exception{
        for(Object view:(List<?>)field(hub,"controls"))if(view instanceof TextView&&((TextView)view).getText().toString().split("\n",2)[0].equals(title))return (View)view;
        return null;
    }
    void choose(VelaHub hub,String label)throws Exception{
        await(()->collectionButton(hub,label)!=null);
        View button=collectionButton(hub,label);
        ui(button::performClick);
    }
    void collectionBrowser()throws Exception{
        activity=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->activity.tools(6));
        VelaHub hub=(VelaHub)field(activity,"hub");
        String next=L10n.text("Next ›"),previousPage=L10n.text("‹ Previous"),sources=L10n.text("Choose another library");
        choose(hub,L10n.text("Home collections"));
        collectionCount=12;
        choose(hub,"Plex · Películas");
        await(()->collectionButton(hub,"Colección 12")!=null);
        check(collectionButton(hub,next)==null,"An exact final Plex page offered an empty next page");
        choose(hub,sources);
        for(boolean jelly:new boolean[]{false,true}){
            mixedCollectionTypes=true;collectionCount=13;serverPageSize=7;
            choose(hub,jelly?"Jellyfin":"Plex · Películas");
            String prefix=jelly?"Jellyfin · ":"";
            await(()->collectionButton(hub,prefix+"Colección 7")!=null);
            check(collectionButton(hub,next)!=null,"A short or filtered collection page hid the next server page");
            choose(hub,next);
            await(()->collectionButton(hub,prefix+"Colección 8")!=null);
            check(collectionButton(hub,prefix+"Colección 13")!=null&&collectionButton(hub,next)==null,"Collection paging skipped records or offered a phantom page");
            choose(hub,previousPage);
            await(()->collectionButton(hub,prefix+"Colección 7")!=null);
            check(collectionButton(hub,previousPage)==null,"Previous did not restore the first collection page");
            choose(hub,sources);
        }
        PlexClient plex=new PlexClient(context);
        String serverId=plex.prefs.getString("serverId","");
        try{
            switchOnCollectionRead=true;
            new LibraryClient(context).collectionPage("plex:1",0);
            throw new AssertionError("Collections from the previous Plex account were accepted after a server change");
        }catch(IOException expected){
            check("replacement-server".equals(plex.prefs.getString("serverId","")),"Account switch fixture did not execute");
        }finally{
            switchOnCollectionRead=false;
            plex.prefs.edit().putString("serverId",serverId).commit();
        }
        collectionCount=13;serverPageSize=12;mixedCollectionTypes=false;
    }
    private void checkLibraryDesign(PlexClient plex,LibraryClient library)throws Exception{
        phase="plex-collection-paging";List<LibraryCollection> first=library.collectionPage("plex:1",0).items,last=library.collectionPage("plex:1",12).items;check(first.size()==12&&last.size()==1,"Plex collection paging failed");for(int i=0;i<6;i++)library.toggleCollection(first.get(i));check(library.pinnedCollections().size()==6,"Collection pins missing");boolean refused=false;try{library.toggleCollection(first.get(6));}catch(IOException limitError){refused=true;}check(refused,"Seventh collection exceeded the home budget");for(int i=3;i<6;i++)library.toggleCollection(first.get(i));check(library.collectionHome(first.get(0)).size()==8,"Home collection did not use eight items");overrun=true;refused=false;try{library.collectionHome(first.get(0));}catch(IOException limitError){refused=true;}overrun=false;check(refused,"Ignored server page limits grew the collection");
        phase="plex-seasons-episodes";PlexClient.Film series=plex.item("301");PlexClient.LibraryPage seasons=library.children(series,0);check(seasons.films.size()==2&&seasons.films.get(0).type.equals("season"),"Season metadata was discarded");PlexClient.LibraryPage episodes=library.children(seasons.films.get(0),0);check(episodes.films.size()==24&&episodes.hasNext()&&library.children(seasons.films.get(0),24).films.size()==2,"Episode paging failed");
        phase="jellyfin-collections-and-isolation";JellyfinClient.prefs(context).edit().putString("base","https://vela-fixture.invalid").putString("serverId","jellyfin-fixture-"+SystemClock.elapsedRealtime()).putString("serverName","Jellyfin Demo").putString("userId",guid(1)).putString("token","fixture-session").commit();LibraryClient.activate(context,"Jellyfin");library=new LibraryClient(context);check(library.pinnedCollections().isEmpty(),"Plex pins appeared in Jellyfin-only home");List<LibraryCollection> jf=library.collectionPage("jellyfin:root",0).items;check(jf.size()==12&&library.collectionPage("jellyfin:root",12).items.size()==1,"Jellyfin collection paging failed");PlexClient.Film jfSeries=new JellyfinClient(context).item(guid(3301));PlexClient.LibraryPage jfSeasons=library.children(jfSeries,0);check(jfSeasons.films.size()==2&&jfSeasons.films.get(0).type.equals("season")&&jfSeasons.films.get(0).title.equals("Temporada 1"),"Jellyfin explicit season title lost");check(library.children(jfSeasons.films.get(0),24).films.size()==2,"Jellyfin episode page failed");library.toggleCollection(first.get(2));library.toggleCollection(jf.get(0));check(library.collectionHome(jf.get(0)).size()==8,"Jellyfin home limit failed");LibraryClient.activate(context,"Both");library=new LibraryClient(context);check(library.pinnedCollections().size()==3,"Combined home lost account-bound collections");String pins=VelaWorkspace.prefs(context,"vela-collections").getString("pins","");check(!pins.contains("https:")&&!pins.contains("fixture-session")&&!VelaWorkspace.snapshot(context).toString().contains("vela-collections"),"Account-specific collection data entered portable settings");
        phase="native-home-and-details";VelaStyle.apply(context,VelaStyle.CINEMA);VelaRecentActivity.save(context,"app","com.android.settings","Ajustes de Android","");activity=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");await(()->((Map<?,?>)field(home,"collectionRows")).size()==3);await(()->!((List<?>)field(home,"posterFilms")).isEmpty());await(()->field(home,"heroBitmap")!=null);backdropVisible();if(previewSections)captureSections();shot("05-home");navigationPhotos();Object collectionShelf=((List<?>)field(home,"shelves")).get(1);ui(((List<View>)field(collectionShelf,"items")).get(0)::requestFocus);Thread.sleep(300);shot("05-collections-home");shot("09-cinema-style");for(int mode:new int[]{VelaStyle.COMPACT,VelaStyle.MINIMAL}){ui(activity::finish);VelaStyle.apply(context,mode);activity=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");await(()->!((List<?>)field(home,"posterFilms")).isEmpty());if(mode==VelaStyle.MINIMAL){Thread.sleep(350);check(field(home,"heroBitmap")==null&&((ImageView)field(home,"backdrop")).getDrawable()==null,"Minimal retained a full-screen backdrop");}else {await(()->field(home,"heroBitmap")!=null);backdropVisible();}shot(mode==VelaStyle.MINIMAL?"11-minimal-style":"10-compact-style");}PlexClient.Film movie=plex.item("101");View origin=(View)((List<?>)field(home,"cards")).get(0);ui(()->call("showWatchedMenu",new Class[]{PlexClient.Film.class,View.class},movie,origin));await(()->sheet()!=null&&sheet().image.getDrawable()!=null);check(sheet().heading.getText().toString().equals(movie.title),"Native movie information missing");shot("06-movie-details");key(KeyEvent.KEYCODE_BACK);check(field(home,"detailSheet")==null,"Closing information kept the image owner");ui(()->call("showWatchedMenu",new Class[]{PlexClient.Film.class,View.class},series,origin));ui(action(L10n.text("Seasons"))::performClick);await(()->sheet().actions.getChildCount()==3);shot("07-seasons");ui(sheet().actions.getChildAt(0)::performClick);await(()->sheet().actions.getChildCount()>=25);shot("08-episodes");ui(action(L10n.text("Next ›"))::performClick);await(()->sheet().actions.getChildCount()==4);check(((TextView)sheet().actions.getChildAt(0)).getText().toString().contains("25"),"Native episode pager repeated page one");key(KeyEvent.KEYCODE_BACK);
        phase="account-switch";plex.prefs.edit().putString("serverId","other-fixture-account").commit();library=new LibraryClient(context);int before=requests;check(library.collectionHome(first.get(0)).isEmpty()&&requests==before,"Stale collection queried another account");String other=VelaWorkspace.create(context,"Other demo");VelaWorkspace.select(context,other);check(LibraryCollection.pinned(context).isEmpty(),"Pinned collections crossed profiles");VelaWorkspace.select(context,profile);VelaWorkspace.delete(context,other);
        result.putString("result","PASS unchanged opaque navigation across real artwork changes, focus without activation and provider action bounds; real Plex/Jellyfin collection routes and twelve-item paging, six-pin/eight-home-title limits, overrun refusal, explicit seasons and paged episodes, inactive-provider isolation, portable-settings exclusion, native collection home, all three real home styles and real-artwork information/season/episode controls, account-change refusal and profile isolation. Reserved HTTPS fixture only; no real account or server contacted.");
    }
    public void onCreate(Bundle b){super.onCreate(b);previewSections="sections".equals(b.getString("fixture"));browsePages="collection-pages".equals(b.getString("fixture"));captureLanguage=b.getString("language","es");start();}
    public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}check(!new PlexClient(context).connected()&&!new JellyfinClient(context).connected(),"Personal accounts not allowed");
        previousMode=VelaEdition.mode(context);VelaEdition.select(context,"full");
        catalog=new JSONArray(new String(RefinementHttp.asset(getContext(),"catalog.json"),java.nio.charset.StandardCharsets.UTF_8));for(int i=0;i<catalog.length();i++)if(catalog.getJSONObject(i).optString("type").equals("show")){seriesIndex=i;break;}RefinementHttp.install(this::reply);previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Vela · Demo");VelaWorkspace.select(context,profile);L10n.choose(context,captureLanguage);Ui.init(context);Personalization.prepare(context);Personalization.complete(context);Updates.prefs(context).edit().putBoolean("automatic",false).commit();PlexClient plex=new PlexClient(context);plex.prefs.edit().putString("serverUri","https://vela-fixture.invalid").putString("serverId","fixture-"+SystemClock.elapsedRealtime()).putString("serverToken","fixture-session").putString("serverName","Biblioteca Demo").commit();LibraryClient.activate(context,"Plex");LibraryClient library=new LibraryClient(context);
        if(browsePages){
            phase="native-collection-browser";
            JellyfinClient.prefs(context).edit().putString("base","https://vela-fixture.invalid").putString("serverId","jellyfin-demo").putString("userId",guid(42)).putString("token","fixture-session").commit();
            collectionBrowser();
            result.putString("result","PASS native Plex and Jellyfin collection paging with short, mixed and exact final pages. Forward and back preserve server positions.");
        }else{
            checkLibraryDesign(plex,library);
        }
    }catch(Throwable e){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(e));}finally{try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}}catch(Exception ignored){}}if("lite".equals(previousMode))VelaEdition.select(context,"lite");finish(result.containsKey("error")?1:-1,result);}
}
