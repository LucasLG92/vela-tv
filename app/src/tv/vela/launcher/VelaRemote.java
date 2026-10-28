package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.KeyEvent;
import android.widget.EditText;
import org.json.*;
import java.util.*;

/** Profile-owned foreground shortcuts. No accessibility service or controller-brand assumptions. */
final class VelaRemote {
    static final int LIMIT=24,HOLD_MS=650;
    static final String[] ACTIONS={"home","tv","apps","inputs","search","voice","settings","profiles"};
    private static final String[] LEGACY={"search_key","voice_key","profiles_key"};
    static KeyEvent gamepad(KeyEvent e){int key=e.getKeyCode(),mapped=key==KeyEvent.KEYCODE_BUTTON_A?KeyEvent.KEYCODE_DPAD_CENTER:key==KeyEvent.KEYCODE_BUTTON_B?KeyEvent.KEYCODE_BACK:key;return mapped==key?e:new KeyEvent(e.getDownTime(),e.getEventTime(),e.getAction(),mapped,e.getRepeatCount(),e.getMetaState(),e.getDeviceId(),e.getScanCode(),e.getFlags(),e.getSource());}
    static boolean allowed(int key){return key>=7&&key<=KeyEvent.getMaxKeyCode()&&!reserved(key);}
    static boolean reserved(int key){return key==KeyEvent.KEYCODE_HOME||key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE||key==KeyEvent.KEYCODE_POWER||key==KeyEvent.KEYCODE_SLEEP||key==KeyEvent.KEYCODE_WAKEUP||key==KeyEvent.KEYCODE_SOFT_SLEEP||key==KeyEvent.KEYCODE_APP_SWITCH||key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE||key==KeyEvent.KEYCODE_MUTE||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_BUTTON_B||key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT||key>=KeyEvent.KEYCODE_DPAD_UP_LEFT&&key<=KeyEvent.KEYCODE_DPAD_DOWN_RIGHT||key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN||key==KeyEvent.KEYCODE_LAST_CHANNEL;}
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-remote");}
    static final class Binding {
        final int key,scan,gesture;final String action;
        Binding(int key,int scan,int gesture,String action){this.key=key;this.scan=key==0?scan:0;this.gesture=gesture;this.action=action;}
        boolean button(int key,int scan){return this.key==key&&(key!=0||this.scan==scan);}
        boolean matches(KeyEvent event){return button(event.getKeyCode(),event.getScanCode());}
        JSONObject json()throws JSONException{return new JSONObject().put("key",key).put("scan",scan).put("gesture",gesture).put("action",action);}
    }
    static boolean allowed(KeyEvent event){return allowed(event.getKeyCode())||event.getKeyCode()==0&&event.getScanCode()>0&&event.getScanCode()<=65535;}
    private static boolean action(String action){return Arrays.asList(ACTIONS).contains(action)||action.matches("app:[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");}
    static void validate(JSONArray array)throws JSONException{
        if(array.length()>LIMIT)throw new JSONException("Too many shortcuts");Set<String> seen=new HashSet<>();
        for(int i=0;i<array.length();i++){JSONObject j=array.getJSONObject(i);if(j.length()!=4)throw new JSONException("Unknown shortcut fields");for(Iterator<String> it=j.keys();it.hasNext();)if(!Arrays.asList("key","scan","gesture","action").contains(it.next()))throw new JSONException("Unknown shortcut field");for(String k:Arrays.asList("key","scan","gesture"))if(!(j.get(k) instanceof Integer))throw new JSONException("Invalid key type");int key=j.getInt("key"),scan=j.getInt("scan"),gesture=j.getInt("gesture");String action=j.getString("action");if(!(allowed(key)&&scan==0||key==0&&scan>0&&scan<=65535)||gesture<0||gesture>1||action.length()>240||!action(action)||!seen.add(key+":"+scan+":"+gesture))throw new JSONException("Invalid shortcut");}
    }
    static List<Binding> read(Context c){
        List<Binding> out=new ArrayList<>();SharedPreferences p=prefs(c);try{String raw=p.getString("bindings","[]");if(raw.length()>8192)throw new JSONException("Shortcuts too large");JSONArray array=new JSONArray(raw);validate(array);for(int i=0;i<array.length();i++){JSONObject j=array.getJSONObject(i);out.add(new Binding(j.getInt("key"),j.getInt("scan"),j.getInt("gesture"),j.getString("action")));}}catch(JSONException|ClassCastException invalid){out.clear();}
        for(String old:LEGACY){int key=p.getInt(old,-1);if(allowed(key)&&find(out,key,0,0)==null&&out.size()<LIMIT)out.add(new Binding(key,0,0,old.equals("voice_key")?"voice":old.equals("profiles_key")?"profiles":"search"));}return out;
    }
    static Binding find(List<Binding> list,int key,int scan,int gesture){for(Binding b:list)if(b.button(key,scan)&&b.gesture==gesture)return b;return null;}
    private static void save(Context c,List<Binding> list){try{JSONArray a=new JSONArray();for(Binding b:list)a.put(b.json());validate(a);SharedPreferences.Editor edit=prefs(c).edit().putString("bindings",a.toString());for(String old:LEGACY)edit.remove(old);if(!edit.commit())throw new IllegalStateException("Shortcuts unavailable");}catch(JSONException invalid){throw new IllegalArgumentException(invalid);}}
    static void set(Context c,int key,int scan,int gesture,String action){List<Binding> list=read(c);Binding old=find(list,key,scan,gesture);if(old!=null)list.remove(old);if(!action.isEmpty())list.add(new Binding(key,scan,gesture,action));save(c,list);}
    static void remove(Context c,int key,int scan){List<Binding> list=read(c);list.removeIf(b->b.button(key,scan));save(c,list);}
    static String button(int key,int scan){if(key==0)return L10n.text("Remote button")+" · "+scan;if(key==KeyEvent.KEYCODE_PROG_RED)return L10n.text("Red button");if(key==KeyEvent.KEYCODE_PROG_GREEN)return L10n.text("Green button");if(key==KeyEvent.KEYCODE_PROG_YELLOW)return L10n.text("Yellow button");if(key==KeyEvent.KEYCODE_PROG_BLUE)return L10n.text("Blue button");return KeyEvent.keyCodeToString(key).replace("KEYCODE_","").replace('_',' ');}
    static String label(Context c,String action){if(action.isEmpty())return L10n.text("Original action");if(action.startsWith("app:")){String pkg=action.substring(4);try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(android.content.pm.PackageManager.NameNotFoundException missing){return L10n.text("App unavailable");}}return L10n.text(action.equals("home")?"Home":action.equals("tv")?"Live TV":action.equals("apps")?"Apps":action.equals("inputs")?"HDMI inputs":action.equals("search")?"Search":action.equals("voice")?"Search by voice":action.equals("settings")?"Settings":action.equals("profiles")?"Profiles":"Profiles");}
    static String gesture(int gesture){return L10n.text(gesture==0?"Short press":"Long press");}
    static String summary(Context c,int key,int scan){List<Binding> list=read(c);Binding tap=find(list,key,scan,0),hold=find(list,key,scan,1);return gesture(0)+" · "+label(c,tap==null?"":tap.action)+"   /   "+gesture(1)+" · "+(hold==null?L10n.text("Same as short press"):label(c,hold.action));}
    static boolean voice(Context c,int key){Binding b=find(read(c),key,0,0);return b!=null&&b.action.equals("voice");}
    static int destination(Context c,KeyEvent e){if(e.getAction()!=KeyEvent.ACTION_DOWN||e.getRepeatCount()!=0||c instanceof Activity&&((Activity)c).getCurrentFocus() instanceof EditText)return -1;Binding b=find(read(c),e.getKeyCode(),e.getScanCode(),0);return b!=null?(b.action.equals("search")?0:b.action.equals("profiles")?4:-1):e.getKeyCode()==KeyEvent.KEYCODE_SEARCH?0:-1;}
    static void assign(Context c,String action,int key){if(!Arrays.asList(LEGACY).contains(action)||!allowed(key))throw new IllegalArgumentException("Reserved remote button");SharedPreferences.Editor edit=prefs(c).edit();for(String other:LEGACY)if(!other.equals(action)&&prefs(c).getInt(other,-1)==key)edit.remove(other);List<Binding> list=read(c);list.removeIf(b->b.key==key||b.action.equals(action.equals("voice_key")?"voice":action.equals("profiles_key")?"profiles":"search"));try{JSONArray array=new JSONArray();for(Binding b:list)array.put(b.json());edit.putString("bindings",array.toString()).putInt(action,key).commit();}catch(JSONException impossible){throw new IllegalStateException(impossible);}}

