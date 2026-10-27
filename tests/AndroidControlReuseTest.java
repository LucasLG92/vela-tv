package tv.vela.launcher;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;import java.util.*;

/** Same controls before/after optimisation; records view creation without claiming hardware RAM. */
public final class AndroidControlReuseTest extends Instrumentation {
    private Activity activity;private boolean expected=true;
    Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
    void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    public void onCreate(Bundle args){super.onCreate(args);expected=!"false".equals(args.getString("reuse"));start();}
    public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();try{
        check(Build.MODEL.contains("sdk")||Build.FINGERPRINT.contains("generic"),"Owned emulator required");L10n.choose(c,"en");Ui.init(c);
        boolean lite=c.getPackageName().endsWith(".lite");c.getSharedPreferences(lite?"lite_updates":"updates",0).edit().putBoolean("automatic",false).commit();c.getSharedPreferences("personalization",0).edit().putBoolean("initialized",true).putBoolean("complete",true).commit();
        activity=startActivitySync(new Intent().setClassName(c.getPackageName(),"tv.vela.launcher."+(lite?"LiteHomeActivity":"HomeActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));int[] clicks={0};VelaPlaybackPanel[] panel={null};
        ui(()->{panel[0]=new VelaPlaybackPanel(activity,"Demo",()->Arrays.asList(new VelaPlaybackPanel.Item("Picture format",clicks[0]%2==0?"Original":"Zoomed",()->clicks[0]++),new VelaPlaybackPanel.Item("Signal","1920 × 1080",null),new VelaPlaybackPanel.Item("Audio","Default",()->{}),new VelaPlaybackPanel.Item("Subtitles","Off",()->{})),()->{});activity.setContentView(panel[0]);});
        LinearLayout rows=(LinearLayout)field(panel[0],"list");Set<View> created=Collections.newSetFromMap(new IdentityHashMap<>());View[] original=new View[4];ui(()->{for(int i=0;i<4;i++)original[i]=rows.getChildAt(i);});
        for(int n=0;n<=40;n++){
            ui(()->{for(int i=0;i<rows.getChildCount();i++)created.add(rows.getChildAt(i));});
            if(expected)for(int i=0;i<4;i++)check(rows.getChildAt(i)==original[i],"Changing an option recreated control rows");
            check(!rows.getChildAt(1).isFocusable(),"Read-only signal became a focus stop");
            if(n<40)ui(()->rows.getChildAt(0).performClick());
        }
        check(clicks[0]==40&&rows.getChildAt(0).hasFocus(),"Repeated changes lost selection or focus");
        ui(()->panel[0].remote(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)));check(rows.getChildAt(2).hasFocus(),"Remote did not skip read-only signal");
        ui(panel[0]::close);check(rows.getChildCount()==0&&((List<?>)field(panel[0],"buttons")).isEmpty(),"Closed controls retained rows");
        result.putInt("unique_control_rows",created.size());result.putInt("option_changes",40);result.putString("result","PASS control lifecycle, remote focus and "+(expected?"view reuse":"baseline view count")+"; view identities measured, not device RAM.");
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}finally{if(activity!=null)ui(activity::finish);}finish(result.containsKey("error")?1:-1,result);}
}
