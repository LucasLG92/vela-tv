package tv.vela.launcher;

import android.animation.*;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Full-screen, silent aerial films or real Plex backdrops. Resources exist only while visible. */
final class AmbientView extends FrameLayout implements SurfaceHolder.Callback {
    private final boolean plexMode;
    private final boolean preview;
    private final SurfaceView video;
    private final ImageView photoA,photoB;
    private final FrameLayout chrome,caption;
    private final TextView title,credit,clock,status,weatherText,weatherCity;
    private final ImageView weatherIcon;
    private AmbientWeather weather;
    private AmbientWeather.Snapshot weatherSnapshot;
    private String weatherMessage=L10n.text("Loading weather…"),weatherSymbol="";
    private final View veil;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ExecutorService io;
    private CinemaImages images;
    private MediaPlayer player;
    private ObjectAnimator pan;
    private CinemaImages.Request request;
    private List<AerialCatalog.Scene> scenes=new ArrayList<>();
    private List<PlexClient.Film> films=new ArrayList<>();
    private ImageView currentPhoto;
    private boolean active,videoStarted;
    private int index=-1,failures,generation,videoWidth,videoHeight;
    private Runnable timeout;
    private final Runnable advance=()->next();
    private final Runnable hideCaption=this::hideCaption;
    private void hideCaption(){
        if(!active)return;
        caption.animate().alpha(0).setDuration(Visuals.duration(getContext(),650)).start();
        if(!infoPinned())chrome.animate().alpha(0).setDuration(Visuals.duration(getContext(),650)).start();
    }
    private boolean infoPinned(){return AmbientPrefs.clock(getContext())||AmbientPrefs.weather(getContext());}
    private final Runnable minute=new Runnable(){public void run(){if(!active||!infoPinned())return;updateInfo();main.postDelayed(this,60_000-System.currentTimeMillis()%60_000);}};
    AmbientView(Context c,boolean preview){
        super(c);this.preview=preview;plexMode=AmbientPrefs.plex(c);setBackgroundColor(Color.BLACK);setClipChildren(true);
        photoA=new ImageView(c);photoB=new ImageView(c);
        for(ImageView photo:new ImageView[]{photoA,photoB}){photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setAlpha(0f);addView(photo,new LayoutParams(-1,-1));}
        video=new SurfaceView(c);video.setVisibility(plexMode?GONE:VISIBLE);if(!plexMode){addView(video,new LayoutParams(-1,-1,Gravity.CENTER));video.getHolder().addCallback(this);}
        veil=new View(c);veil.setBackgroundColor(Color.BLACK);veil.setAlpha(1);addView(veil,new LayoutParams(-1,-1));
        chrome=new FrameLayout(c);addView(chrome,new LayoutParams(-1,-1));
        View shade=new View(c);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x80000000}));LayoutParams sp=new LayoutParams(-1,260,Gravity.BOTTOM);chrome.addView(shade,sp);
        caption=new FrameLayout(c);LayoutParams cp=new LayoutParams(-1,120,Gravity.BOTTOM);cp.setMargins(76,0,640,64);chrome.addView(caption,cp);
        title=text(27,0xFFF6F6FA,Ui.medium);credit=text(17,0xFFC3C5CD,Ui.regular);caption.addView(title,new LayoutParams(-1,52));LayoutParams cr=new LayoutParams(-1,42);cr.topMargin=55;caption.addView(credit,cr);
        FrameLayout info=new FrameLayout(c);LayoutParams ip=new LayoutParams(540,190,Gravity.RIGHT|Gravity.BOTTOM);ip.setMargins(0,0,76,70);chrome.addView(info,ip);
        clock=text(70,0xFFF6F7FA,Ui.light);clock.setFontFeatureSettings("tnum");clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);info.addView(clock,new LayoutParams(-1,86));clock.setVisibility(AmbientPrefs.clock(c)?VISIBLE:GONE);
        LinearLayout weatherRow=new LinearLayout(c);weatherRow.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LayoutParams wp=new LayoutParams(-1,48);wp.topMargin=90;info.addView(weatherRow,wp);
        weatherIcon=new ImageView(c);weatherIcon.setColorFilter(0xFFF4F5F8);LinearLayout.LayoutParams iconPosition=new LinearLayout.LayoutParams(32,32);iconPosition.rightMargin=12;weatherRow.addView(weatherIcon,iconPosition);
        weatherText=text(25,0xFFF0F2F8,Ui.medium);weatherText.setSingleLine(true);weatherText.setMaxWidth(490);weatherText.setEllipsize(android.text.TextUtils.TruncateAt.END);weatherText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);weatherRow.addView(weatherText,new LinearLayout.LayoutParams(-2,48));
        weatherCity=text(19,0xFFD4D8E2,Ui.regular);weatherCity.setSingleLine(true);weatherCity.setEllipsize(android.text.TextUtils.TruncateAt.END);weatherCity.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LayoutParams cityPosition=new LayoutParams(-1,34);cityPosition.topMargin=142;info.addView(weatherCity,cityPosition);
        status=text(23,0xFFCBD0D9,Ui.regular);status.setGravity(Gravity.CENTER);status.setPadding(100,30,100,30);addView(status,new LayoutParams(-1,140,Gravity.CENTER));
        chrome.setAlpha(0);
    }
    private TextView text(int size,int color,android.graphics.Typeface font){TextView t=new TextView(getContext());t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);t.setTextColor(color);t.setTypeface(font);t.setIncludeFontPadding(false);t.setShadowLayer(9,0,2,0xAA000000);return t;}
    void start(){
        if(active)return;active=true;weatherMessage=L10n.text("Loading weather…");failures=0;index=-1;generation++;io=Visuals.worker(1);status.setVisibility(VISIBLE);status.setText(plexMode?L10n.text("Preparing your library…"):L10n.text("Preparing landscapes…"));
        if(infoPinned()){chrome.setAlpha(1);minute.run();}
        if(AmbientPrefs.weather(getContext())){weather=new AmbientWeather(getContext(),(snapshot,message)->{if(!active)return;weatherSnapshot=snapshot;weatherMessage=message;updateInfo();});weather.start();}
        if(plexMode){
            LibraryClient plex=new LibraryClient(getContext());images=new CinemaImages(getContext(),plex);
            io.execute(()->{List<PlexClient.Film> library=plex.cached();if(library.isEmpty()&&plex.connected())try{library=plex.library();}catch(Exception ignored){}
                Set<String> seen=new HashSet<>();List<PlexClient.Film> unique=new ArrayList<>();for(PlexClient.Film film:library)if(film.art!=null&&!film.art.isEmpty()&&seen.add(film.art))unique.add(film);
                main.post(()->{if(!active)return;films=unique;if(films.isEmpty())message(Ui.tr("Conecte Plex ou Jellyfin ou confira o servidor para ver sua biblioteca.","Link Plex or Jellyfin or check the server to see your library.","Vincula Plex o Jellyfin o comprueba el servidor para ver tu biblioteca."));else next();});
            });
        }else{
            video.setVisibility(VISIBLE);
            io.execute(()->{try{List<AerialCatalog.Scene> catalogue=AerialCatalog.read(getContext(),AmbientPrefs.theme(getContext()));if(!preview)Collections.shuffle(catalogue);
                main.post(()->{if(!active)return;scenes=catalogue;if(scenes.isEmpty())message(L10n.text("No videos in this theme."));else if(video.getHolder().getSurface().isValid())next();});
            }catch(Exception e){main.post(()->{if(active)message(L10n.text("Could not open the aerial catalogue."));});}});
        }
    }
    private void next(){if(!active)return;if(plexMode)nextPhoto();else nextVideo();}
    private void nextVideo(){
        if(scenes.isEmpty()||!video.getHolder().getSurface().isValid())return;
        releasePlayer();index=(index+1)%scenes.size();AerialCatalog.Scene scene=scenes.get(index);videoStarted=false;veil.setAlpha(1);
        MediaPlayer next=new MediaPlayer();player=next;
        try{
            next.setVolume(0,0);next.setSurface(video.getHolder().getSurface());next.setScreenOnWhilePlaying(false);
            next.setOnVideoSizeChangedListener((p,w,h)->{if(p==player){videoWidth=w;videoHeight=h;resizeVideo();}});
            next.setOnPreparedListener(p->{if(!active||p!=player)return;p.start();});
            next.setOnInfoListener((p,what,extra)->{if(p==player&&active&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){
                videoStarted=true;failures=0;if(timeout!=null)main.removeCallbacks(timeout);status.setVisibility(GONE);veil.animate().alpha(0).setDuration(Visuals.duration(getContext(),700)).start();showCaption(scene.title,scene.credit);
            }return false;});
            next.setOnCompletionListener(p->{if(p==player&&active)nextVideo();});
            next.setOnErrorListener((p,what,extra)->{if(p==player&&active)videoFailed();return true;});
            next.setDataSource(scene.url);next.prepareAsync();
            timeout=()->{if(player==next&&active&&!videoStarted)videoFailed();};main.postDelayed(timeout,20_000);
        }catch(Exception e){videoFailed();}
    }
    private void videoFailed(){
        releasePlayer();failures++;
        if(failures>=scenes.size()){message(L10n.text("Could not load videos. Check your connection or choose your library."));return;}
        status.setVisibility(VISIBLE);status.setText(L10n.text("Trying another landscape…"));main.removeCallbacks(advance);main.postDelayed(advance,1500);
    }
    private void releasePlayer(){
        if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
        MediaPlayer old=player;player=null;videoStarted=false;
        if(old!=null){old.setOnPreparedListener(null);old.setOnInfoListener(null);old.setOnErrorListener(null);old.setOnCompletionListener(null);old.setOnVideoSizeChangedListener(null);old.release();}
    }
    private void nextPhoto(){
        if(films.isEmpty()||images==null)return;
        if(request!=null)request.cancel();if(timeout!=null)main.removeCallbacks(timeout);
        index=(index+1)%films.size();PlexClient.Film film=films.get(index);int epoch=++generation;
        boolean light=Visuals.light(getContext());
        timeout=()->{if(!active||epoch!=generation)return;failures++;if(failures>=films.size())message(Ui.tr("Não foi possível ler as imagens da biblioteca. Confira o servidor.","Could not read library images. Check the server.","No se han podido leer las imágenes de tu biblioteca. Comprueba el servidor."));else nextPhoto();};main.postDelayed(timeout,12_000);
        request=images.load(film.art,light?1280:1920,light?720:1080,b->{
            if(!active||epoch!=generation)return;if(timeout!=null)main.removeCallbacks(timeout);failures=0;status.setVisibility(GONE);veil.setAlpha(0);
            ImageView old=currentPhoto,next=old==photoA?photoB:photoA;next.animate().cancel();next.setScaleX(1);next.setScaleY(1);next.setImageBitmap(b);next.setAlpha(0f);currentPhoto=next;
            if(pan!=null)pan.cancel();
            next.animate().alpha(1).setDuration(Visuals.duration(getContext(),900)).withEndAction(()->{if(old!=null&&currentPhoto==next){old.animate().cancel();old.setAlpha(0f);old.setImageDrawable(null);}}).start();
            if(!Visuals.reducedMotion(getContext())&&ValueAnimator.areAnimatorsEnabled()){
                pan=ObjectAnimator.ofPropertyValuesHolder(next,PropertyValuesHolder.ofFloat(View.SCALE_X,1,1.045f),PropertyValuesHolder.ofFloat(View.SCALE_Y,1,1.045f));pan.setDuration(AmbientPrefs.seconds(getContext())*1000L);pan.setInterpolator(new android.view.animation.LinearInterpolator());pan.start();
            }
            showCaption(film.title,Ui.tr("Sua biblioteca · ","Your library · ","Tu biblioteca · ")+film.provider);main.removeCallbacks(advance);main.postDelayed(advance,AmbientPrefs.seconds(getContext())*1000L);
        });
    }
    private void showCaption(String name,String by){
        main.removeCallbacks(hideCaption);chrome.animate().cancel();caption.animate().cancel();
        boolean visible=AmbientPrefs.captions(getContext());title.setText(name);credit.setText(by);caption.setAlpha(visible?1:0);chrome.setAlpha(visible||infoPinned()?1:0);
        if(visible)main.postDelayed(hideCaption,8000);
    }
    private void updateInfo(){
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("HH:mm",Ui.locale());
        String zone=weatherSnapshot==null?"":weatherSnapshot.zone;
        if(!zone.isEmpty()&&Arrays.asList(java.util.TimeZone.getAvailableIDs()).contains(zone))format.setTimeZone(java.util.TimeZone.getTimeZone(zone));
        clock.setText(format.format(new Date()));
        boolean enabled=AmbientPrefs.weather(getContext());weatherText.setVisibility(enabled?VISIBLE:GONE);weatherCity.setVisibility(enabled?VISIBLE:GONE);
        if(!enabled){weatherIcon.setVisibility(GONE);return;}
        long now=System.currentTimeMillis();
        if(weatherSnapshot!=null&&weatherSnapshot.usable(now)){
            weatherText.setText(weatherSnapshot.degrees()+" · "+AmbientWeather.condition(weatherSnapshot.code));weatherCity.setText(weatherSnapshot.city+(weatherSnapshot.fresh(now)?"":L10n.text(" · Latest saved data")));
            String icon=AmbientWeather.icon(weatherSnapshot.code,weatherSnapshot.day);
            if(!icon.equals(weatherSymbol))try(java.io.InputStream in=getContext().getAssets().open("symbols/"+icon+".png")){weatherIcon.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(in,null));weatherSymbol=icon;}catch(java.io.IOException ignored){}
            weatherIcon.setVisibility(VISIBLE);
        }else{weatherIcon.setVisibility(GONE);weatherText.setText(weatherMessage.isEmpty()?L10n.text("Weather unavailable"):weatherMessage);AmbientWeather.Place place=AmbientWeather.manual(getContext());weatherCity.setText(place==null?"":place.name);}
    }
    private void message(String text){status.setText(text);status.setVisibility(VISIBLE);veil.setAlpha(currentPhoto==null?1:0);}
    private void resizeVideo(){if(videoWidth<=0||videoHeight<=0||getWidth()==0)return;float scale=Math.max(getWidth()/(float)videoWidth,getHeight()/(float)videoHeight);video.setLayoutParams(new LayoutParams(Math.round(videoWidth*scale),Math.round(videoHeight*scale),Gravity.CENTER));}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){resizeVideo();}
    @Override public void surfaceCreated(SurfaceHolder holder){if(active&&!plexMode&&!scenes.isEmpty()&&player==null)nextVideo();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){releasePlayer();}
    void stop(){
        active=false;generation++;main.removeCallbacksAndMessages(null);releasePlayer();
        if(weather!=null){weather.close();weather=null;}weatherSnapshot=null;weatherIcon.setImageDrawable(null);weatherSymbol="";
        if(pan!=null){pan.cancel();pan=null;}
        for(ImageView photo:new ImageView[]{photoA,photoB}){photo.animate().cancel();photo.setImageDrawable(null);photo.setAlpha(0f);}
        veil.animate().cancel();chrome.animate().cancel();caption.animate().cancel();currentPhoto=null;request=null;
        if(images!=null){images.close();images=null;}if(io!=null){io.shutdownNow();io=null;}
        films.clear();scenes.clear();video.setVisibility(GONE);
    }
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
