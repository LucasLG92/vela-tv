package tv.vela.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/** Recycled app cards. Package artwork is loaded locally, only for requested cells. */
final class InstalledAppsGrid extends GridView {
    private static final int COLUMNS=5,ICON_SIZE=96,CACHE_BYTES=512*1024;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=Visuals.worker(1);
    private final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(CACHE_BYTES){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private final Set<String> pending=new HashSet<>();
    private final List<SidebarApps.Choice> choices=new ArrayList<>();
    private final List<View> above;
    private final View close;
    private final AppsAdapter adapter=new AppsAdapter();
    private volatile boolean released;

    InstalledAppsGrid(Context context,Set<String> featured,List<View> above,View close,TextView status,Consumer<SidebarApps.Choice> open){
        super(context);this.above=new ArrayList<>(above);this.close=close;
        setId(View.generateViewId());setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);
        setNumColumns(COLUMNS);setColumnWidth(320);setHorizontalSpacing(28);setVerticalSpacing(22);setStretchMode(NO_STRETCH);
        setPadding(8,8,8,16);setClipToPadding(false);setClipChildren(false);setGravity(Gravity.LEFT);
        setSelector(new ColorDrawable(Color.TRANSPARENT));setCacheColorHint(Color.TRANSPARENT);setVerticalScrollBarEnabled(true);
        setAdapter(adapter);setEmptyView(status);
        setOnItemClickListener((parent,view,position,id)->{if(!released&&position>=0&&position<choices.size())open.accept(choices.get(position));});
        setOnItemSelectedListener(new OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){highlight();}
            public void onNothingSelected(AdapterView<?> parent){highlight();}
        });
        setOnFocusChangeListener((view,focus)->highlight());
        setOnScrollListener(new OnScrollListener(){
            public void onScrollStateChanged(AbsListView view,int state){}
            public void onScroll(AbsListView view,int first,int visible,int total){highlight();}
        });
        Set<String> excluded=new HashSet<>(featured);
        worker.execute(()->{
            if(released)return;List<SidebarApps.Choice> found=new ArrayList<>();
            try{found.addAll(SidebarApps.choices(context));}catch(RuntimeException ignored){}
            found.removeIf(choice->excluded.contains(choice.pkg));
            main.post(()->{
                if(released)return;choices.addAll(found);adapter.notifyDataSetChanged();
                status.setText(Ui.tr("Nenhum aplicativo disponível.","No apps available.","No hay aplicaciones disponibles."));
                if(choices.isEmpty()){if(above.isEmpty())close.requestFocus();}
                else if(above.isEmpty()){setSelection(0);requestFocus();}
            });
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode(),position=getSelectedItemPosition();
        if(event.getAction()==KeyEvent.ACTION_DOWN&&position>=0){
            if(key==KeyEvent.KEYCODE_DPAD_LEFT&&position%COLUMNS==0)return true;
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&(position%COLUMNS==COLUMNS-1||position==getCount()-1))return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                if((position/COLUMNS+1)*COLUMNS>=getCount())return true;
                if(position+COLUMNS>=getCount()){setSelection(getCount()-1);return true;}
            }
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP&&event.getAction()==KeyEvent.ACTION_DOWN&&position<COLUMNS){
            int column=Math.max(0,position);
            View target=above.isEmpty()?close:above.get(Math.min(above.size()-1,(int)((column+.5f)*above.size()/COLUMNS)));
            target.requestFocus();return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void highlight(){
        int selected=getSelectedItemPosition();
        for(int i=0;i<getChildCount();i++)((Tile)getChildAt(i)).selected(hasFocus()&&getFirstVisiblePosition()+i==selected);
    }

    private void icon(Tile tile,SidebarApps.Choice choice){
        tile.pkg=choice.pkg;tile.label.setText(choice.label);tile.setContentDescription(choice.label);
        tile.initial.setText(choice.label.isEmpty()?"?":choice.label.substring(0,choice.label.offsetByCodePoints(0,1)).toUpperCase(Ui.locale()));
        Bitmap cached=icons.get(choice.pkg);tile.image.setImageBitmap(cached);tile.initial.setVisibility(cached==null?VISIBLE:GONE);
        if(cached!=null||released||!pending.add(choice.pkg))return;
        try{worker.execute(()->{
            if(released)return;Bitmap bitmap=loadIcon(choice.pkg);
            main.post(()->{
                pending.remove(choice.pkg);if(released){if(bitmap!=null)bitmap.recycle();return;}
                if(bitmap==null)return;icons.put(choice.pkg,bitmap);
                for(int i=0;i<getChildCount();i++){Tile visible=(Tile)getChildAt(i);if(choice.pkg.equals(visible.pkg)){visible.image.setImageBitmap(bitmap);visible.initial.setVisibility(GONE);}}
            });
        });}catch(RejectedExecutionException full){pending.remove(choice.pkg);}
    }

    private Bitmap loadIcon(String pkg){
        Bitmap bitmap=null;
        try{
            Drawable original=pkg.equals("com.plexapp.android")||pkg.equals(SidebarApps.YOUTUBE)||pkg.equals("com.amazon.amazonvideo.livingroom")
                ?SidebarApps.icon(getContext(),pkg):getContext().getPackageManager().getApplicationIcon(pkg);
            if(original==null)return null;
            bitmap=Bitmap.createBitmap(ICON_SIZE,ICON_SIZE,Bitmap.Config.ARGB_8888);
            int width=Math.max(1,original.getIntrinsicWidth()),height=Math.max(1,original.getIntrinsicHeight());
            float scale=ICON_SIZE/(float)Math.max(width,height);int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
            original.setBounds((ICON_SIZE-w)/2,(ICON_SIZE-h)/2,(ICON_SIZE+w)/2,(ICON_SIZE+h)/2);original.draw(new Canvas(bitmap));
            return bitmap;
        }catch(android.content.pm.PackageManager.NameNotFoundException|RuntimeException ignored){if(bitmap!=null)bitmap.recycle();return null;}
    }

    void trim(){icons.evictAll();}
    void release(){
        if(released)return;released=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);pending.clear();
        for(int i=0;i<getChildCount();i++)((Tile)getChildAt(i)).image.setImageDrawable(null);
        setAdapter(null);icons.evictAll();choices.clear();
    }

    private final class AppsAdapter extends BaseAdapter {
        public int getCount(){return choices.size();}
        public SidebarApps.Choice getItem(int position){return choices.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View reused,ViewGroup parent){
            Tile tile=reused instanceof Tile?(Tile)reused:new Tile();icon(tile,choices.get(position));
            tile.selected(hasFocus()&&position==getSelectedItemPosition());return tile;
        }
    }

    private final class Tile extends FrameLayout {
        final ImageView image;
        final TextView label,initial;
        final View mark;
        String pkg;
        boolean styled,focused;
        Tile(){
            super(InstalledAppsGrid.this.getContext());setLayoutParams(new AbsListView.LayoutParams(320,144));
            setFocusable(false);setDefaultFocusHighlightEnabled(false);setClipChildren(false);
            image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams artwork=new LayoutParams(72,72,Gravity.TOP|Gravity.CENTER_HORIZONTAL);artwork.topMargin=19;addView(image,artwork);
            initial=caption(31,Ui.displaySemibold);initial.setTextColor(0xFFB9BDCA);initial.setBackground(shape(0xFF303541,19));addView(initial,new LayoutParams(artwork));
            label=caption(19,Ui.medium);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(14,0,14,0);
            LayoutParams name=new LayoutParams(-1,32);name.topMargin=99;addView(label,name);
            mark=new View(getContext());mark.setBackground(shape(Appearance.accent(getContext()),2));LayoutParams m=new LayoutParams(32,3,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);m.bottomMargin=5;addView(mark,m);
        }
        private TextView caption(int size,android.graphics.Typeface font){TextView text=new TextView(getContext());text.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);text.setTypeface(font);text.setTextColor(0xFFF7F7FA);text.setGravity(Gravity.CENTER);text.setIncludeFontPadding(false);text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return text;}
        void selected(boolean value){if(styled&&focused==value)return;styled=true;focused=value;setBackground(shape(focused?0xFF3B404E:0xFF20232C,20));mark.setVisibility(focused?VISIBLE:INVISIBLE);setElevation(focused?16:0);setScaleX(focused?1.025f:1);setScaleY(focused?1.025f:1);}
    }
    private static GradientDrawable shape(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);return shape;}
}
