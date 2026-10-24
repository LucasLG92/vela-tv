package tv.vela.launcher;

/** Standalone compatibility entry point; Home embeds the same native screen. */
public class UpdatesActivity extends ScreenActivity {
    @Override VelaScreen createScreen(VelaScreen.Navigation navigation){return new UpdatesScreen(this,navigation);}
    boolean canShowInstaller(){return screen!=null&&((UpdatesScreen)screen).canShowInstaller();}
}
