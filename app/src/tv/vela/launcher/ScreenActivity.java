package tv.vela.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;

/** Compatibility entry points use the same controllers as the integrated Home section. */
abstract class ScreenActivity extends Activity {
    VelaScreen screen;
    abstract VelaScreen createScreen(VelaScreen.Navigation navigation);
    @Override protected void onCreate(Bundle state){super.onCreate(state);if(this instanceof SettingsActivity&&VelaEdition.route(this,"full"))return;Ui.init(this);Watchdog.install(this);if(this instanceof SettingsActivity&&VelaSessions.blocked(this)){startActivity(new Intent(this,HomeActivity.class).putExtra("velaTools",4).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();return;}if(this instanceof SettingsActivity&&(getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)||getIntent().hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))&&!Watchdog.crashedTooOften(this)){startActivity(new Intent(this,HomeActivity.class).putExtra("velaEntry","settings").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();return;}screen=createScreen(new VelaScreen.Navigation(){
        public boolean embedded(){return false;}
        public void changed(VelaScreen s){}
        public View navigationTarget(VelaScreen s){return null;}
        public void content(VelaScreen s,View v){setContentView(v);}
        public void open(VelaScreen s,Intent i){startActivity(i);}
        public void close(VelaScreen s){finish();}
        public void fullscreen(VelaScreen s,boolean enabled){}
        public void permission(VelaScreen s,String[] names,int code){requestPermissions(names,code);}
    });screen.create(getIntent(),state);}
    @Override protected void onResume(){super.onResume();if(screen!=null)screen.resume();}
    @Override protected void onPause(){if(screen!=null)screen.pause();super.onPause();}
    @Override protected void onDestroy(){if(screen!=null)screen.destroy();super.onDestroy();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(screen!=null)screen.onNewIntent(i);}
    @Override protected void onSaveInstanceState(Bundle b){if(screen!=null)screen.onSaveInstanceState(b);super.onSaveInstanceState(b);}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(screen!=null)screen.onActivityResult(code,result,data);}
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){super.onRequestPermissionsResult(code,names,results);if(screen!=null)screen.onRequestPermissionsResult(code,names,results);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return screen!=null&&screen.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(screen!=null)screen.onBackPressed();else super.onBackPressed();}
}
