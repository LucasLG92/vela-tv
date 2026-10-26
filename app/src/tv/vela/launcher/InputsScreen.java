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
    private final TvInputs.Source source;private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable refresh=()->{if(this.active&&!this.picking)render();};
    private DeviceIcons icons;
    private FrameLayout root;private final List<View> cards=new ArrayList<>();
    private FrameLayout heroWell;private ImageView heroIcon;private TextView heroName,heroPort,open;private TvInputs.Entry heroEntry;
    private boolean active,picking,consumeConfirmUp;private String selected="";private int column;
    InputsScreen(Activity owner,Navigation navigation,TvInputs.Source source){super(owner,navigation);this.source=source;}
    protected void onCreate(Bundle state){if(state!=null)selected=state.getString("selected","");}
    protected void onResume(){active=true;icons=new DeviceIcons(this);source.start(()->{main.removeCallbacks(refresh);main.postDelayed(refresh,150);});render();}
    protected void onPause(){active=false;source.stop();if(icons!=null){icons.close();icons=null;}main.removeCallbacksAndMessages(null);}
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
        heroEntry=entry;if(icons!=null)heroWell.setBackground(icons.surface(entry.model,false,62));heroName.setText(entry.title);heroPort.setText(entry.port+"  ·  "+TvInputs.status(entry.state));
        boolean named=!entry.model.equals("other")&&entry.state==TvInputs.CONNECTED;
        open.setText(L10n.text("Open")+"  →");
        if(icons!=null)icons.load(entry.model,heroIcon,true);else heroIcon.setImageDrawable(null);
    }
    private void render(){
        picking=false;boolean navFocus=navigation.navigationTarget(this)!=null&&navigation.navigationTarget(this).hasFocus();
        if(icons!=null)icons.clearViews();root=new FrameLayout(this);cards.clear();

        heroName=text("",56,VelaTheme.WHITE);place(root,heroName,78,40,900,92);heroPort=text("",24,VelaTheme.SECONDARY);place(root,heroPort,80,140,860,45);
        open=action("",()->launch(heroEntry));place(root,open,80,223,360,62);open.setOnLongClickListener(v->{if(heroEntry!=null){consumeConfirmUp=true;choose(heroEntry);}return true;});
        open.setBackground(rounded(Appearance.accent(this),VelaTheme.CONTROL_RADIUS));open.setTextColor(VelaTheme.INK);open.setOnFocusChangeListener((v,f)->{open.setBackground(rounded(Appearance.accent(this),VelaTheme.CONTROL_RADIUS));open.animate().cancel();open.animate().scaleX(f?1.035f:1).scaleY(f?1.035f:1).setDuration(Visuals.duration(this,150)).start();});
        heroWell=new FrameLayout(this);GradientDrawable heroSurface=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF363842,0xFF17181E});heroSurface.setCornerRadius(62);heroSurface.setStroke(1,0x20FFFFFF);heroWell.setBackground(heroSurface);place(root,heroWell,1160,22,324,324);heroIcon=new InputArtwork(this,"other");place(heroWell,heroIcon,58,58,208,208);
        place(root,text(t("Suas conexões","Your connections","Tus conexiones"),29,VelaTheme.WHITE),64,386,1000,54);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(false);LinearLayout strip=new LinearLayout(this);strip.setClipChildren(false);strip.setClipToPadding(false);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(24,20,24,20);scroll.addView(strip);place(root,scroll,40,448,1704,334);
        scroll.setOnScrollChangeListener((v,x,y,ox,oy)->{if(icons!=null)icons.refreshVisible();});
        List<TvInputs.Entry> entries=source.read();View restore=null;TvInputs.Entry initial=entries.isEmpty()?null:entries.get(0);for(TvInputs.Entry e:entries)if(e.state==TvInputs.CONNECTED&&!e.model.equals("other")){initial=e;break;}
        for(int i=0;i<Math.min(32,entries.size());i++){
            TvInputs.Entry entry=entries.get(i);FrameLayout card=new FrameLayout(this);card.setId(View.generateViewId());card.setFocusable(true);card.setFocusableInTouchMode(true);card.setDefaultFocusHighlightEnabled(false);card.setClipChildren(false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(232,294);cp.rightMargin=28;strip.addView(card,cp);cards.add(card);int index=i;
            FrameLayout well=new FrameLayout(this);well.setClipChildren(false);place(card,well,8,0,216,216);
            InputArtwork art=new InputArtwork(this,entry.model);place(well,art,43,43,130,130);if(icons!=null)icons.load(entry.model,art);
            TextView port=text(entry.port,15,VelaTheme.SECONDARY);place(well,port,16,12,100,25);
            TextView name=text(entry.title,21,VelaTheme.SECONDARY),status=text(TvInputs.status(entry.state),15,VelaTheme.MUTED);place(card,name,4,230,224,30);place(card,status,4,268,224,25);
            card.setContentDescription(entry.title+", "+entry.port+", "+TvInputs.status(entry.state));
            java.util.function.Consumer<Boolean> style=f->{well.setBackground(icons.surface(entry.model,f,37));name.setTextColor(f?VelaTheme.WHITE:VelaTheme.SECONDARY);art.setAlpha(entry.state==TvInputs.DISCONNECTED?.4f:entry.state==TvInputs.STANDBY?.6f:1f);well.animate().cancel();well.animate().scaleX(f?1.045f:1).scaleY(f?1.045f:1).translationY(f?-3:0).setDuration(Visuals.duration(this,150)).start();};style.accept(false);
            card.setOnFocusChangeListener((v,f)->{style.accept(f);if(f){selected=entry.key;column=index;feature(entry);}});card.setOnClickListener(v->launch(entry));card.setOnLongClickListener(v->{consumeConfirmUp=true;choose(entry);return true;});
            if(entry.key.equals(selected)){restore=card;initial=entry;}
        }
        if(initial!=null)feature(initial);else{heroName.setText(t("Suas entradas HDMI","Your HDMI inputs","Tus entradas HDMI"));heroPort.setText(t("Disponíveis quando a TV as compartilha","Available when shared by your TV","Disponibles cuando la TV las comparte"));open.setText(t("Ajustes da televisão","TV settings","Ajustes de la televisión"));open.setOnClickListener(v->systemSettings());}
        TextView settings=action(t("Ajustes da televisão","TV settings","Ajustes de la televisión"),this::systemSettings);place(root,settings,64,806,420,58);
        place(root,text(t("OK · Abrir     Manter OK · Identificar dispositivo","OK · Open     Hold OK · Identify device","OK · Abrir     Mantener OK · Identificar dispositivo"),20,VelaTheme.MUTED),540,810,1180,40);
        setContentView(root);if(!navFocus){if(restore!=null)restore.requestFocus();else if(!cards.isEmpty()){int preferred=0;for(int n=0;n<entries.size();n++)if(entries.get(n)==initial){preferred=n;break;}cards.get(preferred).requestFocus();}else open.requestFocus();}root.post(()->{if(active&&icons!=null)icons.refreshVisible();});
    }
    private void choose(TvInputs.Entry entry){
        picking=true;if(icons!=null)icons.clearViews();root=new FrameLayout(this);cards.clear();place(root,text(entry.port+" · "+t("Escolha o dispositivo","Choose device","Elige el dispositivo"),38,VelaTheme.WHITE),64,24,1600,70);
        place(root,text(t("Automático usa o nome que a TV compartilha.","Automatic uses the name shared by the TV.","Automático usa el nombre que comparte la televisión."),23,VelaTheme.SECONDARY),64,104,1600,48);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);place(root,scroll,64,180,1640,710);String current=TvInputs.preset(this,entry.key);View focus=null;
        String previous="";for(String model:TvInputs.PRESETS){String group=DeviceProfiles.group(model);if(!group.isEmpty()&&!group.equals(previous)){TextView section=text(group,22,VelaTheme.MUTED);section.setPadding(20,0,0,0);list.addView(section,new LinearLayout.LayoutParams(-1,56));previous=group;}TextView choice=action(TvInputs.name(model)+(model.equals(current)?"  ✓":""),()->{TvInputs.assign(this,entry.key,model);selected=entry.key;render();});choice.setGravity(Gravity.CENTER_VERTICAL);choice.setPadding(28,0,28,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,76);p.bottomMargin=12;list.addView(choice,p);if(model.equals(current))focus=choice;}
        setContentView(root);if(focus!=null)focus.requestFocus();
    }
    @Override boolean standaloneChrome(){return false;}
    @Override boolean navigationLeft(){return !picking&&(!cards.contains(getCurrentFocus())||column==0);}
    public boolean dispatchKeyEvent(KeyEvent event){if(consumeConfirmUp&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){if(event.getAction()==KeyEvent.ACTION_UP)consumeConfirmUp=false;return true;}return false;}
    public void onBackPressed(){if(picking)render();else super.onBackPressed();}

    /** A neutral port placeholder; recognized active devices use local brand vectors. */
    static final class InputArtwork extends ImageView {
        private final Paint paint=new Paint(3);private final RectF box=new RectF();
        InputArtwork(Context c,String model){super(c);setTag(model);setScaleType(ScaleType.FIT_CENTER);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){if(getDrawable()!=null){super.onDraw(c);return;}c.save();float scale=Math.min(getWidth()/150f,getHeight()/100f);c.translate((getWidth()-150*scale)/2,(getHeight()-100*scale)/2);c.scale(scale,scale);paint.setColor(VelaTheme.SECONDARY);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);String model=String.valueOf(getTag());
            if(model.equals("soundbar")||model.equals("receiver")){box.set(15,32,135,72);c.drawRoundRect(box,12,12,paint);c.drawCircle(40,52,11,paint);c.drawCircle(110,52,11,paint);c.drawLine(67,46,83,46,paint);c.drawLine(67,58,83,58,paint);}else if(model.equals("dvd")||model.equals("bluray")){c.drawCircle(75,50,38,paint);c.drawCircle(75,50,10,paint);}else{box.set(17,9,133,81);c.drawRoundRect(box,10,10,paint);c.drawLine(75,82,75,94,paint);c.drawLine(53,94,97,94,paint);paint.setStyle(Paint.Style.FILL);paint.setTypeface(Ui.medium);paint.setTextSize(model.equals("pc")?26:20);c.drawText(model.equals("pc")?"PC":"HDMI",model.equals("pc")?58:47,53,paint);}c.restore();}
    }
}
