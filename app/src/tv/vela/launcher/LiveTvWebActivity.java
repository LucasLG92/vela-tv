package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;

/** The broadcaster's player, loaded only on selection in an expendable, private process. */
public class LiveTvWebActivity extends Activity {
    private static boolean directoryReady;
    private static int instances;
    private final Handler main=new Handler(Looper.getMainLooper());
    private FrameLayout root,content,fullscreen;
    private LinearLayout toolbar;
    private TextView location,message,backButton;
    WebView web;
    private Pointer pointer;
    private WebChromeClient.CustomViewCallback customCallback;
    private AlertDialog mediaDialog;
    private PermissionRequest mediaRequest;
    private String address,originHost;
    private boolean foreground,cursor=true,destroyed,longOk;
    private final Runnable timeout=()->notice(t("O site está demorando. Use Recarregar ou volte aos canais.","The website is taking a while. Reload it or return to channels.","La web está tardando. Usa Recargar o vuelve a los canales."));
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    static boolean safeUrl(String value){return LiveTvData.webUrl(value);}
    static String host(String value){try{return new java.net.URI(value).getHost().toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
    static boolean sameSite(String a,String b){
        if(a.equals(b))return !a.isEmpty();
        for(String domain:new String[]{"mediasetinfinity.es","mediaset.es","atresplayer.com","atresmedia.com","rtve.es","youtube.com","youtube-nocookie.com"})
            if((a.equals(domain)||a.endsWith("."+domain))&&(b.equals(domain)||b.endsWith("."+domain)))return true;
        return false;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);instances++;Ui.init(this);
        address=getIntent().getStringExtra("url");
        if(!safeUrl(address)){finish();return;}originHost=host(address);
        root=new FrameLayout(this);root.setBackgroundColor(0xFF0E1420);
        content=new FrameLayout(this);FrameLayout.LayoutParams body=new FrameLayout.LayoutParams(-1,-1);body.topMargin=dp(84);root.addView(content,body);
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(22),dp(12),dp(22),dp(12));toolbar.setBackgroundColor(0xFF152132);root.addView(toolbar,new FrameLayout.LayoutParams(-1,dp(84)));
        backButton=action("‹ "+t("Canais","Channels","Canales"),()->finish());
        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);identity.setPadding(dp(16),0,dp(12),0);toolbar.addView(identity,new LinearLayout.LayoutParams(0,-1,1));
        String name=getIntent().getStringExtra("name");TextView title=text(name==null?"Vela":name.substring(0,Math.min(100,name.length())),18,0xFFF5F7FA);title.setTypeface(Ui.semibold);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);identity.addView(title,new LinearLayout.LayoutParams(-1,0,1));
        location=text(originHost,12,0xFFACBCD0);location.setSingleLine(true);location.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);identity.addView(location,new LinearLayout.LayoutParams(-1,0,1));
        action(t("Reproduzir","Play","Reproducir"),()->video("play"));
        action(t("Ampliar","Expand","Ampliar"),()->{showTools(false);video("fullscreen");});
        action(t("Cursor / mando","Cursor / remote","Cursor / mando"),()->{cursor=!cursor;pointer.setVisibility(cursor?View.VISIBLE:View.GONE);showTools(false);notice(cursor?t("Flechas: cursor · OK: clicar · Canal ±: rolar · Menu: controles","Arrows: cursor · OK: click · Channel ±: scroll · Menu: controls","Flechas: cursor · OK: pulsar · Canal ±: desplazar · Menú: controles"):t("Navegação normal do site. Menu para voltar ao cursor.","Normal website navigation. Menu switches back to the cursor.","Navegación normal de la web. Menú para volver al cursor."));});
        action(t("Recarregar","Reload","Recargar"),()->{if(web!=null){notice("");web.reload();showTools(false);}});
        fullscreen=new FrameLayout(this);fullscreen.setBackgroundColor(Color.BLACK);fullscreen.setVisibility(View.GONE);root.addView(fullscreen,new FrameLayout.LayoutParams(-1,-1));
        pointer=new Pointer();root.addView(pointer,new FrameLayout.LayoutParams(-1,-1));
        message=text("",14,Color.WHITE);message.setPadding(dp(18),dp(12),dp(18),dp(12));message.setGravity(Gravity.CENTER);message.setBackgroundColor(0xE6142030);message.setVisibility(View.GONE);FrameLayout.LayoutParams note=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);note.setMargins(dp(80),0,dp(80),dp(20));root.addView(message,note);
        setContentView(root);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try{
            if(!directoryReady){WebView.setDataDirectorySuffix("live-tv");directoryReady=true;}
            web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setGeolocationEnabled(false);
            settings.setSupportMultipleWindows(true);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSafeBrowsingEnabled(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setBuiltInZoomControls(false);
            String version="";android.content.pm.PackageInfo provider=WebView.getCurrentWebViewPackage();if(provider!=null)version=provider.versionName;
            if(version.matches("[0-9.]+"))settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"+version+" Safari/537.36");
            CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){if(!safeUrl(request.getUrl().toString())){if(request.isForMainFrame())notice(t("Este link precisa de um app externo. Você pode voltar aos canais.","This link needs an external app. You can return to channels.","Este enlace necesita una app externa. Puedes volver a los canales."));return true;}return false;}
                @Override public void onPageStarted(WebView v,String url,Bitmap icon){location.setText(host(url)+" · "+t("Carregando…","Loading…","Cargando…"));main.removeCallbacks(timeout);main.postDelayed(timeout,30_000);}
                @Override public void onPageFinished(WebView v,String url){main.removeCallbacks(timeout);location.setText(host(url));}
                @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())failure();}
                @Override public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=400)failure();}
                @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError error){handler.cancel();if(error.getUrl().equals(v.getUrl()))failure();}
                @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){disposeWeb();notice(t("O reprodutor foi fechado para liberar memória. Volte aos canais e tente novamente.","The player closed to free memory. Return to channels and try again.","El reproductor se ha cerrado para liberar memoria. Vuelve a los canales e inténtalo de nuevo."));showTools(true);return true;}
            });
            web.setWebChromeClient(new WebChromeClient(){
                @Override public void onShowCustomView(View view,CustomViewCallback callback){if(customCallback!=null||!foreground){callback.onCustomViewHidden();return;}customCallback=callback;fullscreen.addView(view,new FrameLayout.LayoutParams(-1,-1));fullscreen.setVisibility(View.VISIBLE);pointer.bringToFront();message.bringToFront();showTools(false);}
                @Override public void onHideCustomView(){hideFullscreen();}
                @Override public void onPermissionRequest(PermissionRequest request){requestMedia(request);}
                @Override public void onPermissionRequestCanceled(PermissionRequest request){if(mediaRequest==request)cancelPermission();}
            });
            web.setDownloadListener((url,agent,disposition,type,length)->notice(t("Downloads não são necessários para assistir. Volte ao reprodutor oficial.","Downloads are not needed to watch. Return to the official player.","No necesitas descargar archivos para verlo. Vuelve al reproductor oficial.")));
            content.addView(web,new FrameLayout.LayoutParams(-1,-1));web.loadUrl(address);showTools(false);
            notice(t("Flechas: cursor · OK: clicar · Canal ±: rolar · Menu: controles","Arrows: cursor · OK: click · Channel ±: scroll · Menu: controls","Flechas: cursor · OK: pulsar · Canal ±: desplazar · Menú: controles"));
        }catch(RuntimeException|LinkageError e){disposeWeb();notice(t("Android System WebView não está disponível. Atualize-o e tente novamente.","Android System WebView is unavailable. Update it and try again.","Android System WebView no está disponible. Actualízalo y vuelve a intentarlo."));showTools(true);}
    }
    private void failure(){main.removeCallbacks(timeout);notice(t("Não foi possível abrir o site. Recarregue ou volte aos canais.","The website could not be opened. Reload it or return to channels.","No se ha podido abrir la web. Recarga o vuelve a los canales."));showTools(true);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String label,int size,int color){TextView v=new TextView(this);v.setText(label);v.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);v.setTextColor(color);v.setTypeface(Ui.medium);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView action(String label,Runnable run){TextView v=text(label,14,Color.WHITE);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),0,dp(12),0);v.setFocusable(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(round(0xFF293A50));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.setMargins(0,0,dp(8),0);toolbar.addView(v,p);v.setOnFocusChangeListener((view,focus)->{v.setTextColor(focus?0xFF162033:Color.WHITE);v.setBackground(round(focus?0xFFF5F7FA:0xFF293A50));});v.setOnClickListener(view->run.run());return v;}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private final Runnable hideNotice=()->{if(message!=null)message.setVisibility(View.GONE);};
    private void notice(String value){if(destroyed||message==null)return;main.removeCallbacks(hideNotice);message.setText(value);message.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);if(!value.isEmpty())main.postDelayed(hideNotice,10_000);}
    private void showTools(boolean focus){if(toolbar==null)return;toolbar.setVisibility(focus?View.VISIBLE:View.GONE);FrameLayout.LayoutParams body=(FrameLayout.LayoutParams)content.getLayoutParams();body.topMargin=focus?dp(84):0;content.setLayoutParams(body);if(focus){toolbar.bringToFront();backButton.requestFocus();}else if(web!=null)web.requestFocus();pointer.bringToFront();if(message!=null)message.bringToFront();}
    private void video(String operation){
        if(web==null)return;
        // Only operate ordinary player controls, preserving the provider's consent, ads, login and DRM.
        String script="(function(){function find(w){try{var a=w.document.querySelectorAll('video');for(var i=0;i<a.length;i++)if(a[i].getBoundingClientRect().width>100)return a[i];for(var j=0;j<w.frames.length;j++){var v=find(w.frames[j]);if(v)return v;}}catch(e){}return null;}var v=find(window);if(!v)return false;try{"+
            (operation.equals("fullscreen")?"var f=v.requestFullscreen||v.webkitRequestFullscreen;if(f)f.call(v);":operation.equals("pause")?"v.pause();":operation.equals("toggle")?"if(v.paused)v.play();else v.pause();":"v.play();")+"return true;}catch(e){return false;}})()";
        web.evaluateJavascript(script,value->{if(!destroyed&&!"true".equals(value)&&!operation.equals("pause"))notice(t("Use o cursor para pulsar Play ou tela cheia no reprodutor oficial.","Use the cursor to press Play or fullscreen in the official player.","Usa el cursor para pulsar Play o pantalla completa en el reproductor oficial."));});
    }
    private void requestMedia(PermissionRequest request){
        String origin=request.getOrigin().toString();boolean media=false;for(String resource:request.getResources())if(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource))media=true;
        if(!foreground||!safeUrl(origin)||!sameSite(originHost,host(origin))||!media){request.deny();return;}
        Set<String> approved=getSharedPreferences("tv-web-permissions",0).getStringSet("media",Collections.emptySet());
        if(approved.contains(origin)){request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});return;}
        cancelPermission();mediaRequest=request;
        mediaDialog=new AlertDialog.Builder(this).setTitle(t("Reprodução protegida","Protected playback","Reproducción protegida")).setMessage(host(origin)+t(" solicita o identificador de mídia do dispositivo para seu reprodutor oficial."," requests the device media identifier for its official player."," solicita el identificador multimedia del dispositivo para su reproductor oficial."))
            .setPositiveButton(t("Permitir","Allow","Permitir"),(dialog,which)->{if(mediaRequest!=request)return;mediaRequest=null;mediaDialog=null;Set<String> next=new HashSet<>(approved);next.add(origin);getSharedPreferences("tv-web-permissions",0).edit().putStringSet("media",next).apply();request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});})
            .setNegativeButton(t("Cancelar","Cancel","Cancelar"),(dialog,which)->cancelPermission()).setOnCancelListener(dialog->cancelPermission()).show();
    }
    private void cancelPermission(){PermissionRequest old=mediaRequest;mediaRequest=null;if(mediaDialog!=null){mediaDialog.dismiss();mediaDialog=null;}if(old!=null)old.deny();}
    private void hideFullscreen(){WebChromeClient.CustomViewCallback old=customCallback;customCallback=null;fullscreen.removeAllViews();fullscreen.setVisibility(View.GONE);if(old!=null)old.onCustomViewHidden();}
    private void disposeWeb(){cancelPermission();hideFullscreen();WebView old=web;web=null;if(old!=null){content.removeView(old);old.stopLoading();old.onPause();old.setWebChromeClient(null);old.setWebViewClient(new WebViewClient());old.removeAllViews();old.destroy();}}
    @Override protected void onResume(){super.onResume();foreground=true;if(web!=null){web.onResume();web.resumeTimers();}getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    @Override protected void onPause(){foreground=false;main.removeCallbacksAndMessages(null);cancelPermission();if(web!=null){web.pauseTimers();CookieManager.getInstance().flush();disposeWeb();}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause();}
    @Override protected void onStop(){super.onStop();if(!isChangingConfigurations())finish();}
    @Override protected void onDestroy(){destroyed=true;instances--;main.removeCallbacksAndMessages(null);if(fullscreen!=null)disposeWeb();super.onDestroy();
        // This process hosts only this activity. End Chromium as well as the page when it closes.
        if(isFinishing()&&(getPackageName()+":tvweb").equals(Application.getProcessName()))main.postDelayed(()->{if(instances==0)android.os.Process.killProcess(android.os.Process.myPid());},750);
    }
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_CRITICAL&&!foreground)finish();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(mediaDialog!=null)return super.dispatchKeyEvent(event);
        if(key==KeyEvent.KEYCODE_BACK){if(!down){if(customCallback!=null){hideFullscreen();showTools(false);}else finish();}return true;}
        if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_GUIDE){if(!down)showTools(!toolbar.hasFocus());return true;}
        if(!down&&longOk&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){longOk=false;return true;}
        if(toolbar.hasFocus())return super.dispatchKeyEvent(event);
        if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){if(down&&web!=null)web.scrollBy(0,(key==KeyEvent.KEYCODE_CHANNEL_UP?-1:1)*Math.round(web.getHeight()*.65f));return true;}
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(!down)video(key==KeyEvent.KEYCODE_MEDIA_PAUSE?"pause":key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE?"toggle":"play");return true;}
        if(cursor&&web!=null){
            if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){if(down)pointer.move(key,event.getRepeatCount());return true;}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(down&&event.getRepeatCount()>0){longOk=true;showTools(true);}else if(!down){if(!longOk)pointer.click();longOk=false;}return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    private final class Pointer extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private float x=-1,y=-1;
        Pointer(){super(LiveTvWebActivity.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);setWillNotDraw(false);}
        protected void onDraw(Canvas canvas){if(x<0){x=getWidth()*.5f;y=getHeight()*.5f;}paint.setColor(0xCC142033);canvas.drawCircle(x,y,dp(9),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Color.WHITE);canvas.drawCircle(x,y,dp(8),paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,dp(2),paint);}
        void move(int key,int repeats){if(x<0){x=getWidth()*.5f;y=getHeight()*.5f;}int step=dp(repeats>6?28:12);float nextX=x+(key==KeyEvent.KEYCODE_DPAD_RIGHT?step:key==KeyEvent.KEYCODE_DPAD_LEFT?-step:0),nextY=y+(key==KeyEvent.KEYCODE_DPAD_DOWN?step:key==KeyEvent.KEYCODE_DPAD_UP?-step:0);
            if(web!=null&&customCallback==null&&(nextY<dp(14)||nextY>getHeight()-dp(14)))web.scrollBy(0,key==KeyEvent.KEYCODE_DPAD_UP?-step*3:step*3);
            x=Math.max(dp(12),Math.min(getWidth()-dp(12),nextX));y=Math.max(dp(12),Math.min(getHeight()-dp(12),nextY));invalidate();}
        void click(){View target=customCallback==null?web:fullscreen;if(target==null)return;int[] at=new int[2],base=new int[2];target.getLocationOnScreen(at);getLocationOnScreen(base);float tx=x+base[0]-at[0],ty=y+base[1]-at[1];long time=SystemClock.uptimeMillis();MotionEvent press=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,tx,ty,0),release=MotionEvent.obtain(time,time+40,MotionEvent.ACTION_UP,tx,ty,0);press.setSource(InputDevice.SOURCE_TOUCHSCREEN);release.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{target.dispatchTouchEvent(press);target.dispatchTouchEvent(release);}finally{press.recycle();release.recycle();}}
    }
}
