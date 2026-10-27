package tv.vela.launcher;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Plex device authorization in Vela's native TV shell. Passwords are entered only on Plex. */
class PlexLinkScreen extends VelaScreen {
    PlexLinkScreen(Activity owner, Navigation navigation){super(owner,navigation);}
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor io = new ScheduledThreadPoolExecutor(1);
    private PlexClient plex;
    private FrameLayout stage, pin;
    private LinearLayout serversPanel;
    private ScrollView serversScroll;
    private TextView heading, detail, code, hint, retry;
    private final List<View> cards = new ArrayList<>();
    private long pinId, expiresAt;
    private String pinCode;
    private ScheduledFuture<?> poll;
    private boolean closed, busy;
    private View attemptedCard;
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    /** A replaceable client also lets isolated native tests exercise the real screen without an account. */
    PlexClient createClient(){return new PlexClient(this);}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);Ui.init(this);Watchdog.install(this);plex=createClient();
        io.setRemoveOnCancelPolicy(true);io.setKeepAliveTime(10,TimeUnit.SECONDS);io.allowCoreThreadTimeOut(true);
        FrameLayout root=new FrameLayout(this){@Override protected void onSizeChanged(int w,int h,int ow,int oh){if(stage==null)return;float scale=Math.min(w/1920f,h/1080f);stage.setScaleX(scale);stage.setScaleY(scale);stage.setTranslationX((w-1920*scale)/2);stage.setTranslationY((h-1080*scale)/2);}};
        root.setBackgroundColor(VelaTheme.BACKGROUND);stage=new FrameLayout(this);stage.setPivotX(0);stage.setPivotY(0);root.addView(stage,new FrameLayout.LayoutParams(1920,1080));
        GradientDrawable glow=new GradientDrawable();glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);glow.setGradientRadius(1260);glow.setGradientCenter(.12f,.38f);glow.setColors(new int[]{VelaTheme.PANEL,VelaTheme.BACKGROUND});stage.setBackground(glow);
        put(stage,text(t("Conectar Plex","Connect Plex","Conectar Plex"),58,0xFFF7F7FA),112,132,740,90);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.logo_plex);logo.setScaleType(ImageView.ScaleType.FIT_START);put(stage,logo,116,324,304,112);
        TextView title=text(t("Seu cinema.\nSempre com você.","Your cinema.\nAlways with you.","Tu cine.\nSiempre contigo."),49,0xFFF7F7FA);title.setTypeface(Ui.displaySemibold);put(stage,title,116,532,680,142);
        TextView intro=text(t("Cartazes, novidades e o que você estava assistindo. Sua biblioteca, direto no início do Vela.","Posters, recent additions and what you were watching. Your library, right on Vela’s home screen.","Carteles, novedades y lo que estabas viendo. Tu biblioteca, directamente en el inicio de Vela."),25,VelaTheme.SECONDARY);intro.setMaxLines(5);intro.setGravity(Gravity.TOP);intro.setLineSpacing(6,1);put(stage,intro,116,724,644,190);
        put(stage,text(t("Sua senha fica no Plex.","Your password stays on Plex.","Tu contraseña se introduce solo en Plex."),19,VelaTheme.MUTED),116,976,700,40);
        heading=text(t("Vincular sua conta","Link your account","Vincular tu cuenta"),37,0xFFF7F7FA);heading.setTypeface(Ui.displaySemibold);put(stage,heading,936,182,840,72);
        detail=text(t("Preparando uma conexão segura…","Preparing a secure connection…","Preparando una conexión segura…"),24,VelaTheme.SECONDARY);detail.setMaxLines(3);detail.setGravity(Gravity.TOP);put(stage,detail,936,270,840,96);
        pin=new FrameLayout(this);pin.setBackground(round(VelaTheme.CARD,26));put(stage,pin,936,390,840,248);
        TextView website=text("plex.tv/link",30,VelaTheme.SECONDARY);website.setGravity(Gravity.CENTER);put(pin,website,32,28,776,52);
        code=text("····",80,0xFFF7F7FA);code.setTypeface(Ui.displaySemibold);code.setGravity(Gravity.CENTER);code.setLetterSpacing(.16f);code.setSingleLine();code.setEllipsize(TextUtils.TruncateAt.END);code.setSaveEnabled(false);put(pin,code,32,100,776,110);
        serversScroll=new ScrollView(this);serversScroll.setFillViewport(false);serversScroll.setClipToPadding(true);serversScroll.setPadding(0,0,0,4);serversScroll.setVerticalScrollBarEnabled(false);serversScroll.setVisibility(View.GONE);
        serversPanel=new LinearLayout(this);serversPanel.setOrientation(LinearLayout.VERTICAL);serversScroll.addView(serversPanel,new ScrollView.LayoutParams(-1,-2));put(stage,serversScroll,936,374,840,496);
        hint=text("",19,VelaTheme.MUTED);put(stage,hint,940,882,836,32);
        TextView back=button(t("Voltar","Back","Volver"),936,936,232,this::finish);
        retry=button(t("Tentar novamente","Retry","Reintentar"),1190,936,350,()->{startActivity(new android.content.Intent(this,PlexLinkActivity.class));finish();});retry.setVisibility(View.GONE);
        setContentView(root);back.requestFocus();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);getWindow().setBackgroundDrawable(null);
        io.execute(()->{
            if(!plex.prefs.getString("accountToken", "").isEmpty()){
                try{showServers();}catch(Exception ignored){error(t("Não foi possível consultar seus servidores. Sua sessão foi preservada.","Could not reach your servers. Your session has been kept.","No se han podido consultar tus servidores. Tu sesión se conserva."));}return;
            }
            try{
                JSONObject created=plex.createPin();pinId=created.getLong("id");pinCode=created.getString("code");expiresAt=SystemClock.elapsedRealtime()+created.optLong("expiresIn",900)*1000;
                main.post(()->{if(closed)return;detail.setText(t("Abra plex.tv/link no celular ou computador e digite este código.","Open plex.tv/link on your phone or computer and enter this code.","Abre plex.tv/link en el móvil u ordenador e introduce este código."));code.setText(pinCode);hint.setText(t("Esta tela avança quando você autoriza no Plex.","This screen continues when you authorize on Plex.","Esta pantalla continúa al autorizar en Plex."));});
                poll=io.scheduleWithFixedDelay(this::check,3,3,TimeUnit.SECONDS);
            }catch(Exception ignored){error(t("Não foi possível conectar. Verifique a Internet e tente novamente.","Could not connect. Check your Internet connection and try again.","No se ha podido iniciar la conexión. Comprueba Internet y vuelve a intentarlo."));}
        });
    }
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void put(FrameLayout parent,View view,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(view,p);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView button(String value,int x,int y,int width,Runnable action){TextView v=text(value,24,0xFFF7F7FA);v.setGravity(Gravity.CENTER);v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(round(VelaTheme.CARD,18));v.setOnFocusChangeListener((view,focused)->{v.setBackground(round(focused?Appearance.accent(this):VelaTheme.CARD,18));v.setTextColor(focused?VelaTheme.INK:0xFFF7F7FA);});v.setOnClickListener(view->action.run());put(stage,v,x,y,width,72);return v;}
    private void check(){
        if(closed)return;
        if(SystemClock.elapsedRealtime()>=expiresAt){if(poll!=null)poll.cancel(false);error(t("O código expirou. Tente novamente para obter outro.","The code expired. Retry to get another one.","El código ha caducado. Pulsa Reintentar para obtener otro."));return;}
        try{if(!plex.checkPin(pinId,pinCode))return;}catch(Exception ignored){return;} // Transient PIN requests may recover before expiry.
        if(poll!=null)poll.cancel(false);
        try{showServers();}catch(Exception ignored){error(t("Não foi possível consultar seus servidores. Sua sessão foi preservada.","Could not reach your servers. Your session has been kept.","No se han podido consultar tus servidores. Tu sesión se conserva."));}
    }
    private void showServers()throws Exception{
        List<PlexClient.Server> servers=plex.servers();
        if(servers.size()==1){try{plex.select(servers.get(0));complete();}catch(Exception ignored){main.post(()->{if(!closed){renderServers(servers);selectionError();}});}return;}
        main.post(()->{if(!closed)renderServers(servers);});
    }
    private void complete(){main.post(()->{if(!closed){LibraryClient.activate(this,"Plex");setResult(RESULT_OK);finish();}});}
    private void renderServers(List<PlexClient.Server> servers){
        pin.setVisibility(View.GONE);serversScroll.setVisibility(View.VISIBLE);serversPanel.removeAllViews();cards.clear();retry.setVisibility(View.GONE);busy=false;
        heading.setText(t("Escolha seu servidor","Choose your server","Elige tu servidor"));
        detail.setText(servers.isEmpty()?t("Nenhum servidor seguro encontrado. Verifique Plex Media Server e tente novamente.","No secure server found. Check Plex Media Server and try again.","No se ha encontrado un servidor con conexión segura. Comprueba Plex Media Server y reinténtalo."):t("Escolha a biblioteca que deseja ver no início.","Choose the library you want on your home screen.","Elige la biblioteca que quieres ver en Inicio."));
        hint.setText(servers.isEmpty()?"":t("↑ ↓ Escolher servidor  ·  OK Conectar","↑ ↓ Choose server  ·  OK Connect","↑ ↓ Elegir servidor  ·  OK Conectar"));
        String selected=plex.prefs.getString("serverId","");View focus=null;
        for(PlexClient.Server server:servers){boolean current=!selected.isEmpty()&&selected.equals(server.id);View card=serverCard(server,current);cards.add(card);if(focus==null||current)focus=card;}
        if(focus!=null)focus.requestFocus();else showRetry();
    }
    private View serverCard(PlexClient.Server server,boolean current){
        FrameLayout card=new FrameLayout(this);card.setId(View.generateViewId());card.setFocusable(true);card.setFocusableInTouchMode(true);card.setDefaultFocusHighlightEnabled(false);card.setBackground(round(VelaTheme.CARD,22));
        ServerIcon icon=new ServerIcon();put(card,icon,26,26,60,60);
        TextView name=text(server.name,28,0xFFF7F7FA);name.setTypeface(Ui.semibold);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);put(card,name,110,22,626,38);
        String sub=current?t("Selecionado no Vela","Selected in Vela","Seleccionado en Vela"):t("Biblioteca do Plex","Plex library","Biblioteca de Plex");
        TextView subtitle=text(sub,19,VelaTheme.SECONDARY);subtitle.setSingleLine();put(card,subtitle,110,66,626,28);
        TextView arrow=text(current?"✓":"›",33,VelaTheme.SECONDARY);arrow.setGravity(Gravity.CENTER);put(card,arrow,756,22,52,68);
        card.setContentDescription(server.name+", "+sub);card.setOnFocusChangeListener((v,focused)->{int color=focused?VelaTheme.INK:0xFFF7F7FA;card.setBackground(round(focused?Appearance.accent(this):VelaTheme.CARD,22));name.setTextColor(color);subtitle.setTextColor(focused?0xFF4B5363:VelaTheme.SECONDARY);arrow.setTextColor(focused?0xFF4B5363:VelaTheme.SECONDARY);icon.focused=focused;icon.invalidate();});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,112);p.bottomMargin=14;serversPanel.addView(card,p);card.setOnClickListener(v->select(server));return card;
    }
    private void select(PlexClient.Server server){if(busy||closed)return;attemptedCard=getCurrentFocus();busy=true;detail.setText(t("Conectando a ","Connecting to ","Conectando con ")+server.name+"…");for(View v:cards){v.setEnabled(false);v.setAlpha(.55f);}retry.setVisibility(View.GONE);
        io.execute(()->{try{plex.select(server);complete();}catch(Exception ignored){main.post(()->{if(!closed)selectionError();});}});
    }
    private void selectionError(){busy=false;for(View v:cards){v.setEnabled(true);v.setAlpha(1f);}if(attemptedCard!=null)attemptedCard.requestFocus();detail.setText(t("Não foi possível conectar. Verifique se o servidor está ligado ou escolha outro.","Could not connect. Check that the server is on, or choose another.","No se ha podido conectar. Comprueba que el servidor esté encendido o elige otro."));}
    private void showRetry(){retry.setVisibility(View.VISIBLE);retry.requestFocus();}
    private void error(String message){main.post(()->{if(closed)return;pin.setVisibility(View.GONE);detail.setText(message);hint.setText("");showRetry();});}
    private final class ServerIcon extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean focused;
        ServerIcon(){super(PlexLinkScreen.this);}
        @Override protected void onDraw(Canvas canvas){int color=focused?0xFF4B5363:VelaTheme.SECONDARY;paint.setColor(focused?0x17262831:0x22363841);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(0,0,60,60,15,15,paint);paint.setColor(color);paint.setStrokeWidth(2.5f);paint.setStyle(Paint.Style.STROKE);canvas.drawRoundRect(14,15,46,45,5,5,paint);canvas.drawLine(14,30,46,30,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(22,23,2,paint);canvas.drawCircle(22,38,2,paint);canvas.drawRoundRect(31,21,39,24,1,1,paint);canvas.drawRoundRect(31,36,39,39,1,1,paint);}
    }
    @Override protected void onDestroy(){closed=true;if(poll!=null)poll.cancel(true);io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
