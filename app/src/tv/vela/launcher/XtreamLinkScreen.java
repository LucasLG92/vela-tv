package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** The account is entered on this device, never in a build, link, screenshot or saved view state. */
final class XtreamLinkScreen extends VelaScreen {
    XtreamLinkScreen(Activity owner, Navigation navigation){super(owner,navigation);}
    private FrameLayout stage,pane;
    private TextView status,http;
    private EditText address,username,password;
    private final List<View> controls=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Visuals.worker(1);
    private Future<?> job;
    private XtreamClient client;
    private boolean active,busy,editing,allowHttp,confirm;
    private int epoch;
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Ui.init(this);Watchdog.install(this);client=new XtreamClient(this);editing=!client.connected();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        FrameLayout root=new FrameLayout(this){@Override protected void onSizeChanged(int w,int h,int ow,int oh){if(stage==null)return;float s=Math.min(w/1920f,h/1080f);stage.setScaleX(s);stage.setScaleY(s);stage.setTranslationX((w-1920*s)/2);stage.setTranslationY((h-1080*s)/2);}};
        root.setBackgroundColor(VelaTheme.BACKGROUND);stage=new FrameLayout(this);stage.setPivotX(0);stage.setPivotY(0);stage.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{VelaTheme.PANEL,VelaTheme.BACKGROUND}));root.addView(stage,new FrameLayout.LayoutParams(1920,1080));
        put(stage,text("IPTV · Xtream",57,0xFFF7F7FA),112,132,740,90);
        TextView mark=text("TV",128,0xFFF7F7FA);mark.setGravity(Gravity.CENTER);put(stage,mark,170,338,360,184);
        put(stage,text(t("Sua televisão.\nSeus canais.","Your television.\nYour channels.","Tu televisión.\nTus canales."),43,0xFFF7F7FA),116,570,650,134);
        TextView description=text(t("Conecte seu serviço Xtream. Canais e programação dentro de Vela. Seus dados ficam cifrados neste dispositivo.","Connect your Xtream service. Channels and listings inside Vela. Your account stays encrypted on this device.","Conecta tu servicio Xtream. Canales y programación dentro de Vela. Los datos de tu cuenta se guardan cifrados en este dispositivo."),24,VelaTheme.SECONDARY);description.setGravity(Gravity.TOP);description.setMaxLines(7);put(stage,description,116,754,650,230);
        pane=new FrameLayout(this);pane.setSaveFromParentEnabled(false);put(stage,pane,936,180,840,740);status=text("",21,VelaTheme.SECONDARY);status.setMaxLines(3);status.setGravity(Gravity.TOP);put(stage,status,936,950,840,112);
        setContentView(root);getWindow().setBackgroundDrawable(null);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void put(FrameLayout parent,View view,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(view,p);}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(18);return d;}
    private TextView button(String value,int y,Runnable action){TextView v=text(value,25,0xFFF7F7FA);v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setDefaultFocusHighlightEnabled(false);v.setPadding(26,0,26,0);v.setBackground(round(VelaTheme.CARD));put(pane,v,0,y,840,76);v.setOnFocusChangeListener((view,focused)->{v.setBackground(round(focused?Appearance.accent(this):VelaTheme.CARD));v.setTextColor(focused?VelaTheme.INK:0xFFF7F7FA);});v.setOnClickListener(view->action.run());controls.add(v);return v;}
    private EditText input(String hint,int y,boolean secret){EditText v=new EditText(this);v.setId(View.generateViewId());v.setHint(hint);v.setHintTextColor(0xFF99A9BF);v.setTextColor(0xFFF7F7FA);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,25);v.setSingleLine();v.setSaveEnabled(false);v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);v.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_ACTION_NEXT);v.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(secret?512:2048)});v.setPadding(24,0,24,0);v.setBackground(round(VelaTheme.CARD));put(pane,v,0,y,840,82);controls.add(v);return v;}
    private void render(){
        pane.removeAllViews();controls.clear();address=null;username=null;password=null;http=null;status.setText("");busy=false;
        put(pane,text(confirm?t("Remover a conta?","Remove this account?","¿Eliminar esta cuenta?"):editing?t("Conectar sua conta","Connect your account","Conectar tu cuenta"):t("Conta conectada","Account connected","Cuenta conectada"),33,0xFFF7F7FA),0,0,840,80);
        if(confirm){button(t("Eliminar do Vela","Remove from Vela","Eliminar de Vela"),180,()->{client.disconnect();confirm=false;editing=true;render();});button(t("Cancelar","Cancel","Cancelar"),288,()->{confirm=false;render();});controls.get(1).requestFocus();}
        else if(editing){
            address=input(t("Servidor: https://…","Server: https://…","Servidor: https://…"),112,false);username=input(t("Usuário Xtream","Xtream username","Usuario Xtream"),222,false);username.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512)});password=input(t("Senha Xtream","Xtream password","Contraseña Xtream"),332,true);
            allowHttp=false;http=button("",442,()->{allowHttp=!allowHttp;httpLabel();});http.setTextSize(TypedValue.COMPLEX_UNIT_PX,21);httpLabel();
            button(t("Conectar e usar em Vela","Connect and use in Vela","Conectar y usar en Vela"),552,this::connect);button(t("Voltar","Back","Volver"),660,()->{if(client.connected()){editing=false;render();}else finish();});address.requestFocus();
        }else{
            button(t("Ver meus canais","Watch my channels","Ver mis canales"),132,()->{client.activate();setResult(RESULT_OK);finish();});button(t("Alterar conta","Change account","Cambiar cuenta"),240,()->{editing=true;render();});button(t("Eliminar a conta deste dispositivo","Remove account from this device","Eliminar cuenta de este dispositivo"),348,()->{confirm=true;render();});
            button(t("Voltar","Back","Volver"),456,this::finish);controls.get(0).requestFocus();
        }
    }
    private void httpLabel(){http.setText((allowHttp?"✓  ":"○  ")+t("Meu servidor usa HTTP sem cifragem","My server uses HTTP without encryption","Mi servidor usa HTTP sin cifrado"));}
    private void connect(){
        if(busy||password==null)return;String base=address.getText().toString().trim(),user=username.getText().toString(),pw=password.getText().toString();boolean httpAllowed=allowHttp;
        try{XtreamClient.server(base,httpAllowed);if(!XtreamClient.credential(user)||!XtreamClient.credential(pw))throw new IllegalArgumentException();}catch(Exception invalid){status.setText(t("Confira servidor, usuário e senha. Para HTTP, ative a opção indicada.","Check the server, username and password. For HTTP, enable the indicated option.","Revisa servidor, usuario y contraseña. Para HTTP, activa la opción indicada."));return;}
        password.setText("");((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pane.getWindowToken(),0);busy=true;int generation=++epoch;for(View v:controls)v.setEnabled(false);status.setText(t("Conectando…","Connecting…","Conectando…"));
        job=io.submit(()->{try{XtreamClient.Account a=client.login(base,user,pw,httpAllowed);main.post(()->{if(!active||generation!=epoch)return;try{client.save(a);editing=false;render();setResult(RESULT_OK);}catch(Exception failure){failed();}});}catch(Exception failure){main.post(()->{if(active&&generation==epoch)failed();});}});
    }
    private void failed(){busy=false;for(View v:controls)v.setEnabled(true);status.setText(t("Não foi possível conectar. Confira os dados, o estado da conta e a conexão. Nenhuma conta foi alterada.","Could not connect. Check the details, account status and connection. Your existing account was kept.","No se ha podido conectar. Comprueba los datos, el estado de la cuenta y la conexión. Se conserva la cuenta anterior."));if(password!=null)password.requestFocus();}
    @Override protected void onResume(){super.onResume();active=true;render();}
    @Override protected void onPause(){active=false;epoch++;if(job!=null)job.cancel(true);if(password!=null)password.setText("");if(username!=null)username.setText("");if(address!=null)address.setText("");super.onPause();}
    @Override protected void onDestroy(){io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onBackPressed(){if(confirm){confirm=false;render();}else if(editing&&client.connected()){editing=false;render();}else super.onBackPressed();}
}
