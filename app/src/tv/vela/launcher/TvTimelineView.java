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
        default void correction(LiveTvData.Channel channel){}
        default boolean history(){return false;}
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
    private boolean followClock=true;
    private boolean loading;private String wrapTitle="",wrapFirst="",wrapSecond="";private int wrapWidth;private float wrapScale;
    private final TvRemotePress remote;
    private Set<String> favoriteIds=Collections.emptySet();
    void favorites(Set<String> ids){favoriteIds=new HashSet<>(ids);invalidate();describe();}
    TvTimelineView(Context context,Actions actions){
        super(context);this.actions=actions;setWillNotDraw(false);setFocusableInTouchMode(true);setFocusable(false);setClickable(true);setId(View.generateViewId());setDefaultFocusHighlightEnabled(false);
        remote=new TvRemotePress(this,()->performClick(),()->performLongClick());
        setOnClickListener(v->{if(!channels.isEmpty())actions.activate(channels.get(row),programme());});
        setOnLongClickListener(v->{if(!channels.isEmpty())actions.options(channels.get(row));return true;});
        setOnFocusChangeListener((v,focused)->{if(!focused)remote.cancel();invalidate();if(focused)notifySelection();});
    }
    static long present(long now){return now-now%1800;}
    void rowHeight(int pixels){rowHeight=Math.max(ROW,Math.min(180,pixels));requestLayout();invalidate();}
    void data(List<LiveTvData.Channel> visible,Map<String,List<LiveTvData.Programme>> values,long from,LiveTvData.Channel selected,boolean waiting){
        int count=Math.min(VelaReading.rows(getContext()),visible.size());boolean pageChanged=channels.size()!=count,sameRecords=!pageChanged;for(int i=0;i<Math.min(channels.size(),count);i++){pageChanged|=!channels.get(i).id.equals(visible.get(i).id);sameRecords&=channels.get(i)==visible.get(i);}
        boolean changed=from!=start;Map<String,List<LiveTvData.Programme>> retained=waiting&&sameRecords&&!changed&&values.isEmpty()?programmes:values;start=from;now=System.currentTimeMillis()/1000;loading=waiting;channels.clear();channels.addAll(visible.subList(0,count));programmes=retained;setFocusable(!channels.isEmpty());
        row=pageChanged?0:Math.max(0,Math.min(row,channels.size()-1));if(selected!=null)for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(selected.id))row=i;
        if(cursor<start||cursor>=start+SECONDS)cursor=Math.max(start,Math.min(start+SECONDS-1,now));
        invalidate();notifySelection();
    }
    void followNow(long value){setCursor(value,true);}
    boolean followsNow(){return followClock;}
    void cursor(long value){setCursor(value,false);}
    private void setCursor(long value,boolean live){followClock=live;cursor=Math.max(start,Math.min(start+SECONDS-1,value));invalidate();notifySelection();}
    long cursorTime(){return cursor;}
    void minute(long value){
        now=value;
        if(followClock&&value>=start&&value<start+SECONDS){cursor=value;notifySelection();}
        else if(hasFocus())notifySelection();
        invalidate();
    }
    private List<LiveTvData.Programme> schedule(){return channels.isEmpty()?Collections.emptyList():programmes.getOrDefault(channels.get(row).id,Collections.emptyList());}
    LiveTvData.Programme programme(){for(LiveTvData.Programme p:schedule())if(p.start<=cursor&&p.end>cursor)return p;return null;}
    private void describe(){if(channels.isEmpty())return;LiveTvData.Programme p=programme();LiveTvData.Channel c=channels.get(row);setContentDescription((favoriteIds.contains(c.id)?"★ ":"")+LiveTvActivity.displayName(c.name)+" · "+(p==null?Ui.tr("Sem programação","No programme information","Sin programación"):p.title+" · "+time(p.start)+"–"+time(p.end)));}
    private void notifySelection(){if(channels.isEmpty())return;describe();actions.selected(channels.get(row),programme());}
    private final android.util.LruCache<Long,String> times=new android.util.LruCache<>(96);
    private final Date stamp=new Date();
    private String time(long value){
        long minute=value/60;String cached=times.get(minute);
        if(cached==null){stamp.setTime(value*1000);cached=clock.format(stamp);times.put(minute,cached);}
        return cached;
    }
    LiveTvData.Programme following(LiveTvData.Programme selected){
        if(selected==null)return null;
        for(LiveTvData.Programme p:schedule())if(p.start>=selected.end)return p;
        return null;
    }
    static String startsIn(long seconds){
        long minutes=Math.max(1,(seconds+59)/60),h=minutes/60,m=minutes%60;
        String duration=h==0?m+" min":m==0?h+" h":h+" h "+m+" min";
        return String.format(Ui.locale(),L10n.text("Starts in %s"),duration);
    }
    static long windowFor(long previous,int direction,long target){
        long next=previous+direction*5400;
        if(target<next||target>=next+SECONDS)next=present(target);
        return next;
    }
    private float x(long value){return LABEL+(getWidth()-LABEL)*(value-start)/(float)SECONDS;}
    private float textScale=1f;
    private int focusColor;
    private void label(Canvas c,String value,float x,float baseline,float width,int size,int color,Typeface font){if(width<12)return;paint.setTypeface(font);paint.setTextSize(size*textScale);paint.setColor(color);c.drawText(TextUtils.ellipsize(value,paint,width,TextUtils.TruncateAt.END).toString(),x,baseline,paint);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);textScale=VelaReading.scale(getContext());focusColor=VelaTheme.pearl(Appearance.accent(getContext()));int w=getWidth();
        label(c,date.format(new Date(start*1000)),12,31,LABEL-24,18,VelaTheme.SECONDARY,Ui.medium);
        boolean onAir=now>=start&&now<start+SECONDS&&!channels.isEmpty();
        float hand=onAir?x(now):-200;
        float tag=Math.min(w-88,Math.max(LABEL+4,hand-40));
        for(int i=0;i<6;i++){float px=LABEL+(w-LABEL)*i/6f;if(!onAir||px+76<tag||px+6>tag+84)label(c,time(start+i*1800),px+10,31,(w-LABEL)/6f-20,18,VelaTheme.SECONDARY,Ui.medium);paint.setColor(0x10FFFFFF);c.drawRect(px,HEADER,px+1,HEADER+channels.size()*rowHeight,paint);}
        for(int i=0;i<channels.size();i++){
            float top=HEADER+i*rowHeight;boolean focused=hasFocus()&&row==i;paint.setColor(0x16FFFFFF);c.drawRect(0,top+rowHeight-4,w,top+rowHeight-3,paint);
            if(focused){paint.setColor(0x13FFFFFF);c.drawRoundRect(0,top,LABEL-7,top+rowHeight-8,12,12,paint);paint.setColor(focusColor);c.drawRoundRect(0,top+18,3,top+rowHeight-24,1.5f,1.5f,paint);}
            if(favoriteIds.contains(channels.get(i).id))label(c,"★",LABEL-31,top+22,22,12,VelaTheme.SECONDARY,Ui.medium);
            label(c,LiveTvActivity.displayName(channels.get(i).name),80,top+36+(rowHeight-ROW)/2f,LABEL-106,21,VelaTheme.WHITE,Ui.medium);
            String quality=channels.get(i).antenna()?channels.get(i).number:TvChannelNames.quality(channels.get(i).name);label(c,quality,80,top+60+(rowHeight-ROW)/2f,LABEL-106,13,VelaTheme.MUTED,Ui.medium);
            List<LiveTvData.Programme> list=programmes.getOrDefault(channels.get(i).id,Collections.emptyList());long edge=start;
            c.save();c.clipRect(LABEL,top,w,top+rowHeight-2);
            for(LiveTvData.Programme p:list){if(p.end<=start||p.start>=start+SECONDS)continue;if(p.start>edge)block(c,edge,Math.min(p.start,start+SECONDS),top,null,focused);block(c,Math.max(start,p.start),Math.min(start+SECONDS,p.end),top,p,focused);edge=Math.max(edge,p.end);}
            if(edge<start+SECONDS)block(c,edge,start+SECONDS,top,null,focused);c.restore();
        }
        if(onAir){
            float px=hand;
            paint.setColor(0xFF334749);c.drawRoundRect(tag,5,tag+84,41,18,18,paint);
            label(c,time(now),tag+12,30,64,17,VelaTheme.WHITE,Ui.semibold);
            paint.setColor(0xFF97C7BC);
            c.drawRect(px-1,40,px+1,HEADER-4,paint);c.drawCircle(px,HEADER-5,3.5f,paint);
            for(int i=1;i<=channels.size();i++){
                float bottom=HEADER+i*rowHeight;
                c.drawRect(px-1,bottom-7,px+1,bottom-1,paint);
            }
        }
    }
    private void block(Canvas c,long from,long to,float top,LiveTvData.Programme p,boolean focused){
        if(to<=from)return;float left=Math.max(LABEL,x(from))+3,right=Math.min(getWidth(),x(to))-3;if(right-left<3){
            if(focused&&cursor>=from&&cursor<to){paint.setColor(focusColor);float mid=(left+right)/2;c.drawRect(mid-1,top+8,mid+1,top+rowHeight-16,paint);}
            return;
        }
        boolean chosen=focused&&(p==null?cursor>=from&&cursor<to:p.start<=cursor&&p.end>cursor);paint.setColor(chosen?focusColor:p==null?0x044A5558:p.current(now)?0x10455B65:0x076B7B88);c.drawRoundRect(left,top,right,top+rowHeight-8,12,12,paint);
        if(!chosen){paint.setColor(0x32FFFFFF);c.drawRect(right,top+15,right+1,top+rowHeight-22,paint);}
        if(right-left<104)return;
        String title=p==null?(loading?Ui.tr("Carregando…","Loading…","Cargando…"):Ui.tr("Sem programação","No programme information","Sin programación")):p.title;
        float reading=textScale,titleBaseline=top+31+(rowHeight-ROW)/2f,timingBaseline=top+rowHeight-20,lineStep=23*reading;
        paint.setTypeface(Ui.medium);paint.setTextSize(21*reading);float descent=paint.descent();paint.setTextSize(14*reading);float timingTop=timingBaseline+paint.ascent();
        boolean expanded=chosen&&p!=null&&right-left>=180*reading&&titleBaseline+lineStep+descent<=timingTop-4;
        if(expanded){int width=(int)(right-left-28);if(!title.equals(wrapTitle)||width!=wrapWidth||wrapScale!=reading){wrapTitle=title;wrapWidth=width;wrapScale=reading;paint.setTypeface(Ui.medium);paint.setTextSize(21*reading);int n=paint.breakText(title,true,width,null);if(n<title.length()){int space=title.lastIndexOf(' ',n);if(space>n/2)n=space;if(n>0&&Character.isHighSurrogate(title.charAt(n-1)))n--;wrapFirst=title.substring(0,n);wrapSecond=title.substring(n).trim();}else{wrapFirst=title;wrapSecond="";}}label(c,wrapFirst,left+14,titleBaseline,width,21,VelaTheme.INK,Ui.medium);if(!wrapSecond.isEmpty())label(c,wrapSecond,left+14,titleBaseline+lineStep,width,21,VelaTheme.INK,Ui.medium);}
        else label(c,title,left+14,titleBaseline,right-left-28,21,chosen?VelaTheme.INK:p==null?VelaTheme.MUTED:VelaTheme.WHITE,Ui.medium);
        if(p!=null&&right-left>=180*reading){String timing=time(p.start)+"–"+time(p.end);if(p.current(now)){String remaining=LiveTvActivity.remainingText(p.remainingMinutes(now));timing=right-left<330*reading?remaining:timing+"  ·  "+remaining;}label(c,timing,left+14,timingBaseline,right-left-28,14,chosen?0xFF4F5857:VelaTheme.SECONDARY,Ui.regular);}
        if(chosen&&p!=null&&p.current(now)&&right-left>=180*reading){paint.setColor(0x28626773);c.drawRoundRect(left+14,top+rowHeight-13,right-14,top+rowHeight-10,1.5f,1.5f,paint);paint.setColor(VelaTheme.INK);c.drawRoundRect(left+14,top+rowHeight-13,left+14+(right-left-28)*p.progress(now)/1000f,top+rowHeight-10,1.5f,1.5f,paint);}
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_INFO&&!channels.isEmpty()){if(event.getRepeatCount()==0)actions.correction(channels.get(row));return true;}
        if(remote.key(event))return true;
        if(channels.isEmpty())return super.onKeyDown(key,event);
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){int direction=key==KeyEvent.KEYCODE_DPAD_UP?-1:1,target=row+direction;if(target<0||target>=channels.size()){if(actions.channels(direction))return true;return super.onKeyDown(key,event);}row=target;invalidate();notifySelection();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int direction=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;LiveTvData.Programme p=programme();long target=p==null?cursor+direction*1800:direction>0?p.end:p.start-1;if(target<start||target>=start+SECONDS){if(direction<0&&start<=present(now)&&!actions.history()){return super.onKeyDown(key,event);}actions.time(direction,target);}else cursor(target);return true;}
        return super.onKeyDown(key,event);
    }
    @Override protected void onDetachedFromWindow(){remote.cancel();super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)remote.cancel();}
    @Override public boolean onKeyUp(int key,KeyEvent event){if(remote.key(event))return true;if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;return super.onKeyUp(key,event);}
    int logoY(int index){return HEADER+index*rowHeight+(rowHeight-60)/2;}
}
