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
public class SettingsActivity extends Activity {
    private SettingsView view;
    private AmbientView preview;
    private boolean returnToSetup;
    private int entryPage;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);Watchdog.install(this);Ui.init(this);
        if(!getIntent().getBooleanExtra("fromSetup",false)&&Personalization.launchIfNeeded(this)){finish();return;}
        if(HomeMode.state(this).optBoolean("switchPending"))HomeMode.verify(this);
        view=new SettingsView(this);setContentView(view);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawable(null);
    }
    @Override protected void onResume() {
        super.onResume();if(view==null||Updates.resumeInstallation(this))return;view.resume();
        if(getIntent().hasExtra("page")){
            int target=getIntent().getIntExtra("page",0);getIntent().removeExtra("page");
            returnToSetup=getIntent().getBooleanExtra("fromSetup",false);getIntent().removeExtra("fromSetup");
            if(target==1||target==3||target==4||target==9||target==11||target==14||target==15||target==17||target==21){entryPage=target;view.render(target);}
        }
        else if(view.page==14||view.page==15||view.page==21)view.render(view.page);
        if(getIntent().getBooleanExtra("plex",false)){getIntent().removeExtra("plex");startActivity(new Intent(this,PlexLinkActivity.class));}
        if(preview==null&&getIntent().getBooleanExtra("screensaver",false)){getIntent().removeExtra("screensaver");showPreview();}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
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
    void showPreview(){view.pause();preview=new AmbientView(this,true);setContentView(preview);preview.start();}
    private void closePreview(){if(preview==null)return;preview.stop();preview=null;setContentView(view);view.resume();}

    static final class SettingsView extends FrameLayout {
        private static final int WHITE=0xFFF7F7FA,DIM=0xFFA5A9B8,INK=0xFF171A23;
        private final SettingsActivity activity;
        private final FrameLayout stage,pane;
        private FrameLayout rowHost;
        private final TextView heading,detail,footer,status;
        private final Handler main=new Handler(Looper.getMainLooper());
        private final ExecutorService io=Visuals.worker(1);
        private final List<TextView> labels=new ArrayList<>(),values=new ArrayList<>();
        private final List<FrameLayout> rows=new ArrayList<>();
        private JSONObject state=new JSONObject();
        private boolean active,closed,reading,observedBusy;
        private long pendingAt;
        private int page,returnRow,searchGeneration;
        private int shortcutSlot,pickerFocus;
        private EditText cityInput;
        private String cityQuery="";
        private List<AmbientWeather.Place> cityResults=Collections.emptyList();
        private java.util.concurrent.Future<?> searchJob;
        private final Runnable poll=()->readState();
        SettingsView(SettingsActivity a){
            super(a);activity=a;setBackgroundColor(0xFF11141D);setClipChildren(false);
            stage=new FrameLayout(a);stage.setClipChildren(false);addView(stage,new LayoutParams(1920,1080));
            View glow=new View(a);GradientDrawable gradient=new GradientDrawable();gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);gradient.setGradientCenter(.22f,.42f);gradient.setGradientRadius(950);gradient.setColors(new int[]{0xFF303D50,0xFF161B27,0xFF11141D});glow.setBackground(gradient);put(stage,glow,0,0,1920,1080);
            TextView brand=text("VELA",24,0xFFB9C5D6,Ui.semibold);brand.setLetterSpacing(.22f);put(stage,brand,116,66,350,42);
            TextView title=text("Ajustes",60,WHITE,Ui.displaySemibold);put(stage,title,112,132,660,86);
            ImageView mark=new ImageView(a);mark.setImageResource(R.drawable.vela_mark);put(stage,mark,220,318,184,184);
            heading=text("Tu televisión.",43,WHITE,Ui.displaySemibold);heading.setMaxLines(2);put(stage,heading,116,570,650,114);
            detail=text("Todo a tu manera.",24,0xFFBAC1D0,Ui.regular);detail.setGravity(Gravity.TOP);detail.setMaxLines(4);detail.setLineSpacing(7,1);put(stage,detail,116,704,620,146);
            footer=text("Vela · "+version(),19,0xFF7F899E,Ui.medium);put(stage,footer,116,970,610,40);
            View divider=new View(a);divider.setBackgroundColor(0x15FFFFFF);put(stage,divider,855,136,1,806);
            pane=new FrameLayout(a);pane.setClipChildren(false);put(stage,pane,936,130,840,850);
            status=text("",19,0xFFB8C8DF,Ui.regular);status.setMaxLines(2);put(stage,status,936,981,840,68);
            render(0);
        }
        private String version(){try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception e){return "1.0.0";}}
        private TextView text(String s,int size,int color,Typeface font){TextView t=new TextView(activity);t.setText(s);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);t.setTextColor(color);t.setTypeface(font);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
        private void put(FrameLayout parent,View view,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(view,p);}
        private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(17);return d;}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){float scale=Math.min(w/1920f,h/1080f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(scale);stage.setScaleY(scale);stage.setTranslationX((w-1920*scale)/2);stage.setTranslationY((h-1080*scale)/2);}
        private void section(String s,int y){TextView h=text(s,18,0xFF7F8AA1,Ui.semibold);h.setLetterSpacing(.08f);put(pane,h,20,y,780,30);}
        private void row(String label,String value,int y,String title,String description,Runnable action){row(label,value,"",y,title,description,action);}
        private void row(String label,String value,String secondary,int y,String title,String description,Runnable action){
            final boolean scrolling=rowHost!=pane;
            FrameLayout row=new FrameLayout(activity);row.setId(View.generateViewId());row.setFocusable(true);row.setFocusableInTouchMode(true);row.setClickable(true);row.setDefaultFocusHighlightEnabled(false);row.setContentDescription(label+(value.isEmpty()?"":", "+value));row.setClipChildren(false);
            int height=secondary.isEmpty()?72:106;put(rowHost,row,0,y,840,height);
            TextView name=text(label,27,WHITE,Ui.medium);put(row,name,26,secondary.isEmpty()?0:17,510,secondary.isEmpty()?72:36);
            TextView right=text(value,23,DIM,Ui.regular);right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);right.setSingleLine(true);right.setEllipsize(android.text.TextUtils.TruncateAt.END);put(row,right,548,0,264,height);
            TextView sub=text(secondary,19,DIM,Ui.regular);if(!secondary.isEmpty())put(row,sub,26,58,720,32);
            rows.add(row);labels.add(name);values.add(right);
            row.setOnFocusChangeListener((v,focused)->{
                row.setBackground(focused?round(Appearance.accent(activity)):null);name.setTextColor(focused?INK:WHITE);right.setTextColor(focused?0xFF60636C:DIM);sub.setTextColor(focused?0xFF656874:DIM);
                row.animate().cancel();float scale=focused&&!scrolling&&!Visuals.reducedMotion(activity)?1.018f:1;
                row.animate().scaleX(scale).scaleY(scale).translationZ(focused?14:0).setDuration(Visuals.duration(activity,140)).start();
                if(focused){heading.setText(title);detail.setText(description);}
            });
            row.setOnClickListener(v->action.run());
        }
        void render(int next){
            if(page==10&&next!=10){searchGeneration++;if(searchJob!=null)searchJob.cancel(true);}
            page=next;pane.removeAllViews();rowHost=pane;rows.clear();labels.clear();values.clear();status.setText("");pickerFocus=0;
            if(page==0){
                section("TU TELEVISIÓN",0);
                row("Pantalla de inicio",mode(),42,"Tu televisión, a tu manera.","Elige Vela como inicio o vuelve en cualquier momento al inicio original de tu televisión.",()->open(1,0));
                row("Salvapantallas",AmbientPrefs.styleName(activity),122,"Un paisaje para cada pausa.","Elige vídeos aéreos o los fondos de tu biblioteca de Plex. Añade la hora y el tiempo de tu ciudad, y pruébalo aquí.",()->open(6,1));
                row("Ajustes de la TV","›",202,"Tu televisión, a mano.","Imagen, sonido, red, Bluetooth y todos los controles de la televisión.",()->launch(new Intent(Settings.ACTION_SETTINGS)));
                section("TU CONTENIDO",320);
                row(Ui.tr("Contas e serviços","Accounts and services","Cuentas y servicios"),"›",362,Ui.tr("Seu conteúdo, conectado.","Your content, connected.","Tu contenido, conectado."),Ui.tr("Gerencie Plex, o servidor e o acesso aos apps de vídeo desta TV.","Manage Plex, your server and access to this TV's video apps.","Gestiona Plex, tu servidor y el acceso a las apps de vídeo de esta televisión."),()->open(14,3));
                row("YouTube","›",442,"Tus vídeos en la TV.","Permite las filas que YouTube comparte con la televisión. Los recomendados personales y Ver más tarde se abren en YouTube.",()->open(3,4));
                section("VELA",560);
                row(Ui.tr("Acessos do menu","Menu shortcuts","Accesos del menú"),"›",602,Ui.tr("Seu menu, seus apps.","Your menu, your apps.","Tu menú, tus apps."),Ui.tr("Adicione, altere ou oculte apps. Para ordenar diretamente na barra, mantenha OK sobre um ícone e mova-o com as setas.","Add, change or hide apps. To reorder directly in the sidebar, hold OK on an icon and move it with the arrows.","Añade, cambia u oculta apps. Para ordenarlas directamente en la barra, mantén OK sobre un icono y muévelo con las flechas."),()->open(11,5));
                row(Ui.tr("Aparência e desempenho","Appearance and performance","Apariencia y rendimiento"),Visuals.light(activity)?"Ligera":"Alta calidad",682,"Bonito. Y ligero.","Conserva la calidad de las imágenes o elige un consumo menor. Vela libera sus imágenes cuando abres otra aplicación.",()->open(2,6));
                row(Ui.tr("Informações e atualizações","Information and updates","Información y actualizaciones"),"›",762,"Hecha para tu televisión.",Ui.tr("Versão, atualizações e licenças do Vela.","Vela version, updates and licenses.","Versión, actualizaciones y licencias de Vela."),()->open(4,7));
            }else if(page==1){
                section("PANTALLA DE INICIO",0);
                row("Vela",isVela()?"✓":"Activar","Inicio, salvapantallas y recuperación.",54,"Tu inicio personal.","Activa Vela y su salvapantallas. La app guarda el retorno al inicio original de la televisión antes de completar la configuración.",()->switchHome(true));
                row("Original",!isVela()?"✓":"","El inicio de fábrica de tu televisión.",178,"Siempre puedes volver.","Restaura el inicio y el salvapantallas originales de tu televisión.",()->switchHome(false));
                note("El cambio puede tardar unos segundos. Si la TV pide autorización, marca Permitir siempre y acepta con el mando.",338);
            }else if(page==2){
                section("IMAGEN Y RENDIMIENTO",0);
                row("Calidad de imagen",Visuals.light(activity)?"Ligera":"Alta",54,"La imagen que prefieras.","Alta conserva los fondos en Full HD. Ligera usa fondos de 720p y prescinde del desenfoque del menú. Los carteles siguen completos.",()->{Visuals.prefs(activity).edit().putBoolean("light",!Visuals.light(activity)).apply();render(2);});
                row("Reducir movimiento",Visuals.reducedMotion(activity)?"Sí":"No",134,"Un foco más tranquilo.","Elimina el aumento y las transiciones de selección. También se respeta la desactivación de animaciones de Android.",()->{Visuals.prefs(activity).edit().putBoolean("reducedMotion",!Visuals.reducedMotion(activity)).apply();render(2);rows.get(1).requestFocus();});
                section("MEMORIA",270);
                Debug.MemoryInfo memory=new Debug.MemoryInfo();Debug.getMemoryInfo(memory);
                row("Memoria del proceso",String.format(Ui.locale(),"%.1f MB",memory.getTotalPss()/1024f),312,"Una lectura real.","Memoria compartida proporcional de Inicio y Ajustes en este instante. Incluye Android y gráficos; varía con las imágenes y el dispositivo. Pulsa para actualizar.",()->{render(2);rows.get(2).requestFocus();});
                note("Caché de imágenes: hasta "+Visuals.cacheKb(activity)/1024+" MB. Se vacía al salir de Inicio. No se reproducen vídeos ni se animan fondos en el inicio.",440);
                row(Ui.tr("Personalizar aparência","Customize appearance","Personalizar apariencia"),"›",684,Ui.tr("Seu estilo.","Your style.","Tu estilo."),Ui.tr("Escolha cor, tamanho dos cartazes e intensidade do fundo. O desenho original fica disponível.","Choose color, poster size and background intensity. The original design stays available.","Elige color, tamaño de carteles e intensidad del fondo. El diseño original sigue disponible."),()->open(17,3));
            }else if(page==17){
                section(Ui.tr("APARÊNCIA","APPEARANCE","APARIENCIA"),0);
                row(Ui.tr("Cor de destaque","Accent color","Color de acento"),Appearance.colorName(Appearance.colorIndex(activity)),54,Ui.tr("Um toque de cor.","A touch of color.","Un toque de color."),Ui.tr("A cor aparece na seleção do menu, na ação principal, no progresso e nos Ajustes.","Color appears in menu selection, the main action, progress and Settings.","El color aparece en la selección del menú, la acción principal, el progreso y los Ajustes."),()->render(18));
                row(Ui.tr("Tamanho dos cartazes","Poster size","Tamaño de carteles"),Appearance.sizeName(Appearance.sizeIndex(activity)),134,Ui.tr("Seu cinema, na sua medida.","Your cinema, your size.","Tu cine, a tu medida."),Ui.tr("Três tamanhos com a proporção original. As páginas continuam limitadas a 24 títulos.","Three sizes with the original aspect ratio. Pages remain limited to 24 titles.","Tres tamaños con su proporción original. Las páginas siguen limitadas a 24 títulos."),()->render(19));
                row(Ui.tr("Intensidade do fundo","Background intensity","Intensidad del fondo"),Appearance.backdropName(Appearance.backdropIndex(activity)),214,Ui.tr("O ambiente que você quiser.","Set the atmosphere.","El ambiente que quieras."),Ui.tr("Escureça o fundo para ler melhor ou deixe mais visível a imagem original.","Dim the background for reading or reveal more of the original artwork.","Oscurece el fondo para leer mejor o deja más visible la imagen original."),()->render(20));
                row(Ui.tr("Restaurar aparência","Reset appearance","Restaurar apariencia"),"›",374,Ui.tr("Voltar ao desenho original.","Return to the original design.","Volver al diseño original."),Ui.tr("Restaura apenas cor, cartazes e fundo. Conserva contas, apps, clima e desempenho.","Resets only color, posters and background. Keeps accounts, apps, weather and performance.","Restaura solo color, carteles y fondo. Conserva cuentas, apps, clima y rendimiento."),()->{Appearance.reset(activity);render(17);rows.get(3).requestFocus();});
                note(Ui.tr("As mudanças aparecem ao voltar ao início. Sem fundos adicionais, vídeos ou animações permanentes.","Changes appear when you return home. No additional backgrounds, videos or continuous animations.","Los cambios aparecen al volver al inicio. Sin fondos adicionales, vídeos ni animaciones permanentes."),520);
            }else if(page==18||page==19||page==20){
                String key=page==18?"color":page==19?"size":"backdrop";int current=page==18?Appearance.colorIndex(activity):page==19?Appearance.sizeIndex(activity):Appearance.backdropIndex(activity),count=page==18?6:3;
                section(page==18?Ui.tr("COR DE DESTAQUE","ACCENT COLOR","COLOR DE ACENTO"):page==19?Ui.tr("CARTAZES","POSTERS","CARTELES"):Ui.tr("FUNDO","BACKGROUND","FONDO"),0);
                for(int i=0;i<count;i++){final int choice=i;String label=page==18?Appearance.colorName(i):page==19?Appearance.sizeName(i):Appearance.backdropName(i);row(label,i==current?"✓":"",54+i*80,label,Ui.tr("Escolha com OK. Voltar retorna à aparência.","Choose with OK. Back returns to appearance.","Elige con OK. Atrás vuelve a apariencia."),()->{Appearance.set(activity,key,choice);int same=page;render(same);rows.get(choice).requestFocus();});}
            }else if(page==3){
                section("YOUTUBE",0);
                boolean allowed=activity.checkSelfPermission(TvSuggestions.PERMISSION)==PackageManager.PERMISSION_GRANTED;
                row("Filas de la televisión",allowed?"Permitidas":"Activar",54,"Lo que comparte YouTube.","La disponibilidad de vídeos depende de lo que la aplicación de YouTube publique en tu televisión. No solicita tu contraseña.",()->{if(!allowed)activity.requestPermissions(new String[]{TvSuggestions.PERMISSION},80);else toast("El acceso ya está permitido.");});
                row("Abrir YouTube","›",134,"Vuelve a tus vídeos.","Abre YouTube para ver tus recomendados personales, suscripciones y la lista Ver más tarde.",()->{Intent i=HomeApps.launchIntent(activity.getPackageManager(),TvSuggestions.YOUTUBE);if(i!=null)launch(i);else toast("YouTube no está instalado.");});
            }else if(page==4){
                section("INFORMACIÓN",0);
                row("Vela",version(),54,"Vela.","Tu biblioteca y tus aplicaciones, en una pantalla pensada para el mando de tu televisión.",()->{});
                row("Televisión",Build.MANUFACTURER+" · "+Build.MODEL,134,"Tu dispositivo.","Los datos corresponden al dispositivo donde está instalada Vela.",()->{});
                row("Android",Build.VERSION.RELEASE,214,"El sistema de tu TV.","Los ajustes de imagen, sonido, red y cuentas se gestionan desde Ajustes de la TV.",()->{});
                row("Licencias","›",334,"Créditos y licencias.","Avisos de los componentes de código abierto, tipografías y símbolos utilizados en Vela.",()->open(5,7));
                row(Ui.tr("Assistente de configuração","Setup guide","Asistente de configuración"),"›",434,Ui.tr("Tudo a seu gosto.","Make it yours.","Todo a tu gusto."),Ui.tr("Revise contas, apps e localização sem apagar sua configuração.","Review accounts, apps and location without erasing your setup.","Repasa cuentas, apps y ubicación sin borrar tu configuración."),()->activity.startActivity(new Intent(activity,SetupActivity.class)));
                row(Ui.tr("Atualizações","Updates","Actualizaciones"),"›",534,Ui.tr("Sempre em dia.","Always up to date.","Siempre al día."),Ui.tr("Novas versões do GitHub, com verificação automática e instalação confirmada por você.","New GitHub releases, with automatic checks and installation confirmed by you.","Nuevas versiones de GitHub, con comprobación automática e instalación confirmada por ti."),()->activity.startActivity(new Intent(activity,UpdatesActivity.class)));
            }else if(page==6){
                section("SALVAPANTALLAS",0);
                row("Estilo",AmbientPrefs.styleName(activity),54,"Una pausa a tu gusto.","Aéreos muestra vídeos de paisajes y ciudades. Plex o Jellyfin usan las imágenes originales de tu biblioteca con movimiento suave.",()->render(7));
                row("Vista previa","Abrir",134,"Así se verá en tu tele.","La vista previa utiliza el estilo y los ajustes que elijas. Cualquier flecha, OK o Atrás cierra la vista previa.",()->activity.showPreview());
                row("Hora",AmbientPrefs.clock(activity)?"Visible":"Oculta",214,"La hora, de un vistazo.","El reloj permanece visible en los dos estilos. Se actualiza al cambiar de minuto, sin animaciones continuas.",()->{AmbientPrefs.get(activity).edit().putBoolean("clock",!AmbientPrefs.clock(activity)).apply();render(6);rows.get(2).requestFocus();});
                row("Tiempo local",AmbientPrefs.weather(activity)?"Visible":"Oculto",294,"Tu tiempo, en tu pantalla.","Temperatura y estado del cielo junto a la hora. Se consultan al entrar y se reutilizan durante treinta minutos.",()->{AmbientPrefs.get(activity).edit().putBoolean("weather",!AmbientPrefs.weather(activity)).apply();render(6);rows.get(3).requestFocus();});
                row("Ubicación",AmbientWeather.name(activity),374,"El tiempo de donde estás.","Usa la ubicación aproximada de la televisión o busca una ciudad. Si tu televisión no detecta su ubicación, puedes elegirla con el mando.",()->render(9));
                row("Texto en pantalla",AmbientPrefs.captions(activity)?"Discreto":"Oculto",454,"La imagen es protagonista.","El lugar o el título aparece durante ocho segundos. La hora y el tiempo permanecen visibles si los activas.",()->{AmbientPrefs.get(activity).edit().putBoolean("captions",!AmbientPrefs.captions(activity)).apply();render(6);rows.get(5).requestFocus();});
                if(AmbientPrefs.plex(activity))row("Cambiar imagen",AmbientPrefs.seconds(activity)==30?"30 segundos":AmbientPrefs.seconds(activity)==60?"1 minuto":"2 minutos",534,"A tu ritmo.","Elige cuánto dura cada fondo de Plex. Si reduces el movimiento en Imagen y rendimiento, las fotos se muestran sin zoom.",()->{int n=AmbientPrefs.seconds(activity);AmbientPrefs.get(activity).edit().putInt("seconds",n==30?60:n==60?120:30).apply();render(6);rows.get(6).requestFocus();});
                else row("Tema",AmbientPrefs.themeName(activity),534,"Paisajes o ciudades.","Alterna todos los vídeos o elige paisajes naturales o ciudades. Se reproducen en silencio y requieren Internet.",()->render(8));
                row("Activación en la TV","›",654,"Cuando tu tele descanse.","El tiempo de espera y la activación dependen de tu televisión. Abre los ajustes de salvapantallas de Android para elegirlos.",()->{Intent intent=new Intent(Settings.ACTION_DREAM_SETTINGS);if(intent.resolveActivity(activity.getPackageManager())==null)intent=new Intent(Settings.ACTION_SETTINGS);launch(intent);});
                note("El tiempo usa Open-Meteo. Si no hay conexión, muestra los últimos datos hasta tres horas. Sin ubicación configurada, elige tu ciudad para activarlo.",758);
            }else if(page==7){
                section("ESTILO DE SALVAPANTALLAS",0);
                row("Aéreos",!AmbientPrefs.plex(activity)?"✓":"","Paisajes y ciudades en vídeo.",54,"Paisajes en movimiento.",Ui.tr("Paisagens e cidades em movimento, em tela cheia e sem som. Requer Internet.","Landscapes and cities in motion, full screen and silent. Internet required.","Paisajes y ciudades en movimiento, a pantalla completa y sin sonido. Requiere Internet."),()->{AmbientPrefs.get(activity).edit().putString("style","aerial").apply();render(6);});
                row(Ui.tr("Sua biblioteca","Your library","Tu biblioteca"),AmbientPrefs.plex(activity)?"✓":"","Tus fondos originales con un zoom suave.",178,"Tu cine también descansa.",Ui.tr("Seus filmes e séries preenchem cada pausa com suas imagens originais.","Your movies and series fill each pause with their original artwork.","Tus películas y series llenan cada pausa con sus fondos originales."),()->{AmbientPrefs.get(activity).edit().putString("style","plex").apply();render(6);});
            }else if(page==8){
                section("TEMA DE LOS AÉREOS",0);
                String[] themes={"all","landscape","city"},names={"Todos","Paisajes","Ciudades"};
                for(int i=0;i<themes.length;i++){final String theme=themes[i];row(names[i],theme.equals(AmbientPrefs.theme(activity))?"✓":"",54+i*84,"Elige dónde viajar.","Vídeos reales de costas, montañas y ciudades. No se reproducen vídeos fuera del salvapantallas.",()->{AmbientPrefs.get(activity).edit().putString("theme",theme).apply();render(6);rows.get(6).requestFocus();});}
            }else if(page==9){
                section("UBICACIÓN DEL TIEMPO",0);
                boolean allowed=activity.checkSelfPermission(AmbientWeather.PERMISSION)==PackageManager.PERMISSION_GRANTED;
                row("Ubicación automática","auto".equals(AmbientWeather.mode(activity))&&allowed?"✓":allowed?"Activar":"Permitir",54,"El tiempo de tu televisión.","Solo necesita ubicación aproximada mientras usas Vela. Si la TV no obtiene una ubicación, se conserva tu ciudad manual como alternativa.",()->{if(!allowed)activity.requestPermissions(new String[]{AmbientWeather.PERMISSION},81);else locationPermission(true);});
                AmbientWeather.Place place=AmbientWeather.manual(activity);
                row("Elegir ciudad",place==null?"Buscar":place.name,134,"Tu ciudad, siempre a mano.","Busca el nombre de tu ciudad o su código postal. Puedes añadir el país para afinar los resultados. No necesita permiso de ubicación.",()->{cityQuery=place==null?"":place.name;cityResults=Collections.emptyList();render(10);});
                note("Open-Meteo recibe coordenadas aproximadas para consultar el tiempo. Al buscar una ciudad, recibe el texto de la búsqueda. Vela no solicita ubicación precisa ni acceso en segundo plano.",290);
                note("Si Android tiene la ubicación desactivada o tu televisión no la facilita, elige una ciudad. Datos meteorológicos de Open-Meteo; búsqueda de lugares basada en GeoNames.",480);
            }else if(page==10){
                section("BUSCAR CIUDAD",0);
                cityInput=new EditText(activity);cityInput.setId(View.generateViewId());cityInput.setText(cityQuery);cityInput.setSelection(cityInput.length());cityInput.setHint("Ciudad o código postal");cityInput.setTextSize(TypedValue.COMPLEX_UNIT_PX,27);cityInput.setTextColor(WHITE);cityInput.setHintTextColor(DIM);cityInput.setSingleLine(true);cityInput.setPadding(26,0,26,0);cityInput.setBackground(round(0xFF272F40));cityInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);cityInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);put(pane,cityInput,0,54,840,74);
                cityInput.setOnFocusChangeListener((v,focus)->{cityInput.setBackground(round(focus?0xFF38465C:0xFF272F40));if(focus){heading.setText("El tiempo de tu ciudad.");detail.setText("Escribe con el mando y pulsa Buscar. Elige el lugar correcto entre los resultados.");}});
                cityInput.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){searchCities();return true;}return false;});
                row("Buscar","›",146,"Encuentra tu ciudad.","Busca una ciudad o código postal. Si hay varias con el mismo nombre, elige la provincia y el país correctos.",this::searchCities);
                cityInput.setNextFocusDownId(rows.get(0).getId());rows.get(0).setNextFocusUpId(cityInput.getId());
                for(int i=0;i<cityResults.size();i++){final AmbientWeather.Place place=cityResults.get(i);row(place.name,"Elegir",place.label,242+i*112,"El tiempo de "+place.name+".",place.label,()->{AmbientWeather.select(activity,place);if(activity.returnToSetup){activity.finish();return;}render(6);rows.get(4).requestFocus();});}
                if(cityResults.isEmpty())note("Pulsa en el campo para escribir con el teclado de la televisión. Puedes cambiar la ciudad cuando quieras.",276);
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
                row(Ui.tr("TV em direto","Live TV","TV en directo"),"›",554,Ui.tr("Seus canais, no Vela.","Your channels, in Vela.","Tus canales, dentro de Vela."),Ui.tr("M3U, Xtream e antena. Canais, favoritos e programação em uma interface nativa.","M3U, Xtream and antenna. Channels, favorites and listings in a native interface.","M3U, Xtream y antena. Canales, favoritos y programación en una interfaz nativa."),()->activity.startActivity(new Intent(activity,LiveTvActivity.class).putExtra("sources",true)));
                row(Ui.tr("Biblioteca do início","Home library","Biblioteca de Inicio"),new LibraryClient(activity).provider(),654,Ui.tr("Escolha sua biblioteca.","Choose your library.","Elige tu biblioteca."),Ui.tr("Escolha Plex ou Jellyfin para o início, catálogo e fundos do protetor de tela. As duas contas são mantidas.","Choose Plex or Jellyfin for home, catalog and library screensaver artwork. Both authorizations are kept.","Elige Plex o Jellyfin para Inicio, catálogo y fondos del salvapantallas. Se conservan las dos autorizaciones."),()->render(21));
            }else if(page==21){
                section(Ui.tr("BIBLIOTECA DO INÍCIO","HOME LIBRARY","BIBLIOTECA DE INICIO"),0);LibraryClient library=new LibraryClient(activity);PlexClient plex=new PlexClient(activity);JellyfinClient jellyfin=new JellyfinClient(activity);
                row("Plex",plex.connected()?(library.isJellyfin()?plex.name():"✓ "+plex.name()):Ui.tr("Conectar","Connect","Conectar"),54,"Plex",Ui.tr("Usa esta biblioteca sem desconectar Jellyfin.","Use this library without unlinking Jellyfin.","Usa esta biblioteca sin desvincular Jellyfin."),()->{if(plex.connected()){LibraryClient.activate(activity,"Plex");render(21);}else activity.startActivity(new Intent(activity,PlexLinkActivity.class));});
                row("Jellyfin",jellyfin.connected()?(library.isJellyfin()?"✓ "+jellyfin.name():jellyfin.name()):Ui.tr("Conectar","Connect","Conectar"),154,"Jellyfin",Ui.tr("Usa esta biblioteca sem desconectar Plex.","Use this library without unlinking Plex.","Usa esta biblioteca sin desvincular Plex."),()->{if(jellyfin.connected()){LibraryClient.activate(activity,"Jellyfin");render(21);}else activity.startActivity(new Intent(activity,JellyfinLinkActivity.class));});
                note(Ui.tr("A troca é aplicada ao voltar ao início. Cada servidor mantém seu usuário, progresso e imagens.","Switching applies when you return home. Each server keeps its user, progress and artwork.","El cambio se aplica al volver a Inicio. Cada servidor mantiene su usuario, progreso e imágenes."),310);
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
                section("LICENCIAS DE CÓDIGO ABIERTO",0);
                ScrollView scroll=new ScrollView(activity);scroll.setFocusable(true);scroll.setVerticalScrollBarEnabled(false);put(pane,scroll,16,60,808,735);
                TextView legal=text(license(),18,0xFFCBD0DD,Ui.regular);legal.setGravity(Gravity.TOP);legal.setLineSpacing(5,1);scroll.addView(legal);scroll.requestFocus();
                heading.setText("Gracias al código abierto.");detail.setText("Vela conserva los avisos exigidos por las licencias de sus componentes.");
            }
            if(page==11||page==12)for(int i=0;i<rows.size();i++){if(i>0)rows.get(i).setNextFocusUpId(rows.get(i-1).getId());if(i+1<rows.size())rows.get(i).setNextFocusDownId(rows.get(i+1).getId());}
            if(page==10&&cityResults.isEmpty())cityInput.requestFocus();else if(!rows.isEmpty())rows.get(page==12?pickerFocus:page==18?Appearance.colorIndex(activity):page==19?Appearance.sizeIndex(activity):page==20?Appearance.backdropIndex(activity):0).requestFocus();
        }
        private void shortcutScroll(int count){
            ScrollView scroll=new ScrollView(activity);scroll.setClipToPadding(true);scroll.setClipBounds(new Rect(0,0,856,776));scroll.setVerticalScrollBarEnabled(false);scroll.setPadding(8,8,8,14);put(pane,scroll,-8,46,856,776);
            rowHost=new FrameLayout(activity);rowHost.setClipChildren(false);scroll.addView(rowHost,new ScrollView.LayoutParams(840,count*84+20));
        }
        private void providerRow(String name,String pkg,int y){
            row(name,SidebarApps.launchable(activity,pkg)?Ui.tr("Abrir","Open","Abrir"):Ui.tr("Não instalado","Not installed","No instalada"),y,name,Ui.tr("Entre ou troque de conta no próprio app. O acesso do menu abre a sessão dessa pessoa.","Sign in or change account in the app itself. The menu shortcut opens that person's session.","Inicia sesión o cambia de cuenta dentro de su app. El acceso del menú abre la sesión de esa persona."),()->{Intent intent=HomeApps.launchIntent(activity.getPackageManager(),pkg);if(intent!=null)launch(intent);else toast(Ui.tr("Instale o app na loja da TV.","Install the app from the TV store.","Instala la app desde la tienda de la televisión."));});
        }
        private void shortcutIcon(String pkg){
            if(pkg.isEmpty())return;
            FrameLayout row=rows.get(rows.size()-1);TextView label=labels.get(labels.size()-1);
            LayoutParams position=(LayoutParams)label.getLayoutParams();position.leftMargin=86;position.width=446;label.setLayoutParams(position);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ImageView icon=new ImageView(activity);icon.setImageDrawable(SidebarApps.icon(activity,pkg));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);put(row,icon,25,16,42,40);
        }
        private void chooseShortcut(String pkg){
            if(!pkg.isEmpty()&&!SidebarApps.launchable(activity,pkg)){toast(Ui.tr("Este app não está mais disponível.","This app is no longer available.","Esta app ya no está disponible."));render(12);return;}
            int focus=shortcutSlot;if(shortcutSlot<0)focus=SidebarApps.add(activity,pkg);else SidebarApps.assign(activity,shortcutSlot,pkg);render(11);rows.get(focus).requestFocus();
        }
        void locationPermission(boolean allowed){
            if(closed)return;
            if(allowed)AmbientWeather.prefs(activity).edit().putString("mode","auto").remove("cache").apply();
            render(9);if(!allowed)status.setText("Sin permiso de ubicación. Puedes elegir una ciudad.");
        }
        private void searchCities(){
            cityQuery=cityInput.getText().toString().trim();
            if(cityQuery.length()<2){status.setText("Escribe al menos dos caracteres.");cityInput.requestFocus();return;}
            if(cityQuery.length()>100){status.setText("Escribe una búsqueda más corta.");return;}
            ((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(cityInput.getWindowToken(),0);
            rows.get(0).requestFocus();status.setText("Buscando ciudades…");
            if(searchJob!=null)searchJob.cancel(true);int epoch=++searchGeneration;String query=cityQuery;
            searchJob=io.submit(()->{try{List<AmbientWeather.Place> places=AmbientWeather.search(query);main.post(()->{
                if(closed||!active||page!=10||epoch!=searchGeneration)return;cityResults=places;render(10);
                if(places.isEmpty())status.setText("No hay resultados. Prueba el nombre de la ciudad o añade el país.");else rows.get(1).requestFocus();
            });}catch(Exception ignored){main.post(()->{if(!closed&&active&&page==10&&epoch==searchGeneration)status.setText("No se ha podido buscar. Comprueba Internet y vuelve a intentarlo.");});}});
        }
        private void note(String s,int y){TextView t=text(s,21,DIM,Ui.regular);t.setGravity(Gravity.TOP);t.setLineSpacing(7,1);put(pane,t,26,y,780,210);}
        private String license(){StringBuilder s=new StringBuilder();for(String file:new String[]{"licenses/upstream-MIT.txt","licenses/live-tv-sources.txt","licenses/tdtchannels-Apache-2.0.txt","ambient/sources.txt","fonts/OFL.txt","symbols/LICENSE-material-symbols.txt","brands/sources.txt","brands/JELLYFIN-ARTWORK-LICENSE.txt"})try(java.io.InputStream in=activity.getAssets().open(file);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);s.append(new String(out.toByteArray(),"UTF-8")).append("\n\n");}catch(Exception ignored){}return s.toString();}
        private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_SHORT).show();}
        private void launch(Intent i){try{activity.startActivity(i);}catch(ActivityNotFoundException|SecurityException e){toast("No se ha podido abrir esta opción.");}}
        private void open(int page,int selected){returnRow=selected;render(page);}
        private boolean isVela(){return HomeMode.FREE.equals(state.optString("mode"))&&!state.optBoolean("homeReverted");}
        private boolean selectedAsHome(){
            Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolved=activity.getPackageManager().resolveActivity(intent,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return resolved!=null&&resolved.activityInfo!=null&&activity.getPackageName().equals(resolved.activityInfo.packageName);
        }
        private String mode(){return isVela()?"Vela":selectedAsHome()?"Vela · completar":"Original";}
        private boolean busy(){return !state.optString("busy").isEmpty()||pendingAt!=0;}
        private void switchHome(boolean personal){
            boolean recovery=state.optBoolean("switchPending")||!HomeMode.stringList(state,"disabled").isEmpty();
            if(!busy()&&(personal!=isVela()||(!personal&&recovery))){HomeMode.request(activity,personal?HomeMode.FREE:HomeMode.ORIGINAL);pendingAt=SystemClock.uptimeMillis();observedBusy=false;}
            render(0);readState();
        }
        void resume(){active=true;readState();}
        void pause(){active=false;searchGeneration++;if(searchJob!=null)searchJob.cancel(true);main.removeCallbacks(poll);}
        void destroy(){closed=true;pause();io.shutdownNow();main.removeCallbacksAndMessages(null);}
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
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&page==21){if(e.getAction()==KeyEvent.ACTION_UP)render(14);return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&(page>=17&&page<=20)){if(e.getAction()==KeyEvent.ACTION_UP){int old=page;render(old==17?2:17);rows.get(old==17?3:old-18).requestFocus();}return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&activity.returnToSetup&&page==activity.entryPage){if(e.getAction()==KeyEvent.ACTION_UP)activity.finish();return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&(page==15||page==16)){if(e.getAction()==KeyEvent.ACTION_UP)render(page==16?15:14);return true;}
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&page!=0){if(e.getAction()==KeyEvent.ACTION_UP){if(page==5){render(4);rows.get(3).requestFocus();}else if(page==12){render(11);rows.get(shortcutSlot<0?SidebarApps.slots(activity).length:Math.min(shortcutSlot,rows.size()-1)).requestFocus();}else if(page==10){render(9);rows.get(1).requestFocus();}else if(page==7||page==8||page==9){int focus=page==9?4:page==8?6:0;render(6);rows.get(focus).requestFocus();}else{render(0);if(returnRow<rows.size())rows.get(returnRow).requestFocus();}}return true;}
            return false;
        }
    }
}