    /** One pending press and one delayed callback, allocated only when a bound button is pressed. */
    static final class Dispatcher {
        interface Host {void activate(String action);void replay(KeyEvent event);}
        private final Activity activity;private final Host host;private final Handler main=new Handler(Looper.getMainLooper());
        private KeyEvent down,discarded;private Binding tap,hold;private boolean fired,replaying;private String signature="";private List<Binding> cached=Collections.emptyList();
        private final Runnable longPress=this::performLongPress;
        private void performLongPress(){if(down!=null&&!fired&&hold!=null){fired=true;host.activate(hold.action);}}
        Dispatcher(Activity activity,Host host){this.activity=activity;this.host=host;}
        private List<Binding> bindings(){SharedPreferences p=prefs(activity);String key=VelaWorkspace.active(activity)+p.getString("bindings","")+p.getInt("search_key",-1)+":"+p.getInt("voice_key",-1)+":"+p.getInt("profiles_key",-1);if(!key.equals(signature)){signature=key;cached=read(activity);}return cached;}
        boolean dispatch(KeyEvent event){
            if(replaying)return false;
            if(discarded!=null&&same(discarded,event)){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)discarded=null;else{if(event.getAction()==KeyEvent.ACTION_UP)discarded=null;return true;}}
            if(down!=null&&down.getDeviceId()==event.getDeviceId()&&down.getKeyCode()==event.getKeyCode()&&(down.getKeyCode()!=0||down.getScanCode()==event.getScanCode())){
                if(event.getAction()==KeyEvent.ACTION_UP){KeyEvent original=down;Binding shortAction=tap,longAction=hold;boolean done=fired;reset();if(event.isCanceled())return true;if(!done&&longAction!=null&&event.getEventTime()-original.getDownTime()>=HOLD_MS){host.activate(longAction.action);return true;}if(!done){if(shortAction!=null)host.activate(shortAction.action);else{replaying=true;try{host.replay(original);host.replay(event);}finally{replaying=false;}}}return true;}
                if(event.getAction()==KeyEvent.ACTION_DOWN){if(hold!=null&&event.isLongPress())longPress.run();return true;}
            }
            if(down!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)cancel();
            if(event.getAction()!=KeyEvent.ACTION_DOWN||event.getRepeatCount()!=0||!allowed(event))return false;
            List<Binding> list=bindings();Binding shortAction=find(list,event.getKeyCode(),event.getScanCode(),0),longAction=find(list,event.getKeyCode(),event.getScanCode(),1);if(shortAction==null&&longAction==null)return false;if(activity.getCurrentFocus() instanceof EditText&&!(shortAction!=null&&shortAction.action.equals("voice")||longAction!=null&&longAction.action.equals("voice")))return false;
            reset();down=new KeyEvent(event);tap=shortAction;hold=longAction;if(hold!=null)main.postDelayed(longPress,HOLD_MS);return true;
        }
        private static boolean same(KeyEvent a,KeyEvent b){return a.getDeviceId()==b.getDeviceId()&&a.getKeyCode()==b.getKeyCode()&&(a.getKeyCode()!=0||a.getScanCode()==b.getScanCode());}
        private void reset(){main.removeCallbacks(longPress);down=null;tap=null;hold=null;fired=false;}
        void cancel(){if(down!=null)discarded=down;reset();}
    }
}
