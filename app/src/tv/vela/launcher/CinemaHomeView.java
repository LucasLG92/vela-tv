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
    private final FrameLayout stage,wallpaper,surface,sharedHeader,sidebar,overlay;private final View pageBackdrop;private VelaHub toolsLayer;private CharSequence toolsHeading;private final Map<View,Integer> toolsHidden=new IdentityHashMap<>();
    private final View scrim,appsDivider,backdropShade,backdropLeft,backdropBottom;
    private final ImageView backdrop,titleLogo,providerMark;private final TextView appsHeading;
    private final FrameLayout account;
    private final ImageView accountIcon;
    private final TextView accountTitle,accountInitial;
    private final TextView pageTitle,title,meta,summary,rowNote,connection,play,link,serverLabel,reorderHint;
    private final HomeStatusView headerStatus;
    private final TextView updateHint;
    private final Updates.Listener updateListener=()->updateAvailable();
    private final TextView libraryChooser,pagePrevious,pageNext,pageStatus,catalogFilter,catalogSort;
    private CatalogFilterSheet filterSheet;
    private CatalogFilters.Query catalogQuery=new CatalogFilters.Query();
    private final Map<String,CatalogFilters.Query> catalogQueries=new HashMap<>();
    private final FrameLayout pager;
    private final ScrollView shelvesScroll;
    private final LinearLayout shelfList;
    private final FrameLayout shortcutMenu;
    private final TextView shortcutTitle,shortcutMove,shortcutRemove;
    private final ScrollView appScroll;
    private final FrameLayout appPanel;
    private InstalledAppsGrid installedApps;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Visuals.worker(1);
    private final LibraryClient plex;
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
    private boolean watchedBusy;
    private TextView watchedStatus,watchedMark;private TextView detailsButton;private Future<?> detailWork,collectionWork;private CinemaImages.Request detailImage;private int detailGeneration,collectionGeneration;private View detailOrigin;private VelaContentDetails detailSheet;private CharSequence detailHeading;private final Map<LibraryCollection,List<PlexClient.Film>> collectionRows=new LinkedHashMap<>();private String collectionSignature="";private long collectionAt;
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
    private List<PlexClient.Film> films=new ArrayList<>(),youtube=new ArrayList<>(),watchNext=new ArrayList<>();
    private int watchGeneration;
    private PlexClient.Film selected;
    private View lastContent,overlayReturn;
    private Bitmap heroBitmap;
    private boolean active,loading,closed,sidebarOpen;
    private int filter,imageGeneration,posterGeneration;
    private HomeLiveTvView liveTv;
    private boolean livePage;
    private HomeScreenHost panels;
    private boolean panelReturnLive;
    private long refreshedAt;
    private String displayedServer="";
    private String heroKey="";
    private String appearanceKey="";
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(closed||!((HomeActivity)activity).foreground())return;
        if(headerStatus.getVisibility()==VISIBLE)headerStatus.updateClock();
        if(active&&plex.connected() && SystemClock.elapsedRealtime()-refreshedAt>300_000)refresh();
        main.postDelayed(this,60_000-System.currentTimeMillis()%60_000);
    }};

    CinemaHomeView(Activity activity){
        super(activity);this.activity=activity;plex=new LibraryClient(activity);images=new CinemaImages(activity,plex);
        setBackgroundColor(BG);setClipChildren(false);
        stage=new FrameLayout(activity);stage.setClipChildren(false);addView(stage,new LayoutParams(1920,1080));
        wallpaper=new FrameLayout(activity);stage.addView(wallpaper,new LayoutParams(1920,1080));
        pageBackdrop=new View(activity);pageBackdrop.setBackground(VelaTheme.canvas());pageBackdrop.setVisibility(INVISIBLE);stage.addView(pageBackdrop,new LayoutParams(1920,1080));
        surface=new FrameLayout(activity);surface.setClipChildren(false);stage.addView(surface,new LayoutParams(1920,1080));
        sharedHeader=new FrameLayout(activity);stage.addView(sharedHeader,new LayoutParams(1920,1080));
        backdrop=new ImageView(activity);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setCropToPadding(true);place(backdrop,96,0,1824,1080);
        backdropLeft=new View(activity);backdropLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{BG,0xCF090A0D,0x00090A0D}));place(backdropLeft,96,0,1440,1080);
        backdropBottom=new View(activity);backdropBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00090A0D,0xB8090A0D,BG}));place(backdropBottom,96,535,1824,545);
        backdropShade=new View(activity);backdropShade.setBackgroundColor(BG);place(backdropShade,96,0,1824,1080);
        View top=new View(activity);top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xCF090A0D,0x00090A0D}));place(top,96,0,1824,240);
        // Reparent the existing image and fades; never scale or duplicate the wallpaper.
        for(View layer:new View[]{backdrop,backdropLeft,backdropBottom,backdropShade,top}){android.view.ViewGroup.LayoutParams bounds=layer.getLayoutParams();surface.removeView(layer);wallpaper.addView(layer,bounds);}
        pageTitle=text(L10n.text("Home"),32,WHITE,Ui.displaySemibold);place(pageTitle,190,56,540,48);
        headerStatus=new HomeStatusView(activity);place(headerStatus,800,42,1040,72);
        updateHint=text("",17,DIM,Ui.medium);updateHint.setSingleLine();updateHint.setEllipsize(android.text.TextUtils.TruncateAt.END);updateHint.setVisibility(GONE);place(updateHint,194,104,540,22);
        providerMark=new ImageView(activity);providerMark.setScaleType(ImageView.ScaleType.FIT_START);providerMark.setImageDrawable(brand(0));place(providerMark,194,218,80,38);
        connection=text(L10n.text("Your library"),17,0xFFD2D2DC,Ui.medium);connection.setSingleLine();connection.setEllipsize(android.text.TextUtils.TruncateAt.END);place(connection,292,218,700,38);
        title=text(L10n.text("Your cinema starts here."),54,WHITE,Ui.displaySemibold);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setLineSpacing(0,.95f);place(title,190,280,860,136);
        titleLogo=new ImageView(activity);titleLogo.setScaleType(ImageView.ScaleType.FIT_START);titleLogo.setVisibility(GONE);place(titleLogo,194,280,650,136);
        meta=text("Plex · Jellyfin · YouTube · Prime Video",19,0xFFD1D1DA,Ui.medium);meta.setSingleLine();meta.setEllipsize(android.text.TextUtils.TruncateAt.END);place(meta,194,426,800,34);
        summary=text(L10n.text("Connect Plex to discover your library."),21,0xFFE1E1E6,Ui.regular);summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setLineSpacing(5,1);place(summary,194,470,760,70);
        play=button(L10n.text("Open Plex"),21);Drawable arrow=symbol("play",22);if(arrow!=null)arrow.setTint(BG);play.setCompoundDrawables(arrow,null,null,null);play.setCompoundDrawablePadding(8);place(play,194,574,256,60);
        play.setTextColor(BG);play.setBackground(round(Appearance.accent(activity),30));play.setOnFocusChangeListener((v,f)->{if(f){lastContent=v;setSidebar(false);}play.setBackground(round(Appearance.accent(activity),30));lift(v,f?1.035f:1,f?-2:0,f?16:0);});play.setOnClickListener(v->openFilm());
        link=button("⋯",29);link.setPadding(0,0,0,0);link.setSingleLine(true);link.setContentDescription(Ui.tr("Opções de biblioteca","Library options","Opciones de biblioteca"));place(link,468,574,60,60);link.setBackground(round(0x50FFFFFF,30));
        link.setOnFocusChangeListener((v,f)->{if(f){lastContent=v;setSidebar(false);}link.setTextColor(f?BG:WHITE);link.setBackground(round(f?WHITE:0x50FFFFFF,30));lift(v,f?1.06f:1,0,f?14:0);});
        link.setOnClickListener(v->{if(filter==3)enableYouTube();else manageLibrary();});
        detailsButton=button(L10n.text("Details"),21);detailsButton.setSingleLine(true);detailsButton.setEllipsize(android.text.TextUtils.TruncateAt.END);place(detailsButton,548,574,180,60);detailsButton.setOnClickListener(v->{if(selected!=null)showWatchedMenu(selected,detailsButton);});
        rowNote=text(L10n.text("Your posters appear when you connect Plex."),20,DIM,Ui.regular);place(rowNote,194,805,1420,60);
        shelvesScroll=new ScrollView(activity){
            // Focus moves whole shelves; Android's rectangle scrolling can hide their heading.
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){return 0;}
            @Override public boolean requestChildRectangleOnScreen(View child,Rect rect,boolean immediate){return false;}
        };shelvesScroll.setFocusable(false);shelvesScroll.setVerticalScrollBarEnabled(false);shelvesScroll.setSmoothScrollingEnabled(false);shelvesScroll.setClipToPadding(true);shelvesScroll.setClipChildren(true);
        shelfList=new LinearLayout(activity);shelfList.setOrientation(LinearLayout.VERTICAL);shelvesScroll.addView(shelfList,new ScrollView.LayoutParams(-1,-2));place(shelvesScroll,182,724,1658,356);
        shelvesScroll.getViewTreeObserver().addOnScrollChangedListener(()->schedulePosters());
        pager=new FrameLayout(activity);place(pager,194,680,1646,52);
        libraryChooser=button("",21);libraryChooser.setSingleLine(true);libraryChooser.setEllipsize(android.text.TextUtils.TruncateAt.END);pager.addView(libraryChooser,new LayoutParams(360,48));libraryChooser.setOnClickListener(v->showLibrarySections());
        catalogFilter=button(Ui.tr("Filtros ▾","Filters ▾","Filtros ▾"),20);LayoutParams fp=new LayoutParams(200,48);fp.leftMargin=376;pager.addView(catalogFilter,fp);catalogFilter.setOnClickListener(v->showCatalogFilters(false));
        catalogSort=button("",20);catalogSort.setSingleLine();catalogSort.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams so=new LayoutParams(300,48);so.leftMargin=592;pager.addView(catalogSort,so);catalogSort.setOnClickListener(v->showCatalogFilters(true));
        pageStatus=text("",20,DIM,Ui.medium);pageStatus.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LayoutParams ps=new LayoutParams(310,48);ps.leftMargin=914;pager.addView(pageStatus,ps);
        pagePrevious=button(Ui.tr("‹ Anterior","‹ Previous","‹ Anterior"),20);LayoutParams pp=new LayoutParams(172,48);pp.leftMargin=1250;pager.addView(pagePrevious,pp);pagePrevious.setOnClickListener(v->{if(!catalogLoading&&catalogPage!=null&&catalogPage.offset>0)loadCatalog(catalogSection.key,Math.max(0,catalogPage.offset-PlexClient.PAGE_SIZE),true);});
        pageNext=button(Ui.tr("Seguinte ›","Next ›","Siguiente ›"),20);LayoutParams pn=new LayoutParams(202,48);pn.leftMargin=1440;pager.addView(pageNext,pn);pageNext.setOnClickListener(v->{if(catalogLoading)return;if(catalogError)loadCatalog(requestedSection,requestedOffset,true);else if(catalogPage!=null&&catalogPage.hasNext())loadCatalog(catalogSection.key,catalogPage.nextOffset(),true);});
        scrim=new View(activity);scrim.setBackgroundColor(Color.BLACK);scrim.setAlpha(0);stage.addView(scrim,new LayoutParams(1920,1080));
        sidebar=new FrameLayout(activity);sidebar.setClipToOutline(true);sidebar.setBackground(VelaNavigation.surface());sidebar.setElevation(16);
        LayoutParams panel=new LayoutParams(76,1032);panel.leftMargin=24;panel.topMargin=24;stage.addView(sidebar,panel);
        appsHeading=text(L10n.text("Pinned apps"),15,VelaTheme.MUTED,Ui.medium);appsHeading.setLetterSpacing(.08f);appsHeading.setSingleLine();appsHeading.setEllipsize(android.text.TextUtils.TruncateAt.END);appsHeading.setVisibility(GONE);LayoutParams groupTitle=new LayoutParams(284,26);groupTitle.leftMargin=22;groupTitle.topMargin=480;sidebar.addView(appsHeading,groupTitle);
        serverLabel=text("",16,0xFFB8B9C4,Ui.medium);serverLabel.setSingleLine(true);serverLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);serverLabel.setAlpha(0);
        String[] names={L10n.text("Home"),L10n.text("Movies"),"Series",L10n.text("Library"),L10n.text("All Apps"),L10n.text("HDMI inputs"),L10n.text("Live TV"),L10n.text("Settings")};
        String[] icons={"home","play","tv","apps","apps","hdmi","live_tv","settings"};
        int[] y={138,270,336,402,822,888,204,954};
        for(int i=0;i<names.length;i++){
            final int index=i;FrameLayout nav=new FrameLayout(activity);focusable(nav,names[i]);
            LayoutParams p=new LayoutParams(60,56);p.leftMargin=8;p.topMargin=y[i];sidebar.addView(nav,p);sideButtons.add(nav);
            ImageView icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(i==1)icon.setImageResource(R.drawable.ic_movie);
            else if(i==3)icon.setImageResource(R.drawable.ic_video_library);
            else icon.setImageDrawable(symbol(icons[i],27));
            LayoutParams ip=new LayoutParams(27,30,Gravity.CENTER_VERTICAL);ip.leftMargin=16;nav.addView(icon,ip);sideIcons.add(icon);
            TextView label=text(i==4?L10n.text("Apps"):names[i],21,WHITE,Ui.medium);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setAlpha(0);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams lp=new LayoutParams(VelaNavigation.LABEL,56);lp.leftMargin=64;nav.addView(label,lp);sideLabels.add(label);
            nav.setOnFocusChangeListener((v,f)->{if(f)setSidebar(true);styleSide(index,f);});nav.setOnClickListener(v->activateSide(index));
            nav.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN){if(activeSide()==v)leaveSidebar();else activateSide(index);}return true;}return false;});styleSide(i,false);
        }
        // Stable destination IDs, explicit remote order: Home, live TV, movies, series, library.
        int[] primaryOrder={0,6,1,2,3};for(int n=0;n+1<primaryOrder.length;n++){View before=sideButtons.get(primaryOrder[n]),after=sideButtons.get(primaryOrder[n+1]);before.setNextFocusDownId(after.getId());after.setNextFocusUpId(before.getId());after.setAccessibilityTraversalAfter(before.getId());}
        sideButtons.get(4).setNextFocusDownId(sideButtons.get(5).getId());sideButtons.get(5).setNextFocusUpId(sideButtons.get(4).getId());sideButtons.get(5).setNextFocusDownId(sideButtons.get(7).getId());sideButtons.get(7).setNextFocusUpId(sideButtons.get(5).getId());
        appScroll=new ScrollView(activity);appScroll.setFocusable(false);appScroll.setVerticalScrollBarEnabled(true);appScroll.setClipToPadding(true);appScroll.setClipBounds(new Rect(0,0,VelaNavigation.DRAWER,292));appScroll.setFillViewport(true);
        LayoutParams asp=new LayoutParams(-1,292);asp.topMargin=516;sidebar.addView(appScroll,asp);
        appPanel=new FrameLayout(activity);appPanel.setClipChildren(false);appScroll.addView(appPanel,new ScrollView.LayoutParams(-1,288));
        account=new FrameLayout(activity);focusable(account,"");LayoutParams ap=new LayoutParams(VelaNavigation.ROW,80);ap.leftMargin=8;ap.topMargin=24;sidebar.addView(account,ap);
        accountIcon=new ImageView(activity);accountIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);accountIcon.setBackground(round(0x33FFFFFF,22));accountIcon.setClipToOutline(true);accountIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams ai=new LayoutParams(42,42,Gravity.CENTER_VERTICAL);ai.leftMargin=9;account.addView(accountIcon,ai);
        accountInitial=text("",21,WHITE,Ui.medium);accountInitial.setGravity(Gravity.CENTER);accountInitial.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);account.addView(accountInitial,new LayoutParams(ai));
        accountTitle=text("",21,WHITE,Ui.displaySemibold);accountTitle.setSingleLine(true);accountTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);accountTitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);accountTitle.setAlpha(0);LayoutParams at=new LayoutParams(VelaNavigation.LABEL,32);at.leftMargin=74;at.topMargin=5;account.addView(accountTitle,at);LayoutParams serverPos=new LayoutParams(VelaNavigation.LABEL,26);serverPos.leftMargin=74;serverPos.topMargin=44;account.addView(serverLabel,serverPos);
        account.setOnFocusChangeListener((v,f)->{if(f)setSidebar(true);styleProfile();});account.setOnClickListener(v->manageLibrary());sideButtons.get(0).setOnLongClickListener(v->{((VelaHub.Owner)activity).toolsLayout();return true;});account.setOnLongClickListener(v->{((VelaHub.Owner)activity).tools(4);return true;});
        account.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)leaveSidebar();return true;}return false;});
        // The account heading follows the same reveal as the sidebar labels.
        sideLabels.add(accountTitle);
        updateAccount();
        appsDivider=new View(activity);appsDivider.setBackgroundColor(0x25FFFFFF);LayoutParams dp=new LayoutParams(40,1);dp.leftMargin=18;dp.topMargin=488;sidebar.addView(appsDivider,dp);
        reorderHint=text("",22,WHITE,Ui.medium);reorderHint.setPadding(26,0,26,0);reorderHint.setBackground(round(0xF0262831,24));reorderHint.setVisibility(GONE);reorderHint.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);LayoutParams rh=new LayoutParams(1320,64);rh.leftMargin=360;rh.topMargin=128;stage.addView(reorderHint,rh);
        shortcutMenu=new FrameLayout(activity);shortcutMenu.setBackground(round(0xFA262831,26));shortcutMenu.setElevation(28);shortcutMenu.setVisibility(GONE);LayoutParams sm=new LayoutParams(940,224);sm.leftMargin=360;sm.topMargin=128;stage.addView(shortcutMenu,sm);
        shortcutTitle=text("",27,WHITE,Ui.displaySemibold);LayoutParams st=new LayoutParams(888,40);st.leftMargin=26;st.topMargin=17;shortcutMenu.addView(shortcutTitle,st);
        shortcutMove=button(Ui.tr("Mover","Move","Mover"),22);shortcutRemove=button(Ui.tr("Remover da barra","Remove from sidebar","Quitar de la barra"),22);
        TextView[] actions={shortcutMove,shortcutRemove};for(int i=0;i<actions.length;i++){
            final int action=i;TextView item=actions[i];LayoutParams p=new LayoutParams(438,62);p.leftMargin=24+i*454;p.topMargin=70;shortcutMenu.addView(item,p);
            item.setOnFocusChangeListener((v,f)->{if(f){menuAction=action;styleShortcutMenu();}});item.setOnClickListener(v->{if(menuPackage!=null){if(action==0)moveFromShortcutMenu();else removeFromShortcutMenu();}});
        }
        TextView controls=text(Ui.tr("←→ Escolher · OK Aceitar · ↑↓ Mover · Voltar Cancelar","←→ Choose · OK Select · ↑↓ Move · Back Cancel","←→ Elegir · OK Aceptar · ↑↓ Mover · Atrás Cancelar"),19,0xFFD1D8E1,Ui.medium);LayoutParams sc=new LayoutParams(888,30);sc.leftMargin=26;sc.topMargin=144;shortcutMenu.addView(controls,sc);
        TextView explanation=text(Ui.tr("Remover o acesso mantém o app instalado.","Removing the shortcut keeps the app installed.","Al quitar el acceso, la aplicación sigue instalada."),18,DIM,Ui.regular);LayoutParams se=new LayoutParams(888,28);se.leftMargin=26;se.topMargin=180;shortcutMenu.addView(explanation,se);
        overlay=new FrameLayout(activity);overlay.setBackgroundColor(0xFD0C0D12);overlay.setElevation(64);overlay.setVisibility(GONE);stage.addView(overlay,new LayoutParams(1920,1080));
        headerStatus.host(new VelaWidgets.Strip.Host(){public void activate(String id){if(id.equals("apps"))showApps(play);else if(id.equals("inputs"))showInputs(play);else if(id.equals("tv"))navigateSection(6);else if(id.equals("programme"))((VelaHub.Owner)activity).widgetProgramme();else ((VelaHub.Owner)activity).tools(id.equals("weather")?VelaHub.WEATHER:VelaHub.WIDGETS);}public void focused(){setSidebar(false);}public void navigation(){activeSide().requestFocus();}});headerStatus.returnTo(play);
        for(View action:new View[]{play,link,detailsButton})action.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_UP){if(e.getAction()==KeyEvent.ACTION_DOWN){headerStatus.returnTo(v);return headerStatus.focusFirst();}return headerStatus.hasFocus();}return false;});
        refreshSidebarApps();films=plex.cached();showFilms();play.requestFocus();lastContent=play;
    }

    private TextView text(String s,int size,int color,Typeface face){TextView v=new TextView(activity);v.setText(s);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(face);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void focusable(View v,String description){v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);v.setContentDescription(description);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView button(String s,int size){TextView v=text(s,size,WHITE,Ui.medium);focusable(v,s);v.setContentDescription(null);v.setGravity(Gravity.CENTER);v.setPadding(20,6,20,6);v.setBackground(VelaTheme.shape(VelaTheme.CARD,VelaTheme.CONTROL_RADIUS));v.setOnFocusChangeListener((view,f)->{v.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);v.setBackground(VelaTheme.shape(f?Appearance.accent(activity):VelaTheme.CARD,VelaTheme.CONTROL_RADIUS));lift(v,f?1.02f:1,0,f?8:0);});return v;}
    private Drawable symbol(String name,int size){try(java.io.InputStream in=activity.getAssets().open("symbols/"+name+".png")){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)return null;Drawable d=new BitmapDrawable(getResources(),b);d.setBounds(0,0,size,size);d.setTint(0xFFE1E1E8);return d;}catch(java.io.IOException ignored){return null;}}
    private Drawable brand(int index){
        if(index==0)return activity.getDrawable(R.drawable.logo_plex);int b=index-1;
        if(brandBitmaps[b]==null)try(java.io.InputStream in=activity.getAssets().open("brands/"+(index==1?"youtube":"prime")+".png")){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;brandBitmaps[b]=BitmapFactory.decodeStream(in,null,o);}catch(java.io.IOException ignored){}
        Bitmap bitmap=brandBitmaps[b];if(bitmap==null)return null;if(index!=1)return new BitmapDrawable(getResources(),bitmap);
        // Ignore the official file's transparent canvas without allocating a second bitmap.
        Rect source=new Rect(Math.round(bitmap.getWidth()*204f/1705),Math.round(bitmap.getHeight()*141f/573),Math.round(bitmap.getWidth()*1498f/1705),Math.round(bitmap.getHeight()*430f/573));
        return new Drawable(){final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
            @Override public void draw(Canvas canvas){canvas.drawBitmap(bitmap,source,getBounds(),paint);}
            @Override public int getIntrinsicWidth(){return source.width();}
            @Override public int getIntrinsicHeight(){return source.height();}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
            @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    private void place(View v,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;surface.addView(v,p);}
    private void lift(View v,float scale,float y,float z){VelaFocus.card(v,scale>1);}

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){float safe=1-2*VelaReading.margin(activity);float s=Math.min(w*safe/1920f,h*safe/1080f);stage.setPivotX(0);stage.setPivotY(0);stage.setScaleX(s);stage.setScaleY(s);stage.setTranslationX((w-1920*s)/2);stage.setTranslationY((h-1080*s)/2);}
    private View activeSide(){if(toolsLayer!=null)return sideButtons.get(toolsLayer.page()==8?6:7);if(panels!=null)return sideButtons.get(panels.navigationIndex());if(livePage)return sideButtons.get(6);if(filter!=3)return sideButtons.get(filter==4?3:filter);for(int i=0;i<sidePackages.length;i++)if(SidebarApps.YOUTUBE.equals(sidePackages[i])&&appButtons.get(i).getVisibility()==VISIBLE)return appButtons.get(i);return sideButtons.get(0);}
    private void styleSide(int i,boolean focused){boolean selected=toolsLayer!=null?i==(toolsLayer.page()==8?6:7):panels!=null?i==panels.navigationIndex():livePage?i==6:filter!=3&&i==(filter==4?3:filter);VelaNavigation.style(sideButtons.get(i),focused,selected,sidebarOpen);int color=VelaNavigation.foreground(focused,selected);sideLabels.get(i).setTextColor(color);sideIcons.get(i).setColorFilter(color);}
    private void styleApp(int slot,boolean focused){if(slot<0)return;boolean moving=reorderOriginal!=null&&slot==reorderSlot;boolean context=sidePackages[slot].equals(menuPackage);boolean selected=toolsLayer==null&&panels==null&&!livePage&&filter==3&&SidebarApps.YOUTUBE.equals(sidePackages[slot]);VelaNavigation.style(appButtons.get(slot),focused||moving,selected||context,sidebarOpen);appLabels.get(slot).setTextColor(VelaNavigation.foreground(focused||moving,selected||context));appButtons.get(slot).setTranslationZ(moving?16:0);}
    private void styleProfile(){boolean focus=account.hasFocus();VelaNavigation.style(account,focus,false,sidebarOpen);accountTitle.setTextColor(focus?VelaTheme.INK:WHITE);serverLabel.setTextColor(focus?0xFF626773:VelaTheme.SECONDARY);accountInitial.setTextColor(focus?VelaTheme.INK:WHITE);accountIcon.setBackground(round(focus?0x18000000:0x33FFFFFF,22));}
    private void styleSidebar(){boolean homeCanvas=toolsLayer==null&&panels==null&&!livePage;wallpaper.setVisibility(homeCanvas?VISIBLE:INVISIBLE);pageBackdrop.setVisibility(homeCanvas?INVISIBLE:VISIBLE);for(int i=0;i<sideButtons.size();i++)styleSide(i,sideButtons.get(i).hasFocus());for(int i=0;i<appButtons.size();i++)styleApp(i,appButtons.get(i).hasFocus());styleProfile();boolean apps=false;for(View button:appButtons)apps|=button.getVisibility()==VISIBLE;appsHeading.setVisibility(apps&&sidebarOpen?VISIBLE:GONE);appsDivider.setVisibility(apps&&!sidebarOpen?VISIBLE:GONE);}
    private void setSidebar(boolean open){if(sidebarOpen==open)return;if(open&&toolsLayer!=null){scrim.bringToFront();sidebar.bringToFront();overlay.bringToFront();}sidebarOpen=open;VelaNavigation.preview(surface,open);VelaNavigation.preview(sharedHeader,open);VelaNavigation.preview(panels,open);VelaNavigation.preview(liveTv,open);VelaNavigation.preview(toolsLayer,open);LayoutParams p=(LayoutParams)sidebar.getLayoutParams();p.width=open?VelaNavigation.DRAWER:VelaNavigation.RAIL;sidebar.setLayoutParams(p);for(View button:sideButtons)VelaNavigation.width(button,open);for(View button:appButtons)VelaNavigation.width(button,open);VelaNavigation.width(account,open);for(View label:sideLabels)VelaNavigation.reveal(label,open);for(View label:appLabels)VelaNavigation.reveal(label,open);VelaNavigation.reveal(serverLabel,open);scrim.animate().alpha(open?.24f:0f).setDuration(Visuals.duration(activity,140)).start();styleSidebar();}
    private void leaveSidebar(){setSidebar(false);if(toolsLayer!=null){toolsLayer.focusNavigation();return;}if(panels!=null){panels.focusContent();return;}if(livePage){liveTv.focusContent();return;}View v=lastContent;if(v==null||!v.isAttachedToWindow()||v.getVisibility()!=VISIBLE)v=play;v.requestFocus();}
    void mountTools(VelaHub tools){sharedChrome();toolsLayer=tools;toolsHeading=pageTitle.getText();toolsHidden.clear();for(View view:new View[]{surface,panels,liveTv,overlay})if(view!=null){toolsHidden.put(view,view.getVisibility());view.setVisibility(INVISIBLE);}pageTitle.setText(L10n.text(tools.page()==8?"Live TV":"Personalisation"));updateHint.setVisibility(GONE);LayoutParams bounds=new LayoutParams(1790,940);bounds.leftMargin=112;bounds.topMargin=116;stage.addView(tools,stage.indexOfChild(scrim),bounds);sidebar.setVisibility(VISIBLE);setSidebar(false);startHeader();styleSidebar();}
    void unmountTools(){toolsLayer=null;for(Map.Entry<View,Integer> item:toolsHidden.entrySet())item.getKey().setVisibility(item.getValue());toolsHidden.clear();if(toolsHeading!=null)pageTitle.setText(toolsHeading);toolsHeading=null;styleSidebar();}
    void resumeTools(){if(toolsLayer!=null)startHeader();}
    void toolsContent(){setSidebar(false);}
    boolean focusHeader(View from){headerStatus.returnTo(from);return headerStatus.focusFirst();}
    void toolsTitle(String title){if(toolsLayer!=null){pageTitle.setText(title);styleSidebar();}}
    void toolsNavigation(){sideButtons.get(toolsLayer!=null&&toolsLayer.page()==8?6:7).requestFocus();}
    int navigationDestination(){if(panels!=null)return panels.navigationIndex();if(livePage)return 6;return filter==1?1:filter==2?2:filter==4?3:0;}
    void navigateSection(int destination){activateSide(destination);}
    private void activateSide(int i){
        if(toolsLayer!=null){((HomeActivity)activity).afterTools(v->v.activateSide(i));return;}if(i==7&&VelaSessions.blocked(activity)){((VelaHub.Owner)activity).tools(4);return;}
        if(i<=3){if(panels!=null){closePanel(false);resume();}if(livePage){leaveLiveTv();resume();}changeFilter(i==3?4:i);setSidebar(false);play.requestFocus();return;}
        if(i==4)showApps(sideButtons.get(i));else if(i==5)showInputs(sideButtons.get(i));else if(i==6)openLiveTv();else openPanel(new Intent(activity,SettingsActivity.class));
    }
    private void openLiveTv(){
        if(panels!=null)closePanel(false);
        if(livePage){setSidebar(false);liveTv.focusContent();return;}
        pause();releaseImages();surface.setVisibility(INVISIBLE);livePage=true;updateHint.setVisibility(GONE);pageTitle.setText(Ui.tr("TV em direto","Live TV","TV en directo"));
        // The same header and navigation stay attached; TV is a lazy section in this HomeActivity.
        sharedChrome();
        liveTv=new HomeLiveTvView(activity,this,new HomeLiveTvView.Host(){
            public void focusNavigation(){sideButtons.get(6).requestFocus();}
            public void contentFocused(View view){lastContent=view;setSidebar(false);}
            public boolean ownsFocus(){return getVisibility()==VISIBLE&&!sidebarOpen;}
            public boolean header(View from){if(focusHeader(from))return true;focusNavigation();return true;}
            public void modal(boolean open){sidebar.setDescendantFocusability(open?FOCUS_BLOCK_DESCENDANTS:FOCUS_AFTER_DESCENDANTS);}
        });stage.addView(liveTv,stage.indexOfChild(sharedHeader),new LayoutParams(1920,1080));startHeader();setSidebar(false);styleSidebar();liveTv.resume();
    }
    private void leaveLiveTv(){
        if(!livePage)return;livePage=false;HomeLiveTvView old=liveTv;liveTv=null;old.destroy();stage.removeView(old);surface.setVisibility(VISIBLE);pageTitle.setText(filter==0?L10n.text("Home"):filter==1?L10n.text("Movies"):filter==2?"Series":L10n.text("Library"));lastContent=play;styleSidebar();
    }
    private boolean sidebarFocused(){View focus=activity.getCurrentFocus();for(View v=focus;v!=null;){if(v==sidebar)return true;android.view.ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}return false;}
    boolean route(Intent intent){if(!HomeScreenHost.handles(intent))return false;if(panels==null)openPanel(intent);else panels.open(panels.screen,intent);return true;}
    void settings(){if(VelaSessions.blocked(activity)){((VelaHub.Owner)activity).tools(4);return;}openPanel(new Intent(activity,SettingsActivity.class));}
    void searchChannel(LiveTvData.Channel channel){openLiveTv();if(liveTv!=null)liveTv.openSearch(channel);}
    boolean showingUpdates(){return panels!=null&&panels.showingUpdates();}
    boolean canShowInstaller(){return panels!=null&&panels.updatesActive();}
    private void startHeader(){headerStatus.start();main.removeCallbacks(tick);main.post(tick);}
    private void sharedChrome(){for(View chrome:new View[]{pageTitle,headerStatus,updateHint})if(chrome.getParent()==surface){LayoutParams p=(LayoutParams)chrome.getLayoutParams();surface.removeView(chrome);sharedHeader.addView(chrome,p);}}
    private void openPanel(Intent intent){openPanel(intent,null);}
    private void openPanel(Intent intent,Bundle saved){
        if(panels!=null){panels.root(intent);setSidebar(false);styleSidebar();return;}
        closeOverlay();closeShortcutMenu(false);finishReorder(false);panelReturnLive=livePage;pause();if(livePage)leaveLiveTv();releaseImages();surface.setVisibility(INVISIBLE);sharedChrome();updateHint.setVisibility(GONE);
        panels=new HomeScreenHost(activity,new HomeScreenHost.Actions(){
            public void navigation(){sideButtons.get(panels.navigationIndex()).requestFocus();}
            public View navigationTarget(){return sideButtons.get(panels.navigationIndex());}
            public void contentFocused(){setSidebar(false);styleSidebar();}
            public void changed(){refreshSidebarApps();styleSidebar();if(((HomeActivity)activity).foreground()){headerStatus.stop();startHeader();}}
            public void title(String text){pageTitle.setText(text);refreshSidebarApps();styleSidebar();}
            public void fullscreen(boolean enabled){sidebar.setVisibility(enabled?GONE:VISIBLE);pageTitle.setVisibility(enabled?GONE:VISIBLE);headerStatus.setVisibility(enabled?GONE:VISIBLE);if(enabled)headerStatus.stop();else if(!closed&&((HomeActivity)activity).foreground())startHeader();}
            public void exit(){boolean tv=panelReturnLive;closePanel(false);if(tv)openLiveTv();else resume();}
        });stage.addView(panels,stage.indexOfChild(sharedHeader),new LayoutParams(1920,1080));setSidebar(false);
        if(saved==null)panels.root(intent);else panels.restore(saved);if(((HomeActivity)activity).foreground())panels.resume();startHeader();styleSidebar();
    }
    private void closePanel(boolean restore){if(panels==null)return;HomeScreenHost old=panels;old.destroy();stage.removeView(old);panels=null;surface.setVisibility(VISIBLE);sidebar.setVisibility(VISIBLE);pageTitle.setVisibility(VISIBLE);headerStatus.setVisibility(VISIBLE);pageTitle.setText(filter==0?L10n.text("Home"):filter==1?L10n.text("Movies"):filter==2?"Series":filter==3?"YouTube":L10n.text("Library"));styleSidebar();if(restore)resume();}
    Bundle savePanels(){if(panels==null)return null;Bundle out=new Bundle();out.putBundle("screens",panels.save());out.putBoolean("returnLive",panelReturnLive);out.putInt("filter",filter);return out;}
    void restorePanels(Bundle saved){if(saved==null)return;filter=saved.getInt("filter",0);openPanel(null,saved.getBundle("screens"));panelReturnLive=saved.getBoolean("returnLive");}
    boolean permissionResult(int request,String[] names,int[] results){return panels!=null&&panels.permissionResult(request,names,results);}
    void activityResult(int request,int result,Intent data){if(panels!=null)panels.activityResult(request,result,data);}
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
            LayoutParams position=new LayoutParams(sidebarOpen?VelaNavigation.OPEN_ROW:VelaNavigation.ROW,56);position.leftMargin=8;position.topMargin=12+66*visible;appPanel.addView(button,position);button.setVisibility(available[slot]?VISIBLE:GONE);
            if(available[slot]){visible++;focusOrder.add(button);}
            ImageView icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(available[slot])icon.setImageDrawable(SidebarApps.icon(activity,pkg));LayoutParams ip=new LayoutParams(38,30,Gravity.CENTER_VERTICAL);ip.leftMargin=11;button.addView(icon,ip);appIcons.add(icon);
            TextView label=text(name,21,WHITE,Ui.medium);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setAlpha(sidebarOpen?1:0);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LayoutParams lp=new LayoutParams(VelaNavigation.LABEL,56);lp.leftMargin=64;button.addView(label,lp);appLabels.add(label);
            button.setOnFocusChangeListener((v,f)->{if(f)setSidebar(true);styleApp(appButtons.indexOf(v),f);});
            button.setOnLongClickListener(v->openShortcutMenu(v));
            button.setOnClickListener(v->{if(reorderOriginal!=null||menuPackage!=null)return;openPackage(pkg,name);});
            button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(v.hasFocus()&&sidebarOpen)v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});
            button.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(SidebarApps.YOUTUBE.equals(pkg))openYouTubeContent();else leaveSidebar();}return true;}
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
        if(closed||!((HomeActivity)activity).foreground()||slot<0||!availableApps[slot])return false;
        if(reorderOriginal!=null)return true;
        reorderOriginal=sidePackages.clone();reorderSlot=slot;consumeConfirmUp=confirmHeld;reorderConfirmArmed=false;
        view.cancelLongPress();view.setPressed(false);setSidebar(true);view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        reorderHint.setVisibility(VISIBLE);updateReorderHint();styleSidebar();return true;
    }
    private boolean openShortcutMenu(View view){
        int slot=appButtons.indexOf(view);if(toolsLayer!=null&&slot>=0){String pkg=sidePackages[slot];((HomeActivity)activity).afterTools(v->{for(int n=0;n<v.sidePackages.length;n++)if(pkg.equals(v.sidePackages[n])){v.openShortcutMenu(v.appButtons.get(n));break;}});return true;}if(closed||!((HomeActivity)activity).foreground()||slot<0||!availableApps[slot])return false;
        if(reorderOriginal!=null||menuPackage!=null)return true;
        menuPackage=sidePackages[slot];menuSnapshot=sidePackages.clone();menuAction=0;menuConfirmArmed=false;consumeConfirmUp=confirmHeld;
        view.cancelLongPress();view.setPressed(false);view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);setSidebar(true);
        shortcutTitle.setText(SidebarApps.label(activity,menuPackage));shortcutMenu.setVisibility(VISIBLE);shortcutMove.requestFocus();styleShortcutMenu();styleSidebar();return true;
    }
    private void styleShortcutMenu(){
        TextView[] items={shortcutMove,shortcutRemove};for(int i=0;i<items.length;i++){items[i].setBackground(round(i==menuAction?WHITE:VelaTheme.RAISED,20));items[i].setTextColor(i==menuAction?BG:WHITE);}
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
    private void openYouTubeContent(){if(toolsLayer!=null){((HomeActivity)activity).afterTools(v->v.openYouTubeContent());return;}if(panels!=null){closePanel(false);resume();}if(livePage){leaveLiveTv();resume();}changeFilter(3);setSidebar(false);play.requestFocus();loadYouTube();}
    private void enableYouTube(){if(activity.checkSelfPermission(TvSuggestions.PERMISSION)!=PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{TvSuggestions.PERMISSION},80);else loadYouTube();}
    private void updateAccount(){
        String name=plex.connected()?plex.name():Ui.tr("Conectar biblioteca","Connect library","Conectar biblioteca");
        accountTitle.setText(plex.connected()?name:L10n.text("Library"));accountInitial.setText(name.isEmpty()?"P":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(Ui.locale()));
        serverLabel.setText(plex.connected()?plex.provider()+Ui.tr(" · Conectado"," · Connected"," · Conectado"):Ui.tr("Escolher servidor","Choose server","Elegir servidor"));
        account.setContentDescription(name+" · "+Ui.tr("Alterar biblioteca","Change library","Cambiar biblioteca"));
        if(!active)return;
        String key=plex.profileKey();
        if(key.equals(accountImageKey)&&accountIcon.getDrawable()!=null)return;
        if(key.equals(accountRequestKey)&&avatarRequest!=null&&avatarRequest.pending())return;
        if(avatarRequest!=null)avatarRequest.cancel();
        int epoch=++accountGeneration;accountIcon.setImageDrawable(null);accountInitial.setVisibility(VISIBLE);accountImageKey="";
        if(key.isEmpty())return;
        accountRequestKey=key;
        avatarRequest=images.loadAvatar(bitmap->{if(active&&!closed&&epoch==accountGeneration&&key.equals(plex.profileKey())){accountIcon.setImageBitmap(bitmap);accountInitial.setVisibility(GONE);accountImageKey=key;}});
    }
    private void applyAppearance(){
        String key=Appearance.signature(activity);if(key.equals(appearanceKey))return;appearanceKey=key;
        int shade=Appearance.backdropIndex(activity);backdrop.setAlpha(VelaStyle.minimal(activity)?0f:1f);backdropShade.setAlpha(shade==0?.30f:0);backdropLeft.setAlpha(shade==2?.75f:1);backdropBottom.setAlpha(shade==2?.82f:1);
        boolean large=Appearance.sizeIndex(activity)==2;
        LayoutParams pages=(LayoutParams)pager.getLayoutParams();pages.topMargin=large?626:680;pager.setLayoutParams(pages);
        play.setBackground(round(Appearance.accent(activity),30));styleSidebar();for(int i=0;i<appButtons.size();i++)styleApp(i,appButtons.get(i).hasFocus());showFilms();
    }
    void resume(){
        if(panels!=null){startHeader();refreshSidebarApps();panels.resume();styleSidebar();return;}
        if(livePage){startHeader();refreshSidebarApps();liveTv.resume();return;}
        active=true;
        Updates.observe(updateListener);updateAvailable();Updates.automatic(activity);
        applyAppearance();
        startHeader();
        refreshSidebarApps();
        String id=plex.identity();
        if(!id.equals(displayedServer)){cancelCatalog();catalogError=false;catalogSections=Collections.emptyList();catalogSection=null;catalogPage=null;catalogQueries.clear();catalogQuery=new CatalogFilters.Query();requestedSection="";requestedOffset=0;selected=null;images.clear();displayedServer=id;collectionGeneration++;if(collectionWork!=null)collectionWork.cancel(true);collectionRows.clear();collectionSignature="";films=plex.cached();refreshedAt=0;libraryDirty=false;showFilms();}
        else if(libraryDirty){films=plex.cached();libraryDirty=false;if(!browsing())showFilms();}
        if(artReleased||heroBitmap==null||(selected!=null&&!heroKey.equals(selected.provider+selected.key))){artReleased=false;if(selected!=null)selectFilm(selected);main.post(visiblePosters);}
        updateAccount();loadCollections();main.removeCallbacks(tick);tick.run();
        loadWatchNext();
        updatePager();
        if(plex.connected()&&(films.isEmpty()||SystemClock.elapsedRealtime()-refreshedAt>60_000))refresh();
        if(browsing()&&plex.connected()&&catalogPage==null&&!catalogLoading)loadCatalog(requestedSection,requestedOffset,false);
    }
    void pause(){main.removeCallbacks(tick);if(detailSheet!=null)closeOverlay();cancelDetails();collectionGeneration++;if(collectionWork!=null){collectionWork.cancel(true);collectionWork=null;}if(panels!=null){closeShortcutMenu(false);finishReorder(false);confirmHeld=false;consumeConfirmUp=false;headerStatus.stop();panels.pause();return;}if(livePage){headerStatus.stop();liveTv.pause();return;}if(filterSheet!=null)closeOverlay();active=false;Updates.forget(updateListener);if(installedApps!=null)closeOverlay();accountGeneration++;if(avatarRequest!=null)avatarRequest.cancel();avatarRequest=null;accountRequestKey="";headerStatus.stop();cancelCatalog();closeShortcutMenu(false);finishReorder(false);confirmHeld=false;consumeConfirmUp=false;for(View button:appButtons){button.cancelLongPress();button.setPressed(false);}main.removeCallbacks(tick);main.removeCallbacks(visiblePosters);if(heroLoad!=null)main.removeCallbacks(heroLoad);}
    private void updateAvailable(){
        if(!active)return;ReleaseFeed.Release release=Updates.available(activity);
        updateHint.setVisibility(release==null?GONE:VISIBLE);
        if(release!=null)updateHint.setText("Vela "+release.version+Ui.tr(" disponível · Ajustes → Informações → Atualizações"," available · Settings → Information → Updates"," disponible · Ajustes → Información → Actualizaciones"));
    }
    void releaseImages(){
        imageGeneration++;posterGeneration++;artReleased=true;if(heroLoad!=null)main.removeCallbacks(heroLoad);
        images.clear();heroBitmap=null;heroKey="";backdrop.setImageDrawable(null);titleLogo.setImageDrawable(null);titleLogo.setVisibility(GONE);title.setVisibility(VISIBLE);
        accountGeneration++;avatarRequest=null;accountImageKey="";accountRequestKey="";accountIcon.setImageDrawable(null);accountInitial.setVisibility(VISIBLE);
        for(ImageView image:posterImages)image.setImageDrawable(null);
        for(int i=0;i<posterRequests.size();i++)posterRequests.set(i,null);
        releaseAppsGrid();overlay.removeAllViews();overlay.setVisibility(GONE);overlayReturn=null;
        surface.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);sidebar.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);if(panels!=null)panels.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }
    void trimMemory(){if(panels!=null){panels.trimMemory();releaseImages();return;}if(!active)releaseImages();else{images.trim();if(installedApps!=null)installedApps.trim();}}
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
            PlexClient.Film film=posterFilms.get(i);boolean external=film.tvSource;
            final int slot=i;
            CinemaImages.Ready ready=b->{if(active&&!closed&&epoch==posterGeneration&&slot<posterImages.size()&&posterImages.get(slot)==image&&posterVisible(slot))image.setImageBitmap(b);};
            View picture=(View)image.getParent();int width=picture.getLayoutParams().width,height=picture.getLayoutParams().height;
            String path=filter==0&&film.continuing&&!external&&film.art!=null&&!film.art.isEmpty()?film.art:film.thumb;
            posterRequests.set(i,external?images.loadTv(path,width,height,ready):images.load(path,width,height,ready));
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
    void destroy(){closed=true;pause();if(panels!=null){panels.destroy();panels=null;}if(liveTv!=null){liveTv.destroy();liveTv=null;}images.close();io.shutdownNow();main.removeCallbacksAndMessages(null);}
    void reset(){if(panels!=null){closePanel(false);resume();}if(livePage){leaveLiveTv();resume();}closeShortcutMenu(false);finishReorder(false);closeOverlay();changeFilter(0);setSidebar(false);play.requestFocus();}
    void loadYouTube(){loadWatchNext();io.execute(()->{List<PlexClient.Film> fresh=TvSuggestions.youtube(activity);main.post(()->{if(!closed){youtube=fresh;if(filter==3&&active)showFilms();}});});}
    private void loadWatchNext(){int epoch=++watchGeneration;String linked=plex.connected()?plex.packageName():"";io.execute(()->{List<PlexClient.Film> fresh=ContinueWatching.read(activity,linked);main.post(()->{if(!closed&&active&&epoch==watchGeneration){watchNext=fresh;if(filter==0)showFilms();}});});}
    private void refresh(){if(loading)return;loading=true;refreshedAt=SystemClock.elapsedRealtime();String server=plex.identity();io.execute(()->{try{List<PlexClient.Film> fresh=plex.library();main.post(()->{if(!closed&&!watchedBusy&&server.equals(plex.identity())){films=fresh;if(active&&filter==0){showFilms();loadCollections();}else libraryDirty=true;}});}catch(Exception ignored){main.post(()->{if(!closed&&films.isEmpty()&&!browsing())rowNote.setText(Ui.tr("Não foi possível ler sua biblioteca. Confira o servidor ou conecte novamente.","Could not read your library. Check the server or link again.","No se ha podido leer tu biblioteca. Comprueba el servidor o vuelve a vincularlo."));});}finally{main.post(()->loading=false);}});}

    private void loadCollections(){if(!active||closed||filter!=0||!plex.connected())return;List<LibraryCollection> pins=plex.pinnedCollections();String signature=plex.identity();for(LibraryCollection p:pins)signature+="|"+p.identity();if(signature.equals(collectionSignature)&&SystemClock.elapsedRealtime()-collectionAt<300_000)return;collectionGeneration++;if(collectionWork!=null)collectionWork.cancel(true);collectionRows.clear();final int token=collectionGeneration;final String requested=signature,account=plex.identity();if(pins.isEmpty()){collectionSignature=signature;collectionAt=SystemClock.elapsedRealtime();showFilms();return;}collectionWork=io.submit(()->{Map<LibraryCollection,List<PlexClient.Film>> found=new LinkedHashMap<>();boolean complete=true;for(LibraryCollection p:pins){if(Thread.currentThread().isInterrupted())return;try{List<PlexClient.Film> rows=plex.collectionHome(p);if(!rows.isEmpty())found.put(p,rows);}catch(Exception unavailable){complete=false;}}boolean success=complete;main.post(()->{if(!active||closed||token!=collectionGeneration||!account.equals(plex.identity()))return;collectionRows.clear();collectionRows.putAll(found);collectionSignature=success?requested:"";collectionAt=SystemClock.elapsedRealtime();if(filter==0)showFilms();});});}
    void resumeActivity(org.json.JSONObject item){
        String profile=VelaWorkspace.active(activity);if(!profile.equals(item.optString("profile")))return;
        Intent intent=VelaRecentActivity.intent(activity,item);if(intent!=null){launch(intent);return;}
        if(!item.optString("kind").equals("channel")){note(L10n.text("This activity is no longer available."));return;}
        String source=item.optString("scope");if(!source.equals(LiveTvData.source(activity))){note(L10n.text("This channel belongs to another source."));return;}
        VelaScreen requested=panels==null?null:panels.screen;
        io.execute(()->{LiveTvData.Channel found=null;try{found=TvChannelLibrary.resolveActivity(activity,source,item.optString("key"),item.optString("title"));}catch(Exception unavailable){}
            LiveTvData.Channel result=found;main.post(()->{
                if(closed||!((HomeActivity)activity).foreground()||!profile.equals(VelaWorkspace.active(activity))||(panels==null?null:panels.screen)!=requested)return;
                if(!source.equals(LiveTvData.source(activity))){note(L10n.text("This channel belongs to another source."));return;}
                if(result!=null)searchChannel(result);else note(L10n.text("This channel is no longer available."));
            });
        });
    }
    private boolean browsing(){return filter==1||filter==2||filter==4;}
    private boolean accepts(PlexClient.Section section,int kind){return kind==4||(kind==1&&section.type.equals("movie"))||(kind==2&&section.type.equals("show"));}
    private void cancelCatalog(){catalogGeneration++;catalogLoading=false;if(catalogWork!=null){catalogWork.cancel(true);catalogWork=null;}}
    private void changeFilter(int kind){
        if(filterSheet!=null)closeOverlay();cancelCatalog();
        if(kind!=filter){catalogPage=null;requestedOffset=0;requestedSection=catalogSection!=null&&accepts(catalogSection,kind)?catalogSection.key:"";}
        if(catalogSection!=null&&!accepts(catalogSection,kind))catalogSection=null;
        filter=kind;catalogError=false;showFilms();if(kind==0)loadCollections();
        if(browsing()&&plex.connected()&&catalogPage==null)loadCatalog(requestedSection,0,false);
    }
    private void loadCatalog(String sectionKey,int offset,boolean focusCards){
        if(!active||closed||!browsing()||!plex.connected())return;
        cancelCatalog();catalogLoading=true;catalogError=false;requestedSection=sectionKey;requestedOffset=offset;updatePager();
        final int epoch=catalogGeneration,kind=filter;final String server=plex.identity();
        final List<PlexClient.Section> known=catalogSections;final Map<String,CatalogFilters.Query> queries=new HashMap<>();for(Map.Entry<String,CatalogFilters.Query> e:catalogQueries.entrySet())queries.put(e.getKey(),e.getValue().copy());
        catalogWork=io.submit(()->{
            try{
                List<PlexClient.Section> sections=known.isEmpty()?plex.sections():known;
                PlexClient.Section chosen=null;
                for(PlexClient.Section s:sections)if(accepts(s,kind)){if(chosen==null)chosen=s;if(s.key.equals(sectionKey)){chosen=s;break;}}
                final PlexClient.Section section=chosen;
                final CatalogFilters.Query query=section==null?new CatalogFilters.Query():queries.getOrDefault(section.key,new CatalogFilters.Query());
                final PlexClient.LibraryPage page=section==null?null:plex.page(section,offset,query);
                main.post(()->{
                    if(closed||!active||epoch!=catalogGeneration||filter!=kind||!server.equals(plex.identity()))return;
                    catalogLoading=false;catalogWork=null;catalogSections=sections;catalogSection=section;catalogPage=page;catalogQuery=query;
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
        catalogFilter.setText(Ui.tr("Filtros","Filters","Filtros")+(catalogQuery.count()==0?" ▾":" · "+catalogQuery.count()+" ▾"));catalogSort.setText((catalogQuery.descending&&(catalogQuery.sort.isEmpty()||catalogQuery.sort.equals("addedAt")||catalogQuery.sort.equals("DateCreated"))?Ui.tr("Últimas adições","Latest additions","Últimas subidas"):CatalogFilterSheet.sortLabel(catalogQuery.sort,catalogQuery.sort)+(catalogQuery.descending?" ↓":" ↑"))+" ▾");catalogFilter.setEnabled(!catalogLoading&&catalogSection!=null);catalogSort.setEnabled(!catalogLoading&&catalogSection!=null);catalogFilter.setAlpha(catalogFilter.isEnabled()?1:.55f);catalogSort.setAlpha(catalogSort.isEnabled()?1:.55f);
        libraryChooser.setEnabled(!catalogLoading&&!catalogSections.isEmpty());libraryChooser.setAlpha(libraryChooser.isEnabled()?1:.55f);
        String status=catalogLoading?Ui.tr("Carregando…","Loading…","Cargando…"):catalogError?Ui.tr("Sem conexão · Tentar novamente","Offline · Retry","Sin conexión · Reintentar"):"";
        if(status.isEmpty()&&catalogPage!=null){int start=catalogPage.received==0?0:catalogPage.offset+1,end=catalogPage.offset+catalogPage.received;status=start+"–"+end+(catalogPage.total<0?"":Ui.tr(" de "," of "," de ")+catalogPage.total);}
        pageStatus.setText(status);
        pagePrevious.setEnabled(!catalogLoading&&catalogPage!=null&&catalogPage.offset>0);pagePrevious.setAlpha(pagePrevious.isEnabled()?1:.4f);
        pageNext.setText(catalogError?Ui.tr("Tentar novamente","Retry","Reintentar"):Ui.tr("Seguinte ›","Next ›","Siguiente ›"));
        pageNext.setEnabled(!catalogLoading&&(catalogError||(catalogPage!=null&&catalogPage.hasNext())));pageNext.setAlpha(pageNext.isEnabled()?1:.4f);
    }
    private void showCatalogFilters(boolean sorting){
        if(catalogLoading||catalogSection==null)return;final String identity=plex.identity(),section=catalogSection.key;PlexClient.Section source=catalogSection;
        startOverlay(sorting?Ui.tr("Ordenar biblioteca","Sort library","Ordenar biblioteca"):Ui.tr("Filtrar biblioteca","Filter library","Filtrar biblioteca"),sorting?catalogSort:catalogFilter);
        filterSheet=new CatalogFilterSheet(activity,plex,source,catalogQuery,sorting,io,query->{if(!active||closed||!identity.equals(plex.identity())||catalogSection==null||!section.equals(catalogSection.key))return;catalogQueries.put(section,query.copy());catalogQuery=query;closeOverlay();catalogPage=null;selected=null;showFilms();loadCatalog(section,0,true);},this::closeOverlay);LayoutParams p=new LayoutParams(1920,870);p.topMargin=170;overlay.addView(filterSheet,p);
    }
    private void releaseFilterSheet(){if(filterSheet!=null){filterSheet.release();filterSheet=null;}}
    private boolean firstPoster(View view){for(Shelf row:shelves)if(!row.items.isEmpty()&&view==row.items.get(0))return true;return false;}
    private void showLibrarySections(){
        if(catalogLoading||catalogSections.isEmpty())return;
        startOverlay(Ui.tr("Suas bibliotecas","Your libraries","Tus bibliotecas"),libraryChooser);
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
            TextView label=text(heading,27,WHITE,Ui.displaySemibold);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams lp=new LayoutParams(1500,42);lp.leftMargin=12;lp.topMargin=11;root.addView(label,lp);
            posters.setOrientation(LinearLayout.HORIZONTAL);posters.setClipChildren(false);posters.setClipToPadding(false);posters.setPadding(12,18,40,20);
            rail.setHorizontalScrollBarEnabled(false);rail.setSmoothScrollingEnabled(false);rail.setClipChildren(true);rail.setClipToPadding(true);rail.setOverScrollMode(OVER_SCROLL_NEVER);
            rail.setHorizontalFadingEdgeEnabled(true);rail.setFadingEdgeLength(64);rail.addView(posters,new HorizontalScrollView.LayoutParams(-2,railHeight));
            // The shelf viewport's single tree listener also observes horizontal scrolling.
            LayoutParams rp=new LayoutParams(-1,railHeight);rp.topMargin=54;root.addView(rail,rp);
            shelfList.addView(root,new LinearLayout.LayoutParams(-1,railHeight+62));shelves.add(this);
        }
    }
    private void showFilms(){
        if(closed)return;
        boolean restoreFocus=cards.contains(activity.getCurrentFocus());String restoreKey=selected==null?"":filmIdentity(selected);View restored=null;
        pageTitle.setText(filter==0?L10n.text("Home"):filter==1?L10n.text("Movies"):filter==2?"Series":filter==4?L10n.text("Library"):"YouTube");
        styleSidebar();layoutHero();detailsButton.setVisibility(selected==null?GONE:VISIBLE);
        posterGeneration++;
        for(CinemaImages.Request request:posterRequests)if(request!=null)request.cancel();
        for(ImageView image:posterImages)image.setImageDrawable(null);
        shelfList.removeAllViews();shelves.clear();cards.clear();posterImages.clear();posterFilms.clear();posterRequests.clear();posterRails.clear();shelvesScroll.scrollTo(0,0);updatePager();
        List<PlexClient.Film> visible=new ArrayList<>();
        if(filter==3)visible.addAll(youtube);else if(browsing()){if(catalogPage!=null)visible.addAll(catalogPage.films);}else visible.addAll(ContinueWatching.merge(films,watchNext,plex.connected()?plex.packageName():""));
        int viewport=filter==0?570:Appearance.posterHeight(activity)+104;LayoutParams shelfBounds=(LayoutParams)shelvesScroll.getLayoutParams();shelfBounds.height=viewport;shelfBounds.topMargin=1080-viewport;shelvesScroll.setLayoutParams(shelfBounds);shelvesScroll.setClipBounds(new Rect(0,0,shelfBounds.width,viewport));
        rowNote.setVisibility(visible.isEmpty()?VISIBLE:GONE);shelvesScroll.setVisibility(visible.isEmpty()?GONE:VISIBLE);
        link.setContentDescription(filter==3?L10n.text("Enable TV rows"):Ui.tr("Opções de biblioteca","Library options","Opciones de biblioteca"));
        if(visible.isEmpty()&&(filter!=0||collectionRows.isEmpty())){
            detailsButton.setVisibility(GONE);play.setNextFocusDownId(browsing()?pageNext.getId():play.getId());link.setNextFocusDownId(browsing()?pageNext.getId():play.getId());
            selected=null;imageGeneration++;heroBitmap=null;backdrop.setImageDrawable(null);titleLogo.setVisibility(GONE);title.setVisibility(VISIBLE);
            title.setText(filter==3?L10n.text("Everything you love."):plex.connected()?L10n.text("Your library, larger than life."):L10n.text("Your cinema starts here."));
            providerMark.setVisibility(VISIBLE);providerMark.setImageDrawable(filter==3?brand(1):plex.isJellyfin()?activity.getDrawable(R.drawable.brand_jellyfin):brand(0));connection.setText(filter==3?"YouTube":L10n.text("Your library · ")+plex.provider());meta.setText(filter==3?L10n.text("Videos on your television"):"Plex · Jellyfin · YouTube · Prime Video");
            summary.setText(filter==3?L10n.text("Rows shared by YouTube with your television appear here."):plex.connected()?L10n.text("No titles are available in this category."):Ui.tr("Conecte Plex ou Jellyfin para ver seus filmes e séries.","Connect Plex or Jellyfin to see your movies and series.","Conecta Plex o Jellyfin para ver tus películas y series."));
            play.setText(filter==3?L10n.text("Open YouTube"):plex.connected()?Ui.tr("Abrir ","Open ","Abrir ")+plex.provider():Ui.tr("Conectar biblioteca","Connect library","Conectar biblioteca"));layoutPlaybackAction();rowNote.setText(filter==3?L10n.text("Enable access to TV rows from Options."):!plex.connected()?L10n.text("Your posters appear automatically when you connect your account."):browsing()&&catalogPage==null?Ui.tr("Carregando bibliotecas…","Loading libraries…","Cargando bibliotecas…"):Ui.tr("Não há títulos nesta página ou biblioteca.","No titles in this page or library.","No hay títulos en esta página o biblioteca."));return;
        }
        PlexClient.Film keep=null;if(selected!=null)for(PlexClient.Film f:visible)if(filmIdentity(f).equals(filmIdentity(selected)))keep=f;
        if(keep==null&&selected!=null)for(List<PlexClient.Film> values:collectionRows.values())for(PlexClient.Film film:values)if(filmIdentity(film).equals(filmIdentity(selected)))keep=film;
        selectFilm(keep==null?(visible.isEmpty()?collectionRows.values().iterator().next().get(0):visible.get(0)):keep);detailsButton.setVisibility(VISIBLE);
        if(filter==0){
            List<PlexClient.Film> continuing=new ArrayList<>(),library=new ArrayList<>(),recent=new ArrayList<>();
            for(PlexClient.Film film:visible)(film.continuing?continuing:film.recentlyAdded?recent:library).add(film);
            continuing.sort((a,b)->Long.compare(b.lastPlayedMs,a.lastPlayedMs));
            for(String key:VelaLayout.order(activity))if(java.util.Arrays.asList("resume","recent","library").contains(key)&&VelaLayout.visible(activity,key)){List<PlexClient.Film> values=key.equals("resume")?continuing:key.equals("recent")?recent:recent.isEmpty()?library:Collections.emptyList();if(!values.isEmpty())addShelf(VelaLayout.label(key),values,false,12);if(key.equals("library"))for(Map.Entry<LibraryCollection,List<PlexClient.Film>> collection:collectionRows.entrySet())if(!collection.getValue().isEmpty())addShelf(collection.getKey().title,collection.getValue(),false,8);}

        }else addShelf(filter==3?Ui.tr("Do YouTube","From YouTube","Desde YouTube"):catalogSection==null?Ui.tr("Sua biblioteca","Your library","Tu biblioteca"):catalogSection.title,visible,filter==3,browsing()?PlexClient.PAGE_SIZE:12);
        shelfList.setPadding(0,0,0,shelves.isEmpty()?0:Math.max(0,viewport-shelves.get(shelves.size()-1).root.getLayoutParams().height));
        for(int r=0;r<shelves.size();r++){
            Shelf row=shelves.get(r);
            for(int c=0;c<row.items.size();c++){
                View card=row.items.get(c);card.setNextFocusUpId(r==0?(browsing()?libraryChooser:play).getId():shelves.get(r-1).items.get(Math.min(c,shelves.get(r-1).items.size()-1)).getId());
                card.setNextFocusDownId(r+1<shelves.size()?shelves.get(r+1).items.get(Math.min(c,shelves.get(r+1).items.size()-1)).getId():card.getId());
                if(filmIdentity(posterFilms.get(cards.indexOf(card))).equals(restoreKey))restored=card;
            }
        }
        if(!cards.isEmpty()){play.setNextFocusDownId((browsing()?libraryChooser:cards.get(0)).getId());link.setNextFocusDownId((browsing()?libraryChooser:cards.get(0)).getId());detailsButton.setNextFocusDownId((browsing()?libraryChooser:cards.get(0)).getId());libraryChooser.setNextFocusDownId(cards.get(0).getId());catalogFilter.setNextFocusDownId(cards.get(0).getId());catalogSort.setNextFocusDownId(cards.get(0).getId());pagePrevious.setNextFocusDownId(cards.get(0).getId());pageNext.setNextFocusDownId(cards.get(0).getId());}
        if(restoreFocus&&!cards.isEmpty())(restored==null?cards.get(0):restored).requestFocus();
        if(active){artReleased=false;main.post(visiblePosters);}
    }
    private String filmIdentity(PlexClient.Film film){return film.packageName+"|"+film.key+"|"+film.continuing;}
    private void addShelf(String heading,List<PlexClient.Film> films,boolean wide,int limit){
        boolean resumeRow=false;for(PlexClient.Film film:films)if(film.continuing){resumeRow=true;break;}
        boolean resume=filter==0&&resumeRow;
        String layoutKey=resume?"resume":films.isEmpty()||!films.get(0).recentlyAdded?"library":"recent";float layoutScale=filter==0?new float[]{.82f,1,1.12f}[VelaLayout.size(activity,layoutKey,1)]:1;
        int rowPosterWidth=Math.round(Appearance.posterWidth(activity)*layoutScale),rowPosterHeight=Math.round(Appearance.posterHeight(activity)*layoutScale),resumeHeight=rowPosterWidth*2*9/16;int contentHeight=resume?resumeHeight+60:rowPosterHeight;
        Shelf row=new Shelf(heading,wide?294:contentHeight+(resume?36:42));int count=0;
        for(PlexClient.Film film:films){
            if(count++==limit)break;
            boolean captioned=wide||resume;
            // Continue watching uses one shared 16:9 layout; the library keeps portrait posters.
            final int pictureHeight=wide?180:resume?resumeHeight:contentHeight;
            final int width=wide?320:resume?rowPosterWidth*2:rowPosterWidth;
            final int height=wide?230:contentHeight;
            FrameLayout card=new FrameLayout(activity);focusable(card,film.title+", "+film.provider+(film.continuing?", "+ContinueWatching.remaining(film):""));card.setClipChildren(false);card.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),pictureHeight,14);o.setAlpha(1);}});card.setOutlineAmbientShadowColor(0x99000000);card.setOutlineSpotShadowColor(0xDD000000);
            FrameLayout picture=new FrameLayout(activity);picture.setBackground(round(0xFF1A1C24,14));picture.setClipToOutline(true);card.addView(picture,new LayoutParams(width,pictureHeight));
            ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setCropToPadding(true);picture.addView(image,new LayoutParams(-1,-1));
            if(captioned){TextView caption=text(film.title,19,0xFFE2E2E9,Ui.medium);caption.setMaxLines(wide?1:2);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setGravity(Gravity.TOP);LayoutParams cp=new LayoutParams(width,wide?36:50);cp.topMargin=pictureHeight+10;card.addView(caption,cp);}
            if(resume){
                // Readability is drawn by small native overlays, not a second artwork bitmap.
                View shade=new View(activity);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xC9000000}));picture.addView(shade,new LayoutParams(-1,88,Gravity.BOTTOM));
                Drawable provider=providerBrand(film);int markWidth=provider==null?Math.min(width-48,(int)Math.ceil(meta.getPaint().measureText(film.provider))+20):film.packageName.equals(JellyfinClient.PACKAGE)?24:film.packageName.equals(TvSuggestions.YOUTUBE)?82:film.packageName.equals("com.amazon.amazonvideo.livingroom")?64:44;
                FrameLayout badge=new FrameLayout(activity);badge.setBackground(round(0xD9161820,10));LayoutParams bp=new LayoutParams(markWidth+20,32);bp.leftMargin=12;bp.topMargin=12;picture.addView(badge,bp);
                if(provider!=null){ImageView mark=new ImageView(activity);mark.setScaleType(ImageView.ScaleType.FIT_CENTER);mark.setImageDrawable(provider);LayoutParams mp=new LayoutParams(markWidth,20,Gravity.CENTER);badge.addView(mark,mp);}else{TextView name=text(film.provider,14,WHITE,Ui.medium);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);badge.addView(name,new LayoutParams(-1,-1));}
                if(!film.episodeLabel.isEmpty()){TextView episode=text(film.episodeLabel,14,WHITE,Ui.medium);episode.setSingleLine();episode.setEllipsize(android.text.TextUtils.TruncateAt.END);episode.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);episode.setShadowLayer(4,0,1,Color.BLACK);LayoutParams ep=new LayoutParams(Math.max(1,width-markWidth-56),32,Gravity.TOP|Gravity.RIGHT);ep.rightMargin=12;ep.topMargin=12;picture.addView(episode,ep);}
                TextView remaining=text(ContinueWatching.remaining(film),17,WHITE,Ui.medium);remaining.setSingleLine();remaining.setEllipsize(android.text.TextUtils.TruncateAt.END);remaining.setShadowLayer(4,0,1,Color.BLACK);LayoutParams rp=new LayoutParams(width-24,26,Gravity.BOTTOM);rp.leftMargin=12;rp.bottomMargin=12;picture.addView(remaining,rp);
            }
            if(film.progress>=0){View track=new View(activity);track.setBackgroundColor(0x55FFFFFF);picture.addView(track,new LayoutParams(-1,4,Gravity.BOTTOM));View progress=new View(activity);progress.setBackgroundColor(Appearance.accent(activity));LayoutParams pp=new LayoutParams((int)(width*film.progress),4,Gravity.BOTTOM);picture.addView(progress,pp);}
            card.setOnFocusChangeListener((v,f)->{lift(v,f?1.035f:1,f?-4:0,f?14:0);if(f){lastContent=v;setSidebar(false);revealCard(row,v);main.post(()->{if(v.hasFocus()&&v.isAttachedToWindow())revealCard(row,v);});selectFilm(film);schedulePosters();}});card.setOnClickListener(v->{selectFilm(film);openFilm();});card.setOnLongClickListener(v->showWatchedMenu(film,v));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.rightMargin=28;row.posters.addView(card,p);row.items.add(card);cards.add(card);
            posterImages.add(image);posterFilms.add(film);posterRequests.add(null);posterRails.add(row.rail);
        }
    }
    private void revealCard(Shelf row,View card){
        shelvesScroll.scrollTo(0,row.root.getTop());
        int x=row.rail.getScrollX(),margin=row.rail.getHorizontalFadingEdgeLength()+16;
        if(card.getLeft()-margin<x)row.rail.scrollTo(Math.max(0,card.getLeft()-margin),0);
        else if(card.getRight()+margin>x+row.rail.getWidth())row.rail.scrollTo(card.getRight()+margin-row.rail.getWidth(),0);
    }
    private void heroBounds(View view,int top,int height){LayoutParams p=(LayoutParams)view.getLayoutParams();p.topMargin=top;p.height=height;view.setLayoutParams(p);}
    private void layoutHero(){
        boolean home=filter==0,large=Appearance.sizeIndex(activity)==2;
        heroBounds(providerMark,home?132:218,home?32:38);heroBounds(connection,home?132:218,home?32:38);
        heroBounds(title,home?178:280,home?128:136);heroBounds(titleLogo,home?178:280,home?128:136);
        heroBounds(meta,home?310:426,home?30:34);heroBounds(summary,home?350:470,home?56:large?40:70);summary.setMaxLines(home?2:large?1:2);
        for(View action:new View[]{play,link,detailsButton})heroBounds(action,home?426:large?530:574,home?56:60);
    }
    private void layoutPlaybackAction(){
        LayoutParams action=(LayoutParams)play.getLayoutParams();action.width=Math.min(440,Math.max(256,(int)Math.ceil(play.getPaint().measureText(play.getText().toString()))+90));play.setLayoutParams(action);
        LayoutParams options=(LayoutParams)link.getLayoutParams();options.leftMargin=action.leftMargin+action.width+18;link.setLayoutParams(options);LayoutParams info=(LayoutParams)detailsButton.getLayoutParams();info.leftMargin=options.leftMargin+options.width+18;info.width=Math.min(260,Math.max(180,(int)Math.ceil(detailsButton.getPaint().measureText(detailsButton.getText().toString()))+44));detailsButton.setLayoutParams(info);
    }
    private Drawable providerBrand(PlexClient.Film film){return film.packageName.equals(JellyfinClient.PACKAGE)?activity.getDrawable(R.drawable.brand_jellyfin):film.packageName.equals("com.plexapp.android")?brand(0):film.packageName.equals(TvSuggestions.YOUTUBE)?brand(1):film.packageName.equals("com.amazon.amazonvideo.livingroom")?brand(2):null;}
    private void selectFilm(PlexClient.Film film){
        boolean same=heroKey.equals(film.provider+film.key)&&heroBitmap!=null&&!artReleased;
        String metadata=film.meta==null?"":film.meta;
        selected=film;title.setText(film.title);Drawable provider=providerBrand(film);providerMark.setImageDrawable(provider);providerMark.setVisibility(provider==null?GONE:VISIBLE);
        connection.setText((film.packageName.equals(JellyfinClient.PACKAGE)?film.continuing?Ui.tr("Continuar assistindo","Continue watching","Seguir viendo"):film.recentlyAdded?Ui.tr("Adicionados recentemente","Recently added","Últimas subidas"):Ui.tr("Sua biblioteca","Your library","Tu biblioteca"):film.section)+(!film.tvSource?" · "+plex.name():provider==null?" · "+film.provider:""));meta.setText(metadata+(film.continuing&&film.remainingMs()>0?(metadata.isEmpty()?"":"  ·  ")+ContinueWatching.remaining(film):""));summary.setText(film.summary);play.setText(film.nextEpisode?Ui.tr("Ver em ","Watch in ","Ver en ")+film.provider:film.continuing?Ui.tr("Continuar em ","Resume in ","Continuar en ")+film.provider:Ui.tr("Abrir ","Open ","Abrir ")+film.provider);layoutPlaybackAction();
        if(same)return;
        title.setVisibility(VISIBLE);titleLogo.setVisibility(GONE);titleLogo.setImageDrawable(null);titleLogo.setContentDescription(film.title);
        int generation=++imageGeneration;if(heroLoad!=null)main.removeCallbacks(heroLoad);
        if(heroRequest!=null)heroRequest.cancel();if(logoRequest!=null)logoRequest.cancel();
        if(!active)return;
        heroLoad=()->{
            if(!active||closed||generation!=imageGeneration)return;
            CinemaImages.Ready art=b->{if(active&&!closed&&generation==imageGeneration){heroBitmap=b;heroKey=film.provider+film.key;backdrop.setImageBitmap(b);}};
            if(film.tvSource){if(!VelaStyle.minimal(activity))heroRequest=images.loadTv(film.art,Visuals.light(activity)?1280:1920,Visuals.light(activity)?720:1080,art);}
            else{
                boolean light=Visuals.light(activity);if(!VelaStyle.minimal(activity))heroRequest=images.load(film.art,light?1280:1920,light?720:1080,art);
                logoRequest=images.loadLogo(film.logo,b->{if(active&&!closed&&generation==imageGeneration){titleLogo.setImageBitmap(b);titleLogo.setVisibility(VISIBLE);title.setVisibility(INVISIBLE);}});
            }
        };
        main.postDelayed(heroLoad,90);
    }
    private boolean showWatchedMenu(PlexClient.Film film,View from){
        if(watchedBusy)return true;detailOrigin=from;startDetailOverlay(from);final int token=++detailGeneration;String account=plex.identity();detailSheet=new VelaContentDetails(activity,film.title,film.meta+(film.continuing?" · "+ContinueWatching.remaining(film):""),film.summary,film.progress);LayoutParams sheetBounds=new LayoutParams(1390,790);sheetBounds.leftMargin=180;sheetBounds.topMargin=78;overlay.addView(detailSheet,sheetBounds);VelaContentDetails sheet=detailSheet;sheet.ready=()->!watchedBusy;watchedStatus=sheet.description;watchedMark=null;String path=film.thumb;if(!path.isEmpty())detailImage=film.tvSource?images.loadTv(path,320,480,b->{if(detailSheet==sheet)sheet.image.setImageBitmap(b);}):images.load(path,320,480,b->{if(detailSheet==sheet)sheet.image.setImageBitmap(b);});
        TextView primary=sheet.action(L10n.text("Open in ")+film.provider,film.continuing?ContinueWatching.remaining(film):"",()->{closeOverlay();selectFilm(film);openFilm();});
        if(!film.tvSource&&(film.type.equals("show")||film.type.equals("season")))sheet.action(film.type.equals("show")?L10n.text("Seasons"):L10n.text("Episodes"),L10n.text("Browse without leaving Vela"),()->showChildren(film,0));
        if(plex.canMarkWatched(film)){watchedMark=sheet.action(L10n.text("Mark as watched"),L10n.text("Updates your connected server"),()->markWatched(film,account));sheet.actions.removeView(watchedMark);sheet.actions.addView(watchedMark,0);primary=watchedMark;}
        try{org.json.JSONObject ref=plex.reference(film);if(ref!=null)sheet.action(VelaWatchlist.contains(activity,ref.optString("id"))?L10n.text("Remove from Watch later"):L10n.text("Save for later"),"",()->{try{VelaWatchlist.toggle(activity,ref);showWatchedMenu(film,detailOrigin);}catch(Exception bad){sheet.description.setText(L10n.text("Could not save this item."));}});}catch(Exception ignored){}
        if(film.alternative!=null)sheet.action(L10n.text("Also available on ")+film.alternative.provider,"",()->showWatchedMenu(film.alternative,detailOrigin));sheet.action(L10n.text("Back"),"",this::closeOverlay);primary.requestFocus();
        if(!film.tvSource)detailWork=io.submit(()->{try{if(!account.equals(plex.identity()))return;PlexClient.Film fresh=plex.detail(film);main.post(()->{if(active&&!closed&&detailGeneration==token&&detailSheet==sheet&&!watchedBusy&&account.equals(plex.identity())&&fresh!=null){sheet.metadata.setText(fresh.meta+(film.continuing?" · "+ContinueWatching.remaining(film):""));sheet.description.setText(fresh.summary.isEmpty()?L10n.text("No description supplied by this source."):fresh.summary);}});}catch(Exception unavailable){}});return true;
    }
    private void showChildren(PlexClient.Film parent,int offset){View origin=detailOrigin;startDetailOverlay(origin);final int token=++detailGeneration;String account=plex.identity();detailSheet=new VelaContentDetails(activity,parent.title,L10n.text(parent.type.equals("show")?"Seasons":"Episodes"),parent.summary,-1);VelaContentDetails sheet=detailSheet;LayoutParams bounds=new LayoutParams(1390,790);bounds.leftMargin=180;bounds.topMargin=78;overlay.addView(sheet,bounds);if(!parent.thumb.isEmpty())detailImage=images.load(parent.thumb,320,480,b->{if(detailSheet==sheet)sheet.image.setImageBitmap(b);});sheet.action(L10n.text("Loading…"),"",()->{}).setEnabled(false);detailWork=io.submit(()->{try{if(!account.equals(plex.identity()))return;PlexClient.LibraryPage page=plex.children(parent,offset);main.post(()->{if(!active||closed||token!=detailGeneration||detailSheet!=sheet||!account.equals(plex.identity()))return;sheet.actions.removeAllViews();TextView first=null;for(PlexClient.Film child:page.films){String label=child.episodeLabel.isEmpty()?child.title:child.episodeLabel+" · "+(child.detailTitle.isEmpty()?child.title:child.detailTitle);TextView row=sheet.action(label,child.meta,()->{if(child.type.equals("season"))showChildren(child,0);else showWatchedMenu(child,detailOrigin);});if(first==null)first=row;}if(page.films.isEmpty())sheet.description.setText(L10n.text("No items on this page."));if(offset>0)sheet.action(L10n.text("‹ Previous"),"",()->showChildren(parent,Math.max(0,offset-PlexClient.PAGE_SIZE)));if(page.hasNext())sheet.action(L10n.text("Next ›"),"",()->showChildren(parent,page.nextOffset()));TextView back=sheet.action(L10n.text("Back to details"),"",()->showWatchedMenu(parent,detailOrigin));(first==null?back:first).requestFocus();});}catch(Exception error){main.post(()->{if(detailSheet!=sheet)return;sheet.actions.removeAllViews();sheet.description.setText(L10n.text("Could not load this page. Your library is unchanged."));sheet.action(L10n.text("Retry"),"",()->showChildren(parent,offset)).requestFocus();sheet.action(L10n.text("Back to details"),"",()->showWatchedMenu(parent,detailOrigin));});}});}
    private void cancelDetails(){detailGeneration++;if(detailWork!=null){detailWork.cancel(true);detailWork=null;}if(detailImage!=null){detailImage.cancel();detailImage=null;}if(detailSheet!=null){detailSheet.image.setImageDrawable(null);detailSheet=null;}}
    private void markWatched(PlexClient.Film film,String account){
        if(watchedBusy)return;watchedBusy=true;TextView status=watchedStatus,action=watchedMark;if(action!=null)action.setEnabled(false);status.setText(Ui.tr("Salvando no servidor…","Saving to the server…","Guardando en el servidor…"));
        io.execute(()->{try{
            plex.markWatched(film,account);List<PlexClient.Film> fresh=null;try{fresh=plex.library();}catch(Exception ignored){}final List<PlexClient.Film> updated=fresh;
            main.post(()->{watchedBusy=false;if(closed||!account.equals(plex.identity()))return;List<PlexClient.Film> remaining=new ArrayList<>();for(PlexClient.Film f:films)if(!f.continuing||!film.key.equals(f.key)||!film.packageName.equals(f.packageName))remaining.add(f);films=updated==null?remaining:updated;refreshedAt=updated==null?0:SystemClock.elapsedRealtime();if(!active){libraryDirty=true;return;}boolean same=watchedStatus==status&&overlay.getVisibility()==VISIBLE;if(same)closeOverlay();showFilms();if(same&&!cards.isEmpty())cards.get(0).requestFocus();note(Ui.tr("Marcado como assistido","Marked as watched","Marcado como visto"));});
        }catch(Exception failure){main.post(()->{watchedBusy=false;if(closed||watchedStatus!=status||overlay.getVisibility()!=VISIBLE)return;if(action!=null){action.setEnabled(true);action.requestFocus();}status.setText(Ui.tr("Não foi possível confirmar a alteração. Atualize a biblioteca antes de tentar novamente.","Could not confirm the change. Refresh the library before trying again.","No se ha podido confirmar el cambio. Actualiza la biblioteca antes de volver a intentarlo."));});}});
    }
    private void openFilm(){
        if(selected==null){if(filter==3)openPackage(TvSuggestions.YOUTUBE,"YouTube");else if(!plex.connected())manageLibrary();else openPackage(plex.packageName(),plex.provider());return;}
        refreshedAt=0;
        if(selected.tvSource){Intent intent=ContinueWatching.launch(activity,selected);if(intent!=null)launch(intent);else openPackage(selected.packageName,selected.provider);return;}
        Intent intent=plex.open(selected);if(intent!=null&&intent.resolveActivity(activity.getPackageManager())!=null)launch(intent);else openPackage(selected.packageName,selected.provider);
    }
    private void manageLibrary(){if(toolsLayer!=null){((HomeActivity)activity).afterTools(v->v.manageLibrary());return;}activity.startActivity(plex.both()?new Intent(activity,SettingsActivity.class).putExtra("page",21):plex.connected()?new Intent(activity,plex.linkActivity()):new Intent(activity,SettingsActivity.class).putExtra("page",14));}
    private void openPackage(String pkg,String name){if(toolsLayer!=null){((HomeActivity)activity).afterTools(v->v.openPackage(pkg,name));return;}Intent intent=HomeApps.launchIntent(activity.getPackageManager(),pkg);if(intent==null){note(name+L10n.text(" is not installed."));return;}launch(intent);}
    private void launch(Intent intent){try{activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException|SecurityException e){note(L10n.text("Could not open this option."));}}
    private void note(String s){Toast.makeText(activity,s,Toast.LENGTH_SHORT).show();}
    private void releaseAppsGrid(){if(installedApps!=null){installedApps.release();installedApps=null;}}
    private void resetOverlayChrome(){LayoutParams full=new LayoutParams(1920,1080);overlay.setLayoutParams(full);if(detailHeading!=null){pageTitle.setText(detailHeading);detailHeading=null;}}
    private void startDetailOverlay(View from){TextView close=startOverlay("",from);close.setVisibility(GONE);detailHeading=pageTitle.getText();pageTitle.setText(L10n.text("Details"));LayoutParams content=new LayoutParams(1790,940);content.leftMargin=112;content.topMargin=116;overlay.setLayoutParams(content);overlay.setBackgroundColor(BG);LayoutParams button=(LayoutParams)close.getLayoutParams();button.leftMargin=1570;button.topMargin=8;close.setLayoutParams(button);}
    private TextView startOverlay(String heading,View from){resetOverlayChrome();cancelDetails();releaseFilterSheet();releaseAppsGrid();overlayReturn=from;setSidebar(false);surface.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);sidebar.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);if(panels!=null)panels.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);overlay.removeAllViews();overlay.setBackgroundColor(0xFD0C0D12);overlay.setVisibility(VISIBLE);TextView h=text(heading,38,WHITE,Ui.displaySemibold);LayoutParams p=new LayoutParams(1400,70);p.leftMargin=84;p.topMargin=68;overlay.addView(h,p);TextView close=button(Ui.tr("Fechar","Close","Cerrar"),20);p=new LayoutParams(140,56);p.leftMargin=1692;p.topMargin=76;overlay.addView(close,p);close.setOnClickListener(v->closeOverlay());return close;}
    private void closeOverlay(){if(overlay.getVisibility()!=VISIBLE)return;cancelDetails();releaseFilterSheet();releaseAppsGrid();overlay.setVisibility(GONE);overlay.removeAllViews();resetOverlayChrome();surface.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);sidebar.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);if(panels!=null)panels.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);if(overlayReturn!=null&&overlayReturn.isAttachedToWindow())overlayReturn.requestFocus();else play.requestFocus();}
    private void showApps(View from){openPanel(new Intent(HomeScreenHost.APPS));}
    private void option(String label,int y,Runnable action,boolean focus){TextView v=button(label,23);LayoutParams p=new LayoutParams(740,82);p.leftMargin=84;p.topMargin=y;overlay.addView(v,p);v.setOnClickListener(w->action.run());if(focus)v.requestFocus();}
    private void showSettings(View from){openPanel(new Intent(activity,SettingsActivity.class));}
    private void showInputs(View from){openPanel(new Intent(HomeScreenHost.INPUTS));}
    boolean onKey(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_INFO&&overlay.getVisibility()!=VISIBLE&&panels==null&&!livePage&&selected!=null){if(event.getAction()==KeyEvent.ACTION_UP)showWatchedMenu(selected,activity.getCurrentFocus());return true;}
        if(panels!=null&&menuPackage==null&&reorderOriginal==null&&overlay.getVisibility()!=VISIBLE&&!sidebarFocused())return panels.onKey(event);
        if(livePage&&overlay.getVisibility()!=VISIBLE&&liveTv.onKey(event))return true;
        int code=event.getKeyCode();boolean confirm=code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A;
        if(confirm&&watchedBusy)return true;if(confirm&&consumeConfirmUp){if(event.getAction()==KeyEvent.ACTION_UP){consumeConfirmUp=false;confirmHeld=false;reorderConfirmArmed=false;menuConfirmArmed=false;}return true;}
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
        if(confirm)confirmHeld=event.getAction()==KeyEvent.ACTION_DOWN&&(appButtons.contains(activity.getCurrentFocus())||cards.contains(activity.getCurrentFocus()));
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){if(overlay.getVisibility()==VISIBLE){if(filterSheet==null||!filterSheet.back())closeOverlay();}else if(sidebarOpen)leaveSidebar();else activeSide().requestFocus();}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT&&overlay.getVisibility()!=VISIBLE&&!sidebarOpen){View focus=activity.getCurrentFocus();if(focus==play||focus==libraryChooser||firstPoster(focus)){if(event.getAction()==KeyEvent.ACTION_DOWN)activeSide().requestFocus();return true;}}
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&browsing()&&!sidebarOpen&&overlay.getVisibility()!=VISIBLE&&!cards.isEmpty()&&activity.getCurrentFocus()==cards.get(cards.size()-1)&&catalogPage!=null&&catalogPage.hasNext()){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&!catalogLoading)loadCatalog(catalogSection.key,catalogPage.nextOffset(),true);return true;
        }
        return false;
    }
}
