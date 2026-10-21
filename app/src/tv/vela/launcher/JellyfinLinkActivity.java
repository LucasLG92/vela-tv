package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Native server + Quick Connect flow, with a password fallback for servers that disable codes. */
public final class JellyfinLinkActivity extends Activity {
    private FrameLayout stage,pane;
    private TextView detail,status,code;
    private EditText address,username,password;
    private final List<View> controls=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Visuals.worker(1);
    private Future<?> job;
    private JellyfinClient client;
    private JellyfinClient.Server server;
    private String serverAddress="",secret="";
    private int page,epoch;
    private long expires;
    private boolean active,busy;
    private final Runnable poll=()->pollCode();
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Ui.init(this);Watchdog.install(this);client=new JellyfinClient(this);serverAddress=client.prefs.getString("base","");
        FrameLayout root=new FrameLayout(this){@Override protected void onSizeChanged(int w,int h,int ow,int oh){if(stage==null)return;float scale=Math.min(w/1920f,h/1080f);stage.setScaleX(scale);stage.setScaleY(scale);stage.setTranslationX((w-1920*scale)/2);stage.setTranslationY((h-1080*scale)/2);}};
        root.setBackgroundColor(0xFF11141D);stage=new FrameLayout(this);stage.setPivotX(0);stage.setPivotY(0);root.addView(stage,new FrameLayout.LayoutParams(1920,1080));
        GradientDrawable glow=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF293C55,0xFF11141D});stage.setBackground(glow);
        put(stage,text("VELA",24,0xFFB9C5D6),116,66,650,42);put(stage,text("Jellyfin",60,0xFFF7F7FA),112,132,740,90);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_jellyfin);put(stage,logo,230,318,180,180);
        put(stage,text(t("Sua biblioteca.\nSeu servidor.","Your library.\nYour server.","Tu biblioteca.\nTu servidor."),43,0xFFF7F7FA),116,565,650,124);
        detail=text(t("Cartazes, novidades e progresso direto do Jellyfin. Autorize o código em um dispositivo onde você já tenha entrado.","Posters, recent additions and progress straight from Jellyfin. Authorize the code on a device where you are already signed in.","Carteles, novedades y progreso directamente de Jellyfin. Autoriza el código desde un dispositivo donde ya hayas iniciado sesión."),24,0xFFBAC1D0);detail.setMaxLines(6);detail.setGravity(Gravity.TOP);put(stage,detail,116,720,650,220);
        pane=new FrameLayout(this);put(stage,pane,936,180,840,760);status=text("",21,0xFFBAC1D0);status.setMaxLines(3);status.setGravity(Gravity.TOP);put(stage,status,936,950,840,105);
        setContentView(root);page=client.connected()?3:0;render();getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void put(FrameLayout parent,View view,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(view,p);}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(18);return d;}
    private TextView button(String value,int y,Runnable action){TextView v=text(value,25,0xFFF7F7FA);v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setDefaultFocusHighlightEnabled(false);v.setPadding(26,0,26,0);v.setSingleLine();v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setBackground(round(0xFF272F40));v.setOnFocusChangeListener((view,focused)->{v.setBackground(round(focused?Appearance.accent(this):0xFF272F40));v.setTextColor(focused?0xFF171A23:0xFFF7F7FA);});v.setOnClickListener(view->action.run());put(pane,v,0,y,840,76);controls.add(v);return v;}
    private EditText input(String hint,String value,int y,boolean masked){EditText v=new EditText(this);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,25);v.setTextColor(0xFFF7F7FA);v.setHintTextColor(0xFF919BAD);v.setHint(hint);v.setText(value);v.setSingleLine(true);v.setSaveEnabled(false);v.setPadding(24,0,24,0);v.setBackground(round(0xFF252E40));v.setInputType(masked?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);v.setSelectAllOnFocus(true);put(pane,v,0,y,840,82);controls.add(v);return v;}
    private void heading(String title){TextView label=text(title,33,0xFFF7F7FA);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);put(pane,label,0,0,840,92);}
    private void render(){
        pane.removeAllViews();controls.clear();address=null;username=null;password=null;status.setText("");busy=false;
        if(page==0){heading(t("Conectar um servidor","Connect a server","Conectar un servidor"));address=input(t("Endereço do Jellyfin","Jellyfin address","Dirección de Jellyfin"),serverAddress,128,false);
            button(t("Conectar","Connect","Conectar"),242,this::connect);TextView hint=text(t("HTTPS para acesso externo. HTTP é aceito apenas na rede local, sem criptografia.","Use HTTPS for remote access. HTTP is accepted only on the local network, without encryption.","Usa HTTPS para acceso externo. HTTP se admite solo en la red local, sin cifrado."),21,0xFFBAC1D0);hint.setMaxLines(3);hint.setGravity(Gravity.TOP);put(pane,hint,20,358,800,130);button(t("Voltar","Back","Volver"),544,this::finish);address.requestFocus();
        }else if(page==1){heading(server.name);code=text("",62,0xFFF7F7FA);code.setLetterSpacing(.12f);put(pane,code,0,115,840,100);TextView hint=text(t("No Jellyfin do celular ou navegador, abra Conexão rápida e autorize este código.","In Jellyfin on your phone or browser, open Quick Connect and authorize this code.","En Jellyfin, desde el móvil o navegador, abre Conexión rápida y autoriza este código."),25,0xFFBAC1D0);hint.setMaxLines(4);hint.setGravity(Gravity.TOP);put(pane,hint,0,255,820,170);
            button(t("Usar usuário e senha","Use username and password","Usar usuario y contraseña"),480,()->{stop();page=2;render();});button(t("Outro servidor","Another server","Otro servidor"),582,()->{stop();page=0;render();});controls.get(0).requestFocus();
        }else if(page==2){heading(server.name);username=input(t("Usuário","Username","Usuario"),"",128,false);password=input(t("Senha","Password","Contraseña"),"",240,true);password.setOnEditorActionListener((v,a,e)->{login();return true;});button(t("Entrar","Sign in","Iniciar sesión"),362,this::login);button(t("Outro servidor","Another server","Otro servidor"),478,()->{stop();page=0;render();});username.requestFocus();
        }else if(page==3){heading(client.name());TextView who=text(client.prefs.getString("userName","")+" · Jellyfin",25,0xFFBAC1D0);who.setSingleLine();who.setEllipsize(android.text.TextUtils.TruncateAt.END);put(pane,who,0,110,840,60);
            button(t("Usar no início","Use on home screen","Usar en Inicio"),232,()->{LibraryClient.activate(this,"Jellyfin");setResult(RESULT_OK);finish();});button(t("Trocar de servidor ou usuário","Change server or user","Cambiar servidor o usuario"),340,()->{page=0;render();});button(t("Desvincular do Vela","Unlink from Vela","Desvincular de Vela"),448,()->{page=4;render();});button(t("Concluído","Done","Listo"),556,this::finish);
            if(!SidebarApps.launchable(this,JellyfinClient.PACKAGE))status.setText(t("Instale Jellyfin para Android TV para abrir os títulos. Entre nele com o mesmo servidor e usuário.","Install Jellyfin for Android TV to open titles. Sign in there with the same server and user.","Instala Jellyfin para Android TV para abrir los títulos e inicia sesión allí con el mismo servidor y usuario."));controls.get(0).requestFocus();
        }else{heading(t("Desvincular Jellyfin?","Unlink Jellyfin?","¿Desvincular Jellyfin?"));button(t("Desvincular do Vela","Unlink from Vela","Desvincular de Vela"),200,()->submit(()->{try{client.logout();}catch(Exception ignored){}finally{client.disconnect();}return null;},result->{CinemaImages.purgeDisk(this);page=0;render();}));button(t("Cancelar","Cancel","Cancelar"),310,()->{page=3;render();});controls.get(1).requestFocus();}
    }
    private interface Work {Object run()throws Exception;}
    private interface Done {void accept(Object value)throws Exception;}
    private void submit(Work work,Done done){
        if(busy)return;busy=true;int generation=++epoch;for(View v:controls)v.setEnabled(false);status.setText(t("Conectando…","Connecting…","Conectando…"));
        job=io.submit(()->{try{Object value=work.run();main.post(()->{if(!active||generation!=epoch)return;busy=false;for(View v:controls)v.setEnabled(true);try{done.accept(value);}catch(Exception error){failure();}});}catch(Exception error){main.post(()->{if(active&&generation==epoch){busy=false;for(View v:controls)v.setEnabled(true);failure();}});}});
    }
    private void failure(){status.setText(t("Não foi possível conectar. Confira endereço, autorização e conexão do servidor.","Could not connect. Check the address, authorization and server connection.","No se ha podido conectar. Revisa la dirección, la autorización y la conexión del servidor."));}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pane.getWindowToken(),0);}
    private void connect(){if(address==null)return;serverAddress=address.getText().toString().trim();hideKeyboard();submit(()->client.server(serverAddress),value->{server=(JellyfinClient.Server)value;submit(()->client.quickEnabled(server)?client.initiate(server):null,quick->{if(quick==null){page=2;render();return;}JSONObject data=(JSONObject)quick;String pending=data.optString("Secret"),shown=data.optString("Code");if(pending.length()<16||pending.length()>512||!shown.matches("[A-Za-z0-9]{4,12}"))throw new java.io.IOException();page=1;render();secret=pending;code.setText(shown);expires=SystemClock.elapsedRealtime()+300_000;main.postDelayed(poll,5000);});});}
    private void pollCode(){
        if(!active||page!=1||secret.isEmpty())return;if(SystemClock.elapsedRealtime()>=expires){secret="";status.setText(t("Código expirado. Escolha outro servidor para gerar um novo ou entre com sua senha.","Code expired. Choose another server to generate a new code or sign in with your password.","El código ha caducado. Elige otro servidor para generar uno nuevo o inicia sesión con tu contraseña."));return;}
        String pending=secret;int generation=epoch;job=io.submit(()->{try{JellyfinClient.Session session=client.authorized(server,pending)?client.quickLogin(server,pending):null;main.post(()->{if(!active||generation!=epoch||page!=1)return;if(session!=null)linked(session);else main.postDelayed(poll,5000);});}catch(Exception transientFailure){main.post(()->{if(active&&generation==epoch&&page==1)main.postDelayed(poll,5000);});}});
    }
    private void login(){if(password==null||username==null||busy)return;String user=username.getText().toString(),pw=password.getText().toString();password.setText("");hideKeyboard();submit(()->client.passwordLogin(server,user,pw),value->linked((JellyfinClient.Session)value));}
    private void linked(JellyfinClient.Session session){try{client.save(session);LibraryClient.activate(this,"Jellyfin");stop();page=3;render();}catch(Exception error){failure();}}
    private void stop(){epoch++;main.removeCallbacks(poll);if(job!=null)job.cancel(true);job=null;secret="";busy=false;}
    @Override protected void onResume(){super.onResume();active=true;if(page==1&&secret.isEmpty())page=0;render();}
    @Override protected void onPause(){active=false;stop();if(password!=null)password.setText("");super.onPause();}
    @Override protected void onDestroy(){stop();io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onBackPressed(){if(page==3||page==0){super.onBackPressed();return;}stop();page=client.connected()?3:0;render();}
}
