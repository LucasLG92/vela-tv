package tv.vela.launcher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A one-shot, foreground voice session. Vela owns the UI; Android supplies transcription. */
final class VelaVoiceSearch {
    static final int PERMISSION=8341;
    interface Host {void phrase(String text);void keyboard();}
    interface Engine {void start(Intent intent,RecognitionListener listener);void stop();void cancel();void destroy();}
    interface Factory {Engine create(Activity activity);}
    static final Factory SYSTEM=activity->{
        SpeechRecognizer r;
        if(Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(activity))r=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
        else{if(!SpeechRecognizer.isRecognitionAvailable(activity))throw new IllegalStateException("No speech service");r=SpeechRecognizer.createSpeechRecognizer(activity);}
        final SpeechRecognizer recognizer=r;
        return new Engine(){
            public void start(Intent intent,RecognitionListener listener){recognizer.setRecognitionListener(listener);recognizer.startListening(intent);}
            public void stop(){recognizer.stopListening();}
            public void cancel(){recognizer.cancel();}
            public void destroy(){recognizer.destroy();}
        };
    };
    private final Activity activity;private final Host host;private final Factory factory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Engine engine;private Panel panel;private View origin;
    private String profile="";private boolean active,foreground=true,waitingPermission,listening;private int epoch;
    private final Runnable timeout=()->{if(!valid())return;if(listening)finishSpeech();else error(L10n.text("Voice search timed out."));};
    VelaVoiceSearch(Activity a,Host host){this(a,host,SYSTEM);}
    VelaVoiceSearch(Activity a,Host host,Factory factory){activity=a;this.host=host;this.factory=factory;}
    boolean active(){return active;}
    boolean start(){
        if(active){if(listening)finishSpeech();else cancel();return true;}
        if(!foreground||activity.isFinishing()||activity.isDestroyed())return false;
        active=true;profile=VelaWorkspace.active(activity);origin=activity.getCurrentFocus();
        panel=new Panel();((android.widget.FrameLayout)activity.findViewById(android.R.id.content)).addView(panel,new FrameLayout.LayoutParams(-1,-1));
        panel.cancel.requestFocus();
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            waitingPermission=true;panel.status(L10n.text("Microphone permission"));panel.words.setText(L10n.text("Allow the microphone to use Vela voice search."));
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);
        }else begin();
        return true;
    }
    private boolean valid(){
        if(!active)return false;
        if(!profile.equals(VelaWorkspace.active(activity))){cancel();return false;}
        return foreground;
    }
    private void begin(){
        if(!valid())return;waitingPermission=false;release();panel.ready(false);panel.retry.setVisibility(View.GONE);
        panel.status(L10n.text("Preparing microphone…"));panel.words.setText(L10n.text("Search or say a channel, app or HDMI input"));
        int token=epoch;
        try{
            engine=factory.create(activity);
            Intent request=VelaVoice.recognizer(activity).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            engine.start(request,new RecognitionListener(){
                private boolean current(){return token==epoch&&valid();}
                public void onReadyForSpeech(Bundle b){if(!current())return;listening=true;panel.ready(true);panel.status(L10n.text("Listening…"));deadline(20000);}
                public void onBeginningOfSpeech(){if(current())panel.status(L10n.text("Listening…"));}
                public void onRmsChanged(float rms){if(current()&&listening)panel.signal.level(rms);}
                public void onBufferReceived(byte[] audio){} // Never retain, copy or log microphone buffers.
                public void onEndOfSpeech(){if(current()){listening=false;panel.ready(false);panel.status(L10n.text("Recognising…"));deadline(8000);}}
                public void onPartialResults(Bundle b){if(current()){String text=words(b);if(!text.isEmpty())panel.words.setText(text);}}
                public void onResults(Bundle b){if(!current())return;String text=words(b);if(text.isEmpty()){error(L10n.text("No speech recognised. Try again."));return;}cancel();host.phrase(text);}
                public void onError(int code){if(!current())return;error(L10n.text(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Microphone permission is required.":code==SpeechRecognizer.ERROR_NETWORK||code==SpeechRecognizer.ERROR_NETWORK_TIMEOUT?"The speech service could not connect.":code==SpeechRecognizer.ERROR_NO_MATCH||code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?"No speech recognised. Try again.":"Voice recognition is unavailable. Try again or use the keyboard."));}
                public void onEvent(int code,Bundle b){}
            });
            if(active&&token==epoch)deadline(listening?20000:10000);
        }catch(RuntimeException unavailable){error(L10n.text("Voice recognition is unavailable. Try again or use the keyboard."));}
    }
    private static String words(Bundle b){
        if(b==null)return "";try{ArrayList<String> list=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);for(int i=0;list!=null&&i<Math.min(3,list.size());i++){String s=VelaWorkspace.clean(list.get(i),80);if(!s.isEmpty())return s;}}catch(RuntimeException invalid){}return "";
    }
    private void deadline(int delay){main.removeCallbacks(timeout);main.postDelayed(timeout,delay);}
    private void finishSpeech(){
        if(!valid())return;listening=false;panel.ready(false);panel.status(L10n.text("Recognising…"));
        try{if(engine!=null)engine.stop();if(active)deadline(8000);}catch(RuntimeException ended){error(L10n.text("No speech recognised. Try again."));}
    }
    private void error(String text){
        if(!active)return;release();panel.ready(false);panel.status(L10n.text("Vela voice search"));panel.words.setText(text);panel.retry.setVisibility(View.VISIBLE);panel.retry.requestFocus();
    }
    private void release(){
        epoch++;listening=false;main.removeCallbacks(timeout);Engine previous=engine;engine=null;
        if(previous!=null){try{previous.cancel();}catch(RuntimeException ignored){}try{previous.destroy();}catch(RuntimeException ignored){}}
    }
    void cancel(){
        active=false;waitingPermission=false;profile="";release();
        if(panel!=null){panel.ready(false);if(panel.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)panel.getParent()).removeView(panel);panel=null;}
        View restore=origin;origin=null;if(restore!=null&&restore.isAttachedToWindow()&&restore.isShown())restore.requestFocus();
    }
    void pause(){foreground=false;if(waitingPermission){release();if(panel!=null)panel.ready(false);}else cancel();}
    void resume(){foreground=true;if(active&&waitingPermission&&activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)begin();}
    boolean permission(int request){
        if(request!=PERMISSION)return false;
        if(!active||!waitingPermission)return true;
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){if(foreground)begin();}
        else{waitingPermission=false;error(L10n.text("Microphone permission is required."));}
        return true;
    }
    boolean remote(KeyEvent event){
        if(!active)return false;int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE||key==KeyEvent.KEYCODE_BUTTON_B){if(event.getAction()==KeyEvent.ACTION_UP)cancel();return true;}
        if(key==KeyEvent.KEYCODE_ASSIST||key==KeyEvent.KEYCODE_VOICE_ASSIST||key==KeyEvent.KEYCODE_HOME){cancel();return false;}
        if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE||key==KeyEvent.KEYCODE_POWER)return false;
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){View focused=activity.getCurrentFocus();if(focused==panel.retry||focused==panel.keyboard||focused==panel.cancel)focused.performClick();}return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_TAB)){
            List<View> buttons=new ArrayList<>();if(panel.retry.getVisibility()==View.VISIBLE)buttons.add(panel.retry);buttons.add(panel.keyboard);buttons.add(panel.cancel);
            int at=buttons.indexOf(activity.getCurrentFocus()),direction=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP?-1:1;buttons.get(Math.max(0,Math.min(buttons.size()-1,at+direction))).requestFocus();
        }
        return true;
    }
    private final class Panel extends FrameLayout {
        final TextView status,words,retry,keyboard,cancel;final Signal signal;final PanelGlow edge;
        Panel(){
            super(activity);setBackgroundColor(0x66000000);setFocusable(false);
            VelaReading.Stage stage=new VelaReading.Stage(activity,1920,1080);addView(stage,new LayoutParams(-1,-1));FrameLayout canvas=new FrameLayout(activity);stage.addView(canvas);
            VoiceColors colors=new VoiceColors(VelaReading.accent(activity));edge=new PanelGlow(colors);put(canvas,edge,134,790,1722,256);
            signal=new Signal(colors,edge);put(canvas,signal,0,0,124,1080);
            FrameLayout strip=new FrameLayout(activity);strip.setBackground(VelaTheme.panel());put(canvas,strip,150,806,1690,224);
            status=label(L10n.text("Vela voice search"),30);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);put(strip,status,34,20,1060,48);
            words=label("",24);words.setMaxLines(2);words.setEllipsize(android.text.TextUtils.TruncateAt.END);put(strip,words,34,76,1060,72);
            TextView privacy=label(L10n.text("Uses your TV's speech service. Back cancels."),17);privacy.setTextColor(VelaTheme.SECONDARY);put(strip,privacy,34,168,1060,36);
            retry=button(L10n.text("Try again"),()->{if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){waitingPermission=true;activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);}else begin();});retry.setVisibility(View.GONE);
            keyboard=button(L10n.text("Use keyboard"),()->{cancel();host.keyboard();});cancel=button(L10n.text("Cancel"),VelaVoiceSearch.this::cancel);
            put(strip,retry,1140,20,510,52);put(strip,keyboard,1140,84,510,52);put(strip,cancel,1140,148,510,52);
        }
        void status(String text){status.setText(text);signal.setContentDescription(text);}
        void ready(boolean yes){signal.listening(yes);}
        TextView label(String text,int size){TextView v=new TextView(activity);v.setText(text);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*VelaReading.scale(activity));v.setTypeface(Ui.medium);v.setTextColor(VelaTheme.WHITE);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
        TextView button(String text,Runnable action){TextView v=label(text,21);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setGravity(Gravity.CENTER);v.setDefaultFocusHighlightEnabled(false);v.setBackground(VelaTheme.row(false,VelaReading.accent(activity)));v.setOnFocusChangeListener((w,f)->{v.setBackground(VelaTheme.row(f,VelaReading.accent(activity)));v.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);});v.setOnClickListener(w->action.run());return v;}
        void put(FrameLayout parent,View child,int x,int y,int width,int height){LayoutParams p=new LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;parent.addView(child,p);}
    }
    /** Cached shaders give a soft light spill without blur, bitmaps or an off-screen layer. */
    private static final class VoiceColors {
        final int first,second,third;
        VoiceColors(int accent){
            float[] hsv=new float[3];Color.colorToHSV(accent,hsv);
            if(hsv[1]<.12f){first=0xFF97C6FA;second=0xFFC9B6F3;third=0xFFE6BDDA;}
            else{first=accent;hsv[0]=(hsv[0]+22)%360;second=Color.HSVToColor(hsv);hsv[0]=(hsv[0]+316)%360;third=Color.HSVToColor(hsv);}
        }
        int[] rim(){return new int[]{first,second,third,first};}
        static RadialGradient bloom(int color){return new RadialGradient(0,0,1,new int[]{color,(color&0xFFFFFF)|0x65000000,color&0xFFFFFF},new float[]{0,.48f,1},Shader.TileMode.CLAMP);}
    }
    /** Passive panel light, driven by the sidebar's single animation clock. */
    private final class PanelGlow extends View {
        private final Paint paint=new Paint(3);private final RectF rect=new RectF();private final Matrix matrix=new Matrix();
        private final LinearGradient rim;private final RadialGradient first,second;private boolean ready;private float phase,energy;
        PanelGlow(VoiceColors colors){super(activity);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);rim=new LinearGradient(0,0,1690,224,colors.rim(),null,Shader.TileMode.CLAMP);first=VoiceColors.bloom(colors.first);second=VoiceColors.bloom(colors.second);}
        void frame(boolean on,float phase,float energy){ready=on;this.phase=phase;this.energy=energy;invalidate();}
        private void bloom(Canvas c,RadialGradient shader,float x,float y,float width,float height,int alpha){matrix.setScale(width,height);matrix.postTranslate(x,y);shader.setLocalMatrix(matrix);paint.setShader(shader);paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);rect.set(x-width,y-height,x+width,y+height);c.drawOval(rect,paint);}
        @Override protected void onDraw(Canvas c){
            if(!ready)return;
            float wave=(float)Math.sin(phase*Math.PI*2),strength=.45f+.55f*energy;
            bloom(c,first,getWidth()*(.3f+.04f*wave),22,370+energy*90,22+energy*7,(int)(125*strength));
            bloom(c,second,getWidth()*(.7f-.04f*wave),getHeight()-22,350+energy*90,22+energy*7,(int)(110*strength));
            rect.set(16,16,getWidth()-16,getHeight()-16);paint.setShader(rim);paint.setStyle(Paint.Style.STROKE);
            for(int i=5;i>=1;i--){paint.setStrokeWidth(i*4);paint.setAlpha((int)((i==1?125:5+(6-i)*3)*strength));c.drawRoundRect(rect,VelaTheme.PANEL_RADIUS,VelaTheme.PANEL_RADIUS,paint);}
            paint.setShader(null);
        }
    }
    private final class Signal extends View {
        private final Paint paint=new Paint(3);private final RectF rect=new RectF();private final Matrix matrix=new Matrix();
        private final VoiceColors colors;private final PanelGlow edge;private final LinearGradient rim;private final SweepGradient orb;private final RadialGradient first,second;
        private ValueAnimator pulse;private float phase,energy,target;private boolean ready,reduced;private long lastFrame,lastVoice;
        Signal(VoiceColors colors,PanelGlow edge){
            super(activity);this.colors=colors;this.edge=edge;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            rim=new LinearGradient(24,24,100,1056,colors.rim(),null,Shader.TileMode.CLAMP);orb=new SweepGradient(62,88,colors.rim(),null);first=VoiceColors.bloom(colors.first);second=VoiceColors.bloom(colors.second);
        }
        void level(float rms){
            if(!ready||reduced)return;target=Float.isNaN(rms)?0:Math.max(0,Math.min(1,(rms+2)/12));lastVoice=SystemClock.uptimeMillis();
        }
        void listening(boolean value){
            if(ready==value)return;ready=value;if(pulse!=null){pulse.cancel();pulse=null;}phase=0;energy=target=0;lastFrame=lastVoice=0;reduced=VelaFocus.reduced(activity);
            if(value&&!reduced){
                pulse=ValueAnimator.ofFloat(0,1);pulse.setDuration(3200);pulse.setInterpolator(new android.view.animation.LinearInterpolator());pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.addUpdateListener(a->{long now=SystemClock.uptimeMillis();if(now-lastFrame<33)return;lastFrame=now;phase=(Float)a.getAnimatedValue();if(now-lastVoice>220)target*=.8f;energy+=(target-energy)*(target>energy?.4f:.17f);edge.frame(true,phase,energy);invalidate();});pulse.start();
            }
            edge.frame(value,0,0);invalidate();
        }
        @Override protected void onDetachedFromWindow(){if(pulse!=null){pulse.cancel();pulse=null;}ready=false;edge.frame(false,0,0);super.onDetachedFromWindow();}
        private void bloom(Canvas c,RadialGradient shader,float x,float y,float width,float height,int alpha){matrix.setScale(width,height);matrix.postTranslate(x,y);shader.setLocalMatrix(matrix);paint.setShader(shader);paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);rect.set(x-width,y-height,x+width,y+height);c.drawOval(rect,paint);}
        @Override protected void onDraw(Canvas c){
            float wave=reduced?0:(float)Math.sin(phase*Math.PI*2),strength=.5f+.5f*energy;
            if(ready){
                bloom(c,first,43,82,49+energy*10,59+energy*10,(int)(165*strength));bloom(c,second,81,96,42+energy*8,52+energy*8,(int)(150*strength));
                bloom(c,first,25,240+wave*35,22,180,(int)(66*strength));bloom(c,second,99,820-wave*35,22,180,(int)(62*strength));
                rect.set(24,24,100,1056);paint.setShader(rim);paint.setStyle(Paint.Style.STROKE);
                for(int i=5;i>=1;i--){paint.setStrokeWidth(i*3.6f);paint.setAlpha((int)((i==1?135:5+(6-i)*3)*strength));c.drawRoundRect(rect,38,38,paint);}
            }else{paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.3f);paint.setColor(VelaTheme.WHITE);paint.setAlpha(35);rect.set(24,24,100,1056);c.drawRoundRect(rect,38,38,paint);}
            // Inset the whole badge, including its reactive ring, from the sidebar edge.
            int badge=c.save();c.scale(.82f,.82f,62,88);
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF1C1D24);paint.setAlpha(255);c.drawCircle(62,88,26,paint);
            if(ready){matrix.setRotate(phase*360,62,88);orb.setLocalMatrix(matrix);paint.setShader(orb);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.2f);paint.setAlpha(225);c.drawCircle(62,88,28+energy*2,paint);paint.setShader(null);}
            paint.setStyle(Paint.Style.FILL);paint.setColor(VelaTheme.WHITE);paint.setAlpha(ready?255:170);rect.set(55,72,69,93);c.drawRoundRect(rect,7,7,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.8f);paint.setStrokeCap(Paint.Cap.ROUND);rect.set(49,74,75,100);c.drawArc(rect,0,180,false,paint);c.drawLine(62,100,62,108,paint);c.drawLine(56,108,68,108,paint);
            c.restoreToCount(badge);
        }
    }
}
