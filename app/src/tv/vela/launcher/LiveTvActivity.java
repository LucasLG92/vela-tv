package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Native channel browser. Only twelve cards exist at a time; video starts after an explicit selection. */
public class LiveTvActivity extends Activity {
    private static final int WHITE=0xFFF6F7FB,DIM=0xFFA6B3C8,INK=0xFF121A26;
    private static final String ALL="@all",FAVORITES="@favorites";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Visuals.worker(2);
    private FrameLayout root,stage,grid,groupList,dialog;
    private final List<View> cards=new ArrayList<>(),categoryButtons=new ArrayList<>();
    private final List<ImageView> images=new ArrayList<>();private final List<TextView> nowLabels=new ArrayList<>();
    private final List<TextView> remainingLabels=new ArrayList<>();
    private final List<ProgrammeProgress> programmeBars=new ArrayList<>();
    private TextView status,selection,nowNext,previous,next,pageLabel,sourceButton;
    private HomeStatusView header;
    LiveTvData.Catalog catalog;
    private Map<String,LiveTvData.Guide> guide=Collections.emptyMap();
    private List<LiveTvData.Channel> filtered=Collections.emptyList(),pageChannels=Collections.emptyList();
    LiveTvData.Channel selected;
    private Set<String> favorites=Collections.emptySet();
    private String group=ALL;
    private int page,generation,consumeUp=-1;
    private long guideAt;
    private boolean active,closed,loading,guideLoading;
    private boolean firstPage=true;
    private Future<?> catalogJob,guideJob;
    private LiveTvLogos logos;
    LiveTvPlayer playback;
    private boolean longOk;
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    private final Runnable tick=new Runnable(){public void run(){if(!active)return;updateProgrammes();if(catalog!=null&&SystemClock.elapsedRealtime()-guideAt>5*60_000&&!guideLoading)loadGuide();main.postDelayed(this,60_000-System.currentTimeMillis()%60_000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);Ui.init(this);
        root=new FrameLayout(this){@Override protected void onSizeChanged(int w,int h,int ow,int oh){if(stage==null)return;float s=Math.min(w/1920f,h/1080f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(s);stage.setScaleY(s);stage.setTranslationX((w-1920*s)/2);stage.setTranslationY((h-1080*s)/2);}};
        root.setBackgroundColor(0xFF0E1420);stage=new FrameLayout(this);stage.setClipChildren(false);root.addView(stage,new FrameLayout.LayoutParams(1920,1080));
        GradientDrawable glow=new GradientDrawable();glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);glow.setGradientCenter(.14f,.28f);glow.setGradientRadius(1400);glow.setColors(new int[]{0xFF273C51,0xFF142030,0xFF0E1420});stage.setBackground(glow);
        TextView brand=text("VELA",19,0xFF99AFC8,Ui.semibold);brand.setLetterSpacing(.18f);put(stage,brand,114,56,500,35);
        TextView title=text(t("Televisão","Television","Televisión"),53,WHITE,Ui.displaySemibold);put(stage,title,110,104,900,75);
        header=new HomeStatusView(this);put(stage,header,1508,64,340,76);
        sourceButton=button(t("Lista e ajustes","Playlist and settings","Lista y ajustes"));put(stage,sourceButton,1140,166,350,64);sourceButton.setOnClickListener(v->showSources());
        TextView refresh=button(t("Atualizar","Refresh","Actualizar"));put(stage,refresh,1512,166,200,64);refresh.setOnClickListener(v->load(true));
        TextView back=button("‹");back.setContentDescription(t("Voltar ao início","Back to home","Volver al inicio"));put(stage,back,1734,166,110,64);back.setOnClickListener(v->finish());
        ScrollView groups=new ScrollView(this);groups.setVerticalScrollBarEnabled(false);groups.setClipToPadding(true);groups.setClipChildren(true);groups.setClipBounds(new Rect(0,0,292,625));put(stage,groups,104,258,292,625);groupList=new FrameLayout(this);groups.addView(groupList,new ScrollView.LayoutParams(-1,1));
        grid=new FrameLayout(this);grid.setClipChildren(false);put(stage,grid,470,258,1380,612);
        previous=button("‹ "+t("Anterior","Previous","Anterior"));put(stage,previous,470,882,220,54);previous.setOnClickListener(v->renderPage(page-1,true));
        pageLabel=text("",18,DIM,Ui.medium);pageLabel.setGravity(Gravity.CENTER);put(stage,pageLabel,712,882,870,54);
        next=button(t("Seguinte","Next","Siguiente")+" ›");put(stage,next,1604,882,240,54);next.setOnClickListener(v->renderPage(page+1,true));
        selection=text("",28,WHITE,Ui.displaySemibold);selection.setSingleLine(true);selection.setEllipsize(android.text.TextUtils.TruncateAt.END);put(stage,selection,470,956,1374,42);
        nowNext=text("",20,0xFFBDCADC,Ui.regular);nowNext.setSingleLine(true);nowNext.setEllipsize(android.text.TextUtils.TruncateAt.END);put(stage,nowNext,470,1006,1374,38);
        status=text(t("Carregando canais…","Loading channels…","Cargando canales…"),19,DIM,Ui.regular);status.setGravity(Gravity.TOP);status.setMaxLines(4);put(stage,status,114,912,280,140);
        dialog=new FrameLayout(this);dialog.setBackgroundColor(0xF20C121E);dialog.setVisibility(View.GONE);put(stage,dialog,0,0,1920,1080);
        setContentView(root);getWindow().setBackgroundDrawable(null);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private TextView text(String value,int size,int color,Typeface font){TextView v=new TextView(this);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(font);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView button(String label){TextView b=text(label,22,WHITE,Ui.medium);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setGravity(Gravity.CENTER);b.setPadding(18,0,18,0);b.setId(View.generateViewId());b.setFocusable(true);b.setClickable(true);b.setDefaultFocusHighlightEnabled(false);b.setContentDescription(label);b.setBackground(round(0xFF253145,16));b.setOnFocusChangeListener((v,f)->{b.setBackground(round(f?WHITE:0xFF253145,16));b.setTextColor(f?INK:WHITE);});return b;}
    private void load(boolean force){
        if(!active||loading)return;loading=true;int epoch=++generation;if(guideJob!=null)guideJob.cancel(true);guideLoading=false;guide=Collections.emptyMap();status.setText(t("Carregando canais…","Loading channels…","Cargando canales…"));
        catalogJob=io.submit(()->{try{LiveTvData.Catalog fresh=LiveTvData.catalog(this,force);LiveTvLogos.prune(this);main.post(()->{
            if(!active||closed||epoch!=generation)return;loading=false;catalog=fresh;favorites=LiveTvData.favorites(this,catalog.source);
            if(selected==null&&group.equals(ALL)){String last=LiveTvData.prefs(this).getString("last-"+LiveTvData.hash(catalog.source),"");for(int i=0;i<catalog.channels.size();i++)if(catalog.channels.get(i).id.equals(last)){selected=catalog.channels.get(i);page=i/LiveTvData.PAGE_SIZE;break;}}
            renderGroups();renderPage(page,false);if(firstPage&&dialog.getVisibility()!=View.VISIBLE&&!cards.isEmpty())cards.get(Math.max(0,pageChannels.indexOf(selected))).requestFocus();firstPage=false;loadGuide();
        });}catch(Exception e){main.post(()->{if(active&&!closed&&epoch==generation){loading=false;status.setText(t("Não foi possível obter a lista. Verifique a Internet ou a URL e tente Atualizar.","Could not load the list. Check your Internet connection or URL and select Refresh.","No se ha podido obtener la lista. Comprueba Internet o la URL y pulsa Actualizar."));if(catalog==null&&dialog.getVisibility()!=View.VISIBLE)sourceButton.requestFocus();}});}});
    }
    private void loadGuide(){
        if(!active||catalog==null||guideLoading)return;guideLoading=true;final int epoch=generation;LiveTvData.Catalog snapshot=catalog;
        guideJob=io.submit(()->{Map<String,LiveTvData.Guide> fresh;try{fresh=LiveTvData.guide(this,snapshot);}catch(Exception e){fresh=Collections.emptyMap();}Map<String,LiveTvData.Guide> result=fresh;main.post(()->{if(!active||closed||epoch!=generation)return;guideLoading=false;guideAt=SystemClock.elapsedRealtime();guide=result;updateProgrammes();});});
    }
    private void renderGroups(){
        groupList.removeAllViews();categoryButtons.clear();List<String> names=new ArrayList<>(Arrays.asList(ALL,FAVORITES));for(LiveTvData.Channel ch:catalog.channels)if(!names.contains(ch.group))names.add(ch.group);
        if(!names.contains(group))group=ALL;
        groupList.setLayoutParams(new ScrollView.LayoutParams(-1,names.size()*64));
        for(int i=0;i<names.size();i++){
            String name=names.get(i);TextView b=button(name.equals(ALL)?t("Todos os canais","All channels","Todos los canales"):name.equals(FAVORITES)?"★ "+t("Favoritos","Favorites","Favoritos"):name);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,21);b.setTag(name);put(groupList,b,0,i*64,284,56);categoryButtons.add(b);
            b.setOnClickListener(v->{group=name;page=0;renderPage(0,false);styleCategories();});
            b.setOnFocusChangeListener((v,f)->{b.setBackground(f?round(WHITE,14):name.equals(group)?round(0xFF2D4058,14):null);b.setTextColor(f?INK:WHITE);});
        }styleCategories();
    }
    private void styleCategories(){for(View v:categoryButtons)if(!v.hasFocus())v.setBackground(group.equals(v.getTag())?round(0xFF2D4058,14):null);}
    private void renderPage(int requested,boolean focus){
        if(catalog==null)return;if(logos==null)logos=new LiveTvLogos(this);logos.page();for(ImageView image:images)image.setImageDrawable(null);images.clear();nowLabels.clear();remainingLabels.clear();programmeBars.clear();cards.clear();grid.removeAllViews();
        List<LiveTvData.Channel> matches=new ArrayList<>();for(LiveTvData.Channel ch:catalog.channels)if(group.equals(ALL)||group.equals(FAVORITES)&&favorites.contains(ch.id)||group.equals(ch.group))matches.add(ch);filtered=matches;
        page=Math.max(0,Math.min(requested,Math.max(0,(matches.size()-1)/LiveTvData.PAGE_SIZE)));int start=page*LiveTvData.PAGE_SIZE,end=Math.min(matches.size(),start+LiveTvData.PAGE_SIZE);pageChannels=new ArrayList<>(matches.subList(start,end));
        for(int i=0;i<pageChannels.size();i++){
            LiveTvData.Channel ch=pageChannels.get(i);FrameLayout card=new FrameLayout(this);card.setId(View.generateViewId());card.setFocusable(true);card.setClickable(true);card.setDefaultFocusHighlightEnabled(false);card.setContentDescription(ch.name+(favorites.contains(ch.id)?" · ★":""));card.setBackground(round(0xFF233146,21));put(grid,card,(i%4)*350,(i/4)*207,326,188);cards.add(card);
            TextView fallback=text(ch.name.substring(0,Math.min(2,ch.name.length())).toUpperCase(Ui.locale()),33,0xFF647991,Ui.semibold);fallback.setGravity(Gravity.CENTER);put(card,fallback,96,12,134,68);
            ImageView logo=new ImageView(this);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);put(card,logo,96,12,134,68);images.add(logo);logos.load(ch.logo,logo);
            if(!ch.playable()){TextView external=text(LiveTvData.webUrl(ch.web)?t("Web integrada","In-app web","Web integrada"):t("Sem sinal","No signal","Sin señal"),14,0xFF91A2BB,Ui.medium);external.setGravity(Gravity.RIGHT);put(card,external,196,16,118,24);}
            TextView name=text(ch.name+(favorites.contains(ch.id)?"  ★":""),23,WHITE,Ui.medium);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);put(card,name,14,87,298,31);
            TextView current=text("",17,DIM,Ui.regular);current.setSingleLine(true);current.setEllipsize(android.text.TextUtils.TruncateAt.END);current.setGravity(Gravity.CENTER);put(card,current,18,121,290,25);nowLabels.add(current);
            TextView remaining=text("",15,DIM,Ui.medium);remaining.setSingleLine(true);remaining.setGravity(Gravity.CENTER);put(card,remaining,18,148,290,24);remainingLabels.add(remaining);
            ProgrammeProgress progress=new ProgrammeProgress(this);put(card,progress,30,177,266,4);programmeBars.add(progress);
            card.setOnFocusChangeListener((v,has)->{card.setBackground(round(has?WHITE:0xFF233146,21));name.setTextColor(has?INK:WHITE);current.setTextColor(has?0xFF5B677B:DIM);remaining.setTextColor(has?0xFF5B677B:DIM);progress.focused(has);if(has){selected=ch;updateSelection();}card.animate().cancel();card.animate().scaleX(has&&!Visuals.reducedMotion(this)?1.025f:1).scaleY(has&&!Visuals.reducedMotion(this)?1.025f:1).setDuration(Visuals.duration(this,120)).start();});
            card.setOnClickListener(v->play(ch));card.setOnLongClickListener(v->{longOk=true;card.setPressed(false);toggleFavorite(ch);return true;});
            final int index=i;card.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&index==cards.size()-1&&end<matches.size()){renderPage(page+1,true);consumeUp=key;return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT&&index==0){if(page>0)renderPage(page-1,true);else if(!categoryButtons.isEmpty())categoryButtons.get(0).requestFocus();consumeUp=key;return true;}return false;});
        }
        if(pageChannels.isEmpty()){TextView empty=text(t("Adicione canais aos favoritos mantendo OK pressionado sobre um canal.","Add favorites by holding OK on a channel.","Añade favoritos manteniendo pulsado OK sobre un canal."),26,DIM,Ui.regular);empty.setGravity(Gravity.CENTER);put(grid,empty,60,140,1200,200);selected=null;}
        else if(selected==null||!pageChannels.contains(selected))selected=pageChannels.get(0);
        previous.setEnabled(page>0);previous.setFocusable(page>0);previous.setAlpha(page>0?1:.35f);next.setEnabled(end<matches.size());next.setFocusable(end<matches.size());next.setAlpha(end<matches.size()?1:.35f);
        pageLabel.setText(matches.isEmpty()?"0":(start+1)+"–"+end+" / "+matches.size());
        status.setText((catalog.source.equals(LiveTvData.CATALOG)?t("Canais e guia: TDTChannels","Channels and guide: TDTChannels","Canales y guía: TDTChannels"):t("Lista pessoal","Personal playlist","Lista personal"))+(catalog.cached?t("\nLista salva","\nSaved list","\nLista guardada"):"")+t("\nOK ver · manter OK ★","\nOK watch · hold OK ★","\nOK ver · mantener OK ★"));
        updateProgrammes();if(dialog.getVisibility()!=View.VISIBLE&&playback==null){if(focus&&!cards.isEmpty())cards.get(0).requestFocus();else if(getCurrentFocus()==null&&!cards.isEmpty())cards.get(0).requestFocus();}
    }
    private String time(long seconds){return new SimpleDateFormat("HH:mm",Ui.locale()).format(new Date(seconds*1000));}
    private String currentText(LiveTvData.Channel ch,LiveTvData.Programme current){return current!=null?current.title:ch.playable()?t("Em direto","Live","En directo"):LiveTvData.webUrl(ch.web)?t("Reprodutor oficial em Vela","Official player in Vela","Reproductor oficial en Vela"):t("Sinal indisponível","Signal unavailable","Señal no disponible");}
    private static String remainingText(long minutes){return minutes==1?t("Falta 1 min","1 min left","Queda 1 min"):t("Faltam "+minutes+" min",minutes+" min left","Quedan "+minutes+" min");}
    private void updateProgrammes(){updateProgrammes(System.currentTimeMillis()/1000);}
    private void updateProgrammes(long now){
        for(int i=0;i<pageChannels.size()&&i<nowLabels.size();i++){
            LiveTvData.Channel ch=pageChannels.get(i);LiveTvData.Guide entry=guide.get(ch.id);LiveTvData.Programme current=entry==null?null:entry.current(now);
            String title=currentText(ch,current),remaining=current==null?"":remainingText(current.remainingMinutes(now));
            TextView titleView=nowLabels.get(i),remainingView=remainingLabels.get(i);if(!title.contentEquals(titleView.getText()))titleView.setText(title);if(!remaining.contentEquals(remainingView.getText()))remainingView.setText(remaining);
            int visibility=current==null?View.INVISIBLE:View.VISIBLE;remainingView.setVisibility(visibility);ProgrammeProgress progress=programmeBars.get(i);progress.setVisibility(visibility);progress.value(current==null?0:current.progress(now));
            cards.get(i).setContentDescription(ch.name+(favorites.contains(ch.id)?" · ★":"")+" · "+title+(remaining.isEmpty()?"":" · "+remaining));
        }
        updateSelection(now);
    }
    private void updateSelection(){updateSelection(System.currentTimeMillis()/1000);}
    private void updateSelection(long now){
        if(selected==null){selection.setText("");nowNext.setText("");return;}selection.setText(selected.name+" · "+selected.group);LiveTvData.Guide entry=guide.get(selected.id);LiveTvData.Programme current=entry==null?null:entry.current(now);
        String line=current!=null?time(current.start)+"–"+time(current.end)+"  "+current.title:t("Programação não disponível","Programme guide unavailable","Programación no disponible");
        if(entry!=null&&entry.next!=null&&entry.next.start>now)line+="   ·   "+time(entry.next.start)+"  "+entry.next.title;nowNext.setText(line);
        if(playback!=null)playback.metadata(line,favorites.contains(selected.id));
    }
    private void toggleFavorite(LiveTvData.Channel ch){if(catalog==null)return;LiveTvData.toggle(this,catalog.source,ch.id);favorites=LiveTvData.favorites(this,catalog.source);if(playback!=null)updateSelection();else{int focus=pageChannels.indexOf(ch);renderPage(page,false);if(dialog.getVisibility()!=View.VISIBLE&&!cards.isEmpty())cards.get(Math.max(0,Math.min(focus,cards.size()-1))).requestFocus();}}
    void play(LiveTvData.Channel ch){
        selected=ch;if(!ch.playable()){if(LiveTvData.webUrl(ch.web))openWeb(ch);else showExternal(ch);return;}closeDialog();clearLogos();stage.setVisibility(View.GONE);header.stop();
        if(playback==null){playback=new LiveTvPlayer(this,new LiveTvPlayer.Actions(){public void back(){browse();}public void zap(int direction){changeChannel(direction);}public void favorite(){toggleFavorite(selected);}public void external(){showExternal(selected);}});root.addView(playback,new FrameLayout.LayoutParams(-1,-1));}
        playback.select(ch);updateSelection();playback.resume();
    }
    private void changeChannel(int direction){if(filtered.isEmpty()||selected==null)return;int index=filtered.indexOf(selected);for(int i=0;i<filtered.size();i++){index=Math.floorMod(index+direction,filtered.size());LiveTvData.Channel ch=filtered.get(index);if(ch.playable()&&(!group.equals(FAVORITES)||favorites.contains(ch.id))){play(ch);return;}}}
    void browse(){if(playback!=null){LiveTvPlayer old=playback;playback=null;old.pause();root.removeView(old);}stage.setVisibility(View.VISIBLE);if(active){header.start();int absolute=filtered.indexOf(selected);if(absolute>=0)page=absolute/LiveTvData.PAGE_SIZE;renderPage(page,false);int index=pageChannels.indexOf(selected);if(!cards.isEmpty())cards.get(Math.max(0,index)).requestFocus();}}
    private void openDialog(String title,String description){
        if(playback!=null)browse();dialog.removeAllViews();dialog.setVisibility(View.VISIBLE);stage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);groupList.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        header.stop();for(int i=0;i<stage.getChildCount();i++)if(stage.getChildAt(i)!=dialog)stage.getChildAt(i).setVisibility(View.INVISIBLE);
        TextView heading=text(title,43,WHITE,Ui.displaySemibold);put(dialog,heading,380,160,1160,76);TextView detail=text(description,24,DIM,Ui.regular);detail.setMaxLines(4);detail.setGravity(Gravity.TOP);put(dialog,detail,380,254,1160,170);
    }
    private void closeDialog(){dialog.setVisibility(View.GONE);dialog.removeAllViews();for(int i=0;i<stage.getChildCount();i++)if(stage.getChildAt(i)!=dialog)stage.getChildAt(i).setVisibility(View.VISIBLE);grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);groupList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);if(active&&playback==null)header.start();}
    private void dialogAction(String label,int y,Runnable action,boolean focus){TextView b=button(label);put(dialog,b,380,y,1160,68);b.setOnClickListener(v->action.run());if(focus)b.requestFocus();}
    private void showSources(){
        openDialog(t("Sua televisão, sua lista.","Your TV, your playlist.","Tu televisión, tu lista."),t("O catálogo público inclui canais oficiais em aberto e programação. Você também pode usar sua lista M3U por HTTPS. Favoritos ficam neste dispositivo.","The public catalog includes official free channels and listings. You can also use your own HTTPS M3U playlist. Favorites stay on this device.","El catálogo público incluye canales oficiales en abierto y programación. También puedes usar tu lista M3U por HTTPS. Los favoritos se guardan en este dispositivo."));
        EditText url=new EditText(this);url.setText(LiveTvData.source(this).equals(LiveTvData.CATALOG)?"":LiveTvData.source(this));url.setHint("https://…/lista.m3u");url.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);url.setTextColor(WHITE);url.setHintTextColor(DIM);url.setSingleLine(true);url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setPadding(24,0,24,0);url.setBackground(round(0xFF26374E,15));put(dialog,url,380,430,1160,76);
        dialogAction(t("Usar minha lista","Use my playlist","Usar mi lista"),540,()->{String value=url.getText().toString().trim();if(value.length()>2048||!LiveTvData.https(value)){Toast.makeText(this,t("Digite uma URL HTTPS válida.","Enter a valid HTTPS URL.","Introduce una URL HTTPS válida."),Toast.LENGTH_LONG).show();url.requestFocus();return;}setSource(value);},false);
        dialogAction(t("Usar catálogo público","Use public catalog","Usar catálogo público"),624,()->setSource(""),false);
        int y=708;if(SidebarApps.launchable(this,"com.tv.tdtspain")){dialogAction(t("Abrir TDT España original","Open original TDT España","Abrir TDT España original"),y,()->openPackage("com.tv.tdtspain"),false);y+=84;}
        Intent antenna=HomeApps.liveTv(this);if(antenna!=null){dialogAction(t("TV por antena","Antenna TV","TV por antena"),y,()->launch(antenna),false);y+=84;}
        dialogAction(t("Voltar","Back","Volver"),y+16,()->{closeDialog();if(!cards.isEmpty())cards.get(0).requestFocus();},true);
    }
    private void setSource(String value){if(catalogJob!=null)catalogJob.cancel(true);if(guideJob!=null)guideJob.cancel(true);generation++;loading=false;catalog=null;guide=Collections.emptyMap();selected=null;group=ALL;page=0;LiveTvData.setSource(this,value);closeDialog();load(true);}
    private void showExternal(LiveTvData.Channel ch){
        openDialog(ch.name,t("Você pode usar o reprodutor oficial dentro de Vela. O site pode pedir cookies ou sua conta; a disponibilidade depende da emissora.","You can use the official player inside Vela. The website may request cookies or your account; availability depends on the broadcaster.","Puedes usar el reproductor oficial dentro de Vela. La web puede pedir cookies o tu cuenta; la disponibilidad depende de la emisora."));
        int y=450;if(LiveTvData.webUrl(ch.web)){dialogAction(t("Ver dentro de Vela","Watch inside Vela","Ver dentro de Vela"),y,()->openWeb(ch),true);y+=90;}
        if(SidebarApps.launchable(this,"com.tv.tdtspain")){dialogAction(t("Escolher canal no TDT España","Choose channel in TDT España","Elegir canal en TDT España"),y,()->openPackage("com.tv.tdtspain"),y==450);y+=90;}
        dialogAction(t("Voltar aos canais","Back to channels","Volver a los canales"),y,()->{closeDialog();if(!cards.isEmpty())cards.get(0).requestFocus();},y==450);
    }
    private void openWeb(LiveTvData.Channel ch){
        if(!LiveTvData.webUrl(ch.web))return;
        if(playback!=null)browse();closeDialog();selected=ch;
        Intent intent=new Intent(this,LiveTvWebActivity.class).putExtra("url",ch.web).putExtra("name",ch.name);
        try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,t("Não foi possível abrir o reprodutor.","Could not open the player.","No se ha podido abrir el reproductor."),Toast.LENGTH_LONG).show();}
    }
    private void openPackage(String pkg){Intent intent=HomeApps.launchIntent(getPackageManager(),pkg);if(intent!=null)launch(intent);}
    private void launch(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,t("Nenhum app disponível para abrir este link.","No app is available to open this link.","No hay una app disponible para abrir este enlace."),Toast.LENGTH_LONG).show();}}
    private void clearLogos(){if(logos!=null){logos.close();logos=null;}for(ImageView image:images)image.setImageDrawable(null);}
    @Override protected void onResume(){super.onResume();active=true;if(playback!=null)playback.resume();else{header.start();if(catalog==null||!catalog.source.equals(LiveTvData.source(this)))load(false);else{renderPage(page,false);loadGuide();}}main.removeCallbacks(tick);tick.run();if(getIntent().getBooleanExtra("sources",false)){getIntent().removeExtra("sources");showSources();}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    @Override protected void onPause(){active=false;generation++;if(catalogJob!=null)catalogJob.cancel(true);if(guideJob!=null)guideJob.cancel(true);loading=false;guideLoading=false;main.removeCallbacks(tick);header.stop();clearLogos();if(playback!=null)playback.pause();super.onPause();}
    @Override protected void onStop(){clearLogos();super.onStop();}
    @Override protected void onDestroy(){closed=true;clearLogos();if(playback!=null)playback.pause();io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);clearLogos();}
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
            if(progress>0){paint.setColor(focused?INK:accent);canvas.drawRoundRect(0,0,getWidth()*progress/1000f,getHeight(),radius,radius,paint);}
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(event.getAction()==KeyEvent.ACTION_UP&&consumeUp==key){consumeUp=-1;return true;}
        if(event.getAction()==KeyEvent.ACTION_UP&&longOk&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){longOk=false;return true;}
        if(dialog.getVisibility()==View.VISIBLE){if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){closeDialog();if(!cards.isEmpty())cards.get(0).requestFocus();}return true;}}
        else if(playback!=null&&playback.key(event)){if(event.getAction()==KeyEvent.ACTION_DOWN)consumeUp=key;return true;}
        return super.dispatchKeyEvent(event);
    }
}
