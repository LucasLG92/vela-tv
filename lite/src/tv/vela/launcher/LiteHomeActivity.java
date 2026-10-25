package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.TvContract;
import android.os.*;
import android.provider.Settings;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** A local television launcher: ports, its tuner and installed apps. No media or network runtime. */
public class LiteHomeActivity extends Activity {
    private static final int INPUTS=0,TV=1,APPS=2,SETTINGS=3,PROFILE=4,LICENSES=5,TV_SETTINGS=6,UPDATES=7,APPEARANCE=8;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<View> tiles=new ArrayList<>();
    private final List<ImageView> appImages=new ArrayList<>();
    private final List<Future<?>> jobs=new ArrayList<>();
    private final LruCache<String,Bitmap> appIcons=new LruCache<String,Bitmap>(512*1024){protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}};
    private final Runnable tick=new Runnable(){public void run(){if(!visible)return;clock.setText(android.text.format.DateFormat.getTimeFormat(LiteHomeActivity.this).format(new Date()));main.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    private final Runnable inputsChanged=()->{if(this.visible&&this.section==INPUTS)renderInputs(true);};
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(visible&&section==APPS)show(APPS,false);}};
    private FrameLayout windowRoot,shell,content,pager,rail,heroWell;private View stage,scrim,heroPlaceholder;private ImageView heroImage;private TextView heading,clock,footer,heroName,heroPort,heroOpen;private TvInputs.Entry featured;
    private LiteAntennaGuide guide;private LiteAntennaPlayer player;private LiteUpdateManager updates;private LiteSettingsView settingsView;private int settingsCategory;private final LiteUpdateManager.Listener updateListener=this::refreshUpdates;
    private final View[] navigation=new View[4];private final TextView[] navigationLabels=new TextView[4];private final ImageView[] navigationIcons=new ImageView[4];private boolean sidebarOpen;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener=(old,next)->{for(View current=next;current!=null;){if(current==this.content){setSidebar(false);break;}current=current.getParent() instanceof View?(View)current.getParent():null;}};
    private TvInputs.Source inputSource;private DeviceIcons devices;private ExecutorService worker;
    private List<LiteCatalog.App> apps=Collections.emptyList();private int section=INPUTS,inputPage,appPage,generation;
    private String selectedPort="";private TvInputs.Entry editing;private boolean visible,registered,consumeOkUp,rebuildingInputs;

    protected TvInputs.Source createInputsSource(){return new TvInputs.SystemSource(this);}
    protected LiteCatalog.Tuner readTuner(){return LiteCatalog.tuner(this);}
    protected boolean allowAutomaticChecks(){return true;}
    protected LiteAntennaData createAntennaData(){return new LiteAntennaData(this);}
    protected List<LiteCatalog.App> readApps(){return LiteCatalog.apps(this);}
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    private android.content.SharedPreferences preferences(){return getSharedPreferences("lite_home",0);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.init(this);updates=LiteUpdateManager.get(this);section=Math.max(0,Math.min(2,preferences().getInt("start_section",INPUTS)));inputSource=createInputsSource();selectedPort=preferences().getString("selected_port","");
        if(state!=null){section=state.getInt("section",INPUTS);inputPage=Math.max(0,state.getInt("input_page"));appPage=Math.max(0,state.getInt("app_page"));settingsCategory=state.getInt("settings_category",0);}
        buildShell();
    }
    @Override public void onResume(){super.onResume();visible=true;hideSystemBars();IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packagesChanged,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(packagesChanged,filter);registered=true;
        show(section==PROFILE?INPUTS:section,false);tick.run();updates.observe(updateListener);if(allowAutomaticChecks())updates.automaticCheck();if(updates.requested()&&getPackageManager().canRequestPackageInstalls()){show(UPDATES,false);updates.resumeInstall(this);}
    }
    @Override public void onPause(){visible=false;updates.forget(updateListener);main.removeCallbacksAndMessages(null);closeTelevision();inputSource.stop();releaseImages();content.removeAllViews();settingsView=null;tiles.clear();if(registered){unregisterReceiver(packagesChanged);registered=false;}super.onPause();}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);closeTelevision();inputSource.stop();releaseImages();if(shell!=null&&shell.getViewTreeObserver().isAlive())shell.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);super.onDestroy();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);appIcons.evictAll();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("section",section==PROFILE?INPUTS:section);out.putInt("input_page",inputPage);out.putInt("app_page",appPage);out.putInt("settings_category",settingsCategory);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasCategory(Intent.CATEGORY_HOME)){section=Math.max(0,Math.min(2,preferences().getInt("start_section",INPUTS)));if(visible)show(section,false);}}
    @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);buildShell();if(visible){show(section==PROFILE?INPUTS:section,false);main.removeCallbacks(tick);tick.run();}}
    private void hideSystemBars(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private void releaseImages(){generation++;for(Future<?> job:jobs)job.cancel(true);jobs.clear();if(worker!=null){worker.shutdownNow();worker=null;}for(ImageView image:appImages)image.setImageDrawable(null);appImages.clear();appIcons.evictAll();if(devices!=null){devices.close();devices=null;}apps=Collections.emptyList();}
    private void resetContent(){settingsView=null;closeTelevision();releaseImages();content.removeAllViews();pager.removeAllViews();tiles.clear();footer.setText("");}
    private void closeTelevision(){if(guide!=null){guide.close();guide=null;}if(player!=null){player.close();if(windowRoot!=null)windowRoot.removeView(player);player=null;}if(stage!=null)stage.setVisibility(View.VISIBLE);}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);view.setTextColor(color);view.setTypeface(Ui.medium);view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);view.setSingleLine();view.setEllipsize(android.text.TextUtils.TruncateAt.END);return view;}
    private void put(FrameLayout parent,View view,int x,int y,int width,int height){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;parent.addView(view,p);}
    private GradientDrawable plate(int color,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(radius);return drawable;}
    private TextView button(String label,Runnable run){TextView view=text(label,25,VelaTheme.WHITE);view.setGravity(Gravity.CENTER);view.setFocusable(true);view.setFocusableInTouchMode(true);view.setDefaultFocusHighlightEnabled(false);view.setBackground(plate(VelaTheme.CARD,28));view.setOnFocusChangeListener((v,focus)->{view.setBackground(plate(focus?VelaTheme.WHITE:VelaTheme.CARD,28));view.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);});view.setOnClickListener(v->run.run());return view;}
    private void buildShell(){
        if(shell!=null&&shell.getViewTreeObserver().isAlive())shell.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        closeTelevision();windowRoot=new FrameLayout(this);Stage scaled=new Stage();stage=scaled;shell=new FrameLayout(this);scaled.addView(shell);windowRoot.addView(scaled,new FrameLayout.LayoutParams(-1,-1));setContentView(windowRoot);
        rail=new FrameLayout(this);rail.setClipToOutline(true);rail.setBackground(plate(0xDD1D1E25,32));rail.setElevation(16);put(shell,rail,24,24,76,1032);sidebarOpen=false;
        View tint=new View(this);tint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x8C22242C,0xC8111319}));rail.addView(tint,new FrameLayout.LayoutParams(-1,-1));
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.vela_mark);mark.setContentDescription("Vela Lite");put(rail,mark,17,40,42,42);
        String[] labels={t("Entradas HDMI","HDMI inputs","Entradas HDMI"),t("Televisão","Television","Televisión"),t("Aplicativos","Apps","Aplicaciones"),t("Ajustes","Settings","Ajustes")};
        String[] symbols={"hdmi","live_tv","apps","settings"};
        for(int i=0;i<4;i++){final int destination=i;FrameLayout nav=new FrameLayout(this);nav.setId(View.generateViewId());nav.setFocusable(true);nav.setFocusableInTouchMode(true);nav.setDefaultFocusHighlightEnabled(false);nav.setContentDescription(labels[i]);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageDrawable(symbol(symbols[i]));put(nav,image,16,13,27,30);navigationIcons[i]=image;TextView label=text(labels[i],21,VelaTheme.WHITE);label.setAlpha(0);put(nav,label,64,0,206,56);navigationLabels[i]=label;put(rail,nav,8,i==3?954:138+i*66,60,56);navigation[i]=nav;nav.setOnFocusChangeListener((v,f)->{if(f)setSidebar(true);styleNavigation();});nav.setOnClickListener(v->show(destination,false));nav.setOnKeyListener((v,k,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN){if(k==KeyEvent.KEYCODE_DPAD_RIGHT){show(destination,false);return true;}if(k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_DPAD_UP){navigation[Math.max(0,Math.min(3,destination+(k==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)))].requestFocus();return true;}}return false;});}
        for(int i=0;i<4;i++){navigation[i].setNextFocusUpId(navigation[Math.max(0,i-1)].getId());navigation[i].setNextFocusDownId(navigation[Math.min(3,i+1)].getId());}
        heading=text("",32,VelaTheme.WHITE);heading.setTypeface(Ui.displaySemibold);put(shell,heading,190,56,1170,48);clock=text("",30,VelaTheme.WHITE);clock.setVisibility(preferences().getBoolean("show_clock",true)?View.VISIBLE:View.GONE);clock.setFontFeatureSettings("'tnum'");clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(shell,clock,1590,42,250,62);
        content=new FrameLayout(this);content.setClipChildren(false);content.setClipToPadding(false);put(shell,content,112,116,1790,940);
        pager=new FrameLayout(this);put(shell,pager,1448,978,392,62);
        footer=text("",20,VelaTheme.MUTED);put(shell,footer,188,988,1100,42);
        scrim=new View(this);scrim.setBackgroundColor(Color.BLACK);scrim.setAlpha(0);put(shell,scrim,0,0,1920,1080);rail.bringToFront();shell.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
    }
    private Drawable symbol(String name){try(InputStream stream=getAssets().open("symbols/"+name+".png")){Bitmap bitmap=BitmapFactory.decodeStream(stream);return bitmap==null?null:new BitmapDrawable(getResources(),bitmap);}catch(IOException unavailable){return null;}}
    private void setSidebar(boolean open){if(rail==null||sidebarOpen==open)return;sidebarOpen=open;FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)rail.getLayoutParams();bounds.width=open?300:76;rail.setLayoutParams(bounds);for(int i=0;i<4;i++){FrameLayout.LayoutParams row=(FrameLayout.LayoutParams)navigation[i].getLayoutParams();row.width=open?284:60;navigation[i].setLayoutParams(row);navigationLabels[i].setAlpha(open?1:0);}if(scrim!=null)scrim.setAlpha(open?.19f:0);}
    private void styleNavigation(){for(int i=0;i<4;i++){View nav=navigation[i];if(nav!=null){boolean focus=nav.hasFocus();nav.setBackground(focus?plate(VelaTheme.WHITE,28):(section==i||section>=LICENSES&&i==SETTINGS)?plate(0x25FFFFFF,28):null);navigationIcons[i].setColorFilter(focus?VelaTheme.BACKGROUND:0xFFDDDEE7);navigationLabels[i].setTextColor(focus?VelaTheme.BACKGROUND:VelaTheme.WHITE);}}}
    private void show(int destination,boolean keepNavigation){
        section=destination;inputSource.stop();main.removeCallbacks(inputsChanged);resetContent();setSidebar(false);styleNavigation();FrameLayout.LayoutParams footerBounds=(FrameLayout.LayoutParams)footer.getLayoutParams();footerBounds.leftMargin=188;footerBounds.topMargin=988;footerBounds.width=1100;footer.setLayoutParams(footerBounds);
        if(destination==INPUTS){heading.setText(t("Entradas HDMI","HDMI inputs","Entradas HDMI"));devices=new DeviceIcons(this);inputSource.start(()->{main.removeCallbacks(inputsChanged);main.postDelayed(inputsChanged,150);});renderInputs();}
        else if(destination==TV)renderTelevision();else if(destination==APPS)loadApps();else if(destination==SETTINGS||destination==LICENSES||destination==TV_SETTINGS||destination==UPDATES||destination==APPEARANCE)renderSettings(destination);
        if(keepNavigation&&destination<4)navigation[destination].requestFocus();
    }
    private void focusFirst(){if(!tiles.isEmpty())tiles.get(0).requestFocus();else navigation[Math.min(section,3)].requestFocus();}
    private void edgeNavigation(View view,int index,int columns){view.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_LEFT&&index%columns==0){navigation[Math.min(section,3)].requestFocus();return true;}return false;});}
    private void renderInputs(){renderInputs(false);}
    private void feature(TvInputs.Entry entry){
        featured=entry;heroName.setText(entry.title);heroPort.setText(entry.port+"  ·  "+TvInputs.status(entry.state));heroOpen.setText(t("Abrir ","Open ","Abrir ")+(entry.model.equals("other")?entry.port:entry.title)+"  →");
        if(devices!=null){heroWell.setBackground(devices.surface(entry.model,false,62));devices.load(entry.model,heroImage,true);heroPlaceholder.setVisibility(heroImage.getDrawable()==null?View.VISIBLE:View.GONE);}
    }
    private void renderInputs(boolean preserveNavigation){
        rebuildingInputs=true;final String restorePort=selectedPort;
        boolean navFocus=preserveNavigation&&navigation[INPUTS].hasFocus();if(devices==null)devices=new DeviceIcons(this);else devices.clearViews();content.removeAllViews();tiles.clear();pager.removeAllViews();
        List<TvInputs.Entry> entries=inputSource.read();
        if(entries.isEmpty()){
            put(content,text(t("Nenhuma entrada compartilhada pela TV","No inputs shared by this TV","La TV no comparte entradas HDMI"),36,VelaTheme.WHITE),64,110,1560,70);
            put(content,text(t("Instale o Vela Lite na televisão, não no aparelho HDMI.","Install Vela Lite on the television, rather than the HDMI box.","Instala Vela Lite en la televisión, en lugar del dispositivo HDMI."),25,VelaTheme.SECONDARY),64,205,1560,60);
            TextView settings=button(t("Ajustes da televisão","TV settings","Ajustes de la televisión"),()->openSettings(Settings.ACTION_SETTINGS));put(content,settings,64,320,570,76);tiles.add(settings);rebuildingInputs=false;focusFirst();return;
        }
        heroName=text("",60,VelaTheme.WHITE);put(content,heroName,60,40,900,92);heroPort=text("",24,VelaTheme.SECONDARY);put(content,heroPort,64,140,860,45);
        heroOpen=button("",()->{if(featured!=null)launchInput(featured);});heroOpen.setBackground(plate(VelaTheme.WHITE,36));heroOpen.setTextColor(VelaTheme.INK);heroOpen.setOnFocusChangeListener((v,f)->{heroOpen.setBackground(plate(VelaTheme.WHITE,36));heroOpen.setTextColor(VelaTheme.INK);heroOpen.setScaleX(f&&!preferences().getBoolean("reduce_motion",false)?1.035f:1);heroOpen.setScaleY(f&&!preferences().getBoolean("reduce_motion",false)?1.035f:1);});put(content,heroOpen,64,223,550,72);heroOpen.setOnLongClickListener(v->{consumeOkUp=true;if(featured!=null)chooseProfile(featured);return true;});
        heroWell=new FrameLayout(this);heroWell.setBackground(plate(VelaTheme.CARD,62));put(content,heroWell,1160,22,324,324);heroPlaceholder=new Glyph(0);put(heroWell,heroPlaceholder,58,58,208,208);heroImage=new ImageView(this);heroImage.setScaleType(ImageView.ScaleType.FIT_CENTER);put(heroWell,heroImage,58,58,208,208);
        put(content,text(t("Suas conexões","Your connections","Tus conexiones"),29,VelaTheme.WHITE),64,386,1000,54);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipChildren(false);scroll.setClipToPadding(false);scroll.setHorizontalFadingEdgeEnabled(true);scroll.setFadingEdgeLength(48);LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setClipChildren(false);strip.setClipToPadding(false);strip.setPadding(24,20,24,20);scroll.addView(strip);put(content,scroll,40,448,1704,334);
        View restored=null,connected=null;TvInputs.Entry initial=entries.get(0);
        for(int i=0;i<Math.min(32,entries.size());i++){
            TvInputs.Entry entry=entries.get(i);FrameLayout card=new FrameLayout(this);card.setFocusable(true);card.setFocusableInTouchMode(true);card.setDefaultFocusHighlightEnabled(false);card.setClipChildren(false);LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(232,294);position.rightMargin=28;strip.addView(card,position);tiles.add(card);FrameLayout well=new FrameLayout(this);put(card,well,8,0,216,216);well.setBackground(devices.surface(entry.model,false,37));ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);devices.load(entry.model,icon);
            if(icon.getDrawable()==null){Glyph generic=new Glyph(0);generic.setAlpha(entry.state==TvInputs.CONNECTED?1f:.42f);put(well,generic,43,43,130,130);}else{icon.setAlpha(entry.state==TvInputs.CONNECTED?1f:.42f);put(well,icon,43,43,130,130);}put(well,text(entry.port,15,VelaTheme.SECONDARY),16,12,150,25);
            TextView name=text(entry.title,21,VelaTheme.SECONDARY);put(card,name,4,230,224,30);put(card,text(TvInputs.status(entry.state),15,VelaTheme.MUTED),4,268,224,25);card.setContentDescription(entry.title+", "+entry.port+", "+TvInputs.status(entry.state));
            card.setOnFocusChangeListener((v,f)->{if(devices!=null)well.setBackground(devices.surface(entry.model,f,37));name.setTextColor(f?VelaTheme.WHITE:VelaTheme.SECONDARY);well.setScaleX(f&&!preferences().getBoolean("reduce_motion",false)?1.045f:1);well.setScaleY(f&&!preferences().getBoolean("reduce_motion",false)?1.045f:1);if(f&&section==INPUTS&&!rebuildingInputs){selectedPort=entry.key;preferences().edit().putString("selected_port",selectedPort).apply();feature(entry);}});
            card.setOnClickListener(v->launchInput(entry));card.setOnLongClickListener(v->{consumeOkUp=true;chooseProfile(entry);return true;});if(i==0)edgeNavigation(card,0,1);if(entry.key.equals(restorePort)){restored=card;initial=entry;}if(connected==null&&entry.state==TvInputs.CONNECTED){connected=card;if(restored==null)initial=entry;}
        }
        feature(initial);TextView settings=button(t("Ajustes da televisão","TV settings","Ajustes de la televisión"),()->openSettings(Settings.ACTION_SETTINGS));put(content,settings,64,806,420,58);
        FrameLayout.LayoutParams hint=(FrameLayout.LayoutParams)footer.getLayoutParams();hint.leftMargin=652;hint.topMargin=926;hint.width=1180;footer.setLayoutParams(hint);footer.setText(t("OK · Abrir     Manter OK · Identificar dispositivo","OK · Open     Hold OK · Identify device","OK · Abrir     Mantener OK · Identificar dispositivo"));
        rebuildingInputs=false;if(navFocus)navigation[INPUTS].requestFocus();else if(restored!=null)restored.requestFocus();else if(connected!=null)connected.requestFocus();else focusFirst();
    }
    private FrameLayout tile(String title,String subtitle,int slot,int columns,Runnable action){
        FrameLayout card=new FrameLayout(this);card.setFocusable(true);card.setFocusableInTouchMode(true);card.setDefaultFocusHighlightEnabled(false);card.setClipChildren(false);FrameLayout well=new FrameLayout(this);well.setBackground(plate(VelaTheme.CARD,42));put(card,well,24,0,288,288);
        TextView label=text(title,25,VelaTheme.WHITE);label.setGravity(Gravity.CENTER);put(card,label,0,302,336,42);TextView detail=text(subtitle,18,VelaTheme.SECONDARY);detail.setGravity(Gravity.CENTER);put(card,detail,0,349,336,32);
        put(content,card,slot%columns*410,slot/columns*390,336,382);card.setOnClickListener(v->action.run());card.setOnFocusChangeListener((v,f)->{GradientDrawable bg=plate(f?0xFF41424C:VelaTheme.CARD,42);if(f)bg.setStroke(2,VelaTheme.WHITE);well.setBackground(bg);});tiles.add(card);edgeNavigation(card,slot,columns);return card;
    }
    private void launchInput(TvInputs.Entry entry){launch(new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(entry.inputId)));}
    private void launch(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(this,t("A TV não permite abrir esta opção.","This option cannot be opened on this TV.","La TV no permite abrir esta opción."),Toast.LENGTH_SHORT).show();}}
    private void renderTelevision(){
        heading.setText(t("Televisão","Television","Televisión"));guide=new LiteAntennaGuide(this,createAntennaData(),new LiteAntennaGuide.Host(){public void navigation(){LiteHomeActivity.this.navigation[TV].requestFocus();}public void permission(){requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},41);}public void nativeTv(){openNativeTv(null);}public void play(LiveTvData.Channel ch,List<LiveTvData.Channel> channels){playAntenna(ch,channels);}public void longPress(){consumeOkUp=true;}});put(content,guide,0,0,1790,940);
    }
    private void openNativeTv(LiveTvData.Channel channel){if(channel!=null){launch(new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUri(channel.providerId)));return;}LiteCatalog.Tuner tuner=readTuner();if(tuner!=null)launch(tuner.intent);else openSettings(Settings.ACTION_SETTINGS);}
    private void playAntenna(LiveTvData.Channel channel,List<LiveTvData.Channel> channels){List<LiveTvData.Channel> copy=new ArrayList<>(channels);if(guide!=null){guide.close();guide=null;}content.removeAllViews();setSidebar(false);stage.setVisibility(View.GONE);player=new LiteAntennaPlayer(this,createAntennaData(),channel,copy,new LiteAntennaPlayer.Host(){public void exit(){show(TV,false);}public void nativeTv(LiveTvData.Channel ch){openNativeTv(ch);}});windowRoot.addView(player,new FrameLayout.LayoutParams(-1,-1));}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==41&&visible&&section==TV){if(guide!=null)guide.reload(true);else show(TV,false);}}
    private void loadApps(){
        heading.setText(t("Seus apps","Your apps","Tus apps"));put(content,text(t("Carregando aplicativos…","Loading apps…","Cargando aplicaciones…"),30,VelaTheme.SECONDARY),0,100,1300,70);worker=Executors.newSingleThreadExecutor();int token=generation;
        jobs.add(worker.submit(()->{List<LiteCatalog.App> found;try{found=readApps();}catch(RuntimeException unavailable){found=Collections.emptyList();}List<LiteCatalog.App> result=found;main.post(()->{if(!visible||section!=APPS||token!=generation)return;apps=result;renderAppsPage();});}));
    }
    private void renderAppsPage(){
        content.removeAllViews();pager.removeAllViews();tiles.clear();for(ImageView image:appImages)image.setImageDrawable(null);appImages.clear();appIcons.evictAll();for(Future<?> job:jobs)job.cancel(true);jobs.clear();generation++;int token=generation;
        put(content,text(t("Seus aplicativos","Your applications","Tus aplicaciones"),24,VelaTheme.SECONDARY),74,22,1240,48);TextView total=text(apps.size()+" apps",20,VelaTheme.SECONDARY);total.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(content,total,1420,22,270,48);
        int pages=Math.max(1,(apps.size()+11)/12);appPage=Math.min(appPage,pages-1);int start=appPage*12;
        for(int i=start;i<Math.min(start+12,apps.size());i++){
            LiteCatalog.App app=apps.get(i);int slot=i-start;FrameLayout card=new FrameLayout(this);card.setFocusable(true);card.setFocusableInTouchMode(true);card.setDefaultFocusHighlightEnabled(false);FrameLayout well=new FrameLayout(this);well.setBackground(plate(VelaTheme.CARD,43));well.setClipToOutline(true);put(card,well,36,10,192,192);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);put(well,image,0,0,192,192);appImages.add(image);
            TextView label=text(app.title,21,VelaTheme.WHITE);label.setGravity(Gravity.CENTER);put(card,label,0,220,264,32);put(content,card,38+slot%6*290,106+slot/6*278,264,264);card.setContentDescription(app.title);card.setOnClickListener(v->launch(app.intent()));card.setOnFocusChangeListener((v,f)->{GradientDrawable outline=plate(Color.TRANSPARENT,43);if(f)outline.setStroke(2,0xCCFFFFFF);well.setForeground(outline);well.setScaleX(f&&!preferences().getBoolean("reduce_motion",false)?1.07f:1);well.setScaleY(f&&!preferences().getBoolean("reduce_motion",false)?1.07f:1);well.setElevation(f?18:0);});tiles.add(card);edgeNavigation(card,slot,6);
            jobs.add(worker.submit(()->{Bitmap bitmap=appIcons.get(app.key);if(bitmap==null)try{Drawable drawable=getPackageManager().getActivityIcon(app.component);bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Path mask=new Path();mask.addRoundRect(new RectF(0,0,128,128),28.7f,28.7f,Path.Direction.CW);canvas.clipPath(mask);if(drawable instanceof AdaptiveIconDrawable){AdaptiveIconDrawable adaptive=(AdaptiveIconDrawable)drawable;for(Drawable layer:new Drawable[]{adaptive.getBackground(),adaptive.getForeground()})if(layer!=null){layer.setBounds(-32,-32,160,160);layer.draw(canvas);}}else{drawable.setBounds(0,0,128,128);drawable.draw(canvas);}}catch(PackageManager.NameNotFoundException|RuntimeException unavailable){}Bitmap result=bitmap;if(Thread.currentThread().isInterrupted())return;main.post(()->{if(visible&&section==APPS&&token==generation&&result!=null){appIcons.put(app.key,result);image.setImageBitmap(result);}});}));
        }
        if(apps.isEmpty())put(content,text(t("Nenhum aplicativo disponível","No apps available","No hay aplicaciones disponibles"),32,VelaTheme.SECONDARY),0,110,1500,60);
        if(pages>1)paging(appPage,pages,()->{appPage--;renderAppsPage();},()->{appPage++;renderAppsPage();});footer.setText(t("OK · Abrir aplicativo","OK · Open app","OK · Abrir aplicación"));focusFirst();
    }
    private void paging(int page,int pages,Runnable before,Runnable after){TextView prev=button("‹",before),next=button("›",after);prev.setEnabled(page>0);prev.setFocusable(page>0);prev.setAlpha(page>0?1f:.3f);next.setEnabled(page+1<pages);next.setFocusable(page+1<pages);next.setAlpha(page+1<pages?1f:.3f);put(pager,prev,0,0,82,62);TextView count=text((page+1)+" / "+pages,21,VelaTheme.SECONDARY);count.setGravity(Gravity.CENTER);put(pager,count,90,0,160,62);put(pager,next,268,0,82,62);}
    private void chooseProfile(TvInputs.Entry entry){
        inputSource.stop();main.removeCallbacks(inputsChanged);resetContent();section=PROFILE;editing=entry;heading.setText(entry.port+" · "+t("Identificar dispositivo","Identify device","Identificar dispositivo"));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);put(content,scroll,0,0,1600,770);String chosen=TvInputs.preset(this,entry.key);View focus=null;
        for(String profile:TvInputs.PRESETS){TextView item=button(TvInputs.name(profile)+(profile.equals(chosen)?"  ✓":""),()->{TvInputs.assign(this,entry.key,profile);selectedPort=entry.key;show(INPUTS,false);});item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(30,0,30,0);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,76);params.bottomMargin=12;list.addView(item,params);if(profile.equals(chosen))focus=item;}if(focus!=null)focus.requestFocus();footer.setText(t("O modelo só aparece com conexão ativa. Voltar · Cancelar","A device model is shown only with an active connection. Back · Cancel","El modelo solo aparece con conexión activa. Atrás · Cancelar"));
    }
    private void renderSettings(int destination){
        if(destination==TV_SETTINGS)settingsCategory=0;else if(destination==APPEARANCE)settingsCategory=1;else if(destination==UPDATES)settingsCategory=2;else if(destination==LICENSES)settingsCategory=4;section=SETTINGS;heading.setText(t("Ajustes","Settings","Ajustes"));styleNavigation();settingsView=new LiteSettingsView(this,updates,settingsCategory,new LiteSettingsView.Host(){public void navigation(){LiteHomeActivity.this.navigation[SETTINGS].requestFocus();}public void category(int page){settingsCategory=page;}public void homeSettings(){openSettings(Settings.ACTION_HOME_SETTINGS);}public void systemSettings(){openSettings(Settings.ACTION_SETTINGS);}public void antenna(){openNativeTv(null);}public void appearance(){clock.setVisibility(preferences().getBoolean("show_clock",true)?View.VISIBLE:View.GONE);stage.invalidate();}public void install(){updates.requestInstall(LiteHomeActivity.this);}});put(content,settingsView,0,0,1790,940);
    }
    boolean canShowInstaller(){return visible&&player==null;}
    private void refreshUpdates(){if(visible&&!updates.busy()&&updates.requested()&&getPackageManager().canRequestPackageInstalls())updates.resumeInstall(this);if(visible&&settingsView!=null)settingsView.refreshUpdates();}
    private void openSettings(String action){Intent intent=new Intent(action);if(intent.resolveActivity(getPackageManager())==null)intent=new Intent(Settings.ACTION_SETTINGS);launch(intent);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(consumeOkUp&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){if(event.getAction()==KeyEvent.ACTION_UP)consumeOkUp=false;return true;}if(player!=null&&player.remote(event))return true;return super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(player!=null){show(TV,false);return;}if(guide!=null&&guide.back())return;if(settingsView!=null&&settingsView.back())return;if(section==PROFILE)show(INPUTS,false);else if(section==LICENSES||section==TV_SETTINGS||section==UPDATES||section==APPEARANCE)show(SETTINGS,false);else if(!navigation[Math.min(section,3)].hasFocus())navigation[Math.min(section,3)].requestFocus();else if(section!=INPUTS)show(INPUTS,true);}

    private final class Stage extends FrameLayout {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Stage(){super(LiteHomeActivity.this);setWillNotDraw(false);setClipChildren(true);}
        protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);if(getChildCount()>0)getChildAt(0).measure(MeasureSpec.makeMeasureSpec(1920,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(1080,MeasureSpec.EXACTLY));}
        protected void onLayout(boolean changed,int l,int t,int r,int b){if(getChildCount()==0)return;View child=getChildAt(0);float scale=Math.min(getWidth()/1920f,getHeight()/1080f);child.setPivotX(0);child.setPivotY(0);child.setScaleX(scale);child.setScaleY(scale);int x=(int)((getWidth()-1920*scale)/2),y=(int)((getHeight()-1080*scale)/2);child.layout(x,y,x+1920,y+1080);}
        protected void onSizeChanged(int w,int h,int oldW,int oldH){paint.setShader(new RadialGradient(w*.3f,h*.3f,Math.min(w/1920f,h/1080f)*1680,new int[]{VelaTheme.PANEL,0xFF121318,VelaTheme.BACKGROUND},new float[]{0,.55f,1},Shader.TileMode.CLAMP));}
        protected void onDraw(Canvas canvas){if(preferences().getBoolean("black_background",false))canvas.drawColor(Color.BLACK);else canvas.drawRect(0,0,getWidth(),getHeight(),paint);}
    }
    private final class Glyph extends View {
        private final int kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rect=new RectF();
        Glyph(int kind){super(LiteHomeActivity.this);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){c.save();c.scale(getWidth()/100f,getHeight()/100f);paint.setColor(VelaTheme.WHITE);paint.setStrokeWidth(2);paint.setStyle(Paint.Style.STROKE);
            if(kind==2){paint.setStyle(Paint.Style.FILL);for(int x=0;x<3;x++)for(int y=0;y<3;y++){rect.set(12+x*29,12+y*29,31+x*29,31+y*29);c.drawRoundRect(rect,5,5,paint);}}
            else if(kind==3){c.drawCircle(50,50,26,paint);c.drawCircle(50,50,9,paint);for(int i=0;i<8;i++){double angle=i*Math.PI/4;c.drawLine(50+(float)Math.cos(angle)*27,50+(float)Math.sin(angle)*27,50+(float)Math.cos(angle)*40,50+(float)Math.sin(angle)*40,paint);}}
            else{rect.set(10,22,90,77);c.drawRoundRect(rect,8,8,paint);c.drawLine(50,78,50,89,paint);c.drawLine(32,90,68,90,paint);if(kind==1){c.drawLine(32,8,50,21,paint);c.drawLine(68,8,50,21,paint);}else{paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setStyle(Paint.Style.FILL);paint.setTextSize(13);paint.setTextAlign(Paint.Align.CENTER);c.drawText("HDMI",50,56,paint);}}c.restore();
        }
    }
}
