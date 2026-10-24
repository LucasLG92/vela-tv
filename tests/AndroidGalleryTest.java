package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Public documentation only: a clean emulator, openly licensed artwork and explicit demo data. */
public class AndroidGalleryTest extends Instrumentation {
    private final List<Activity> opened=new ArrayList<>();
    private final List<Bitmap> bitmaps=new ArrayList<>();
    private final List<String> shots=new ArrayList<>();
    private final List<PlexClient.Film> films=new ArrayList<>();
    private final List<Bitmap> posters=new ArrayList<>();
    private Bitmap backdrop;
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private Object field(Object o,String key)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(key);}
    private void set(Object o,String key,Object value)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(key);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException next){}throw new NoSuchFieldException(key);}
    private void ui(Runnable run){runOnMainSync(run);waitForIdleSync();}
    private void call(Object o,String name,Class<?> type,Object arg){final Object target=o instanceof ScreenActivity?((ScreenActivity)o).screen:o;ui(()->{try{Method m=type==null?target.getClass().getDeclaredMethod(name):target.getClass().getDeclaredMethod(name,type);m.setAccessible(true);if(type==null)m.invoke(target);else m.invoke(target,arg);}catch(Exception e){throw new RuntimeException(e);}});}
    private interface Ready{boolean ready()throws Exception;}
    private void await(Ready ready,String why,long timeout)throws Exception{long until=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<until){boolean[] done={false};ui(()->{try{done[0]=ready.ready();}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;Thread.sleep(150);}throw new AssertionError(why);}
    private <T extends Activity>T open(Class<T> type)throws Exception{T a=(T)startActivitySync(new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));opened.add(a);await(()->a.hasWindowFocus(),"Window not focused: "+type,10_000);return a;}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void capture(String name)throws Exception{Thread.sleep(500);Bitmap shot=getUiAutomation().takeScreenshot();check(shot!=null&&shot.getWidth()==1920&&shot.getHeight()==1080,"Screenshot is not Full HD");try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"public-"+name+".webp"))){shot.compress(Bitmap.CompressFormat.WEBP_LOSSY,90,out);}finally{shot.recycle();}shots.add(name);}
    private Bitmap asset(String name,int sample)throws Exception{BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;try(InputStream in=getContext().getAssets().open(name)){Bitmap b=BitmapFactory.decodeStream(in,null,options);check(b!=null,"Missing licensed image "+name);bitmaps.add(b);return b;}}
    private void artwork(CinemaHomeView home)throws Exception{Thread.sleep(200);ui(()->{try{((ImageView)field(home,"backdrop")).setImageBitmap(backdrop);((ImageView)field(home,"sidebarArt")).setImageBitmap(backdrop);set(home,"heroBitmap",backdrop);set(home,"heroKey",films.get(0).provider+films.get(0).key);List<ImageView> views=(List<ImageView>)field(home,"posterImages");List<PlexClient.Film> visible=(List<PlexClient.Film>)field(home,"posterFilms");for(int i=0;i<views.size();i++)views.get(i).setImageBitmap(posters.get(films.indexOf(visible.get(i))));}catch(Exception e){throw new RuntimeException(e);}});}
    private void restore(SharedPreferences prefs,Map<String,?> saved){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();if(v instanceof String)e.putString(entry.getKey(),(String)v);else if(v instanceof Integer)e.putInt(entry.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(entry.getKey(),(Long)v);else if(v instanceof Boolean)e.putBoolean(entry.getKey(),(Boolean)v);else if(v instanceof Float)e.putFloat(entry.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(entry.getKey(),new HashSet<>((Set<String>)v));}e.commit();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();Map<SharedPreferences,Map<String,?>> saved=new LinkedHashMap<>();
        try{
            check(Build.MODEL.startsWith("sdk_gphone")||Build.HARDWARE.equals("ranchu"),"Documentation gallery must run on an emulator");PlexClient plex=new PlexClient(c);check(!plex.connected()&&plex.prefs.getString("accountToken","").isEmpty()&&AmbientWeather.manual(c)==null,"Gallery requires a clean emulator without a personal account or location");
            for(SharedPreferences p:new SharedPreferences[]{plex.prefs,Visuals.prefs(c),AmbientPrefs.get(c),AmbientWeather.prefs(c),SidebarApps.prefs(c),Personalization.prefs(c),LiveTvData.prefs(c)})saved.put(p,new HashMap<>(p.getAll()));
            Personalization.prepare(c);Personalization.complete(c);Appearance.reset(c);AmbientWeather.select(c,new AmbientWeather.Place("Reikiavik · Demo","",64.15,-21.94,"Atlantic/Reykjavik"));
            String[] names={"Big Buck Bunny","Charge","Coffee Run","Cosmos Laundromat","Elephants Dream","Sintel","Spring","Sprite Fright","Tears of Steel"};int[] years={2008,2022,2020,2015,2006,2010,2019,2021,2012},order={5,0,8,3,4,6,7,1,2};
            for(int index:order){PlexClient.Film f=new PlexClient.Film();f.key="demo-"+index;f.title=names[index];f.type="movie";f.thumb="/demo/poster-"+(index+1);f.art="/demo/backdrop";f.section="Cortos abiertos";f.meta=years[index]+"  ·  Cortometraje  ·  Creative Commons";f.summary=index==5?"Una joven viajera busca a un pequeño dragón. Un cortometraje abierto de Blender Foundation, dirigido por Colin Levy.":"Un cortometraje abierto de Blender Foundation. Contenido de demostración con licencia Creative Commons.";films.add(f);posters.add(asset("poster-"+(index+1)+(index==2||index==8?".png":".jpg"),2));}
            backdrop=asset("backdrop.jpg",1);
            SidebarApps.prefs(c).edit().putString("slots",new org.json.JSONArray(Arrays.asList("com.plexapp.android",SidebarApps.YOUTUBE,"com.amazon.amazonvideo.livingroom","com.android.chrome","com.google.android.apps.photos","com.google.android.deskclock")).toString()).commit();
            // This literal is deliberately a non-working demo marker, never a Plex credential.
            plex.prefs.edit().putString("serverToken","demo").putString("serverId","demo").putString("serverName","Vela Demo").commit();
            HomeActivity a=open(HomeActivity.class);CinemaHomeView home=(CinemaHomeView)field(a,"home");ui(()->{try{set(home,"films",films);}catch(Exception e){throw new RuntimeException(e);}});call(home,"showFilms",null,null);artwork(home);Thread.sleep(1500);capture("01-inicio");
            ui(()->{Appearance.set(c,"color",2);Appearance.set(c,"size",0);Appearance.set(c,"backdrop",2);});call(home,"applyAppearance",null,null);artwork(home);capture("02-aspecto-menta-compacto");
            ui(()->{Appearance.set(c,"color",3);Appearance.set(c,"size",2);Appearance.set(c,"backdrop",0);});call(home,"applyAppearance",null,null);artwork(home);capture("03-aspecto-lavanda-grande");ui(()->Appearance.reset(c));call(home,"applyAppearance",null,null);artwork(home);
            ui(()->{films.get(0).continuing=true;films.get(0).progress=.45f;films.get(1).continuing=true;films.get(1).progress=.68f;});call(home,"showFilms",null,null);artwork(home);capture("04-seguir-viendo");ui(()->{films.get(0).continuing=false;films.get(1).continuing=false;films.get(0).progress=-1;films.get(1).progress=-1;});
            ui(()->{try{set(home,"filter",4);PlexClient.Section section=new PlexClient.Section("demo","Cortos abiertos · Demo","movie");set(home,"catalogSection",section);set(home,"catalogSections",Collections.singletonList(section));set(home,"catalogPage",new PlexClient.LibraryPage(films,24,33,9));}catch(Exception e){throw new RuntimeException(e);}});call(home,"showFilms",null,null);artwork(home);capture("05-biblioteca-paginada");call(home,"reset",null,null);artwork(home);
            ui(()->{try{((List<View>)field(home,"sideButtons")).get(0).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});artwork(home);capture("06-menu-lateral");
            View app=((List<View>)field(home,"appButtons")).get(0);ui(()->app.requestFocus());call(home,"openShortcutMenu",View.class,app);capture("07-menu-accesos");key(KeyEvent.KEYCODE_BACK);
            call(home,"beginReorder",View.class,app);capture("08-ordenar-accesos");key(KeyEvent.KEYCODE_BACK);Thread.sleep(3000);
            call(home,"showApps",View.class,app);await(()->{HomeScreenHost host=(HomeScreenHost)field(home,"panels");InstalledAppsGrid grid=host==null||!(host.screen instanceof AppsScreen)?null:(InstalledAppsGrid)field(host.screen,"grid");if(grid==null||grid.getCount()==0||grid.getChildCount()==0)return false;for(int i=0;i<grid.getChildCount();i++)if(((ImageView)field(grid.getChildAt(i),"image")).getDrawable()==null)return false;return true;},"App icons did not load",15_000);capture("09-todas-las-apps");key(KeyEvent.KEYCODE_BACK);
            SettingsActivity settings=open(SettingsActivity.class);Object options=field(settings,"view");capture("10-ajustes");
            for(int page:new int[]{17,18,19,20,2,11,14,6,9}){call(options,"render",int.class,page);capture(page==17?"11-apariencia":page==18?"12-colores":page==19?"13-carteles":page==20?"14-fondo":page==2?"15-rendimiento":page==11?"16-accesos":page==14?"17-cuentas":page==6?"18-salvapantallas-ajustes":"19-ubicacion-demo");}
            AmbientPrefs.get(c).edit().putString("style","plex").putBoolean("clock",true).putBoolean("weather",true).commit();ui(()->settings.showPreview());Thread.sleep(800);AmbientView ambient=(AmbientView)field(settings,"preview");ui(()->{try{ImageView image=(ImageView)field(ambient,"photoA");image.setImageBitmap(backdrop);image.setAlpha(1f);image.setImageAlpha(255);set(ambient,"currentPhoto",image);((View)field(ambient,"veil")).setAlpha(0);((View)field(ambient,"status")).setVisibility(View.GONE);((View)field(ambient,"chrome")).setAlpha(1);((View)field(ambient,"caption")).setAlpha(1);((TextView)field(ambient,"title")).setText("Sintel");((TextView)field(ambient,"credit")).setText("Blender Foundation · CC BY 3.0 · Demostración");}catch(Exception e){throw new RuntimeException(e);}});capture("20-salvapantallas-plex-demo");key(KeyEvent.KEYCODE_BACK);
            AmbientPrefs.get(c).edit().putString("style","aerial").putString("theme","landscape").commit();ui(()->settings.showPreview());AmbientView aerial=(AmbientView)field(settings,"preview");await(()->(Boolean)field(aerial,"videoStarted"),"Aerial video did not render",35_000);call(aerial,"releasePlayer",null,null);ui(()->{try{set(aerial,"scenes",new ArrayList<>(Collections.singletonList(new AerialCatalog.Scene("La Tierra desde la ISS · Demo","https://assets.science.nasa.gov/content/dam/science/psd/lunar-science/2023/09/jsc2015m000273_orig.mp4","NASA/JSC · Vídeo de demostración"))));}catch(Exception e){throw new RuntimeException(e);}});call(aerial,"nextVideo",null,null);await(()->(Boolean)field(aerial,"videoStarted"),"NASA demo video did not render",30_000);capture("21-salvapantallas-aereos");key(KeyEvent.KEYCODE_BACK);
            // Remove the marker before account screenshots and the setup guide.
            plex.prefs.edit().remove("serverToken").remove("serverId").remove("serverName").commit();call(options,"render",int.class,14);capture("22-cuentas-sin-vincular");
            SetupActivity setup=open(SetupActivity.class);capture("23-asistente");call(setup,"go",int.class,1);capture("24-asistente-cuentas");ui(()->setup.finish());
            LiveTvActivity tv=open(LiveTvActivity.class);await(()->tv.catalog!=null,"Public TV catalog missing",40_000);Thread.sleep(2000);capture("25-television");call(tv,"showSources",null,null);capture("26-listas-tv");key(KeyEvent.KEYCODE_BACK);
            LiveTvData.Channel demo=new LiveTvData.Channel("demo","Vídeo de prueba CC0","Demostración","",Collections.singletonList("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"));ui(()->tv.play(demo));await(()->tv.playback!=null&&tv.playback.rendered,"Test video did not render",25_000);capture("27-reproductor-video-demo");key(KeyEvent.KEYCODE_BACK);
            result.putInt("screenshots",shots.size());result.putString("gallery",shots.toString());result.putString("result","PASS clean-emulator gallery: actual Vela views, real openly licensed Blender artwork, fictional Plex demo marker removed, no private account/avatar/library/city, documented demo weather location, native TV catalog and real video. Emulator-only and preferences restored.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{for(int i=opened.size()-1;i>=0;i--){Activity a=opened.get(i);ui(()->{if(!a.isFinishing())a.finish();});}for(Map.Entry<SharedPreferences,Map<String,?>> e:saved.entrySet())restore(e.getKey(),e.getValue());for(Bitmap b:bitmaps)if(!b.isRecycled())b.recycle();}
        finish(result.containsKey("error")?1:-1,result);
    }
}
