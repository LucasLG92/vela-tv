package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Native painted guide: focus must not move text or resize the programme footprint. */
public final class AndroidGuideFocusTest extends Instrumentation {
    private Activity activity;private Context context;private String profile,previous,phase="setup";
    private TvTimelineView timeline;private LiveTvData.Channel channel;private LiveTvData.Programme current,next;
    private Map<String,List<LiveTvData.Programme>> schedule;private long from,now;
    private final Bundle result=new Bundle();private LiveTvData.Programme described;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    static final class Text {
        final String value;final float x,y,top,bottom;
        Text(String v,float left,float baseline,Paint paint){value=v;x=left;y=baseline;top=baseline+paint.ascent();bottom=baseline+paint.descent();}
    }
    static final class RecordingCanvas extends Canvas {
        final List<Text> text=new ArrayList<>();final List<RectF> blocks=new ArrayList<>();
        RecordingCanvas(Bitmap target){super(target);}
        @Override public void drawText(String value,float x,float y,Paint paint){text.add(new Text(value,x,y,paint));super.drawText(value,x,y,paint);}
        @Override public void drawRoundRect(float left,float top,float right,float bottom,float rx,float ry,Paint paint){if(left>222&&top==58)blocks.add(new RectF(left,top,right,bottom));super.drawRoundRect(left,top,right,bottom,rx,ry,paint);}
        Text title(String value){for(Text t:text)if(t.value.equals(value))return t;throw new AssertionError("Painted title missing: "+value);}
        Text timing(float x){for(Text t:text)if(Math.abs(t.x-x)<1&&t.y>100&&t.value.contains("–"))return t;throw new AssertionError("Painted schedule missing");}
    }
    RecordingCanvas draw()throws Exception{final RecordingCanvas[] captured={null};ui(()->{Bitmap bitmap=Bitmap.createBitmap(1332,710,Bitmap.Config.ARGB_8888);RecordingCanvas canvas=new RecordingCanvas(bitmap);timeline.draw(canvas);captured[0]=canvas;bitmap.recycle();});return captured[0];}
    void same(RecordingCanvas before,RecordingCanvas after,String title)throws Exception{
        Text a=before.title(title),b=after.title(title);check(a.x==b.x&&a.y==b.y,"Focus moved the title baseline: "+a.y+" -> "+b.y);
        Text ta=before.timing(a.x),tb=after.timing(b.x);check(ta.x==tb.x&&ta.y==tb.y,"Focus moved the schedule baseline: "+ta.y+" -> "+tb.y);
        check(before.blocks.equals(after.blocks),"Focus resized the programme footprint");
    }
    LiveTvData.Channel demo(boolean lite)throws Exception{
        Constructor<?> constructor;
        if(lite){constructor=LiveTvData.Channel.class.getDeclaredConstructor(long.class,String.class,String.class,String.class,boolean.class,boolean.class,boolean.class);constructor.setAccessible(true);return (LiveTvData.Channel)constructor.newInstance(1L,"fixture.input","1","Demo",false,true,false);}
        constructor=LiveTvData.Channel.class.getDeclaredConstructor(String.class,String.class,String.class,String.class,List.class);constructor.setAccessible(true);return (LiveTvData.Channel)constructor.newInstance("demo","Demo","Demo","",Collections.emptyList());
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){context=getTargetContext();try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        boolean lite=context.getPackageName().endsWith(".lite");previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Guide test");VelaWorkspace.select(context,profile);VelaEdition.select(context,lite?"lite":"full");
        if(!lite){Class<?> setup=Class.forName("tv.vela.launcher.Personalization");for(String name:new String[]{"prepare","complete"}){Method method=setup.getDeclaredMethod(name,Context.class);method.setAccessible(true);method.invoke(null,context);}}
        L10n.choose(context,"en");Ui.init(context);context.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();
        activity=startActivitySync(new Intent().setClassName(context.getPackageName(),"tv.vela.launcher."+(lite?"LiteHomeActivity":"HomeActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        channel=demo(lite);now=System.currentTimeMillis()/1000;from=TvTimelineView.present(now);current=new LiveTvData.Programme(from-3600,from+3600,"Current programme","");next=new LiveTvData.Programme(from+3600,from+7200,"Next programme","");schedule=Collections.singletonMap(channel.id,Arrays.asList(current,next));
        FrameLayout root=new FrameLayout(activity);TextView parking=new TextView(activity);parking.setFocusable(true);parking.setFocusableInTouchMode(true);
        ui(()->{timeline=new TvTimelineView(activity,new TvTimelineView.Actions(){public void selected(LiveTvData.Channel c,LiveTvData.Programme p){described=p;}public void activate(LiveTvData.Channel c,LiveTvData.Programme p){}public void options(LiveTvData.Channel c){}public boolean channels(int n){return false;}public void time(int n,long cursor){}});timeline.rowHeight(106);root.addView(parking,new FrameLayout.LayoutParams(100,40));FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(1332,710);placement.topMargin=80;root.addView(timeline,placement);activity.setContentView(root);timeline.data(Collections.singletonList(channel),schedule,from,channel,false);parking.requestFocus();});
        phase="current focus geometry";RecordingCanvas idle=draw();ui(timeline::requestFocus);same(idle,draw(),current.title);
        phase="next focus geometry";ui(()->timeline.cursor(next.start));RecordingCanvas focused=draw();ui(parking::requestFocus);same(focused,draw(),next.title);ui(timeline::requestFocus);
        phase="idle redraw";RecordingCanvas stable=draw();for(int i=0;i<8;i++){ui(()->{timeline.minute(now+60);timeline.invalidate();});same(stable,draw(),next.title);check(timeline.programme()==next,"Idle tick moved the selected programme");}
        phase="refresh retains selection";ui(()->timeline.data(Collections.singletonList(channel),Collections.emptyMap(),from,channel,true));check(timeline.programme()==next&&described==next,"Refreshing the same guide cleared the selected programme");same(stable,draw(),next.title);
        ui(()->timeline.data(Collections.singletonList(channel),schedule,from,channel,false));same(stable,draw(),next.title);
        phase="long titles and reading sizes";
        LiveTvData.Programme longTitle=new LiveTvData.Programme(from-3600,from+5400,"A long programme title about journeys through spectacular landscapes and distant cities around the world","");
        Map<String,List<LiveTvData.Programme>> longSchedule=Collections.singletonMap(channel.id,Arrays.asList(longTitle,new LiveTvData.Programme(from+5400,from+10800,"Next programme","")));
        for(int size=0;size<3;size++){
            final int reading=size;ui(()->{VelaReading.prefs(context).edit().putInt("text",reading).commit();timeline.rowHeight(636/VelaReading.rows(context));timeline.data(Collections.singletonList(channel),longSchedule,from,channel,false);timeline.cursor(now);timeline.requestFocus();});
            RecordingCanvas painted=draw();Text timing=painted.timing(239);int lines=0;
            for(Text text:painted.text)if(text.x==239&&text.y>58&&text.y<timing.y){lines++;check(text.top>=58&&text.bottom<=timing.top-3,"Long title overlaps the schedule at reading size "+size);}
            check(lines>=1&&lines<=2,"Long title escaped its bounded line area");
            ui(parking::requestFocus);Text unfocused=draw().timing(239);check(timing.y==unfocused.y,"Reading preference reintroduced a focus jump");
        }
        ui(()->{VelaReading.prefs(context).edit().putInt("text",0).commit();timeline.rowHeight(106);timeline.requestFocus();});
        phase="honest empty guide";ui(()->timeline.data(Collections.singletonList(channel),Collections.emptyMap(),from,channel,false));check(timeline.programme()==null&&described==null,"Completed empty guide retained stale listings");
        phase="new time window";ui(()->timeline.data(Collections.singletonList(channel),Collections.emptyMap(),from+5400,channel,true));check(timeline.programme()==null,"New window borrowed old guide data");
        phase="rebound channel isolation";LiveTvData.Channel rebound=demo(lite);ui(()->{timeline.data(Collections.singletonList(channel),schedule,from,channel,false);timeline.data(Collections.singletonList(rebound),Collections.emptyMap(),from,rebound,true);});check(timeline.programme()==null,"Rebound channel records borrowed the previous guide");
        result.putString("result","PASS current/next focus geometry, unchanged programme footprint, idle minute/redraw stability, retained selection during same-window refresh, honest completed gaps and new-window isolation. Shared native guide, synthetic metadata, no stream or tuner opened.");
    }catch(Throwable error){result.putString("error",phase+"\n"+android.util.Log.getStackTraceString(error));}finally{try{if(activity!=null)ui(activity::finish);if(profile!=null){VelaWorkspace.select(context,previous);VelaWorkspace.delete(context,profile);}}catch(Exception ignored){}}finish(result.containsKey("error")?1:-1,result);}
}
