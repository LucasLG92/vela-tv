package tv.vela.launcher;

/** Standalone compatibility entry point; Home embeds the same native screen. */
public class PlexLinkActivity extends ScreenActivity {
    @Override VelaScreen createScreen(VelaScreen.Navigation navigation){return new PlexLinkScreen(this,navigation){@Override PlexClient createClient(){return PlexLinkActivity.this.createClient();}};}
    PlexClient createClient(){return new PlexClient(this);}
}
