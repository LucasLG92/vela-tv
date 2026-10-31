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
    private Engine engine;private Panel panel,closing;private View origin;
    private String profile="";private boolean active,foreground=true,waitingPermission,listening;private int epoch;
    private final Runnable timeout=()->{if(!valid())return;if(listening)finishSpeech();else error(L10n.text("Voice search timed out."));};
    VelaVoiceSearch(Activity a,Host host){this(a,host,SYSTEM);}
    VelaVoiceSearch(Activity a,Host host,Factory factory){activity=a;this.host=host;this.factory=factory;}
    boolean active(){return active;}
    boolean start(){
        if(active){if(listening)finishSpeech();else cancel();return true;}
        if(!foreground||activity.isFinishing()||activity.isDestroyed())return false;
        clearClosing();active=true;profile=VelaWorkspace.active(activity);origin=activity.getCurrentFocus();
        panel=new Panel();((android.widget.FrameLayout)activity.findViewById(android.R.id.content)).addView(panel,new FrameLayout.LayoutParams(-1,-1));
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            waitingPermission=true;panel.notice(L10n.text("Microphone permission"),L10n.text("Allow the microphone to use Vela voice search."),false);
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
        if(!valid())return;waitingPermission=false;release();panel.ready(false);panel.quiet();
        panel.status(L10n.text("Preparing microphone…"));
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
                public void onEndOfSpeech(){if(current()){listening=false;panel.processing();panel.status(L10n.text("Recognising…"));deadline(8000);}}
                public void onPartialResults(Bundle b){} // The rail is the listening indication; no transcript panel.
                public void onResults(Bundle b){if(!current())return;String text=words(b);if(text.isEmpty()){error(L10n.text("No speech recognised. Try again."));return;}complete(text);}
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
        if(!valid())return;listening=false;panel.processing();panel.status(L10n.text("Recognising…"));
        try{if(engine!=null)engine.stop();if(active)deadline(8000);}catch(RuntimeException ended){error(L10n.text("No speech recognised. Try again."));}
    }
    private void error(String text){
        if(!active)return;release();panel.ready(false);panel.notice(L10n.text("Vela voice search"),text,true);
    }
    private void release(){
        epoch++;listening=false;main.removeCallbacks(timeout);Engine previous=engine;engine=null;
        if(previous!=null){try{previous.cancel();}catch(RuntimeException ignored){}try{previous.destroy();}catch(RuntimeException ignored){}}
    }
    private void clearClosing(){Panel previous=closing;closing=null;if(previous!=null){previous.animate().withEndAction(null).cancel();if(previous.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)previous.getParent()).removeView(previous);}}
    private void complete(String text){
        String owner=profile;Panel finished=panel;panel=null;cancel();if(finished==null)return;
        finished.signal.freeze();closing=finished;
        Runnable deliver=()->{if(closing!=finished)return;clearClosing();if(foreground&&!activity.isFinishing()&&!activity.isDestroyed()&&owner.equals(VelaWorkspace.active(activity)))host.phrase(text);};
        if(VelaFocus.reduced(activity))deliver.run();else finished.animate().alpha(0).setDuration(180).withEndAction(deliver).start();
    }
    void cancel(){
        clearClosing();active=false;waitingPermission=false;profile="";release();
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
        if(!panel.showingPrompt()){
            if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A)&&event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()&&listening)finishSpeech();
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){View focused=activity.getCurrentFocus();if(focused==panel.retry||focused==panel.keyboard||focused==panel.cancel)focused.performClick();}return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_TAB)){
            List<View> buttons=new ArrayList<>();if(panel.retry.getVisibility()==View.VISIBLE)buttons.add(panel.retry);buttons.add(panel.keyboard);buttons.add(panel.cancel);
            int at=buttons.indexOf(activity.getCurrentFocus()),direction=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP?-1:1;buttons.get(Math.max(0,Math.min(buttons.size()-1,at+direction))).requestFocus();
        }
        return true;
    }
    /** Quiet during capture/processing; permission and failure controls are created only if needed. */
    private final class Panel extends FrameLayout {
        final Signal signal;private final FrameLayout canvas;private FrameLayout strip;private View anchor;private final Rect screenBounds=new Rect();private final int[] canvasAt=new int[2];
        private TextView status,words,retry,keyboard,cancel;
        Panel(){
            super(activity);setFocusable(false);
            VelaReading.Stage stage=new VelaReading.Stage(activity,1920,1080);addView(stage,new LayoutParams(-1,-1));canvas=new FrameLayout(activity);stage.addView(canvas);
            anchor=activity.findViewById(android.R.id.content).findViewWithTag("vela-sidebar");signal=new Signal(new VoiceColors(VelaReading.accent(activity)));put(canvas,signal,10,10,104,1060);
            canvas.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->geometry());
            signal.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
        private void geometry(){
            float x=24,y=24,w=76,h=1032;
            if(anchor!=null&&anchor.isAttachedToWindow()&&anchor.getGlobalVisibleRect(screenBounds)&&canvas.getWidth()>0){canvas.getLocationOnScreen(canvasAt);float sx=canvas.getScaleX(),sy=canvas.getScaleY();if(sx>0&&sy>0){x=(screenBounds.left-canvasAt[0])/sx;y=(screenBounds.top-canvasAt[1])/sy;w=screenBounds.width()/sx;h=screenBounds.height()/sy;}}
            int left=Math.round(x)-14,top=Math.round(y)-14,width=Math.max(48,Math.round(w))+28,height=Math.max(80,Math.round(h))+28;FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)signal.getLayoutParams();
            if(p.leftMargin!=left||p.topMargin!=top||p.width!=width||p.height!=height){p.leftMargin=left;p.topMargin=top;p.width=width;p.height=height;signal.setLayoutParams(p);}
        }
        boolean showingPrompt(){return strip!=null&&strip.getVisibility()==View.VISIBLE;}
        void quiet(){if(strip!=null)strip.setVisibility(View.GONE);setBackground(null);}
        void notice(String title,String detail,boolean canRetry){
            ensurePrompt();setBackgroundColor(0x33000000);strip.setVisibility(View.VISIBLE);status(title);words.setText(detail);retry.setVisibility(canRetry?View.VISIBLE:View.GONE);(canRetry?retry:cancel).requestFocus();
        }
        private void ensurePrompt(){
            if(strip!=null)return;
            strip=new FrameLayout(activity);strip.setBackground(VelaTheme.panel());put(canvas,strip,150,806,1690,224);
            status=label("",30);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);put(strip,status,34,20,1060,48);
            words=label("",24);words.setMaxLines(2);words.setEllipsize(android.text.TextUtils.TruncateAt.END);put(strip,words,34,76,1060,72);
            TextView privacy=label(L10n.text("Uses your TV's speech service. Back cancels."),17);privacy.setTextColor(VelaTheme.SECONDARY);put(strip,privacy,34,168,1060,36);
            retry=button(L10n.text("Try again"),()->{if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){waitingPermission=true;notice(L10n.text("Microphone permission"),L10n.text("Allow the microphone to use Vela voice search."),false);activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);}else begin();});
            keyboard=button(L10n.text("Use keyboard"),()->{cancel();host.keyboard();});cancel=button(L10n.text("Cancel"),VelaVoiceSearch.this::cancel);
            put(strip,retry,1140,20,510,52);put(strip,keyboard,1140,84,510,52);put(strip,cancel,1140,148,510,52);
        }
        void status(String text){if(status!=null)status.setText(text);if(!android.text.TextUtils.equals(text,signal.getContentDescription()))signal.setContentDescription(text);}
        void ready(boolean yes){signal.listening(yes);}
        void processing(){signal.thinking();}
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
    /** A clipped, fluid light field follows the actual navigation surface. No full-screen render layer. */
    private final class Signal extends View {
        private final Paint paint=new Paint(3);private final RectF rect=new RectF(),bar=new RectF();private final Matrix matrix=new Matrix();
        private final Path clip=new Path(),ribbon=new Path();private final float[] centers=new float[17],widths=new float[17];
        private final RadialGradient blue,violet,pearl;private final LinearGradient sheet;private ValueAnimator pulse;
        private float phase,flow,energy,target,processMix,intro,noise=-2,peak=8;private boolean ready,reduced,processing;private long started,lastFrame,lastVoice;
        Signal(VoiceColors colors){
            super(activity);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);blue=VoiceColors.bloom(colors.first);violet=VoiceColors.bloom(colors.second);pearl=VoiceColors.bloom(0xFFE8F3FF);
            sheet=new LinearGradient(0,0,1,1,new int[]{colors.first,colors.third,colors.second},null,Shader.TileMode.CLAMP);
        }
        protected void onSizeChanged(int w,int h,int ow,int oh){bar.set(14,14,w-14,h-14);clip.rewind();clip.addRoundRect(bar,32,32,Path.Direction.CW);}
        void level(float rms){if(!ready||reduced||Float.isNaN(rms)||Float.isInfinite(rms))return;float value=Math.max(-12,Math.min(30,rms));noise=Math.min(noise,value);peak=Math.max(8,Math.max(value,peak*.995f));target=(float)Math.pow(Math.max(0,Math.min(1,(value-noise)/Math.max(6,peak-noise))),.8);lastVoice=SystemClock.uptimeMillis();}
        void listening(boolean value){state(value,false);}void thinking(){state(false,true);}
        void freeze(){if(pulse!=null){pulse.cancel();pulse=null;}}
        private void frame(){if(getParent()==null)return;Panel owner=panel;if(owner!=null&&owner.signal==this)owner.geometry();invalidate();}
        private void state(boolean value,boolean thinking){
            if(ready==value&&processing==thinking)return;boolean was=ready||processing;ready=value;processing=thinking;reduced=VelaFocus.reduced(activity);
            if(!value&&!thinking){freeze();energy=target=0;intro=0;frame();return;}
            if(!was){started=SystemClock.uptimeMillis();phase=flow=energy=target=processMix=0;lastFrame=lastVoice=0;noise=-2;peak=8;}
            if(reduced){freeze();intro=1;processMix=thinking?1:0;frame();return;}
            if(pulse==null){pulse=ValueAnimator.ofFloat(0,1);pulse.setDuration(60000);pulse.setInterpolator(new android.view.animation.LinearInterpolator());pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.addUpdateListener(a->{long now=SystemClock.uptimeMillis();if(now-lastFrame<33)return;float dt=lastFrame==0?.033f:Math.min(.1f,(now-lastFrame)/1000f);lastFrame=now;phase=(now-started)/1000f;intro=Math.min(1,phase/.32f);intro=1-(1-intro)*(1-intro)*(1-intro);if(now-lastVoice>240)target*=.78f;float desired=ready?target:0;energy+=(desired-energy)*(1-(float)Math.exp(-dt/(desired>energy?.055f:.26f)));processMix+=((processing?1:0)-processMix)*(1-(float)Math.exp(-dt/.3f));flow+=dt*(.48f-.32f*processMix+.22f*energy);frame();});pulse.start();}
            frame();
        }
        protected void onDetachedFromWindow(){freeze();ready=processing=false;super.onDetachedFromWindow();}
        private void field(Canvas c,RadialGradient shader,float x,float y,float w,float h,float rotation,int alpha){matrix.setScale(w,h);matrix.postRotate(rotation);matrix.postTranslate(x,y);shader.setLocalMatrix(matrix);paint.setShader(shader);paint.setStyle(Paint.Style.FILL);paint.setAlpha(Math.max(0,Math.min(255,alpha)));c.drawRect(bar,paint);}
        private void sheet(Canvas c,float offset,float amplitude,int alpha){
            float w=bar.width(),h=bar.height();for(int i=0;i<=16;i++){float y=i/16f,envelope=(float)Math.sin(y*Math.PI);centers[i]=bar.left+w*(.5f+.22f*(float)Math.sin(y*7.2f+flow*1.2f+offset)+.1f*(float)Math.sin(y*13.4f-flow*.63f));widths[i]=w*(.075f+.07f*amplitude)*envelope;}
            ribbon.rewind();ribbon.moveTo(centers[0],bar.top);for(int i=1;i<=16;i++){float y0=bar.top+(i-1)*h/16f,y1=bar.top+i*h/16f;ribbon.cubicTo(centers[i-1]-widths[i-1],y0+h/32,centers[i]-widths[i],y1-h/32,centers[i]-widths[i],y1);}for(int i=15;i>=0;i--){float y0=bar.top+(i+1)*h/16f,y1=bar.top+i*h/16f;ribbon.cubicTo(centers[i+1]+widths[i+1],y0-h/32,centers[i]+widths[i],y1+h/32,centers[i]+widths[i],y1);}ribbon.close();
            matrix.setScale(w,h);matrix.postTranslate(bar.left,bar.top);sheet.setLocalMatrix(matrix);paint.setShader(sheet);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(12);paint.setAlpha(alpha/8);c.drawPath(ribbon,paint);paint.setStrokeWidth(5);paint.setAlpha(alpha/5);c.drawPath(ribbon,paint);paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);c.drawPath(ribbon,paint);
        }
        protected void onDraw(Canvas c){
            boolean lit=ready||processing;float w=bar.width(),h=bar.height(),strength=intro*(1-.55f*processMix),t=flow;
            if(lit&&w>0&&h>0){int save=c.save();c.clipPath(clip);
                field(c,blue,bar.left+w*(.22f+.24f*(float)Math.sin(t*.71f)),bar.top+h*(.3f+.18f*(float)Math.sin(t*.53f)),w*.88f,h*(.29f+.06f*energy),12+12*(float)Math.sin(t*.3f),(int)((100+75*energy)*strength));
                field(c,violet,bar.left+w*(.78f+.13f*(float)Math.cos(t*.59f)),bar.top+h*(.7f+.17f*(float)Math.cos(t*.43f)),w*.95f,h*.3f,-15,(int)((100+60*energy)*strength));
                field(c,pearl,bar.left+w*.5f,bar.top+h*(.47f+.2f*(float)Math.sin(t*.67f)),w*.85f,h*.22f,8,(int)((25+34*energy)*strength));
                sheet(c,.3f,energy,(int)((32+35*energy)*strength));sheet(c,2.7f,energy*.7f,(int)((22+24*energy)*strength));c.restoreToCount(save);
            }
            // The original profile icon keeps its position in both rail and expanded drawer.
            float cx=bar.left+38,cy=bar.top+64;paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF1B1E27);paint.setAlpha(255);c.drawCircle(cx,cy,22,paint);
            if(lit){int save=c.save();c.clipRect(cx-42,cy-42,cx+42,cy+42);rect.set(bar);bar.set(cx-42,cy-42,cx+42,cy+42);field(c,blue,cx-9+5*(float)Math.sin(t),cy-5,33+energy*7,32,0,(int)(170*intro));field(c,violet,cx+9,cy+9+4*(float)Math.cos(t*.83f),30,28,0,(int)(145*intro));field(c,pearl,cx-7,cy-10,16,13,0,(int)(120*intro));bar.set(rect);c.restoreToCount(save);}
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(VelaTheme.WHITE);paint.setAlpha(lit?255:175);int badge=c.save();c.translate(cx,cy);c.scale(.82f,.82f);rect.set(-7,-16,7,5);c.drawRoundRect(rect,7,7,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.8f);paint.setStrokeCap(Paint.Cap.ROUND);rect.set(-13,-14,13,12);c.drawArc(rect,0,180,false,paint);c.drawLine(0,12,0,20,paint);c.drawLine(-6,20,6,20,paint);c.restoreToCount(badge);
        }
    }
}
