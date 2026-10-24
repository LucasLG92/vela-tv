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
    AppsScreen(Activity owner,Navigation navigation){super(owner,navigation);}
    protected void onCreate(Bundle state){if(state!=null)selection=state.getInt("selection",0);}
    protected void onResume(){
        root=new FrameLayout(this);TextView status=label(Ui.tr("Carregando aplicativos…","Loading apps…","Cargando aplicaciones…"),22),count=label("",20),hint=label(Ui.tr("Seus favoritos primeiro","Your favorites first","Tus favoritas primero"),24);
        place(hint,74,22,1240,48);place(count,1420,22,270,48);place(status,74,100,1600,70);
        View menu=navigation.navigationTarget(this);
        grid=new InstalledAppsGrid(this,Arrays.asList(SidebarApps.slots(this)),menu,status,count,item->{Intent intent=HomeApps.launchIntent(getPackageManager(),item.pkg);if(intent!=null)startActivity(intent);},false);place(grid,26,90,1738,824);grid.initialSelection(selection);setContentView(root);
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(VelaTheme.SECONDARY);v.setTypeface(Ui.medium);return v;}
    private void place(View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;root.addView(v,p);}
    void focusGrid(){if(grid!=null)grid.requestFocus();}
    @Override boolean standaloneChrome(){return false;}
    @Override boolean navigationLeft(){return grid==null||grid.getSelectedItemPosition()%InstalledAppsGrid.COLUMNS==0;}
    protected void onPause(){if(grid!=null){selection=Math.max(0,grid.getSelectedItemPosition());grid.release();grid=null;}if(root!=null)root.removeAllViews();}
    protected void onDestroy(){onPause();}
    @Override void onTrimMemory(int level){if(grid!=null)grid.trim();}
    protected void onSaveInstanceState(Bundle out){out.putInt("selection",grid==null?selection:Math.max(0,grid.getSelectedItemPosition()));}
}
