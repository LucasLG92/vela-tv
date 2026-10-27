package tv.vela.launcher;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** The settings layout in both editions: native categories, recycled rows, no media or effects. */
final class TvChannelOrganizer extends FrameLayout implements AutoCloseable {
    interface Provider {
        List<TvPersonalization.Item> items(int tab)throws Exception;
        void activate(int tab,String key)throws Exception;
        void order(List<String> keys)throws Exception;
        void clearRecent()throws Exception;
        default String selectedLabel(){return "";}
        void close();
    }
    private final Provider provider;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> job;
    private final FrameLayout surface;
    private final ListView list;
    private final BaseAdapter adapter;
    private TextView heading,caption,hint,clear,done;
    private final List<TextView> tabs=new ArrayList<>();
    private final List<CategoryIcon> icons=new ArrayList<>();
    private List<TvPersonalization.Item> values=new ArrayList<>(),beforeMove;
    private int tab,generation;
    private boolean closed,busy,moving,building=true,optionsPending,failed;

    TvChannelOrganizer(Context c,Provider p){
        super(c);provider=p;
        surface=new FrameLayout(c);surface.setBackground(plate(0xB51C1D25,32));
        put(this,surface,520,12,1218,904);
        put(this,label(L10n.text("Television"),31),86,20,378,62);
        TextView intro=label(t("Organize your television","Organiza tu televisión"),20);
        intro.setTextColor(VelaTheme.SECONDARY);put(this,intro,86,92,378,42);
        String[] names={t("Favourites","Favoritos"),t("Hidden channels","Canales ocultos"),t("Categories","Categorías"),t("Recent","Recientes")};
        for(int i=0;i<names.length;i++){
            final int index=i;
            TextView category=label(names[i],23);category.setId(View.generateViewId());
            category.setFocusable(true);category.setFocusableInTouchMode(true);
            category.setDefaultFocusHighlightEnabled(false);category.setSingleLine();
            category.setEllipsize(android.text.TextUtils.TruncateAt.END);category.setPadding(22,0,20,0);
            CategoryIcon icon=new CategoryIcon(i);icon.setBounds(0,0,28,28);
            category.setCompoundDrawables(icon,null,null,null);category.setCompoundDrawablePadding(22);
            icons.add(icon);tabs.add(category);put(this,category,80,170+i*92,388,76);
            category.setOnFocusChangeListener((v,focus)->{if(!building&&focus)select(index,false);skin();});
            category.setOnClickListener(v->select(index,true));
            category.setOnKeyListener((v,key,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN)return navigationKey(key);
                if(key==KeyEvent.KEYCODE_DPAD_LEFT){provider.close();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){focusOptions();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                    int next=Math.max(0,Math.min(3,index+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)));
                    if(next==index&&key==KeyEvent.KEYCODE_DPAD_DOWN)done.requestFocus();else tabs.get(next).requestFocus();
                    return true;
                }
                return false;
            });
        }
        done=button(t("Back to television","Volver a televisión"),provider::close);
        put(this,done,80,674,388,64);
        done.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return navigationKey(key);
            if(key==KeyEvent.KEYCODE_DPAD_UP){tabs.get(3).requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){provider.close();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){focusOptions();return true;}
            return key==KeyEvent.KEYCODE_DPAD_DOWN;
        });
        TextView leftHint=label("← "+t("Television","Televisión")+"\n→ "+t("Options","Opciones"),17);
        leftHint.setTextColor(VelaTheme.MUTED);leftHint.setSingleLine(false);
        put(this,leftHint,86,852,378,64);
        heading=label("",42);heading.setTypeface(Ui.displaySemibold);put(surface,heading,42,26,1134,72);
        caption=label("",23);caption.setTextColor(VelaTheme.SECONDARY);caption.setSingleLine(false);
        caption.setMaxLines(2);put(surface,caption,42,106,1134,66);
        list=new ListView(c);list.setId(View.generateViewId());list.setDivider(null);
        list.setDefaultFocusHighlightEnabled(false);list.setCacheColorHint(Color.TRANSPARENT);list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        put(surface,list,42,194,1134,616);
        adapter=new BaseAdapter(){
            public int getCount(){return values.size();}
            public Object getItem(int i){return values.get(i);}
            public long getItemId(int i){return values.get(i).key.hashCode();}
            public View getView(int i,View recycled,ViewGroup parent){
                ChannelRow row=recycled instanceof ChannelRow?(ChannelRow)recycled:new ChannelRow();row.bind(i);return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int index,long id){styleRows();}
            public void onNothingSelected(AdapterView<?> parent){styleRows();}
        });
        list.setOnFocusChangeListener((v,focus)->{styleRows();skin();});
        list.setOnItemClickListener((parent,v,index,id)->activate(index));
        list.setOnKeyListener((v,key,e)->{
            if(e.getAction()!=KeyEvent.ACTION_DOWN)return navigationKey(key);
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){cancelMove();tabs.get(tab).requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
            int at=list.getSelectedItemPosition();
            if(moving&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){
                int to=at+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);
                if(at>=0&&to>=0&&to<values.size()){Collections.swap(values,at,to);adapter.notifyDataSetChanged();list.setSelection(to);}
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_UP&&at<=0)return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN&&at==values.size()-1){if(clear.getVisibility()==VISIBLE&&clear.isEnabled())clear.requestFocus();return true;}
            return false;
        });
        hint=label("",18);hint.setTextColor(VelaTheme.SECONDARY);hint.setSingleLine(false);
        hint.setMaxLines(3);put(surface,hint,48,826,1118,64);
        clear=button("",()->work(()->{if(tab==1)provider.activate(1,"@selected");else provider.clearRecent();},0));
        put(surface,clear,42,742,1134,68);
        clear.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return navigationKey(key);
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){tabs.get(tab).requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP){if(!values.isEmpty()){list.requestFocus();list.setSelection(values.size()-1);}else tabs.get(tab).requestFocus();return true;}
            return key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        building=false;load(-1);tabs.get(0).requestFocus();
    }
    private static boolean navigationKey(int key){return key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;}
    private static String t(String en,String es){return Ui.tr(en,en,es);}
    private TextView label(String text,int size){
        TextView v=new TextView(getContext());v.setText(text);v.setTypeface(Ui.medium);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(VelaTheme.WHITE);
        v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;
    }
    private TextView button(String text,Runnable click){
        TextView v=label(text,22);v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);
        v.setDefaultFocusHighlightEnabled(false);v.setGravity(Gravity.CENTER);v.setSingleLine();
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setPadding(22,0,22,0);v.setBackground(plate(VelaTheme.CARD,20));
        v.setOnFocusChangeListener((view,focus)->{v.setBackground(plate(focus?Appearance.accent(getContext()):VelaTheme.CARD,20));v.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);skin();});
        v.setOnClickListener(view->{if(!busy)click.run();});return v;
    }
    private GradientDrawable plate(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private void put(FrameLayout parent,View view,int x,int y,int w,int h){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(view,p);
    }
    private void skin(){
        for(int i=0;i<tabs.size();i++){
            TextView v=tabs.get(i);boolean focus=v.hasFocus();int color=focus?VelaTheme.INK:i==tab?VelaTheme.WHITE:VelaTheme.SECONDARY;
            v.setBackground(plate(focus?Appearance.accent(getContext()):i==tab?0x2AFFFFFF:Color.TRANSPARENT,20));
            v.setTextColor(color);icons.get(i).color(color);
        }
    }
    private void select(int index,boolean options){
        if(closed)return;
        if(index==tab){if(failed)load(options?0:-1);else if(options)focusOptions();return;}
        cancelMove();tab=index;
        boolean previousBuilding=building;building=true;tabs.get(tab).requestFocus();building=previousBuilding;
        values.clear();adapter.notifyDataSetChanged();load(options?0:-1);
    }
    private void focusOptions(){
        if(busy){optionsPending=true;return;}
        if(!values.isEmpty()){list.requestFocus();if(list.getSelectedItemPosition()<0)list.setSelection(0);}
        else if(clear.getVisibility()==VISIBLE&&clear.isEnabled())clear.requestFocus();
    }
    private void help(){
        heading.setText(tabs.get(tab).getText());
        String[] captions={t("Your favourite channels, in your order.","Tus canales favoritos, en el orden que tú elijas."),t("Choose which channels appear in your guide.","Elige qué canales aparecen en tu guía."),t("Keep the categories you use close at hand.","Deja a mano las categorías que utilizas."),t("Return to what you were watching.","Vuelve a lo que estabas viendo.")};
        String selected=provider.selectedLabel();caption.setText(tab==1&&!selected.isEmpty()?t("Selected: ","Seleccionado: ")+selected:captions[tab]);
        boolean action=tab==3||tab==1;clear.setVisibility(action?VISIBLE:GONE);
        clear.setEnabled(tab!=1||!selected.isEmpty());clear.setFocusable(clear.isEnabled());clear.setAlpha(clear.isEnabled()?1:.4f);
        clear.setText(tab==1?t("Hide selected channel","Ocultar canal seleccionado"):t("Clear recent","Borrar recientes"));
        FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)list.getLayoutParams();int height=action?528:616;
        if(layout.height!=height){layout.height=height;list.setLayoutParams(layout);}
        String text=moving?t("↑ ↓ Move · OK Save · Back Cancel","↑ ↓ Mover · OK Guardar · Atrás Cancelar"):
            tab==0?t("OK · Pick up a favourite   ·   ← Categories","OK · Elegir favorito   ·   ← Categorías"):
            tab==1?t("OK · Show channel again   ·   ← Categories","OK · Volver a mostrar el canal   ·   ← Categorías"):
            tab==2?t("OK · Show or hide. Channels remain in All channels.","OK · Mostrar u ocultar. Sus canales siguen en Todos."):
            t("OK · Watch channel   ·   ← Categories","OK · Ver canal   ·   ← Categorías");
        hint.setText(values.isEmpty()?t("Nothing here yet. ","Todavía no hay elementos. ")+text:text);skin();styleRows();
    }
    private void load(int focus){
        if(closed)return;busy=true;failed=false;optionsPending=focus>=0;help();hint.setText(t("Loading…","Cargando…"));
        int token=++generation;if(job!=null)job.cancel(true);final int target=tab;
        job=worker.submit(()->{try{
            List<TvPersonalization.Item> result=provider.items(target);
            main.post(()->{
                if(closed||token!=generation)return;busy=false;
                if(result.isEmpty())tabs.get(tab).requestFocus();
                values=new ArrayList<>(result.subList(0,Math.min(512,result.size())));
                adapter.notifyDataSetChanged();help();
                if(optionsPending&&!values.isEmpty()){list.requestFocus();list.setSelection(Math.max(0,Math.min(focus,values.size()-1)));}
                else if(optionsPending&&clear.getVisibility()==VISIBLE&&clear.isEnabled())clear.requestFocus();else tabs.get(tab).requestFocus();
                optionsPending=false;
            });
        }catch(Exception unavailable){main.post(()->{if(closed||token!=generation)return;busy=false;failed=true;hint.setText(t("Could not load. Choose the category to try again.","No se ha podido cargar. Elige la categoría para reintentar."));tabs.get(tab).requestFocus();});}});
    }
    private interface Job {void run()throws Exception;}
    private void work(Job action,int focus){
        if(busy||closed)return;busy=true;int token=++generation;
        job=worker.submit(()->{try{action.run();main.post(()->{if(!closed&&token==generation){busy=false;load(focus);}});}
            catch(Exception failure){main.post(()->{if(!closed&&token==generation){busy=false;hint.setText(t("Could not save. Please try again.","No se ha podido guardar. Vuelve a intentarlo."));}});}});
    }
    private void activate(int index){
        if(busy||index<0||index>=values.size())return;
        if(tab==0){
            if(!moving){beforeMove=new ArrayList<>(values);moving=true;help();adapter.notifyDataSetChanged();}
            else{moving=false;List<String> order=new ArrayList<>();for(TvPersonalization.Item i:values)order.add(i.key);beforeMove=null;work(()->provider.order(order),index);}
        }else{String key=values.get(index).key;work(()->provider.activate(tab,key),index);}
    }
    private void cancelMove(){if(moving){moving=false;values=beforeMove;beforeMove=null;adapter.notifyDataSetChanged();help();}}
    private void styleRows(){for(int i=0;i<list.getChildCount();i++)if(list.getChildAt(i) instanceof ChannelRow)((ChannelRow)list.getChildAt(i)).skin(list.hasFocus()&&list.getFirstVisiblePosition()+i==list.getSelectedItemPosition());}
    boolean back(){if(moving){cancelMove();return true;}if(surface.hasFocus()){tabs.get(tab).requestFocus();return true;}provider.close();return true;}
    public void close(){
        if(closed)return;closed=true;generation++;if(job!=null)job.cancel(true);worker.shutdownNow();
        main.removeCallbacksAndMessages(null);list.setAdapter(null);values.clear();beforeMove=null;
    }
    private final class ChannelRow extends FrameLayout {
        private final FrameLayout card;
        private final TextView number,name,value;
        private final GradientDrawable normal=plate(VelaTheme.CARD,20),focused=plate(Appearance.accent(getContext()),20);
        ChannelRow(){super(TvChannelOrganizer.this.getContext());setFocusable(false);
            card=new FrameLayout(getContext());put(this,card,0,0,1134,78);
            number=label("",21);number.setGravity(Gravity.CENTER);put(card,number,18,0,48,78);
            name=label("",23);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);put(card,name,84,0,780,78);
            value=label("",20);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(card,value,872,0,234,78);
        }
        void bind(int index){
            setLayoutParams(new AbsListView.LayoutParams(-1,88));TvPersonalization.Item item=values.get(index);
            number.setText(tab==0?(moving&&list.getSelectedItemPosition()==index?"↕":String.valueOf(index+1)):"");
            name.setText(item.label);value.setText(tab==0?(moving&&list.getSelectedItemPosition()==index?t("Moving","Moviendo"):"↕"):tab==1?t("Show","Mostrar"):tab==2?(item.hidden?t("Hidden","Oculta"):t("Visible","Visible")):t("Watch","Ver canal"));
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)name.getLayoutParams();p.leftMargin=tab==0?84:24;p.width=tab==0?780:840;name.setLayoutParams(p);
            setContentDescription(item.label+" · "+value.getText());skin(list.hasFocus()&&list.getSelectedItemPosition()==index);
        }
        void skin(boolean selected){card.setBackground(selected?focused:normal);name.setTextColor(selected?VelaTheme.INK:VelaTheme.WHITE);number.setTextColor(selected?VelaTheme.INK:VelaTheme.SECONDARY);value.setTextColor(selected?0xFF60636C:VelaTheme.SECONDARY);}
    }
    private static final class CategoryIcon extends android.graphics.drawable.Drawable {
        private final int kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path star=new Path();private int color=VelaTheme.WHITE;
        CategoryIcon(int kind){this.kind=kind;for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?13:6;float x=16+(float)Math.cos(a)*radius,y=16+(float)Math.sin(a)*radius;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();}
        void color(int value){if(color!=value){color=value;invalidateSelf();}}
        public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/32f,b.height()/32f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            if(kind==0)c.drawPath(star,paint);
            else if(kind==1){c.drawOval(3,8,29,24,paint);c.drawCircle(16,16,4,paint);c.drawLine(4,3,28,29,paint);}
            else if(kind==2){for(int i=0;i<3;i++){int y=8+i*8;c.drawCircle(5,y,1,paint);c.drawLine(11,y,28,y,paint);}}
            else{c.drawCircle(16,16,12,paint);c.drawLine(16,8,16,16,paint);c.drawLine(16,16,22,20,paint);}c.restore();
        }
        public void setAlpha(int alpha){paint.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
