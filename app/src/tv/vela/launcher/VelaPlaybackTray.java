package tv.vela.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

import tv.vela.launcher.VelaPlaybackPanel.Group;
import tv.vela.launcher.VelaPlaybackPanel.Item;

/** A disposable command strip on the active video surface. */
final class VelaPlaybackTray extends FrameLayout {
    private static final Group[] GROUPS=Group.values();
    private static final int VISIBLE_CHOICES=4;
    private final FrameLayout canvas;
    private TextView breadcrumb;
    private LinearLayout row;
    private final String programme;
    private final Drawable.ConstantState art;
    private float pixels=1;
    private final String channel;
    private final VelaPlaybackPanel.Model model;
    private final Runnable exit;
    private final List<Item> options=new ArrayList<>();
    private Group page;
    private int groupIndex,choiceIndex;
    private boolean closed;
    private VelaPlaybackPanel advanced;
    private final Runnable redraw=()->{
        if(closed)return;
        if(advanced==null)render();else advanced.refreshOptions();
    };

    VelaPlaybackTray(Context c,String channel,CharSequence programme,Drawable logo,VelaPlaybackPanel.Model model,Runnable exit){
        super(c);this.channel=channel;this.model=model;this.exit=exit;
        this.programme=TextUtils.isEmpty(programme)?channel:programme.toString();
        art=logo==null?null:logo.getConstantState();
        canvas=new FrameLayout(c);addView(canvas,new LayoutParams(-1,-1));setBackground(VelaPlayerUi.shade());
        buildStrip();
    }
    private void buildStrip(){
        Context c=getContext();canvas.removeAllViews();
        int titleX=100;
        if(art!=null){
            ImageView image=new ImageView(c);image.setImageDrawable(art.newDrawable(getResources()));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);put(canvas,image,100,817,54,54);titleX=176;
        }
        TextView title=label(34,VelaTheme.WHITE);
        title.setText(TextUtils.isEmpty(programme)?channel:programme);title.setTypeface(Ui.displaySemibold);put(canvas,title,titleX,806,990,69);
        breadcrumb=label(20,VelaTheme.SECONDARY);breadcrumb.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(canvas,breadcrumb,1185,817,635,52);
        View rule=new View(c);rule.setBackgroundColor(0x48FFFFFF);put(canvas,rule,100,894,1720,1);
        row=new LinearLayout(c);put(canvas,row,100,921,1720,103);
        render();
    }
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);
        float fit=Math.min(width/1920f,height/1080f)*(1-2*VelaReading.margin(getContext()));
        if(fit>0&&fit!=pixels){
            pixels=fit;LayoutParams p=(LayoutParams)canvas.getLayoutParams();
            p.width=px(1920);p.height=px(1080);p.gravity=Gravity.CENTER;
            buildStrip();
        }
        super.onMeasure(w,h);
    }
    private int px(int value){return Math.round(value*pixels);}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){
        LayoutParams p=new LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);parent.addView(v,p);
    }
    private TextView label(int size,int color){
        TextView t=new TextView(getContext());t.setSingleLine();t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTypeface(Ui.medium);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*pixels*VelaReading.scale(getContext()));return t;
    }
    private void render(){
        int target=page==null?groupIndex:choiceIndex;List<Item> all=model.items();options.clear();row.removeAllViews();
        breadcrumb.setText(page==null?L10n.text("Back · Return to programme"):L10n.text(page.label)+"   /   "+L10n.text("Back"));
        if(page==null){
            for(int i=0;i<GROUPS.length;i++){
                Group group=GROUPS[i];Item first=null,selected=null;
                for(Item item:all)if(item.group==group){if(first==null)first=item;if(item.selected)selected=item;}
                boolean available=first!=null;
                String value=!available?L10n.text("Unavailable"):group==Group.MORE?L10n.text("Signal and playback"):selected!=null?selected.title:"—";
                addChoice(i,L10n.text(group.label),value,false,available,i==target);
            }
        }else{
            for(Item item:all)if(item.group==page)options.add(item);
            if(options.isEmpty()){page=null;render();return;}
            choiceIndex=Math.min(target,options.size()-1);target=choiceIndex;
            int start=(choiceIndex/VISIBLE_CHOICES)*VISIBLE_CHOICES;
            for(int i=start;i<Math.min(start+VISIBLE_CHOICES,options.size());i++){
                Item item=options.get(i);addChoice(i,item.title,"",item.selected,true,i==target);
            }
            if(options.size()>VISIBLE_CHOICES)breadcrumb.append("    "+(choiceIndex+1)+" / "+options.size());
        }
    }
    private void addChoice(int index,String title,String detail,boolean checked,boolean available,boolean focus){
        Context c=getContext();FrameLayout tile=new FrameLayout(c);TextView name=label(26,VelaTheme.WHITE),value=label(19,VelaTheme.SECONDARY);
        int left=page==null?88:26;
        if(page==null)put(tile,new Symbol(c,GROUPS[index]),26,29,44,44);
        name.setText(title);value.setText(detail);put(tile,name,left,detail.isEmpty()?12:8,405-left,detail.isEmpty()?77:50);
        if(!detail.isEmpty())put(tile,value,left,54,405-left,34);
        TextView tick=label(27,0xFF8AB6A9);if(checked){tick.setText("✓");put(tile,tick,367,29,38,40);}
        tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setDefaultFocusHighlightEnabled(false);
        tile.setContentDescription(title+(detail.isEmpty()?"":", "+detail));tile.setSelected(checked);
        tile.setOnFocusChangeListener((v,f)->{
            if(f){if(page==null)groupIndex=index;else choiceIndex=index;}
            style(tile,name,value,tick,available,f);
        });
        tile.setOnClickListener(v->{if(!available){Toast.makeText(c,L10n.text("This option is unavailable for the current signal."),Toast.LENGTH_SHORT).show();return;}activate(index);});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(416),px(103));p.rightMargin=px(14);row.addView(tile,p);
        style(tile,name,value,tick,available,false);if(focus)tile.requestFocus();
    }
    private void style(View tile,TextView name,TextView value,TextView tick,boolean available,boolean focused){
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(px(19));shape.setColor(focused?0xFFE8EBE7:0x00000000);tile.setBackground(shape);
        View first=((FrameLayout)tile).getChildAt(0);
        if(first instanceof Symbol)((Symbol)first).tint(focused?VelaTheme.INK:VelaTheme.WHITE);
        name.setTextColor(focused?VelaTheme.INK:available?VelaTheme.WHITE:VelaTheme.SECONDARY);
        value.setTextColor(focused?0xFF464E50:VelaTheme.SECONDARY);tick.setTextColor(focused?0xFF24644F:0xFF8AB6A9);
    }
    /** Four small line symbols in the same coordinate space as their focus tile. */
    private static final class Symbol extends View {
        private final Group group;
        private final android.graphics.Paint pen=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path=new android.graphics.Path();
        Symbol(Context c,Group group){super(c);this.group=group;pen.setStrokeWidth(1.7f);pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);pen.setStrokeJoin(android.graphics.Paint.Join.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void tint(int color){pen.setColor(color);invalidate();}
        @Override protected void onDraw(android.graphics.Canvas c){
            int save=c.save();c.scale(getWidth()/28f,getHeight()/28f);pen.setStyle(android.graphics.Paint.Style.STROKE);
            switch(group){
                case AUDIO:
                    path.reset();path.moveTo(3,11);path.lineTo(8,11);path.lineTo(14,6);path.lineTo(14,22);path.lineTo(8,17);path.lineTo(3,17);path.close();c.drawPath(path,pen);
                    c.drawArc(10,8,21,20,-53,106,false,pen);c.drawArc(7,4,26,24,-50,100,false,pen);break;
                case SUBTITLES:
                    c.drawRoundRect(2,5,26,23,4,4,pen);c.drawLine(6,15,12,15,pen);c.drawLine(15,15,22,15,pen);c.drawLine(8,19,20,19,pen);break;
                case PICTURE:
                    c.drawRoundRect(2,5,26,23,4,4,pen);c.drawLine(8,18,12,14,pen);c.drawLine(16,14,20,10,pen);break;
                case MORE:
                    pen.setStyle(android.graphics.Paint.Style.FILL);for(int x=5;x<=23;x+=9)c.drawCircle(x,14,1.9f,pen);break;
            }
            c.restoreToCount(save);
        }
    }
    private void activate(int index){
        if(page==null){
            groupIndex=index;Group group=GROUPS[index];
            if(group==Group.MORE){
                canvas.setVisibility(INVISIBLE);
                advanced=new VelaPlaybackPanel(getContext(),channel,()->{
                    List<Item> extra=new ArrayList<>();for(Item i:model.items())if(i.group==Group.MORE)extra.add(i);return extra;
                },()->{advanced=null;if(!closed){canvas.setVisibility(VISIBLE);render();}},L10n.text("More"),L10n.text("Back"));
                addView(advanced,new LayoutParams(-1,-1));return;
            }
            page=group;choiceIndex=0;
            int i=0;for(Item item:model.items())if(item.group==group){if(item.selected){choiceIndex=i;break;}i++;}
        }else{
            if(index>=options.size())return;
            try{options.get(index).action.run();}catch(RuntimeException unavailable){
                Toast.makeText(getContext(),L10n.text("This option is unavailable for the current signal."),Toast.LENGTH_SHORT).show();
            }
            page=null;
        }
        if(!closed)render();
    }
    void moreOptionsChanged(){if(advanced!=null)advanced.refreshOptions();}
    void tracksChanged(){removeCallbacks(redraw);post(redraw);}
    boolean remote(KeyEvent e){
        if(advanced!=null)return advanced.remote(e);
        int key=e.getKeyCode();boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(key==KeyEvent.KEYCODE_MENU){if(down&&e.getRepeatCount()==0)close();return true;}
        if(key==KeyEvent.KEYCODE_BACK){
            if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled()){
                if(page!=null){page=null;render();}else close();
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
            if(down){
                int delta=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;
                if(page==null){int next=Math.max(0,Math.min(GROUPS.length-1,groupIndex+delta));row.getChildAt(next).requestFocus();}
                else{int next=Math.max(0,Math.min(options.size()-1,choiceIndex+delta));if(next/VISIBLE_CHOICES==choiceIndex/VISIBLE_CHOICES)row.getChildAt(next%VISIBLE_CHOICES).requestFocus();else{choiceIndex=next;render();}}
            }return true;
        }
        if(TvRemotePress.select(key)){
            View focused=row.findFocus();
            if(focused!=null)focused.dispatchKeyEvent(e);
            return true;
        }
        return key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
    }
    void close(){if(closed)return;closed=true;removeCallbacks(redraw);if(advanced!=null)advanced.close();
        if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);exit.run();}
}
