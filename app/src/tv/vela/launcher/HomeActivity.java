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
public class HomeActivity extends Activity {
    private CinemaHomeView home;

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
        if(Personalization.launchIfNeeded(this)){finish();return;}
        home = new CinemaHomeView(this);
        setContentView(home);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setBackgroundDrawable(null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (home != null&&!Updates.resumeInstallation(this)) home.resume();
    }

    @Override protected void onPause() {
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
        if (home != null) home.reset();
    }

    @Override protected void onDestroy() {
        if (home != null) home.destroy();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (home != null && home.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==80 && home!=null)home.loadYouTube();
    }

}
