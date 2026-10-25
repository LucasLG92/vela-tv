package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Six channels and a three-hour window. Programmes are painted, not inflated into a view tree. */
final class TvTimelineView extends FrameLayout {
    static final int ROWS=6,SECONDS=3*3600,MAX_PROGRAMMES=32;
    private static final int LABEL=222,HEADER=58,ROW=88;
    interface Actions {
        void selected(LiveTvData.Channel channel,LiveTvData.Programme programme);
        void activate(LiveTvData.Channel channel,LiveTvData.Programme programme);
        void options(LiveTvData.Channel channel);
        boolean channels(int direction);
        void time(int direction,long cursor);
    }
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat clock=new SimpleDateFormat("HH:mm",Ui.locale());
    private final SimpleDateFormat date=new SimpleDateFormat("EEE d MMM",Ui.locale());
    private final List<LiveTvData.Channel> channels=new ArrayList<>();
    private Map<String,List<LiveTvData.Programme>> programmes=Collections.emptyMap();
    private final Actions actions;
    private long start,cursor,now;
    private int row,rowHeight=ROW;
    private boolean loading;
    private final TvRemotePress remote;
    private Set<String> favoriteIds=Collections.emptySet();
    void favorites(Set<String> ids){favoriteIds=new HashSet<>(ids);invalidate();describe();}
    TvTimelineView(Context context,Actions actions){
        super(context);this.actions=actions;setWillNotDraw(false);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setId(View.generateViewId());setDefaultFocusHighlightEnabled(false);
        remote=new TvRemotePress(this,()->performClick(),()->performLongClick());
        setOnClickListener(v->{if(!channels.isEmpty())actions.activate(channels.get(row),programme());});
        setOnLongClickListener(v->{if(!channels.isEmpty())actions.options(channels.get(row));return true;});
        setOnFocusChangeListener((v,focused)->{if(!focused)remote.cancel();invalidate();if(focused)notifySelection();});
    }
    static long present(long now){return now-now%1800;}
    void rowHeight(int pixels){rowHeight=Math.max(ROW,Math.min(120,pixels));requestLayout();invalidate();}
    void data(List<LiveTvData.Channel> visible,Map<String,List<LiveTvData.Programme>> values,long from,LiveTvData.Channel selected,boolean waiting){
        boolean changed=from!=start;start=from;now=System.currentTimeMillis()/1000;loading=waiting;channels.clear();channels.addAll(visible.subList(0,Math.min(ROWS,visible.size())));programmes=values;
        row=Math.max(0,Math.min(row,channels.size()-1));if(selected!=null)for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(selected.id))row=i;
        if(cursor<start||cursor>=start+SECONDS)cursor=Math.max(start,Math.min(start+SECONDS-1,now));
        invalidate();if(hasFocus()||changed)notifySelection();
    }
    void cursor(long value){cursor=Math.max(start,Math.min(start+SECONDS-1,value));invalidate();notifySelection();}
    long cursorTime(){return cursor;}
    void minute(long value){now=value;invalidate();if(hasFocus())notifySelection();}
    private List<LiveTvData.Programme> schedule(){return channels.isEmpty()?Collections.emptyList():programmes.getOrDefault(channels.get(row).id,Collections.emptyList());}
    LiveTvData.Programme programme(){for(LiveTvData.Programme p:schedule())if(p.start<=cursor&&p.end>cursor)return p;return null;}
    private void describe(){if(channels.isEmpty())return;LiveTvData.Programme p=programme();LiveTvData.Channel c=channels.get(row);setContentDescription((favoriteIds.contains(c.id)?"★ ":"")+LiveTvActivity.displayName(c.name)+" · "+(p==null?Ui.tr("Sem programação","No programme information","Sin programación"):p.title+" · "+time(p.start)+"–"+time(p.end)));}
    private void notifySelection(){if(channels.isEmpty())return;describe();actions.selected(channels.get(row),programme());}
    private String time(long value){return clock.format(new Date(value*1000));}
    private float x(long value){return LABEL+(getWidth()-LABEL)*(value-start)/(float)SECONDS;}
    private void label(Canvas c,String value,float x,float baseline,float width,int size,int color,Typeface font){if(width<12)return;paint.setTypeface(font);paint.setTextSize(size);paint.setColor(color);c.drawText(TextUtils.ellipsize(value,paint,width,TextUtils.TruncateAt.END).toString(),x,baseline,paint);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);now=System.currentTimeMillis()/1000;int w=getWidth();
        label(c,date.format(new Date(start*1000)),12,31,LABEL-24,18,VelaTheme.SECONDARY,Ui.medium);
        for(int i=0;i<6;i++){float px=LABEL+(w-LABEL)*i/6f;label(c,time(start+i*1800),px+10,31,(w-LABEL)/6f-20,18,VelaTheme.SECONDARY,Ui.medium);paint.setColor(0x10FFFFFF);c.drawRect(px,HEADER,px+1,HEADER+channels.size()*rowHeight,paint);}
        for(int i=0;i<channels.size();i++){
            float top=HEADER+i*rowHeight;boolean focused=hasFocus()&&row==i;paint.setColor(row==i?0x25363841:0x10262831);c.drawRoundRect(0,top,LABEL-10,top+rowHeight-8,13,13,paint);
            label(c,(favoriteIds.contains(channels.get(i).id)?"★ ":"")+LiveTvActivity.displayName(channels.get(i).name),80,top+36+(rowHeight-ROW)/2f,LABEL-106,21,0xFFF6F7FB,Ui.medium);
            String quality=channels.get(i).antenna()?channels.get(i).number:TvChannelNames.quality(channels.get(i).name);label(c,quality,80,top+60+(rowHeight-ROW)/2f,LABEL-106,13,VelaTheme.MUTED,Ui.medium);
            List<LiveTvData.Programme> list=programmes.getOrDefault(channels.get(i).id,Collections.emptyList());long edge=start;
            c.save();c.clipRect(LABEL,top,w,top+rowHeight-8);
            for(LiveTvData.Programme p:list){if(p.end<=start||p.start>=start+SECONDS)continue;if(p.start>edge)block(c,edge,Math.min(p.start,start+SECONDS),top,null,focused);block(c,Math.max(start,p.start),Math.min(start+SECONDS,p.end),top,p,focused);edge=Math.max(edge,p.end);}
            if(edge<start+SECONDS)block(c,edge,start+SECONDS,top,null,focused);c.restore();
        }
        if(now>=start&&now<start+SECONDS){float px=x(now);paint.setColor(Appearance.tint(getContext(),0xB3));c.drawCircle(px,HEADER-9,4,paint);c.drawRect(px-.75f,HEADER-5,px+.75f,HEADER+channels.size()*rowHeight-8,paint);}
    }
    private void block(Canvas c,long from,long to,float top,LiveTvData.Programme p,boolean focused){
        if(to<=from)return;float left=Math.max(LABEL,x(from))+3,right=Math.min(getWidth(),x(to))-3;if(right-left<3)return;
        boolean chosen=focused&&(p==null?cursor>=from&&cursor<to:p.start<=cursor&&p.end>cursor);paint.setColor(chosen?Appearance.accent(getContext()):p==null?0x16262831:p.current(now)?VelaTheme.RAISED:VelaTheme.CARD);c.drawRoundRect(left,top,right,top+rowHeight-8,12,12,paint);
        String title=p==null?(loading?Ui.tr("Carregando…","Loading…","Cargando…"):Ui.tr("Sem programação","No programme information","Sin programación")):p.title;
        label(c,title,left+14,top+31+(rowHeight-ROW)/2f,right-left-28,21,chosen?VelaTheme.INK:p==null?VelaTheme.MUTED:0xFFF0F3F8,Ui.medium);
        if(p!=null){String timing=time(p.start)+"–"+time(p.end);if(p.current(now))timing+="  ·  "+LiveTvActivity.remainingText(p.remainingMinutes(now));label(c,timing,left+14,top+59+(rowHeight-ROW)/2f,right-left-28,14,chosen?0xFF61636D:VelaTheme.SECONDARY,Ui.regular);}
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(remote.key(event))return true;
        if(channels.isEmpty())return super.onKeyDown(key,event);
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){int direction=key==KeyEvent.KEYCODE_DPAD_UP?-1:1,target=row+direction;if(target<0||target>=channels.size()){if(actions.channels(direction))return true;return super.onKeyDown(key,event);}row=target;invalidate();notifySelection();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int direction=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;LiveTvData.Programme p=programme();long target=p==null?cursor+direction*1800:direction>0?p.end:p.start-1;if(target<start||target>=start+SECONDS){if(direction<0&&start<=present(now)){return super.onKeyDown(key,event);}actions.time(direction,target);}else cursor(target);return true;}
        return super.onKeyDown(key,event);
    }
    @Override protected void onDetachedFromWindow(){remote.cancel();super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)remote.cancel();}
    @Override public boolean onKeyUp(int key,KeyEvent event){if(remote.key(event))return true;if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;return super.onKeyUp(key,event);}
    int logoY(int index){return HEADER+index*rowHeight+(rowHeight-60)/2;}
}
