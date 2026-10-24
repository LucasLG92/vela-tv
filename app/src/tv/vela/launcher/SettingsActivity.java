package tv.vela.launcher;

/** Standalone compatibility entry point; Home embeds the same native screen. */
public class SettingsActivity extends ScreenActivity {
    @Override VelaScreen createScreen(VelaScreen.Navigation navigation){return new SettingsScreen(this,navigation);}
    void showPreview(){((SettingsScreen)screen).showPreview();}
}
