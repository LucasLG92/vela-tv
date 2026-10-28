package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Native TV settings. Focus animates briefly; an idle page schedules no frames or polling. */
class SettingsScreen extends VelaScreen {
    SettingsScreen(Activity owner, Navigation navigation){super(owner,navigation);}
    @Override boolean standaloneChrome(){return false;}
    private SettingsView view;
    private AmbientView preview;
    private View previewReturn;
    private boolean returnToSetup,returnToCaller;
    private int entryPage;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);returnToCaller=getIntent().getBooleanExtra("returnToCaller",false);
        if(!getIntent().getBooleanExtra("fromSetup",false)&&Personalization.launchIfNeeded(owner)){finish();return;}
        if(HomeMode.state(this).optBoolean("switchPending"))HomeMode.verify(this);
        view=new SettingsView(this);setContentView(view);if(state!=null){returnToSetup=state.getBoolean("returnToSetup");entryPage=state.getInt("entryPage");view.updateState=state.getBundle("updateState");view.render(state.getInt("settingsPage",22));view.returnRow=state.getInt("settingsReturnRow",0);view.savedRow=state.getInt("settingsFocusedRow",-1);}
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawable(null);
    }
    @Override protected void onResume() {
        super.onResume();if(view==null)return;if(getIntent().getIntExtra("page",-1)==23){getIntent().removeExtra("page");view.render(23);}if(!view.showingUpdates()&&Updates.resumeInstallation(owner))return;view.resume();
        if(getIntent().hasExtra("page")){
            int target=getIntent().getIntExtra("page",0);getIntent().removeExtra("page");
            returnToSetup=getIntent().getBooleanExtra("fromSetup",false);getIntent().removeExtra("fromSetup");
            if(target==1||target==3||target==4||target==9||target==11||target==14||target==15||target==17||target==21||target==22||target==23||target==25){entryPage=target;view.render(target);}
        }
        else if(view.page==14||view.page==15||view.page==21||view.page==22)view.render(view.page);
        if(getIntent().getBooleanExtra("plex",false)){getIntent().removeExtra("plex");startActivity(new Intent(this,PlexLinkActivity.class));}
        if(preview==null&&getIntent().getBooleanExtra("screensaver",false)){getIntent().removeExtra("screensaver");showPreview();}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("returnToSetup",returnToSetup);out.putInt("entryPage",entryPage);out.putInt("settingsPage",view==null?0:view.page);out.putInt("settingsReturnRow",view==null?0:view.returnRow);if(view!=null)out.putInt("settingsFocusedRow",view.rows.indexOf(getCurrentFocus()));if(view!=null&&view.updates!=null)out.putBundle("updateState",view.updates.save());}
    @Override protected void onPause(){closePreview();if(view!=null)view.pause();super.onPause();}
    @Override protected void onDestroy(){if(view!=null)view.destroy();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(preview!=null){
            int key=e.getKeyCode();
            if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||
                key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(e.getAction()==KeyEvent.ACTION_UP)closePreview();return true;
            }
        }
        if(view!=null&&view.onKey(e))return true;
        return super.dispatchKeyEvent(e);
    }
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] results){super.onRequestPermissionsResult(request,p,results);if(request==80)view.render(3);if(request==81)view.locationPermission(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(view!=null&&view.updates!=null)view.updates.onActivityResult(request,result,data);}
    @Override boolean navigationLeft(){return false;}
    void focusContent(){if(view!=null){if(view.savedRow>=0&&view.savedRow<view.rows.size())view.rows.get(view.savedRow).requestFocus();else if(view.showingUpdates())view.focusOptions();else view.categories[view.categoryFor(view.page)].requestFocus();}}
    boolean showingUpdates(){return view!=null&&view.showingUpdates();}
    boolean updatesActive(){return showingUpdates()&&view.updates.canShowInstaller();}
    void showPreview(){previewReturn=getCurrentFocus();fullPreview(true);view.pause();preview=new AmbientView(owner,true);setContentView(preview);preview.start();}
    private void closePreview(){if(preview==null)return;preview.stop();preview=null;fullPreview(false);setContentView(view);view.resume();if(previewReturn!=null&&previewReturn.isShown())previewReturn.requestFocus();previewReturn=null;}

    static final class SettingsView extends FrameLayout {
        private static final int WHITE=0xFFF7F7FA,DIM=0xFFA5A9B8,INK=VelaTheme.INK;
        private final SettingsScreen activity;
        private final FrameLayout stage,pane,surface;
        private ScrollView scroll;private int contentY;
        private final View[] categories=new View[8];private final TextView[] categoryText=new TextView[8];private final CategoryIcon[] categoryIcons=new CategoryIcon[8];
        private final int[] destinations={22,17,6,14,11,23,0,4};
        private final String[] categoryTitles={Ui.tr("Televisão","Television","Televisión"),Ui.tr("Aspecto","Appearance","Aspecto"),Ui.tr("Protetor de tela","Screensaver","Salvapantallas"),Ui.tr("Contas","Accounts","Cuentas"),Ui.tr("Menu","Menu","Menú"),Ui.tr("Atualizações","Updates","Actualizaciones"),Ui.tr("Sistema","System","Sistema"),Ui.tr("Informações","Information","Información")};
        private boolean building=true,categoryBrowsing;private UpdatesScreen updates;private Bundle updateState;
        private FrameLayout rowHost;
        private final TextView heading,detail,footer,status;
        private final Handler main=new Handler(Looper.getMainLooper());
        private final ExecutorService io=Visuals.worker(1);
        private final List<TextView> labels=new ArrayList<>(),values=new ArrayList<>();
        private final List<FrameLayout> rows=new ArrayList<>();
        private JSONObject state=new JSONObject();
        private boolean active,closed,reading,observedBusy;
        private long pendingAt;
        private int page,returnRow,searchGeneration,savedRow=-1;private final int[] focusMemory=new int[26];
        private int shortcutSlot,pickerFocus;
        private EditText cityInput;
        private String cityQuery="";
        private List<AmbientWeather.Place> cityResults=Collections.emptyList();
        private java.util.concurrent.Future<?> searchJob;
        private final Runnable poll=()->readState();
        SettingsView(SettingsScreen a){
            super(a);activity=a;setClipChildren(false);
            if(!activity.navigation.embedded())setBackgroundColor(VelaTheme.BACKGROUND);
            stage=new FrameLayout(a);stage.setClipChildren(false);addView(stage,new LayoutParams(1790,940));
            ImageView mark=new ImageView(a);mark.setImageResource(R.drawable.vela_mark);mark.setContentDescription("Vela");mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);put(stage,mark,86,24,52,52);
            put(stage,text("Vela",28,WHITE,Ui.semibold),156,24,308,52);
            put(stage,text(VelaWorkspace.profiles(a).get(VelaWorkspace.active(a)),18,VelaTheme.SECONDARY,Ui.medium),86,84,378,34);
            surface=new FrameLayout(a);surface.setBackground(VelaTheme.panel());put(stage,surface,520,12,1218,904);
            heading=text("",36,WHITE,Ui.displaySemibold);put(surface,heading,42,30,1134,62);
            detail=text("",21,VelaTheme.SECONDARY,Ui.regular);detail.setMaxLines(2);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.setGravity(Gravity.TOP);put(surface,detail,42,106,1134,66);
            pane=new FrameLayout(a);pane.setClipChildren(true);put(surface,pane,42,194,1134,622);
            status=text("",18,VelaTheme.SECONDARY,Ui.regular);status.setMaxLines(2);put(surface,status,48,826,1118,64);
            for(int i=0;i<categories.length;i++){
                final int index=i;FrameLayout row=new FrameLayout(a);row.setId(View.generateViewId());row.setFocusable(true);row.setFocusableInTouchMode(true);row.setDefaultFocusHighlightEnabled(false);row.setContentDescription(categoryTitles[i]);
                CategoryIcon icon=new CategoryIcon(a,i);categoryIcons[i]=icon;put(row,icon,22,18,28,28);TextView label=text(categoryTitles[i],23,WHITE,Ui.medium);categoryText[i]=label;put(row,label,72,0,288,64);put(stage,row,80,170+i*76,388,64);categories[i]=row;
                row.setOnFocusChangeListener((v,focus)->{if(!building&&focus&&categoryFor(page)!=index){categoryBrowsing=true;try{render(destinations[index]);}finally{categoryBrowsing=false;}}skin();});
                row.setOnClickListener(v->{render(destinations[index]);focusOptions();});
                row.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN&&key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_LEFT)navigation();else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)focusOptions();else categories[Math.max(0,Math.min(categories.length-1,index+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)))].requestFocus();}return true;});
            }
            footer=text(version(),18,VelaTheme.MUTED,Ui.medium);put(stage,footer,86,804,380,38);
            TextView hint=text("← "+Ui.tr("Menu do Vela","Vela menu","Menú de Vela")+"\n→ "+Ui.tr("Opções","Options","Opciones"),17,VelaTheme.MUTED,Ui.regular);hint.setMaxLines(2);put(stage,hint,86,852,380,64);
            building=false;render(22);categories[0].requestFocus();
        }
        private int categoryFor(int value){if(value==22||value==25)return 0;if(value==24||value==2||value>=17&&value<=20)return 1;if(value>=6&&value<=10)return 2;if(value==3||value>=14&&value<=16||value==21)return 3;if(value==11||value==12)return 4;if(value==23)return 5;if(value==4||value==5)return 7;return 6;}
        private void skin(){int selected=categoryFor(page);for(int i=0;i<categories.length;i++){boolean focus=categories[i].hasFocus();categories[i].setBackground(round(focus?Appearance.accent(activity):selected==i?VelaTheme.SELECTED:Color.TRANSPARENT,VelaTheme.CONTROL_RADIUS));categoryText[i].setTextColor(focus?INK:selected==i?WHITE:VelaTheme.SECONDARY);categoryIcons[i].color(focus?INK:selected==i?WHITE:VelaTheme.SECONDARY);}}
        private void tools(int page){if(activity.owner instanceof VelaHub.Owner)((VelaHub.Owner)activity.owner).tools(page);else activity.startActivity(new Intent(activity,HomeActivity.class).putExtra("velaTools",page));}
        private void navigation(){View target=activity.navigation.navigationTarget(activity);if(target!=null)target.requestFocus();else activity.finish();}
        private void focusOptions(){List<View> focusable=pane.getFocusables(FOCUS_FORWARD);if(!focusable.isEmpty())focusable.get(Math.min(page<focusMemory.length?focusMemory[page]:0,focusable.size()-1)).requestFocus();}
        boolean showingUpdates(){return updates!=null&&page==23;}
        private void closeUpdates(){if(updates!=null){updates.destroy();updates=null;}updateState=null;}
        private void openUpdates(){
            updates=new UpdatesScreen(activity.owner,new VelaScreen.Navigation(){
                public boolean embedded(){return true;}public void content(VelaScreen s,View content){put(rowHost,content,0,0,1134,814);}
                public void open(VelaScreen s,Intent intent){activity.startActivity(intent);}public void close(VelaScreen s){categories[5].requestFocus();}
                public void fullscreen(VelaScreen s,boolean full){}public void permission(VelaScreen s,String[] names,int request){activity.requestPermissions(names,request);}
                public void changed(VelaScreen s){activity.changed();}public View navigationTarget(VelaScreen s){return categories[5];}
            },true);updates.create(new Intent(activity,UpdatesActivity.class),updateState);updateState=null;if(active)updates.resume();if(categoryBrowsing)categories[5].requestFocus();
        }
        private String version(){try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception e){return "1.0.0";}}
        private TextView text(String s,int size,int color,Typeface font){TextView t=new TextView(activity);t.setText(s);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);t.setTextColor(color);t.setTypeface(font);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
        private void put(FrameLayout parent,View view,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(view,p);}
        private GradientDrawable round(int color){return round(color,20);}
        private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){float scale=Math.min(w/1790f,h/940f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(scale);stage.setScaleY(scale);stage.setTranslationX((w-1790*scale)/2);stage.setTranslationY((h-940*scale)/2);}
        private void section(String s,int y){if(y==0){heading.setText(page==22?categoryTitles[0]:s.substring(0,1)+s.substring(1).toLowerCase(Ui.locale()));return;}TextView h=text(s,18,VelaTheme.MUTED,Ui.semibold);h.setLetterSpacing(.08f);put(rowHost,h,8,contentY+18,1118,32);contentY+=64;}
        private void row(String label,String value,int y,String title,String description,Runnable action){row(label,value,"",y,title,description,action);}
        private void row(String label,String value,String secondary,int y,String title,String description,Runnable action){
            FrameLayout row=new FrameLayout(activity);row.setId(View.generateViewId());row.setFocusable(true);row.setFocusableInTouchMode(true);row.setClickable(true);row.setDefaultFocusHighlightEnabled(false);row.setContentDescription(label+(value.isEmpty()?"":", "+value));
            int height=secondary.isEmpty()?78:106;put(rowHost,row,0,contentY,1134,height);contentY+=height+10;
            TextView name=text(label,23,WHITE,Ui.medium);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);put(row,name,24,secondary.isEmpty()?0:12,650,secondary.isEmpty()?78:38);
            TextView right=text(value,22,DIM,Ui.regular);right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);right.setSingleLine(true);right.setEllipsize(android.text.TextUtils.TruncateAt.END);put(row,right,710,0,398,height);
            TextView sub=text(secondary,19,DIM,Ui.regular);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);if(!secondary.isEmpty())put(row,sub,24,58,650,32);
            rows.add(row);labels.add(name);values.add(right);row.setBackground(VelaTheme.row(false,Appearance.accent(activity)));
            row.setNextFocusLeftId(categories[categoryFor(page)].getId());row.setOnFocusChangeListener((v,focused)->{row.setBackground(VelaTheme.row(focused,Appearance.accent(activity)));name.setTextColor(focused?INK:WHITE);right.setTextColor(focused?0xFF60636C:DIM);sub.setTextColor(focused?0xFF656874:DIM);if(focused){status.setText(description);if(page<focusMemory.length)focusMemory[page]=rows.indexOf(row);}});
            row.setOnClickListener(v->{action.run();activity.changed();});
        }
        void render(int next){
            boolean wasBuilding=building;building=true;
            if(page==10&&next!=10){searchGeneration++;if(searchJob!=null)searchJob.cancel(true);}
            Bundle previous=next==23&&updates!=null?updates.save():updateState;closeUpdates();updateState=previous;
            page=next;pane.removeAllViews();rows.clear();labels.clear();values.clear();status.setText("");pickerFocus=0;contentY=0;
            scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setClipToPadding(true);scroll.setVerticalScrollBarEnabled(true);scroll.setScrollbarFadingEnabled(false);scroll.setScrollBarSize(3);scroll.setFocusable(false);put(pane,scroll,0,0,1134,610);rowHost=new FrameLayout(activity);scroll.addView(rowHost,new ScrollView.LayoutParams(1134,LayoutParams.WRAP_CONTENT));String[] captions={L10n.text("Choose your live TV view and connect your channels."),L10n.text("Colour, posters, backgrounds and performance."),L10n.text("A break with your style, clock and weather."),L10n.text("Your libraries and services, connected."),L10n.text("Choose the apps in your sidebar."),"Vela "+version(),L10n.text("Your home screen and television controls."),L10n.text("Version, settings and licences.")};detail.setText(captions[categoryFor(page)]);skin();
            if(page==0){
                section(Ui.tr("SISTEMA","SYSTEM","SISTEMA"),0);
                row(L10n.text("Home screen"),mode(),0,L10n.text("Your television, your way."),L10n.text("Choose Vela as your home screen or return to your TV's original home."),()->open(1,0));
                row(L10n.text("TV settings"),"›",0,L10n.text("Your TV controls, close at hand."),L10n.text("Picture, sound, network, Bluetooth and television controls."),()->launch(new Intent(Settings.ACTION_SETTINGS)));
                row(Ui.tr("Assistente de configuração","Setup guide","Asistente de configuración"),"›",0,L10n.text("Everything, your way."),L10n.text("Review accounts, apps and location without clearing your settings."),()->activity.startActivity(new Intent(activity,SetupActivity.class)));
                row(L10n.text("Remote shortcuts"),"›",0,L10n.text("Remote shortcuts"),L10n.text("Teach Vela a button and choose its short and long press actions"),()->tools(VelaHub.REMOTE));
                row(L10n.text("Your Vela"),"›",0,L10n.text("Your Vela."),L10n.text("Backups, profiles, search, programmes, reading and diagnostics."),()->tools(1));
                note(L10n.text("Your original home screen and screensaver are kept so you can return whenever you like."),0);
            }else if(page==23){
                section(Ui.tr("ATUALIZAÇÕES","UPDATES","ACTUALIZACIONES"),0);detail.setText("Vela "+version());openUpdates();
            }else if(page==1){
                section(L10n.text("HOME SCREEN"),0);
                row("Vela",isVela()?"✓":L10n.text("Activate"),L10n.text("Home screen, screensaver and recovery."),54,L10n.text("Your personal home screen."),L10n.text("Enable Vela and its screensaver. Vela saves a route back to the TV's original home before completing setup."),()->switchHome(true));
                row("Original",!isVela()?"✓":"",L10n.text("Your television's factory home screen."),178,L10n.text("You can always go back."),L10n.text("Restore your television's original home screen and screensaver."),()->switchHome(false));
                note(L10n.text("The change can take a few seconds. If your TV requests permission, select Always allow and confirm with the remote."),338);
            }else if(page==25){
                section(L10n.text("Match content frame rate"),0);detail.setText(L10n.text("Smoother video on compatible displays."));
                String description=L10n.text("Applies to Vela's network player. Display switching may briefly blank the screen. External apps, HDMI and antenna keep their own display controls.");
                for(int i=0;i<3;i++){final int mode=i;row(VelaFrameRate.name(i),VelaFrameRate.mode(activity)==i?"✓":"",0,L10n.text("Match content frame rate"),"",()->{VelaFrameRate.mode(activity,mode);render(25);rows.get(mode).requestFocus();});}
                note(description,0);
            }else if(page==24){
                section(L10n.text("LANGUAGE"),0);
                for(int i=0;i<L10n.CODES.length;i++){final String code=L10n.CODES[i];String name=i==0?L10n.text("System language"):L10n.NAMES[i];row(name,code.equals(L10n.choice())?"✓":"",0,L10n.text("Make Vela feel at home."),L10n.text("Applies to every Vela menu. App names, channels and your library keep their original names."),()->{if(!L10n.choose(activity,code)){toast(L10n.text("Could not save language."));return;}Ui.init(activity);activity.changed();activity.owner.recreate();});}
                note(L10n.text("Your accounts, favourites and television settings are kept."),0);
            }else if(page==2){
                section(L10n.text("PICTURE AND PERFORMANCE"),0);
                row(L10n.text("Image quality"),Visuals.light(activity)?L10n.text("Light"):L10n.text("High"),54,L10n.text("The picture you prefer."),L10n.text("High keeps Full HD backgrounds. Light uses 720p backgrounds and skips menu blur. Posters keep their full quality."),()->{Visuals.prefs(activity).edit().putBoolean("light",!Visuals.light(activity)).apply();render(2);});
                row(L10n.text("Reduce motion"),Visuals.reducedMotion(activity)?L10n.text("Yes"):L10n.text("No"),134,L10n.text("Calmer focus movement."),L10n.text("Remove focus scaling and transitions. Vela also respects Android's animation settings."),()->{Visuals.prefs(activity).edit().putBoolean("reducedMotion",!Visuals.reducedMotion(activity)).apply();render(2);rows.get(1).requestFocus();});
                section(L10n.text("MEMORY"),270);
                Debug.MemoryInfo memory=new Debug.MemoryInfo();Debug.getMemoryInfo(memory);
                row(L10n.text("Process memory"),String.format(Ui.locale(),"%.1f MB",memory.getTotalPss()/1024f),312,L10n.text("A real measurement."),L10n.text("Current proportional memory for Home and Settings, including Android and graphics. It varies with artwork and device. Press to refresh."),()->{render(2);rows.get(2).requestFocus();});

                row(Ui.tr("Personalizar aparência","Customize appearance","Personalizar apariencia"),"›",684,Ui.tr("Seu estilo.","Your style.","Tu estilo."),Ui.tr("Escolha cor, tamanho dos cartazes e intensidade do fundo. O desenho original fica disponível.","Choose color, poster size and background intensity. The original design stays available.","Elige color, tamaño de carteles e intensidad del fondo. El diseño original sigue disponible."),()->open(17,3));
                note(L10n.text("Image cache: up to ")+Visuals.cacheKb(activity)/1024+L10n.text(" MB. Cleared when you leave Home. No videos or animated backgrounds play on Home."),440);
            }else if(page==17){
                section(Ui.tr("APARÊNCIA","APPEARANCE","APARIENCIA"),0);row(L10n.text("Visual styles"),VelaStyle.name(VelaStyle.selected(activity)),0,L10n.text("A complete look, with a preview"),L10n.text("Cinema, Compact or Minimal. Review before saving."),()->{if(activity.owner instanceof VelaHub.Owner)((VelaHub.Owner)activity.owner).toolsStyles();else tools(6);});
                row(Ui.tr("Cor de destaque","Accent color","Color de acento"),Appearance.colorName(Appearance.colorIndex(activity)),54,Ui.tr("Um toque de cor.","A touch of color.","Un toque de color."),Ui.tr("A cor aparece na seleção do menu, na ação principal, no progresso e nos Ajustes.","Color appears in menu selection, the main action, progress and Settings.","El color aparece en la selección del menú, la acción principal, el progreso y los Ajustes."),()->render(18));
                row(Ui.tr("Tamanho dos cartazes","Poster size","Tamaño de carteles"),Appearance.sizeName(Appearance.sizeIndex(activity)),134,Ui.tr("Seu cinema, na sua medida.","Your cinema, your size.","Tu cine, a tu medida."),Ui.tr("Três tamanhos com a proporção original. As páginas continuam limitadas a 24 títulos.","Three sizes with the original aspect ratio. Pages remain limited to 24 titles.","Tres tamaños con su proporción original. Las páginas siguen limitadas a 24 títulos."),()->render(19));
                row(Ui.tr("Intensidade do fundo","Background intensity","Intensidad del fondo"),Appearance.backdropName(Appearance.backdropIndex(activity)),214,Ui.tr("O ambiente que você quiser.","Set the atmosphere.","El ambiente que quieras."),Ui.tr("Escureça o fundo para ler melhor ou deixe mais visível a imagem original.","Dim the background for reading or reveal more of the original artwork.","Oscurece el fondo para leer mejor o deja más visible la imagen original."),()->render(20));
                row(Ui.tr("Restaurar aparência","Reset appearance","Restaurar apariencia"),"›",374,Ui.tr("Voltar ao desenho original.","Return to the original design.","Volver al diseño original."),Ui.tr("Restaura apenas cor, cartazes e fundo. Conserva contas, apps, clima e desempenho.","Resets only color, posters and background. Keeps accounts, apps, weather and performance.","Restaura solo color, carteles y fondo. Conserva cuentas, apps, clima y rendimiento."),()->{Appearance.reset(activity);render(17);rows.get(4).requestFocus();});
                row(L10n.text("Reading and margins"),"›",0,L10n.text("Comfortable from the sofa."),L10n.text("Text size, guide density, contrast and screen margins."),()->tools(2));
                row(L10n.text("Language"),L10n.name(),0,L10n.text("Vela in your language."),L10n.text("Choose a language or follow this television's system language."),()->render(24));
                row(L10n.text("Picture and performance"),"›",0,L10n.text("The picture you prefer."),L10n.text("Colour, posters, backgrounds and performance."),()->open(2,7));
                note(Ui.tr("As mudanças aparecem ao voltar ao início. Sem fundos adicionais, vídeos ou animações permanentes.","Changes appear when you return home. No additional backgrounds, videos or continuous animations.","Los cambios aparecen al volver al inicio. Sin fondos adicionales, vídeos ni animaciones permanentes."),520);
            }else if(page==18||page==19||page==20){
                String key=page==18?"color":page==19?"size":"backdrop";int current=page==18?Appearance.colorIndex(activity):page==19?Appearance.sizeIndex(activity):Appearance.backdropIndex(activity),count=page==18?6:3;
                section(page==18?Ui.tr("COR DE DESTAQUE","ACCENT COLOR","COLOR DE ACENTO"):page==19?Ui.tr("CARTAZES","POSTERS","CARTELES"):Ui.tr("FUNDO","BACKGROUND","FONDO"),0);
                for(int i=0;i<count;i++){final int choice=i;String label=page==18?Appearance.colorName(i):page==19?Appearance.sizeName(i):Appearance.backdropName(i);row(label,i==current?"✓":"",54+i*80,label,Ui.tr("Escolha com OK. Voltar retorna à aparência.","Choose with OK. Back returns to appearance.","Elige con OK. Atrás vuelve a apariencia."),()->{Appearance.set(activity,key,choice);int same=page;render(same);rows.get(choice).requestFocus();});}
            }else if(page==3){
                section("YOUTUBE",0);
                boolean allowed=activity.checkSelfPermission(TvSuggestions.PERMISSION)==PackageManager.PERMISSION_GRANTED;
                row(L10n.text("TV rows"),allowed?L10n.text("Allowed"):L10n.text("Activate"),54,L10n.text("Shared by YouTube."),L10n.text("Video availability depends on what YouTube shares with your TV. Vela does not request your password."),()->{if(!allowed)activity.requestPermissions(new String[]{TvSuggestions.PERMISSION},80);else toast(L10n.text("Access is already allowed."));});
                row(L10n.text("Open YouTube"),"›",134,L10n.text("Back to your videos."),L10n.text("Open YouTube for your recommendations, subscriptions and Watch Later list."),()->{Intent i=HomeApps.launchIntent(activity.getPackageManager(),TvSuggestions.YOUTUBE);if(i!=null)launch(i);else toast(L10n.text("YouTube is not installed."));});
            }else if(page==4){
                section(L10n.text("INFORMATION"),0);
                row("Vela",version(),54,"Vela.",L10n.text("Your library and apps, on a screen designed for your TV remote."),()->{});
                row(L10n.text("Television"),Build.MANUFACTURER+" · "+Build.MODEL,134,L10n.text("Your device."),L10n.text("Information about the device running Vela."),()->{});
                row("Android",Build.VERSION.RELEASE,214,L10n.text("Your television's system."),L10n.text("Manage picture, sound, network and accounts in your TV's settings."),()->{});
                row(L10n.text("Licences"),"›",334,L10n.text("Credits and licences."),L10n.text("Notices for the open-source components, fonts and symbols used in Vela."),()->open(5,7));
                row(Ui.tr("Assistente de configuração","Setup guide","Asistente de configuración"),"›",434,Ui.tr("Tudo a seu gosto.","Make it yours.","Todo a tu gusto."),Ui.tr("Revise contas, apps e localização sem apagar sua configuração.","Review accounts, apps and location without erasing your setup.","Repasa cuentas, apps y ubicación sin borrar tu configuración."),()->activity.startActivity(new Intent(activity,SetupActivity.class)));
                row(Ui.tr("Atualizações","Updates","Actualizaciones"),"›",534,Ui.tr("Sempre em dia.","Always up to date.","Siempre al día."),Ui.tr("Novas versões do GitHub, com verificação automática e instalação confirmada por você.","New GitHub releases, with automatic checks and installation confirmed by you.","Nuevas versiones de GitHub, con comprobación automática e instalación confirmada por ti."),()->render(23));
            }else if(page==6){
                section(L10n.text("SCREENSAVER"),0);
                row(L10n.text("Style"),AmbientPrefs.styleName(activity),54,L10n.text("A break, your way."),L10n.text("Aerials shows landscape and city videos. Plex or Jellyfin uses original library artwork with gentle movement."),()->render(7));
                row(L10n.text("Preview"),L10n.text("Open"),134,L10n.text("See it on your screen."),L10n.text("The preview uses your chosen style and settings. Any arrow, OK or Back closes it."),()->activity.showPreview());
                row(L10n.text("Clock"),AmbientPrefs.clock(activity)?L10n.text("Visible"):L10n.text("Hidden"),214,L10n.text("The time at a glance."),L10n.text("The clock stays visible in both styles. It updates each minute without continuous animation."),()->{AmbientPrefs.get(activity).edit().putBoolean("clock",!AmbientPrefs.clock(activity)).apply();render(6);rows.get(2).requestFocus();});
                row(L10n.text("Local weather"),AmbientPrefs.weather(activity)?L10n.text("Visible"):L10n.text("Hidden"),294,L10n.text("Your weather, on your screen."),L10n.text("Temperature and sky conditions beside the clock. Fetched on entry and reused for thirty minutes."),()->{AmbientPrefs.get(activity).edit().putBoolean("weather",!AmbientPrefs.weather(activity)).apply();render(6);rows.get(3).requestFocus();});
                row(L10n.text("Location"),AmbientWeather.name(activity),374,L10n.text("Weather where you are."),L10n.text("Use your TV's approximate location or search for a city. If location is unavailable, choose it with your remote."),()->render(9));
                row(L10n.text("On-screen text"),AmbientPrefs.captions(activity)?L10n.text("Subtle"):L10n.text("Hidden"),454,L10n.text("Let the picture shine."),L10n.text("The place or title appears for eight seconds. The clock and weather remain visible when enabled."),()->{AmbientPrefs.get(activity).edit().putBoolean("captions",!AmbientPrefs.captions(activity)).apply();render(6);rows.get(5).requestFocus();});
                if(AmbientPrefs.plex(activity))row(L10n.text("Change image"),AmbientPrefs.seconds(activity)==30?L10n.text("30 seconds"):AmbientPrefs.seconds(activity)==60?L10n.text("1 minute"):L10n.text("2 minutes"),534,L10n.text("At your own pace."),L10n.text("Choose how long each library image stays. With Reduce motion enabled, photos appear without zoom."),()->{int n=AmbientPrefs.seconds(activity);AmbientPrefs.get(activity).edit().putInt("seconds",n==30?60:n==60?120:30).apply();render(6);rows.get(6).requestFocus();});
                else row(L10n.text("Theme"),AmbientPrefs.themeName(activity),534,L10n.text("Landscapes or cities."),L10n.text("Shuffle all videos or choose landscapes or cities. Videos play silently and require an Internet connection."),()->render(8));
                row(L10n.text("Enable on your TV"),"›",654,L10n.text("When your television rests."),L10n.text("Idle time and activation depend on your TV. Choose them in Android's screensaver settings."),()->{Intent intent=new Intent(Settings.ACTION_DREAM_SETTINGS);if(intent.resolveActivity(activity.getPackageManager())==null)intent=new Intent(Settings.ACTION_SETTINGS);launch(intent);});
                note(L10n.text("Weather uses Open-Meteo. Offline, the latest data stays available for up to three hours. Choose your city to enable weather."),758);
            }else if(page==7){
                section(L10n.text("SCREENSAVER STYLE"),0);
                row(L10n.text("Aerials"),!AmbientPrefs.plex(activity)?"✓":"",L10n.text("Landscapes and cities in motion."),54,L10n.text("Landscapes in motion."),Ui.tr("Paisagens e cidades em movimento, em tela cheia e sem som. Requer Internet.","Landscapes and cities in motion, full screen and silent. Internet required.","Paisajes y ciudades en movimiento, a pantalla completa y sin sonido. Requiere Internet."),()->{AmbientPrefs.get(activity).edit().putString("style","aerial").apply();render(6);});
                row(Ui.tr("Sua biblioteca","Your library","Tu biblioteca"),AmbientPrefs.plex(activity)?"✓":"",L10n.text("Your original artwork with gentle zoom."),178,L10n.text("Your cinema takes a break, too."),Ui.tr("Seus filmes e séries preenchem cada pausa com suas imagens originais.","Your movies and series fill each pause with their original artwork.","Tus películas y series llenan cada pausa con sus fondos originales."),()->{AmbientPrefs.get(activity).edit().putString("style","plex").apply();render(6);});
            }else if(page==8){
                section(L10n.text("AERIAL THEME"),0);
                String[] themes={"all","landscape","city"},names={L10n.text("All"),L10n.text("Landscapes"),L10n.text("Cities")};
                for(int i=0;i<themes.length;i++){final String theme=themes[i];row(names[i],theme.equals(AmbientPrefs.theme(activity))?"✓":"",54+i*84,L10n.text("Choose where to go."),L10n.text("Real videos of coastlines, mountains and cities. Video plays only in the screensaver."),()->{AmbientPrefs.get(activity).edit().putString("theme",theme).apply();render(6);rows.get(6).requestFocus();});}
            }else if(page==9){
                section(L10n.text("WEATHER LOCATION"),0);
                boolean allowed=activity.checkSelfPermission(AmbientWeather.PERMISSION)==PackageManager.PERMISSION_GRANTED;
                row(L10n.text("Automatic location"),"auto".equals(AmbientWeather.mode(activity))&&allowed?"✓":allowed?L10n.text("Activate"):L10n.text("Allow"),54,L10n.text("Weather for your television."),L10n.text("Only approximate location while using Vela is needed. Your manual city stays available if location cannot be found."),()->{if(!allowed)activity.requestPermissions(new String[]{AmbientWeather.PERMISSION},81);else locationPermission(true);});
                AmbientWeather.Place place=AmbientWeather.manual(activity);
                row(L10n.text("Choose city"),place==null?L10n.text("Search"):place.name,134,L10n.text("Your city, close at hand."),L10n.text("Search for your city or postcode. Add a country to narrow results. Location permission is not required."),()->{cityQuery=place==null?"":place.name;cityResults=Collections.emptyList();render(10);});
                note(L10n.text("Open-Meteo receives approximate coordinates for weather and your search text for city searches. Vela requests neither precise nor background location."),290);
                note(L10n.text("Choose a city if Android location is off or unavailable. Weather by Open-Meteo; place search based on GeoNames."),480);
            }else if(page==10){
                section(L10n.text("SEARCH FOR A CITY"),0);
                cityInput=new EditText(activity);cityInput.setId(View.generateViewId());cityInput.setText(cityQuery);cityInput.setSelection(cityInput.length());cityInput.setHint(L10n.text("City or postcode"));cityInput.setTextSize(TypedValue.COMPLEX_UNIT_PX,27);cityInput.setTextColor(WHITE);cityInput.setHintTextColor(DIM);cityInput.setSingleLine(true);cityInput.setPadding(26,0,26,0);cityInput.setBackground(round(VelaTheme.CARD));cityInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);cityInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);put(rowHost,cityInput,0,0,1134,74);contentY=92;
                cityInput.setOnFocusChangeListener((v,focus)->{cityInput.setBackground(round(focus?VelaTheme.RAISED:VelaTheme.CARD));if(focus){heading.setText(L10n.text("Weather for your city."));detail.setText(L10n.text("Type with your remote and select Search. Choose the correct place from the results."));}});
                cityInput.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){searchCities();return true;}return false;});
                row(L10n.text("Search"),"›",146,L10n.text("Find your city."),L10n.text("Search for a city or postcode. If names match, choose the correct region and country."),this::searchCities);
                cityInput.setNextFocusDownId(rows.get(0).getId());rows.get(0).setNextFocusUpId(cityInput.getId());
                for(int i=0;i<cityResults.size();i++){final AmbientWeather.Place place=cityResults.get(i);row(place.name,L10n.text("Choose"),place.label,242+i*112,L10n.text("Weather in ")+place.name+".",place.label,()->{AmbientWeather.select(activity,place);if(activity.returnToSetup){activity.finish();return;}render(6);rows.get(4).requestFocus();});}
                if(cityResults.isEmpty())note(L10n.text("Select the field to type with your TV keyboard. You can change the city whenever you like."),276);
            }else if(page==11){
                section(Ui.tr("ACESSOS DO MENU","MENU SHORTCUTS","ACCESOS DEL MENÚ"),0);
                String[] selected=SidebarApps.slots(activity);
                shortcutScroll(selected.length+2);
                for(int i=0;i<selected.length;i++){
                    final int slot=i;String pkg=selected[i];String name=SidebarApps.label(activity,pkg);
                    row((i+1)+". "+name,Ui.tr("Alterar","Change","Cambiar"),i*84,Ui.tr("Posição ","Position ","Posición ")+(i+1),Ui.tr("Escolha um app instalado, oculte ou remova este acesso. Ao escolher um app que já está no menu, as posições são trocadas.","Choose an installed app, hide or remove this shortcut. Selecting an app already in the menu swaps their positions.","Elige una app instalada, oculta o elimina este acceso. Si eliges una app que ya está en el menú, se intercambian sus posiciones."),()->{shortcutSlot=slot;render(12);});
                    shortcutIcon(pkg);
                    if(!pkg.isEmpty()&&!SidebarApps.launchable(activity,pkg))values.get(i).setText(Ui.tr("Não disponível","Unavailable","No disponible"));
                }
                row(Ui.tr("Adicionar acesso","Add shortcut","Añadir acceso"),"+",selected.length*84,Ui.tr("Mais apps, do seu jeito.","More apps, your way.","Más apps, a tu manera."),Ui.tr("Adicione outro app instalado. Use as setas para percorrer os acessos da barra lateral.","Add another installed app. Use the arrows to scroll through sidebar shortcuts.","Añade otra app instalada. Usa las flechas para recorrer los accesos de la barra izquierda."),()->{shortcutSlot=-1;render(12);});
                row(Ui.tr("Detectar acessos novamente","Detect shortcuts again","Volver a detectar accesos"),"›",(selected.length+1)*84,Ui.tr("Os apps desta TV.","This TV's apps.","Las apps de esta televisión."),Ui.tr("Substitui a lista pelos apps sugeridos que estão instalados. Não altera contas ou outros ajustes.","Replace the list with suggested apps that are installed. Accounts and other preferences are preserved.","Sustituye la lista por las apps sugeridas que estén instaladas. Conserva tus cuentas y el resto de ajustes."),()->{SidebarApps.reset(activity);render(11);});
                status.setText(Ui.tr("As alterações são salvas automaticamente ao voltar ao início.","Changes are saved automatically and applied when you return home.","Los cambios se guardan automáticamente y aparecen al volver al inicio."));
            }else if(page==12){
                boolean adding=shortcutSlot<0;
                section(adding?Ui.tr("ADICIONAR ACESSO","ADD SHORTCUT","AÑADIR ACCESO"):Ui.tr("ESCOLHER APP · ","CHOOSE APP · ","ELEGIR APP · ")+(shortcutSlot+1),0);
                List<SidebarApps.Choice> choices=SidebarApps.choices(activity);String[] selected=SidebarApps.slots(activity);
                if(adding)choices.removeIf(choice->Arrays.asList(selected).contains(choice.pkg));
                int offset=adding?0:2;shortcutScroll(choices.size()+offset);
                if(!adding){
                    row(SidebarApps.label(activity,""),selected[shortcutSlot].isEmpty()?"✓":"",0,Ui.tr("Um menu mais simples.","A simpler menu.","Un menú más sencillo."),Ui.tr("Oculta apenas este acesso da barra lateral.","Hide this shortcut from the sidebar.","Oculta este acceso de la barra izquierda."),()->chooseShortcut(""));
                    row(Ui.tr("Remover acesso","Remove shortcut","Eliminar acceso"),"−",84,Ui.tr("Remover do menu.","Remove from the menu.","Quitar del menú."),Ui.tr("Remove esta posição. O app continua instalado.","Remove this position. The app stays installed.","Elimina esta posición. La aplicación sigue instalada."),()->{int focus=shortcutSlot;SidebarApps.remove(activity,shortcutSlot);render(11);rows.get(Math.min(focus,rows.size()-1)).requestFocus();});
                }
                for(int i=0;i<choices.size();i++){
                    SidebarApps.Choice choice=choices.get(i);String value="";
                    for(int slot=0;slot<selected.length;slot++)if(choice.pkg.equals(selected[slot]))value=slot==shortcutSlot?"✓":Ui.tr("Posição ","Position ","Posición ")+(slot+1);
                    row(choice.label,value,(i+offset)*84,choice.label,Ui.tr("Use o ícone original deste app no menu. Se já estiver em outra posição, os acessos são trocados.","Use this app's original icon in the menu. If it is already in another position, the shortcuts swap.","Usa el icono original de esta app en el menú. Si ya está en otra posición, los accesos se intercambian."),()->chooseShortcut(choice.pkg));
                    shortcutIcon(choice.pkg);if(!adding&&choice.pkg.equals(selected[shortcutSlot]))pickerFocus=i+offset;
                }
                if(adding&&choices.isEmpty())note(Ui.tr("Todos os apps disponíveis já estão no menu.","All available apps are already in the menu.","Todas las apps disponibles ya están en el menú."),76);
            }else if(page==14){
                section(Ui.tr("CONTAS E SERVIÇOS","ACCOUNTS AND SERVICES","CUENTAS Y SERVICIOS"),0);
                PlexClient plex=new PlexClient(activity);
                row("Plex",plex.connected()?plex.name():Ui.tr("Conectar","Connect","Conectar"),54,Ui.tr("Sua biblioteca, automaticamente.","Your library, automatically.","Tu biblioteca, automáticamente."),Ui.tr("Seus filmes, séries e cartazes originais, juntos ao conectar sua biblioteca.","Your movies, series and original posters, together when you connect your library.","Tus películas, tus series y sus carteles originales, juntos al conectar tu biblioteca."),()->render(15));
                JellyfinClient jellyfin=new JellyfinClient(activity);
                row("Jellyfin",jellyfin.connected()?jellyfin.name():Ui.tr("Conectar","Connect","Conectar"),154,Ui.tr("Seu servidor, no início.","Your server, at home.","Tu servidor, en Inicio."),Ui.tr("Conecte seu servidor Jellyfin com um código ou usuário e senha. Cartazes, novidades e progresso vêm da sua conta.","Connect your Jellyfin server with a code or username and password. Posters, recent additions and progress come from your account.","Conecta tu servidor Jellyfin con un código o con usuario y contraseña. Los carteles, novedades y progreso proceden de tu cuenta."),()->activity.startActivity(new Intent(activity,JellyfinLinkActivity.class)));
                row("YouTube",SidebarApps.launchable(activity,SidebarApps.YOUTUBE)?Ui.tr("No app","In the app","En su app"):Ui.tr("Não instalado","Not installed","No instalada"),254,Ui.tr("Sua sessão do YouTube.","Your YouTube session.","Tu sesión de YouTube."),Ui.tr("Entre ou troque de conta no YouTube. Vela pode mostrar as filas que o app compartilha com a TV.","Sign in or switch accounts in YouTube. Vela can show the rows the app shares with the TV.","Inicia sesión o cambia de cuenta dentro de YouTube. Vela puede mostrar las filas que la app comparta con la TV."),()->render(3));
                providerRow("Prime Video","com.amazon.amazonvideo.livingroom",354);
                row(Ui.tr("Outros aplicativos","Other apps","Otras aplicaciones"),"›",454,Ui.tr("Seus apps e suas contas.","Your apps and accounts.","Tus apps y tus cuentas."),Ui.tr("Adicione apps instalados ao menu. Cada serviço gerencia sua própria sessão.","Add installed apps to the menu. Each service manages its own session.","Añade las apps instaladas al menú. Cada servicio gestiona su propia sesión."),()->render(11));
                row(Ui.tr("TV em direto","Live TV","TV en directo"),"›",554,Ui.tr("Seus canais, no Vela.","Your channels, in Vela.","Tus canales, dentro de Vela."),Ui.tr("M3U, Xtream e antena. Canais, favoritos e programação em uma interface nativa.","M3U, Xtream and antenna. Channels, favorites and listings in a native interface.","M3U, Xtream y antena. Canales, favoritos y programación en una interfaz nativa."),()->render(22));
                row(Ui.tr("Biblioteca do início","Home library","Biblioteca de Inicio"),new LibraryClient(activity).provider(),654,Ui.tr("Escolha sua biblioteca.","Choose your library.","Elige tu biblioteca."),Ui.tr("Escolha Plex ou Jellyfin para o início, catálogo e fundos do protetor de tela. As duas contas são mantidas.","Choose Plex or Jellyfin for home, catalog and library screensaver artwork. Both authorizations are kept.","Elige Plex o Jellyfin para Inicio, catálogo y fondos del salvapantallas. Se conservan las dos autorizaciones."),()->render(21));
            }else if(page==22){
                section(Ui.tr("TV EM DIRETO","LIVE TV","TV EN DIRECTO"),0);
                boolean guide=LiveTvData.guideView(activity);
                row(Ui.tr("Guia","Guide","Guía"),guide?"✓":"",54,Ui.tr("Tudo em uma linha do tempo.","Your TV on a timeline.","Toda tu televisión, en una guía."),Ui.tr("Explore os canais e seus horários lado a lado. É a vista inicial do Vela.","Explore channels and their schedules side by side. This is Vela's default view.","Explora los canales y sus horarios en horizontal. Es la vista predeterminada de Vela."),()->{LiveTvData.guideView(activity,true);render(22);rows.get(0).requestFocus();});
                row(Ui.tr("Canais","Channels","Canales"),guide?"":"✓",134,Ui.tr("Seus canais em destaque.","Your channels, front and center.","Tus canales, protagonistas."),Ui.tr("Logos grandes e o programa no ar. Use as setas para cima e para baixo para mudar de categoria.","Large logos and the current programme. Use Up and Down to change category.","Logos grandes y el programa en emisión. Usa las flechas arriba y abajo para cambiar de categoría."),()->{LiveTvData.guideView(activity,false);render(22);rows.get(1).requestFocus();});
                section(Ui.tr("SUAS FONTES","YOUR SOURCES","TUS FUENTES"),300);
                row(Ui.tr("Listas e ajustes","Playlists and settings","Listas y ajustes"),"›",342,Ui.tr("Seus canais, conectados.","Your channels, connected.","Tus canales, conectados."),Ui.tr("Configure suas listas M3U, conta Xtream, antena e guia de programação.","Configure your M3U playlists, Xtream account, antenna and programme guide.","Configura tus listas M3U, tu cuenta Xtream, la antena y la guía de programación."),()->activity.startActivity(new Intent(activity,LiveTvActivity.class).putExtra("sources",true)));
                section(L10n.text("Playback controls"),1);
                row(L10n.text("Match content frame rate"),VelaFrameRate.name(VelaFrameRate.mode(activity)),0,L10n.text("Smoother video on compatible displays."),L10n.text("Off, seamless changes, or allow display switching. Applies only while Vela plays a network channel."),()->open(25,0));
                note(Ui.tr("A vista escolhida é aplicada ao voltar à televisão.","Your chosen view applies when you return to TV.","La vista elegida se aplica al volver a la televisión."),470);
            }else if(page==21){
                section(Ui.tr("BIBLIOTECA DO INÍCIO","HOME LIBRARY","BIBLIOTECA DE INICIO"),0);LibraryClient library=new LibraryClient(activity);PlexClient plex=new PlexClient(activity);JellyfinClient jellyfin=new JellyfinClient(activity);
                row("Plex",plex.connected()?(library.isJellyfin()||library.both()?plex.name():"✓ "+plex.name()):Ui.tr("Conectar","Connect","Conectar"),54,"Plex",Ui.tr("Usa esta biblioteca sem desconectar Jellyfin.","Use this library without unlinking Jellyfin.","Usa esta biblioteca sin desvincular Jellyfin."),()->{if(plex.connected()){LibraryClient.activate(activity,"Plex");render(21);}else activity.startActivity(new Intent(activity,PlexLinkActivity.class));});
                row("Jellyfin",jellyfin.connected()?(library.isJellyfin()?"✓ "+jellyfin.name():jellyfin.name()):Ui.tr("Conectar","Connect","Conectar"),154,"Jellyfin",Ui.tr("Usa esta biblioteca sem desconectar Plex.","Use this library without unlinking Plex.","Usa esta biblioteca sin desvincular Plex."),()->{if(jellyfin.connected()){LibraryClient.activate(activity,"Jellyfin");render(21);}else activity.startActivity(new Intent(activity,JellyfinLinkActivity.class));});
                row(L10n.text("Plex + Jellyfin together"),library.both()?"✓":L10n.text("Both accounts must be connected"),254,L10n.text("One home, both libraries"),L10n.text("Keep separate accounts and progress. Duplicate titles are combined only when their external IDs match."),()->{if(plex.connected()&&jellyfin.connected()){LibraryClient.activate(activity,"Both");render(21);}});
                note(Ui.tr("A troca é aplicada ao voltar ao início. Cada servidor mantém seu usuário, progresso e imagens.","Switching applies when you return home. Each server keeps its user, progress and artwork.","El cambio se aplica al volver a Inicio. Cada servidor mantiene su usuario, progreso e imágenes."),410);
            }else if(page==15){
                section("PLEX",0);PlexClient plex=new PlexClient(activity);
                row(plex.connected()?Ui.tr("Alterar servidor","Change server","Cambiar servidor"):Ui.tr("Conectar Plex","Connect Plex","Conectar Plex"),plex.connected()?plex.name():"›",54,Ui.tr("Sua biblioteca, conectada.","Your library, connected.","Tu biblioteca, conectada."),Ui.tr("Use o código em plex.tv/link. Se houver um único servidor, ele será escolhido automaticamente; se houver vários, você escolhe.","Use the code at plex.tv/link. A single server is selected automatically; if there are several, you choose.","Usa el código en plex.tv/link. Si solo hay un servidor, se elige automáticamente; si hay varios, tú eliges."),()->activity.startActivity(new Intent(activity,PlexLinkActivity.class)));
                if(!plex.prefs.getString("accountToken","").isEmpty())row(Ui.tr("Desconectar conta","Disconnect account","Desvincular cuenta"),"›",154,Ui.tr("Trocar de pessoa.","Switch person.","Cambiar de persona."),Ui.tr("Apaga a sessão do Plex apenas no Vela. Depois você pode vincular outra conta com um novo código.","Remove the Plex session only from Vela. You can then link another account with a new code.","Elimina la sesión de Plex solo de Vela. Después puedes vincular otra cuenta con un código nuevo."),()->render(16));
                note(Ui.tr("Nome do servidor, avatar, bibliotecas e cartazes vêm da conta que você autoriza. Não precisa editar arquivos nem recompilar.","The server name, avatar, libraries and posters come from the account you authorize. No file edits or rebuilding needed.","El nombre del servidor, el avatar, las bibliotecas y los carteles proceden de la cuenta que autorices. No necesitas editar archivos ni recompilar."),334);
            }else if(page==16){
                section(Ui.tr("DESCONECTAR PLEX","DISCONNECT PLEX","DESVINCULAR PLEX"),0);
                row(Ui.tr("Manter conta","Keep account","Mantener cuenta"),"›",54,Ui.tr("Sua sessão permanece.","Your session stays.","Tu sesión se conserva."),Ui.tr("Voltar sem alterar a conta.","Go back without changing your account.","Volver sin cambiar tu cuenta."),()->render(15));
                row(Ui.tr("Desconectar do Vela","Disconnect from Vela","Desvincular de Vela"),"›",154,Ui.tr("Remover esta sessão.","Remove this session.","Eliminar esta sesión."),Ui.tr("Remove as credenciais, bibliotecas em cache e imagens locais do Plex no Vela. Não apaga conteúdo do servidor nem sai do app Plex.","Remove Plex credentials, cached libraries and local images in Vela. Your server content and Plex app session stay intact.","Elimina las credenciales, las bibliotecas en caché y las imágenes locales de Plex en Vela. Conserva el contenido del servidor y la sesión de la app Plex."),()->{
                    for(View row:rows)row.setEnabled(false);
                    io.execute(()->{new PlexClient(activity).disconnect();CinemaImages.purgeDisk(activity);main.post(()->{if(!closed){render(15);status.setText(Ui.tr("Conta desconectada.","Account disconnected.","Cuenta desvinculada."));}});});
                });
            }else{
                section(L10n.text("OPEN-SOURCE LICENCES"),0);
                ScrollView legalScroll=new ScrollView(activity);legalScroll.setFocusable(true);legalScroll.setVerticalScrollBarEnabled(false);put(rowHost,legalScroll,0,0,1134,610);
                TextView legal=text(license(),18,0xFFCBD0DD,Ui.regular);legal.setGravity(Gravity.TOP);legal.setLineSpacing(5,1);legalScroll.addView(legal);legalScroll.requestFocus();
                heading.setText(L10n.text("Thanks to open source."));detail.setText(L10n.text("Vela preserves the notices required by its components' licences."));
            }
            for(int i=0;i<rows.size();i++){if(i>0)rows.get(i).setNextFocusUpId(rows.get(i-1).getId());if(i+1<rows.size())rows.get(i).setNextFocusDownId(rows.get(i+1).getId());}
            if(!categoryBrowsing&&page==10&&cityResults.isEmpty())cityInput.requestFocus();else if(!categoryBrowsing&&!rows.isEmpty())rows.get(page==12?pickerFocus:page==18?Appearance.colorIndex(activity):page==19?Appearance.sizeIndex(activity):page==20?Appearance.backdropIndex(activity):0).requestFocus();
            if(categoryBrowsing)categories[categoryFor(page)].requestFocus();building=wasBuilding;skin();
        }
        private void shortcutScroll(int count){/* All pages share one clipped, focus-scrolling viewport. */}
        private void providerRow(String name,String pkg,int y){
            row(name,SidebarApps.launchable(activity,pkg)?Ui.tr("Abrir","Open","Abrir"):Ui.tr("Não instalado","Not installed","No instalada"),y,name,Ui.tr("Entre ou troque de conta no próprio app. O acesso do menu abre a sessão dessa pessoa.","Sign in or change account in the app itself. The menu shortcut opens that person's session.","Inicia sesión o cambia de cuenta dentro de su app. El acceso del menú abre la sesión de esa persona."),()->{Intent intent=HomeApps.launchIntent(activity.getPackageManager(),pkg);if(intent!=null)launch(intent);else toast(Ui.tr("Instale o app na loja da TV.","Install the app from the TV store.","Instala la app desde la tienda de la televisión."));});
        }
        private void shortcutIcon(String pkg){
            if(pkg.isEmpty())return;
            FrameLayout row=rows.get(rows.size()-1);TextView label=labels.get(labels.size()-1);
            LayoutParams position=(LayoutParams)label.getLayoutParams();position.leftMargin=86;position.width=586;label.setLayoutParams(position);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ImageView icon=new ImageView(activity);icon.setImageDrawable(SidebarApps.icon(activity,pkg));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);put(row,icon,25,16,42,40);
        }
        private void chooseShortcut(String pkg){
            if(!pkg.isEmpty()&&!SidebarApps.launchable(activity,pkg)){toast(Ui.tr("Este app não está mais disponível.","This app is no longer available.","Esta app ya no está disponible."));render(12);return;}
            int focus=shortcutSlot;if(shortcutSlot<0)focus=SidebarApps.add(activity,pkg);else SidebarApps.assign(activity,shortcutSlot,pkg);render(11);rows.get(focus).requestFocus();
        }
        void locationPermission(boolean allowed){
            if(closed)return;
            if(allowed)AmbientWeather.prefs(activity).edit().putString("mode","auto").remove("cache").apply();
            render(9);if(!allowed)status.setText(L10n.text("Location permission is unavailable. You can choose a city."));
        }
        private void searchCities(){
            cityQuery=cityInput.getText().toString().trim();
            if(cityQuery.length()<2){status.setText(L10n.text("Enter at least two characters."));cityInput.requestFocus();return;}
            if(cityQuery.length()>100){status.setText(L10n.text("Enter a shorter search."));return;}
            ((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(cityInput.getWindowToken(),0);
            rows.get(0).requestFocus();status.setText(L10n.text("Searching for cities…"));
            if(searchJob!=null)searchJob.cancel(true);int epoch=++searchGeneration;String query=cityQuery;
            searchJob=io.submit(()->{try{List<AmbientWeather.Place> places=AmbientWeather.search(query);main.post(()->{
                if(closed||!active||page!=10||epoch!=searchGeneration)return;cityResults=places;render(10);
                if(places.isEmpty())status.setText(L10n.text("No results. Try a city name or add the country."));else rows.get(1).requestFocus();
            });}catch(Exception ignored){main.post(()->{if(!closed&&active&&page==10&&epoch==searchGeneration)status.setText(L10n.text("Search failed. Check your connection and try again."));});}});
        }
        private void note(String s,int y){TextView t=text(s,21,DIM,Ui.regular);t.setGravity(Gravity.TOP);t.setLineSpacing(7,1);t.setMaxLines(6);put(rowHost,t,6,contentY+18,1118,174);contentY+=210;}
        private String license(){StringBuilder s=new StringBuilder();for(String file:new String[]{"licenses/upstream-MIT.txt","licenses/device-icons.txt","licenses/simple-icons-CC0.txt","licenses/simple-icons-DISCLAIMER.txt","licenses/live-tv-sources.txt","licenses/tdtchannels-Apache-2.0.txt","ambient/sources.txt","fonts/OFL.txt","symbols/LICENSE-material-symbols.txt","brands/sources.txt","brands/JELLYFIN-ARTWORK-LICENSE.txt"})try(java.io.InputStream in=activity.getAssets().open(file);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);s.append(new String(out.toByteArray(),"UTF-8")).append("\n\n");}catch(Exception ignored){}return s.toString();}
        private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_SHORT).show();}
        private void launch(Intent i){try{activity.startActivity(i);}catch(ActivityNotFoundException|SecurityException e){toast(L10n.text("Could not open this option."));}}
        private void open(int page,int selected){returnRow=selected;render(page);}
        private boolean isVela(){return HomeMode.FREE.equals(state.optString("mode"))&&!state.optBoolean("homeReverted");}
        private boolean selectedAsHome(){
            Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolved=activity.getPackageManager().resolveActivity(intent,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return resolved!=null&&resolved.activityInfo!=null&&activity.getPackageName().equals(resolved.activityInfo.packageName);
        }
        private String mode(){return isVela()?"Vela":selectedAsHome()?L10n.text("Vela · finish setup"):"Original";}
        private boolean busy(){return !state.optString("busy").isEmpty()||pendingAt!=0;}
        private void switchHome(boolean personal){
            boolean recovery=state.optBoolean("switchPending")||!HomeMode.stringList(state,"disabled").isEmpty();
            if(!busy()&&(personal!=isVela()||(!personal&&recovery))){HomeMode.request(activity,personal?HomeMode.FREE:HomeMode.ORIGINAL);pendingAt=SystemClock.uptimeMillis();observedBusy=false;}
            render(0);readState();
        }
        void resume(){active=true;readState();if(updates!=null)updates.resume();}
        void pause(){active=false;if(updates!=null)updates.pause();searchGeneration++;if(searchJob!=null)searchJob.cancel(true);main.removeCallbacks(poll);}
        void destroy(){closed=true;pause();closeUpdates();io.shutdownNow();main.removeCallbacksAndMessages(null);}
        private void readState(){
            if(!active||closed||reading)return;reading=true;
            io.execute(()->{JSONObject fresh=HomeMode.state(activity);main.post(()->{
                reading=false;if(!active||closed)return;state=fresh;
                String message=state.optString("busy");
                if(!message.isEmpty())observedBusy=true;
                if(message.isEmpty()&&(observedBusy||SystemClock.uptimeMillis()-pendingAt>10_000))pendingAt=0;
                if(page==0&&!values.isEmpty())values.get(0).setText(busy()?"Aplicando…":mode());
                if(page==0||page==1)status.setText(!message.isEmpty()?message:state.optString("error"));
                if(busy())main.postDelayed(poll,500);
            });});
        }
        boolean onKey(KeyEvent e){
            View focusedView=activity.getCurrentFocus();int key=e.getKeyCode();
            if(key==KeyEvent.KEYCODE_DPAD_LEFT&&hasFocus()&&!(focusedView instanceof EditText)){if(e.getAction()==KeyEvent.ACTION_DOWN){if(surface.hasFocus())categories[categoryFor(page)].requestFocus();else navigation();}return true;}
            boolean root=false;for(int destination:destinations)root|=destination==page;
            if(key==KeyEvent.KEYCODE_BACK&&root&&surface.hasFocus()&&!activity.returnToSetup&&!activity.returnToCaller){if(e.getAction()==KeyEvent.ACTION_UP)categories[categoryFor(page)].requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_BACK&&root&&categories[categoryFor(page)].hasFocus()){if(e.getAction()==KeyEvent.ACTION_UP)activity.finish();return true;}

            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&page==21){if(e.getAction()==KeyEvent.ACTION_UP)render(14);return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&page==25){if(e.getAction()==KeyEvent.ACTION_UP){render(22);rows.get(3).requestFocus();}return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&page==24){if(e.getAction()==KeyEvent.ACTION_UP){render(17);rows.get(rows.size()-1).requestFocus();}return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&(page>=17&&page<=20)){if(e.getAction()==KeyEvent.ACTION_UP){int old=page;render(old==17?2:17);rows.get(old==17?3:old-17).requestFocus();}return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&(activity.returnToSetup||activity.returnToCaller)&&page==activity.entryPage){if(e.getAction()==KeyEvent.ACTION_UP)activity.finish();return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&(page==15||page==16)){if(e.getAction()==KeyEvent.ACTION_UP)render(page==16?15:14);return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&page!=0){if(e.getAction()==KeyEvent.ACTION_UP){if(page==5){render(4);rows.get(3).requestFocus();}else if(page==12){render(11);rows.get(shortcutSlot<0?SidebarApps.slots(activity).length:Math.min(shortcutSlot,rows.size()-1)).requestFocus();}else if(page==10){render(9);rows.get(1).requestFocus();}else if(page==7||page==8||page==9){int focus=page==9?4:page==8?6:0;render(6);rows.get(focus).requestFocus();}else{int parent=destinations[categoryFor(page)];render(parent);categories[categoryFor(parent)].requestFocus();}}return true;}
            return false;
        }
        private static final class CategoryIcon extends View {
            final int kind;final Paint paint=new Paint(3);int color=WHITE;
            CategoryIcon(Context c,int kind){super(c);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
            void color(int value){color=value;invalidate();}
            protected void onDraw(Canvas c){c.save();c.scale(getWidth()/32f,getHeight()/32f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.2f);paint.setStrokeCap(Paint.Cap.ROUND);
                if(kind==0){c.drawRoundRect(4,7,28,24,3,3,paint);c.drawLine(16,24,16,28,paint);c.drawLine(11,28,21,28,paint);}
                else if(kind==1){c.drawCircle(16,16,11,paint);c.save();c.clipRect(16,0,32,32);paint.setStyle(Paint.Style.FILL);c.drawCircle(16,16,9,paint);c.restore();}
                else if(kind==2){c.drawRoundRect(4,5,28,27,4,4,paint);c.drawCircle(21,12,2,paint);Path p=new Path();p.moveTo(6,24);p.lineTo(13,15);p.lineTo(19,22);p.lineTo(23,18);p.lineTo(27,24);c.drawPath(p,paint);}
                else if(kind==3){c.drawCircle(16,10,5,paint);c.drawArc(6,17,26,35,190,160,false,paint);}
                else if(kind==4){paint.setStyle(Paint.Style.FILL);for(int y=7;y<=25;y+=9)for(int x=7;x<=25;x+=9)c.drawCircle(x,y,2,paint);}
                else if(kind==5){c.drawArc(5,5,27,27,35,300,false,paint);c.drawLine(25,4,25,11,paint);c.drawLine(25,11,18,9,paint);}
                else if(kind==6){for(int i=0;i<3;i++){int y=8+i*8,x=i==1?22:11;c.drawLine(5,y,27,y,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,3.5f,paint);paint.setStyle(Paint.Style.STROKE);}}
                else{c.drawCircle(16,16,11,paint);c.drawLine(16,15,16,23,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(16,9.5f,1.5f,paint);}c.restore();
            }
        }
    }
}
