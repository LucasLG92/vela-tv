package tv.vela.launcher;

import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Same integrated panel in both editions. Recycled text rows; no logos or media session. */
final class TvChannelOrganizer extends FrameLayout implements AutoCloseable {
    interface Provider {
        List<TvPersonalization.Item> items(int tab)throws Exception;
        void activate(int tab,String key)throws Exception;
        void order(List<String> keys)throws Exception;
        void clearRecent()throws Exception;
        default String selectedLabel(){return "";}
        void close();
    }
    private final Provider provider;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private Future<?> job;
    private final ListView list;private final BaseAdapter adapter;private final TextView hint,clear;
    private final List<TextView> tabs=new ArrayList<>();private List<TvPersonalization.Item> values=new ArrayList<>(),beforeMove;
    private int tab,generation;private boolean closed,busy,moving;
    TvChannelOrganizer(Context c,Provider p){super(c);provider=p;setBackgroundColor(VelaTheme.BACKGROUND);
        put(label(t("Organize your television","Organiza tu televisión"),38),64,36,1330,64);
        TextView close=button(t("Done","Listo"),p::close);put(close,1490,42,228,60);
        String[] names={"★ "+t("Favourites","Favoritos"),t("Hidden channels","Canales ocultos"),t("Categories","Categorías"),t("Recent","Recientes")};
        for(int i=0;i<names.length;i++){final int target=i;TextView b=button(names[i],()->{cancelMove();tab=target;load(0);});tabs.add(b);put(b,64+i*414,130,392,62);}
        list=new ListView(c);list.setId(View.generateViewId());list.setDivider(null);list.setCacheColorHint(android.graphics.Color.TRANSPARENT);list.setVerticalScrollBarEnabled(false);list.setSelector(new android.graphics.drawable.ColorDrawable(0x304E5261));put(list,64,220,1654,550);
        adapter=new BaseAdapter(){public int getCount(){return values.size();}public Object getItem(int i){return values.get(i);}public long getItemId(int i){return values.get(i).key.hashCode();}public View getView(int i,View recycled,ViewGroup parent){TextView row=recycled instanceof TextView?(TextView)recycled:label("",24);TvPersonalization.Item item=values.get(i);row.setText((tab==0?(moving&&list.getSelectedItemPosition()==i?"↕  ":(i+1)+"   "):tab==2?(item.hidden?"○  ":"✓  "):"")+item.label);row.setPadding(24,0,24,0);row.setSingleLine();row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setLayoutParams(new AbsListView.LayoutParams(-1,76));return row;}};list.setAdapter(adapter);
        list.setOnItemClickListener((parent,v,index,id)->activate(index));
        list.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN||!moving)return false;if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){int at=list.getSelectedItemPosition(),to=at+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);if(at>=0&&to>=0&&to<values.size()){Collections.swap(values,at,to);adapter.notifyDataSetChanged();list.setSelection(to);}return true;}return false;});
        hint=label("",21);hint.setTextColor(VelaTheme.SECONDARY);hint.setSingleLine(false);hint.setMaxLines(3);put(hint,64,802,1260,102);
        clear=button(t("Clear recent","Borrar recientes"),()->work(()->{if(tab==1)provider.activate(1,"@selected");else provider.clearRecent();},0));put(clear,1360,822,358,62);load(-1);
    }
    private static String t(String en,String es){return Ui.tr(en,en,es);}
    private TextView label(String text,int size){TextView v=new TextView(getContext());v.setText(text);v.setTypeface(Ui.medium);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(VelaTheme.WHITE);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    private TextView button(String text,Runnable click){TextView v=label(text,22);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setDefaultFocusHighlightEnabled(false);v.setGravity(Gravity.CENTER);v.setBackground(plate(VelaTheme.CARD));v.setOnFocusChangeListener((view,focus)->{v.setBackground(plate(focus?Appearance.accent(getContext()):VelaTheme.CARD));v.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);});v.setOnClickListener(view->{if(!busy)click.run();});return v;}
    private GradientDrawable plate(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(18);return d;}
    private void put(View view,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;addView(view,p);}
    private void help(){clear.setVisibility(tab==3||tab==1?VISIBLE:GONE);clear.setEnabled(tab!=1||!provider.selectedLabel().isEmpty());clear.setFocusable(clear.isEnabled());clear.setAlpha(clear.isEnabled()?1:.4f);clear.setText(tab==1?t("Hide selected channel","Ocultar canal seleccionado"):t("Clear recent","Borrar recientes"));String text=moving?t("↑ ↓ Move · OK Save · Back Cancel","↑ ↓ Mover · OK Guardar · Atrás Cancelar"):tab==0?t("OK · Pick up a favourite, then use ↑ ↓ to move it.","OK · Elige un favorito y muévelo con ↑ ↓."):tab==1?t("OK · Show this channel again. Hide the channel selected in the guide with the button.","OK · Volver a mostrar un canal. El botón oculta el canal seleccionado en la guía."):tab==2?t("OK · Show or hide a category. Channels remain in All channels.","OK · Mostrar u ocultar una categoría. Sus canales siguen en Todos."):t("OK · Watch a recent channel. Only successful playback is remembered.","OK · Ver un canal reciente. Solo se recuerdan emisiones abiertas correctamente.");if(tab==1&&!provider.selectedLabel().isEmpty())text=t("Selected: ","Seleccionado: ")+provider.selectedLabel()+"\n"+text;hint.setText(values.isEmpty()?t("Nothing here yet. ","Todavía no hay elementos. ")+text:text);}
    private void load(int focus){if(closed)return;busy=true;int token=++generation;if(job!=null)job.cancel(true);final int target=tab;hint.setText(t("Loading…","Cargando…"));job=worker.submit(()->{try{List<TvPersonalization.Item> result=provider.items(target);main.post(()->{if(closed||token!=generation)return;busy=false;values=new ArrayList<>(result.subList(0,Math.min(512,result.size())));adapter.notifyDataSetChanged();help();if(focus>=0&&!values.isEmpty()){list.setSelection(Math.min(focus,values.size()-1));list.requestFocus();}else tabs.get(tab).requestFocus();});}catch(Exception unavailable){main.post(()->{if(closed||token!=generation)return;busy=false;hint.setText(t("Could not load. Try selecting the tab again.","No se ha podido cargar. Vuelve a elegir la pestaña."));tabs.get(tab).requestFocus();});}});}
    private interface Job {void run()throws Exception;}
    private void work(Job action,int focus){if(busy||closed)return;busy=true;int token=++generation;job=worker.submit(()->{try{action.run();main.post(()->{if(!closed&&token==generation){busy=false;load(focus);}});}catch(Exception failed){main.post(()->{if(!closed&&token==generation){busy=false;hint.setText(t("Could not save. Please try again.","No se ha podido guardar. Vuelve a intentarlo."));}});}});}
    private void activate(int index){if(busy||index<0||index>=values.size())return;if(tab==0){if(!moving){beforeMove=new ArrayList<>(values);moving=true;help();adapter.notifyDataSetChanged();}else{moving=false;List<String> order=new ArrayList<>();for(TvPersonalization.Item i:values)order.add(i.key);beforeMove=null;work(()->provider.order(order),index);}}else{String key=values.get(index).key;work(()->provider.activate(tab,key),index);}}
    private void cancelMove(){if(moving){moving=false;values=beforeMove;beforeMove=null;adapter.notifyDataSetChanged();help();}}
    boolean back(){if(moving){cancelMove();return true;}provider.close();return true;}
    public void close(){if(closed)return;closed=true;generation++;if(job!=null)job.cancel(true);worker.shutdownNow();main.removeCallbacksAndMessages(null);list.setAdapter(null);values.clear();beforeMove=null;}
}
