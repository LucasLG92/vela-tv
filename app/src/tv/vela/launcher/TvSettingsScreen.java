package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.concurrent.*;

/** Source settings reuse the existing encrypted clients; no channel browser or player is allocated here. */
final class TvSettingsScreen extends VelaScreen {
    private FrameLayout stage,pane;
    private EditText address,epg;
    private TextView status;
    private int page,epoch;
    private boolean active,busy;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor io=Visuals.worker(1);
    private Future<?> job;
    private final M3uClient m3u;
    TvSettingsScreen(Activity owner,Navigation navigation){super(owner,navigation);m3u=new M3uClient(this);}
    protected void onCreate(Bundle state){
        FrameLayout root=new FrameLayout(this){protected void onSizeChanged(int w,int h,int ow,int oh){float scale=Math.min(w/1920f,h/1080f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(scale);stage.setScaleY(scale);stage.setTranslationX((w-1920*scale)/2);stage.setTranslationY((h-1080*scale)/2);}};
        stage=new FrameLayout(this);root.addView(stage,new FrameLayout.LayoutParams(1920,1080));
        put(stage,text("VELA",24),116,66,650,42);put(stage,text(t("Sua televisão","Your television","Tu televisión"),58),112,132,750,90);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.vela_mark);mark.setColorFilter(VelaTheme.WHITE);put(stage,mark,220,318,184,184);
        put(stage,text(t("Suas fontes.\nSeus canais.","Your sources.\nYour channels.","Tus fuentes.\nTus canales."),43),116,570,650,140);
        TextView help=text(t("Listas M3U, conta Xtream, antena e guia. A conexão fica cifrada neste dispositivo.","M3U playlists, Xtream, antenna and guide. Connections stay encrypted on this device.","Listas M3U, cuenta Xtream, antena y guía. Las conexiones se guardan cifradas en este dispositivo."),24);help.setMaxLines(5);help.setGravity(Gravity.TOP);put(stage,help,116,742,630,180);
        pane=new FrameLayout(this);put(stage,pane,936,140,840,800);status=text("",22);status.setMaxLines(3);put(stage,status,936,948,840,100);setContentView(root);
    }
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTypeface(Ui.medium);v.setTextColor(0xFFF7F7FA);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(20);return d;}
    private TextView button(String label,int y,Runnable action){TextView row=text(label,27);row.setId(View.generateViewId());row.setFocusable(true);row.setFocusableInTouchMode(true);row.setDefaultFocusHighlightEnabled(false);row.setPadding(26,0,26,0);row.setSingleLine();row.setBackground(round(VelaTheme.CARD));put(pane,row,0,y,840,76);row.setOnFocusChangeListener((v,f)->{row.setBackground(round(f?Appearance.accent(this):VelaTheme.CARD));row.setTextColor(f?VelaTheme.INK:0xFFF7F7FA);});row.setOnClickListener(v->{if(!busy)action.run();});return row;}
    private EditText input(String hint,int y){EditText v=new EditText(this);v.setId(View.generateViewId());v.setHint(hint);v.setTextColor(0xFFF7F7FA);v.setHintTextColor(0xFFA5AFBF);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24);v.setSingleLine();v.setPadding(24,0,24,0);v.setBackground(round(VelaTheme.CARD));v.setSaveEnabled(false);v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);v.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_ACTION_NEXT);v.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8192)});put(pane,v,0,y,840,82);return v;}
    private void clearFields(){if(address!=null)address.setText("");if(epg!=null)epg.setText("");address=null;epg=null;}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pane.getWindowToken(),0);}
    private void render(int target){
        clearFields();page=target;pane.removeAllViews();status.setText("");sensitive(page!=0);
        if(page==0){
            button(t("Configurar lista M3U","Configure M3U playlist","Configurar lista M3U"),0,()->render(1)).requestFocus();
            button(t("Conta Xtream","Xtream account","Cuenta Xtream"),96,()->startActivity(new Intent(this,XtreamLinkActivity.class)));
            button(t("Guia de programação","Programme guide","Guía de programación"),192,()->render(2));
            button(t("Ajustes de antena","Antenna settings","Ajustes de antena"),288,()->{Intent setup=new AntennaClient(this).setup();if(setup==null)status.setText(t("Sintonizador indisponível","Tuner unavailable","Sintonizador no disponible"));else try{startActivity(setup);}catch(RuntimeException unavailable){status.setText(t("Ajustes indisponíveis","Settings unavailable","Ajustes no disponibles"));}});
            button(t("Atualizar canais","Refresh channels","Actualizar canales"),384,this::refresh);
            button(t("Voltar","Back","Volver"),520,this::finish);
        }else if(page==1){
            address=input(t("URL da lista M3U","M3U playlist URL","URL de la lista M3U"),0);epg=input(t("XMLTV opcional","Optional XMLTV","XMLTV opcional"),116);try{address.setText(m3u.url());epg.setText(m3u.epgUrl());}catch(Exception ignored){}
            boolean[] http={m3u.allowHttp()};TextView allow=button((http[0]?"✓ ":"○ ")+t("Minha lista usa HTTP","My playlist uses HTTP","Mi lista o sus señales usan HTTP"),248,()->{});allow.setOnClickListener(v->{http[0]=!http[0];allow.setText((http[0]?"✓ ":"○ ")+t("Minha lista usa HTTP","My playlist uses HTTP","Mi lista o sus señales usan HTTP"));});
            button(t("Guardar e usar M3U","Save and use M3U","Guardar y usar M3U"),366,()->{try{m3u.configure(address.getText().toString(),epg.getText().toString(),http[0]);hideKeyboard();render(0);status.setText(t("Lista guardada.","Playlist saved.","Lista guardada."));}catch(Exception invalid){status.setText(t("Confira as URLs e a opção HTTP.","Check the URLs and HTTP option.","Revisa las URL y la opción HTTP."));}});
            button(t("Voltar","Back","Volver"),484,()->{hideKeyboard();render(0);});address.requestFocus();
        }else{
            epg=input("XMLTV · HTTPS",0);try{epg.setText(new XmlTvGuide(this).url());}catch(Exception ignored){}
            button(t("Guardar guia","Save guide","Guardar guía"),128,()->{try{new XmlTvGuide(this).configure(epg.getText().toString());hideKeyboard();render(0);}catch(Exception invalid){status.setText("XMLTV · HTTPS");}});
            button(t("Atualizar guia","Refresh guide","Actualizar guía"),236,()->{new XmlTvGuide(this).invalidate();render(0);status.setText(t("Será atualizada ao abrir TV.","Refreshes when you open TV.","Se actualizará al abrir TV."));});
            button("EPG dobleM",344,()->configureGuide(XmlTvGuide.DEFAULT_URL));button(t("Usar apenas a guia da fonte","Use only the source guide","Usar solo la guía de la fuente"),452,()->configureGuide(""));button(t("Voltar","Back","Volver"),560,()->{hideKeyboard();render(0);});epg.requestFocus();
        }
    }
    private void configureGuide(String url){try{new XmlTvGuide(this).configure(url);hideKeyboard();render(0);}catch(Exception invalid){status.setText("XMLTV · HTTPS");}}
    private void refresh(){busy=true;int generation=++epoch;status.setText(t("Atualizando…","Refreshing…","Actualizando…"));job=io.submit(()->{boolean ok=true;try{String mode=LiveTvData.mode(this);if(mode.equals("m3u"))m3u.refresh(true);else if(mode.equals("xtream")){XtreamClient client=new XtreamClient(this);client.categories(true);client.page("*","",0,"",true);}else new AntennaClient(this).page("",null,0);}catch(Exception failure){ok=false;}boolean success=ok;main.post(()->{if(!active||generation!=epoch)return;busy=false;status.setText(success?t("Canales actualizados.","Channels refreshed.","Canales actualizados."):t("Não foi possível atualizar. A lista anterior é preservada.","Could not refresh. The previous list is preserved.","No se ha podido actualizar. Se conserva la lista anterior."));});});}
    protected void onResume(){active=true;busy=false;render(0);}
    protected void onPause(){active=false;busy=false;epoch++;if(job!=null)job.cancel(true);clearFields();}
    protected void onDestroy(){io.shutdownNow();main.removeCallbacksAndMessages(null);}
    public void onBackPressed(){if(page!=0){hideKeyboard();render(0);}else super.onBackPressed();}
}
