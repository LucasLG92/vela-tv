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
    private final android.app.Activity activity;private final String channelSource;private final Actions actions;
    final SurfaceView video;private final FrameLayout stage,intro,quickDrawer;private final VelaPlayerUi.Information panel;
    private final VelaPlayerUi.Signal liveBadge;private final TextView message,quickGroup,quickStatus,quickPrevious,quickNext,quickClose;
    private final LiveTvLogos.LogoView channelLogo;
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
    MediaPlayer player;VelaDvrPlayer dvr;
    private AudioFocusRequest audioFocus;private final AudioManager audio;
    LiveTvData.Channel selected;
    boolean active,rendered,prepared;
    private VelaHlsBuffer buffer;private String directOnlySignal="";private String replayUrl="";private LiveTvData.Programme replayProgramme;
    private long programmeEnd;private final VelaSleepTimer sleep;
    private boolean media3Attempt;private String frameworkSignal="";private boolean paused;private VelaPlaybackTray controls;private int controlsKey=-1;private TextView captions;private int timeShiftStatus=TvInputManager.TIME_SHIFT_STATUS_UNSUPPORTED;private long timeShiftStart,timeShiftCurrent;
    private TvSignalFallback signals;
    private TvSignalFallback.Signal signal;
    private final TvSignalFallback.Progress progress=new TvSignalFallback.Progress();
    private int generation;
    private VelaPlaybackRecovery recovery=new VelaPlaybackRecovery();
    private VelaPlaybackPolicy.Buffer limits;
    private int forcedEngine,currentEngine;private boolean systemBuffering;private Runnable remember;
    private VelaPlaybackRecovery.Failure lastFailure=VelaPlaybackRecovery.Failure.UNKNOWN;
    private final Runnable hide=this::hideControls;
    private final Runnable timeout=()->failed(VelaPlaybackRecovery.Failure.NETWORK);
    private final Runnable retry=this::startSignal;
    private final Runnable watch=new Runnable(){public void run(){
        if(!active||(player==null&&dvr==null)||!rendered)return;
        try{long token=progressToken();if(token==Long.MIN_VALUE&&!systemBuffering)progress.reset(SystemClock.elapsedRealtime(),token);else if(progress.stalled(SystemClock.elapsedRealtime(),token,paused,limits==null?TvSignalFallback.STALL_TIMEOUT_MS:limits.stallMs)){failed(VelaPlaybackRecovery.Failure.STALLED);return;}}catch(IllegalStateException unavailable){failed(VelaPlaybackRecovery.Failure.DECODER);return;}
        main.postDelayed(this,TvSignalFallback.CHECK_INTERVAL_MS);
    }};
    LiveTvPlayer(android.app.Activity a,Actions actions){
        super(a);activity=a;channelSource=LiveTvData.source(a);this.actions=actions;sleep=new VelaSleepTimer(()->actions.back());setBackgroundColor(0xFF000000);setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);setClipChildren(false);setClipToPadding(false);audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
        video=new SurfaceView(a);video.getHolder().addCallback(this);addView(video,new LayoutParams(-1,-1,Gravity.CENTER));
        stage=new FrameLayout(a);addView(stage,new LayoutParams(-1,-1));
        intro=new FrameLayout(a);put(stage,intro,88,56,1744,52);
        liveBadge=new VelaPlayerUi.Signal(a);put(intro,liveBadge,0,0,LayoutParams.WRAP_CONTENT,48);
        message=text("",27,0xFFDFE5F1,Ui.medium);message.setGravity(Gravity.CENTER);message.setPadding(28,12,28,12);message.setBackground(round(0xF215181F));message.setVisibility(GONE);put(stage,message,260,420,1400,170);
        panel=new VelaPlayerUi.Information(a);put(stage,panel,0,0,1920,1080);
        channelLogo=new LiveTvLogos.LogoView(a,"");
        channelLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        channelLogo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        channelLogo.onFilm();channelLogo.setColorListener(color->{panel.rail.color(color);panel.artAvailable(true);});panel.logo(channelLogo);
        quickDrawer=new FrameLayout(a);quickDrawer.setBackground(VelaPlayerUi.wing());quickDrawer.setVisibility(GONE);put(stage,quickDrawer,1224,0,696,1080);
        TextView heading=text(Ui.tr("Canais","Channels","Canales"),42,0xFFF7F7FA,Ui.displaySemibold);put(quickDrawer,heading,40,36,284,60);TextView last=pickerButton(Ui.tr("Anterior","Previous","Canal anterior"));put(quickDrawer,last,324,30,232,54);last.setOnClickListener(v->{closeChannels(true);actions.previous();});
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
        pickerPress=new TvRemotePress(quickList,()->{int at=quickList.getSelectedItemPosition();if(!pickerLoading&&at>=0&&at<pickerChannels.size()){LiveTvData.Channel item=pickerChannels.get(at);closeChannels(true);if(selected==null||!selected.id.equals(item.id))actions.choose(item);}},()->{int at=quickList.getSelectedItemPosition();if(!pickerLoading&&at>=0&&at<pickerChannels.size())channelOptions(pickerChannels.get(at));});
        quickList.setOnItemLongClickListener((parent,v,n,id)->{if(!pickerLoading&&n<pickerChannels.size())actions.favorite(pickerChannels.get(n));return true;});
        selectPress=new TvRemotePress(this,this::selectClick,()->{if(selected!=null)channelOptions(selected);});
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
        for(Placement item:placements){LayoutParams p=(LayoutParams)item.view.getLayoutParams();p.width=item.w<0?item.w:Math.round(item.w*uiScale);p.height=Math.round(item.h*uiScale);p.leftMargin=Math.round(item.x*uiScale);p.topMargin=Math.round(item.y*uiScale);item.view.setLayoutParams(p);if(item.textSize>0)((TextView)item.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,item.textSize*uiScale);}
        quickAdapter.notifyDataSetChanged();
    }
    void select(LiveTvData.Channel ch){selectReplay(ch,null,"");}
    void selectReplay(LiveTvData.Channel ch,LiveTvData.Programme recording,String url){replayUrl=url;replayProgramme=recording;liveBadge.reset(recording!=null);frameworkSignal="";if(controls!=null)controls.close();timeShiftStatus=TvInputManager.TIME_SHIFT_STATUS_UNSUPPORTED;timeShiftStart=timeShiftCurrent=0;closeChannels(false);selected=ch;historyNotified=false;panel.artAvailable(false);panel.rail.color(0);metadata(null,null);channelLogo.label(ch.antenna()&&!ch.number.isEmpty()?ch.number:LiveTvActivity.displayName(ch.name));channelLogo.setImageDrawable(null);if(artwork==null)artwork=new LiveTvLogos(activity);artwork.page();artwork.load(ch,channelLogo);intro.setVisibility(VISIBLE);resetNetworkSession();signals=new TvSignalFallback(ch);signal=replayUrl.isEmpty()?null:new TvSignalFallback.Signal(ch,replayUrl);paused=false;panel.channelName((ch.antenna()&&!ch.number.isEmpty()?ch.number+" · ":"")+LiveTvActivity.displayName(ch.name));showControls();if(active)startSignal();}
    void metadata(LiveTvData.Programme current,LiveTvData.Programme next){
        long now=System.currentTimeMillis()/1000;
        long end=current!=null&&current.current(now)?current.end:0;
        if(programmeEnd!=end){
            programmeEnd=end;
            if(controls!=null&&replayProgramme==null)controls.moreOptionsChanged();
        }
        panel.bind(current,next,replayProgramme,now);
        if(quickDrawer.getVisibility()==VISIBLE)quickAdapter.notifyDataSetChanged();
    }
    void resume(){if(active)return;active=true;if(artwork==null)artwork=new LiveTvLogos(activity);if(selected!=null)artwork.load(selected,channelLogo);signals=new TvSignalFallback(selected);signal=replayUrl.isEmpty()?null:new TvSignalFallback.Signal(selected,replayUrl);resetNetworkSession();setKeepScreenOn(true);if(selected!=null&&(selected.antenna()||video.getHolder().getSurface().isValid()))startSignal();}
    void pause(){sleep.close();if(controls!=null)controls.close();selectPress.cancel();pickerPress.cancel();active=false;main.removeCallbacksAndMessages(null);closeChannels(false);release();intro.setVisibility(GONE);channelLogo.setImageDrawable(null);if(artwork!=null){artwork.close();artwork=null;}setKeepScreenOn(false);}
    private void resetNetworkSession(){directOnlySignal="";recovery=new VelaPlaybackRecovery();forcedEngine=0;limits=VelaPlaybackPolicy.buffer(activity);lastFailure=VelaPlaybackRecovery.Failure.UNKNOWN;}
    private void restartNetwork(){signals=new TvSignalFallback(selected);signal=replayUrl.isEmpty()?null:new TvSignalFallback.Signal(selected,replayUrl);resetNetworkSession();startSignal();}
    private void startSignal(){
        release();if(!active||selected==null||!selected.playable()||!selected.antenna()&&!video.getHolder().getSurface().isValid())return;
        intro.setVisibility(VISIBLE);message.setVisibility(VISIBLE);message.setText(Ui.tr("Conectando a ","Connecting to ","Conectando con ")+LiveTvActivity.displayName(selected.name)+"…");panel.setVisibility(VISIBLE);
        if(selected.antenna()){startAntenna();return;}
        if(limits==null)resetNetworkSession();if(signal==null)signal=nextSignal();if(signal==null){unavailable();return;}
        if(signals.attempts()>1)message.setText(L10n.text("Trying another signal…"));
        final int attempt=generation;
        audioFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener(focus->{if(focus<0&&active&&attempt==generation){release();message.setText(Ui.tr("Áudio interrompido. Pressione OK para tentar novamente.","Audio interrupted. Press OK to retry.","Audio interrumpido. Pulsa OK para reintentar."));message.setVisibility(VISIBLE);showControls();}},main).build();
        if(audio!=null&&audio.requestAudioFocus(audioFocus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){release();message.setText(Ui.tr("Áudio indisponível. Tente novamente.","Audio unavailable. Try again.","Audio no disponible. Vuelve a intentarlo."));return;}
        try{
            rendered=false;prepared=false;paused=false;media3Attempt=false;systemBuffering=false;
            Map<String,String> headers=VelaStreamHeaders.forSignal(signal.channel,signal.url);
            if(headers.isEmpty()&&!replayUrl.isEmpty()&&!signal.channel.streams.isEmpty())headers=VelaStreamHeaders.forSignal(signal.channel,signal.channel.streams.get(0));
            String playback=signal.url;
            if(replayUrl.isEmpty()&&VelaPlaybackPanel.prefs(activity).getBoolean("timeshift",false)&&VelaPlaybackPolicy.format(playback).equals("hls")&&!directOnlySignal.equals(playback)){
                try{buffer=new VelaHlsBuffer(activity,playback,headers);playback=buffer.url();}
                catch(java.io.IOException unavailable){directOnlySignal=signal.url;}
            }
            currentEngine=buffer!=null?VelaPlaybackPolicy.MEDIA3:forcedEngine!=0?forcedEngine:VelaPlaybackPolicy.chooseEngine(activity,selected);
            if(!recovery.start(LiveTvData.hash(signal.url),currentEngine,SystemClock.elapsedRealtime())){release();unavailable();return;}
            if(currentEngine==VelaPlaybackPolicy.MEDIA3){
                media3Attempt=true;
                dvr=new VelaDvrPlayer(activity,selected.name,video.getHolder().getSurface(),playback,buffer==null?headers:Collections.emptyMap(),limits,new VelaDvrPlayer.Events(){
                    public void ready(){if(active&&attempt==generation){prepared=true;tracksChanged();}}
                    public void tracksChanged(){if(active&&attempt==generation&&controls!=null)controls.tracksChanged();}
                    public void frame(){if(active&&attempt==generation)playbackStarted(null);}
                    public void size(int h){if(active&&attempt==generation){resize();quality(h);}}
                    public void failed(){failed(VelaPlaybackRecovery.Failure.UNKNOWN);}
                    public void failed(VelaPlaybackRecovery.Failure reason){if(active&&attempt==generation)LiveTvPlayer.this.failed(reason);}
                    public void captions(String value){if(active&&attempt==generation)caption(value);}
                    public void ended(){if(active&&attempt==generation)completed();}
                });
            }else{
                MediaPlayer next=new MediaPlayer();player=next;
                next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
                next.setSurface(video.getHolder().getSurface());next.setScreenOnWhilePlaying(true);
                next.setOnVideoSizeChangedListener((m,w,h)->{if(m==player){resize();quality(h);}});
                next.setOnPreparedListener(m->{if(active&&m==player){try{prepared=true;restoreTracks(m);if(controls!=null)controls.tracksChanged();m.start();boolean hasAudio=false,hasVideo=false;for(MediaPlayer.TrackInfo track:m.getTrackInfo()){hasAudio|=track.getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO;hasVideo|=track.getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO;}if(hasAudio&&!hasVideo)playbackStarted(m);}catch(RuntimeException bad){failed(VelaPlaybackRecovery.Failure.DECODER);}}});
                next.setOnInfoListener((m,what,extra)->{if(active&&m==player){if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)playbackStarted(m);else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START)systemBuffering=true;else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END)systemBuffering=false;}return false;});
                next.setOnErrorListener((m,what,extra)->{if(active&&m==player)failed(extra==MediaPlayer.MEDIA_ERROR_IO||extra==MediaPlayer.MEDIA_ERROR_TIMED_OUT?VelaPlaybackRecovery.Failure.NETWORK:VelaPlaybackRecovery.Failure.DECODER);return true;});
                next.setOnCompletionListener(m->{if(active&&m==player)completed();});
                next.setOnTimedTextListener((m,text)->{if(m==player)caption(text==null?"":text.getText());});
                liveBadge.resolution(0);
                next.setDataSource(activity,Uri.parse(playback),VelaStreamHeaders.transport(headers));next.prepareAsync();
            }
            main.postDelayed(timeout,limits.timeoutMs);
        }catch(java.io.IOException network){failed(VelaPlaybackRecovery.Failure.NETWORK);}catch(RuntimeException unsupported){failed(VelaPlaybackRecovery.Failure.DECODER);}
    }
    private TvSignalFallback.Signal nextSignal(){return signals==null?null:signals.next(ch->VelaPlaybackPolicy.order(activity,selected,ch,ch.xtreamId.isEmpty()?ch.streams:new XtreamClient(activity).streams(ch)),recovery.sameVariantOnly());}
    private void startAntenna(){
        video.setVisibility(GONE);liveBadge.resolution(0);TvView next=new TvView(activity);tuner=next;addView(next,0,new LayoutParams(-1,-1));
        next.setCallback(new TvView.TvInputCallback(){
            public void onTimeShiftStatusChanged(String input,int status){
                if(tuner!=next||timeShiftStatus==status)return;
                timeShiftStatus=status;
                if(status!=TvInputManager.TIME_SHIFT_STATUS_AVAILABLE)paused=false;
                if(controls!=null)controls.moreOptionsChanged();
            }
            public void onVideoAvailable(String input){if(active&&tuner==next){int mode=VelaPlaybackPanel.prefs(activity).getInt(VelaPlaybackPanel.key(selected.name,"aspect"),0);next.setScaleX(mode==1?1.08f:1);next.setScaleY(mode==1?1.08f:1);rendered=true;prepared=true;notifyStarted();main.removeCallbacks(timeout);message.setVisibility(GONE);pictureReady();}}
            public void onVideoUnavailable(String input,int reason){if(tuner!=next||!active)return;if(reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY){rendered=true;prepared=true;notifyStarted();main.removeCallbacks(timeout);message.setVisibility(GONE);pictureReady();return;}rendered=false;main.removeCallbacks(hide);message.setVisibility(VISIBLE);message.setText(reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL?Ui.tr("Sem sinal de antena. Confira o cabo.","No antenna signal. Check the cable.","Sin señal de antena. Comprueba el cable."):Ui.tr("Sintonizando…","Tuning…","Sintonizando…"));if(reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL)main.removeCallbacks(timeout);showControls();}
            public void onConnectionFailed(String input){if(active&&tuner==next)failed();}
            public void onDisconnected(String input){if(active&&tuner==next)failed();}
            public void onContentBlocked(String input,TvContentRating rating){if(tuner==next){message.setText(Ui.tr("Conteúdo bloqueado pelos controles da TV.","Content blocked by the TV parental controls.","Contenido bloqueado por los controles de la TV."));message.setVisibility(VISIBLE);main.removeCallbacks(timeout);showControls();}}
            public void onTrackSelected(String input,int type,String id){if(tuner==next&&controls!=null)controls.tracksChanged();}
            public void onTracksChanged(String input,List<TvTrackInfo> tracks){if(tuner==next){VelaPlaybackPanel.restoreTv(activity,next,selected.name);if(controls!=null)controls.tracksChanged();}if(tuner==next&&tracks!=null)for(TvTrackInfo track:tracks)if(track.getType()==TvTrackInfo.TYPE_VIDEO&&track.getVideoHeight()>0)quality(track.getVideoHeight());}
        });
        next.setTimeShiftPositionCallback(new TvView.TimeShiftPositionCallback(){public void onTimeShiftStartPositionChanged(String input,long value){timeShiftStart=value;}public void onTimeShiftCurrentPositionChanged(String input,long value){timeShiftCurrent=value;}});
        try{next.tune(selected.inputId,android.media.tv.TvContract.buildChannelUri(Long.parseLong(selected.epgId)));next.setStreamVolume(1f);main.postDelayed(timeout,25_000);}catch(RuntimeException unavailable){failed();}
    }
    private void completed(){if(replayUrl.isEmpty()){failed(VelaPlaybackRecovery.Failure.NETWORK);return;}release();message.setText(L10n.text("The programme has ended. Press Back to return to the guide."));message.setVisibility(VISIBLE);showControls();}
    private void failed(){failed(VelaPlaybackRecovery.Failure.UNKNOWN);}
    private void failed(VelaPlaybackRecovery.Failure reason){
        if(selected!=null&&selected.antenna()){release();unavailable();return;}
        boolean buffered=buffer!=null;int engine=currentEngine;lastFailure=reason;android.util.Log.w("VelaPlayback","Recovery: "+reason+" engine="+engine+" starts="+recovery.starts());
        VelaPlaybackRecovery.Action action=recovery.failed(reason,engine,VelaPlaybackPolicy.engine(activity)==VelaPlaybackPolicy.AUTO);
        release();if(!active)return;
        if(!replayUrl.isEmpty()){message.setText(L10n.text("The source could not play this programme. Press Back to return to the guide."));message.setVisibility(VISIBLE);showControls();return;}
        if(action==VelaPlaybackRecovery.Action.STOP){unavailable();return;}
        if(buffered){directOnlySignal=signal.url;forcedEngine=0;message.setText(L10n.text("This signal will play without local pause."));main.postDelayed(retry,250);return;}
        if(action==VelaPlaybackRecovery.Action.RETRY){forcedEngine=engine;message.setText(L10n.text("Reconnecting…"));main.postDelayed(retry,1000);return;}
        if(action==VelaPlaybackRecovery.Action.OTHER_ENGINE){forcedEngine=engine==VelaPlaybackPolicy.MEDIA3?VelaPlaybackPolicy.SYSTEM:VelaPlaybackPolicy.MEDIA3;message.setText(L10n.text("Trying the compatibility player…"));main.postDelayed(retry,250);return;}
        forcedEngine=0;signal=nextSignal();if(signal==null){unavailable();return;}
        message.setVisibility(VISIBLE);message.setText(L10n.text("Trying another signal…"));panel.setVisibility(VISIBLE);main.postDelayed(retry,250);
    }
    private void unavailable(){intro.setVisibility(GONE);message.setVisibility(VISIBLE);message.setText(selected!=null&&selected.antenna()?Ui.tr("Sem sinal de antena. Confira o cabo e os canais sintonizados.","No antenna signal. Check the cable and tuned channels.","Sin señal de antena. Comprueba el cable y los canales sintonizados."):Ui.tr("Nenhuma emissão disponível. Pressione OK para tentar novamente ou → para ver os canais.","No signal available. Press OK to retry or → for channels.","No hay señales disponibles. Pulsa OK para reintentar o → para ver los canales."));if(lastFailure==VelaPlaybackRecovery.Failure.ACCESS||recovery.sameVariantOnly())message.setText(L10n.text("The source refused access. Check the source settings."));showControls();}
    private void resize(){
        if(player==null&&dvr==null)return;
        try{
            int vw=dvr!=null?dvr.width():player.getVideoWidth(),vh=dvr!=null?dvr.height():player.getVideoHeight(),w=getWidth(),h=getHeight();
            if(vw<=0||vh<=0||w<=0||h<=0)return;
            int mode=selected==null?0:VelaPlaybackPanel.prefs(activity).getInt(VelaPlaybackPanel.key(selected.name,"aspect"),0);
            float scale=mode==1?Math.max(w/(float)vw,h/(float)vh):Math.min(w/(float)vw,h/(float)vh);
            int targetW=mode==2?-1:Math.round(vw*scale),targetH=mode==2?-1:Math.round(vh*scale);
            LayoutParams p=(LayoutParams)video.getLayoutParams();if(p.width==targetW&&p.height==targetH&&p.gravity==Gravity.CENTER)return;
            p.width=targetW;p.height=targetH;p.gravity=Gravity.CENTER;video.setLayoutParams(p);
        }catch(IllegalStateException unavailable){}
    }
    private void release(){
        media3Attempt=false;
        if(remember!=null){main.removeCallbacks(remember);remember=null;}
        if(dvr!=null){dvr.close();dvr=null;}if(buffer!=null){buffer.close();buffer=null;}generation++;main.removeCallbacks(hideIntro);main.removeCallbacks(timeout);main.removeCallbacks(hide);main.removeCallbacks(retry);main.removeCallbacks(watch);TvView oldTuner=tuner;tuner=null;if(oldTuner!=null){oldTuner.setCallback(null);oldTuner.reset();removeView(oldTuner);}video.setVisibility(VISIBLE);if(captions!=null){captions.setText("");captions.setVisibility(GONE);}MediaPlayer old=player;player=null;prepared=false;rendered=false;
        if(old!=null){old.setOnPreparedListener(null);old.setOnInfoListener(null);old.setOnErrorListener(null);old.setOnCompletionListener(null);old.setOnVideoSizeChangedListener(null);old.setOnTimedTextListener(null);old.release();}
        if(audio!=null&&audioFocus!=null)audio.abandonAudioFocusRequest(audioFocus);audioFocus=null;
    }
    private void quality(int height){liveBadge.resolution(height);}
    private void concealIntro(){intro.setVisibility(GONE);}
    private void pictureReady(){
        main.removeCallbacks(hideIntro);main.postDelayed(hideIntro,VelaPlayerUi.INTRO_MS);
        if(panel.getVisibility()==VISIBLE&&quickDrawer.getVisibility()!=VISIBLE&&controls==null)panel.rail.reveal();
        delayHide();
    }
    private void delayHide(){main.removeCallbacks(hide);if(rendered&&!paused&&quickDrawer.getVisibility()!=VISIBLE)main.postDelayed(hide,VelaPlayerUi.INFO_MS);}
    private void hideControls(){if(rendered&&!paused&&quickDrawer.getVisibility()!=VISIBLE){panel.fadeOut();message.setVisibility(GONE);requestFocus();}}
    private void dismissInfo(){main.removeCallbacks(hide);panel.setVisibility(GONE);if(rendered)message.setVisibility(GONE);requestFocus();}
    private void showControls(){if(controls!=null||quickDrawer.getVisibility()==VISIBLE)return;panel.present();requestFocus();delayHide();}
    private TextView pickerButton(String label){TextView b=text(label,21,0xFFF7F7FA,Ui.medium);b.setId(View.generateViewId());b.setFocusable(true);b.setClickable(true);b.setGravity(Gravity.CENTER);b.setDefaultFocusHighlightEnabled(false);b.setBackground(round(VelaTheme.CARD));b.setOnFocusChangeListener((v,f)->{if(b==quickAll||b==quickFavorites||b==quickRecent){stylePickerTabs();return;}b.setTextColor(f?VelaTheme.INK:0xFFF7F7FA);b.setBackground(round(f?Appearance.accent(activity):VelaTheme.CARD));});return b;}
    private void styleChannelRows(){for(int i=0;i<quickList.getChildCount();i++)if(quickList.getChildAt(i) instanceof ChannelRow)((ChannelRow)quickList.getChildAt(i)).highlight(quickList.hasFocus()&&quickList.getFirstVisiblePosition()+i==quickList.getSelectedItemPosition());}
    private final class ChannelRow extends FrameLayout {
        final LiveTvLogos.LogoView logo;final TextView name,show,remaining,state;final ProgressBar bar;String bound="";boolean playing;
        ChannelRow(){super(activity);logo=new LiveTvLogos.LogoView(activity,"");logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);logo.plated();addView(logo);name=text("",22,0xFFF7F7FA,Ui.medium);show=text("",23,VelaTheme.WHITE,Ui.displaySemibold);remaining=text("",16,VelaTheme.SECONDARY,Ui.medium);for(TextView label:new TextView[]{name,show,remaining}){label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(label);}state=text("",18,VelaTheme.WHITE,Ui.medium);state.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);addView(state);bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.accent(activity)));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(VelaTheme.RAISED));addView(bar);}
        private void at(View view,int x,int y,int w,int h){LayoutParams p=new LayoutParams(Math.round(w*uiScale),Math.round(h*uiScale));p.leftMargin=Math.round(x*uiScale);p.topMargin=Math.round(y*uiScale);view.setLayoutParams(p);}
        void bind(LiveTvData.Channel ch,int index){
            setLayoutParams(new AbsListView.LayoutParams(-1,Math.round(108*uiScale)));
            at(logo,18,18,68,68);at(state,0,9,14,26);
            at(name,104,14,274,29);at(remaining,384,14,204,29);
            at(show,104,47,486,38);at(bar,104,94,484,3);
            logo.label(ch.antenna()&&!ch.number.isEmpty()?ch.number:LiveTvActivity.displayName(ch.name));
            playing=selected!=null&&selected.id.equals(ch.id);
            state.setText(playing?"●":"");state.setContentDescription(playing?L10n.text("Watching"):null);
            state.setTextSize(TypedValue.COMPLEX_UNIT_PX,12*uiScale);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX,20*uiScale);
            show.setTextSize(TypedValue.COMPLEX_UNIT_PX,25*uiScale);
            remaining.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            remaining.setTextSize(TypedValue.COMPLEX_UNIT_PX,18*uiScale);
            name.setText((TvChannelNames.favorite(ch,favoriteIds)?"★ ":"")+LiveTvActivity.displayName(ch.name));
            LiveTvData.Guide guide=pickerGuide.get(ch.id);
            long now=System.currentTimeMillis()/1000;
            LiveTvData.Programme on=guide==null?null:guide.current(now);
            show.setText(on==null?Ui.tr("Sem programação","No programme information","Sin programación"):on.title);
            remaining.setText(on==null?"":LiveTvActivity.remainingText(on.remainingMinutes(now)));
            bar.setProgress(on==null?0:on.progress(now));
            setContentDescription((playing?L10n.text("Watching")+" · ":"")+name.getText()+" · "+show.getText()+(on==null?"":" · "+remaining.getText()));
            String key=ch.id+":"+ch.logo;
            if(!key.equals(bound)){bound=key;logo.setImageDrawable(null);if(artwork!=null)artwork.load(ch,logo);}
            highlight(quickList.hasFocus()&&index==quickList.getSelectedItemPosition());
        }
        void highlight(boolean focus){
            setBackground(VelaPlayerUi.row(activity,focus));
            bar.setVisibility(focus&&remaining.length()>0?VISIBLE:INVISIBLE);
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x2817181E));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(VelaTheme.INK));
            state.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);
            name.setTextColor(focus?0xFF40464C:VelaTheme.SECONDARY);
            show.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);
            remaining.setTextColor(focus?0xFF48505A:VelaTheme.SECONDARY);
        }

    }
    void channels(List<LiveTvData.Channel> values,Map<String,LiveTvData.Guide> programmes,String group,int page,int total){
        String focused="";int oldPosition=quickList.getSelectedItemPosition(),oldTop=quickList.getSelectedView()==null?0:quickList.getSelectedView().getTop();if(oldPosition>=0&&oldPosition<pickerChannels.size())focused=pickerChannels.get(oldPosition).id;boolean samePage=pickerPage==page;View retainedFocus=quickDrawer.findFocus();boolean enterRows=pickerLoading||pickerDirection!=0||quickList.hasFocus()||retainedFocus==null;
        pickerChannels=new ArrayList<>(values.subList(0,Math.min(12,values.size())));pickerGuide=new HashMap<>(programmes);pickerPage=page;pickerTotal=total;pickerLoading=false;quickGroup.setText(group);updatePickerStatus();
        if(quickDrawer.getVisibility()==VISIBLE){quickList.setAdapter(null);if(artwork!=null)artwork.page();if(channelLogo.getDrawable()==null&&artwork!=null&&selected!=null)artwork.load(selected,channelLogo);quickList.setAdapter(quickAdapter);int focus=pickerDirection<0?Math.max(0,pickerChannels.size()-1):samePage?Math.max(0,Math.min(oldPosition,pickerChannels.size()-1)):0;if(samePage&&pickerDirection==0)for(int i=0;i<pickerChannels.size();i++)if(pickerChannels.get(i).id.equals(focused))focus=i;quickList.setSelectionFromTop(focus,samePage&&pickerDirection==0?oldTop:0);if(pickerChannels.isEmpty())(pickerRecent?quickRecent:quickFavorites).requestFocus();else if(enterRows)quickList.requestFocus();else if(retainedFocus.isAttachedToWindow())retainedFocus.requestFocus();}
        pickerDirection=0;
    }
    void channelGuide(Map<String,LiveTvData.Guide> programmes){pickerGuide=new HashMap<>(programmes);if(quickDrawer.getVisibility()==VISIBLE)quickAdapter.notifyDataSetChanged();}
    private void stylePickerTabs(){if(quickAll==null||quickFavorites==null||quickRecent==null)return;VelaPlayerUi.tab(quickAll,!pickerFavorites&&!pickerRecent);VelaPlayerUi.tab(quickFavorites,pickerFavorites);VelaPlayerUi.tab(quickRecent,pickerRecent);}
    private void updatePickerStatus(){stylePickerTabs();quickRecent.setText((pickerRecent?"✓ ":"")+Ui.tr("Recentes","Recent","Recientes"));quickAll.setText((pickerFavorites||pickerRecent?"":"✓ ")+Ui.tr("Lista","List","Lista"));quickFavorites.setText((pickerFavorites?"✓ ":"")+"★ "+Ui.tr("Favoritos","Favorites","Favoritos"));quickPrevious.setEnabled(!pickerLoading&&pickerPage>0);quickPrevious.setAlpha(quickPrevious.isEnabled()?1:.3f);quickNext.setEnabled(!pickerLoading&&(pickerPage+1)*12<pickerTotal);quickNext.setAlpha(quickNext.isEnabled()?1:.3f);quickStatus.setText(pickerLoading?Ui.tr("Carregando…","Loading…","Cargando…"):pickerTotal==0?(pickerFavorites?Ui.tr("Ainda sem favoritos","No favorites yet","Aún no tienes favoritos"):"0 / 0"):(pickerPage*12+1)+"–"+(pickerPage*12+pickerChannels.size())+" / "+pickerTotal);}
    private void requestChannels(int page){if(pickerLoading||page<0||(long)page*12>=pickerTotal)return;pickerDirection=Integer.compare(page,pickerPage);pickerLoading=true;updatePickerStatus();actions.channels(page,pickerFavorites);}
    void channelError(){pickerLoading=false;updatePickerStatus();quickStatus.setText(Ui.tr("Não foi possível carregar. Tente novamente.","Could not load. Please retry.","No se ha podido cargar. Reinténtalo."));}
    void favorites(Set<String> ids){boolean changed=!favoriteIds.equals(ids);favoriteIds=new HashSet<>(ids);if(selected!=null)panel.channelName((TvChannelNames.favorite(selected,favoriteIds)?"★ ":"")+(selected.antenna()&&!selected.number.isEmpty()?selected.number+" · ":"")+LiveTvActivity.displayName(selected.name));if(quickDrawer.getVisibility()==VISIBLE){quickAdapter.notifyDataSetChanged();if(changed&&pickerFavorites&&!pickerLoading){pickerLoading=true;updatePickerStatus();actions.channels(pickerPage,true);}}}
    private void filterChannels(boolean value){if(pickerFavorites==value&&!pickerRecent)return;pickerRecent=false;pickerFavorites=value;pickerLoading=true;pickerDirection=0;updatePickerStatus();actions.channels(0,value);}
    private void selectClick(){if(!rendered&&player==null&&dvr==null&&selected!=null){restartNetwork();}else if(panel.getVisibility()==VISIBLE&&rendered)dismissInfo();else showControls();}
    private void openChannels(){main.removeCallbacks(hide);panel.setVisibility(GONE);intro.setVisibility(GONE);quickDrawer.setVisibility(VISIBLE);if(artwork==null)artwork=new LiveTvLogos(activity);quickList.setAdapter(quickAdapter);updatePickerStatus();int focus=0;for(int i=0;i<pickerChannels.size();i++)if(selected!=null&&pickerChannels.get(i).id.equals(selected.id))focus=i;quickList.setSelection(focus);quickList.requestFocus();if(pickerChannels.isEmpty())(pickerRecent?quickRecent:quickFavorites).requestFocus();if((pickerFavorites||pickerRecent)&&!pickerLoading){pickerLoading=true;updatePickerStatus();actions.channels(pickerPage,pickerFavorites);}}
    private void closeChannels(boolean focus){pickerPress.cancel();selectPress.cancel();if(quickDrawer.getVisibility()!=VISIBLE)return;quickDrawer.setVisibility(GONE);quickList.setAdapter(null);if(artwork!=null)artwork.page();if(focus){if(rendered){panel.setVisibility(GONE);requestFocus();}else showControls();}}
    private void playbackStarted(MediaPlayer p){
        rendered=true;notifyStarted();recovery.success();long first=progressToken();progress.reset(SystemClock.elapsedRealtime(),first);main.removeCallbacks(timeout);main.removeCallbacks(watch);main.postDelayed(watch,TvSignalFallback.CHECK_INTERVAL_MS);
        if(remember!=null)main.removeCallbacks(remember);final int attempt=generation,engine=currentEngine;final TvSignalFallback.Signal playing=signal;final LiveTvData.Channel requested=selected;
        if(playing!=null&&replayUrl.isEmpty()){remember=()->{if(active&&attempt==generation&&rendered&&!paused){try{long now=progressToken();if(now!=Long.MIN_VALUE&&now!=first){VelaPlaybackPolicy.success(activity,requested,playing.url,engine);recovery.stable(engine);}}catch(IllegalStateException ignored){}}remember=null;};main.postDelayed(remember,5000);}
        message.setVisibility(GONE);LiveTvData.prefs(activity).edit().putString("last-"+LiveTvData.hash(LiveTvData.source(activity)),selected.id).apply();pictureReady();
    }
    private void notifyStarted(){if(!historyNotified&&selected!=null){historyNotified=true;VelaRecentActivity.channel(activity,selected.id,LiveTvActivity.displayName(selected.name),LiveTvData.source(activity));actions.started(selected);}}
    boolean pairedKey(int key){return controls!=null||key==controlsKey||TvRemotePress.select(key);}
    boolean key(KeyEvent event){
        int key=event.getKeyCode();if(key==controlsKey){if(event.getAction()==KeyEvent.ACTION_UP)controlsKey=-1;return true;}if(controls!=null)return controls.remote(event);if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)openPlaybackControls();return true;}if(quickDrawer.getVisibility()==VISIBLE&&quickList.hasFocus()&&pickerPress.key(event))return true;if(quickDrawer.getVisibility()!=VISIBLE&&selectPress.key(event))return true;if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
        if(quickDrawer.getVisibility()==VISIBLE){if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_DPAD_LEFT&&quickList.hasFocus()){closeChannels(true);return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&quickList.hasFocus())return true;return false;}
        if(key==KeyEvent.KEYCODE_LAST_CHANNEL){if(event.getRepeatCount()==0)actions.previous();return true;}if(key==KeyEvent.KEYCODE_BACK){if(event.getRepeatCount()==0){if(panel.getVisibility()==VISIBLE&&rendered&&message.getVisibility()!=VISIBLE)dismissInfo();else actions.back();}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT){openChannels();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){openChannels();int at=-1;for(int i=0;i<pickerChannels.size();i++)if(selected!=null&&pickerChannels.get(i).id.equals(selected.id))at=i;int next=at<0?0:at+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);if(next>=0&&next<pickerChannels.size())quickList.setSelection(next);else if(next<0&&pickerPage>0)requestChannels(pickerPage-1);else if(next>=pickerChannels.size()&&(pickerPage+1)*12<pickerTotal)requestChannels(pickerPage+1);return true;}
        if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){if(event.getRepeatCount()==0)actions.zap(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}

        if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(event.getRepeatCount()==0){if(panel.getVisibility()==VISIBLE){controlsKey=key;openPlaybackControls();}else showControls();}return true;}
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&tuner!=null&&timeShiftStatus==TvInputManager.TIME_SHIFT_STATUS_AVAILABLE){
            if(event.getRepeatCount()==0){
                if(paused)tuner.timeShiftResume();else tuner.timeShiftPause();
                paused=!paused;
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&prepared&&dvr!=null&&(buffer!=null||!replayUrl.isEmpty())){
            if(event.getRepeatCount()==0){togglePause();showControls();}
            return true;
        }
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&prepared&&player!=null&&!replayUrl.isEmpty()){
            if(event.getRepeatCount()>0)return true;
            try{
                if(player.isPlaying()){
                    player.pause();paused=true;
                    main.removeCallbacks(timeout);showControls();
                }else{
                    player.start();paused=false;
                    progress.reset(SystemClock.elapsedRealtime(),player.getCurrentPosition());
                    if(!rendered)main.postDelayed(timeout,TvSignalFallback.START_TIMEOUT_MS);
                    delayHide();
                }
            }catch(IllegalStateException ignored){}
            return true;
        }
        if(panel.getVisibility()==VISIBLE)delayHide();return false;
    }
    private long position(){return dvr!=null?dvr.position():player==null?0:player.getCurrentPosition();}
    private long progressToken(){
        if(dvr!=null)return dvr.progress();if(player==null)return Long.MIN_VALUE;
        try{PersistableBundle metrics=player.getMetrics();Object frames=metrics==null?null:metrics.get(MediaPlayer.MetricsConstants.FRAMES);if(frames instanceof Number&&((Number)frames).longValue()>0)return ((Number)frames).longValue();}catch(RuntimeException unavailable){}
        long time=player.getCurrentPosition();return time>0?time:Long.MIN_VALUE;
    }
    private void caption(String value){
        if(value==null||value.isEmpty()){if(captions!=null){captions.setText("");captions.setVisibility(GONE);}return;}
        if(captions==null){captions=new TextView(activity);captions.setTextColor(android.graphics.Color.WHITE);captions.setTextSize(24);captions.setGravity(Gravity.CENTER);captions.setBackgroundColor(0x99000000);LayoutParams cp=new LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cp.bottomMargin=60;cp.leftMargin=120;cp.rightMargin=120;addView(captions,cp);}captions.setText(value);captions.setVisibility(VISIBLE);
    }
    private void restoreTracks(MediaPlayer p){
        SharedPreferences saved=VelaPlaybackPanel.prefs(activity);String prefix=VelaGuideFix.key(selected.name)+"-",audio=saved.getString(prefix+"audio",""),sub=saved.getString(prefix+"sub","");
        if(audio.isEmpty()&&sub.isEmpty())return;
        try{MediaPlayer.TrackInfo[] tracks=p.getTrackInfo();for(int i=0;i<tracks.length;i++){
            int type=tracks[i].getTrackType();if(type!=MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO&&type!=MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)continue;
            String wanted=type==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO?audio:sub;
            if(type==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT&&wanted.equals("off")){if(p.getSelectedTrack(type)==i)p.deselectTrack(i);}
            else if((("lang:"+tracks[i].getLanguage()).equals(wanted)||(tracks[i].getLanguage()!=null&&("lang:"+Locale.forLanguageTag(tracks[i].getLanguage()).getDisplayLanguage(Ui.locale())).equals(wanted)))&&p.getSelectedTrack(type)!=i)p.selectTrack(i);
        }}catch(RuntimeException unavailable){}
    }

    private void channelOptions(LiveTvData.Channel ch){LiveTvData.Guide g=pickerGuide.get(ch.id);LiveTvData.Programme p=g==null?null:g.current(System.currentTimeMillis()/1000);if(activity instanceof VelaHub.Owner)((VelaHub.Owner)activity).channelOptions(ch.name,()->actions.favorite(ch),p==null?null:new VelaShows.Airing(p.title,ch.name,p.start,p.end,"",p.description,p.image,ch.logo,channelSource,TvChannelLibrary.key(ch)));else actions.favorite(ch);}
    private void openPlaybackControls(){
        if(selected==null)return;main.removeCallbacks(hide);closeChannels(false);panel.setVisibility(GONE);intro.setVisibility(GONE);
        controls=new VelaPlaybackTray(activity,LiveTvActivity.displayName(selected.name),panel.title.getText(),channelLogo.getDrawable(),this::playbackItems,()->{controls=null;controlsKey=-1;requestFocus();if(active)delayHide();});addView(controls,new LayoutParams(-1,-1));
    }
    private List<VelaPlaybackPanel.Item> playbackItems(){
        List<VelaPlaybackPanel.Item> items=new ArrayList<>();
        if(tuner!=null){
            VelaPlaybackPanel.tvItems(activity,tuner,selected.name,items);
            if(timeShiftStatus==TvInputManager.TIME_SHIFT_STATUS_AVAILABLE){
                items.add(new VelaPlaybackPanel.Item(paused?L10n.text("Resume"):L10n.text("Pause live TV"),L10n.text("Tuner buffer"),()->{if(paused)tuner.timeShiftResume();else tuner.timeShiftPause();paused=!paused;}));
                items.add(new VelaPlaybackPanel.Item(L10n.text("Rewind 30 seconds"),L10n.text("Within the available window"),()->tuner.timeShiftSeekTo(Math.max(timeShiftStart,timeShiftCurrent-30000))));
                items.add(new VelaPlaybackPanel.Item(L10n.text("Return to live"),"",()->{tuner.timeShiftSeekTo(System.currentTimeMillis());tuner.timeShiftResume();paused=false;}));
            }else items.add(new VelaPlaybackPanel.Item(L10n.text("Antenna pause unavailable"),L10n.text("The tuner does not offer this feature to Vela."),null));
        }else{
            if(dvr!=null&&prepared)dvr.items(items);else if(player!=null&&prepared)frameworkItems(items);
        }
        SharedPreferences saved=VelaPlaybackPanel.prefs(activity);String aspectKey=VelaPlaybackPanel.key(selected.name,"aspect");int aspect=saved.getInt(aspectKey,0);
        VelaPlaybackPanel.pictureItems(items,tuner!=null,aspect,next->{saved.edit().putInt(aspectKey,next).apply();resize();if(tuner!=null){tuner.setScaleX(next==1?1.08f:1);tuner.setScaleY(next==1?1.08f:1);}});
        items.add(new VelaPlaybackPanel.Item(L10n.text("Audio delay"),L10n.text("The system player does not allow Vela to change this. Use your TV's sound settings."),null));sleep.items(items,replayProgramme==null?programmeEnd:0);if(tuner==null)networkItems(items);return items;
    }
    private void frameworkItems(List<VelaPlaybackPanel.Item> items){
        MediaPlayer p=player;SharedPreferences saved=VelaPlaybackPanel.prefs(activity);String prefix=VelaGuideFix.key(selected.name)+"-";
        try{MediaPlayer.TrackInfo[] tracks=p.getTrackInfo();boolean subtitles=false;for(int i=0;i<tracks.length;i++){
            final int index=i;MediaPlayer.TrackInfo track=tracks[i];int type=track.getTrackType();subtitles|=type==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT;
            if(type==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO||type==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT){String code=track.getLanguage();String language=code==null||code.isEmpty()||code.equals("und")?L10n.text("Default"):Locale.forLanguageTag(code).getDisplayLanguage(Ui.locale());items.add(new VelaPlaybackPanel.Item(type==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO?VelaPlaybackPanel.Group.AUDIO:VelaPlaybackPanel.Group.SUBTITLES,language,p.getSelectedTrack(type)==index,()->{p.selectTrack(index);saved.edit().putString(prefix+(type==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO?"audio":"sub"),"lang:"+(code==null?"und":code)).apply();}));}
        }
        if(subtitles)items.add(new VelaPlaybackPanel.Item(VelaPlaybackPanel.Group.SUBTITLES,L10n.text("Off"),p.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)<0,()->{int index=p.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);if(index>=0)p.deselectTrack(index);caption("");saved.edit().putString(prefix+"sub","off").apply();}));
        items.add(new VelaPlaybackPanel.Item(L10n.text("Video signal"),p.getVideoWidth()+" × "+p.getVideoHeight()+" · "+safeFormats(tracks),null));
        }catch(RuntimeException unavailable){items.add(new VelaPlaybackPanel.Item(L10n.text("Preparing playback"),L10n.text("Tracks appear when connected."),null));}
    }
    private void networkItems(List<VelaPlaybackPanel.Item> items){
        int engine=VelaPlaybackPolicy.engine(activity),profile=VelaPlaybackPolicy.profile(activity);
        items.add(new VelaPlaybackPanel.Item(L10n.text("Playback engine"),VelaPlaybackPolicy.engineName(engine),()->{VelaPlaybackPanel.prefs(activity).edit().putInt("engine",(engine+1)%3).apply();restartNetwork();}));
        items.add(new VelaPlaybackPanel.Item(L10n.text("Buffering profile"),VelaPlaybackPolicy.profileName(profile)+" · "+VelaPlaybackPolicy.profileDetail(profile),()->{VelaPlaybackPanel.prefs(activity).edit().putInt("buffer",(profile+1)%3).apply();restartNetwork();}));
        int matching=VelaFrameRate.mode(activity);String detail=VelaFrameRate.name(matching);
        if(matching!=VelaFrameRate.OFF)detail+=" · "+(dvr==null?L10n.text("Unavailable for this signal"):dvr.frameRateDetail());
        items.add(new VelaPlaybackPanel.Item(L10n.text("Match content frame rate"),detail,()->{VelaFrameRate.mode(activity,(matching+1)%3);frameworkSignal="";restartNetwork();}));
        if(replayUrl.isEmpty()){
            SharedPreferences saved=VelaPlaybackPanel.prefs(activity);boolean enabled=saved.getBoolean("timeshift",false);
            items.add(new VelaPlaybackPanel.Item(L10n.text("Local live pause"),enabled?L10n.text("On for supported HLS · Up to 15 min and 256 MiB on disk"):L10n.text("Off · Only buffers the channel you are watching"),()->{saved.edit().putBoolean("timeshift",!enabled).apply();restartNetwork();}));
        }
        if(prepared&&(buffer!=null||!replayUrl.isEmpty())){
            items.add(new VelaPlaybackPanel.Item(paused?L10n.text("Resume"):L10n.text("Pause"),"",this::togglePause));
            boolean seekable=dvr!=null?dvr.seekable():player!=null&&player.getDuration()>0;
            if(seekable){
                items.add(new VelaPlaybackPanel.Item(L10n.text("Rewind 30 seconds"),L10n.text("Within the available window"),()->seekRelative(-30000)));
                items.add(new VelaPlaybackPanel.Item(replayUrl.isEmpty()?L10n.text("Return to live"):L10n.text("Forward 30 seconds"),"",()->{if(replayUrl.isEmpty()&&dvr!=null){dvr.live();paused=false;}else seekRelative(30000);}));
            }else items.add(new VelaPlaybackPanel.Item(L10n.text("Rewind unavailable"),L10n.text("This player does not expose a seekable window for this signal."),null));
        }
        if(buffer!=null)items.add(new VelaPlaybackPanel.Item(L10n.text("Temporary storage"),String.format(java.util.Locale.ROOT,L10n.text("%.0f s · %.1f MiB on disk"),buffer.seconds(),buffer.bytes()/1048576d),null));
    }
    private void togglePause(){if(dvr!=null){if(paused)dvr.play();else dvr.pause();}else if(player!=null){if(paused)player.start();else player.pause();}else return;paused=!paused;}
    private void seekRelative(long delta){long target=Math.max(0,position()+delta),duration=dvr!=null?dvr.duration():player==null?0:player.getDuration();if(duration>0)target=Math.min(target,Math.max(0,duration-1));if(dvr!=null)dvr.seek(target);else if(player!=null)player.seekTo((int)Math.min(Integer.MAX_VALUE,target));}
    private static String safeFormats(MediaPlayer.TrackInfo[] tracks){Set<String> out=new LinkedHashSet<>();for(MediaPlayer.TrackInfo t:tracks)try{MediaFormat f=t.getFormat();if(f!=null&&f.containsKey(MediaFormat.KEY_MIME))out.add(f.getString(MediaFormat.KEY_MIME));}catch(RuntimeException ignored){}return android.text.TextUtils.join(" · ",out);}
    @Override public void surfaceCreated(SurfaceHolder holder){if(active&&selected!=null&&!selected.antenna())startSignal();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){if(selected==null||!selected.antenna())release();}
}
