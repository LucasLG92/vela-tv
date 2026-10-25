package tv.vela.launcher;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** One pending callback only while OK is held. Release never also activates after a long press. */
final class TvRemotePress {
    static final int HOLD_MS=Math.max(650,ViewConfiguration.getLongPressTimeout());
    private final View owner;
    private final Runnable click,hold;
    private boolean pressed,held;
    private int key;
    private final Runnable timeout=this::checkHeld;
    private void checkHeld(){if(pressed&&owner.isAttachedToWindow()&&owner.hasWindowFocus()&&owner.hasFocus())fire();else cancel();}
    TvRemotePress(View owner,Runnable click,Runnable hold){
        this.owner=owner;this.click=click;this.hold=hold;
        View.OnFocusChangeListener previous=owner.getOnFocusChangeListener();
        owner.setOnFocusChangeListener((v,focus)->{if(!focus)cancel();if(previous!=null)previous.onFocusChange(v,focus);});
        owner.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){cancel();}});
    }
    static boolean select(int key){return key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;}
    boolean key(KeyEvent event){
        int code=event.getKeyCode();if(!select(code)){if(event.getAction()==KeyEvent.ACTION_DOWN)cancel();return false;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getRepeatCount()==0&&!pressed){pressed=true;held=false;key=code;owner.postDelayed(timeout,HOLD_MS);}
            if(pressed&&key==code&&event.getEventTime()-event.getDownTime()>=HOLD_MS)fire();
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_UP){
            boolean valid=pressed&&key==code&&!event.isCanceled();
            if(valid&&!held&&event.getEventTime()-event.getDownTime()>=HOLD_MS)fire();
            boolean activate=valid&&!held;cancel();if(activate)click.run();return true;
        }
        return true;
    }
    private void fire(){if(held)return;held=true;owner.removeCallbacks(timeout);hold.run();}
    void cancel(){owner.removeCallbacks(timeout);pressed=false;held=false;}
}
