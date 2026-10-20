package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Optional first-run guide; no artwork, background service or account copying from other apps. */
public class SetupActivity extends Activity {
    private FrameLayout stage;
    private LinearLayout actions;
    private TextView title,description,progress,footnote;
    private final List<View> buttons=new ArrayList<>();
    private int page,focus;
    private boolean active;
    private AmbientWeather weather;
    private String weatherMessage="";
    private static final int WHITE=0xFFF7F7FA,DIM=0xFFA7B2C5;
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);Ui.init(this);Personalization.prepare(this);
        if(saved!=null){page=saved.getInt("page");focus=saved.getInt("focus");}
        FrameLayout root=new FrameLayout(this){
            @Override protected void onSizeChanged(int w,int h,int ow,int oh){
                if(stage==null)return;float s=Math.min(w/1920f,h/1080f);
                stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(s);stage.setScaleY(s);
                stage.setTranslationX((w-1920*s)/2);stage.setTranslationY((h-1080*s)/2);
            }
        };
        root.setBackgroundColor(0xFF11141D);stage=new FrameLayout(this);root.addView(stage,new FrameLayout.LayoutParams(1920,1080));
        GradientDrawable glow=new GradientDrawable();glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);glow.setGradientCenter(.24f,.40f);glow.setGradientRadius(1300);glow.setColors(new int[]{0xFF30465A,0xFF161D2A,0xFF11141D});stage.setBackground(glow);
        TextView brand=text("VELA",26,0xFFB9C5D6,Ui.semibold);brand.setLetterSpacing(.22f);put(brand,112,76,650,48);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.vela_mark);put(mark,116,205,120,120);
        progress=text("",20,DIM,Ui.medium);put(progress,116,390,650,36);
        title=text("",59,WHITE,Ui.displaySemibold);title.setMaxLines(3);title.setGravity(Gravity.TOP);put(title,112,448,670,235);
        description=text("",25,0xFFBAC5D6,Ui.regular);description.setMaxLines(6);description.setGravity(Gravity.TOP);description.setLineSpacing(8,1);put(description,116,705,630,225);
        actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);put(actions,943,244,820,650);
        footnote=text("",20,0xFF8F9EB6,Ui.regular);footnote.setGravity(Gravity.TOP);footnote.setMaxLines(3);put(footnote,951,916,800,94);
        setContentView(root);getWindow().setBackgroundDrawable(null);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private TextView text(String value,int size,int color,Typeface font){TextView v=new TextView(this);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(font);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void put(View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;stage.addView(v,p);}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(18);return d;}
    private void button(String label,String detail,Runnable action){
        final int index=buttons.size();LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(28,8,28,8);row.setFocusable(true);row.setClickable(true);row.setDefaultFocusHighlightEnabled(false);row.setId(View.generateViewId());row.setContentDescription(label);
        TextView name=text(label,28,WHITE,Ui.medium),sub=text(detail,19,DIM,Ui.regular);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name,new LinearLayout.LayoutParams(-1,40));if(!detail.isEmpty())row.addView(sub,new LinearLayout.LayoutParams(-1,28));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,94);p.bottomMargin=12;actions.addView(row,p);buttons.add(row);
        row.setOnFocusChangeListener((v,has)->{row.setBackground(has?round(WHITE):round(0x10FFFFFF));name.setTextColor(has?0xFF171A23:WHITE);sub.setTextColor(has?0xFF616A79:DIM);if(has)focus=index;});
        row.setOnClickListener(v->action.run());
    }
    private void render(){
        stopWeather();actions.removeAllViews();buttons.clear();footnote.setText("");
        progress.setText(page==0?t("BEM-VINDO","WELCOME","BIENVENIDO"):t("CONFIGURAÇÃO · ","SETUP · ","CONFIGURACIÓN · ")+page+" / 4");
        if(page==0){
            title.setText(t("Sua TV.\nSeu espaço.","Your TV.\nYour space.","Tu televisión.\nA tu manera."));
            description.setText(t("Conecte sua biblioteca, escolha seus apps e configure o tempo. Tudo com o controle remoto.","Connect your library, choose your apps and set up weather. All with your remote.","Conecta tu biblioteca, elige tus apps y configura el tiempo. Todo desde el mando."));
            button(t("Vamos começar","Get started","Empezar"),t("Alguns passos; você pode pular qualquer um.","A few steps; you can skip any of them.","Unos pocos pasos; puedes saltarte cualquiera."),()->go(1));
            button(t("Fazer depois","Set up later","Configurar más tarde"),t("Começar com os apps instalados.","Start with your installed apps.","Empezar con las aplicaciones instaladas."),this::finishSetup);
            footnote.setText(t("As contas e preferências ficam nesta instalação.","Accounts and preferences stay in this installation.","Las cuentas y preferencias se guardan en esta instalación."));
        }else if(page==1){
            title.setText(t("Seu conteúdo.\nSuas contas.","Your content.\nYour accounts.","Tu contenido.\nTus cuentas."));
            description.setText(t("Plex encontra seus servidores após a autorização. YouTube e Prime usam as sessões dos próprios apps.","Plex discovers your servers after authorization. YouTube and Prime use their own apps' sessions.","Plex detecta tus servidores al autorizar tu cuenta. YouTube y Prime usan la sesión de sus propias apps."));
            PlexClient plex=new PlexClient(this);
            button(plex.connected()?"Plex · "+plex.name():t("Conectar Plex","Connect Plex","Conectar Plex"),t("Perfil, servidor e cartazes da sua biblioteca.","Your library's profile, server and posters.","Perfil, servidor y carteles de tu biblioteca."),()->startActivity(new Intent(this,PlexLinkActivity.class)));
            provider("YouTube",SidebarApps.YOUTUBE);provider("Prime Video","com.amazon.amazonvideo.livingroom");
            next();footnote.setText(t("Você autoriza cada serviço. Vela nunca pede as senhas dessas contas.","You authorize each service. Vela never asks for these account passwords.","Tú autorizas cada servicio. Vela nunca pide las contraseñas de esas cuentas."));
        }else if(page==2){
            title.setText(t("Seus apps.\nNa sua ordem.","Your apps.\nYour order.","Tus apps.\nEn tu orden."));
            description.setText(t("Detectamos os apps disponíveis nesta TV. Escolha os acessos e ordene-os na barra mantendo OK pressionado.","We detect the apps available on this TV. Choose shortcuts and reorder them in the sidebar by holding OK.","Detectamos las apps disponibles en esta televisión. Elige los accesos y ordénalos en la barra manteniendo pulsado OK."));
            String[] slots=SidebarApps.slots(this);StringBuilder names=new StringBuilder();for(String pkg:slots)if(!pkg.isEmpty()){if(names.length()>0)names.append(" · ");names.append(SidebarApps.label(this,pkg));}
            button(t("Personalizar acessos","Customize shortcuts","Personalizar accesos"),names.length()>0?names.toString():t("Adicionar seus primeiros apps.","Add your first apps.","Añade tus primeras apps."),()->settings(11));
            next();footnote.setText(t("Os apps não são instalados nem removidos pelo assistente.","The guide does not install or uninstall apps.","El asistente no instala ni desinstala aplicaciones."));
        }else if(page==3){
            title.setText(t("O tempo.\nDo seu lugar.","The weather.\nWhere you are.","El tiempo.\nDonde tú estás."));
            description.setText(t("A localização aproximada é opcional. Se sua TV não a fornecer, escolha uma cidade. O relógio usa a hora da TV.","Approximate location is optional. If your TV cannot provide it, choose a city. The clock uses the TV's time.","La ubicación aproximada es opcional. Si tu tele no la facilita, elige una ciudad. El reloj usa la hora de la televisión."));
            button(t("Detectar localização","Detect location","Detectar ubicación"),t("Permissão aproximada, durante o uso.","Approximate permission, while in use.","Permiso aproximado, mientras usas Vela."),this::automaticLocation);
            button(t("Escolher cidade","Choose city","Elegir ciudad"),AmbientWeather.name(this),()->settings(9));next();
            footnote.setText(weatherMessage.isEmpty()?t("Você também pode configurar o tempo mais tarde.","You can also set up weather later.","También puedes configurar el tiempo más tarde."):weatherMessage);startWeather();
        }else{
            title.setText(t("Tudo pronto.\nBem-vindo a Vela.","All set.\nWelcome to Vela.","Todo preparado.\nBienvenido a Vela."));
            description.setText(t("Mude suas contas, apps e cidade quando quiser nos Ajustes. Sua configuração não é compartilhada com outras instalações.","Change your accounts, apps and city at any time in Settings. Your setup is not shared with other installations.","Cambia tus cuentas, apps y ciudad cuando quieras desde Ajustes. Tu configuración no se comparte con otras instalaciones."));
            button(t("Abrir meu início","Open my home","Abrir mi inicio"),t("Ver sua biblioteca e seus apps.","See your library and apps.","Ver tu biblioteca y tus apps."),this::finishSetup);
            button(t("Usar como início da TV","Use as TV home","Usar como inicio de la TV"),t("Escolher a ativação na sua televisão.","Choose activation on your television.","Elegir la activación en tu televisión."),()->{Personalization.complete(this);startActivity(new Intent(this,SettingsActivity.class).putExtra("page",1));finish();});
            button(t("Voltar","Back","Volver"),"",()->go(3));
        }
        for(int i=0;i<buttons.size();i++){if(i>0)buttons.get(i).setNextFocusUpId(buttons.get(i-1).getId());if(i+1<buttons.size())buttons.get(i).setNextFocusDownId(buttons.get(i+1).getId());}
        if(!buttons.isEmpty())buttons.get(Math.min(focus,buttons.size()-1)).requestFocus();
    }
    private void provider(String name,String pkg){
        boolean installed=SidebarApps.launchable(this,pkg);
        button(name,installed?t("Entrar ou trocar de conta no app.","Sign in or change account in the app.","Iniciar sesión o cambiar de cuenta en su app."):t("Não instalado nesta TV.","Not installed on this TV.","No está instalada en esta televisión."),()->{
            if(name.equals("YouTube")&&installed)settings(3);
            else{Intent intent=HomeApps.launchIntent(getPackageManager(),pkg);if(intent!=null)startActivity(intent);else Toast.makeText(this,t("Instale o app na loja da TV.","Install the app from the TV store.","Instala la app desde la tienda de la televisión."),Toast.LENGTH_LONG).show();}
        });
    }
    private void next(){button(t("Continuar","Continue","Continuar"),t("Você pode completar este passo depois.","You can complete this step later.","Puedes completar este paso más tarde."),()->go(page+1));button(t("Voltar","Back","Volver"),"",()->go(page-1));}
    private void go(int next){page=next;focus=0;render();}
    private void settings(int target){startActivity(new Intent(this,SettingsActivity.class).putExtra("page",target).putExtra("fromSetup",true));}
    private void finishSetup(){Personalization.complete(this);startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK));finish();}
    private void automaticLocation(){
        if(checkSelfPermission(AmbientWeather.PERMISSION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{AmbientWeather.PERMISSION},81);return;}
        AmbientWeather.prefs(this).edit().putString("mode","auto").remove("cache").apply();weatherMessage="";render();
    }
    private void startWeather(){
        if(!active||page!=3||weather!=null)return;
        weather=new AmbientWeather(this,(snapshot,message)->{if(active&&page==3){weatherMessage=snapshot==null?message:snapshot.city+" · "+snapshot.degrees();footnote.setText(weatherMessage);}});weather.start();
    }
    private void stopWeather(){if(weather!=null){weather.close();weather=null;}}
    @Override protected void onResume(){super.onResume();active=true;render();}
    @Override protected void onPause(){active=false;stopWeather();super.onPause();}
    @Override protected void onDestroy(){stopWeather();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",page);out.putInt("focus",focus);}
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){super.onRequestPermissionsResult(request,names,results);if(request==81){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)automaticLocation();else{weatherMessage=t("Sem permissão; escolha uma cidade ou continue.","No permission; choose a city or continue.","Sin permiso; elige una ciudad o continúa.");render();}}}
    @Override public void onBackPressed(){if(page>0)go(page-1);else if(Personalization.needed(this))finishSetup();else super.onBackPressed();}
}
