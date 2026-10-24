package tv.vela.launcher;

/** Standalone compatibility entry point; Home embeds the same native screen. */
public class XtreamLinkActivity extends ScreenActivity {
    @Override VelaScreen createScreen(VelaScreen.Navigation navigation){return new XtreamLinkScreen(this,navigation);}
}
