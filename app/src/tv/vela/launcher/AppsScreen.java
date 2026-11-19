package tv.vela.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The application library lives in Home and releases its recycled icon grid on pause. */
final class AppsScreen extends VelaScreen {
    private FrameLayout root;
    private InstalledAppsGrid grid;
    private int selection;
    private View recent;
    AppsScreen(Activity owner,Navigation navigation){super(owner,navigation);}
    protected void onCreate(Bundle state){if(state!=null)selection=state.getInt("selection",0);}
    protected void onResume(){
        root=new FrameLayout(this);
        TextView status=label(Ui.tr("Carregando aplicativos…","Loading apps…","Cargando aplicaciones…"),24);
        TextView count=label("",22);
        count.setGravity(Gravity.CENTER_VERTICAL);
        place(count,80,16,230,62);
        TextView organize=label(L10n.text("Organize"),22);
        organize.setGravity(Gravity.CENTER);
        organize.setDefaultFocusHighlightEnabled(false);
        organize.setBackground(VelaTheme.shape(VelaTheme.CARD,VelaTheme.CONTROL_RADIUS));
        organize.setOnFocusChangeListener((v,focused)->{
            organize.setTextColor(focused?VelaTheme.INK:VelaTheme.SECONDARY);
            organize.setBackground(VelaTheme.shape(focused?Appearance.accent(this):VelaTheme.CARD,VelaTheme.CONTROL_RADIUS));
        });
        organize.setFocusable(true);
        organize.setId(View.generateViewId());
        organize.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){navigation.navigationTarget(this).requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN&&grid!=null)return grid.requestFocus();
            return false;
        });
        organize.setOnClickListener(v->{if(owner instanceof VelaHub.Owner)((VelaHub.Owner)owner).tools(6);});
        place(organize,325,18,206,56);
        place(status,80,130,1520,70);
        org.json.JSONObject item=VelaRecentActivity.available(this);
        if(item.length()>0&&(owner instanceof HomeActivity||VelaRecentActivity.intent(this,item)!=null)){
            recent=new VelaRecentActivity.Card(this,item,()->{if(owner instanceof HomeActivity)((HomeActivity)owner).reopenActivity(item);else {Intent intent=VelaRecentActivity.intent(this,item);if(intent!=null)startActivity(intent);}});
            place(recent,1170,6,520,78);
            recent.setNextFocusLeftId(organize.getId());
            organize.setNextFocusRightId(recent.getId());
            recent.setOnKeyListener((v,k,e)->e.getAction()==KeyEvent.ACTION_DOWN&&k==KeyEvent.KEYCODE_DPAD_DOWN&&grid!=null&&grid.requestFocus());
        }
        grid=new InstalledAppsGrid(this,Arrays.asList(SidebarApps.slots(this)),recent==null?organize:recent,status,count,app->{
            Intent intent=HomeApps.launchIntent(getPackageManager(),app.pkg);
            if(intent!=null)startActivity(intent);
        },false);
        place(grid,26,128,1738,770);
        grid.initialSelection(selection);
        setContentView(root);
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(VelaTheme.SECONDARY);v.setTypeface(Ui.medium);return v;}
    private void place(View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;root.addView(v,p);}
    void focusGrid(){if(grid!=null)grid.requestFocus();}
    @Override boolean standaloneChrome(){return false;}
    @Override boolean navigationLeft(){return grid==null||grid.getSelectedItemPosition()%InstalledAppsGrid.COLUMNS==0;}
    protected void onPause(){recent=null;if(grid!=null){selection=Math.max(0,grid.getSelectedItemPosition());grid.release();grid=null;}if(root!=null)root.removeAllViews();}
    protected void onDestroy(){onPause();}
    @Override void onTrimMemory(int level){if(grid!=null)grid.trim();}
    protected void onSaveInstanceState(Bundle out){out.putInt("selection",grid==null?selection:Math.max(0,grid.getSelectedItemPosition()));}
}
