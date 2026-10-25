package tv.vela.launcher;

import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.media.tv.*;
import android.net.Uri;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One hardware-backed player, explicitly started by the viewer and released on every pause. */
final class LiveTvPlayer extends FrameLayout implements SurfaceHolder.Callback {
    interface Actions {void back();void zap(int direction);void channels(int page);void choose(LiveTvData.Channel channel);default void previous(){}default void started(LiveTvData.Channel channel){}default void favorite(LiveTvData.Channel channel){}default void channels(int page,boolean favorites){channels(page);}}
    private final android.app.Activity activity;private final Actions actions;
    final SurfaceView video;private final FrameLayout stage,panel,intro,quickDrawer;
    private final TextView channel,programme,message,format,programmeTiming,quickGroup,quickStatus,quickPrevious,quickNext,quickClose;
    private final UpNextView nextProgramme;private final ProgressBar programmeProgress;private final LiveTvLogos.LogoView channelLogo;
    private final ListView quickList;private final BaseAdapter quickAdapter;
    private final TextView quickAll,quickFavorites,quickRecent;private final TvRemotePress selectPress,pickerPress;
    private Set<String> favoriteIds=Collections.emptySet();boolean pickerFavorites,pickerRecent;private boolean historyNotified;
    private LiveTvLogos artwork;private boolean pickerLoading;private int pickerDirection;
    List<LiveTvData.Channel> pickerChannels=Collections.emptyList();Map<String,LiveTvData.Guide> pickerGuide=Collections.emptyMap();int pickerPage,pickerTotal;
    private final Runnable hideIntro=this::concealIntro;
    TvView tuner;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Placement> placements=new ArrayList<>();
    private float uiScale=1;private int controlsWidth,controlsHeight;
    private static final class Placement {
        final View view;final int x,y,h;int w;final float textSize;
        Placement(View v,int left,int top,int width,int height){view=v;x=left;y=top;w=width;h=height;textSize=v instanceof TextView?((TextView)v).getTextSize():0;}
    }
    MediaPlayer player;
    private AudioFocusRequest audioFocus;private final AudioManager audio;
    LiveTvData.Channel selected;
    boolean active,rendered,prepared;
    private boolean paused;
    private TvSignalFallback signals;
    private TvSignalFallback.Signal signal;
    private final TvSignalFallback.Progress progress=new TvSignalFallback.Progress();
    private int generation;
    private final Runnable hide=this::hideControls;
    private final Runnable timeout=()->failed();
    private final Runnable retry=this::startSignal;
    private final Runnable watch=new Runnable(){public void run(){
        if(!active||player==null||!rendered)return;
        try{if(progress.stalled(SystemClock.elapsedRealtime(),player.getCurrentPosition(),paused)){failed();return;}}catch(IllegalStateException unavailable){failed();return;}
        main.postDelayed(this,TvSignalFallback.CHECK_INTERVAL_MS);
    }};
    LiveTvPlayer(android.app.Activity a,Actions actions){
        super(a);activity=a;this.actions=actions;setBackgroundColor(0xFF000000);setFocusable(true);setFocusableInTouchMode(true);setClipChildren(false);setClipToPadding(false);audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
        video=new SurfaceView(a);video.getHolder().addCallback(this);addView(video,new LayoutParams(-1,-1,Gravity.CENTER));
        stage=new FrameLayout(a);addView(stage,new LayoutParams(-1,-1));
        intro=new FrameLayout(a);put(stage,intro,88,56,1744,52);
        TextView live=text("●  "+Ui.tr("AO VIVO","LIVE","EN DIRECTO"),17,0xFFF7F7FA,Ui.semibold);live.setLetterSpacing(.08f);live.setGravity(Gravity.CENTER);live.setBackground(round(0xBC262831));put(intro,live,0,0,198,52);
        format=text("",18,0xFFF0F2F7,Ui.medium);format.setGravity(Gravity.CENTER);format.setBackground(round(0xDA262831));put(intro,format,1474,0,270,52);
        message=text("",27,0xFFDFE5F1,Ui.medium);message.setGravity(Gravity.CENTER);put(stage,message,260,420,1400,170);
        panel=new FrameLayout(a);GradientDrawable shade=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xEB090B10,0xF5090B10,0xFF090B10});shade.setColors(new int[]{0x00000000,0xEB090B10,0xF5090B10,0xFF090B10},new float[]{0,.18f,.62f,1});panel.setBackground(shade);put(stage,panel,0,752,1920,328);
        channelLogo=new LiveTvLogos.LogoView(a,"");channelLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);channelLogo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);put(panel,channelLogo,88,68,88,88);
        channel=text("",23,0xFFC5CAD3,Ui.medium);channel.setSingleLine();channel.setEllipsize(android.text.TextUtils.TruncateAt.END);put(panel,channel,216,52,1516,34);
        programme=text("",37,0xFFF7F7FA,Ui.displaySemibold);programme.setMaxLines(2);programme.setGravity(Gravity.TOP);programme.setEllipsize(android.text.TextUtils.TruncateAt.END);put(panel,programme,216,92,1516,96);
        programmeTiming=text("",19,0xFFC5CAD3,Ui.medium);put(panel,programmeTiming,216,195,1516,28);
        nextProgramme=new UpNextView(a);put(panel,nextProgramme,1216,46,616,194);
        programmeProgress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);programmeProgress.setMax(1000);programmeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.accent(a)));programmeProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(VelaTheme.RAISED));put(panel,programmeProgress,216,242,1516,4);
        TextView hint=text(Ui.tr("↑ ↓ Explorar · CH+/− Trocar · → Canais · OK Informação · Manter OK ★","↑ ↓ Browse · CH+/− Change · → Channels · OK Information · Hold OK ★","↑ ↓ Explorar · CH+/− Cambiar · → Canales · OK Información · Mantener OK ★"),16,0xFF929CAE,Ui.regular);put(panel,hint,216,274,1516,25);
        quickDrawer=new FrameLayout(a);quickDrawer.setBackground(round(VelaTheme.INK));quickDrawer.setVisibility(GONE);put(stage,quickDrawer,1224,32,664,1016);
        TextView heading=text(Ui.tr("Canais","Channels","Canales"),34,0xFFF7F7FA,Ui.displaySemibold);put(quickDrawer,heading,32,30,280,54);TextView last=pickerButton(Ui.tr("Anterior","Previous","Canal anterior"));put(quickDrawer,last,324,30,232,54);last.setOnClickListener(v->{closeChannels(true);actions.previous();});
        quickGroup=text("",19,VelaTheme.SECONDARY,Ui.medium);quickGroup.setSingleLine();quickGroup.setEllipsize(android.text.TextUtils.TruncateAt.END);put(quickDrawer,quickGroup,32,92,584,32);
        quickClose=pickerButton("×");quickClose.setContentDescription(Ui.tr("Fechar canais","Close channels","Cerrar canales"));put(quickDrawer,quickClose,570,30,62,54);quickClose.setOnClickListener(v->closeChannels(true));
        quickList=new ListView(a);quickList.setId(View.generateViewId());quickList.setDivider(null);quickList.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));quickList.setVerticalScrollBarEnabled(false);quickList.setOverScrollMode(OVER_SCROLL_NEVER);quickList.setCacheColorHint(android.graphics.Color.TRANSPARENT);put(quickDrawer,quickList,24,202,616,664);
        quickAdapter=new BaseAdapter(){public int getCount(){return pickerChannels.size();}public Object getItem(int n){return pickerChannels.get(n);}public long getItemId(int n){return pickerChannels.get(n).id.hashCode();}public View getView(int n,View recycled,ViewGroup parent){ChannelRow row=recycled instanceof ChannelRow?(ChannelRow)recycled:new ChannelRow();row.bind(pickerChannels.get(n),n);return row;}};
        quickList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){styleChannelRows();}public void onNothingSelected(AdapterView<?> p){styleChannelRows();}});quickList.setOnFocusChangeListener((v,f)->styleChannelRows());
        quickList.setOnItemClickListener((parent,v,n,id)->{if(pickerLoading||n>=pickerChannels.size())return;LiveTvData.Channel ch=pickerChannels.get(n);closeChannels(true);if(selected==null||!selected.id.equals(ch.id))actions.choose(ch);});
        quickList.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;int index=quickList.getSelectedItemPosition();if(key==KeyEvent.KEYCODE_DPAD_DOWN&&index==pickerChannels.size()-1&&(pickerPage+1)*12<pickerTotal){requestChannels(pickerPage+1);return true;}if(key==KeyEvent.KEYCODE_DPAD_UP&&index==0&&pickerPage>0){requestChannels(pickerPage-1);return true;}return false;});
        quickPrevious=pickerButton("‹");quickPrevious.setContentDescription(Ui.tr("Página anterior","Previous page","Página anterior"));put(quickDrawer,quickPrevious,32,900,84,52);quickPrevious.setOnClickListener(v->requestChannels(pickerPage-1));
        quickNext=pickerButton("›");quickNext.setContentDescription(Ui.tr("Próxima página","Next page","Página siguiente"));put(quickDrawer,quickNext,548,900,84,52);quickNext.setOnClickListener(v->requestChannels(pickerPage+1));
        quickStatus=text("",17,VelaTheme.SECONDARY,Ui.medium);quickStatus.setGravity(Gravity.CENTER);put(quickDrawer,quickStatus,126,900,412,52);
        TextView quickHint=text(Ui.tr("↑ ↓ Explorar · OK Ver · ← Voltar ao vídeo","↑ ↓ Browse · OK Watch · Hold OK ★ · Back Close","↑ ↓ Explorar · OK Ver · Mantener OK ★ · Atrás Cerrar"),16,0xFF929CAE,Ui.regular);put(quickDrawer,quickHint,32,968,600,26);
        quickAll=pickerButton(Ui.tr("Lista","List","Lista"));put(quickDrawer,quickAll,32,132,184,50);quickFavorites=pickerButton("★ "+Ui.tr("Favoritos","Favorites","Favoritos"));put(quickDrawer,quickFavorites,224,132,200,50);quickRecent=pickerButton(Ui.tr("Recentes","Recent","Recientes"));put(quickDrawer,quickRecent,432,132,200,50);quickRecent.setOnClickListener(v->{if(pickerRecent)return;pickerRecent=true;pickerFavorites=false;pickerLoading=true;pickerDirection=0;updatePickerStatus();actions.channels(0,false);});quickAll.setOnClickListener(v->filterChannels(false));quickFavorites.setOnClickListener(v->filterChannels(true));
        pickerPress=new TvRemotePress(quickList,()->{int at=quickList.getSelectedItemPosition();if(!pickerLoading&&at>=0&&at<pickerChannels.size()){LiveTvData.Channel item=pickerChannels.get(at);closeChannels(true);if(selected==null||!selected.id.equals(item.id))actions.choose(item);}},()->{int at=quickList.getSelectedItemPosition();if(!pickerLoading&&at>=0&&at<pickerChannels.size())actions.favorite(pickerChannels.get(at));});
        quickList.setOnItemLongClickListener((parent,v,n,id)->{if(!pickerLoading&&n<pickerChannels.size())actions.favorite(pickerChannels.get(n));return true;});
        selectPress=new TvRemotePress(this,this::selectClick,()->{if(selected!=null)actions.favorite(selected);});
        quickAll.setNextFocusRightId(quickFavorites.getId());quickFavorites.setNextFocusLeftId(quickAll.getId());quickAll.setNextFocusDownId(quickList.getId());quickFavorites.setNextFocusDownId(quickList.getId());
        quickList.setNextFocusUpId(quickAll.getId());quickList.setNextFocusDownId(quickNext.getId());quickPrevious.setNextFocusUpId(quickList.getId());quickNext.setNextFocusUpId(quickList.getId());quickClose.setNextFocusDownId(quickList.getId());
    }
    private TextView text(String value,int size,int color,Typeface font){TextView t=new TextView(activity);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);t.setTextColor(color);t.setTypeface(font);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){placements.add(new Placement(v,x,y,w,h));LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(15*uiScale);return d;}
    /** Size the small controls once per viewport change; avoid a scaled render layer over SurfaceView. */
    @Override protected void onMeasure(int width,int height){scaleControls(MeasureSpec.getSize(width),MeasureSpec.getSize(height));super.onMeasure(width,height);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){resize();}
    private void scaleControls(int w,int h){
        if(w<=0||h<=0||w==controlsWidth&&h==controlsHeight)return;controlsWidth=w;controlsHeight=h;
        uiScale=Math.min(w/1920f,h/1080f);stage.setTranslationX((w-1920*uiScale)/2);stage.setTranslationY((h-1080*uiScale)/2);
        for(Placement item:placements){LayoutParams p=(LayoutParams)item.view.getLayoutParams();p.width=Math.round(item.w*uiScale);p.height=Math.round(item.h*uiScale);p.leftMargin=Math.round(item.x*uiScale);p.topMargin=Math.round(item.y*uiScale);item.view.setLayoutParams(p);if(item.textSize>0)((TextView)item.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,item.textSize*uiScale);}
        ((GradientDrawable)quickDrawer.getBackground()).setCornerRadius(28*uiScale);quickAdapter.notifyDataSetChanged();
    }
    void select(LiveTvData.Channel ch){closeChannels(false);selected=ch;historyNotified=false;metadata(null,null);channelLogo.setImageDrawable(null);if(artwork==null)artwork=new LiveTvLogos(activity);artwork.page();artwork.load(ch,channelLogo);intro.setVisibility(VISIBLE);signals=new TvSignalFallback(ch);signal=null;paused=false;channel.setText((ch.antenna()&&!ch.number.isEmpty()?ch.number+" · ":"")+LiveTvActivity.displayName(ch.name));showControls();if(active)startSignal();}
    void metadata(LiveTvData.Programme current,LiveTvData.Programme next){
        long now=System.currentTimeMillis()/1000;if(current!=null&&!current.current(now))current=null;
        programme.setText(current==null?Ui.tr("Programação não disponível","Programme information unavailable","Programación no disponible"):current.title);
        programmeTiming.setText(current==null?"":Ui.time(current.start*1000)+"–"+Ui.time(current.end*1000)+"  ·  "+LiveTvActivity.remainingText(current.remainingMinutes(now)));
        boolean upcoming=next!=null&&(current==null||next.start>=current.end)&&nextProgramme.bind(next.title,next.start,next.end,now);if(!upcoming)nextProgramme.bind(null,0,0,now);
        int width=upcoming?922:1516;for(Placement item:placements)if((item.view==channel||item.view==programme||item.view==programmeTiming||item.view==programmeProgress)&&item.w!=width){item.w=width;LayoutParams p=(LayoutParams)item.view.getLayoutParams();p.width=Math.round(width*uiScale);item.view.setLayoutParams(p);}
        programmeProgress.setVisibility(current==null?INVISIBLE:VISIBLE);programmeProgress.setProgress(current==null?0:current.progress(now));
        // Reuse the host minute tick; an open channel list needs no extra timer.
        if(quickDrawer.getVisibility()==VISIBLE)quickAdapter.notifyDataSetChanged();
    }
    void resume(){if(active)return;active=true;if(artwork==null)artwork=new LiveTvLogos(activity);if(selected!=null)artwork.load(selected,channelLogo);signals=new TvSignalFallback(selected);signal=null;setKeepScreenOn(true);if(selected!=null&&(selected.antenna()||video.getHolder().getSurface().isValid()))startSignal();}
    void pause(){selectPress.cancel();pickerPress.cancel();active=false;main.removeCallbacksAndMessages(null);closeChannels(false);release();intro.setVisibility(GONE);channelLogo.setImageDrawable(null);if(artwork!=null){artwork.close();artwork=null;}setKeepScreenOn(false);}
    private void startSignal(){
        release();if(!active||selected==null||!selected.playable()||!selected.antenna()&&!video.getHolder().getSurface().isValid())return;
        intro.setVisibility(VISIBLE);message.setVisibility(VISIBLE);message.setText(Ui.tr("Conectando a ","Connecting to ","Conectando con ")+LiveTvActivity.displayName(selected.name)+"…");panel.setVisibility(VISIBLE);
        if(selected.antenna()){startAntenna();return;}
        if(signal==null)signal=nextSignal();if(signal==null){unavailable();return;}
        if(signals.attempts()>1)message.setText(Ui.tr("Tentando outra emissão…","Trying another signal…","Probando otra señal…"));
        final int attempt=generation;
        audioFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener(focus->{if(focus<0&&active&&attempt==generation){release();message.setText(Ui.tr("Áudio interrompido. Pressione OK para tentar novamente.","Audio interrupted. Press OK to retry.","Audio interrumpido. Pulsa OK para reintentar."));message.setVisibility(VISIBLE);showControls();}},main).build();
        if(audio!=null&&audio.requestAudioFocus(audioFocus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){release();message.setText(Ui.tr("Áudio indisponível. Tente novamente.","Audio unavailable. Try again.","Audio no disponible. Vuelve a intentarlo."));return;}
        try{
            MediaPlayer next=new MediaPlayer();player=next;rendered=false;prepared=false;paused=false;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            next.setSurface(video.getHolder().getSurface());next.setScreenOnWhilePlaying(true);
            next.setOnVideoSizeChangedListener((p,w,h)->{if(p==player){resize();quality(h);}});
            next.setOnPreparedListener(p->{if(active&&p==player){try{prepared=true;p.start();boolean hasAudio=false,hasVideo=false;for(MediaPlayer.TrackInfo track:p.getTrackInfo()){hasAudio|=track.getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO;hasVideo|=track.getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO;}if(hasAudio&&!hasVideo)playbackStarted(p);}catch(IllegalStateException unavailable){failed();}}});
            next.setOnInfoListener((p,what,extra)->{if(active&&p==player&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){playbackStarted(p);}return false;});
            next.setOnErrorListener((p,what,extra)->{if(active&&p==player)failed();return true;});
            next.setOnCompletionListener(p->{if(active&&p==player)failed();});
            format.setText(Ui.tr("Qualidade automática","Automatic quality","Calidad automática"));
            next.setDataSource(activity,Uri.parse(signal.url),Collections.singletonMap("User-Agent","Vela"));next.prepareAsync();main.postDelayed(timeout,TvSignalFallback.START_TIMEOUT_MS);
        }catch(Exception e){failed();}
    }
    private TvSignalFallback.Signal nextSignal(){return signals==null?null:signals.next(ch->ch.xtreamId.isEmpty()?ch.streams:new XtreamClient(activity).streams(ch));}
    private void startAntenna(){
        video.setVisibility(GONE);format.setText(Ui.tr("ANTENA","ANTENNA","ANTENA"));TvView next=new TvView(activity);tuner=next;addView(next,0,new LayoutParams(-1,-1));
        next.setCallback(new TvView.TvInputCallback(){
            public void onVideoAvailable(String input){if(active&&tuner==next){rendered=true;prepared=true;notifyStarted();main.removeCallbacks(timeout);message.setVisibility(GONE);hideIntroSoon();delayHide();}}
            public void onVideoUnavailable(String input,int reason){if(tuner!=next||!active)return;if(reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY){rendered=true;prepared=true;notifyStarted();main.removeCallbacks(timeout);message.setVisibility(GONE);hideIntroSoon();delayHide();return;}rendered=false;main.removeCallbacks(hide);message.setVisibility(VISIBLE);message.setText(reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL?Ui.tr("Sem sinal de antena. Confira o cabo.","No antenna signal. Check the cable.","Sin señal de antena. Comprueba el cable."):Ui.tr("Sintonizando…","Tuning…","Sintonizando…"));if(reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL)main.removeCallbacks(timeout);showControls();}
            public void onConnectionFailed(String input){if(active&&tuner==next)failed();}
            public void onDisconnected(String input){if(active&&tuner==next)failed();}
            public void onContentBlocked(String input,TvContentRating rating){if(tuner==next){message.setText(Ui.tr("Conteúdo bloqueado pelos controles da TV.","Content blocked by the TV parental controls.","Contenido bloqueado por los controles de la TV."));message.setVisibility(VISIBLE);main.removeCallbacks(timeout);showControls();}}
            public void onTracksChanged(String input,List<TvTrackInfo> tracks){if(tuner==next&&tracks!=null)for(TvTrackInfo track:tracks)if(track.getType()==TvTrackInfo.TYPE_VIDEO&&track.getVideoHeight()>0)quality(track.getVideoHeight());}
        });
        try{next.tune(selected.inputId,android.media.tv.TvContract.buildChannelUri(Long.parseLong(selected.epgId)));next.setStreamVolume(1f);main.postDelayed(timeout,25_000);}catch(RuntimeException unavailable){failed();}
    }
    private void failed(){
        release();if(!active)return;
        if(selected!=null&&!selected.antenna()&&(signal=nextSignal())!=null){message.setVisibility(VISIBLE);message.setText(Ui.tr("Tentando outra emissão…","Trying another signal…","Probando otra señal…"));panel.setVisibility(VISIBLE);main.postDelayed(retry,250);return;}
        unavailable();
    }
    private void unavailable(){message.setVisibility(VISIBLE);message.setText(selected!=null&&selected.antenna()?Ui.tr("Sem sinal de antena. Confira o cabo e os canais sintonizados.","No antenna signal. Check the cable and tuned channels.","Sin señal de antena. Comprueba el cable y los canales sintonizados."):Ui.tr("Nenhuma emissão disponível. Pressione OK para tentar novamente ou → para ver os canais.","No signal available. Press OK to retry or → for channels.","No hay señales disponibles. Pulsa OK para reintentar o → para ver los canales."));showControls();}
    private void resize(){if(player==null)return;try{int vw=player.getVideoWidth(),vh=player.getVideoHeight(),w=getWidth(),h=getHeight();if(vw<=0||vh<=0||w<=0||h<=0)return;float s=Math.min(w/(float)vw,h/(float)vh);video.setLayoutParams(new LayoutParams(Math.round(vw*s),Math.round(vh*s),Gravity.CENTER));}catch(IllegalStateException ignored){}}
    private void release(){
        generation++;main.removeCallbacks(hideIntro);main.removeCallbacks(timeout);main.removeCallbacks(hide);main.removeCallbacks(retry);main.removeCallbacks(watch);TvView oldTuner=tuner;tuner=null;if(oldTuner!=null){oldTuner.setCallback(null);oldTuner.reset();removeView(oldTuner);}video.setVisibility(VISIBLE);MediaPlayer old=player;player=null;prepared=false;rendered=false;
        if(old!=null){old.setOnPreparedListener(null);old.setOnInfoListener(null);old.setOnErrorListener(null);old.setOnCompletionListener(null);old.setOnVideoSizeChangedListener(null);old.release();}
        if(audio!=null&&audioFocus!=null)audio.abandonAudioFocusRequest(audioFocus);audioFocus=null;
    }
    private void quality(int height){format.setText(height>=2160?"4K UHD · "+height+"p":height>=1080?"Full HD · "+height+"p":height>=720?"HD · "+height+"p":height>0?height+"p":Ui.tr("Qualidade automática","Automatic quality","Calidad automática"));}
    private void concealIntro(){intro.setVisibility(GONE);}
    private void hideIntroSoon(){main.removeCallbacks(hideIntro);main.postDelayed(hideIntro,4000);}
    private void delayHide(){main.removeCallbacks(hide);if(rendered&&!paused&&quickDrawer.getVisibility()!=VISIBLE)main.postDelayed(hide,6000);}
    private void hideControls(){if(rendered&&!paused&&quickDrawer.getVisibility()!=VISIBLE){panel.setVisibility(GONE);message.setVisibility(GONE);requestFocus();}}
    private void showControls(){if(quickDrawer.getVisibility()==VISIBLE)return;panel.setVisibility(VISIBLE);requestFocus();delayHide();}
    private TextView pickerButton(String label){TextView b=text(label,27,0xFFF7F7FA,Ui.medium);b.setId(View.generateViewId());b.setFocusable(true);b.setClickable(true);b.setGravity(Gravity.CENTER);b.setDefaultFocusHighlightEnabled(false);b.setBackground(round(VelaTheme.CARD));b.setOnFocusChangeListener((v,f)->{b.setTextColor(f?VelaTheme.INK:0xFFF7F7FA);b.setBackground(round(f?Appearance.accent(activity):VelaTheme.CARD));});return b;}
    private void styleChannelRows(){for(int i=0;i<quickList.getChildCount();i++)if(quickList.getChildAt(i) instanceof ChannelRow)((ChannelRow)quickList.getChildAt(i)).highlight(quickList.hasFocus()&&quickList.getFirstVisiblePosition()+i==quickList.getSelectedItemPosition());}
    private final class ChannelRow extends FrameLayout {
        final LiveTvLogos.LogoView logo;final TextView name,show,remaining;final ProgressBar bar;String bound="";
        ChannelRow(){super(activity);logo=new LiveTvLogos.LogoView(activity,"");logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(logo);name=text("",22,0xFFF7F7FA,Ui.medium);show=text("",16,VelaTheme.SECONDARY,Ui.regular);remaining=text("",15,VelaTheme.SECONDARY,Ui.medium);for(TextView label:new TextView[]{name,show,remaining}){label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(label);}bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.accent(activity)));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(VelaTheme.RAISED));addView(bar);}
        private void at(View view,int x,int y,int w,int h){LayoutParams p=new LayoutParams(Math.round(w*uiScale),Math.round(h*uiScale));p.leftMargin=Math.round(x*uiScale);p.topMargin=Math.round(y*uiScale);view.setLayoutParams(p);}
        void bind(LiveTvData.Channel ch,int index){setLayoutParams(new AbsListView.LayoutParams(-1,Math.round(108*uiScale)));at(logo,16,25,60,60);at(name,102,12,492,30);at(show,102,46,492,24);at(remaining,102,76,492,22);at(bar,102,100,480,3);name.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*uiScale);show.setTextSize(TypedValue.COMPLEX_UNIT_PX,16*uiScale);remaining.setTextSize(TypedValue.COMPLEX_UNIT_PX,15*uiScale);name.setText((selected!=null&&selected.id.equals(ch.id)?"▶  ":"")+(TvChannelNames.favorite(ch,favoriteIds)?"★ ":"")+LiveTvActivity.displayName(ch.name));LiveTvData.Guide g=pickerGuide.get(ch.id);LiveTvData.Programme p=g==null?null:g.current(System.currentTimeMillis()/1000);show.setText(p==null?Ui.tr("Sem programação","No programme information","Sin programación"):p.title);remaining.setVisibility(p==null?INVISIBLE:VISIBLE);remaining.setText(p==null?"":LiveTvActivity.remainingText(p.remainingMinutes(System.currentTimeMillis()/1000)));bar.setVisibility(p==null?INVISIBLE:VISIBLE);bar.setProgress(p==null?0:p.progress(System.currentTimeMillis()/1000));setContentDescription(name.getText()+" · "+show.getText()+(p==null?"":" · "+remaining.getText()));String key=ch.id+":"+ch.logo;if(!key.equals(bound)){bound=key;logo.setImageDrawable(null);if(artwork!=null)artwork.load(ch,logo);}highlight(quickList.hasFocus()&&index==quickList.getSelectedItemPosition());}
        void highlight(boolean focus){setBackground(focus?round(Appearance.accent(activity)):null);name.setTextColor(focus?VelaTheme.INK:0xFFF7F7FA);show.setTextColor(focus?0xFF61636D:VelaTheme.SECONDARY);remaining.setTextColor(focus?0xFF61636D:VelaTheme.SECONDARY);}
    }
    void channels(List<LiveTvData.Channel> values,Map<String,LiveTvData.Guide> programmes,String group,int page,int total){
        String focused="";int oldPosition=quickList.getSelectedItemPosition(),oldTop=quickList.getSelectedView()==null?0:quickList.getSelectedView().getTop();if(oldPosition>=0&&oldPosition<pickerChannels.size())focused=pickerChannels.get(oldPosition).id;boolean samePage=pickerPage==page;
        pickerChannels=new ArrayList<>(values.subList(0,Math.min(12,values.size())));pickerGuide=new HashMap<>(programmes);pickerPage=page;pickerTotal=total;pickerLoading=false;quickGroup.setText(group);updatePickerStatus();
        if(quickDrawer.getVisibility()==VISIBLE){quickList.setAdapter(null);if(artwork!=null)artwork.page();if(channelLogo.getDrawable()==null&&artwork!=null&&selected!=null)artwork.load(selected,channelLogo);quickList.setAdapter(quickAdapter);int focus=pickerDirection<0?Math.max(0,pickerChannels.size()-1):samePage?Math.max(0,Math.min(oldPosition,pickerChannels.size()-1)):0;if(samePage&&pickerDirection==0)for(int i=0;i<pickerChannels.size();i++)if(pickerChannels.get(i).id.equals(focused))focus=i;quickList.setSelectionFromTop(focus,samePage&&pickerDirection==0?oldTop:0);if(pickerChannels.isEmpty())(pickerRecent?quickRecent:quickFavorites).requestFocus();else quickList.requestFocus();}
        pickerDirection=0;
    }
    void channelGuide(Map<String,LiveTvData.Guide> programmes){pickerGuide=new HashMap<>(programmes);if(quickDrawer.getVisibility()==VISIBLE)quickAdapter.notifyDataSetChanged();}
    private void updatePickerStatus(){quickRecent.setText((pickerRecent?"✓ ":"")+Ui.tr("Recentes","Recent","Recientes"));quickAll.setText((pickerFavorites||pickerRecent?"":"✓ ")+Ui.tr("Lista","List","Lista"));quickFavorites.setText((pickerFavorites?"✓ ":"")+"★ "+Ui.tr("Favoritos","Favorites","Favoritos"));quickPrevious.setEnabled(!pickerLoading&&pickerPage>0);quickPrevious.setAlpha(quickPrevious.isEnabled()?1:.3f);quickNext.setEnabled(!pickerLoading&&(pickerPage+1)*12<pickerTotal);quickNext.setAlpha(quickNext.isEnabled()?1:.3f);quickStatus.setText(pickerLoading?Ui.tr("Carregando…","Loading…","Cargando…"):pickerTotal==0?(pickerFavorites?Ui.tr("Ainda sem favoritos","No favorites yet","Aún no tienes favoritos"):"0 / 0"):(pickerPage*12+1)+"–"+(pickerPage*12+pickerChannels.size())+" / "+pickerTotal);}
    private void requestChannels(int page){if(pickerLoading||page<0||(long)page*12>=pickerTotal)return;pickerDirection=Integer.compare(page,pickerPage);pickerLoading=true;updatePickerStatus();actions.channels(page,pickerFavorites);}
    void channelError(){pickerLoading=false;updatePickerStatus();quickStatus.setText(Ui.tr("Não foi possível carregar. Tente novamente.","Could not load. Please retry.","No se ha podido cargar. Reinténtalo."));}
    void favorites(Set<String> ids){boolean changed=!favoriteIds.equals(ids);favoriteIds=new HashSet<>(ids);if(selected!=null)channel.setText((TvChannelNames.favorite(selected,favoriteIds)?"★ ":"")+(selected.antenna()&&!selected.number.isEmpty()?selected.number+" · ":"")+LiveTvActivity.displayName(selected.name));if(quickDrawer.getVisibility()==VISIBLE){quickAdapter.notifyDataSetChanged();if(changed&&pickerFavorites&&!pickerLoading){pickerLoading=true;updatePickerStatus();actions.channels(pickerPage,true);}}}
    private void filterChannels(boolean value){if(pickerFavorites==value&&!pickerRecent)return;pickerRecent=false;pickerFavorites=value;pickerLoading=true;pickerDirection=0;updatePickerStatus();actions.channels(0,value);}
    private void selectClick(){if(!rendered&&player==null&&selected!=null){signals=new TvSignalFallback(selected);signal=null;startSignal();}else if(panel.getVisibility()==VISIBLE&&rendered&&!paused)hideControls();else showControls();}
    private void openChannels(){main.removeCallbacks(hide);panel.setVisibility(GONE);intro.setVisibility(GONE);quickDrawer.setVisibility(VISIBLE);if(artwork==null)artwork=new LiveTvLogos(activity);quickList.setAdapter(quickAdapter);updatePickerStatus();int focus=0;for(int i=0;i<pickerChannels.size();i++)if(selected!=null&&pickerChannels.get(i).id.equals(selected.id))focus=i;quickList.setSelection(focus);quickList.requestFocus();if(pickerChannels.isEmpty())(pickerRecent?quickRecent:quickFavorites).requestFocus();if((pickerFavorites||pickerRecent)&&!pickerLoading){pickerLoading=true;updatePickerStatus();actions.channels(pickerPage,pickerFavorites);}}
    private void closeChannels(boolean focus){pickerPress.cancel();selectPress.cancel();if(quickDrawer.getVisibility()!=VISIBLE)return;quickDrawer.setVisibility(GONE);quickList.setAdapter(null);if(artwork!=null)artwork.page();if(focus){if(rendered){panel.setVisibility(GONE);requestFocus();}else showControls();}}
    private void playbackStarted(MediaPlayer p){rendered=true;notifyStarted();progress.reset(SystemClock.elapsedRealtime(),p.getCurrentPosition());main.removeCallbacks(timeout);main.removeCallbacks(watch);main.postDelayed(watch,TvSignalFallback.CHECK_INTERVAL_MS);message.setVisibility(GONE);LiveTvData.prefs(activity).edit().putString("last-"+LiveTvData.hash(LiveTvData.source(activity)),selected.id).apply();hideIntroSoon();delayHide();}
    private void notifyStarted(){if(!historyNotified&&selected!=null){historyNotified=true;actions.started(selected);}}
    boolean key(KeyEvent event){
        int key=event.getKeyCode();if(quickDrawer.getVisibility()==VISIBLE&&quickList.hasFocus()&&pickerPress.key(event))return true;if(quickDrawer.getVisibility()!=VISIBLE&&selectPress.key(event))return true;if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
        if(quickDrawer.getVisibility()==VISIBLE){if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_DPAD_LEFT&&quickList.hasFocus()){closeChannels(true);return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&quickList.hasFocus())return true;return false;}
        if(key==KeyEvent.KEYCODE_LAST_CHANNEL){if(event.getRepeatCount()==0)actions.previous();return true;}if(key==KeyEvent.KEYCODE_BACK){actions.back();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT){openChannels();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){openChannels();int at=-1;for(int i=0;i<pickerChannels.size();i++)if(selected!=null&&pickerChannels.get(i).id.equals(selected.id))at=i;int next=at<0?0:at+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);if(next>=0&&next<pickerChannels.size())quickList.setSelection(next);else if(next<0&&pickerPage>0)requestChannels(pickerPage-1);else if(next>=pickerChannels.size()&&(pickerPage+1)*12<pickerTotal)requestChannels(pickerPage+1);return true;}
        if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){if(event.getRepeatCount()==0)actions.zap(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}

        if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(panel.getVisibility()==VISIBLE&&rendered&&!paused)hideControls();else showControls();return true;}
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&prepared&&player!=null){try{if(player.isPlaying()){player.pause();paused=true;main.removeCallbacks(timeout);showControls();}else{player.start();paused=false;progress.reset(SystemClock.elapsedRealtime(),player.getCurrentPosition());if(!rendered)main.postDelayed(timeout,TvSignalFallback.START_TIMEOUT_MS);delayHide();}}catch(IllegalStateException ignored){}return true;}
        if(panel.getVisibility()==VISIBLE)delayHide();return false;
    }
    @Override public void surfaceCreated(SurfaceHolder holder){if(active&&selected!=null&&!selected.antenna())startSignal();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){if(selected==null||!selected.antenna())release();}
}
