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
import java.util.concurrent.*;

/** Uses Madrid as an explicit test city, restores all personal settings, and never changes home mode. */
public class AndroidWeatherTest extends Instrumentation {
    interface Condition {boolean ready()throws Exception;}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void await(Condition condition,String why)throws Exception{
        long end=SystemClock.uptimeMillis()+30_000;
        while(SystemClock.uptimeMillis()<end){boolean[] ready={false};Throwable[] error={null};runOnMainSync(()->{try{ready[0]=condition.ready();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);if(ready[0])return;Thread.sleep(150);}
        throw new AssertionError(why);
    }
    private void restore(SharedPreferences prefs,Map<String,?> saved){
        SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:saved.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);}edit.commit();
    }
    private void screenshot(SettingsActivity activity,Context context,String file)throws Exception{
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Could not capture native screen");
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),file))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private void infoVisible(AmbientView view)throws Exception{
        await(()->field(view,"weatherSnapshot")!=null,"Live weather never arrived");
        AmbientWeather.Snapshot snapshot=(AmbientWeather.Snapshot)field(view,"weatherSnapshot");check(snapshot.city.equals("Madrid"),"Weather associated with wrong city");
        check(((TextView)field(view,"weatherText")).getText().toString().startsWith(snapshot.degrees()),"Real temperature missing");
        check(((TextView)field(view,"clock")).getText().toString().matches("[0-2][0-9]:[0-5][0-9]"),"Clock missing");
        check(((TextView)field(view,"weatherCity")).getText().toString().equals("Madrid"),"City missing");
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();Map<String,?> ambient=new HashMap<>(AmbientPrefs.get(c).getAll()),weather=new HashMap<>(AmbientWeather.prefs(c).getAll());SettingsActivity activity=null;
        try{
            AmbientPrefs.get(c).edit().clear().commit();check(AmbientPrefs.clock(c)&&AmbientPrefs.weather(c),"Clock/weather not enabled by default");
            AmbientPrefs.get(c).edit().putBoolean("clock",false).commit();check(!AmbientPrefs.clock(c),"Migration overwrote user toggle");
            long now=System.currentTimeMillis();String fixture="{\"timezone\":\"Europe/Madrid\",\"current\":{\"temperature_2m\":-2.4,\"weather_code\":71,\"is_day\":0}}";
            AmbientWeather.Snapshot cold=AmbientWeather.parse(fixture,"Fixture",now);check(cold.degrees().equals("-2 °C")&&!cold.day,"Negative/night parsing failed");
            check(cold.fresh(now+AmbientWeather.FRESH-1)&&!cold.fresh(now+AmbientWeather.FRESH),"Fresh cache expiry failed");check(!cold.usable(now+AmbientWeather.MAX_AGE+1)&&!cold.usable(now-1),"Expired/future weather accepted");
            boolean rejected=false;try{AmbientWeather.parse(fixture.replace("-2.4","null"),"Fixture",now);}catch(Exception expected){rejected=true;}check(rejected,"Missing temperature displayed as real zero");
            check(AmbientWeather.condition(95).equals("Tormenta")&&AmbientWeather.icon(0,false).equals("clear_night"),"Weather symbols incorrect");
            List<AmbientWeather.Place> places=AmbientWeather.search("Madrid");AmbientWeather.Place madrid=null;for(AmbientWeather.Place place:places)if(place.zone.equals("Europe/Madrid")&&place.name.equals("Madrid")){madrid=place;break;}check(madrid!=null,"Real city search failed");
            AmbientWeather.prefs(c).edit().clear().commit();AmbientWeather.select(c,madrid);
            AmbientPrefs.get(c).edit().putString("style","plex").putBoolean("clock",true).putBoolean("weather",true).putBoolean("captions",true).putInt("seconds",120).commit();
            activity=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final SettingsActivity a=activity;
            runOnMainSync(()->a.showPreview());AmbientView plex=(AmbientView)field(a,"preview");
            infoVisible(plex);await(()->{ImageView image=(ImageView)field(plex,"currentPhoto");return image!=null&&image.getAlpha()>.99f;},"Real Plex backdrop missing");
            await(()->((View)field(plex,"caption")).getAlpha()<.01f,"Caption did not disappear after eight seconds");
            check(((View)field(plex,"chrome")).getAlpha()>.99f,"Clock/weather disappeared with title");check(((View)field(plex,"weatherIcon")).getVisibility()==View.VISIBLE,"Weather icon missing");
            screenshot(a,c,"weather-plex.png");AmbientWeather client=(AmbientWeather)field(plex,"weather");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);check(field(plex,"weather")==null&&(Boolean)field(client,"closed"),"Weather client not released");check(((ExecutorService)field(client,"io")).isShutdown(),"Weather executor retained after exit");
            AmbientWeather.prefs(c).edit().putString("mode","auto").commit();
            AmbientPrefs.get(c).edit().putString("style","aerial").putString("theme","landscape").commit();runOnMainSync(()->a.showPreview());AmbientView aerial=(AmbientView)field(a,"preview");
            infoVisible(aerial);await(()->(Boolean)field(aerial,"videoStarted"),"Aerial never rendered");await(()->((View)field(aerial,"caption")).getAlpha()<.01f,"Aerial caption did not disappear");check(((View)field(aerial,"chrome")).getAlpha()>.99f,"Weather vanished on aerial");screenshot(a,c,"weather-aerial.png");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);check(field(aerial,"weather")==null,"Aerial weather retained");
            Object settings=field(a,"view");Method render=settings.getClass().getDeclaredMethod("render",int.class),search=settings.getClass().getDeclaredMethod("searchCities");render.setAccessible(true);search.setAccessible(true);
            runOnMainSync(()->{try{render.invoke(settings,10);((EditText)field(settings,"cityInput")).setText("Madrid");search.invoke(settings);}catch(Exception e){throw new RuntimeException(e);}});
            await(()->!((List<?>)field(settings,"cityResults")).isEmpty(),"Native city search did not show results");
            runOnMainSync(()->{try{((List<View>)field(settings,"rows")).get(1).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            check(AmbientWeather.mode(c).equals("manual")&&AmbientWeather.name(c).equals("Madrid"),"Selecting a search result did not save the city");
            screenshot(a,c,"weather-settings.png");
            // Without a configured location or permission, remain usable and show setup instead of invented data.
            AmbientWeather.prefs(c).edit().clear().putString("mode","auto").commit();
            if(c.checkSelfPermission(AmbientWeather.PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&AmbientWeather.manual(c)==null){
                CountDownLatch updated=new CountDownLatch(1);AmbientWeather[] fallback={null};String[] message={null};runOnMainSync(()->{fallback[0]=new AmbientWeather(c,(snapshot,text)->{message[0]=text;updated.countDown();});fallback[0].start();});check(updated.await(2,TimeUnit.SECONDS)&&message[0].contains("ubicación"),"Permission-free setup fallback failed");runOnMainSync(()->fallback[0].close());
            }
            result.putString("result","PASS defaults, preserved toggles, live native city search and selection, automatic/manual fallback, real weather, invalid/missing data, cache age, persistent clock/weather on Plex and real aerials, and resource cleanup. Madrid was a test city only.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null){SettingsActivity a=activity;runOnMainSync(()->a.finish());}restore(AmbientPrefs.get(c),ambient);restore(AmbientWeather.prefs(c),weather);}
        finish(result.containsKey("error")?1:-1,result);
    }
}
