package tv.vela.launcher;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.*;
import java.util.Locale;

/** One display request owned by the playing surface; never changes Android's saved settings. */
final class VelaFrameRate {
    static final int OFF=0,SEAMLESS=1,SWITCH=2;
    interface Output {void apply(float fps,int mode);void clear();float refreshRate();}
    private final Output output;
    private final int mode;
    private float rate,applied;
    private boolean closed,playing;

    VelaFrameRate(Activity activity,Surface surface,int mode){this(new DisplayOutput(activity,surface),mode);}
    VelaFrameRate(Output output,int mode){this.output=output;this.mode=mode>=OFF&&mode<=SWITCH?mode:OFF;}
    static int mode(Context c){int n=c.getSharedPreferences("vela-video",0).getInt("frame-rate",OFF);return n>=OFF&&n<=SWITCH?n:OFF;}
    static void mode(Context c,int value){if(value<OFF||value>SWITCH)throw new IllegalArgumentException("Invalid frame-rate policy");c.getSharedPreferences("vela-video",0).edit().putInt("frame-rate",value).apply();}
    static String name(int mode){return L10n.text(mode==SEAMLESS?"Seamless only":mode==SWITCH?"Allow display switching":"Off");}
    void rate(float value){
        float fps=VideoCadence.canonical(value);
        if(closed||mode==OFF||fps==0||Math.abs(rate-fps)<0.001f)return;
        rate=fps;apply();
    }
    void playing(boolean value){if(closed||playing==value)return;playing=value;if(value)apply();else clear();}
    void unknown(){if(closed)return;rate=0;clear();}
    private void apply(){if(!playing||rate==0||applied==rate)return;try{output.apply(rate,mode);applied=rate;}catch(RuntimeException unsupported){try{output.clear();}catch(RuntimeException unavailable){/* Playback remains available. */}finally{applied=0;}}}
    private void clear(){if(applied==0)return;try{output.clear();}catch(RuntimeException unavailable){}finally{applied=0;}}
    String detail(){return rate==0?L10n.text("Waiting for video timing"):String.format(Locale.ROOT,"%.3f fps · %.2f Hz",rate,output.refreshRate());}
    float rate(){return rate;}
    void close(){if(closed)return;closed=true;clear();rate=0;}

    private static final class DisplayOutput implements Output {
        final Activity activity;final Surface surface;
        int previousMode,requestedMode;boolean override;
        DisplayOutput(Activity activity,Surface surface){this.activity=activity;this.surface=surface;}
        public void apply(float fps,int mode){
            if(!surface.isValid())throw new IllegalStateException("Video surface unavailable");
            if(Build.VERSION.SDK_INT>=31){surface.setFrameRate(fps,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,mode==SWITCH?Surface.CHANGE_FRAME_RATE_ALWAYS:Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);return;}
            surface.setFrameRate(fps,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
            // Android 11 lacks the three-argument Surface API. Only explicit opt-in may
            // choose a same-resolution mode; do not request a different picture profile.
            if(mode!=SWITCH)return;
            Display display=activity.getWindow().getDecorView().getDisplay();if(display==null)return;
            Display.Mode current=display.getMode();if(VideoCadence.compatible(fps,current.getRefreshRate()))return;
            Display.Mode best=null;
            for(Display.Mode candidate:display.getSupportedModes())if(candidate.getPhysicalWidth()==current.getPhysicalWidth()&&candidate.getPhysicalHeight()==current.getPhysicalHeight()&&VideoCadence.compatible(fps,candidate.getRefreshRate())&&(best==null||candidate.getRefreshRate()<best.getRefreshRate()))best=candidate;
            if(best==null){restoreMode();return;}
            Window window=activity.getWindow();WindowManager.LayoutParams attrs=window.getAttributes();
            if(!override){previousMode=attrs.preferredDisplayModeId;override=true;}
            requestedMode=best.getModeId();attrs.preferredDisplayModeId=requestedMode;window.setAttributes(attrs);
        }
        public float refreshRate(){Display d=activity.getWindow().getDecorView().getDisplay();return d==null?0:d.getRefreshRate();}
        public void clear(){
            try{if(surface.isValid())surface.setFrameRate(0,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);}
            finally{restoreMode();}
        }
        private void restoreMode(){if(!override)return;Window window=activity.getWindow();WindowManager.LayoutParams attrs=window.getAttributes();if(attrs.preferredDisplayModeId==requestedMode){attrs.preferredDisplayModeId=previousMode;window.setAttributes(attrs);}override=false;}
    }
}
