package tv.vela.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/** A single recycled icon library. Only visible apps request local artwork. */
final class InstalledAppsGrid extends GridView {
    static final int COLUMNS=6;
    private final int iconSize,cacheBytes;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=Visuals.worker(1);
    private final LruCache<String,Bitmap> icons;
    private final Set<String> pending=new HashSet<>(),favorites;
    private final List<SidebarApps.Choice> choices=new ArrayList<>();
    private final View close;
    private final AppsAdapter adapter=new AppsAdapter();
    private volatile boolean released;
    private int initialPosition;

    InstalledAppsGrid(Context context,List<String> featured,View close,TextView status,TextView count,Consumer<SidebarApps.Choice> open){
        this(context,featured,close,status,count,open,true);
    }
    InstalledAppsGrid(Context context,List<String> featured,View close,TextView status,TextView count,Consumer<SidebarApps.Choice> open,boolean connectCloseDown){
        super(context);this.close=close;favorites=new HashSet<>(featured);
        iconSize=Visuals.light(context)?128:192;cacheBytes=Visuals.light(context)?512*1024:1024*1024;
        icons=new LruCache<String,Bitmap>(cacheBytes){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}};
        setId(View.generateViewId());setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);
        setNumColumns(COLUMNS);setColumnWidth(264);setHorizontalSpacing(26);setVerticalSpacing(14);setStretchMode(NO_STRETCH);
        setPadding(12,16,12,16);setClipToPadding(false);setClipChildren(false);setGravity(Gravity.LEFT);
        setSelector(new ColorDrawable(Color.TRANSPARENT));setCacheColorHint(Color.TRANSPARENT);setVerticalScrollBarEnabled(false);
        setAdapter(adapter);setEmptyView(status);if(connectCloseDown)close.setNextFocusDownId(getId());
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
        List<String> order=new ArrayList<>(featured);
        worker.execute(()->{
            if(released)return;List<SidebarApps.Choice> found=new ArrayList<>();
            try{
                List<SidebarApps.Choice> all=SidebarApps.choices(context);Map<String,SidebarApps.Choice> remaining=new LinkedHashMap<>();
                for(SidebarApps.Choice choice:all)remaining.put(choice.pkg,choice);
                for(String pkg:order){SidebarApps.Choice choice=remaining.remove(pkg);if(choice!=null)found.add(choice);}
                found.addAll(remaining.values());List<String> custom=VelaLayout.apps(context);if(!custom.isEmpty())found.sort(Comparator.comparingInt(choice->{int at=custom.indexOf(choice.pkg);return at<0?10000:at;}));
            }catch(RuntimeException ignored){}
            main.post(()->{
                if(released)return;choices.addAll(found);adapter.notifyDataSetChanged();
                count.setText(choices.size()+Ui.tr(" aplicativos"," apps"," apps"));
                status.setText(Ui.tr("Nenhum aplicativo disponível.","No apps available.","No hay aplicaciones disponibles."));
                if(choices.isEmpty())close.requestFocus();else{setSelection(Math.min(initialPosition,choices.size()-1));requestFocus();}
            });
        });
    }

    void initialSelection(int position){initialPosition=Math.max(0,position);if(!choices.isEmpty())setSelection(Math.min(initialPosition,choices.size()-1));}

    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode(),position=getSelectedItemPosition();
        if(event.getAction()==KeyEvent.ACTION_DOWN&&position>=0){
            if(key==KeyEvent.KEYCODE_DPAD_LEFT&&position%COLUMNS==0)return true;
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&(position%COLUMNS==COLUMNS-1||position==getCount()-1))return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                if((position/COLUMNS+1)*COLUMNS>=getCount())return true;
                if(position+COLUMNS>=getCount()){setSelection(getCount()-1);return true;}
            }
            if(key==KeyEvent.KEYCODE_DPAD_UP&&position<COLUMNS){close.requestFocus();return true;}
        }
        return super.dispatchKeyEvent(event);
    }

    private void highlight(){
        int selected=getSelectedItemPosition();
        for(int i=0;i<getChildCount();i++)((Tile)getChildAt(i)).selected(hasFocus()&&getFirstVisiblePosition()+i==selected);
    }

    private void icon(Tile tile,SidebarApps.Choice choice){
        tile.pkg=choice.pkg;tile.label.setText(choice.label);tile.setContentDescription(choice.label);
        tile.pin.setVisibility(favorites.contains(choice.pkg)?VISIBLE:INVISIBLE);
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
            Drawable original=null;String provider=pkg.equals("com.plexapp.android")?"plex":pkg.equals(SidebarApps.YOUTUBE)?"youtube":pkg.equals("com.amazon.amazonvideo.livingroom")?"prime":null;
            if(provider!=null)try(java.io.InputStream in=getContext().getAssets().open("brands/icon-"+provider+".png")){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=iconSize==128?4:2;
                Bitmap source=BitmapFactory.decodeStream(in,null,options);if(source!=null)original=new BitmapDrawable(getResources(),source);
            }catch(java.io.IOException ignored){}
            if(original==null)original=getContext().getPackageManager().getApplicationIcon(pkg);
            bitmap=Bitmap.createBitmap(iconSize,iconSize,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
            Path mask=new Path();mask.addRoundRect(new RectF(0,0,iconSize,iconSize),iconSize*.224f,iconSize*.224f,Path.Direction.CW);canvas.clipPath(mask);
            if(original instanceof AdaptiveIconDrawable){
                // Use the app's own layers in the TV square mask, without changing its colors.
                AdaptiveIconDrawable adaptive=(AdaptiveIconDrawable)original;int inset=iconSize/4;
                for(Drawable layer:new Drawable[]{adaptive.getBackground(),adaptive.getForeground()})if(layer!=null){layer.setBounds(-inset,-inset,iconSize+inset,iconSize+inset);layer.draw(canvas);}
            }else{
                int width=Math.max(1,original.getIntrinsicWidth()),height=Math.max(1,original.getIntrinsicHeight());
                float scale=iconSize/(float)Math.max(width,height);int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
                original.setBounds((iconSize-w)/2,(iconSize-h)/2,(iconSize+w)/2,(iconSize+h)/2);original.draw(canvas);
            }
            return bitmap;
        }catch(PackageManager.NameNotFoundException|RuntimeException ignored){if(bitmap!=null)bitmap.recycle();return null;}
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
        final FrameLayout artwork;
        final View pin;
        String pkg;
        boolean styled,focused;
        Tile(){
            super(InstalledAppsGrid.this.getContext());setLayoutParams(new AbsListView.LayoutParams(264,250));
            setFocusable(false);setDefaultFocusHighlightEnabled(false);setClipChildren(false);
            artwork=new FrameLayout(getContext());artwork.setBackground(shape(VelaTheme.CARD,43));artwork.setClipToOutline(true);
            LayoutParams art=new LayoutParams(192,192,Gravity.TOP|Gravity.CENTER_HORIZONTAL);art.topMargin=10;addView(artwork,art);
            image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);artwork.addView(image,new LayoutParams(-1,-1));
            initial=caption(56,Ui.displaySemibold);initial.setTextColor(0xFFB9BDCA);artwork.addView(initial,new LayoutParams(-1,-1));
            label=caption(21,Ui.medium);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(8,0,8,0);
            LayoutParams name=new LayoutParams(-1,32);name.topMargin=215;addView(label,name);
            pin=new View(getContext());pin.setBackground(shape(Appearance.accent(getContext()),3));LayoutParams p=new LayoutParams(5,5,Gravity.TOP|Gravity.CENTER_HORIZONTAL);p.topMargin=245;addView(pin,p);
        }
        private TextView caption(int size,android.graphics.Typeface font){TextView text=new TextView(getContext());text.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);text.setTypeface(font);text.setGravity(Gravity.CENTER);text.setIncludeFontPadding(false);text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return text;}
        void selected(boolean value){
            if(styled&&focused==value)return;styled=true;focused=value;
            boolean enlarge=focused&&!Visuals.reducedMotion(getContext());artwork.setScaleX(enlarge?1.045f:1);artwork.setScaleY(enlarge?1.045f:1);artwork.setElevation(focused?10:0);
            GradientDrawable outline=shape(Color.TRANSPARENT,43);if(focused)outline.setStroke(2,0xCCFFFFFF);artwork.setForeground(outline);
            label.setTextColor(focused?0xFFFFFFFF:0xFFC5CAD5);
        }
    }
    private static GradientDrawable shape(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);return shape;}
}
