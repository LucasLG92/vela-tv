package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** A cinematic home with a floating sidebar. Only focus transitions animate. */
final class CinemaHomeView extends FrameLayout {
    private static final int BG=0xFF090A0D, WHITE=0xFFF7F7FA, DIM=0xFFB0B2BC;
    private static final android.animation.TimeInterpolator EASE=new android.view.animation.DecelerateInterpolator(1.5f);
    private final Activity activity;
    private final FrameLayout stage,surface,sidebar,overlay;
    private final View scrim,sideTint,backdropShade,backdropLeft,backdropBottom;
    private final ImageView backdrop,titleLogo,sidebarArt,providerMark;
    private final FrameLayout account;
    private final ImageView accountIcon;
    private final TextView accountTitle,accountInitial;
    private final TextView pageTitle,title,meta,summary,rowNote,connection,play,link,serverLabel,reorderHint;
    private final HomeStatusView headerStatus;
    private final TextView libraryChooser,pagePrevious,pageNext,pageStatus;
    private final FrameLayout pager;
    private final ScrollView shelvesScroll;
    private final LinearLayout shelfList;
    private final FrameLayout shortcutMenu;
    private final TextView shortcutTitle,shortcutMove,shortcutRemove;
    private final ScrollView appScroll;
    private final FrameLayout appPanel;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Visuals.worker(1);
    private final PlexClient plex;
    private final CinemaImages images;
    private final ArrayList<FrameLayout> sideButtons=new ArrayList<>();
    private final ArrayList<TextView> sideLabels=new ArrayList<>();
    private final ArrayList<ImageView> sideIcons=new ArrayList<>();
    private final ArrayList<FrameLayout> appButtons=new ArrayList<>();
    private final ArrayList<TextView> appLabels=new ArrayList<>();
    private final ArrayList<ImageView> appIcons=new ArrayList<>();
    private String[] sidePackages;
    private boolean[] availableApps;
    private String[] reorderOriginal;
    private int reorderSlot=-1;
    private boolean confirmHeld,consumeConfirmUp,reorderConfirmArmed;
    private String menuPackage;
    private String[] menuSnapshot;
    private int menuAction;
    private boolean menuConfirmArmed;
    private final ArrayList<View> cards=new ArrayList<>();
    private final ArrayList<ImageView> posterImages=new ArrayList<>();
    private final ArrayList<PlexClient.Film> posterFilms=new ArrayList<>();
    private final ArrayList<CinemaImages.Request> posterRequests=new ArrayList<>();
    private final ArrayList<HorizontalScrollView> posterRails=new ArrayList<>();
    private final ArrayList<Shelf> shelves=new ArrayList<>();
    private List<PlexClient.Section> catalogSections=Collections.emptyList();
    private PlexClient.Section catalogSection;
    private PlexClient.LibraryPage catalogPage;
    private Future<?> catalogWork;
    private int catalogGeneration,requestedOffset;
    private String requestedSection="";
    private boolean catalogLoading,catalogError;
    private CinemaImages.Request heroRequest,logoRequest,avatarRequest;
    private String accountImageKey="",accountRequestKey="";
    private int accountGeneration;
    private Runnable heroLoad;
    private boolean artReleased=true,libraryDirty;
    private final Runnable visiblePosters=()->loadVisiblePosters();
    private final Bitmap[] brandBitmaps=new Bitmap[2];
    private List<PlexClient.Film> films=new ArrayList<>(),youtube=new ArrayList<>();
    private PlexClient.Film selected;
    private View lastContent,overlayReturn;
    private Bitmap heroBitmap;
    private boolean active,loading,closed,sidebarOpen;
    private int filter,imageGeneration,posterGeneration;
    private long refreshedAt;
    private String displayedServer="";
    private String heroKey="";
    private String appearanceKey="";
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!active)return;
        headerStatus.updateClock();
        if(plex.connected() && SystemClock.elapsedRealtime()-refreshedAt>300_000)refresh();
        main.postDelayed(this,60_000-System.currentTimeMillis()%60_000);
    }};

    CinemaHomeView(Activity activity){
        super(activity);this.activity=activity;plex=new PlexClient(activity);images=new CinemaImages(activity,plex);
        setBackgroundColor(BG);setClipChildren(false);
        stage=new FrameLayout(activity);stage.setClipChildren(false);addView(stage,new LayoutParams(1920,1080));
        surface=new FrameLayout(activity);surface.setClipChildren(false);stage.addView(surface,new LayoutParams(1920,1080));
        backdrop=new ImageView(activity);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setCropToPadding(true);place(backdrop,96,0,1824,970);
        backdropLeft=new View(activity);backdropLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xF5090A0D,0x9C090A0D,0x00090A0D}));place(backdropLeft,96,0,1330,970);
        backdropBottom=new View(activity);backdropBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00090A0D,0xB8090A0D,BG}));place(backdropBottom,96,535,1824,545);
        backdropShade=new View(activity);backdropShade.setBackgroundColor(BG);place(backdropShade,96,0,1824,1080);
        View top=new View(activity);top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x85090A0D,0x00090A0D}));place(top,96,0,1824,180);
        pageTitle=text("Inicio",32,WHITE,Ui.displaySemibold);place(pageTitle,190,56,800,48);
        headerStatus=new HomeStatusView(activity);place(headerStatus,1500,42,340,72);
        providerMark=new ImageView(activity);providerMark.setScaleType(ImageView.ScaleType.FIT_START);providerMark.setImageDrawable(brand(0));place(providerMark,194,250,94,43);
        connection=text("Tu biblioteca",17,0xFFD2D2DC,Ui.medium);place(connection,306,252,690,38);
        title=text("Tu cine empieza aquí.",65,WHITE,Ui.displaySemibold);title.setMaxLines(2);title.setLineSpacing(0,.98f);place(title,190,311,800,140);
        titleLogo=new ImageView(activity);titleLogo.setScaleType(ImageView.ScaleType.FIT_START);titleLogo.setVisibility(GONE);place(titleLogo,194,307,650,146);
        meta=text("Plex · YouTube · Prime Video",19,0xFFD1D1DA,Ui.medium);place(meta,194,463,800,34);
        summary=text("Vincula Plex para descubrir tu biblioteca.",21,0xFFE1E1E6,Ui.regular);summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setLineSpacing(5,1);place(summary,194,504,720,70);
        play=button("Abrir Plex",21);Drawable arrow=symbol("play",22);if(arrow!=null)arrow.setTint(BG);play.setCompoundDrawables(arrow,null,null,null);play.setCompoundDrawablePadding(8);place(play,194,600,256,60);
        play.setTextColor(BG);play.setBackground(round(Appearance.accent(activity),30));play.setOnFocusChangeListener((v,f)->{if(f){lastContent=v;setSidebar(false);}play.setBackground(round(Appearance.accent(activity),30));lift(v,f?1.035f:1,f?-2:0,f?16:0);});play.setOnClickListener(v->openFilm());
        link=button("⋯",29);link.setPadding(0,0,0,0);link.setSingleLine(true);link.setContentDescription("Opciones de Plex");place(link,468,600,60,60);link.setBackground(round(0x50FFFFFF,30));
        link.setOnFocusChangeListener((v,f)->{if(f){lastContent=v;setSidebar(false);}link.setTextColor(f?BG:WHITE);link.setBackground(round(f?WHITE:0x50FFFFFF,30));lift(v,f?1.06f:1,0,f?14:0);});
        link.setOnClickListener(v->{if(filter==3)enableYouTube();else activity.startActivity(new Intent(activity,PlexLinkActivity.class));});
        rowNote=text("Tus carteles aparecerán al vincular Plex.",20,DIM,Ui.regular);place(rowNote,194,805,1420,60);
        shelvesScroll=new ScrollView(activity);shelvesScroll.setFocusable(false);shelvesScroll.setVerticalScrollBarEnabled(false);shelvesScroll.setSmoothScrollingEnabled(false);shelvesScroll.setClipToPadding(true);
        shelfList=new LinearLayout(activity);shelfList.setOrientation(LinearLayout.VERTICAL);shelvesScroll.addView(shelfList,new ScrollView.LayoutParams(-1,-2));place(shelvesScroll,182,724,1658,356);
        shelvesScroll.getViewTreeObserver().addOnScrollChangedListener(()->schedulePosters());
        pager=new FrameLayout(activity);place(pager,194,680,1646,52);
        libraryChooser=button("",21);libraryChooser.setSingleLine(true);libraryChooser.setEllipsize(android.text.TextUtils.TruncateAt.END);pager.addView(libraryChooser,new LayoutParams(560,48));libraryChooser.setOnClickListener(v->showLibrarySections());
        pageStatus=text("",20,DIM,Ui.medium);pageStatus.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LayoutParams ps=new LayoutParams(650,48);ps.leftMargin=574;pager.addView(pageStatus,ps);
        pagePrevious=button(Ui.tr("‹ Anterior","‹ Previous","‹ Anterior"),20);LayoutParams pp=new LayoutParams(172,48);pp.leftMargin=1250;pager.addView(pagePrevious,pp);pagePrevious.setOnClickListener(v->{if(!catalogLoading&&catalogPage!=null&&catalogPage.offset>0)loadCatalog(catalogSection.key,Math.max(0,catalogPage.offset-PlexClient.PAGE_SIZE),true);});
        pageNext=button(Ui.tr("Seguinte ›","Next ›","Siguiente ›"),20);LayoutParams pn=new LayoutParams(202,48);pn.leftMargin=1440;pager.addView(pageNext,pn);pageNext.setOnClickListener(v->{if(catalogLoading)return;if(catalogError)loadCatalog(requestedSection,requestedOffset,true);else if(catalogPage!=null&&catalogPage.hasNext())loadCatalog(catalogSection.key,catalogPage.nextOffset(),true);});
        scrim=new View(activity);scrim.setBackgroundColor(Color.BLACK);scrim.setAlpha(0);stage.addView(scrim,new LayoutParams(1920,1080));
        sidebar=new FrameLayout(activity);sidebar.setClipToOutline(true);sidebar.setBackground(round(0xDD1D1E25,32));sidebar.setElevation(16);
        LayoutParams panel=new LayoutParams(76,1032);panel.leftMargin=24;panel.topMargin=24;stage.addView(sidebar,panel);
        sidebarArt=new ImageView(activity);sidebarArt.setScaleType(ImageView.ScaleType.CENTER_CROP);sidebarArt.setCropToPadding(true);sidebarArt.setAlpha(.62f);Visuals.sidebarBlur(sidebarArt);sidebarArt.setVisibility(GONE);sidebar.addView(sidebarArt,new LayoutParams(-1,-1));
        sideTint=new View(activity);sideTint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x8C22242C,0xC8111319}));sidebar.addView(sideTint,new LayoutParams(-1,-1));
        serverLabel=text("",16,0xFFB8B9C4,Ui.medium);serverLabel.setAlpha(0);LayoutParams serverPos=new LayoutParams(190,28);serverPos.leftMargin=82;serverPos.topMargin=70;sidebar.addView(serverLabel,serverPos);
        String[] names={"Inicio","Películas","Series","Biblioteca","Todas las apps","Entradas HDMI","TV en directo","Ajustes"};
        String[] icons={"home","play","tv","apps","apps","hdmi","live_tv","settings"};
        int[] y={138,204,270,336,756,822,888,954};
        for(int i=0;i<names.length;i++){
            final int index=i;FrameLayout nav=new FrameLayout(activity);focusable(nav,names[i]);
            LayoutParams p=new LayoutParams(60,56);p.leftMargin=8;p.topMargin=y[i];sidebar.addView(nav,p);sideButtons.add(nav);
            ImageView icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(i==1)icon.setImageResource(R.drawable.ic_movie);
            else if(i==3)icon.setImageResource(R.drawable.ic_video_library);
            else icon.setImageDrawable(symbol(icons[i],27));
            LayoutParams ip=new LayoutParams(27,30,Gravity.CENTER_VERTICAL);ip.leftMargin=16;nav.addView(icon,ip);sideIcons.add(icon);
            TextView label=text(names[i],21,WHITE,Ui.medium);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setAlpha(0);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams lp=new LayoutParams(206,56);lp.leftMargin=64;nav.addView(label,lp);sideLabels.add(label);
            nav.setOnFocusChangeListener((v,f)->{if(f)setSidebar(true);styleSide(index,f);});nav.setOnClickListener(v->activateSide(index));
            nav.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)leaveSidebar();return true;}return false;});styleSide(i,false);
        }
        appScroll=new ScrollView(activity);appScroll.setFocusable(false);appScroll.setVerticalScrollBarEnabled(true);appScroll.setClipToPadding(true);appScroll.setClipBounds(new Rect(0,0,300,292));appScroll.setFillViewport(true);
        LayoutParams asp=new LayoutParams(-1,292);asp.topMargin=440;sidebar.addView(appScroll,asp);
        appPanel=new FrameLayout(activity);appPanel.setClipChildren(false);appScroll.addView(appPanel,new ScrollView.LayoutParams(-1,288));
        account=new FrameLayout(activity);focusable(account,"");LayoutParams ap=new LayoutParams(284,64);ap.leftMargin=8;ap.topMargin=28;sidebar.addView(account,ap);
        accountIcon=new ImageView(activity);accountIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);accountIcon.setBackground(round(0x33FFFFFF,22));accountIcon.setClipToOutline(true);accountIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams ai=new LayoutParams(42,42,Gravity.CENTER_VERTICAL);ai.leftMargin=9;account.addView(accountIcon,ai);
        accountInitial=text("",21,WHITE,Ui.medium);accountInitial.setGravity(Gravity.CENTER);accountInitial.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);account.addView(accountInitial,new LayoutParams(ai));
        accountTitle=text("",21,WHITE,Ui.displaySemibold);accountTitle.setSingleLine(true);accountTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);accountTitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);accountTitle.setAlpha(0);LayoutParams at=new LayoutParams(206,32);at.leftMargin=74;at.topMargin=1;account.addView(accountTitle,at);
        account.setOnFocusChangeListener((v,f)->{if(f)setSidebar(true);accountTitle.setAlpha(sidebarOpen?1:0);account.setBackground(f?round(0x28FFFFFF,20):null);});account.setOnClickListener(v->activity.startActivity(new Intent(activity,PlexLinkActivity.class)));
        account.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)leaveSidebar();return true;}return false;});
        // The account heading follows the same reveal as the sidebar labels.
        sideLabels.add(accountTitle);
        updateAccount();
        View divider=new View(activity);divider.setBackgroundColor(0x25FFFFFF);LayoutParams dp=new LayoutParams(40,1);dp.leftMargin=18;dp.topMargin=423;sidebar.addView(divider,dp);
        reorderHint=text("",22,WHITE,Ui.medium);reorderHint.setPadding(26,0,26,0);reorderHint.setBackground(round(0xF0243540,24));reorderHint.setVisibility(GONE);reorderHint.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);LayoutParams rh=new LayoutParams(1320,64);rh.leftMargin=360;rh.topMargin=128;stage.addView(reorderHint,rh);
        shortcutMenu=new FrameLayout(activity);shortcutMenu.setBackground(round(0xFA202A36,26));shortcutMenu.setElevation(28);shortcutMenu.setVisibility(GONE);LayoutParams sm=new LayoutParams(940,224);sm.leftMargin=360;sm.topMargin=128;stage.addView(shortcutMenu,sm);
        shortcutTitle=text("",27,WHITE,Ui.displaySemibold);LayoutParams st=new LayoutParams(888,40);st.leftMargin=26;st.topMargin=17;shortcutMenu.addView(shortcutTitle,st);
        shortcutMove=button(Ui.tr("Mover","Move","Mover"),22);shortcutRemove=button(Ui.tr("Remover da barra","Remove from sidebar","Quitar de la barra"),22);
        TextView[] actions={shortcutMove,shortcutRemove};for(int i=0;i<actions.length;i++){
            final int action=i;TextView item=actions[i];LayoutParams p=new LayoutParams(438,62);p.leftMargin=24+i*454;p.topMargin=70;shortcutMenu.addView(item,p);
            item.setOnFocusChangeListener((v,f)->{if(f){menuAction=action;styleShortcutMenu();}});item.setOnClickListener(v->{if(menuPackage!=null){if(action==0)moveFromShortcutMenu();else removeFromShortcutMenu();}});
        }
        TextView controls=text(Ui.tr("←→ Escolher · OK Aceitar · ↑↓ Mover · Voltar Cancelar","←→ Choose · OK Select · ↑↓ Move · Back Cancel","←→ Elegir · OK Aceptar · ↑↓ Mover · Atrás Cancelar"),19,0xFFD1D8E1,Ui.medium);LayoutParams sc=new LayoutParams(888,30);sc.leftMargin=26;sc.topMargin=144;shortcutMenu.addView(controls,sc);
        TextView explanation=text(Ui.tr("Remover o acesso mantém o app instalado.","Removing the shortcut keeps the app installed.","Al quitar el acceso, la aplicación sigue instalada."),18,DIM,Ui.regular);LayoutParams se=new LayoutParams(888,28);se.leftMargin=26;se.topMargin=180;shortcutMenu.addView(explanation,se);
        overlay=new FrameLayout(activity);overlay.setBackgroundColor(0xFD0C0D12);overlay.setElevation(64);overlay.setVisibility(GONE);stage.addView(overlay,new LayoutParams(1920,1080));
        refreshSidebarApps();films=plex.cached();showFilms();play.requestFocus();lastContent=play;
    }

    private TextView text(String s,int size,int color,Typeface face){TextView v=new TextView(activity);v.setText(s);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(face);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void focusable(View v,String description){v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);v.setContentDescription(description);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView button(String s,int size){TextView v=text(s,size,WHITE,Ui.medium);focusable(v,s);v.setContentDescription(null);v.setGravity(Gravity.CENTER);v.setPadding(20,6,20,6);v.setBackground(round(0xFF262831,18));v.setOnFocusChangeListener((view,f)->{v.setBackground(round(f?0xFF454852:0xFF262831,18));lift(v,f?1.035f:1,0,f?16:0);});return v;}
    private Drawable symbol(String name,int size){try(java.io.InputStream in=activity.getAssets().open("symbols/"+name+".png")){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)return null;Drawable d=new BitmapDrawable(getResources(),b);d.setBounds(0,0,size,size);d.setTint(0xFFE1E1E8);return d;}catch(java.io.IOException ignored){return null;}}
    private Drawable brand(int index){if(index==0)return activity.getDrawable(R.drawable.logo_plex);int b=index-1;if(brandBitmaps[b]==null)try(java.io.InputStream in=activity.getAssets().open("brands/"+(index==1?"youtube":"prime")+".png")){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;brandBitmaps[b]=BitmapFactory.decodeStream(in,null,o);}catch(java.io.IOException ignored){}return brandBitmaps[b]==null?null:new BitmapDrawable(getResources(),brandBitmaps[b]);}
    private void place(View v,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;surface.addView(v,p);}
    private void lift(View v,float scale,float y,float z){if(Visuals.reducedMotion(activity)){scale=1;y=0;}v.animate().cancel();v.animate().scaleX(scale).scaleY(scale).translationY(y).translationZ(z).setDuration(Visuals.duration(activity,170)).setInterpolator(EASE).start();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){float s=Math.min(w/1920f,h/1080f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(s);stage.setScaleY(s);stage.setTranslationX((w-1920*s)/2);stage.setTranslationY((h-1080*s)/2);}
    private View activeSide(){if(filter!=3)return sideButtons.get(filter==4?3:filter);for(int i=0;i<sidePackages.length;i++)if(SidebarApps.YOUTUBE.equals(sidePackages[i])&&appButtons.get(i).getVisibility()==VISIBLE)return appButtons.get(i);return sideButtons.get(0);}
    private void styleSide(int i,boolean f){boolean selected=filter!=3&&i==(filter==4?3:filter);sideButtons.get(i).setBackground(f?round(Appearance.accent(activity),28):selected?round(Appearance.tint(activity,0x25),28):null);sideLabels.get(i).setTextColor(f?BG:WHITE);sideIcons.get(i).setColorFilter(f?BG:0xFFDDDEE7);}
    private void styleApp(int slot,boolean focused){if(slot<0)return;boolean moving=reorderOriginal!=null&&slot==reorderSlot;boolean context=sidePackages[slot].equals(menuPackage);appButtons.get(slot).setBackground(moving?round(0xF03D737A,28):focused||context?round(Appearance.tint(activity,0x3C),28):filter==3&&SidebarApps.YOUTUBE.equals(sidePackages[slot])?round(Appearance.tint(activity,0x25),28):null);appButtons.get(slot).setTranslationZ(moving?16:0);}
    private void styleSidebar(){for(int i=0;i<sideButtons.size();i++)styleSide(i,sideButtons.get(i).hasFocus());for(int i=0;i<appButtons.size();i++)styleApp(i,appButtons.get(i).hasFocus());}
    private void setSidebar(boolean open){if(sidebarOpen==open)return;sidebarOpen=open;LayoutParams p=(LayoutParams)sidebar.getLayoutParams();p.width=open?300:76;sidebar.setLayoutParams(p);List<FrameLayout> buttons=new ArrayList<>(sideButtons);buttons.addAll(appButtons);for(FrameLayout v:buttons){LayoutParams q=(LayoutParams)v.getLayoutParams();q.width=open?284:60;v.setLayoutParams(q);}List<TextView> labels=new ArrayList<>(sideLabels);labels.addAll(appLabels);for(TextView v:labels){v.animate().cancel();v.animate().alpha(open?1:0).setDuration(Visuals.duration(activity,110)).start();}serverLabel.setAlpha(open?1:0);sidebarArt.setVisibility(open&&heroBitmap!=null?VISIBLE:GONE);scrim.animate().alpha(open?.19f:0).setDuration(Visuals.duration(activity,140)).start();}
    private void leaveSidebar(){setSidebar(false);View v=lastContent;if(v==null||!v.isAttachedToWindow()||v.getVisibility()!=VISIBLE)v=play;v.requestFocus();}
    private void activateSide(int i){
        if(i<=3){changeFilter(i==3?4:i);setSidebar(false);play.requestFocus();return;}
        if(i==4)showApps(sideButtons.get(i));else if(i==5)showInputs(sideButtons.get(i));else if(i==6)activity.startActivity(new Intent(activity,LiveTvActivity.class));else activity.startActivity(new Intent(activity,SettingsActivity.class));
    }
    private void refreshSidebarApps(){
        if(reorderOriginal!=null||menuPackage!=null)return;
        String[] selected=SidebarApps.slots(activity);boolean[] available=new boolean[selected.length];
        for(int i=0;i<selected.length;i++)available[i]=SidebarApps.launchable(activity,selected[i]);
        if(Arrays.equals(selected,sidePackages)&&Arrays.equals(available,availableApps))return;
        if(appButtons.contains(activity.getCurrentFocus()))play.requestFocus();
        appPanel.removeAllViews();appButtons.clear();appLabels.clear();appIcons.clear();sidePackages=selected;availableApps=available;
        int visible=0;List<View> focusOrder=new ArrayList<>();
        for(int slot=0;slot<selected.length;slot++){
            final String pkg=selected[slot];String name=SidebarApps.label(activity,pkg);
            FrameLayout button=new FrameLayout(activity);focusable(button,name);appButtons.add(button);
            LayoutParams position=new LayoutParams(sidebarOpen?284:60,56);position.leftMargin=8;position.topMargin=12+66*visible;appPanel.addView(button,position);button.setVisibility(available[slot]?VISIBLE:GONE);
            if(available[slot]){visible++;focusOrder.add(button);}
            ImageView icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(available[slot])icon.setImageDrawable(SidebarApps.icon(activity,pkg));LayoutParams ip=new LayoutParams(38,30,Gravity.CENTER_VERTICAL);ip.leftMargin=11;button.addView(icon,ip);appIcons.add(icon);
            TextView label=text(name,21,WHITE,Ui.medium);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setAlpha(sidebarOpen?1:0);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams lp=new LayoutParams(206,56);lp.leftMargin=64;button.addView(label,lp);appLabels.add(label);
            button.setOnFocusChangeListener((v,f)->{if(f)setSidebar(true);styleApp(appButtons.indexOf(v),f);});
            button.setOnLongClickListener(v->openShortcutMenu(v));
            button.setOnClickListener(v->{if(reorderOriginal!=null||menuPackage!=null)return;if(SidebarApps.YOUTUBE.equals(pkg)){changeFilter(3);setSidebar(false);play.requestFocus();loadYouTube();}else openPackage(pkg,name);});
            button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(v.hasFocus()&&sidebarOpen)v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});
            button.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)leaveSidebar();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)moveSidebarApp(appButtons.indexOf(v),key==KeyEvent.KEYCODE_DPAD_DOWN);return true;}
                return false;
            });
        }
        appPanel.setLayoutParams(new ScrollView.LayoutParams(-1,visible*66+24));appScroll.scrollTo(0,0);
        focusOrder.add(0,sideButtons.get(3));focusOrder.add(sideButtons.get(4));
        for(int i=0;i+1<focusOrder.size();i++){focusOrder.get(i).setNextFocusDownId(focusOrder.get(i+1).getId());focusOrder.get(i+1).setNextFocusUpId(focusOrder.get(i).getId());}
        if(filter==3&&activeSide()==sideButtons.get(0)){filter=0;showFilms();}
        styleSidebar();
    }
    private void layoutSidebarApps(){
        int visible=0;List<View> order=new ArrayList<>();order.add(sideButtons.get(3));
        for(int i=0;i<appButtons.size();i++)if(availableApps[i]){
            FrameLayout button=appButtons.get(i);LayoutParams p=(LayoutParams)button.getLayoutParams();p.topMargin=12+66*visible++;button.setLayoutParams(p);order.add(button);
        }
        order.add(sideButtons.get(4));
        for(int i=0;i+1<order.size();i++){order.get(i).setNextFocusDownId(order.get(i+1).getId());order.get(i+1).setNextFocusUpId(order.get(i).getId());order.get(i+1).setAccessibilityTraversalAfter(order.get(i).getId());}
        styleSidebar();
    }
    private boolean beginReorder(View view){
        int slot=appButtons.indexOf(view);
        if(!active||slot<0||!availableApps[slot])return false;
        if(reorderOriginal!=null)return true;
        reorderOriginal=sidePackages.clone();reorderSlot=slot;consumeConfirmUp=confirmHeld;reorderConfirmArmed=false;
        view.cancelLongPress();view.setPressed(false);setSidebar(true);view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        reorderHint.setVisibility(VISIBLE);updateReorderHint();styleSidebar();return true;
    }
    private boolean openShortcutMenu(View view){
        int slot=appButtons.indexOf(view);if(!active||slot<0||!availableApps[slot])return false;
        if(reorderOriginal!=null||menuPackage!=null)return true;
        menuPackage=sidePackages[slot];menuSnapshot=sidePackages.clone();menuAction=0;menuConfirmArmed=false;consumeConfirmUp=confirmHeld;
        view.cancelLongPress();view.setPressed(false);view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);setSidebar(true);
        shortcutTitle.setText(SidebarApps.label(activity,menuPackage));shortcutMenu.setVisibility(VISIBLE);shortcutMove.requestFocus();styleShortcutMenu();styleSidebar();return true;
    }
    private void styleShortcutMenu(){
        TextView[] items={shortcutMove,shortcutRemove};for(int i=0;i<items.length;i++){items[i].setBackground(round(i==menuAction?WHITE:0xFF354151,20));items[i].setTextColor(i==menuAction?BG:WHITE);}
    }
    private void closeShortcutMenu(boolean restoreFocus){
        if(menuPackage==null)return;String pkg=menuPackage;menuPackage=null;menuSnapshot=null;menuConfirmArmed=false;shortcutMenu.setVisibility(GONE);styleSidebar();
        if(restoreFocus){refreshSidebarApps();int slot=Arrays.asList(sidePackages).indexOf(pkg);focusNearbyApp(slot);}
    }
    private void focusNearbyApp(int slot){
        int start=Math.max(0,Math.min(slot,sidePackages.length));
        for(int i=start;i<sidePackages.length;i++)if(availableApps[i]){appButtons.get(i).requestFocus();return;}
        for(int i=start-1;i>=0;i--)if(availableApps[i]){appButtons.get(i).requestFocus();return;}
        sideButtons.get(4).requestFocus();
    }
    private boolean moveFromShortcutMenu(){
        if(menuPackage==null)return false;String pkg=menuPackage;boolean current=Arrays.equals(menuSnapshot,SidebarApps.slots(activity));closeShortcutMenu(false);refreshSidebarApps();
        int slot=Arrays.asList(sidePackages).indexOf(pkg);
        if(!current||slot<0||!availableApps[slot]){focusNearbyApp(slot);note(Ui.tr("Os acessos mudaram. Tente novamente.","Shortcuts changed. Please try again.","Los accesos han cambiado. Vuelve a intentarlo."));return false;}
        View button=appButtons.get(slot);button.requestFocus();return beginReorder(button);
    }
    private void removeFromShortcutMenu(){
        if(menuPackage==null)return;int slot=Arrays.asList(menuSnapshot).indexOf(menuPackage);
        boolean removed=SidebarApps.remove(activity,menuSnapshot,menuPackage);closeShortcutMenu(false);refreshSidebarApps();focusNearbyApp(slot);
        note(removed?Ui.tr("Acesso removido. O app continua instalado.","Shortcut removed. The app stays installed.","Acceso quitado. La app sigue instalada."):Ui.tr("Os acessos mudaram. Tente novamente.","Shortcuts changed. Please try again.","Los accesos han cambiado. Vuelve a intentarlo."));
    }
    private void updateReorderHint(){
        int count=0,position=0;for(int i=0;i<availableApps.length;i++)if(availableApps[i]){count++;if(i==reorderSlot)position=count;}
        reorderHint.setText(Ui.tr("Ordenar apps · ↑↓ Mover · OK Salvar · Voltar Cancelar", "Reorder apps · ↑↓ Move · OK Save · Back Cancel", "Ordenar apps · ↑↓ Mover · OK Guardar · Atrás Cancelar")+"   "+position+" / "+count);
    }
    private void swapSidebarApps(int from,int to){
        String pkg=sidePackages[from];sidePackages[from]=sidePackages[to];sidePackages[to]=pkg;
        boolean available=availableApps[from];availableApps[from]=availableApps[to];availableApps[to]=available;
        Collections.swap(appButtons,from,to);Collections.swap(appLabels,from,to);Collections.swap(appIcons,from,to);
    }
    private void moveReorder(boolean down){
        int next=reorderSlot+(down?1:-1);while(next>=0&&next<sidePackages.length&&!availableApps[next])next+=down?1:-1;
        if(next<0||next>=sidePackages.length)return;
        swapSidebarApps(reorderSlot,next);reorderSlot=next;layoutSidebarApps();updateReorderHint();
    }
    private void finishReorder(boolean save){
        if(reorderOriginal==null)return;
        String[] original=reorderOriginal;String moved=sidePackages[reorderSlot];
        boolean saved=save&&SidebarApps.reorder(activity,original,sidePackages);
        reorderOriginal=null;reorderSlot=-1;reorderConfirmArmed=false;reorderHint.setVisibility(GONE);
        if(!saved)for(int i=0;i<original.length;i++)if(!original[i].equals(sidePackages[i])){
            for(int j=i+1;j<sidePackages.length;j++)if(original[i].equals(sidePackages[j])){swapSidebarApps(i,j);break;}
        }
        layoutSidebarApps();refreshSidebarApps();
        for(int i=0;i<sidePackages.length;i++)if(sidePackages[i].equals(moved)&&availableApps[i]){appButtons.get(i).requestFocus();appButtons.get(i).requestRectangleOnScreen(new Rect(0,0,appButtons.get(i).getWidth(),appButtons.get(i).getHeight()),true);break;}
        if(active&&!closed)note(saved?Ui.tr("Ordem salva.","Order saved.","Orden guardado."):save?Ui.tr("Os acessos mudaram. Tente novamente.","Shortcuts changed. Please try again.","Los accesos han cambiado. Vuelve a intentarlo."):Ui.tr("Ordem anterior mantida.","Previous order kept.","Orden anterior conservado."));
    }
    private void moveSidebarApp(int slot,boolean down){
        int next=slot+(down?1:-1);while(next>=0&&next<sidePackages.length&&!availableApps[next])next+=down?1:-1;
        View target=next>=0&&next<sidePackages.length?appButtons.get(next):sideButtons.get(down?4:3);target.requestFocus();
    }
    private void enableYouTube(){if(activity.checkSelfPermission(TvSuggestions.PERMISSION)!=PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{TvSuggestions.PERMISSION},80);else loadYouTube();}
    private void updateAccount(){
        String name=plex.connected()?plex.name():Ui.tr("Vincular Plex","Link Plex","Vincular Plex");
        accountTitle.setText(name);accountInitial.setText(name.isEmpty()?"P":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(Ui.locale()));
        serverLabel.setText(plex.connected()?Ui.tr("Plex · Conectado","Plex · Connected","Plex · Conectado"):Ui.tr("Escolher servidor","Choose server","Elegir servidor"));
        account.setContentDescription(name+" · "+Ui.tr("Alterar servidor Plex","Change Plex server","Cambiar servidor Plex"));
        if(!active)return;
        String key=PlexClient.profileKey(plex.prefs.getString("accountToken",""));
        if(key.equals(accountImageKey)&&accountIcon.getDrawable()!=null)return;
        if(key.equals(accountRequestKey)&&avatarRequest!=null&&avatarRequest.pending())return;
        if(avatarRequest!=null)avatarRequest.cancel();
        int epoch=++accountGeneration;accountIcon.setImageDrawable(null);accountInitial.setVisibility(VISIBLE);accountImageKey="";
        if(key.isEmpty())return;
        accountRequestKey=key;
        avatarRequest=images.loadAvatar(bitmap->{if(active&&!closed&&epoch==accountGeneration&&key.equals(PlexClient.profileKey(plex.prefs.getString("accountToken","")))){accountIcon.setImageBitmap(bitmap);accountInitial.setVisibility(GONE);accountImageKey=key;}});
    }
    private void applyAppearance(){
        String key=Appearance.signature(activity);if(key.equals(appearanceKey))return;appearanceKey=key;
        int shade=Appearance.backdropIndex(activity);backdropShade.setAlpha(shade==0?.30f:0);backdropLeft.setAlpha(shade==2?.75f:1);backdropBottom.setAlpha(shade==2?.82f:1);
        boolean large=Appearance.sizeIndex(activity)==2;int height=Appearance.posterHeight(activity)+104;
        LayoutParams layout=(LayoutParams)shelvesScroll.getLayoutParams();layout.topMargin=1080-height;layout.height=height;shelvesScroll.setLayoutParams(layout);
        LayoutParams action=(LayoutParams)play.getLayoutParams();action.topMargin=large?554:600;play.setLayoutParams(action);
        LayoutParams options=(LayoutParams)link.getLayoutParams();options.topMargin=action.topMargin;link.setLayoutParams(options);
        LayoutParams description=(LayoutParams)summary.getLayoutParams();description.height=large?40:70;summary.setLayoutParams(description);summary.setMaxLines(large?1:2);
        LayoutParams pages=(LayoutParams)pager.getLayoutParams();pages.topMargin=large?626:680;pager.setLayoutParams(pages);
        play.setBackground(round(Appearance.accent(activity),30));styleSidebar();for(int i=0;i<appButtons.size();i++)styleApp(i,appButtons.get(i).hasFocus());showFilms();
    }
    void resume(){
        active=true;
        applyAppearance();
        headerStatus.start();
        refreshSidebarApps();
        Visuals.sidebarBlur(sidebarArt);
        String id=plex.identity();
        if(!id.equals(displayedServer)){cancelCatalog();catalogError=false;catalogSections=Collections.emptyList();catalogSection=null;catalogPage=null;requestedSection="";requestedOffset=0;selected=null;images.clear();displayedServer=id;films=plex.cached();refreshedAt=0;libraryDirty=false;showFilms();}
        else if(libraryDirty){films=plex.cached();libraryDirty=false;if(!browsing())showFilms();}
        if(artReleased||heroBitmap==null||(selected!=null&&!heroKey.equals(selected.provider+selected.key))){artReleased=false;if(selected!=null)selectFilm(selected);main.post(visiblePosters);}
        updateAccount();main.removeCallbacks(tick);tick.run();
        updatePager();
        if(plex.connected()&&(films.isEmpty()||SystemClock.elapsedRealtime()-refreshedAt>60_000))refresh();
        if(browsing()&&plex.connected()&&catalogPage==null&&!catalogLoading)loadCatalog(requestedSection,requestedOffset,false);
    }
    void pause(){active=false;accountGeneration++;if(avatarRequest!=null)avatarRequest.cancel();avatarRequest=null;accountRequestKey="";headerStatus.stop();cancelCatalog();closeShortcutMenu(false);finishReorder(false);confirmHeld=false;consumeConfirmUp=false;for(View button:appButtons){button.cancelLongPress();button.setPressed(false);}main.removeCallbacks(tick);main.removeCallbacks(visiblePosters);if(heroLoad!=null)main.removeCallbacks(heroLoad);}
    void releaseImages(){
        imageGeneration++;posterGeneration++;artReleased=true;if(heroLoad!=null)main.removeCallbacks(heroLoad);
        images.clear();heroBitmap=null;heroKey="";backdrop.setImageDrawable(null);sidebarArt.setImageDrawable(null);titleLogo.setImageDrawable(null);titleLogo.setVisibility(GONE);title.setVisibility(VISIBLE);
        accountGeneration++;avatarRequest=null;accountImageKey="";accountRequestKey="";accountIcon.setImageDrawable(null);accountInitial.setVisibility(VISIBLE);
        for(ImageView image:posterImages)image.setImageDrawable(null);
        for(int i=0;i<posterRequests.size();i++)posterRequests.set(i,null);
        overlay.removeAllViews();overlay.setVisibility(GONE);overlayReturn=null;
        surface.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);sidebar.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }
    void trimMemory(){if(!active)releaseImages();else images.trim();}
    private void loadVisiblePosters(){
        if(!active||closed)return;
        int epoch=posterGeneration;
        for(int i=0;i<posterImages.size();i++){
            View card=cards.get(i);ImageView image=posterImages.get(i);
            if(!posterVisible(i)){
                if(posterRequests.get(i)!=null)posterRequests.get(i).cancel();posterRequests.set(i,null);image.setImageDrawable(null);continue;
            }
            CinemaImages.Request request=posterRequests.get(i);
            if(image.getDrawable()!=null||(request!=null&&request.pending()))continue;
            PlexClient.Film film=posterFilms.get(i);boolean wide=filter==3;
            final int slot=i;
            CinemaImages.Ready ready=b->{if(active&&!closed&&epoch==posterGeneration&&slot<posterImages.size()&&posterImages.get(slot)==image&&posterVisible(slot))image.setImageBitmap(b);};
            posterRequests.set(i,wide?images.loadTv(film.art,ready):images.load(film.thumb,Appearance.posterWidth(activity),Appearance.posterHeight(activity),ready));
        }
    }
    private boolean posterVisible(int i){
        HorizontalScrollView rail=posterRails.get(i);View card=cards.get(i),row=(View)rail.getParent();
        int left=rail.getScrollX()-200,right=rail.getScrollX()+rail.getWidth()+200;
        int top=shelvesScroll.getScrollY(),bottom=top+shelvesScroll.getHeight();
        int cardTop=row.getTop()+rail.getTop()+card.getTop(),cardBottom=row.getTop()+rail.getTop()+card.getBottom();
        return card.getRight()>=left&&card.getLeft()<=right&&cardBottom>top&&cardTop<bottom;
    }
    private void schedulePosters(){main.removeCallbacks(visiblePosters);main.postDelayed(visiblePosters,40);}
    void destroy(){closed=true;pause();images.close();io.shutdownNow();main.removeCallbacksAndMessages(null);}
    void reset(){closeShortcutMenu(false);finishReorder(false);closeOverlay();changeFilter(0);setSidebar(false);play.requestFocus();}
    void loadYouTube(){io.execute(()->{List<PlexClient.Film> fresh=TvSuggestions.youtube(activity);main.post(()->{if(!closed){youtube=fresh;if(filter==3&&active)showFilms();}});});}
    private void refresh(){if(loading)return;loading=true;refreshedAt=SystemClock.elapsedRealtime();String server=plex.identity();io.execute(()->{try{List<PlexClient.Film> fresh=plex.library();main.post(()->{if(!closed&&server.equals(plex.identity())){films=fresh;if(active&&filter==0)showFilms();else libraryDirty=true;}});}catch(Exception ignored){main.post(()->{if(!closed&&films.isEmpty()&&!browsing())rowNote.setText("No se ha podido leer Plex. Comprueba el servidor o vuelve a vincularlo.");});}finally{main.post(()->loading=false);}});}

    private boolean browsing(){return filter==1||filter==2||filter==4;}
    private boolean accepts(PlexClient.Section section,int kind){return kind==4||(kind==1&&section.type.equals("movie"))||(kind==2&&section.type.equals("show"));}
    private void cancelCatalog(){catalogGeneration++;catalogLoading=false;if(catalogWork!=null){catalogWork.cancel(true);catalogWork=null;}}
    private void changeFilter(int kind){
        cancelCatalog();
        if(kind!=filter){catalogPage=null;requestedOffset=0;requestedSection=catalogSection!=null&&accepts(catalogSection,kind)?catalogSection.key:"";}
        if(catalogSection!=null&&!accepts(catalogSection,kind))catalogSection=null;
        filter=kind;catalogError=false;showFilms();
        if(browsing()&&plex.connected()&&catalogPage==null)loadCatalog(requestedSection,0,false);
    }
    private void loadCatalog(String sectionKey,int offset,boolean focusCards){
        if(!active||closed||!browsing()||!plex.connected())return;
        cancelCatalog();catalogLoading=true;catalogError=false;requestedSection=sectionKey;requestedOffset=offset;updatePager();
        final int epoch=catalogGeneration,kind=filter;final String server=plex.identity();
        final List<PlexClient.Section> known=catalogSections;
        catalogWork=io.submit(()->{
            try{
                List<PlexClient.Section> sections=known.isEmpty()?plex.sections():known;
                PlexClient.Section chosen=null;
                for(PlexClient.Section s:sections)if(accepts(s,kind)){if(chosen==null)chosen=s;if(s.key.equals(sectionKey)){chosen=s;break;}}
                final PlexClient.Section section=chosen;
                final PlexClient.LibraryPage page=section==null?null:plex.page(section,offset);
                main.post(()->{
                    if(closed||!active||epoch!=catalogGeneration||filter!=kind||!server.equals(plex.identity()))return;
                    catalogLoading=false;catalogWork=null;catalogSections=sections;catalogSection=section;catalogPage=page;
                    if(section!=null)requestedSection=section.key;
                    selected=null;showFilms();
                    if(section==null)rowNote.setText(Ui.tr("Não há bibliotecas de filmes ou séries disponíveis nesta categoria.","No movie or series libraries available in this category.","No hay bibliotecas de películas o series disponibles en esta categoría."));
                    if(focusCards&&!sidebarOpen&&overlay.getVisibility()!=VISIBLE){if(!cards.isEmpty())cards.get(0).requestFocus();else libraryChooser.requestFocus();}
                });
            }catch(Exception ignored){main.post(()->{if(!closed&&active&&epoch==catalogGeneration){catalogLoading=false;catalogWork=null;catalogError=true;updatePager();if(catalogPage==null)rowNote.setText(Ui.tr("Não foi possível carregar. Selecione Tentar novamente.","Could not load. Select Retry.","No se ha podido cargar. Selecciona Reintentar."));}});}
        });
    }
    private void updatePager(){
        boolean visible=browsing()&&plex.connected();pager.setVisibility(visible?VISIBLE:GONE);if(!visible)return;
        libraryChooser.setText(catalogSection==null?Ui.tr("Escolher biblioteca ▾","Choose library ▾","Elegir biblioteca ▾"):catalogSection.title+" ▾");
        libraryChooser.setEnabled(!catalogLoading&&!catalogSections.isEmpty());libraryChooser.setAlpha(libraryChooser.isEnabled()?1:.55f);
        String status=catalogLoading?Ui.tr("Carregando…","Loading…","Cargando…"):catalogError?Ui.tr("Sem conexão · Tentar novamente","Offline · Retry","Sin conexión · Reintentar"):"";
        if(status.isEmpty()&&catalogPage!=null){int start=catalogPage.received==0?0:catalogPage.offset+1,end=catalogPage.offset+catalogPage.received;status=start+"–"+end+(catalogPage.total<0?"":Ui.tr(" de "," of "," de ")+catalogPage.total);}
        pageStatus.setText(status);
        pagePrevious.setEnabled(!catalogLoading&&catalogPage!=null&&catalogPage.offset>0);pagePrevious.setAlpha(pagePrevious.isEnabled()?1:.4f);
        pageNext.setText(catalogError?Ui.tr("Tentar novamente","Retry","Reintentar"):Ui.tr("Seguinte ›","Next ›","Siguiente ›"));
        pageNext.setEnabled(!catalogLoading&&(catalogError||(catalogPage!=null&&catalogPage.hasNext())));pageNext.setAlpha(pageNext.isEnabled()?1:.4f);
    }
    private boolean firstPoster(View view){for(Shelf row:shelves)if(!row.items.isEmpty()&&view==row.items.get(0))return true;return false;}
    private void showLibrarySections(){
        if(catalogLoading||catalogSections.isEmpty())return;
        startOverlay(Ui.tr("Bibliotecas do Plex","Plex libraries","Bibliotecas de Plex"),libraryChooser);
        ScrollView scroll=new ScrollView(activity);LayoutParams sp=new LayoutParams(1250,760);sp.leftMargin=84;sp.topMargin=194;overlay.addView(scroll,sp);
        LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(8,8,32,16);scroll.addView(list);
        boolean first=true;
        for(PlexClient.Section section:catalogSections)if(accepts(section,filter)){
            TextView item=button(section.title+" · "+(section.type.equals("movie")?Ui.tr("Filmes","Movies","Películas"):Ui.tr("Séries","Series","Series")),23);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,82);p.bottomMargin=20;list.addView(item,p);
            item.setOnClickListener(v->{closeOverlay();loadCatalog(section.key,0,true);});
            if(first){item.requestFocus();first=false;}
        }
    }
    private final class Shelf {
        final FrameLayout root=new FrameLayout(activity);
        final HorizontalScrollView rail=new HorizontalScrollView(activity);
        final LinearLayout posters=new LinearLayout(activity);
        final List<View> items=new ArrayList<>();
        Shelf(String heading,int railHeight){
            TextView label=text(heading,27,WHITE,Ui.displaySemibold);LayoutParams lp=new LayoutParams(1500,42);lp.leftMargin=12;lp.topMargin=11;root.addView(label,lp);
            posters.setOrientation(LinearLayout.HORIZONTAL);posters.setClipChildren(false);posters.setClipToPadding(false);posters.setPadding(12,18,40,20);
            rail.setHorizontalScrollBarEnabled(false);rail.setSmoothScrollingEnabled(false);rail.setClipChildren(false);rail.setClipToPadding(false);rail.addView(posters,new HorizontalScrollView.LayoutParams(-2,railHeight));
            // The shelf viewport's single tree listener also observes horizontal scrolling.
            LayoutParams rp=new LayoutParams(-1,railHeight);rp.topMargin=54;root.addView(rail,rp);
            shelfList.addView(root,new LinearLayout.LayoutParams(-1,railHeight+62));shelves.add(this);
        }
    }
    private void showFilms(){
        if(closed)return;
        boolean restoreFocus=cards.contains(activity.getCurrentFocus());String restoreKey=selected==null?"":selected.key;View restored=null;
        pageTitle.setText(filter==0?"Inicio":filter==1?"Películas":filter==2?"Series":filter==4?"Biblioteca":"YouTube");
        styleSidebar();
        posterGeneration++;
        for(CinemaImages.Request request:posterRequests)if(request!=null)request.cancel();
        for(ImageView image:posterImages)image.setImageDrawable(null);
        shelfList.removeAllViews();shelves.clear();cards.clear();posterImages.clear();posterFilms.clear();posterRequests.clear();posterRails.clear();shelvesScroll.scrollTo(0,0);updatePager();
        List<PlexClient.Film> visible=new ArrayList<>();
        if(filter==3)visible.addAll(youtube);else if(browsing()){if(catalogPage!=null)visible.addAll(catalogPage.films);}else visible.addAll(films);
        rowNote.setVisibility(visible.isEmpty()?VISIBLE:GONE);shelvesScroll.setVisibility(visible.isEmpty()?GONE:VISIBLE);
        link.setContentDescription(filter==3?"Activar filas de la TV":"Opciones de Plex");
        if(visible.isEmpty()){
            play.setNextFocusDownId(browsing()?pageNext.getId():play.getId());link.setNextFocusDownId(browsing()?pageNext.getId():play.getId());
            selected=null;imageGeneration++;heroBitmap=null;backdrop.setImageDrawable(null);sidebarArt.setImageDrawable(null);titleLogo.setVisibility(GONE);title.setVisibility(VISIBLE);
            title.setText(filter==3?"Todo lo que te gusta.":plex.connected()?"Tu biblioteca, a lo grande.":"Tu cine empieza aquí.");
            providerMark.setImageDrawable(brand(filter==3?1:0));connection.setText(filter==3?"YouTube":"Tu biblioteca · Plex");meta.setText(filter==3?"Vídeos en tu televisión":"Plex · YouTube · Prime Video");
            summary.setText(filter==3?"Aquí aparecerán las filas que YouTube comparta con tu televisión.":plex.connected()?"No hay títulos disponibles en esta categoría.":"Conecta Plex para descubrir tus películas y series con sus imágenes originales.");
            play.setText(filter==3?"Abrir YouTube":plex.connected()?"Abrir Plex":"Vincular Plex");rowNote.setText(filter==3?"Activa el acceso a las filas de la TV desde el botón de opciones.":!plex.connected()?"Los carteles aparecerán automáticamente al vincular tu cuenta.":browsing()&&catalogPage==null?Ui.tr("Carregando bibliotecas do Plex…","Loading Plex libraries…","Cargando bibliotecas de Plex…"):Ui.tr("Não há títulos nesta página ou biblioteca.","No titles in this page or library.","No hay títulos en esta página o biblioteca."));return;
        }
        PlexClient.Film keep=null;if(selected!=null)for(PlexClient.Film f:visible)if(f.key.equals(selected.key)&&f.provider.equals(selected.provider))keep=f;
        selectFilm(keep==null?visible.get(0):keep);
        if(filter==0){
            List<PlexClient.Film> continuing=new ArrayList<>(),library=new ArrayList<>();
            for(PlexClient.Film film:visible)(film.continuing?continuing:library).add(film);
            if(!continuing.isEmpty())addShelf(Ui.tr("Continuar assistindo","Continue watching","Seguir viendo"),continuing,false,12);
            if(!library.isEmpty())addShelf(Ui.tr("Da sua biblioteca","From your library","De tu biblioteca"),library,false,12);
        }else addShelf(filter==3?Ui.tr("Do YouTube","From YouTube","Desde YouTube"):catalogSection==null?Ui.tr("Sua biblioteca","Your library","Tu biblioteca"):catalogSection.title,visible,filter==3,browsing()?PlexClient.PAGE_SIZE:12);
        for(int r=0;r<shelves.size();r++){
            Shelf row=shelves.get(r);
            for(int c=0;c<row.items.size();c++){
                View card=row.items.get(c);card.setNextFocusUpId(r==0?(browsing()?libraryChooser:play).getId():shelves.get(r-1).items.get(Math.min(c,shelves.get(r-1).items.size()-1)).getId());
                card.setNextFocusDownId(r+1<shelves.size()?shelves.get(r+1).items.get(Math.min(c,shelves.get(r+1).items.size()-1)).getId():card.getId());
                if(posterFilms.get(cards.indexOf(card)).key.equals(restoreKey))restored=card;
            }
        }
        if(!cards.isEmpty()){play.setNextFocusDownId((browsing()?libraryChooser:cards.get(0)).getId());link.setNextFocusDownId((browsing()?libraryChooser:cards.get(0)).getId());libraryChooser.setNextFocusDownId(cards.get(0).getId());pagePrevious.setNextFocusDownId(cards.get(0).getId());pageNext.setNextFocusDownId(cards.get(0).getId());}
        if(restoreFocus&&!cards.isEmpty())(restored==null?cards.get(0):restored).requestFocus();
        if(active){artReleased=false;main.post(visiblePosters);}
    }
    private void addShelf(String heading,List<PlexClient.Film> films,boolean wide,int limit){
        Shelf row=new Shelf(heading,wide?294:Appearance.posterHeight(activity)+42);int count=0;
        for(PlexClient.Film film:films){
            if(count++==limit)break;
            final int width=wide?320:Appearance.posterWidth(activity),height=wide?230:Appearance.posterHeight(activity),pictureHeight=wide?180:height;
            FrameLayout card=new FrameLayout(activity);focusable(card,film.title+", "+film.provider);card.setClipChildren(false);card.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),pictureHeight,14);o.setAlpha(1);}});card.setOutlineAmbientShadowColor(0x99000000);card.setOutlineSpotShadowColor(0xDD000000);
            FrameLayout picture=new FrameLayout(activity);picture.setBackground(round(0xFF1A1C24,14));picture.setClipToOutline(true);card.addView(picture,new LayoutParams(width,pictureHeight));
            ImageView image=new ImageView(activity);image.setScaleType(wide?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);image.setCropToPadding(true);picture.addView(image,new LayoutParams(-1,-1));
            if(wide){TextView caption=text(film.title,19,0xFFE2E2E9,Ui.medium);caption.setMaxLines(1);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams cp=new LayoutParams(width,36);cp.topMargin=190;card.addView(caption,cp);}
            if(film.progress>=0){View progress=new View(activity);progress.setBackgroundColor(Appearance.accent(activity));LayoutParams pp=new LayoutParams((int)(width*film.progress),4,Gravity.BOTTOM);picture.addView(progress,pp);}
            card.setOnFocusChangeListener((v,f)->{lift(v,f?1.055f:1,f?-6:0,f?26:0);if(f){lastContent=v;setSidebar(false);shelvesScroll.scrollTo(0,row.root.getTop());selectFilm(film);schedulePosters();}});card.setOnClickListener(v->{selectFilm(film);openFilm();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.rightMargin=28;row.posters.addView(card,p);row.items.add(card);cards.add(card);
            posterImages.add(image);posterFilms.add(film);posterRequests.add(null);posterRails.add(row.rail);
        }
    }
    private void selectFilm(PlexClient.Film film){
        boolean same=heroKey.equals(film.provider+film.key)&&heroBitmap!=null&&!artReleased;
        selected=film;title.setText(film.title);providerMark.setImageDrawable(brand(film.provider.equals("YouTube")?1:0));
        connection.setText(film.section+(film.provider.equals("Plex")?" · "+plex.name():""));meta.setText(film.meta);summary.setText(film.summary);play.setText(film.provider.equals("YouTube")?"Abrir YouTube":"Abrir en Plex");
        if(same)return;
        title.setVisibility(VISIBLE);titleLogo.setVisibility(GONE);titleLogo.setImageDrawable(null);titleLogo.setContentDescription(film.title);
        int generation=++imageGeneration;if(heroLoad!=null)main.removeCallbacks(heroLoad);
        if(heroRequest!=null)heroRequest.cancel();if(logoRequest!=null)logoRequest.cancel();
        if(!active)return;
        heroLoad=()->{
            if(!active||closed||generation!=imageGeneration)return;
            CinemaImages.Ready art=b->{if(active&&!closed&&generation==imageGeneration){heroBitmap=b;heroKey=film.provider+film.key;backdrop.setImageBitmap(b);sidebarArt.setImageBitmap(b);sidebarArt.setVisibility(sidebarOpen?VISIBLE:GONE);}};
            if(film.provider.equals("YouTube"))heroRequest=images.loadTv(film.art,Visuals.light(activity)?1280:1920,Visuals.light(activity)?720:1080,art);
            else{
                boolean light=Visuals.light(activity);heroRequest=images.load(film.art,light?1280:1920,light?720:1080,art);
                logoRequest=images.loadLogo(film.logo,b->{if(active&&!closed&&generation==imageGeneration){titleLogo.setImageBitmap(b);titleLogo.setVisibility(VISIBLE);title.setVisibility(INVISIBLE);}});
            }
        };
        main.postDelayed(heroLoad,90);
    }
    private void openFilm(){
        if(selected==null){if(filter==3)openPackage(TvSuggestions.YOUTUBE,"YouTube");else if(!plex.connected())activity.startActivity(new Intent(activity,PlexLinkActivity.class));else openPackage("com.plexapp.android","Plex");return;}
        if(selected.provider.equals("YouTube")){try{Intent supplied=Intent.parseUri(selected.intent,Intent.URI_INTENT_SCHEME);Intent safe=new Intent(Intent.ACTION_VIEW,supplied.getData()).setPackage(TvSuggestions.YOUTUBE);if(safe.resolveActivity(activity.getPackageManager())!=null)launch(safe);else openPackage(TvSuggestions.YOUTUBE,"YouTube");}catch(Exception ignored){openPackage(TvSuggestions.YOUTUBE,"YouTube");}return;}
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(plex.openUri(selected))).setPackage("com.plexapp.android");if(intent.resolveActivity(activity.getPackageManager())!=null)launch(intent);else openPackage("com.plexapp.android","Plex");
    }
    private void openPackage(String pkg,String name){Intent intent=HomeApps.launchIntent(activity.getPackageManager(),pkg);if(intent==null){note(name+" no está instalado.");return;}launch(intent);}
    private void launch(Intent intent){try{activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException|SecurityException e){note("No se ha podido abrir esta opción.");}}
    private void note(String s){Toast.makeText(activity,s,Toast.LENGTH_SHORT).show();}
    private void startOverlay(String heading,View from){overlayReturn=from;setSidebar(false);surface.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);sidebar.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);overlay.removeAllViews();overlay.setVisibility(VISIBLE);TextView h=text(heading,46,WHITE,Ui.displaySemibold);LayoutParams p=new LayoutParams(1400,70);p.leftMargin=84;p.topMargin=68;overlay.addView(h,p);TextView close=button("Cerrar",20);p=new LayoutParams(140,56);p.leftMargin=1692;p.topMargin=76;overlay.addView(close,p);close.setOnClickListener(v->closeOverlay());}
    private void closeOverlay(){if(overlay.getVisibility()!=VISIBLE)return;overlay.setVisibility(GONE);surface.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);sidebar.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);if(overlayReturn!=null&&overlayReturn.isAttachedToWindow())overlayReturn.requestFocus();else play.requestFocus();}
    private FrameLayout appTile(String pkg,String name,int index){FrameLayout tile=new FrameLayout(activity);focusable(tile,name);tile.setBackground(round(0xFF20232C,24));tile.setOnFocusChangeListener((v,f)->{v.setBackground(round(f?0xFF393D49:0xFF20232C,24));lift(v,f?1.04f:1,f?-3:0,f?22:0);});ImageView logo=new ImageView(activity);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);if(index<3)logo.setImageDrawable(brand(index));else logo.setImageDrawable(SidebarApps.icon(activity,pkg));tile.addView(logo,new LayoutParams(index==0?155:index==3?70:245,index==0?72:index==3?70:85,Gravity.CENTER));if(index==3){TextView label=text(name,18,WHITE,Ui.medium);label.setGravity(Gravity.CENTER);LayoutParams p=new LayoutParams(-1,30,Gravity.BOTTOM);p.bottomMargin=8;tile.addView(label,p);}tile.setOnClickListener(v->openPackage(pkg,name));return tile;}
    private void showApps(View from){
        startOverlay(Ui.tr("Seus apps","Your apps","Tus apps"),from);Set<String> seen=new HashSet<>();int featured=0;
        for(String pkg:SidebarApps.slots(activity)){
            if(!SidebarApps.launchable(activity,pkg)||!seen.add(pkg))continue;
            int brand=pkg.equals("com.plexapp.android")?0:pkg.equals(SidebarApps.YOUTUBE)?1:pkg.equals("com.amazon.amazonvideo.livingroom")?2:3;
            FrameLayout tile=appTile(pkg,SidebarApps.label(activity,pkg),brand);LayoutParams p=new LayoutParams(394,176);p.leftMargin=84+featured*422;p.topMargin=196;overlay.addView(tile,p);if(featured==0)tile.requestFocus();if(++featured==4)break;
        }
        int top=featured==0?196:408;
        TextView installed=text(Ui.tr("Todos os aplicativos","All apps","Todas las aplicaciones"),24,WHITE,Ui.displaySemibold);LayoutParams ip=new LayoutParams(1600,48);ip.leftMargin=84;ip.topMargin=top;overlay.addView(installed,ip);
        ScrollView scroll=new ScrollView(activity);LayoutParams sp=new LayoutParams(1752,986-top-68);sp.leftMargin=76;sp.topMargin=top+68;overlay.addView(scroll,sp);GridLayout grid=new GridLayout(activity);grid.setColumnCount(5);grid.setPadding(8,8,8,16);scroll.addView(grid);
        for(SidebarApps.Choice choice:SidebarApps.choices(activity)){if(!seen.add(choice.pkg))continue;TextView tile=button(choice.label,21);tile.setContentDescription(choice.label);tile.setOnClickListener(v->openPackage(choice.pkg,choice.label));GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=320;p.height=100;p.setMargins(0,0,28,26);grid.addView(tile,p);if(featured==0&&grid.getChildCount()==1)tile.requestFocus();}
    }
    private void option(String label,int y,Runnable action,boolean focus){TextView v=button(label,23);LayoutParams p=new LayoutParams(740,82);p.leftMargin=84;p.topMargin=y;overlay.addView(v,p);v.setOnClickListener(w->action.run());if(focus)v.requestFocus();}
    private void showSettings(View from){startOverlay("Ajustes",from);option(Ui.tr("Contas e serviços","Accounts and services","Cuentas y servicios"),210,()->activity.startActivity(new Intent(activity,SettingsActivity.class).putExtra("page",14)),true);option("Inicio y salvapantallas",316,()->activity.startActivity(new Intent(activity,SettingsActivity.class)),false);option("Ajustes de la televisión",422,()->launch(new Intent(Settings.ACTION_SETTINGS)),false);}
    private void showInputs(View from){startOverlay("Entradas y televisión",from);List<HomeApps.Item> inputs=HomeApps.allInputs(activity);int n=0;for(HomeApps.Item item:inputs){option(item.label+(item.connected?"":" · Sin señal"),210+n*106,()->{if(item.kind==HomeApps.LIVE_TV){Intent intent=HomeApps.liveTv(activity);if(intent!=null)launch(intent);}else launch(new Intent(Intent.ACTION_VIEW,android.media.tv.TvContract.buildChannelUriForPassthroughInput(item.inputId)));},n==0);n++;}if(n==0)option("Abrir ajustes de la TV",210,()->launch(new Intent(Settings.ACTION_SETTINGS)),true);}
    boolean onKey(KeyEvent event){
        int code=event.getKeyCode();boolean confirm=code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A;
        if(confirm&&event.getAction()==KeyEvent.ACTION_UP&&consumeConfirmUp){consumeConfirmUp=false;confirmHeld=false;reorderConfirmArmed=false;menuConfirmArmed=false;return true;}
        if(menuPackage!=null){
            if(confirm){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)menuConfirmArmed=true;else if(event.getAction()==KeyEvent.ACTION_UP&&menuConfirmArmed){if(menuAction==0)moveFromShortcutMenu();else removeFromShortcutMenu();}return true;}
            if(code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP)closeShortcutMenu(true);return true;}
            if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN){menuAction=1-menuAction;(menuAction==0?shortcutMove:shortcutRemove).requestFocus();styleShortcutMenu();}return true;}
            if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN&&moveFromShortcutMenu())moveReorder(code==KeyEvent.KEYCODE_DPAD_DOWN);return true;}
        }
        if(reorderOriginal!=null){
            if(confirm){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)reorderConfirmArmed=true;else if(event.getAction()==KeyEvent.ACTION_UP&&reorderConfirmArmed)finishReorder(true);return true;}
            if(code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP)finishReorder(false);return true;}
            if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)moveReorder(code==KeyEvent.KEYCODE_DPAD_DOWN);return true;}
            if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
        }
        if(confirm)confirmHeld=event.getAction()==KeyEvent.ACTION_DOWN&&appButtons.contains(activity.getCurrentFocus());
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){if(overlay.getVisibility()==VISIBLE)closeOverlay();else if(sidebarOpen)leaveSidebar();else activeSide().requestFocus();}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT&&overlay.getVisibility()!=VISIBLE&&!sidebarOpen){View focus=activity.getCurrentFocus();if(focus==play||focus==libraryChooser||firstPoster(focus)){if(event.getAction()==KeyEvent.ACTION_DOWN)activeSide().requestFocus();return true;}}
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&browsing()&&!sidebarOpen&&overlay.getVisibility()!=VISIBLE&&!cards.isEmpty()&&activity.getCurrentFocus()==cards.get(cards.size()-1)&&catalogPage!=null&&catalogPage.hasNext()){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&!catalogLoading)loadCatalog(catalogSection.key,catalogPage.nextOffset(),true);return true;
        }
        return false;
    }
}
