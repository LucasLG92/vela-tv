package tv.vela.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * The Vela home screen (Android's "Home" role in Vela mode). It runs in the :home process: if it crashes,
 * the safety net in the main process keeps working. On the 3rd crash within 5 min, the Watchdog gives the home screen
 * back to the TV's original one.
 */
public class HomeActivity extends Activity implements VelaHub.Owner {
    private CinemaHomeView home;
    private VelaHub hub;private android.view.View toolsReturnFocus;private VelaProgrammeRefresh programmes;private final android.os.Handler toolsMain=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable programmeTick=new Runnable(){public void run(){if(!foreground)return;scanProgrammes();toolsMain.postDelayed(this,60000);}};
    private void scanProgrammes(){if(hub!=null)return;if(programmes==null)programmes=new VelaProgrammeRefresh(this);programmes.check(new FullToolsData(this){public void activate(VelaHub.Hit hit){}public void closed(boolean changed){}});}
    private VelaSync sync;
    private void stopSync(){if(sync!=null){sync.close();sync=null;}}
    private void startSync(){stopSync();if(!foreground||hub!=null)return;sync=new VelaSync(this);sync.resume(()->{if(foreground&&hub==null)recreate();});}
    @Override public void toolsGuide(String name){tools(9);if(hub!=null)hub.guideFix(name);}
    private boolean destroying;
    private int toolsKey=-1;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener reading=()->{if(home!=null)VelaReading.apply(home);};
    private boolean foreground;
    boolean foreground(){return foreground;}
    TvInputs.Source createInputsSource(){return new TvInputs.SystemSource(this);}
    PlexClient createPlexClient(){return new PlexClient(this);}
    boolean canShowInstaller(){return foreground&&hub==null&&home!=null&&home.canShowInstaller();}

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        if (Watchdog.crashedTooOften(this)) {
            // Don't try to draw again: only show a notice while the main process restores the original home screen.
            HomeMode.requestRevertHome(this);
            TextView t = new TextView(this);
            t.setText(Ui.tr("O Início do Vela teve um problema.\nVoltando para o Início original da TV…",
                    "Vela's home screen ran into a problem.\nGoing back to the TV's original home screen…", "El inicio de Vela ha tenido un problema.\nVolviendo al inicio original de la TV…"));
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(Color.rgb(7, 11, 30));
            setContentView(t);
            return;
        }
        Ui.init(this);
        boolean setup=Personalization.needed(this);
        home = new CinemaHomeView(this);
        setContentView(home);
        if(savedInstanceState!=null&&savedInstanceState.containsKey("velaScreens"))home.restorePanels(savedInstanceState.getBundle("velaScreens"));else if("settings".equals(getIntent().getStringExtra("velaEntry")))home.settings();else if(setup)home.route(new Intent(this,SetupActivity.class));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawable(null);
        home.getViewTreeObserver().addOnGlobalLayoutListener(reading);
        int tools=savedInstanceState==null?getIntent().getIntExtra("velaTools",-1):savedInstanceState.getInt("velaTools",-1);getIntent().removeExtra("velaTools");if(tools>=0)tools(tools);
    }

    @Override protected void onResume() {
        super.onResume();
        foreground=true;toolsMain.removeCallbacks(programmeTick);programmeTick.run();
        if(home!=null&&hub==null){home.resume();if(!home.showingUpdates())Updates.resumeInstallation(this);}VelaShows.schedule(this);startSync();
    }

    @Override protected void onPause() {
        foreground=false;stopSync();toolsMain.removeCallbacksAndMessages(null);if(programmes!=null)programmes.pause();
        if(hub!=null)hub.pause();
        if (home != null) home.pause();
        super.onPause();
    }

    @Override protected void onStop() {
        if(home!=null)home.releaseImages();
        super.onStop();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(home!=null)home.trimMemory();
    }
    @Override public void onLowMemory() {
        super.onLowMemory();
        if(home!=null)home.trimMemory();
    }

    /** Home returns to the opening page with the sidebar closed and the main action focused. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if(intent.hasExtra("velaTools")){int page=intent.getIntExtra("velaTools",0);intent.removeExtra("velaTools");tools(page);return;}
        if(hub!=null){hub.close();return;}
        if(home!=null){if("settings".equals(intent.getStringExtra("velaEntry")))home.settings();else home.reset();}
    }

    @Override protected void onDestroy() {
        destroying=true;if(programmes!=null)programmes.close();toolsMain.removeCallbacksAndMessages(null);if(hub!=null)hub.close();if(home!=null&&home.getViewTreeObserver().isAlive())home.getViewTreeObserver().removeOnGlobalLayoutListener(reading);
        if (home != null) home.destroy();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getKeyCode()==toolsKey){if(event.getAction()==KeyEvent.ACTION_UP)toolsKey=-1;return true;}
        if(hub!=null){if(hub.remote(event))return true;KeyEvent mapped=VelaRemote.gamepad(event);if(mapped!=event&&hub.remote(mapped))return true;return super.dispatchKeyEvent(mapped);}
        int target=VelaRemote.destination(this,event);if(target>=0){toolsKey=event.getKeyCode();tools(target);return true;}event=VelaRemote.gamepad(event);
        if (home != null && home.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(home!=null&&home.permissionResult(requestCode,permissions,results))return;
        if(requestCode==80 && home!=null)home.loadYouTube();
    }

    @Override public void startActivity(Intent intent){
        if(home!=null){
            if(intent.getComponent()!=null&&intent.getComponent().getClassName().equals(HomeActivity.class.getName())){home.reset();return;}
            if(home.route(intent))return;
        }
        super.startActivity(intent);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==VelaHub.EXPORT||request==VelaHub.IMPORT){if(hub==null)tools(1);hub.result(request,result,data);return;}if(home!=null)home.activityResult(request,result,data);}
    @Override protected void onSaveInstanceState(Bundle state){if(hub!=null)state.putInt("velaTools",hub.page());if(home!=null){Bundle screens=home.savePanels();if(screens!=null)state.putBundle("velaScreens",screens);}super.onSaveInstanceState(state);}

    @Override public void tools(int page){
        stopSync();
        if(hub!=null){hub.close();if(destroying)return;}if(home==null)return;if(programmes!=null)programmes.pause();toolsReturnFocus=getCurrentFocus();home.pause();home.releaseImages();home.setVisibility(android.view.View.INVISIBLE);
        hub=new VelaHub(this,new FullToolsData(this){
            public void activate(VelaHub.Hit hit){try{if(hit.value instanceof Intent)startActivity((Intent)hit.value);else if(hit.value instanceof TvInputs.Entry){TvInputs.Entry e=(TvInputs.Entry)hit.value;startActivity(new Intent(Intent.ACTION_VIEW,android.media.tv.TvContract.buildChannelUriForPassthroughInput(e.inputId)));}else if(hit.value instanceof LiveTvData.Channel)home.searchChannel((LiveTvData.Channel)hit.value);else if(hit.value instanceof PlexClient.Film){Intent i=new LibraryClient(HomeActivity.this).open((PlexClient.Film)hit.value);if(i!=null)startActivity(i);}}catch(RuntimeException unavailable){android.widget.Toast.makeText(HomeActivity.this,"No se puede abrir en este dispositivo.",android.widget.Toast.LENGTH_SHORT).show();}}
            public void closed(boolean changed){hub=null;android.view.View restore=toolsReturnFocus;toolsReturnFocus=null;if(destroying)return;if(changed){if(home.getViewTreeObserver().isAlive())home.getViewTreeObserver().removeOnGlobalLayoutListener(reading);home.destroy();home=new CinemaHomeView(HomeActivity.this);setContentView(home);home.getViewTreeObserver().addOnGlobalLayoutListener(reading);}home.setVisibility(android.view.View.VISIBLE);if(foreground&&home!=null)home.resume();if(!changed&&restore!=null&&restore.isAttachedToWindow()&&restore.isShown())restore.requestFocus();scanProgrammes();startSync();}
        },page);((android.widget.FrameLayout)findViewById(android.R.id.content)).addView(hub,new android.widget.FrameLayout.LayoutParams(-1,-1));hub.bringToFront();
    }

}
