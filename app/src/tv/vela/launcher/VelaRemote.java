package tv.vela.launcher;

import android.content.*;
import android.view.KeyEvent;

/** Optional shortcuts are interpreted only in the foreground launcher. No accessibility interception. */
final class VelaRemote {
    static KeyEvent gamepad(KeyEvent e){int key=e.getKeyCode(),mapped=key==KeyEvent.KEYCODE_BUTTON_A?KeyEvent.KEYCODE_DPAD_CENTER:key==KeyEvent.KEYCODE_BUTTON_B?KeyEvent.KEYCODE_BACK:key;return mapped==key?e:new KeyEvent(e.getDownTime(),e.getEventTime(),e.getAction(),mapped,e.getRepeatCount(),e.getMetaState(),e.getDeviceId(),e.getScanCode(),e.getFlags(),e.getSource());}
    static boolean allowed(int key){return key>=7&&key<=288&&!reserved(key);}
    static boolean reserved(int key){return key==KeyEvent.KEYCODE_HOME||key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_POWER||key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_BUTTON_B||key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN||key==KeyEvent.KEYCODE_LAST_CHANNEL;}
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-remote");}
    static int destination(Context c,KeyEvent e){if(e.getAction()!=KeyEvent.ACTION_DOWN||e.getRepeatCount()!=0||c instanceof android.app.Activity&&((android.app.Activity)c).getCurrentFocus() instanceof android.widget.EditText)return -1;int key=e.getKeyCode();if(key==KeyEvent.KEYCODE_SEARCH||key==prefs(c).getInt("search_key",-1))return 0;if(key==prefs(c).getInt("profiles_key",-1))return 4;return -1;}
    static void assign(Context c,String action,int key){if(!allowed(key))throw new IllegalArgumentException("Reserved remote button");SharedPreferences.Editor edit=prefs(c).edit();String other=action.equals("search_key")?"profiles_key":"search_key";if(prefs(c).getInt(other,-1)==key)edit.remove(other);edit.putInt(action,key).commit();}
}
