package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One mounted screen, with a bounded stack of routes and safe navigation state, never hidden views. */
final class HomeScreenHost extends FrameLayout implements VelaScreen.Navigation {
    static final String APPS="tv.vela.launcher.APPS",INPUTS="tv.vela.launcher.INPUTS";
    interface Actions {
        void navigation();
        void contentFocused();
        void title(String title);
        void fullscreen(boolean enabled);
        void exit();
        void changed();
        View navigationTarget();
    }
    private final Activity activity;
    private final Actions actions;
    private final FrameLayout pane;
    private final ArrayList<Bundle> history=new ArrayList<>();
    private final Map<Integer,Long> permissions=new HashMap<>();
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener=(old,next)->{
        focusChanged(next);
    };
    VelaScreen screen;
    private Intent route;
    private View lastFocus;
    private long token,sequence;
    private boolean active,closed,full;
    private int navigationIndex=7;
    HomeScreenHost(Activity activity,Actions actions){
        super(activity);this.activity=activity;this.actions=actions;setClipChildren(false);
        GradientDrawable background=new GradientDrawable();background.setGradientType(GradientDrawable.RADIAL_GRADIENT);background.setGradientCenter(.3f,.3f);background.setGradientRadius(1680);background.setColors(new int[]{VelaTheme.PANEL,0xFF121318,VelaTheme.BACKGROUND});setBackground(background);
        pane=new FrameLayout(activity);pane.setClipChildren(true);addView(pane,contentBounds(false));
        getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
    }
    private void focusChanged(View next){if(next!=null&&inside(next)){lastFocus=next;if(active)actions.contentFocused();}}
    private LayoutParams contentBounds(boolean full){LayoutParams p=new LayoutParams(full?1920:1790,full?1080:940);p.leftMargin=full?0:112;p.topMargin=full?0:116;return p;}
    static boolean handles(Intent intent){
        if(APPS.equals(intent.getAction())||INPUTS.equals(intent.getAction()))return true;
        ComponentName c=intent.getComponent();if(c==null)return false;String name=c.getClassName();
        return name.equals(SettingsActivity.class.getName())||name.equals(UpdatesActivity.class.getName())||name.equals(PlexLinkActivity.class.getName())||name.equals(JellyfinLinkActivity.class.getName())||name.equals(XtreamLinkActivity.class.getName())||name.equals(SetupActivity.class.getName())||name.equals(LiveTvActivity.class.getName());
    }
    private Intent safe(Intent input){
        Intent out=new Intent();if(input.getComponent()!=null)out.setComponent(input.getComponent());else out.setAction(input.getAction());
        for(String flag:new String[]{"returnToCaller","fromSetup","plex","screensaver","sources"})if(input.hasExtra(flag))out.putExtra(flag,input.getBooleanExtra(flag,false));
        if(input.hasExtra("page"))out.putExtra("page",input.getIntExtra("page",0));return out;
    }
    int navigationIndex(){return navigationIndex;}
    boolean showingUpdates(){return screen instanceof UpdatesScreen;}
    boolean updatesActive(){return active&&screen instanceof UpdatesScreen&&((UpdatesScreen)screen).canShowInstaller();}
    boolean fullscreen(){return full;}
    private boolean inside(View view){for(View v=view;v!=null;){if(v==pane)return true;android.view.ViewParent parent=v.getParent();v=parent instanceof View?(View)parent:null;}return false;}
    public View navigationTarget(VelaScreen value){return actions.navigationTarget();}
    public void changed(VelaScreen value){if(screen==value)actions.changed();}
    public boolean embedded(){return true;}
    void root(Intent intent){history.clear();show(safe(intent),null,0,++sequence);}
    private VelaScreen create(Intent intent){
        if(APPS.equals(intent.getAction()))return new AppsScreen(activity,this);
        if(INPUTS.equals(intent.getAction()))return new InputsScreen(activity,this,((HomeActivity)activity).createInputsSource());
        String name=intent.getComponent().getClassName();
        if(name.equals(SettingsActivity.class.getName()))return new SettingsScreen(activity,this);
        if(name.equals(UpdatesActivity.class.getName()))return new UpdatesScreen(activity,this);
        if(name.equals(PlexLinkActivity.class.getName()))return new PlexLinkScreen(activity,this){@Override PlexClient createClient(){return ((HomeActivity)activity).createPlexClient();}};
        if(name.equals(JellyfinLinkActivity.class.getName()))return new JellyfinLinkScreen(activity,this);
        if(name.equals(XtreamLinkActivity.class.getName()))return new XtreamLinkScreen(activity,this);
        if(name.equals(SetupActivity.class.getName()))return new SetupScreen(activity,this);
        return new TvSettingsScreen(activity,this);
    }
    private String title(VelaScreen value){
        if(value instanceof AppsScreen)return Ui.tr("Seus apps","Your apps","Tus apps");
        if(value instanceof InputsScreen)return Ui.tr("Entradas HDMI","HDMI inputs","Entradas HDMI");
        if(value instanceof UpdatesScreen)return Ui.tr("Atualizações","Updates","Actualizaciones");
        if(value instanceof PlexLinkScreen)return "Plex";
        if(value instanceof JellyfinLinkScreen)return "Jellyfin";
        if(value instanceof XtreamLinkScreen)return "Xtream";
        if(value instanceof SetupScreen)return Ui.tr("Configuração","Setup","Configuración");
        if(value instanceof TvSettingsScreen)return Ui.tr("Ajustes de TV","TV settings","Ajustes de TV");
        return Ui.tr("Ajustes","Settings","Ajustes");
    }
    private void detach(){VelaScreen previous=screen;screen=null;if(previous!=null)previous.destroy();pane.removeAllViews();lastFocus=null;if(full){full=false;pane.setLayoutParams(contentBounds(false));actions.fullscreen(false);}}
    private void show(Intent intent,Bundle state,int focus,long id){
        detach();if(closed)return;route=intent;token=id;sequence=Math.max(sequence,id);screen=create(intent);
        navigationIndex=screen instanceof AppsScreen?4:screen instanceof InputsScreen?5:7;actions.title(title(screen));
        VelaScreen current=screen;current.navigationToken=token;current.create(intent,state);if(screen!=current)return;
        if(active)current.resume();if(screen==current){focus(focus);pane.post(()->{if(!closed&&screen==current)focus(focus);});}
    }
    public void content(VelaScreen value,View view){if(screen!=value)return;pane.removeAllViews();pane.addView(view,new LayoutParams(-1,-1));}
    public void open(VelaScreen from,Intent intent){
        if(screen!=from||closed)return;
        if(intent.getComponent()!=null&&intent.getComponent().getClassName().equals(HomeActivity.class.getName())){actions.exit();return;}
        if(!handles(intent)){activity.startActivity(intent);return;}
        if(screen instanceof UpdatesScreen&&intent.getComponent()!=null&&intent.getComponent().getClassName().equals(UpdatesActivity.class.getName()))return;
        history.add(snapshot());if(history.size()>8)history.remove(1);show(safe(intent),null,0,++sequence);
    }
    public void close(VelaScreen value){
        if(screen!=value){for(int i=history.size()-1;i>=0;i--)if(history.get(i).getLong("token")==value.navigationToken)history.remove(i);return;}
        if(history.isEmpty()){
            if(screen instanceof SettingsScreen||screen instanceof AppsScreen||screen instanceof InputsScreen){actions.navigation();return;}
            actions.exit();return;
        }
        Bundle saved=history.remove(history.size()-1);show(saved.getParcelable("route"),saved.getBundle("state"),saved.getInt("focus"),saved.getLong("token"));
    }
    public void fullscreen(VelaScreen value,boolean enabled){if(screen!=value)return;full=enabled;pane.setLayoutParams(contentBounds(enabled));actions.fullscreen(enabled);}
    public void permission(VelaScreen value,String[] names,int request){if(screen!=value)return;permissions.put(request,token);activity.requestPermissions(names,request);}
    boolean permissionResult(int request,String[] names,int[] results){Long owner=permissions.remove(request);if(owner==null)return false;if(screen!=null&&owner==token)screen.onRequestPermissionsResult(request,names,results);return true;}
    void activityResult(int request,int result,Intent data){if(screen!=null)screen.onActivityResult(request,result,data);}
    void resume(){if(closed)return;active=true;if(screen!=null)screen.resume();}
    void trimMemory(){if(screen!=null)screen.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);}
    void pause(){active=false;if(screen!=null)screen.pause();}
    void destroy(){if(closed)return;closed=true;pause();detach();history.clear();permissions.clear();if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);}
    private int focusIndex(){if(screen==null||screen.content==null)return 0;List<View> focusable=screen.content.getFocusables(View.FOCUS_FORWARD);int i=focusable.indexOf(activity.getCurrentFocus());return Math.max(0,i);}
    private void focus(int index){if(screen==null||screen.content==null)return;if(screen instanceof AppsScreen){((AppsScreen)screen).focusGrid();return;}List<View> values=screen.content.getFocusables(View.FOCUS_FORWARD);if(!values.isEmpty())values.get(Math.min(Math.max(index,0),values.size()-1)).requestFocus();}
    void focusContent(){if(lastFocus!=null&&lastFocus.isAttachedToWindow()&&lastFocus.isShown())lastFocus.requestFocus();else focus(0);}
    boolean onKey(KeyEvent event){
        if(screen==null)return false;if(screen.dispatchKeyEvent(event))return true;
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)screen.onBackPressed();return true;}
        if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT&&screen.navigationLeft()&&!full){if(event.getAction()==KeyEvent.ACTION_DOWN)actions.navigation();return true;}
        return false;
    }
    private Bundle snapshot(){Bundle b=new Bundle();b.putParcelable("route",safe(screen.getIntent()));b.putBundle("state",screen.save());b.putInt("focus",focusIndex());b.putLong("token",token);return b;}
    Bundle save(){Bundle b=new Bundle();if(screen!=null)b.putBundle("current",snapshot());b.putParcelableArrayList("history",new ArrayList<>(history));b.putInt("nav",navigationIndex);Bundle pending=new Bundle();for(Map.Entry<Integer,Long> request:permissions.entrySet())pending.putLong(String.valueOf(request.getKey()),request.getValue());b.putBundle("permissions",pending);return b;}
    void restore(Bundle saved){if(saved==null)return;Bundle pending=saved.getBundle("permissions");if(pending!=null)for(String key:pending.keySet())try{int request=Integer.parseInt(key);if(request==80||request==81)permissions.put(request,pending.getLong(key));}catch(NumberFormatException invalid){}ArrayList<Bundle> old=saved.getParcelableArrayList("history");if(old!=null)for(Bundle entry:old)if(history.size()<8)history.add(entry);Bundle now=saved.getBundle("current");if(now!=null){Intent i=now.getParcelable("route");if(i!=null&&handles(i))show(safe(i),now.getBundle("state"),now.getInt("focus"),now.getLong("token",++sequence));}}
}
