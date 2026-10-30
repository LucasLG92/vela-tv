package tv.vela.launcher;

import android.content.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/** Small profile-owned header modules. No widget provider, service or independent clock timer. */
final class VelaWidgets {
    static final List<String> TYPES=Collections.unmodifiableList(Arrays.asList("clock","weather","date","programme","shortcut"));
    static final int LIMIT=4;
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-widgets");}
    static final class State {
        final List<String> items=new ArrayList<>();String shortcut="apps";int format;
        boolean has(String id){return items.contains(id);}
    }
    static State read(Context c){
        State state=new State();String raw=prefs(c).getString("document","");
        if(raw.isEmpty()){if(!c.getPackageName().endsWith(".lite")||VelaWorkspace.prefs(c,"lite_home").getBoolean("show_clock",true))state.items.add("clock");if(!c.getPackageName().endsWith(".lite"))state.items.add("weather");return state;}
        try{if(raw.length()>2048)throw new JSONException("Widget settings too large");JSONObject j=new JSONObject(raw);JSONArray a=j.optJSONArray("items");for(int i=0;a!=null&&i<Math.min(16,a.length())&&state.items.size()<LIMIT;i++){String id=a.optString(i);if(TYPES.contains(id)&&!state.has(id))state.items.add(id);}state.format=Math.max(0,Math.min(2,j.optInt("format")));String shortcut=j.optString("shortcut");if(Arrays.asList("apps","inputs","tv").contains(shortcut))state.shortcut=shortcut;}catch(JSONException|ClassCastException invalid){state.items.clear();state.items.add("clock");}return state;
    }
    static void validate(JSONObject j)throws JSONException{
        if(j.length()>3)throw new JSONException("Unknown widget settings");for(Iterator<String> keys=j.keys();keys.hasNext();)if(!Arrays.asList("items","format","shortcut").contains(keys.next()))throw new JSONException("Unknown widget key");JSONArray a=j.getJSONArray("items");if(a.length()>LIMIT)throw new JSONException("Too many widgets");Set<String> seen=new HashSet<>();for(int i=0;i<a.length();i++)if(!TYPES.contains(a.getString(i))||!seen.add(a.getString(i)))throw new JSONException("Invalid widget");int format=j.optInt("format",0);if(format<0||format>2||!Arrays.asList("apps","inputs","tv").contains(j.optString("shortcut","apps")))throw new JSONException("Invalid widget option");
    }
    static void save(Context c,State state){
        try{JSONObject j=new JSONObject().put("items",new JSONArray(state.items)).put("format",state.format).put("shortcut",state.shortcut);prefs(c).edit().putString("document",j.toString()).commit();}catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    static boolean toggle(Context c,String id){if(!TYPES.contains(id))return false;State s=read(c);if(!s.items.remove(id)){if(s.items.size()==LIMIT)return false;s.items.add(id);}save(c,s);return true;}
    static void move(Context c,String id,int delta){State s=read(c);int from=s.items.indexOf(id),to=from+delta;if(from>=0&&to>=0&&to<s.items.size()){Collections.swap(s.items,from,to);save(c,s);}}
    static VelaShows.Airing next(Context c){VelaShows.Airing next=null;long now=System.currentTimeMillis()/1000;for(VelaShows.Airing p:VelaShows.cached(c))if(p.start>=now&&(next==null||p.start<next.start))next=p;return next;}
    static String label(String id){return L10n.text(id.equals("clock")?"Clock":id.equals("weather")?"Weather":id.equals("date")?"Date":id.equals("programme")?"Next programme":"Quick access");}
    static String target(String id){return L10n.text(id.equals("inputs")?"HDMI inputs":id.equals("tv")?"Live TV":"Apps");}
    static String format(int choice){return L10n.text(choice==1?"24-hour":choice==2?"12-hour":"System");}
    static String detail(String id){return L10n.text(id.equals("clock")?"Your television's local time":id.equals("weather")?"Current temperature and your chosen city":id.equals("date")?"The day and date at a glance":id.equals("programme")?"Your next followed programme from the cached guide":"Open TV, HDMI inputs or your apps");}

    static class Strip extends FrameLayout {
        interface Host {void activate(String id);void focused();void navigation();}
        private Host host;private View down;private final LinearLayout line;
        private final Map<String,FrameLayout> cards=new LinkedHashMap<>();
        private final Map<String,TextView> values=new HashMap<>(),details=new HashMap<>();
        private final Map<String,ImageView> icons=new HashMap<>();
        private State state;private TextView clock,temperature,city;private ImageView icon;
        private AmbientWeather weather;private AmbientWeather.Snapshot snapshot;private String symbol="",message="",signature="";
        private boolean active;private SharedPreferences selectedPrefs;
        private final Handler main=new Handler(Looper.getMainLooper());
        private final Runnable redraw=()->{if(active)refresh();};
        private final SharedPreferences.OnSharedPreferenceChangeListener selectionChanged=(p,k)->{main.removeCallbacks(redraw);main.post(redraw);};
        private final Runnable weatherRestart=()->{if(active&&state.has("weather")){stopWeather();startWeather();}};
        private final SharedPreferences.OnSharedPreferenceChangeListener weatherChanged=(p,k)->{if(Arrays.asList("mode","city","lat","lon","zone").contains(k)){main.removeCallbacks(weatherRestart);main.postDelayed(weatherRestart,60);}};
        private final SharedPreferences.OnSharedPreferenceChangeListener showsChanged=(p,k)->{if(active&&state.has("programme"))updateClock();};
        Strip(Context c){super(c);setFocusable(false);setClipChildren(false);line=new LinearLayout(c);line.setOrientation(LinearLayout.HORIZONTAL);line.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);addView(line,new LayoutParams(-2,72,Gravity.RIGHT));refresh();}
        void host(Host value){host=value;}
        void returnTo(View view){down=view;for(FrameLayout card:cards.values())card.setNextFocusDownId(view.getId());}
        boolean focusFirst(){if(cards.isEmpty())return false;cards.values().iterator().next().requestFocus();return true;}
        private TextView text(int size,int color){TextView v=new TextView(getContext());v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTypeface(Ui.medium);v.setTextColor(color);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
        private void put(FrameLayout parent,View v,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
        private GradientDrawable plate(boolean focus){GradientDrawable d=new GradientDrawable();d.setColor(focus?VelaReading.accent(getContext()):0xD91B1D25);d.setCornerRadius(18);d.setStroke(1,focus?0x00FFFFFF:0x24FFFFFF);return d;}
        private void icon(ImageView image,String name){try(InputStream in=getContext().getAssets().open("symbols/"+name+".png")){image.setImageDrawable(Drawable.createFromStream(in,null));}catch(java.io.IOException ignored){image.setImageDrawable(null);}}
        private void refresh(){
            State next=read(getContext());String key=VelaWorkspace.active(getContext())+next.items+next.shortcut+next.format;
            if(!key.equals(signature)){
                boolean wasFocused=hasFocus();signature=key;state=next;line.removeAllViews();cards.clear();values.clear();details.clear();icons.clear();clock=null;temperature=null;city=null;icon=null;symbol="";
                for(String id:state.items){
                    int width=id.equals("clock")?180:id.equals("weather")?224:id.equals("date")?184:id.equals("programme")?300:208;
                    FrameLayout card=new FrameLayout(getContext());card.setId(View.generateViewId());card.setFocusable(true);card.setFocusableInTouchMode(true);card.setDefaultFocusHighlightEnabled(false);card.setBackground(plate(false));
                    LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(width,72);if(!cards.isEmpty())bounds.leftMargin=12;line.addView(card,bounds);cards.put(id,card);
                    boolean weatherCard=id.equals("weather"),withIcon=weatherCard||id.equals("programme")||id.equals("shortcut");int inset=weatherCard?66:withIcon?52:18,detailInset=weatherCard?inset:18;
                    TextView value=text(id.equals("clock")?30:weatherCard?26:20,VelaTheme.WHITE),detail=text(14,VelaTheme.SECONDARY);put(card,value,inset,10,width-inset-16,32);put(card,detail,detailInset,44,width-detailInset-(weatherCard?16:18),20);values.put(id,value);details.put(id,detail);
                    if(withIcon){ImageView image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setColorFilter(VelaTheme.WHITE);put(card,image,18,weatherCard?18:16,weatherCard?36:24,weatherCard?36:24);icons.put(id,image);if(id.equals("programme"))icon(image,"bell");else if(id.equals("shortcut"))icon(image,state.shortcut.equals("inputs")?"hdmi":state.shortcut.equals("tv")?"live_tv":"apps");}
                    if(id.equals("clock")){clock=value;clock.setFontFeatureSettings("tnum");clock.setGravity(Gravity.CENTER);FrameLayout.LayoutParams clockBounds=(FrameLayout.LayoutParams)clock.getLayoutParams();clockBounds.topMargin=0;clockBounds.height=72;clock.setLayoutParams(clockBounds);detail.setVisibility(GONE);}if(id.equals("weather")){temperature=value;city=detail;icon=icons.get(id);}
                    card.setOnClickListener(v->{if(host!=null)host.activate(id.equals("shortcut")?state.shortcut:id);});
                    card.setOnFocusChangeListener((v,f)->{card.setBackground(plate(f));value.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);detail.setTextColor(f?VelaTheme.INK:VelaTheme.SECONDARY);if(icons.get(id)!=null)icons.get(id).setColorFilter(f?VelaTheme.INK:VelaTheme.WHITE);if(f&&host!=null)host.focused();});
                    card.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_UP)return true;if(k==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN&&(down==null||!down.isShown()||!down.requestFocus())&&host!=null)host.navigation();return true;}if(k==KeyEvent.KEYCODE_DPAD_LEFT&&cards.values().iterator().next()==v&&host!=null){if(e.getAction()==KeyEvent.ACTION_DOWN)host.navigation();return true;}return false;});
                }
                List<FrameLayout> ordered=new ArrayList<>(cards.values());for(int i=0;i<ordered.size();i++){FrameLayout card=ordered.get(i);card.setNextFocusRightId(ordered.get(Math.min(i+1,ordered.size()-1)).getId());if(i>0)card.setNextFocusLeftId(ordered.get(i-1).getId());if(down!=null)card.setNextFocusDownId(down.getId());}
                if(wasFocused&&!focusFirst()&&down!=null)down.requestFocus();
            }
            if(active&&state.has("weather")){if(weather==null)startWeather();}else stopWeather();setVisibility(cards.isEmpty()?GONE:VISIBLE);updateClock();
        }
        void start(){if(!active){active=true;selectedPrefs=prefs(getContext());selectedPrefs.registerOnSharedPreferenceChangeListener(selectionChanged);AmbientWeather.prefs(getContext()).registerOnSharedPreferenceChangeListener(weatherChanged);VelaShows.prefs(getContext()).registerOnSharedPreferenceChangeListener(showsChanged);}refresh();}
        private void startWeather(){if(weather!=null||!active||!state.has("weather"))return;message="";weather=new AmbientWeather(getContext(),(value,status)->{if(!active||!state.has("weather"))return;snapshot=value;message=status;updateClock();});weather.start();}
        private void stopWeather(){if(weather!=null){weather.close();weather=null;}snapshot=null;symbol="";if(icon!=null)icon.setImageDrawable(null);}
        void updateClock(){
            if(state==null)return;Date now=new Date();boolean twentyFour=state.format==1||state.format==0&&android.text.format.DateFormat.is24HourFormat(getContext());String time=new SimpleDateFormat(twentyFour?"HH:mm":"h:mm a",Ui.locale()).format(now);
            if(clock!=null)clock.setText(time);
            if(state.has("date")){values.get("date").setText(new SimpleDateFormat("EEEE",Ui.locale()).format(now));details.get("date").setText(new SimpleDateFormat("d MMMM",Ui.locale()).format(now));}
            if(state.has("programme")){VelaShows.Airing next=next(getContext());values.get("programme").setText(next==null?L10n.text("Upcoming"):next.title);details.get("programme").setText(next==null?L10n.text("Follow programmes in the guide"):new SimpleDateFormat("HH:mm",Ui.locale()).format(new Date(next.start*1000))+" · "+next.channel);}
            if(state.has("shortcut")){values.get("shortcut").setText(target(state.shortcut));details.get("shortcut").setText(L10n.text("Quick access"));}
            updateWeather();for(String id:cards.keySet())cards.get(id).setContentDescription(label(id)+", "+values.get(id).getText()+", "+details.get(id).getText());if(cards.containsKey("weather")&&snapshot!=null&&snapshot.usable(System.currentTimeMillis()))cards.get("weather").setContentDescription(cards.get("weather").getContentDescription()+", "+condition(snapshot.code));
        }
        private String condition(int code){if(code==0)return Ui.tr("Céu limpo","Clear","Despejado");if(code<=2)return Ui.tr("Sol e nuvens","Partly cloudy","Sol y nubes");if(code==3)return Ui.tr("Nublado","Cloudy","Nublado");if(code==45||code==48)return Ui.tr("Nevoeiro","Fog","Niebla");if(code>=71&&code<=77||code==85||code==86)return Ui.tr("Neve","Snow","Nieve");if(code>=95)return Ui.tr("Trovoada","Thunderstorm","Tormenta");return Ui.tr("Chuva","Rain","Lluvia");}
        private void updateWeather(){
            if(temperature==null)return;long now=System.currentTimeMillis();
            if(snapshot!=null&&snapshot.usable(now)){temperature.setText(snapshot.degrees());city.setText(snapshot.city+(snapshot.fresh(now)?"":" · "+L10n.text("Last update")));String name=AmbientWeather.icon(snapshot.code,snapshot.day);if(!name.equals(symbol)){icon(icon,name);symbol=name;}icon.setVisibility(VISIBLE);}
            else{temperature.setText("—");AmbientWeather.Place place=AmbientWeather.manual(getContext());city.setText(place==null?L10n.text("Choose your city"):(message.startsWith(L10n.text("Loading weather for "))?Ui.tr("Consultando…","Loading…","Consultando…"):L10n.text("Weather unavailable"))+" · "+place.name);icon(icon,"cloud");symbol="";icon.setVisibility(VISIBLE);}
        }
        void stop(){active=false;if(selectedPrefs!=null)selectedPrefs.unregisterOnSharedPreferenceChangeListener(selectionChanged);selectedPrefs=null;AmbientWeather.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(weatherChanged);VelaShows.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(showsChanged);main.removeCallbacksAndMessages(null);stopWeather();}
    }
}
