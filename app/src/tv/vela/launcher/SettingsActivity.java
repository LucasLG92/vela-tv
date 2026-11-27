package tv.vela.launcher;

import android.content.Intent;

/** Standalone compatibility entry point; Home embeds the same native screen. */
public class SettingsActivity extends ScreenActivity {
    @Override boolean routeEntry(){
        if(VelaEdition.route(this,"full"))return true;
        Intent home=new Intent(this,HomeActivity.class);
        if(VelaSessions.blocked(this))home.putExtra("velaTools",4);
        else {
            Intent entry=getIntent();
            boolean icon=entry.hasCategory(Intent.CATEGORY_LAUNCHER)||entry.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            if(!icon||Watchdog.crashedTooOften(this))return false;
            home.putExtra("velaEntry","settings");
        }
        startActivity(home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
        return true;
    }
    @Override VelaScreen createScreen(VelaScreen.Navigation navigation){return new SettingsScreen(this,navigation);}
    void showPreview(){((SettingsScreen)screen).showPreview();}
}
