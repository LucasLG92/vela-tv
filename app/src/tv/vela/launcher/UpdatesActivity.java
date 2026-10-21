package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

/** TV-native update controls. Android always owns the final installation confirmation. */
public final class UpdatesActivity extends Activity {
    private FrameLayout stage;
    private TextView title,status,action,check,automatic,download,channel,extra;
    private boolean active,installAfterDownload,waitingPermission;
    private String message="";
    private final Updates.Listener listener=()->{if(active)render();};
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Ui.init(this);Watchdog.install(this);
        FrameLayout root=new FrameLayout(this){@Override protected void onSizeChanged(int w,int h,int oldw,int oldh){float scale=Math.min(w/1920f,h/1080f);stage.setScaleX(scale);stage.setScaleY(scale);stage.setTranslationX((w-1920*scale)/2);stage.setTranslationY((h-1080*scale)/2);}};root.setBackgroundColor(0xFF11141D);
        stage=new FrameLayout(this);stage.setPivotX(0);stage.setPivotY(0);root.addView(stage,new FrameLayout.LayoutParams(1920,1080));setContentView(root);
        View glow=new View(this);GradientDrawable gradient=new GradientDrawable();gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);gradient.setGradientRadius(1000);gradient.setGradientCenter(.2f,.4f);gradient.setColors(new int[]{0xFF303D50,0xFF11141D});glow.setBackground(gradient);put(glow,0,0,1920,1080);
        TextView brand=text("VELA",24,0xFFB9C5D6);brand.setLetterSpacing(.22f);put(brand,116,66,500,42);
        put(text(t("Atualizações","Updates","Actualizaciones"),58,0xFFF7F7FA),112,132,750,90);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.vela_mark);put(mark,220,318,184,184);
        TextView heading=text(t("Sempre em dia.","Always up to date.","Siempre al día."),43,0xFFF7F7FA);put(heading,116,570,650,100);
        TextView explanation=text(t("Novas versões do GitHub, na sua TV. O Android pede confirmação antes de instalar e suas configurações são mantidas.","New GitHub releases, on your TV. Android asks before installing and keeps your settings.","Las nuevas versiones de GitHub, en tu televisión. Android pide confirmar la instalación y conserva tu configuración."),24,0xFFBAC1D0);explanation.setMaxLines(5);explanation.setGravity(Gravity.TOP);explanation.setLineSpacing(6,1);put(explanation,116,704,630,190);
        put(text("Vela · "+Updates.version(this),19,0xFF7F899E),116,970,650,40);
        title=text("",34,0xFFF7F7FA);put(title,936,155,840,90);status=text("",21,0xFFBAC1D0);status.setMaxLines(3);status.setGravity(Gravity.TOP);put(status,936,258,840,100);
        action=button("",360,()->{ReleaseFeed.Release release=Updates.available(this);if(release==null)return;message="";if(Updates.ready(this,release))requestInstall();else{installAfterDownload=true;Updates.download(this);}});
        check=button(t("Buscar agora","Check now","Buscar ahora"),452,()->{message="";Updates.check(this);});
        automatic=button("",590,()->{Updates.prefs(this).edit().putBoolean("automatic",!Updates.automaticEnabled(this)).apply();render();Updates.automatic(this);});
        download=button("",682,()->{Updates.prefs(this).edit().putBoolean("download",!Updates.automaticDownload(this)).apply();render();Updates.automatic(this);});
        channel=button("",774,()->{Updates.prefs(this).edit().putBoolean("previews",!Updates.previews(this)).putLong("checked",0).putLong("attempt",0).apply();render();Updates.automatic(this);});
        extra=button("",866,()->{if(Updates.state()!=Updates.IDLE){Updates.clearInstallationRequest(this);Updates.cancel();}else try{ReleaseFeed.Release release=Updates.available(this);startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(release==null?ReleaseFeed.REPO+"/releases":release.page())));}catch(ActivityNotFoundException absent){message=t("Nenhum navegador disponível.","No browser available.","No hay un navegador disponible.");render();}});
        waitingPermission=state!=null&&state.getBoolean("permission");installAfterDownload=state!=null&&state.getBoolean("installAfterDownload");render();check.requestFocus();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);getWindow().setBackgroundDrawable(null);
    }
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);view.setTextColor(color);view.setTypeface(Ui.medium);view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private void put(View view,int x,int y,int width,int height){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;stage.addView(view,p);}
    private GradientDrawable round(int color){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(18);return drawable;}
    private TextView button(String value,int y,Runnable run){TextView view=text(value,25,0xFFF7F7FA);view.setId(View.generateViewId());view.setFocusable(true);view.setFocusableInTouchMode(true);view.setDefaultFocusHighlightEnabled(false);view.setSingleLine();view.setEllipsize(android.text.TextUtils.TruncateAt.END);view.setPadding(26,0,26,0);view.setBackground(round(0xFF272F40));put(view,936,y,840,76);view.setOnFocusChangeListener((v,focused)->{view.setBackground(round(focused?Appearance.accent(this):0xFF272F40));view.setTextColor(focused?0xFF171A23:0xFFF7F7FA);});view.setOnClickListener(v->run.run());return view;}
    private void render(){
        ReleaseFeed.Release release=Updates.available(this);int state=Updates.state();boolean busy=state!=Updates.IDLE,ready=Updates.ready(this,release);
        String subtitle=t("Busca automática uma vez por dia, ao abrir o Vela.","Automatic checks once a day when Vela opens.","Comprobación automática una vez al día al abrir Vela.");
        if(state==Updates.CHECKING)title.setText(t("Buscando versões…","Checking releases…","Buscando versiones…"));
        else if(state==Updates.DOWNLOADING)title.setText(t("Baixando · ","Downloading · ","Descargando · ")+Updates.progress()+"%");
        else if(state==Updates.VERIFYING)title.setText(t("Verificando o instalador…","Verifying installer…","Comprobando el instalador…"));
        else if(!Updates.error().isEmpty()){title.setText(t("Não foi possível concluir.","Could not complete.","No se ha podido completar."));subtitle=Updates.error();}
        else if(release!=null){title.setText("Vela "+release.version+(ready?t(" pronta"," ready"," preparada"):t(" disponível"," available"," disponible")));subtitle=String.format(Ui.locale(),"%.1f MB",release.size/1048576f)+" · "+t("Assinatura e integridade verificadas antes de instalar.","Signature and integrity checked before installation.","Firma e integridad comprobadas antes de instalar.");}
        else title.setText(Updates.prefs(this).getLong("checked",0)>0?t("Nenhuma nova versão neste canal.","No new releases in this channel.","No hay nuevas versiones en este canal."):t("Encontre a próxima versão.","Find the next version.","Busca la próxima versión."));
        status.setText(message.isEmpty()?subtitle:message);action.setText(release==null?t("Nenhuma atualização disponível","No update available","No hay actualización disponible"):ready?t("Instalar ","Install ","Instalar ")+release.version:t("Baixar e instalar ","Download and install ","Descargar e instalar ")+release.version);
        if(busy)extra.requestFocus();else if(action.hasFocus()&&release==null){check.setEnabled(true);check.requestFocus();}action.setEnabled(!busy&&release!=null);action.setAlpha(action.isEnabled()?1:.45f);check.setEnabled(!busy);check.setAlpha(busy?.45f:1);
        String yes=t("Ativada","On","Activada"),no=t("Desativada","Off","Desactivada");automatic.setText(t("Comprovar automaticamente","Check automatically","Comprobar automáticamente")+"  ·  "+(Updates.automaticEnabled(this)?yes:no));download.setText(t("Baixar automaticamente","Download automatically","Descargar automáticamente")+"  ·  "+(Updates.automaticDownload(this)?yes:no));channel.setText(t("Incluir versões prévias","Include preview releases","Incluir versiones preliminares")+"  ·  "+(Updates.previews(this)?yes:no));
        automatic.setEnabled(!busy);download.setEnabled(!busy);channel.setEnabled(!busy);extra.setText(busy?t("Cancelar","Cancel","Cancelar"):t("Ver no GitHub","View on GitHub","Ver en GitHub"));
        if(active&&!busy&&installAfterDownload){installAfterDownload=false;if(ready&&Updates.error().isEmpty())requestInstall();}
        else if(active&&!busy&&Updates.installationRequested(this)&&getPackageManager().canRequestPackageInstalls())Updates.install(this);
    }
    private void requestInstall(){
        if(!Updates.requestInstallation(this)){message=t("Não foi possível preparar a instalação. Baixe a atualização novamente.","Could not prepare installation. Download the update again.","No se ha podido preparar la instalación. Descarga la actualización de nuevo.");render();return;}
        if(!getPackageManager().canRequestPackageInstalls()){
            waitingPermission=true;message=t("Permita instalar atualizações do Vela e volte a esta tela.","Allow Vela to install updates and return to this screen.","Permite instalar actualizaciones desde Vela y vuelve a esta pantalla.");
            try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),901);}catch(ActivityNotFoundException|SecurityException missing){waitingPermission=false;Updates.clearInstallationRequest(this);message=t("Abra as permissões de instalação nos ajustes da TV.","Open install permissions in TV settings.","Abre los permisos de instalación en los ajustes de la televisión.");render();}return;
        }
        Updates.install(this);
    }
    boolean canShowInstaller(){return active;}
    @Override protected void onResume(){
        super.onResume();active=true;Updates.observe(listener);
        if(waitingPermission||Updates.installationRequested(this)){
            waitingPermission=false;
            if(getPackageManager().canRequestPackageInstalls())message="";
            else{Updates.clearInstallationRequest(this);message=t("Permissão não concedida. Você pode tentar instalar novamente.","Permission not granted. You can try installing again.","No se ha concedido el permiso. Puedes volver a intentar la instalación.");}
            action.requestFocus();
        }
        render();Updates.automatic(this);
    }
    @Override protected void onPause(){active=false;Updates.forget(listener);if(isFinishing())Updates.clearInstallationRequest(this);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("permission",waitingPermission);state.putBoolean("installAfterDownload",installAfterDownload);super.onSaveInstanceState(state);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==902&&result==RESULT_CANCELED)message=t("Instalação cancelada. Você pode tentar novamente.","Installation cancelled. You can try again.","Instalación cancelada. Puedes intentarlo de nuevo.");}
}
