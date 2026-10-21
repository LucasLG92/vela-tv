package tv.vela.launcher;

import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One hardware-backed player, explicitly started by the viewer and released on every pause. */
final class LiveTvPlayer extends FrameLayout implements SurfaceHolder.Callback {
    interface Actions {void back();void zap(int direction);void favorite();void external();}
    private final LiveTvActivity activity;private final Actions actions;
    final SurfaceView video;private final FrameLayout stage,panel;
    private final TextView channel,programme,message,favorite;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<TextView> buttons=new ArrayList<>();
    MediaPlayer player;
    private AudioFocusRequest audioFocus;private final AudioManager audio;
    LiveTvData.Channel selected;
    boolean active,rendered,prepared;
    private boolean paused;
    private int signal;
    private long startedAt;
    private final Runnable hide=this::hideControls;
    private final Runnable timeout=()->failed();
    LiveTvPlayer(LiveTvActivity a,Actions actions){
        super(a);activity=a;this.actions=actions;setBackgroundColor(0xFF000000);audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
        video=new SurfaceView(a);video.getHolder().addCallback(this);addView(video,new LayoutParams(-1,-1,Gravity.CENTER));
        stage=new FrameLayout(a);addView(stage,new LayoutParams(1920,1080));
        TextView live=text(Ui.tr("AO VIVO","LIVE","EN DIRECTO"),19,0xFFF7F7FA,Ui.semibold);live.setLetterSpacing(.12f);put(stage,live,100,64,500,45);
        message=text("",27,0xFFDFE5F1,Ui.medium);message.setGravity(Gravity.CENTER);put(stage,message,260,420,1400,170);
        panel=new FrameLayout(a);GradientDrawable shade=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xEA070C15,0xFF070C15});panel.setBackground(shade);put(stage,panel,0,700,1920,380);
        channel=text("",37,0xFFF7F7FA,Ui.displaySemibold);put(panel,channel,100,80,1650,57);
        programme=text("",23,0xFFB6C3D5,Ui.regular);programme.setSingleLine(true);programme.setEllipsize(android.text.TextUtils.TruncateAt.END);put(panel,programme,100,147,1710,44);
        control(Ui.tr("Canais","Channels","Canales"),100,actions::back);
        favorite=control(Ui.tr("Favorito","Favorite","Favorito"),440,actions::favorite);
        control(Ui.tr("Outra emissão","Other signal","Otra señal"),780,()->{signal++;startSignal();});
        control(Ui.tr("Web integrada","In-app web","Web integrada"),1120,actions::external);
        TextView hint=text(Ui.tr("↑ / ↓ trocar canal    ·    OK controles    ·    Voltar canais","↑ / ↓ change channel    ·    OK controls    ·    Back channels","↑ / ↓ cambiar canal    ·    OK controles    ·    Atrás canales"),18,0xFF8998AF,Ui.regular);put(panel,hint,100,320,1720,36);
    }
    private TextView text(String value,int size,int color,Typeface font){TextView t=new TextView(activity);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);t.setTextColor(color);t.setTypeface(font);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private TextView control(String name,int x,Runnable action){TextView b=text(name,23,0xFFF7F7FA,Ui.medium);b.setGravity(Gravity.CENTER);b.setId(View.generateViewId());b.setFocusable(true);b.setClickable(true);b.setDefaultFocusHighlightEnabled(false);b.setBackground(round(0xFF222B3B));put(panel,b,x,218,310,70);buttons.add(b);b.setOnClickListener(v->{action.run();showControls();});b.setOnFocusChangeListener((v,f)->{b.setBackground(round(f?0xFFF7F7FA:0xFF222B3B));b.setTextColor(f?0xFF131A25:0xFFF7F7FA);if(f)delayHide();});return b;}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(15);return d;}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){float s=Math.min(w/1920f,h/1080f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(s);stage.setScaleY(s);stage.setTranslationX((w-1920*s)/2);stage.setTranslationY((h-1080*s)/2);resize();}
    void select(LiveTvData.Channel ch){selected=ch;signal=0;paused=false;channel.setText(ch.name);buttons.get(3).setText(ch.xtreamId.isEmpty()?Ui.tr("Web integrada","In-app web","Web integrada"):"TiviMate");showControls();if(active)startSignal();}
    void metadata(String title,boolean chosen){programme.setText(title);favorite.setText((chosen?"★ ":"☆ ")+Ui.tr("Favorito","Favorite","Favorito"));}
    void resume(){if(active)return;active=true;setKeepScreenOn(true);if(selected!=null&&video.getHolder().getSurface().isValid())startSignal();}
    void pause(){active=false;main.removeCallbacksAndMessages(null);release();setKeepScreenOn(false);}
    private void startSignal(){
        release();if(!active||selected==null||!selected.playable()||!video.getHolder().getSurface().isValid())return;
        message.setVisibility(VISIBLE);message.setText(Ui.tr("Conectando a ","Connecting to ","Conectando con ")+selected.name+"…");panel.setVisibility(VISIBLE);
        audioFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener(focus->{if(focus<0&&active){release();message.setText(Ui.tr("Áudio interrompido. Escolha outra emissão para continuar.","Audio interrupted. Select Other signal to resume.","Audio interrumpido. Elige Otra señal para continuar."));message.setVisibility(VISIBLE);showControls();}},main).build();
        if(audio!=null&&audio.requestAudioFocus(audioFocus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){release();message.setText(Ui.tr("Áudio indisponível. Tente novamente.","Audio unavailable. Try again.","Audio no disponible. Vuelve a intentarlo."));return;}
        MediaPlayer next=new MediaPlayer();player=next;rendered=false;prepared=false;paused=false;
        try{
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            next.setSurface(video.getHolder().getSurface());next.setScreenOnWhilePlaying(true);
            next.setOnVideoSizeChangedListener((p,w,h)->{if(p==player)resize();});
            next.setOnPreparedListener(p->{if(active&&p==player){prepared=true;p.start();}});
            next.setOnInfoListener((p,what,extra)->{if(active&&p==player&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){rendered=true;startedAt=SystemClock.elapsedRealtime();main.removeCallbacks(timeout);message.setVisibility(GONE);LiveTvData.prefs(activity).edit().putString("last-"+LiveTvData.hash(LiveTvData.source(activity)),selected.id).apply();delayHide();}return false;});
            next.setOnErrorListener((p,what,extra)->{if(active&&p==player)failed();return true;});
            next.setOnCompletionListener(p->{if(active&&p==player)failed();});
            List<String> urls=selected.xtreamId.isEmpty()?selected.streams:new XtreamClient(activity).streams(selected);if(urls.isEmpty())throw new IllegalStateException();signal=Math.floorMod(signal,urls.size());
            next.setDataSource(activity,Uri.parse(urls.get(signal)),Collections.singletonMap("User-Agent","Vela"));next.prepareAsync();main.postDelayed(timeout,20_000);
        }catch(Exception e){failed();}
    }
    private void failed(){
        release();if(!active)return;message.setVisibility(VISIBLE);message.setText(selected!=null&&!selected.xtreamId.isEmpty()?Ui.tr("Não foi possível abrir a emissão. Tente Outra emissão ou abra o TiviMate.","Could not open this signal. Try Other signal or open TiviMate.","No se ha podido abrir la señal. Prueba Otra señal o abre TiviMate."):Ui.tr("Não foi possível abrir esta emissão. Tente Outra emissão ou Web integrada.","This signal could not be opened. Try Other signal or In-app web.","No se ha podido abrir esta señal. Prueba Otra señal o Web integrada."));showControls();
    }
    private void resize(){if(player==null)return;try{int vw=player.getVideoWidth(),vh=player.getVideoHeight(),w=getWidth(),h=getHeight();if(vw<=0||vh<=0||w<=0||h<=0)return;float s=Math.min(w/(float)vw,h/(float)vh);video.setLayoutParams(new LayoutParams(Math.round(vw*s),Math.round(vh*s),Gravity.CENTER));}catch(IllegalStateException ignored){}}
    private void release(){
        main.removeCallbacks(timeout);main.removeCallbacks(hide);MediaPlayer old=player;player=null;prepared=false;rendered=false;
        if(old!=null){old.setOnPreparedListener(null);old.setOnInfoListener(null);old.setOnErrorListener(null);old.setOnCompletionListener(null);old.setOnVideoSizeChangedListener(null);old.release();}
        if(audio!=null&&audioFocus!=null)audio.abandonAudioFocusRequest(audioFocus);audioFocus=null;
    }
    private void delayHide(){main.removeCallbacks(hide);if(rendered&&!paused)main.postDelayed(hide,6000);}
    private void hideControls(){if(rendered&&!paused){panel.setVisibility(GONE);message.setVisibility(GONE);}}
    private void showControls(){panel.setVisibility(VISIBLE);buttons.get(0).requestFocus();delayHide();}
    boolean key(KeyEvent event){
        int key=event.getKeyCode();if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
        if(key==KeyEvent.KEYCODE_BACK){actions.back();return true;}
        if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN||panel.getVisibility()!=VISIBLE&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){
            actions.zap(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_DPAD_UP?1:-1);return true;
        }
        if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&panel.getVisibility()!=VISIBLE){showControls();return true;}
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&prepared&&player!=null){try{if(player.isPlaying()){player.pause();paused=true;showControls();}else{player.start();paused=false;delayHide();}}catch(IllegalStateException ignored){}return true;}
        if(panel.getVisibility()==VISIBLE)delayHide();return false;
    }
    @Override public void surfaceCreated(SurfaceHolder holder){if(active&&selected!=null)startSignal();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){release();}
}
