package tv.vela.launcher;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Weather only, approximate location, bounded HTTP and a private thirty-minute cache. */
final class AmbientWeather {
    static final String PERMISSION=Manifest.permission.ACCESS_COARSE_LOCATION;
    static final long FRESH=30*60_000L,MAX_AGE=3*60*60_000L;
    interface Listener {void update(Snapshot snapshot,String message);}
    static final class Place {
        final String name,label,zone;final double lat,lon;
        Place(String name,String label,double lat,double lon,String zone){this.name=name;this.label=label;this.lat=round(lat);this.lon=round(lon);this.zone=zone;}
        String key(){return lat+","+lon;}
    }
    static final class Snapshot {
        final String city,zone;final double temperature;final int code;final boolean day;final long at;
        Snapshot(String city,String zone,double t,int code,boolean day,long at){this.city=city;this.zone=zone;temperature=t;this.code=code;this.day=day;this.at=at;}
        boolean usable(long now){return now>=at&&now-at<=MAX_AGE;}
        boolean fresh(long now){return now>=at&&now-at<FRESH;}
        String degrees(){return Math.round(temperature)+" °C";}
    }
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Visuals.worker(1);
    private CancellationSignal location;
    private boolean closed,resolved;
    private Place selected;
    private Snapshot last;
    private final Runnable refresh=this::refresh;
    AmbientWeather(Context c,Listener listener){context=c.getApplicationContext();this.listener=listener;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("ambient-weather",Context.MODE_PRIVATE);}
    static double round(double n){return Math.round(n*100d)/100d;}
    static Place manual(Context c){
        SharedPreferences p=prefs(c);String name=p.getString("city",Config.CITY).trim();
        try{double lat=Double.parseDouble(p.getString("lat",Config.LATITUDE)),lon=Double.parseDouble(p.getString("lon",Config.LONGITUDE));
            if(!name.isEmpty()&&Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180&&(lat!=0||lon!=0))return new Place(name,name,lat,lon,p.getString("zone",""));
        }catch(Exception ignored){}return null;
    }
    static String mode(Context c){return prefs(c).getString("mode",manual(c)==null?"auto":"manual");}
    static String name(Context c){Place p=manual(c);return "manual".equals(mode(c))&&p!=null?p.name:Ui.tr("Automática","Automatic","Automática");}
    static void select(Context c,Place place){prefs(c).edit().putString("mode","manual").putString("city",place.name).putString("lat",Double.toString(place.lat)).putString("lon",Double.toString(place.lon)).putString("zone",place.zone).remove("cache").apply();}
    static List<Place> search(String query)throws Exception{
        query=query.trim();if(query.length()<2||query.length()>100)return Collections.emptyList();
        JSONObject body=new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query,"UTF-8")+"&count=5&language="+Ui.language+"&format=json"));
        JSONArray results=body.optJSONArray("results");List<Place> places=new ArrayList<>();
        for(int i=0;results!=null&&i<results.length();i++){
            JSONObject o=results.getJSONObject(i);String name=o.getString("name"),area=o.optString("admin1"),country=o.optString("country");
            String label=name+(area.isEmpty()||area.equals(name)?"":" · "+area)+(country.isEmpty()?"":" · "+country);
            places.add(new Place(name,label,o.getDouble("latitude"),o.getDouble("longitude"),o.optString("timezone")));
        }return places;
    }
    static Snapshot parse(String body,String city,long at)throws Exception{
        JSONObject root=new JSONObject(body),current=root.getJSONObject("current");
        if(current.isNull("temperature_2m"))throw new IOException("Sin temperatura.");
        double t=current.getDouble("temperature_2m");if(!Double.isFinite(t)||t < -100||t > 100)throw new IOException("Temperatura no válida.");
        return new Snapshot(city,root.optString("timezone",""),t,current.getInt("weather_code"),current.optInt("is_day",1)==1,at);
    }
    static String condition(int code){
        if(code==0)return "Despejado";if(code==1)return "Poco nuboso";if(code==2)return "Parcialmente nublado";if(code==3)return "Nublado";
        if(code==45||code==48)return "Niebla";if(code>=51&&code<=57)return "Llovizna";
        if(code>=61&&code<=67||code>=80&&code<=82)return "Lluvia";if(code>=71&&code<=77||code==85||code==86)return "Nieve";
        if(code==95||code==96||code==99)return "Tormenta";return "Tiempo local";
    }
    static String icon(int code,boolean day){
        if(code==0)return day?"sunny":"clear_night";if(code==1||code==2)return day?"partly_cloudy_day":"partly_cloudy_night";
        if(code==3)return "cloud";if(code==45||code==48)return "foggy";if(code>=71&&code<=77||code==85||code==86)return "snowflake";
        if(code==95||code==96||code==99)return "thunderstorm";return "rainy";
    }
    static String get(String url)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","Vela/1.2.0");
        try{if(connection.getResponseCode()!=200)throw new IOException("Servicio no disponible.");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>256*1024)throw new IOException("Respuesta demasiado grande.");out.write(b,0,n);}return new String(out.toByteArray(),"UTF-8");
            }
        }finally{connection.disconnect();}
    }
    void start(){
        if(closed)return;
        if(!"auto".equals(mode(context))){use(manual(context));return;}
        try{
            LocationManager manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            if(context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED&&manager!=null&&manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                location=new CancellationSignal();main.postDelayed(()->fallback(),10_000);
                manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER,location,command->main.post(command),fix->{
                    if(closed||resolved)return;
                    if(fix==null){fallback();return;}
                    resolved=true;main.removeCallbacksAndMessages(null);location.cancel();
                    Place p=new Place("Ubicación de la TV","",fix.getLatitude(),fix.getLongitude(),"");
                    prefs(context).edit().putString("autoLat",Double.toString(p.lat)).putString("autoLon",Double.toString(p.lon)).putLong("autoAt",System.currentTimeMillis()).apply();use(p);
                });return;
            }
        }catch(SecurityException|IllegalArgumentException ignored){}
        fallback();
    }
    private void fallback(){
        if(closed||resolved)return;resolved=true;if(location!=null)location.cancel();main.removeCallbacksAndMessages(null);
        Place p=manual(context);if(p==null)try{SharedPreferences stored=prefs(context);long age=System.currentTimeMillis()-stored.getLong("autoAt",0);if(age>=0&&age<7*86400_000L)p=new Place("Ubicación de la TV","",Double.parseDouble(stored.getString("autoLat","")),Double.parseDouble(stored.getString("autoLon","")),"");}catch(Exception ignored){}
        use(p);
    }
    private void use(Place place){
        if(closed)return;selected=place;
        if(place==null){listener.update(null,Ui.tr("Escolha sua localização nos Ajustes","Choose your location in Settings","Elige tu ubicación en Ajustes"));return;}
        SharedPreferences p=prefs(context);
        if(place.key().equals(p.getString("cacheKey","")))try{last=parse(p.getString("cache",""),place.name,p.getLong("cacheAt",0));}catch(Exception ignored){}
        long now=System.currentTimeMillis();if(last!=null&&last.usable(now))listener.update(last,"");else listener.update(null,"Consultando el tiempo de "+place.name+"…");
        if(last!=null&&last.fresh(now)){main.postDelayed(refresh,Math.max(1000,FRESH-(now-last.at)));return;}
        refresh();
    }
    private void refresh(){
        if(closed||selected==null)return;Place place=selected;
        io.execute(()->{try{
            String body=get("https://api.open-meteo.com/v1/forecast?latitude="+place.lat+"&longitude="+place.lon+"&current=temperature_2m,weather_code,is_day&timezone=auto");Snapshot snapshot=parse(body,place.name,System.currentTimeMillis());
            main.post(()->{if(closed)return;last=snapshot;prefs(context).edit().putString("cache",body).putString("cacheKey",place.key()).putLong("cacheAt",snapshot.at).apply();listener.update(snapshot,"");main.postDelayed(refresh,FRESH);});
        }catch(Exception ignored){main.post(()->{if(closed)return;listener.update(last!=null&&last.usable(System.currentTimeMillis())?last:null,"Tiempo no disponible");main.postDelayed(refresh,15*60_000L);});}});
    }
    void close(){closed=true;if(location!=null)location.cancel();main.removeCallbacksAndMessages(null);io.shutdownNow();last=null;selected=null;}
}
