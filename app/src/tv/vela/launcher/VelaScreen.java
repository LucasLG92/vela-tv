package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Reusable native screen logic. One real Activity owns permissions, windows and external intents. */
abstract class VelaScreen extends ContextThemeWrapper {
    interface Navigation {
        boolean embedded();
        void content(VelaScreen screen, View view);
        void open(VelaScreen screen, Intent intent);
        void close(VelaScreen screen);
        void fullscreen(VelaScreen screen, boolean enabled);
        void permission(VelaScreen screen, String[] names, int request);
        void changed(VelaScreen screen);
        View navigationTarget(VelaScreen screen);
    }
    final Activity owner;
    final Navigation navigation;
    private Intent intent;
    private boolean resumed, destroyed, finishing, full;
    long navigationToken;
    private final boolean secureBefore;
    View content;
    static final int RESULT_OK=Activity.RESULT_OK, RESULT_CANCELED=Activity.RESULT_CANCELED;
    VelaScreen(Activity owner,Navigation navigation){super(owner,owner.getTheme());this.owner=owner;this.navigation=navigation;intent=new Intent(owner.getIntent());secureBefore=(owner.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0;}
    final void create(Intent intent,Bundle state){this.intent=new Intent(intent);onCreate(state);}
    final void resume(){if(!destroyed&&!resumed){resumed=true;onResume();}}
    final void pause(){if(resumed){resumed=false;onPause();}}
    final void destroy(){if(destroyed)return;finishing=true;pause();destroyed=true;onDestroy();if(!secureBefore)owner.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    final Bundle save(){Bundle state=new Bundle();onSaveInstanceState(state);return state;}
    protected void onCreate(Bundle state){}
    protected void onResume(){}
    protected void onPause(){}
    protected void onDestroy(){}
    protected void onSaveInstanceState(Bundle state){}
    protected void onNewIntent(Intent intent){setIntent(intent);}
    protected void onActivityResult(int request,int result,Intent data){}
    public void onRequestPermissionsResult(int request,String[] names,int[] results){}
    public boolean dispatchKeyEvent(KeyEvent event){return false;}
    public void onBackPressed(){finish();}
    void onTrimMemory(int level){}
    final boolean isFinishing(){return finishing||owner.isFinishing();}
    final boolean isDestroyed(){return destroyed||owner.isDestroyed();}
    final Intent getIntent(){return intent;}
    final void setIntent(Intent intent){this.intent=new Intent(intent);}
    final Window getWindow(){return owner.getWindow();}
    final View getCurrentFocus(){return owner.getCurrentFocus();}
    final void setResult(int result){if(!navigation.embedded())owner.setResult(result);}
    final void finish(){navigation.close(this);}
    @Override public void startActivity(Intent intent){navigation.open(this,intent);}
    final void startActivityForResult(Intent intent,int request){owner.startActivityForResult(intent,request);}
    final void requestPermissions(String[] names,int request){navigation.permission(this,names,request);}
    final void setContentView(View view){content=view;if(navigation.embedded()&&!full&&standaloneChrome())integrate(view);navigation.content(this,view);}
    final void changed(){navigation.changed(this);}
    final void fullPreview(boolean enabled){full=enabled;navigation.fullscreen(this,enabled);}
    final void sensitive(boolean enabled){if(enabled)owner.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else if(!secureBefore)owner.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    boolean standaloneChrome(){return true;}
    boolean navigationLeft(){return !(owner.getCurrentFocus() instanceof EditText);}
    // Remove only standalone chrome. The shared Home sidebar, heading and clock remain above this content.
    private void integrate(View view){
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if(!(view instanceof ViewGroup))return;ViewGroup group=(ViewGroup)view;
        ViewGroup.LayoutParams layout=view.getLayoutParams();
        if(layout!=null&&layout.width==1920&&layout.height==1080){
            for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);ViewGroup.LayoutParams raw=child.getLayoutParams();
                if(raw instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)raw;
                    if(child.getClass()==View.class&&p.width==1920&&p.height==1080)child.setVisibility(View.GONE);
                    if(child instanceof TextView&&p.leftMargin<800&&p.topMargin<230&&(((TextView)child).getText().toString().equals("VELA")||((TextView)child).getTextSize()>=50))child.setVisibility(View.GONE);
                }
            }
            return;
        }
        for(int i=0;i<group.getChildCount();i++)integrate(group.getChildAt(i));
    }
}
