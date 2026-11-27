package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Event-driven HDMI cards; no network icons, background services or idle polling. */
final class InputsScreen extends VelaScreen {
    final boolean home;
    private List<LiteCatalog.App> apps;
    private final TvInputs.Source source;private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable refresh=()->{if(this.active&&!this.picking)render();};
    private DeviceIcons icons;
    private FrameLayout root;private final List<View> cards=new ArrayList<>();
    private VelaSpotlight inputLight;private FrameLayout heroWell;private ImageView heroIcon;private TextView heroName,heroPort,open;private TvInputs.Entry heroEntry;
    private VelaHdmiBrowser hdmi;
    private boolean active,picking,consumeConfirmUp;private String selected="";private int column;
    InputsScreen(Activity owner,Navigation navigation,TvInputs.Source source){this(owner,navigation,source,false);}
    InputsScreen(Activity owner,Navigation navigation,TvInputs.Source source,boolean home){super(owner,navigation);this.source=source;this.home=home;}
    protected void onCreate(Bundle state){if(state!=null)selected=state.getString("selected","");}
    protected void onResume(){active=true;icons=new DeviceIcons(this);source.start(()->{main.removeCallbacks(refresh);main.postDelayed(refresh,150);});render();}
    protected void onPause(){closePreview();active=false;apps=null;source.stop();if(icons!=null){icons.close();icons=null;}main.removeCallbacksAndMessages(null);}
    @Override void onTrimMemory(int level){if(icons!=null){icons.close();icons=null;}}
    protected void onDestroy(){onPause();cards.clear();if(root!=null)root.removeAllViews();}
    protected void onSaveInstanceState(Bundle out){out.putString("selected",selected);}
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTypeface(Ui.medium);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setIncludeFontPadding(false);v.setSingleLine();v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private void place(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private TextView action(String title,Runnable click){TextView v=text(title,24,VelaTheme.WHITE);v.setFocusable(true);v.setDefaultFocusHighlightEnabled(false);v.setGravity(Gravity.CENTER);v.setBackground(rounded(VelaTheme.CARD,VelaTheme.CONTROL_RADIUS));v.setOnFocusChangeListener((w,f)->{v.setBackground(rounded(f?Appearance.accent(this):VelaTheme.CARD,VelaTheme.CONTROL_RADIUS));v.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);});v.setOnClickListener(w->click.run());return v;}
    private void systemSettings(){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(RuntimeException unavailable){Toast.makeText(this,t("Ajustes indisponíveis","Settings unavailable","Ajustes no disponibles"),Toast.LENGTH_SHORT).show();}}
    private void launch(TvInputs.Entry entry){if(entry==null)return;try{startActivity(new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(entry.inputId)));}catch(RuntimeException unavailable){Toast.makeText(this,t("Entrada indisponível","Input unavailable","Entrada no disponible"),Toast.LENGTH_SHORT).show();}}
    private void feature(TvInputs.Entry entry){
        heroEntry=entry;if(icons!=null)inputLight.tint(icons.lightColor(entry.model));heroName.setText(entry.title);heroPort.setText(entry.port+"  ·  "+TvInputs.status(entry.state));
        boolean named=!entry.model.equals("other")&&entry.state==TvInputs.CONNECTED;
        open.setText(L10n.text("Open")+"  →");
        if(icons!=null)icons.load(entry.model,heroIcon,true);else heroIcon.setImageDrawable(null);
    }
    private void closePreview(){if(hdmi!=null){hdmi.close();hdmi=null;}}
    private void liveLayout(List<TvInputs.Entry> entries,TvInputs.Layout style,boolean navFocus){
        FrameLayout backdrop=navigation instanceof HomeScreenHost?(HomeScreenHost)navigation:null;
        hdmi=new VelaHdmiBrowser(this,style,entries,selected,icons,backdrop,new VelaHdmiBrowser.Actions(){
            public void selected(TvInputs.Entry e){selected=e.key;column=style==TvInputs.Layout.MONITOR?0:entries.indexOf(e);}
            public void open(TvInputs.Entry e){launch(e);}
            public void identify(TvInputs.Entry e){consumeConfirmUp=true;choose(e);}
        });
        place(root,hdmi,0,0,1790,940);cards.addAll(hdmi.rows);
        if(home){
            if(apps==null)apps=VelaStartApps.read(this);
            place(root,new VelaStartApps(this,apps,()->startActivity(new Intent(HomeScreenHost.APPS))),64,790,1654,70);
        }else{
            place(root,action(L10n.text("TV settings"),this::systemSettings),64,806,420,58);
            place(root,text(t("OK · Abrir     Manter OK · Identificar dispositivo","OK · Open     Hold OK · Identify device","OK · Abrir     Mantener OK · Identificar dispositivo"),20,VelaTheme.MUTED),540,810,1180,40);
        }
        setContentView(root);
        if(!navFocus)hdmi.initial.requestFocus();
    }
    private void render(){
        picking=false;boolean navFocus=navigation.navigationTarget(this)!=null&&navigation.navigationTarget(this).hasFocus();
        closePreview();if(icons!=null)icons.clearViews();else icons=new DeviceIcons(this);root=new FrameLayout(this);cards.clear();
        List<TvInputs.Entry> available=source.read();
        List<TvInputs.Entry> entries=available.subList(0,Math.min(32,available.size()));
        TvInputs.Layout layout=TvInputs.Layout.selected(this);
        if(!entries.isEmpty()&&layout!=TvInputs.Layout.CARDS){liveLayout(entries,layout,navFocus);return;}
        inputLight=new VelaSpotlight(this);inputLight.tint(0xff849792);place(root,inputLight,0,0,1790,490);heroName=text("",82,VelaTheme.WHITE);heroName.setSingleLine();heroName.setAutoSizeTextTypeUniformWithConfiguration(42,82,2,android.util.TypedValue.COMPLEX_UNIT_PX);heroName.setTypeface(Ui.displayMedium);place(root,heroName,82,85,940,116);heroPort=text("",24,VelaTheme.SECONDARY);place(root,heroPort,86,40,900,43);
        open=action("",()->launch(heroEntry));place(root,open,86,238,270,60);open.setOnLongClickListener(v->{if(heroEntry!=null){consumeConfirmUp=true;choose(heroEntry);}return true;});
        open.setOnFocusChangeListener((v,f)->{open.setBackground(rounded(f?VelaTheme.pearl(Appearance.accent(this)):VelaTheme.CARD,30));open.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);VelaFocus.card(open,f);});open.setBackground(rounded(VelaTheme.CARD,30));
        heroWell=new FrameLayout(this);place(root,heroWell,1050,10,520,360);heroIcon=new DeviceIcons.Artwork(this,"other");place(heroWell,heroIcon,70,16,380,328);
        if(!entries.isEmpty())place(root,text(t("Suas conexões","Your connections","Tus conexiones"),29,VelaTheme.WHITE),64,386,1000,54);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(false);LinearLayout strip=new LinearLayout(this);strip.setClipChildren(false);strip.setClipToPadding(false);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(24,20,24,20);scroll.addView(strip);place(root,scroll,40,448,1704,334);
        scroll.setOnScrollChangeListener((v,x,y,ox,oy)->{if(icons!=null)icons.refreshVisible();});
        View restore=null;TvInputs.Entry initial=entries.isEmpty()?null:entries.get(0);for(TvInputs.Entry e:entries)if(e.state==TvInputs.CONNECTED&&!e.model.equals("other")){initial=e;break;}
        for(int i=0;i<entries.size();i++){
            TvInputs.Entry entry=entries.get(i);FrameLayout card=new FrameLayout(this);card.setId(View.generateViewId());card.setFocusable(true);card.setFocusableInTouchMode(true);card.setDefaultFocusHighlightEnabled(false);card.setClipChildren(false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(232,294);cp.rightMargin=28;strip.addView(card,cp);cards.add(card);int index=i;
            FrameLayout well=new FrameLayout(this);well.setClipChildren(false);place(card,well,8,0,216,216);
            DeviceIcons.Artwork art=new DeviceIcons.Artwork(this,entry.model);place(well,art,43,43,130,130);if(icons!=null)icons.load(entry.model,art);
            TextView port=text(entry.port,15,VelaTheme.WHITE);VelaTheme.inputPort(port);place(well,port,16,12,100,25);
            TextView name=text(entry.title,21,VelaTheme.SECONDARY),status=text(TvInputs.status(entry.state),15,VelaTheme.MUTED);place(card,name,4,230,224,30);place(card,status,4,268,224,25);
            card.setContentDescription(entry.title+", "+entry.port+", "+TvInputs.status(entry.state));
            java.util.function.Consumer<Boolean> style=f->{well.setBackground(icons.surface(entry.model,f,37));name.setTextColor(f?VelaTheme.WHITE:VelaTheme.SECONDARY);art.setAlpha(entry.state==TvInputs.DISCONNECTED?.4f:entry.state==TvInputs.STANDBY?.6f:1f);VelaFocus.card(well,f);};style.accept(false);
            card.setOnFocusChangeListener((v,f)->{if(!active||icons==null)return;style.accept(f);if(f){selected=entry.key;column=index;feature(entry);}});card.setOnClickListener(v->launch(entry));card.setOnLongClickListener(v->{consumeConfirmUp=true;choose(entry);return true;});
            if(entry.key.equals(selected)){restore=card;initial=entry;}
        }
        if(initial!=null)feature(initial);else{heroEntry=null;heroName.setText(t("Suas entradas HDMI","Your HDMI inputs","Tus entradas HDMI"));heroPort.setText(t("Disponíveis quando a TV as compartilha","Available when shared by your TV","Disponibles cuando la TV las comparte"));open.setText(t("Ajustes da televisão","TV settings","Ajustes de la televisión"));open.setOnClickListener(v->systemSettings());}
        View appsStrip=null;
        if(home){
            if(apps==null)apps=VelaStartApps.read(this);
            appsStrip=new VelaStartApps(this,apps,()->startActivity(new Intent(HomeScreenHost.APPS)));
            place(root,appsStrip,64,entries.isEmpty()?470:790,1654,70);
        }else if(!entries.isEmpty()){
            TextView settings=action(t("Ajustes da televisão","TV settings","Ajustes de la televisión"),this::systemSettings);place(root,settings,64,806,420,58);
            place(root,text(t("OK · Abrir     Manter OK · Identificar dispositivo","OK · Open     Hold OK · Identify device","OK · Abrir     Mantener OK · Identificar dispositivo"),20,VelaTheme.MUTED),540,810,1180,40);
        }
        setContentView(root);if(!navFocus){if(restore!=null)restore.requestFocus();else if(!cards.isEmpty()){int preferred=0;for(int n=0;n<entries.size();n++)if(entries.get(n)==initial){preferred=n;break;}cards.get(preferred).requestFocus();}else if(appsStrip!=null)appsStrip.requestFocus();else open.requestFocus();}root.post(()->{if(active&&icons!=null)icons.refreshVisible();});
    }
    private void choose(TvInputs.Entry entry){
        closePreview();picking=true;if(icons!=null)icons.clearViews();root=new FrameLayout(this);cards.clear();place(root,text(entry.port+" · "+t("Escolha o dispositivo","Choose device","Elige el dispositivo"),38,VelaTheme.WHITE),64,24,1600,70);
        place(root,text(t("Automático usa o nome que a TV compartilha.","Automatic uses the name shared by the TV.","Automático usa el nombre que comparte la televisión."),23,VelaTheme.SECONDARY),64,104,1600,48);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);place(root,scroll,64,180,1640,710);String current=TvInputs.preset(this,entry.key);View focus=null;
        String previous="";for(String model:TvInputs.PRESETS){String group=DeviceProfiles.group(model);if(!group.isEmpty()&&!group.equals(previous)){TextView section=text(group,22,VelaTheme.MUTED);section.setPadding(20,0,0,0);list.addView(section,new LinearLayout.LayoutParams(-1,56));previous=group;}TextView choice=action(TvInputs.name(model)+(model.equals(current)?"  ✓":""),()->{TvInputs.assign(this,entry.key,model);selected=entry.key;render();});choice.setGravity(Gravity.CENTER_VERTICAL);choice.setPadding(28,0,28,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,76);p.bottomMargin=12;list.addView(choice,p);if(model.equals(current))focus=choice;}
        setContentView(root);if(focus!=null)focus.requestFocus();
    }
    @Override boolean standaloneChrome(){return false;}
    @Override boolean navigationLeft(){return !picking&&(!cards.contains(getCurrentFocus())||column==0);}
    public boolean dispatchKeyEvent(KeyEvent event){if(consumeConfirmUp&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){if(event.getAction()==KeyEvent.ACTION_UP)consumeConfirmUp=false;return true;}return false;}
    public void onBackPressed(){if(picking)render();else super.onBackPressed();}


}
