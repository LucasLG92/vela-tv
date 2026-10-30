package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Three native TV sources, recycled categories and twelve visible channel cards. */
public class LiveTvActivity extends Activity {
    private VelaAutoTune.Session autoTune;
    private void resumeAutoTune(){if(autoTune==null)autoTune=new VelaAutoTune.Session(this,new VelaAutoTune.Host(){public String source(){return LiveTvData.source(LiveTvActivity.this);}public boolean allowed(){return active&&!closed;}public void tune(VelaAutoTune.Plan plan){String profile=VelaWorkspace.active(LiveTvActivity.this);io.submit(()->{LiveTvData.Channel found=null;try{found=TvChannelLibrary.resolveActivity(LiveTvActivity.this,plan.scope,plan.key,plan.channel);}catch(Exception unavailable){}LiveTvData.Channel channel=found;main.post(()->{if(!active||closed||!profile.equals(VelaWorkspace.active(LiveTvActivity.this))||!plan.scope.equals(LiveTvData.source(LiveTvActivity.this)))return;if(channel==null){Toast.makeText(LiveTvActivity.this,L10n.text("This channel is no longer available."),0).show();return;}generation++;if(catalogJob!=null)catalogJob.cancel(true);if(guideJob!=null)guideJob.cancel(true);loading=false;guideLoading=false;play(channel);});});}});autoTune.resume();}
    private static final int WHITE=0xFFF6F7FB,DIM=0xFFA1ADBE,INK=0xFF121A26;
    private static final String ALL="@all",FAVORITES="@favorites";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Visuals.worker(2);
    private FrameLayout root,stage,grid,dialog;
    private TvTimelineView timeline;
    private TextView gridMode,guideMode,guideNow,guideEarlier,guideLater;
    private boolean timelineMode,timelineEnterLast,timelineFollowing=true;
    private int timelineOffset,timelineEpoch;
    private long timelineStart=TvTimelineView.present(System.currentTimeMillis()/1000),timelineCursor=-1;
    private Future<?> timelineJob;
    private List<LiveTvData.Channel> timelineChannels=Collections.emptyList();
    private Map<String,List<LiveTvData.Programme>> timelineProgrammes=Collections.emptyMap();
    private ListView groupList;
    private FrameLayout categoryViewport;
    private BaseAdapter groupAdapter;
    private final List<TvCatalogCache.Group> groups=new ArrayList<>();
    private final List<View> cards=new ArrayList<>();
    private final List<ImageView> images=new ArrayList<>();
    private final List<TextView> nowLabels=new ArrayList<>(),remainingLabels=new ArrayList<>();
    private final List<ProgrammeProgress> programmeBars=new ArrayList<>();
    private final Map<String,TextView> sourceTabs=new LinkedHashMap<>();
    private TextView status,selection,nowNext,previous,next,pageLabel,sourceButton,searchButton,sourceSelector,categoryTitle,empty;
    private HomeStatusView header;
    LiveTvData.Catalog catalog;
    private XtreamClient xtream;
    private M3uClient m3u;
    private AntennaClient antenna;
    private Map<String,LiveTvData.Guide> guide=Collections.emptyMap();
    private List<LiveTvData.Channel> filtered=Collections.emptyList(),pageChannels=Collections.emptyList();
    LiveTvData.Channel selected;
    private Set<String> favorites=Collections.emptySet();
    private String group="",query="",mode="",source="";
    private int page,total,generation,guideEpoch,consumeUp=-1;
    private long guideAt;
    private boolean active,closed,loading,guideLoading,longOk,firstPage=true;
    private Future<?> catalogJob,guideJob,pickerJob;private int pickerEpoch;
    private LiveTvLogos logos;
    LiveTvPlayer playback;
    private EditText privateUrl,privateEpg;
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    private final Runnable tick=new Runnable(){public void run(){if(!active)return;updateProgrammes();if(catalog!=null&&SystemClock.elapsedRealtime()-guideAt>5*60_000&&!guideLoading)loadGuide();main.postDelayed(this,60_000-System.currentTimeMillis()%60_000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);Ui.init(this);xtream=new XtreamClient(this);m3u=new M3uClient(this);antenna=new AntennaClient(this);
        root=new FrameLayout(this){@Override protected void onSizeChanged(int w,int h,int ow,int oh){if(stage==null)return;float s=Math.min(w/1920f,h/1080f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(s);stage.setScaleY(s);stage.setTranslationX((w-1920*s)/2);stage.setTranslationY((h-1080*s)/2);}};
        root.setBackgroundColor(0xFF0A1019);stage=new FrameLayout(this);stage.setClipChildren(false);root.addView(stage,new FrameLayout.LayoutParams(1920,1080));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF1B293B,0xFF101925,0xFF0A1019});stage.setBackground(background);
        put(stage,text(t("Em direto","Live TV","En directo"),55,WHITE,Ui.displaySemibold),86,92,1050,82);
        header=new HomeStatusView(this);put(stage,header,1504,64,340,76);
        timelineMode=LiveTvData.guideView(this);
        gridMode=button(t("Canais","Channels","Canales"));put(stage,gridMode,452,184,152,56);gridMode.setOnClickListener(v->setTimelineMode(false));
        guideMode=button(t("Guia","Guide","Guía"));put(stage,guideMode,616,184,152,56);guideMode.setOnClickListener(v->setTimelineMode(true));
        guideNow=button(t("Agora","Now","Ahora"));put(stage,guideNow,1000,184,116,56);guideNow.setOnClickListener(v->{timelineFollowing=true;timelineCursor=System.currentTimeMillis()/1000;timelineStart=TvTimelineView.present(timelineCursor);loadTimeline(true);});
        guideEarlier=button("‹");guideEarlier.setContentDescription(t("Programação anterior","Earlier programmes","Programación anterior"));put(stage,guideEarlier,1132,184,56,56);guideEarlier.setOnClickListener(v->shiftTime(-1,timelineStart-1));
        guideLater=button("›");guideLater.setContentDescription(t("Próxima programação","Later programmes","Programación posterior"));put(stage,guideLater,1204,184,56,56);guideLater.setOnClickListener(v->shiftTime(1,timelineStart+TvTimelineView.SECONDS));
        tab("m3u","M3U",88);tab("xtream","Xtream",268);tab("antenna",t("Antena","Antenna","Antena"),448);
        sourceSelector=button("");put(stage,sourceSelector,88,184,286,56);sourceSelector.setOnClickListener(v->showSourcePicker());
        searchButton=button(t("Buscar","Search","Buscar"));put(stage,searchButton,1440,184,188,56);searchButton.setOnClickListener(v->showSearch());
        sourceButton=button("⚙");sourceButton.setContentDescription(t("Listas e ajustes","Playlists and settings","Listas y ajustes"));put(stage,sourceButton,1644,184,88,56);sourceButton.setOnClickListener(v->showSources());
        TextView back=button("‹");back.setContentDescription(t("Voltar ao início","Back to home","Volver al inicio"));put(stage,back,1748,184,96,56);back.setOnClickListener(v->finish());
        categoryTitle=text(t("CATEGORIAS","CATEGORIES","CATEGORÍAS"),16,0xFF8192AA,Ui.semibold);categoryTitle.setLetterSpacing(.1f);put(stage,categoryTitle,100,277,300,34);
        // The stage permits enlarged card focus. Category rows need their own fixed clipping boundary.
        categoryViewport=new FrameLayout(this){@Override protected void dispatchDraw(Canvas canvas){int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(save);}};
        categoryViewport.setClipChildren(true);categoryViewport.setClipToPadding(true);put(stage,categoryViewport,88,326,304,570);
        groupList=new ListView(this);groupList.setId(View.generateViewId());groupList.setDivider(null);groupList.setVerticalScrollBarEnabled(false);groupList.setPadding(0,0,0,0);groupList.setClipChildren(true);groupList.setClipToPadding(true);groupList.setOverScrollMode(View.OVER_SCROLL_NEVER);groupList.setCacheColorHint(Color.TRANSPARENT);groupList.setSelector(round(0x447C91AF,13));groupList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);put(categoryViewport,groupList,0,0,304,570);
        groupAdapter=new BaseAdapter(){public int getCount(){return groups.size();}public Object getItem(int i){return groups.get(i);}public long getItemId(int i){return groups.get(i).id.hashCode();}public View getView(int i,View recycled,android.view.ViewGroup parent){TextView label=recycled instanceof TextView?(TextView)recycled:text("",21,WHITE,Ui.medium);label.setLayoutParams(new AbsListView.LayoutParams(-1,64));label.setPadding(18,0,14,0);label.setSingleLine();label.setEllipsize(TextUtils.TruncateAt.END);TvCatalogCache.Group item=groups.get(i);label.setText(categoryName(item.name));label.setContentDescription(item.name);label.setBackground(item.id.equals(group)?round(0xFF334158,13):null);return label;}};
        groupList.setAdapter(groupAdapter);groupList.setOnItemClickListener((parent,view,position,id)->{group=groups.get(position).id;page=0;query="";timelineOffset=0;selected=null;renderPage(0,false);});
        grid=new FrameLayout(this);grid.setClipChildren(false);put(stage,grid,452,278,1392,594);
        timeline=new TvTimelineView(this,new TvTimelineView.Actions(){
            public void selected(LiveTvData.Channel ch,LiveTvData.Programme p){LiveTvActivity.this.selected=ch;timelineSelection(ch,p);}
            public void activate(LiveTvData.Channel ch,LiveTvData.Programme p){if(p==null||p.current(System.currentTimeMillis()/1000))play(ch);else showUpcoming(ch,p);}
            public void options(LiveTvData.Channel ch){toggleFavorite(ch);}
            public boolean channels(int direction){return timelineChannels(direction,true);}
            public void time(int direction,long cursor){shiftTime(direction,cursor);}
        });put(stage,timeline,452,278,1392,594);timeline.setNextFocusLeftId(groupList.getId());timeline.setNextFocusUpId(guideNow.getId());
        empty=text("",29,DIM,Ui.regular);empty.setGravity(Gravity.CENTER);empty.setMaxLines(4);put(stage,empty,540,400,1190,220);
        previous=button("‹ "+t("Anterior","Previous","Anterior"));put(stage,previous,452,890,206,52);previous.setOnClickListener(v->{if(timelineMode)timelineChannels(-1,false);else renderPage(page-1,true);});
        pageLabel=text("",18,DIM,Ui.medium);pageLabel.setGravity(Gravity.CENTER);put(stage,pageLabel,682,890,922,52);
        next=button(t("Seguinte","Next","Siguiente")+" ›");put(stage,next,1628,890,216,52);next.setOnClickListener(v->{if(timelineMode)timelineChannels(1,false);else renderPage(page+1,true);});
        selection=text("",27,WHITE,Ui.displaySemibold);selection.setSingleLine(true);selection.setEllipsize(TextUtils.TruncateAt.END);put(stage,selection,452,964,1392,38);
        nowNext=text("",19,DIM,Ui.regular);nowNext.setSingleLine(true);nowNext.setEllipsize(TextUtils.TruncateAt.END);put(stage,nowNext,452,1008,1392,34);
        status=text("",17,DIM,Ui.regular);status.setGravity(Gravity.TOP);status.setMaxLines(5);put(stage,status,100,926,292,122);
        dialog=new FrameLayout(this);dialog.setBackgroundColor(0xFF101824);dialog.setVisibility(View.GONE);put(stage,dialog,0,0,1920,1080);
        updateViewMode();setContentView(root);getWindow().setBackgroundDrawable(null);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private TextView text(String value,int size,int color,Typeface font){TextView v=new TextView(this);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(font);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView button(String label){TextView b=text(label,21,WHITE,Ui.medium);b.setId(View.generateViewId());b.setSingleLine();b.setEllipsize(TextUtils.TruncateAt.END);b.setGravity(Gravity.CENTER);b.setPadding(16,0,16,0);b.setFocusable(true);b.setClickable(true);b.setDefaultFocusHighlightEnabled(false);b.setBackground(round(0xFF253145,15));b.setOnFocusChangeListener((v,f)->{b.setBackground(round(f?0xFFF3F5FA:0xFF253145,15));b.setTextColor(f?INK:WHITE);});return b;}
    private void tab(String key,String label,int x){TextView b=button(label);sourceTabs.put(key,b);b.setOnFocusChangeListener((v,f)->{b.setBackground(round(f?0xFFF3F5FA:key.equals(mode)?0xFF435571:0xFF253145,15));b.setTextColor(f?INK:WHITE);});b.setOnClickListener(v->{if(key.equals("xtream")&&!xtream.connected()){openAccount();return;}if(playback!=null)browse();LiveTvData.select(this,key);query="";closeDialog();load(false);});}
    private void tabs(){for(Map.Entry<String,TextView> e:sourceTabs.entrySet()){TextView v=e.getValue();if(!v.hasFocus()){v.setBackground(round(e.getKey().equals(mode)?0xFF435571:0xFF253145,15));v.setTextColor(WHITE);}}sourceSelector.setText((mode.equals("xtream")?"Xtream":mode.equals("antenna")?t("Antena","Antenna","Antena"):"M3U")+"  ⌄");searchButton.setText(query.isEmpty()?t("Buscar","Search","Buscar"):t("Buscar","Search","Buscar")+" · "+query);}
    private void load(boolean force){
        if(!active||closed)return;String now=LiveTvData.source(this);if(!now.equals(source)){
            cancelTimeline();timelineOffset=0;timelineProgrammes=Collections.emptyMap();timelineStart=TvTimelineView.present(System.currentTimeMillis()/1000);savePosition();source=now;mode=LiveTvData.mode(this);catalog=null;selected=null;guide=Collections.emptyMap();firstPage=true;group=LiveTvData.prefs(this).getString("tv-group-"+LiveTvData.hash(source),"");page=LiveTvData.prefs(this).getInt("tv-page-"+LiveTvData.hash(source),0);query="";clearLogos();cards.clear();images.clear();grid.removeAllViews();
        }tabs();loadPage(page,firstPage,0,force);
    }
    private void renderPage(int requested,boolean focus){loadPage(Math.max(0,requested),focus,0,false);}
    private void loadPage(int requested,boolean focus,int zap,boolean force){
        if(!active||closed)return;if(catalogJob!=null)catalogJob.cancel(true);if(guideJob!=null)guideJob.cancel(true);cancelTimeline();loading=true;guideLoading=false;int epoch=++generation;String wantedSource=source,wantedMode=mode,wantedGroup=group,wantedQuery=query,selectedId=selected==null?LiveTvData.prefs(this).getString("last-"+LiveTvData.hash(source),""):selected.id,selectedFamily=selected==null?LiveTvData.prefs(this).getString("last-family-"+LiveTvData.hash(source),""):TvChannelNames.family(selected);
        status.setText(t("Carregando canais…","Loading channels…","Cargando canales…"));
        catalogJob=io.submit(()->{try{
            List<TvCatalogCache.Group> available=new ArrayList<>();TvCatalogCache.Page result;boolean cached=false;String chosen=wantedGroup;Set<String> chosenFavorites;
            available.add(new TvCatalogCache.Group(ALL,t("Todos os canais","All channels","Todos los canales"),0));available.add(new TvCatalogCache.Group(FAVORITES,"★ "+t("Favoritos","Favorites","Favoritos"),0));
            if(wantedMode.equals("xtream")){
                List<XtreamClient.Category> cats=xtream.categories(force);for(XtreamClient.Category c:cats)available.add(new TvCatalogCache.Group("@xc:"+c.id,c.name,0));chosenFavorites=xtream.favoriteIds();
                if(chosen.isEmpty())chosen=available.size()>2?available.get(2).id:ALL;if(!contains(available,chosen))chosen=ALL;
                XtreamClient.Page data=chosen.equals(FAVORITES)?xtream.favoritePage(requested,wantedQuery):xtream.page(chosen.equals(ALL)?"*":chosen.substring(4),name(available,chosen),requested,wantedQuery,force);result=new TvCatalogCache.Page(data.channels,data.page,data.total);
            }else{
                chosenFavorites=LiveTvData.favorites(this,wantedSource);
                if(wantedMode.equals("m3u")){cached=m3u.refresh(force);for(TvCatalogCache.Group c:m3u.groups())available.add(new TvCatalogCache.Group("@m3u:"+c.id,c.name,c.count));if(!contains(available,chosen))chosen=ALL;result=m3u.page(chosen.startsWith("@m3u:")?chosen.substring(5):null,wantedQuery,chosen.equals(FAVORITES)?chosenFavorites:null,requested);}
                else{if(!contains(available,chosen))chosen=ALL;result=antenna.page(wantedQuery,chosen.equals(FAVORITES)?chosenFavorites:null,requested);}
            }
            final String actualGroup=chosen;final boolean offline=cached;main.post(()->{
                if(!active||closed||epoch!=generation||!wantedSource.equals(LiveTvData.source(this)))return;loading=false;total=result.total;page=result.page;group=actualGroup;favorites=chosenFavorites;groups.clear();groups.addAll(available);catalog=new LiveTvData.Catalog(source,result.channels,offline);guide=Collections.emptyMap();selected=null;for(LiveTvData.Channel c:result.channels)if(c.id.equals(selectedId)||TvChannelNames.family(c).equals(selectedFamily))selected=c;
                groupAdapter.notifyDataSetChanged();int index=groupIndex(group);groupList.setItemChecked(index,true);if(firstPage)groupList.setSelection(index);tabs();renderCards(page,focus);firstPage=false;savePosition();loadGuide();if(zap!=0&&playback!=null&&!result.channels.isEmpty())play(result.channels.get(zap>0?0:result.channels.size()-1));
            });
        }catch(Exception error){main.post(()->{if(!active||closed||epoch!=generation)return;loading=false;status.setText(t("Não foi possível carregar. Confira a lista e tente Atualizar.","Could not load channels. Check your playlist and try Refresh.","No se han podido cargar los canales. Revisa la lista y pulsa Actualizar."));if(catalog==null){empty.setText(wantedMode.equals("antenna")?t("A TV não permite ler seus canais. Abra os ajustes de antena.","The TV did not share its channels. Open antenna settings.","La TV no permite leer sus canales. Abre los ajustes de antena."):t("Conecte sua lista para começar.","Connect your playlist to get started.","Conecta tu lista para empezar."));empty.setVisibility(View.VISIBLE);}if(getCurrentFocus()==null)sourceButton.requestFocus();});}});
    }
    private static boolean contains(List<TvCatalogCache.Group> groups,String id){for(TvCatalogCache.Group g:groups)if(g.id.equals(id))return true;return false;}
    private static String name(List<TvCatalogCache.Group> groups,String id){for(TvCatalogCache.Group g:groups)if(g.id.equals(id))return g.name;return "";}
    private int groupIndex(String id){for(int i=0;i<groups.size();i++)if(groups.get(i).id.equals(id))return i;return 0;}
    private void savePosition(){if(source.isEmpty()||catalog==null)return;SharedPreferences.Editor e=LiveTvData.prefs(this).edit().putString("tv-group-"+LiveTvData.hash(catalog.source),group).putInt("tv-page-"+LiveTvData.hash(catalog.source),page);if(selected!=null)e.putString("last-"+LiveTvData.hash(catalog.source),selected.id).putString("last-family-"+LiveTvData.hash(catalog.source),TvChannelNames.family(selected));e.apply();}
    static String displayName(String name){return TvChannelNames.display(name);}
    private static String quality(String name){return TvChannelNames.quality(name);}
    private String categoryName(String value){String clean=value.replaceFirst("(?i)^ES\\s*[|:]\\s*","");if(clean.equalsIgnoreCase("National & General"))return t("Generalistas","General","Generalistas");if(clean.equalsIgnoreCase("News & International"))return t("Notícias","News","Noticias");if(clean.equalsIgnoreCase("Local TV"))return t("Locais","Local","Locales");if(clean.startsWith("Entertainment &"))return t("Entretenimento","Entertainment","Entretenimiento");if(clean.equalsIgnoreCase("Movies"))return t("Cinema","Movies","Cine");if(clean.startsWith("FAST &"))return t("Temáticos","Themed","Temáticos");if(clean.startsWith("Regional & Autonom"))return t("Regionais","Regional","Autonómicos");return clean;}
    private void renderCards(int requested,boolean focus){
        if(catalog==null)return;if(logos==null)logos=new LiveTvLogos(this);logos.page();for(ImageView image:images)image.setImageDrawable(null);images.clear();nowLabels.clear();remainingLabels.clear();programmeBars.clear();cards.clear();grid.removeAllViews();filtered=catalog.channels;pageChannels=catalog.channels;boolean more=(page+1)*LiveTvData.PAGE_SIZE<total;
        if(timelineMode){renderTimeline(focus);return;}updateViewMode();
        for(int i=0;i<pageChannels.size();i++){
            LiveTvData.Channel ch=pageChannels.get(i);FrameLayout card=new FrameLayout(this);card.setId(View.generateViewId());card.setFocusable(true);card.setClickable(true);card.setDefaultFocusHighlightEnabled(false);card.setClipChildren(false);put(grid,card,(i%6)*234,(i/6)*306,216,286);cards.add(card);
            TvLogoTile tile=new TvLogoTile(this);put(card,tile,16,0,184,184);
            LiveTvLogos.LogoView logo=new LiveTvLogos.LogoView(this,displayName(ch.name));logo.setColorListener(tile::color);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);put(tile,logo,16,28,152,128);images.add(logo);if(playback==null)logos.load(ch,logo);
            TextView badge=text(ch.antenna()?ch.number:quality(ch.name),12,0xFFC4CEDD,Ui.semibold);badge.setGravity(Gravity.RIGHT);put(tile,badge,102,10,66,20);
            if(TvChannelNames.favorite(ch,favorites)){TextView star=text("★",16,Appearance.accent(this),Ui.semibold);put(tile,star,14,10,40,24);}
            TextView title=text(displayName(ch.name),22,WHITE,Ui.medium);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setGravity(Gravity.CENTER);put(card,title,0,194,216,29);
            TextView current=text("",16,DIM,Ui.regular);current.setSingleLine(true);current.setEllipsize(TextUtils.TruncateAt.END);current.setGravity(Gravity.CENTER);put(card,current,0,229,216,23);nowLabels.add(current);
            TextView remaining=text("",14,0xFFBAC6D6,Ui.medium);remaining.setSingleLine(true);remaining.setGravity(Gravity.CENTER);put(card,remaining,0,253,216,20);remainingLabels.add(remaining);
            ProgrammeProgress progress=new ProgrammeProgress(this);put(card,progress,16,281,184,3);programmeBars.add(progress);
            card.setOnFocusChangeListener((v,has)->{tile.focused(has);progress.focused(has);title.setTextColor(has?WHITE:0xFFD5DCE7);if(has){selected=ch;updateSelection();}tile.animate().cancel();tile.animate().scaleX(has&&!Visuals.reducedMotion(this)?1.055f:1).scaleY(has&&!Visuals.reducedMotion(this)?1.055f:1).setDuration(Visuals.duration(this,110)).start();});
            card.setOnClickListener(v->play(ch));card.setOnLongClickListener(v->{card.setPressed(false);toggleFavorite(ch);return true;});TvRemotePress press=new TvRemotePress(card,card::performClick,card::performLongClick);
            if(i%6==0)card.setNextFocusLeftId(groupList.getId());if(i<6)card.setNextFocusUpId(searchButton.getId());
            final int index=i;card.setOnKeyListener((v,key,event)->{if(press.key(event))return true;if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&index==cards.size()-1&&more){renderPage(page+1,true);consumeUp=key;return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT&&index==0&&page>0){renderPage(page-1,true);consumeUp=key;return true;}return false;});
        }
        empty.setVisibility(pageChannels.isEmpty()?View.VISIBLE:View.GONE);empty.setText(mode.equals("antenna")&&!antenna.available()?t("Este dispositivo não tem sintonizador de antena.","This device has no antenna tuner.","Este dispositivo no tiene sintonizador de antena."):group.equals(FAVORITES)?t("Guarde seus canais mantendo OK pressionado.","Hold OK on a channel to add a favorite.","Mantén OK en un canal para añadirlo a favoritos."):!query.isEmpty()?t("Nenhum canal encontrado.","No matching channels.","No hay canales con ese nombre."):mode.equals("antenna")?t("Não há canais sintonizados. Abra os ajustes de antena.","No tuned channels. Open antenna settings.","No hay canales sintonizados. Abre los ajustes de antena."):t("Esta categoria não tem canais.","This category has no channels.","Esta categoría no tiene canales."));
        if(pageChannels.isEmpty())selected=null;else if(selected==null)selected=pageChannels.get(0);
        previous.setEnabled(page>0);previous.setFocusable(page>0);previous.setAlpha(page>0?1:.3f);next.setEnabled(more);next.setFocusable(more);next.setAlpha(more?1:.3f);
        pageLabel.setText(total==0?"0":(page*12+1)+"–"+(page*12+pageChannels.size())+" / "+total);
        status.setText((catalog.cached?t("Lista salva · sem conexão","Saved list · offline","Lista guardada · sin conexión"):mode.equals("antenna")?t("Sintonizador da TV","TV tuner","Sintonizador de la TV"):mode.equals("xtream")?"Xtream":"M3U")+t("\nOK ver\nManter OK · Opções","\nOK watch\nHold OK · Options","\nOK ver\nMantener OK · Opciones"));
        updateProgrammes();if(dialog.getVisibility()!=View.VISIBLE&&playback==null&&focus&&!cards.isEmpty())cards.get(Math.max(0,pageChannels.indexOf(selected))).requestFocus();
    }
    private void setTimelineMode(boolean value){
        if(timelineMode==value){if(value)timeline.requestFocus();return;}timelineMode=value;updateViewMode();cancelTimeline();timelineProgrammes=Collections.emptyMap();
        timelineOffset=selected==null?0:Math.max(0,pageChannels.indexOf(selected))/VelaReading.rows(this)*VelaReading.rows(this);renderCards(page,true);
    }
    private void updateViewMode(){
        boolean showing=timelineMode;grid.setVisibility(showing?View.GONE:View.VISIBLE);timeline.setVisibility(showing?View.VISIBLE:View.GONE);
        for(TextView b:new TextView[]{guideNow,guideEarlier,guideLater})b.setVisibility(showing?View.VISIBLE:View.GONE);
        gridMode.setVisibility(View.GONE);guideMode.setVisibility(View.GONE);gridMode.setSelected(!showing);guideMode.setSelected(showing);
        for(TextView b:new TextView[]{gridMode,guideMode}){b.setOnFocusChangeListener((v,f)->{b.setBackground(round(f?0xFFF3F5FA:b.isSelected()?0xFF3B4E68:0xFF253145,15));b.setTextColor(f?INK:WHITE);});if(!b.hasFocus())b.setBackground(round(b.isSelected()?0xFF3B4E68:0xFF253145,15));}
        long present=TvTimelineView.present(System.currentTimeMillis()/1000);guideEarlier.setEnabled(timelineStart>present);guideEarlier.setAlpha(timelineStart>present?1:.3f);guideLater.setEnabled(timelineStart<present+45*3600);guideLater.setAlpha(timelineStart<present+45*3600?1:.3f);
    }
    private boolean timelineChannels(int direction,boolean edge){
        if(loading)return true;int offset=timelineOffset+direction*VelaReading.rows(this);
        if(offset>=0&&offset<pageChannels.size()){timelineOffset=offset;selected=pageChannels.get(direction>0?offset:Math.min(offset+VelaReading.rows(this)-1,pageChannels.size()-1));renderCards(page,true);return true;}
        int target=page+direction;if(target<0||target*LiveTvData.PAGE_SIZE>=total)return false;
        timelineOffset=direction>0?0:VelaReading.rows(this);timelineEnterLast=edge&&direction<0;selected=null;loadPage(target,true,0,false);return true;
    }
    private void renderTimeline(boolean focus){
        if(pageChannels.isEmpty())timelineOffset=0;else timelineOffset=Math.max(0,Math.min(timelineOffset,(pageChannels.size()-1)/VelaReading.rows(this)*VelaReading.rows(this)));
        timelineChannels=new ArrayList<>(pageChannels.subList(timelineOffset,Math.min(pageChannels.size(),timelineOffset+VelaReading.rows(this))));
        if(!timelineChannels.contains(selected))selected=timelineChannels.isEmpty()?null:timelineChannels.get(timelineEnterLast?timelineChannels.size()-1:0);timelineEnterLast=false;
        timeline.removeAllViews();for(int i=0;i<timelineChannels.size();i++){
            LiveTvData.Channel ch=timelineChannels.get(i);LiveTvLogos.LogoView logo=new LiveTvLogos.LogoView(this,displayName(ch.name));logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setFocusable(false);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);put(timeline,logo,10,timeline.logoY(i),60,60);images.add(logo);if(playback==null)logos.load(ch,logo);
        }
        timeline.data(timelineChannels,Collections.emptyMap(),timelineStart,selected,true);timelineProgrammes=Collections.emptyMap();updateViewMode();
        boolean before=page>0||timelineOffset>0,after=page*12+timelineOffset+timelineChannels.size()<total;timeline.setNextFocusDownId(after?next.getId():before?previous.getId():guideNow.getId());
        previous.setEnabled(before);previous.setFocusable(before);previous.setAlpha(before?1:.3f);next.setEnabled(after);next.setFocusable(after);next.setAlpha(after?1:.3f);
        pageLabel.setText(total==0?"0":(page*12+timelineOffset+1)+"–"+(page*12+timelineOffset+timelineChannels.size())+" / "+total);
        empty.setVisibility(timelineChannels.isEmpty()?View.VISIBLE:View.GONE);empty.setText(t("Não há canais nesta seleção.","No channels in this selection.","No hay canales en esta selección."));
        status.setText(t("Flechas · Explorar\nOK · Ver\nManter OK · Opções","Arrows · Explore\nOK · Watch\nHold OK · Options","Flechas · Explorar\nOK · Ver\nMantener OK · Opciones"));
        if(focus&&dialog.getVisibility()!=View.VISIBLE&&playback==null&&!timelineChannels.isEmpty())timeline.requestFocus();loadTimeline(false);
    }
    private void cancelTimeline(){timelineEpoch++;if(timelineJob!=null)timelineJob.cancel(true);}
    private void shiftTime(int direction,long cursor){
        long present=TvTimelineView.present(System.currentTimeMillis()/1000),nextStart=Math.max(present,Math.min(present+45*3600,timelineStart+direction*90*60));if(nextStart==timelineStart)return;
        timelineFollowing=false;timelineStart=nextStart;timelineCursor=cursor;loadTimeline(true);
    }
    private void loadTimeline(boolean focus){
        if(!active||closed||playback!=null||!timelineMode||timelineChannels.isEmpty())return;cancelTimeline();int token=timelineEpoch,epoch=generation;long from=timelineStart;List<LiveTvData.Channel> visible=new ArrayList<>(timelineChannels);Map<String,LiveTvData.Guide> fallback=new HashMap<>(guide);String currentMode=mode;
        timeline.data(visible,Collections.emptyMap(),from,selected,true);if(timelineCursor>=0){timeline.cursor(timelineCursor);timelineCursor=-1;}updateViewMode();if(focus)timeline.requestFocus();
        timelineJob=io.submit(()->{Map<String,List<LiveTvData.Programme>> result=new HashMap<>();try{result.putAll(new XmlTvGuide(this).window(visible,from));if(currentMode.equals("antenna")){List<LiveTvData.Channel> missing=new ArrayList<>();for(LiveTvData.Channel ch:visible)if(!result.containsKey(ch.id))missing.add(ch);result.putAll(antenna.window(missing,from));}}catch(Exception unavailable){}
            for(LiveTvData.Channel ch:visible){List<LiveTvData.Programme> list=new ArrayList<>(result.getOrDefault(ch.id,Collections.emptyList()));LiveTvData.Guide entry=fallback.get(ch.id);if(entry!=null)for(LiveTvData.Programme p:new LiveTvData.Programme[]{entry.now,entry.next})if(p!=null&&p.end>from&&p.start<from+TvTimelineView.SECONDS){boolean overlaps=false;for(LiveTvData.Programme known:list)if(known.start<p.end&&known.end>p.start){overlaps=true;break;}if(!overlaps)list.add(p);}list.sort((a,b)->Long.compare(a.start,b.start));if(list.size()>TvTimelineView.MAX_PROGRAMMES)list=new ArrayList<>(list.subList(0,TvTimelineView.MAX_PROGRAMMES));result.put(ch.id,list);}
            main.post(()->{if(!active||closed||!timelineMode||epoch!=generation||token!=timelineEpoch)return;timelineProgrammes=result;timeline.data(visible,result,from,selected,false);timeline.minute(System.currentTimeMillis()/1000);});
        });
    }
    private void timelineSelection(LiveTvData.Channel ch,LiveTvData.Programme programme){
        long now=System.currentTimeMillis()/1000;selection.setText(programme==null?displayName(ch.name):programme.title);
        nowNext.setText(displayName(ch.name)+"   ·   "+(programme==null?t("Programação não disponível","Programme guide unavailable","Programación no disponible"):time(programme.start)+"–"+time(programme.end)+"   ·   "+(programme.current(now)?remainingText(programme.remainingMinutes(now)):programme.end<=now?t("Terminado","Ended","Finalizado"):t("A seguir","Upcoming","Próximamente"))));
    }
    private VelaProgrammeArt upcomingArt;private VelaContentDetails upcomingCard;
    private void clearUpcoming(){if(upcomingArt!=null){upcomingArt.close();upcomingArt=null;}upcomingCard=null;}
    private void showUpcoming(LiveTvData.Channel ch,LiveTvData.Programme programme){
        openDialog("","");dialog.removeAllViews();for(int i=0;i<stage.getChildCount();i++)if(stage.getChildAt(i)!=dialog)stage.getChildAt(i).setVisibility(View.VISIBLE);updateViewMode();dialog.setBackground(VelaContentDetails.programmeScrim());
        VelaContentDetails card=new VelaContentDetails(this,programme.title,displayName(ch.name)+" · "+time(programme.start)+"–"+time(programme.end)+" · "+L10n.text(programme.end<=System.currentTimeMillis()/1000?"Ended":"Upcoming"),programme.description,-1);upcomingCard=card;String artwork=VelaProgrammeArt.url(programme.image.isEmpty()?ch.logo:programme.image);card.broadcast(!artwork.isEmpty());put(dialog,card,265,210,1390,660);if(!artwork.isEmpty()){upcomingArt=new VelaProgrammeArt(this,dialog);upcomingArt.bind(card.image,artwork);}VelaAutoTune.action(card,this,source,ch.id,displayName(ch.name),programme);card.action(L10n.text("Watch channel live"),"",()->play(ch));card.action(L10n.text("Back to guide"),"",()->{closeDialog();timeline.requestFocus();});card.actions.getChildAt(0).requestFocus();
    }
    private void loadGuide(){
        if(!active||catalog==null||guideLoading||pageChannels.isEmpty())return;guideLoading=true;int epoch=generation,guideToken=++guideEpoch;List<LiveTvData.Channel> snapshot=new ArrayList<>(pageChannels);String currentMode=mode;
        guideJob=io.submit(()->{Map<String,LiveTvData.Guide> fresh=new HashMap<>();try{try{fresh.putAll(new XmlTvGuide(this).guide(snapshot));}catch(Exception unavailable){if(Thread.currentThread().isInterrupted())throw unavailable;}List<LiveTvData.Channel> missing=new ArrayList<>();long now=System.currentTimeMillis()/1000;for(LiveTvData.Channel ch:snapshot)if(!fresh.containsKey(ch.id)||fresh.get(ch.id).current(now)==null)missing.add(ch);if(!missing.isEmpty()){Map<String,LiveTvData.Guide> fallback=currentMode.equals("xtream")?xtream.guide(missing):currentMode.equals("antenna")?antenna.guide(missing):m3u.guide(missing);for(Map.Entry<String,LiveTvData.Guide> entry:fallback.entrySet())if(!fresh.containsKey(entry.getKey())||entry.getValue().current(now)!=null)fresh.put(entry.getKey(),entry.getValue());}}catch(Exception failure){}Map<String,LiveTvData.Guide> result=fresh;main.post(()->{if(!active||closed||epoch!=generation||guideToken!=guideEpoch)return;guideLoading=false;guideAt=SystemClock.elapsedRealtime();guide=result;updateProgrammes();if(timelineMode)loadTimeline(false);if(logos!=null&&playback==null)for(int i=0;i<images.size()&&i<pageChannels.size();i++)if(images.get(i).getDrawable()==null)logos.load((timelineMode?timelineChannels:pageChannels).get(i),images.get(i));});});
    }
    private void reloadGuide(){if(guideJob!=null)guideJob.cancel(true);guideEpoch++;guideLoading=false;guideAt=0;loadGuide();}
    private String time(long seconds){return new SimpleDateFormat("HH:mm",Ui.locale()).format(new Date(seconds*1000));}
    private String currentText(LiveTvData.Channel ch,LiveTvData.Programme current){return current!=null?current.title:t("Em direto","Live","En directo");}
    static String remainingText(long minutes){
        String duration=minutes>60?minutes/60+" h"+(minutes%60==0?"":" "+minutes%60+" min"):minutes+" min";
        return minutes==1?t("Falta "+duration,duration+" left","Queda "+duration):t("Faltam "+duration,duration+" left","Quedan "+duration);
    }
    private void updateProgrammes(){updateProgrammes(System.currentTimeMillis()/1000);}
    private void updateProgrammes(long now){
        for(int i=0;i<pageChannels.size()&&i<nowLabels.size();i++){
            LiveTvData.Channel ch=pageChannels.get(i);LiveTvData.Guide entry=guide.get(ch.id);LiveTvData.Programme current=entry==null?null:entry.current(now);
            String title=currentText(ch,current),remaining=current==null?"":remainingText(current.remainingMinutes(now));
            TextView titleView=nowLabels.get(i),remainingView=remainingLabels.get(i);if(!title.contentEquals(titleView.getText()))titleView.setText(title);if(!remaining.contentEquals(remainingView.getText()))remainingView.setText(remaining);
            int visibility=current==null?View.INVISIBLE:View.VISIBLE;remainingView.setVisibility(visibility);ProgrammeProgress progress=programmeBars.get(i);progress.setVisibility(visibility);progress.value(current==null?0:current.progress(now));
            cards.get(i).setContentDescription(ch.name+(TvChannelNames.favorite(ch,favorites)?" · ★":"")+" · "+title+(remaining.isEmpty()?"":" · "+remaining));
        }
        if(timelineMode&&playback==null){if(timelineFollowing&&timelineStart!=TvTimelineView.present(now)){timelineStart=TvTimelineView.present(now);timelineCursor=now;loadTimeline(false);}timeline.minute(now);}else updateSelection(now);
    }
    private void updateSelection(){updateSelection(System.currentTimeMillis()/1000);}
    private void updateSelection(long now){
        if(timelineMode&&playback==null&&selected!=null){timelineSelection(selected,timeline.programme());return;}if(selected==null){selection.setText("");nowNext.setText("");return;}selection.setText(displayName(selected.name)+" · "+categoryName(selected.group));LiveTvData.Guide entry=guide.get(selected.id);LiveTvData.Programme current=entry==null?null:entry.current(now);
        String line=current!=null?time(current.start)+"–"+time(current.end)+"  "+current.title:t("Programação não disponível","Programme guide unavailable","Programación no disponible");
        if(entry!=null&&entry.next!=null&&entry.next.start>now)line+="   ·   "+time(entry.next.start)+"  "+entry.next.title;nowNext.setText(line);
        if(playback!=null){playback.metadata(current,entry==null?null:entry.next);if(entry!=null&&entry.next!=null&&entry.next.start<=now&&!guideLoading&&SystemClock.elapsedRealtime()-guideAt>=15000)loadGuide();}
    }
    private void toggleFavorite(LiveTvData.Channel ch){
        if(catalog==null||loading)return;boolean added=!TvChannelNames.favorite(ch,favorites);if(added&&favorites.size()>=128){Toast.makeText(this,t("Máximo 128 favoritos.","Maximum 128 favorites.","Máximo 128 favoritos."),Toast.LENGTH_SHORT).show();return;}
        if(mode.equals("xtream"))try{xtream.toggleFavorite(ch);favorites=xtream.favoriteIds();}catch(Exception failed){Toast.makeText(this,t("Não foi possível guardar.","Could not save favorite.","No se ha podido guardar el favorito."),Toast.LENGTH_SHORT).show();return;}
        else{if(TvChannelNames.favorite(ch,favorites)){for(LiveTvData.Channel v:TvChannelNames.variants(ch))if(favorites.contains(v.id))LiveTvData.toggle(this,catalog.source,v.id);}else LiveTvData.toggle(this,catalog.source,ch.id);favorites=LiveTvData.favorites(this,catalog.source);}
        if(playback!=null){playback.favorites(favorites);updateSelection();}else if(timelineMode){Set<String> stars=new HashSet<>();for(LiveTvData.Channel item:pageChannels)if(TvChannelNames.favorite(item,favorites))stars.add(item.id);timeline.favorites(stars);}else renderPage(page,true);Toast.makeText(this,added?t("Adicionado aos favoritos","Added to favorites","Añadido a favoritos"):t("Removido dos favoritos","Removed from favorites","Quitado de favoritos"),Toast.LENGTH_SHORT).show();
    }
    void play(LiveTvData.Channel ch){
        if(loading||catalog!=null&&!catalog.source.equals(LiveTvData.source(this))||!ch.playable())return;selected=ch;savePosition();cancelTimeline();closeDialog();clearLogos();stage.setVisibility(View.GONE);header.stop();
        if(playback==null){playerGroup=group.equals(FAVORITES)||group.equals(TvChannelLibrary.RECENT)?ALL:group;playback=new LiveTvPlayer(this,new LiveTvPlayer.Actions(){public void back(){browse();}public void zap(int direction){changeChannel(direction);}public void channels(int page){loadPlayerChannels(page);}public void choose(LiveTvData.Channel channel){choosePlayerChannel(channel);}public void favorite(LiveTvData.Channel channel){toggleFavorite(channel);}public void channels(int page,boolean favorites){loadPlayerChannels(page,favorites);}public void previous(){String scope=source,key=selected==null?"":TvChannelLibrary.key(selected);io.submit(()->{for(LiveTvData.Channel c:TvChannelLibrary.recent(LiveTvActivity.this,scope))if(!TvChannelLibrary.key(c).equals(key)){main.post(()->{if(active&&scope.equals(source))play(c);});return;}});}public void started(LiveTvData.Channel c){String scope=source;new TvPersonalization(LiveTvActivity.this,scope).played(TvChannelLibrary.key(c),c.name);io.submit(()->{try{TvChannelLibrary.remember(LiveTvActivity.this,scope,c);}catch(Exception ignored){}});}});root.addView(playback,new FrameLayout.LayoutParams(-1,-1));}
        pickerEpoch++;if(pickerJob!=null)pickerJob.cancel(true);playback.channels(pageChannels,guide,categoryName(name(groups,group)),page,total);playback.select(ch);playback.favorites(favorites);updateSelection();playback.resume();
    }
    private void choosePlayerChannel(LiveTvData.Channel channel){if(playback==null)return;group=playback.pickerRecent?TvChannelLibrary.RECENT:playback.pickerFavorites?"@favorites":playerGroup;generation++;guideEpoch++;if(catalogJob!=null)catalogJob.cancel(true);if(guideJob!=null)guideJob.cancel(true);cancelTimeline();loading=false;guideLoading=false;pageChannels=new ArrayList<>(playback.pickerChannels);filtered=pageChannels;guide=new HashMap<>(playback.pickerGuide);page=playback.pickerPage;total=playback.pickerTotal;catalog=new LiveTvData.Catalog(source,pageChannels,true);timelineOffset=Math.max(0,pageChannels.indexOf(channel))/VelaReading.rows(this)*VelaReading.rows(this);play(channel);loadGuide();}
    private String playerGroup="";
    private void loadPlayerChannels(int requested){loadPlayerChannels(requested,playback!=null&&playback.pickerFavorites);}
    private void loadPlayerChannels(int requested,boolean onlyFavorites){
        if(!active||closed||playback==null)return;if(pickerJob!=null)pickerJob.cancel(true);int token=++pickerEpoch;LiveTvPlayer target=playback;String wantedSource=source,wantedMode=mode,wantedGroup=playback.pickerRecent?TvChannelLibrary.RECENT:onlyFavorites?"@favorites":playerGroup,wantedQuery="",label=categoryName(name(groups,onlyFavorites?"@favorites":playerGroup));
        pickerJob=io.submit(()->{try{TvCatalogCache.Page result=TvPlayerCatalog.page(this,wantedMode,wantedGroup,label,wantedQuery,requested);main.post(()->{if(active&&!closed&&token==pickerEpoch&&playback==target&&wantedSource.equals(LiveTvData.source(this)))target.channels(result.channels,Collections.emptyMap(),label,result.page,result.total);});Map<String,LiveTvData.Guide> programmes=TvPlayerCatalog.guide(this,wantedMode,result.channels);main.post(()->{if(active&&!closed&&token==pickerEpoch&&playback==target&&wantedSource.equals(LiveTvData.source(this)))target.channelGuide(programmes);});}catch(Exception unavailable){main.post(()->{if(active&&!closed&&token==pickerEpoch&&playback==target)target.channelError();});}});
    }
    private void changeChannel(int direction){if(selected==null||filtered.isEmpty()||loading)return;int target=filtered.indexOf(selected)+direction;if(target<0||target>=filtered.size()){int requested=page+direction;if(requested>=0&&requested*12<total){loadPage(requested,false,direction,false);return;}target=direction>0?0:filtered.size()-1;}play(filtered.get(target));}
    void browse(){pickerEpoch++;if(pickerJob!=null)pickerJob.cancel(true);if(playback!=null){LiveTvPlayer old=playback;playback=null;old.pause();root.removeView(old);}stage.setVisibility(View.VISIBLE);if(active){header.start();renderCards(page,true);}}
    private void openDialog(String title,String description){clearUpcoming();dialog.setBackgroundColor(0xFF101824);if(playback!=null)browse();dialog.removeAllViews();dialog.setVisibility(View.VISIBLE);header.stop();for(int i=0;i<stage.getChildCount();i++)if(stage.getChildAt(i)!=dialog)stage.getChildAt(i).setVisibility(View.INVISIBLE);TextView heading=text(title,45,WHITE,Ui.displaySemibold);put(dialog,heading,380,134,1160,86);TextView detail=text(description,24,DIM,Ui.regular);detail.setMaxLines(3);detail.setGravity(Gravity.TOP);put(dialog,detail,380,242,1160,132);}
    private void closeDialog(){clearUpcoming();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);if(privateUrl!=null)privateUrl.setText("");if(privateEpg!=null)privateEpg.setText("");privateUrl=null;privateEpg=null;dialog.setVisibility(View.GONE);dialog.removeAllViews();for(int i=0;i<stage.getChildCount();i++)if(stage.getChildAt(i)!=dialog)stage.getChildAt(i).setVisibility(View.VISIBLE);updateViewMode();empty.setVisibility(catalog==null||pageChannels.isEmpty()?View.VISIBLE:View.GONE);if(active&&playback==null)header.start();}
    private TextView dialogAction(String label,int y,Runnable action,boolean focus){TextView b=button(label);put(dialog,b,380,y,1160,76);b.setOnClickListener(v->action.run());if(focus)b.requestFocus();return b;}
    private void showSources(){openDialog(t("Sua televisão.","Your television.","Tu televisión."),t("Listas, conta e programação.","Playlists, account and programme guide.","Listas, cuenta y programación."));dialogAction(t("Configurar lista M3U","Configure M3U playlist","Configurar lista M3U"),366,this::showM3u,true);dialogAction(t("Conta Xtream","Xtream account","Cuenta Xtream"),458,this::openAccount,false);dialogAction(t("Guia de programação","Programme guide","Guía de programación"),550,this::showEpg,false);dialogAction(t("Ajustes de antena","Antenna settings","Ajustes de antena"),642,this::antennaSettings,false);dialogAction(t("Atualizar canais","Refresh channels","Actualizar canales"),734,()->{closeDialog();load(true);},false);dialogAction(t("Voltar aos canais","Back to channels","Volver a los canales"),854,()->{closeDialog();sourceButton.requestFocus();},false);}
    private void showEpg(){openDialog(t("Sua programação","Your programme guide","Tu programación"),t("EPG dobleM ou seu XMLTV HTTPS. A guia da fonte completa os canais sem correspondência.","EPG dobleM or your HTTPS XMLTV. The source guide fills unmatched channels.","EPG dobleM o tu XMLTV HTTPS. La guía de la fuente completa los canales sin coincidencia."));getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);privateEpg=input("XMLTV · HTTPS",384);try{privateEpg.setText(new XmlTvGuide(this).url());}catch(Exception ignored){}dialogAction(t("Guardar guia","Save guide","Guardar guía"),514,()->{try{new XmlTvGuide(this).configure(privateEpg.getText().toString());closeDialog();reloadGuide();}catch(Exception invalid){Toast.makeText(this,"XMLTV · HTTPS",Toast.LENGTH_LONG).show();}},false);dialogAction(t("Atualizar guia","Refresh guide","Actualizar guía"),606,()->{new XmlTvGuide(this).invalidate();closeDialog();reloadGuide();},false);dialogAction("EPG dobleM",698,()->{try{new XmlTvGuide(this).configure(XmlTvGuide.DEFAULT_URL);closeDialog();reloadGuide();}catch(Exception ignored){}},false);dialogAction(t("Usar apenas a guia da fonte","Use only the source guide","Usar solo la guía de la fuente"),790,()->{try{new XmlTvGuide(this).configure("");closeDialog();reloadGuide();}catch(Exception ignored){}},false);dialogAction(t("Voltar","Back","Volver"),900,this::showSources,true);}
    private void showSourcePicker(){openDialog(t("Escolha sua fonte","Choose your source","Elige tu fuente"),t("Suas listas e os canais da TV, no mesmo lugar.","Your playlists and TV channels, together.","Tus listas y los canales de la TV, en el mismo lugar."));int y=408;for(TextView choice:sourceTabs.values()){if(choice.getParent()!=null)((ViewGroup)choice.getParent()).removeView(choice);put(dialog,choice,380,y,1160,76);y+=108;}sourceTabs.get(mode).requestFocus();}
    private void showChannelActions(LiveTvData.Channel ch){openDialog(displayName(ch.name),t("Escolha como assistir ou guarde nos favoritos.","Choose how to watch or save a favorite.","Elige cómo verlo o guárdalo en favoritos."));dialogAction(TvChannelNames.favorite(ch,favorites)?t("Remover dos favoritos","Remove favorite","Quitar de favoritos"):t("Adicionar aos favoritos","Add favorite","Añadir a favoritos"),408,()->{closeDialog();toggleFavorite(ch);},true);if(TvChannelNames.variants(ch).size()>1)dialogAction(t("Qualidade","Quality","Calidad")+" · "+(quality(ch.name).isEmpty()?"Auto":quality(ch.name)),516,()->showQualities(ch),false);dialogAction(t("Voltar aos canais","Back to channels","Volver a los canales"),784,()->{closeDialog();renderCards(page,true);},false);}
    private void showQualities(LiveTvData.Channel ch){openDialog(displayName(ch.name),t("A escolha fica guardada para este canal.","Your choice is remembered for this channel.","La elección se guarda para este canal."));int y=366;int i=0;Map<String,Integer> qualityCounts=new HashMap<>();for(LiveTvData.Channel variant:TvChannelNames.variants(ch)){String label=quality(variant.name);if(label.isEmpty())label=Ui.tr("Sinal","Signal","Señal")+" "+(++i);if(variant.name.toUpperCase(Locale.ROOT).contains("(ZAP)"))label+=" · ZAP";else if(variant.name.toUpperCase(Locale.ROOT).contains("(AV)"))label+=" · AV";else if(variant.name.toUpperCase(Locale.ROOT).contains("(VDF)"))label+=" · VDF";int number=qualityCounts.getOrDefault(label,0)+1;qualityCounts.put(label,number);if(number>1)label+=" · "+t("Sinal","Signal","Señal")+" "+number;TextView button=button((variant.id.equals(ch.id)?"✓  ":"")+label);put(dialog,button,380,y,1160,64);button.setOnClickListener(v->{TvChannelNames.prefer(this,source,variant);closeDialog();renderPage(page,true);});if(variant.id.equals(ch.id))button.requestFocus();y+=76;}}
    private void openAccount(){closeDialog();startActivity(new Intent(this,XtreamLinkActivity.class));}
    private void antennaSettings(){Intent setup=antenna.setup();if(setup==null){Toast.makeText(this,t("O sintonizador não oferece ajustes.","The tuner did not provide a setup screen.","El sintonizador no ofrece una pantalla de ajustes."),Toast.LENGTH_LONG).show();return;}try{startActivity(setup);}catch(RuntimeException unavailable){Toast.makeText(this,t("Não foi possível abrir os ajustes.","Could not open antenna settings.","No se han podido abrir los ajustes de antena."),Toast.LENGTH_LONG).show();}}
    private EditText input(String hint,int y){EditText field=new EditText(this);field.setId(View.generateViewId());field.setHint(hint);field.setSaveEnabled(false);field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_ACTION_NEXT);field.setSingleLine();field.setTextColor(WHITE);field.setHintTextColor(DIM);field.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);field.setPadding(24,0,24,0);field.setBackground(round(0xFF253346,14));field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8192)});put(dialog,field,380,y,1160,80);return field;}
    private void showM3u(){
        openDialog(t("Sua lista M3U","Your M3U playlist","Tu lista M3U"),t("Seus canais e sua programação. A conexão fica guardada cifrada nesta TV.","Your channels and listings. Your connection is saved encrypted on this TV.","Tus canales y tu programación. La conexión se guarda cifrada en esta TV."));getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);privateUrl=input(t("URL da lista","Playlist URL","URL de la lista"),384);privateEpg=input(t("XMLTV opcional","Optional XMLTV","XMLTV opcional"),494);
        try{privateUrl.setText(m3u.url());privateEpg.setText(m3u.epgUrl());}catch(Exception failure){}
        boolean[] http={m3u.allowHttp()};TextView allow=dialogAction((http[0]?"✓ ":"○ ")+t("Minha lista ou sinais usam HTTP","My playlist or streams use HTTP","Mi lista o sus señales usan HTTP"),606,()->{},false);allow.setOnClickListener(v->{http[0]=!http[0];allow.setText((http[0]?"✓ ":"○ ")+t("Minha lista ou sinais usam HTTP","My playlist or streams use HTTP","Mi lista o sus señales usan HTTP"));});
        dialogAction(t("Guardar e usar M3U","Save and use M3U","Guardar y usar M3U"),714,()->{try{m3u.configure(privateUrl.getText().toString(),privateEpg.getText().toString(),http[0]);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(dialog.getWindowToken(),0);closeDialog();load(true);}catch(Exception bad){Toast.makeText(this,t("Confira as URLs. Para HTTP, ative a opção indicada.","Check the URLs. For HTTP, enable the indicated option.","Revisa las URL. Para HTTP, activa la opción indicada."),Toast.LENGTH_LONG).show();}},false);
        dialogAction(t("Voltar","Back","Volver"),822,this::showSources,true);
    }
    private void showSearch(){openDialog(t("Encontre seu canal","Find your channel","Encuentra tu canal"),t("Pesquise pelo nome. Não é necessário carregar os logotipos do catálogo inteiro.","Search by name and jump straight to your channel.","Busca por nombre y encuentra tu canal al instante."));EditText search=input(t("Nome do canal","Channel name","Nombre del canal"),408);search.setText(query);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);dialogAction(t("Buscar canais","Search channels","Buscar canales"),536,()->{query=search.getText().toString().trim().substring(0,Math.min(160,search.getText().toString().trim().length()));group=ALL;page=0;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);closeDialog();tabs();renderPage(0,true);},false);dialogAction(t("Limpar busca","Clear search","Quitar búsqueda"),644,()->{query="";closeDialog();tabs();renderPage(0,true);},false);dialogAction(t("Voltar","Back","Volver"),784,()->{closeDialog();searchButton.requestFocus();},true);}
    @Override protected void onResume(){super.onResume();active=true;if(timelineMode!=LiveTvData.guideView(this))firstPage=true;timelineMode=LiveTvData.guideView(this);if(playback!=null)playback.resume();else{header.start();load(false);}main.removeCallbacks(tick);tick.run();if(getIntent().getBooleanExtra("sources",false)){getIntent().removeExtra("sources");showSources();}resumeAutoTune();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    @Override protected void onPause(){savePosition();active=false;if(autoTune!=null)autoTune.pause();if(upcomingCard!=null)closeDialog();pickerEpoch++;if(pickerJob!=null)pickerJob.cancel(true);cancelTimeline();generation++;if(catalogJob!=null)catalogJob.cancel(true);if(guideJob!=null)guideJob.cancel(true);loading=false;guideLoading=false;main.removeCallbacks(tick);header.stop();clearLogos();if(privateUrl!=null)privateUrl.setText("");if(privateEpg!=null)privateEpg.setText("");if(playback!=null)playback.pause();super.onPause();}
    @Override protected void onDestroy(){closed=true;cancelTimeline();clearLogos();if(playback!=null)playback.pause();io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);clearLogos();}
    private void clearLogos(){if(logos!=null){logos.close();logos=null;}for(ImageView image:images)image.setImageDrawable(null);}
    /** Drawn only when the existing minute tick changes the value or remote focus changes. */
    private static final class ProgrammeProgress extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private int progress;
        private boolean focused;
        ProgrammeProgress(Context context){super(context);accent=Appearance.accent(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void value(int value){if(progress==value)return;progress=value;invalidate();}
        void focused(boolean value){if(focused==value)return;focused=value;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            float radius=getHeight()/2f;paint.setColor(focused?0xFFD3DAE4:0xFF40516A);canvas.drawRoundRect(0,0,getWidth(),getHeight(),radius,radius,paint);
            if(progress>0){paint.setColor(accent);canvas.drawRoundRect(0,0,getWidth()*progress/1000f,getHeight(),radius,radius,paint);}
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();if(playback!=null&&playback.pairedKey(key)){if(event.getAction()==KeyEvent.ACTION_UP&&consumeUp==key)consumeUp=-1;if(playback.key(event))return true;}if(event.getAction()==KeyEvent.ACTION_UP&&consumeUp==key){consumeUp=-1;return true;}if(event.getAction()==KeyEvent.ACTION_UP&&longOk&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){longOk=false;return true;}if(dialog.getVisibility()==View.VISIBLE){if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){closeDialog();if(timelineMode)timeline.requestFocus();else sourceButton.requestFocus();}return true;}}else if(playback!=null&&playback.key(event)){if(event.getAction()==KeyEvent.ACTION_DOWN)consumeUp=key;return true;}else if(playback==null&&event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_SEARCH){showSearch();consumeUp=key;return true;}if(key==KeyEvent.KEYCODE_MENU){showSources();consumeUp=key;return true;}if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){if(timelineMode)timelineChannels(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1,false);else renderPage(page+(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1),true);consumeUp=key;return true;}}return super.dispatchKeyEvent(event);}
}
