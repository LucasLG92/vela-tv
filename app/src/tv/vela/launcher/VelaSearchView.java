package tv.vela.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** Six native result cards at a time. No artwork worker, catalogue cache or second menu. */
final class VelaSearchView extends FrameLayout {
    interface Host {
        void search(String query);void voice();void settings();void close();void navigation();boolean header(View from);
        void open(VelaHub.Hit hit);void save(VelaHub.Hit hit);void next(int direction);void focused();
    }
    private final Activity activity;private final Host host;
    final EditText input;private final TextView count,empty,previous,next;
    private final FrameLayout cards;private final View[] filters=new View[5];
    private final ArrayList<VelaHub.Hit> all=new ArrayList<>(),visible=new ArrayList<>();
    private final ArrayList<View> tiles=new ArrayList<>();
    private final int pageSize;private String lastQuery="";private int filter,localPage,backendPage;private boolean more,loading,closed;
    private final String[] kinds={"All results","Library","Television","Apps","HDMI"};
    VelaSearchView(Activity a,Host host,String query){
        super(a);activity=a;this.host=host;pageSize=VelaReading.scale(a)>1.12f?4:6;setBackground(VelaTheme.canvas());
        input=new EditText(a);input.setText(query);input.setSingleLine();input.setTextSize(TypedValue.COMPLEX_UNIT_PX,32*VelaReading.scale(a));input.setTypeface(Ui.medium);input.setTextColor(VelaTheme.WHITE);input.setHintTextColor(VelaTheme.SECONDARY);input.setHint(L10n.text("What do you want to find?"));input.setPadding(28,10,28,10);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});input.setBackground(VelaTheme.shape(VelaTheme.PANEL,22));input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setOnEditorActionListener((v,action,e)->{host.search(input.getText().toString().trim());return true;});input.setOnFocusChangeListener((v,f)->{if(f)host.focused();input.setBackground(VelaTheme.shape(f?VelaTheme.RAISED:VelaTheme.PANEL,22));});put(this,input,44,24,1280,84);
        TextView search=button(L10n.text("Search"),()->host.search(input.getText().toString().trim()));put(this,search,1344,24,180,84);
        TextView voice=button(L10n.text("Speak"),host::voice);put(this,voice,1544,24,200,84);
        for(int i=0;i<filters.length;i++){final int at=i;TextView v=button(L10n.text(kinds[i]),()->chooseFilter(at));v.setTextSize(TypedValue.COMPLEX_UNIT_PX,21*VelaReading.scale(a));put(this,v,44+i*226,142,210,58);filters[i]=v;v.setOnKeyListener((view,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){focusFirst();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){input.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT&&at==0){host.navigation();return true;}return false;});}
        TextView prefs=button(L10n.text("Voice and search"),host::settings);prefs.setTextSize(TypedValue.COMPLEX_UNIT_PX,21*VelaReading.scale(a));put(this,prefs,1394,142,350,58);
        cards=new FrameLayout(a);put(this,cards,44,232,1700,596);
        empty=label(L10n.text("Search your library, channels, apps and inputs."),28,VelaTheme.SECONDARY);empty.setMaxLines(3);empty.setGravity(Gravity.CENTER);put(cards,empty,160,120,1380,200);
        count=label("",20,VelaTheme.SECONDARY);put(this,count,44,856,970,60);
        previous=button("‹  "+L10n.text("Previous"),()->move(-1));next=button(L10n.text("Next")+"  ›",()->move(1));TextView back=button(L10n.text("Back"),host::close);put(this,back,1064,856,200,60);put(this,previous,1284,856,210,60);put(this,next,1514,856,230,60);
        input.setOnKeyListener((v,k,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(k==KeyEvent.KEYCODE_DPAD_DOWN){filters[filter].requestFocus();return true;}if(k==KeyEvent.KEYCODE_DPAD_UP)return host.header(v);if(k==KeyEvent.KEYCODE_DPAD_LEFT&&input.getSelectionStart()==0&&input.getSelectionEnd()==0){host.navigation();return true;}return false;});
        paintFilters();pager();
    }
    private TextView label(String s,int size,int color){TextView v=new TextView(activity);v.setText(s);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*VelaReading.scale(activity));v.setTextColor(secondary(color));v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private int secondary(int color){return VelaReading.prefs(activity).getBoolean("contrast",false)&&(color==VelaTheme.SECONDARY||color==VelaTheme.MUTED)?0xFFE0E1E8:color;}
    private TextView button(String s,Runnable action){TextView v=label(s,23,VelaTheme.WHITE);v.setGravity(Gravity.CENTER);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(VelaTheme.shape(VelaTheme.PANEL,18));v.setOnClickListener(w->action.run());v.setOnFocusChangeListener((w,f)->{if(f)host.focused();v.setBackground(VelaTheme.shape(f?VelaReading.accent(activity):VelaTheme.PANEL,18));v.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);if(Arrays.asList(filters).contains(v))paintFilters();});return v;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    static int kind(VelaHub.Hit h){return h.value instanceof Intent?3:h.value instanceof TvInputs.Entry?4:h.value instanceof LiveTvData.Channel||h.value instanceof VelaShows.Airing?2:1;}
    private void paintFilters(){for(int i=0;i<filters.length;i++){TextView v=(TextView)filters[i];if(v==null)continue;boolean focus=v.hasFocus();v.setBackground(VelaTheme.shape(focus?VelaReading.accent(activity):i==filter?VelaTheme.RAISED:VelaTheme.PANEL,18));v.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);}}
    void loading(String query){if(!query.equals(lastQuery)){filter=0;paintFilters();lastQuery=query;}loading=true;all.clear();visible.clear();input.setText(query);releaseCards();empty.setText(L10n.text("Searching…"));empty.setVisibility(VISIBLE);count.setText("");pager();}
    void idle(){loading=false;more=false;localPage=backendPage=0;releaseCards();all.clear();visible.clear();empty.setText(L10n.text("Search your library, channels, apps and inputs."));empty.setVisibility(VISIBLE);count.setText("");pager();}
    void error(){loading=false;empty.setText(L10n.text("Could not search. Try again."));empty.setVisibility(VISIBLE);pager();}
    void results(java.util.List<VelaHub.Hit> values,int page,boolean hasMore){
        if(closed)return;loading=false;all.clear();all.addAll(values.subList(0,Math.min(96,values.size())));backendPage=page;more=hasMore||values.size()>96;localPage=0;render(hasFocus());
    }
    private void chooseFilter(int at){filter=at;localPage=0;paintFilters();if(!loading)render(false);}
    private void releaseCards(){for(View v:tiles)if(v instanceof ViewGroup){View icon=((ViewGroup)v).getChildAt(0);if(icon instanceof ImageView)((ImageView)icon).setImageDrawable(null);}for(View v:tiles)cards.removeView(v);tiles.clear();}
    private void render(boolean focus){
        releaseCards();visible.clear();for(VelaHub.Hit h:all)if(filter==0||kind(h)==filter)visible.add(h);int start=localPage*pageSize;
        for(int i=start;i<Math.min(start+pageSize,visible.size());i++)addCard(visible.get(i),i-start);
        empty.setVisibility(tiles.isEmpty()?VISIBLE:GONE);empty.setText(all.isEmpty()?L10n.text("No matches. Try another name."):L10n.text("No matches in this category."));
        count.setText(visible.isEmpty()?L10n.text("No results"):L10n.text("Results")+" "+(start+1)+"–"+Math.min(start+pageSize,visible.size())+" / "+visible.size()+"  ·  "+L10n.text("Page ")+(backendPage+1));pager();if(focus&&!tiles.isEmpty()&&isShown())tiles.get(0).requestFocus();
    }
    private void addCard(VelaHub.Hit hit,int i){
        FrameLayout tile=new FrameLayout(activity);tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setDefaultFocusHighlightEnabled(false);tile.setBackground(VelaTheme.panel());tile.setPadding(0,0,0,0);
        ImageView icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);Drawable image=null;
        if(hit.value instanceof Intent)try{Intent intent=(Intent)hit.value;if(intent.getComponent()!=null)image=activity.getPackageManager().getActivityIcon(intent.getComponent());}catch(Exception unavailable){}
        icon.setImageDrawable(image==null?new TypeIcon(kind(hit)):image);put(tile,icon,28,pageSize==4?102:54,76,76);
        TextView type=label(hit.kind,18,VelaTheme.MUTED);type.setSingleLine();type.setEllipsize(TextUtils.TruncateAt.END);put(tile,type,130,21,666,28);
        TextView name=label(hit.title,29,VelaTheme.WHITE);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setTypeface(Ui.displaySemibold);put(tile,name,130,52,666,pageSize==4?154:90);
        TextView detail=label(hit.detail,20,VelaTheme.SECONDARY);detail.setSingleLine();detail.setEllipsize(TextUtils.TruncateAt.END);put(tile,detail,130,pageSize==4?232:146,666,pageSize==4?38:30);
        tile.setContentDescription(hit.title+", "+hit.kind+", "+hit.detail);tile.setOnClickListener(v->host.open(hit));tile.setOnLongClickListener(v->{host.save(hit);return true;});
        tile.setOnFocusChangeListener((v,f)->{if(f)host.focused();tile.setBackground(f?VelaTheme.shape(VelaReading.accent(activity),VelaTheme.PANEL_RADIUS):VelaTheme.panel());name.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);type.setTextColor(f?0xFF555A65:secondary(VelaTheme.MUTED));detail.setTextColor(f?0xFF555A65:secondary(VelaTheme.SECONDARY));if(icon.getDrawable() instanceof TypeIcon)((TypeIcon)icon.getDrawable()).color(f?VelaTheme.INK:VelaTheme.WHITE);});
        final int index=i;tile.setOnKeyListener((v,key,event)->{if(key<KeyEvent.KEYCODE_DPAD_UP||key>KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(index+2<tiles.size())tiles.get(index+2).requestFocus();else if(next.isEnabled())next.requestFocus();else previous.requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP){if(index>=2)tiles.get(index-2).requestFocus();else filters[filter].requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(index%2==0&&index+1<tiles.size()){tiles.get(index+1).requestFocus();return true;}return true;}
            if(index%2==1){tiles.get(index-1).requestFocus();return true;}host.navigation();return true;
        });put(cards,tile,(i%2)*860,(i/2)*(pageSize==4?308:200),840,pageSize==4?280:184);tiles.add(tile);
    }
    private void pager(){previous.setEnabled(!loading&&(localPage>0||backendPage>0));next.setEnabled(!loading&&((localPage+1)*pageSize<visible.size()||more));previous.setAlpha(previous.isEnabled()?1:.35f);next.setAlpha(next.isEnabled()?1:.35f);}
    private void move(int direction){if(loading)return;if(direction<0&&localPage>0||direction>0&&(localPage+1)*pageSize<visible.size()){localPage+=direction;render(true);}else if(direction<0&&backendPage>0||direction>0&&more)host.next(direction);}
    void focusFirst(){if(!tiles.isEmpty())tiles.get(0).requestFocus();else input.requestFocus();}
    void partial(boolean value){if(value)count.append(" · "+L10n.text("Some sources are unavailable. Try again."));}
    void message(String text){count.setText(text);}
    void close(){closed=true;releaseCards();all.clear();visible.clear();}
    private static final class TypeIcon extends Drawable {
        final int type;final Paint p=new Paint(3);final Path play=new Path();int color=VelaTheme.WHITE;TypeIcon(int type){this.type=type;play.moveTo(34,28);play.lineTo(51,40);play.lineTo(34,52);play.close();}
        void color(int value){color=value;invalidateSelf();}
        public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/80f,b.height()/80f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);
            if(type==2||type==4){c.drawRoundRect(9,17,71,59,8,8,p);c.drawLine(27,69,53,69,p);c.drawLine(40,59,40,69,p);if(type==2){c.drawLine(29,8,40,17,p);c.drawLine(51,8,40,17,p);}}
            else{c.drawRoundRect(13,9,67,71,10,10,p);c.drawPath(play,p);}c.restore();}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
