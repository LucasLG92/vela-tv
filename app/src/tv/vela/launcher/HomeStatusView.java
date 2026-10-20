package tv.vela.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Static native header; shares the screensaver's location and thirty-minute weather cache. */
final class HomeStatusView extends FrameLayout {
    private final TextView clock,temperature,city;
    private final ImageView icon;
    private AmbientWeather weather;
    private AmbientWeather.Snapshot snapshot;
    private String message="",symbol="";
    private boolean active;

    HomeStatusView(Context context){
        super(context);setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout line=new LinearLayout(context);line.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(line,0,0,340,44);
        clock=text(31,0xFFF7F7FA,Ui.medium);clock.setFontFeatureSettings("tnum");clock.setLetterSpacing(.01f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,44);cp.rightMargin=18;line.addView(clock,cp);
        TextView separator=text(17,0xFFAEB4C0,Ui.regular);separator.setText("·");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,44);sp.rightMargin=18;line.addView(separator,sp);
        icon=new ImageView(context);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setColorFilter(0xFFF1F4FA);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(24,24);ip.rightMargin=10;line.addView(icon,ip);
        temperature=text(25,0xFFF0F2F7,Ui.medium);line.addView(temperature,new LinearLayout.LayoutParams(-2,44));
        city=text(16,0xFFCFD3DD,Ui.regular);city.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(city,0,44,340,24);
        updateClock();
    }
    private TextView text(int size,int color,Typeface face){
        TextView view=new TextView(getContext());view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);view.setTextColor(color);view.setTypeface(face);view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);view.setSingleLine(true);view.setEllipsize(TextUtils.TruncateAt.END);view.setShadowLayer(4,0,1,0xDD000000);view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return view;
    }
    private void put(View view,int x,int y,int width,int height){LayoutParams p=new LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;addView(view,p);}
    void start(){
        if(active)return;active=true;message="";updateClock();
        weather=new AmbientWeather(getContext(),(value,status)->{if(!active)return;snapshot=value;message=status;updateWeather();});
        weather.start();
    }
    void updateClock(){clock.setText(new SimpleDateFormat("HH:mm",Ui.locale()).format(new Date()));updateWeather();}
    private void updateWeather(){
        long now=System.currentTimeMillis();
        if(snapshot!=null&&snapshot.usable(now)){
            temperature.setText(snapshot.degrees());
            city.setText(snapshot.city+(snapshot.fresh(now)?"":" · "+Ui.tr("Últimos dados","Last update","Últimos datos")));
            setContentDescription(clock.getText()+" · "+snapshot.degrees()+" · "+description(snapshot.code)+" · "+city.getText());
            String name=AmbientWeather.icon(snapshot.code,snapshot.day);
            if(!name.equals(symbol)){
                try(InputStream in=getContext().getAssets().open("symbols/"+name+".png")){Drawable drawable=Drawable.createFromStream(in,null);icon.setImageDrawable(drawable);symbol=name;}
                catch(java.io.IOException ignored){icon.setImageDrawable(null);symbol="";}
            }
            icon.setVisibility(VISIBLE);
        }else{
            temperature.setText("—");icon.setVisibility(GONE);icon.setImageDrawable(null);symbol="";
            AmbientWeather.Place place=AmbientWeather.manual(getContext());
            boolean setup=message.contains("ubicación");
            String status=setup?Ui.tr("Configure o tempo em Ajustes","Set weather location in Settings","Configura el tiempo en Ajustes"):message.isEmpty()||message.startsWith("Consultando")?Ui.tr("Consultando…","Loading…","Consultando…"):Ui.tr("Tempo indisponível","Weather unavailable","Tiempo no disponible");
            city.setText(status+(place!=null&&!setup?" · "+place.name:""));
            setContentDescription(clock.getText()+" · "+city.getText());
        }
    }
    private static String description(int code){
        if(code==0)return Ui.tr("Céu limpo","Clear","Despejado");
        if(code==1)return Ui.tr("Poucas nuvens","Mostly clear","Poco nuboso");
        if(code==2)return Ui.tr("Sol e nuvens","Partly cloudy","Sol y nubes");
        if(code==3)return Ui.tr("Nublado","Cloudy","Nublado");
        if(code==45||code==48)return Ui.tr("Nevoeiro","Fog","Niebla");
        if(code>=51&&code<=57)return Ui.tr("Garoa","Drizzle","Llovizna");
        if(code>=61&&code<=67||code>=80&&code<=82)return Ui.tr("Chuva","Rain","Lluvia");
        if(code>=71&&code<=77||code==85||code==86)return Ui.tr("Neve","Snow","Nieve");
        if(code==95||code==96||code==99)return Ui.tr("Trovoada","Thunderstorm","Tormenta");
        return Ui.tr("Tempo local","Local weather","Tiempo local");
    }
    void stop(){
        active=false;if(weather!=null){weather.close();weather=null;}snapshot=null;icon.setImageDrawable(null);symbol="";
    }
}
