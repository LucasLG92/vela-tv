package tv.vela.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.FrameLayout;

/** Shared passive player styling. No timer, bitmap cache, request or decoder. */
final class VelaPlayerUi {
    static final int INFO_MS=4000,INTRO_MS=3000;
    private VelaPlayerUi(){}
    static GradientDrawable shade(){
        // Equal stops also work on API 26. The upper half is completely clear.
        int clear=0x00080A0D;
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{
            clear,clear,clear,clear,clear,clear,0x06080A0D,0x2F080A0D,0x78080A0D,
            0xA7080A0D,0xCF080A0D,0xEC080A0D,0xFA080A0D});
    }
    static GradientDrawable wing(){
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xC5101317,0xF5111418,0xFB101216,0xFF101216});
    }
    static final class Information extends FrameLayout {
        final TextView title,channel,remaining;
        final UpNextView next;
        final Timeline rail;
        private View logo;
        private LiveTvData.Programme current,following,replay;
        private String channelName="";
        private boolean hasArtwork,hasLiveTitle;
        private float scale=1;

        Information(Context context){
            super(context);
            setBackground(shade());
            channel=label(context,Ui.medium,VelaTheme.SECONDARY);
            title=label(context,Ui.displaySemibold,VelaTheme.WHITE);
            remaining=label(context,Ui.medium,VelaTheme.WHITE);
            remaining.setGravity(Gravity.CENTER);
            next=new UpNextView(context);
            rail=new Timeline(context);
            for(View v:new View[]{channel,title,next,rail,remaining})addView(v,new LayoutParams(0,0));
        }
        private static TextView label(Context c,Typeface face,int color){
            TextView t=new TextView(c);
            t.setTypeface(face);t.setTextColor(color);t.setIncludeFontPadding(false);
            t.setGravity(Gravity.CENTER_VERTICAL);t.setSingleLine();
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);legible(t);return t;
        }
        void logo(View view){
            if(logo!=null)removeView(logo);
            logo=view;addView(view,new LayoutParams(0,0));
        }
        void channelName(String name){
            channelName=name;channel.setContentDescription(name);
            updateLabel();
        }
        void artAvailable(boolean available){
            if(hasArtwork!=available){hasArtwork=available;updateLabel();}
        }
        private void updateLabel(){
            channel.setText(hasLiveTitle&&hasArtwork?L10n.text("On now"):channelName);
        }
        void bind(LiveTvData.Programme on,LiveTvData.Programme after,LiveTvData.Programme archive,long now){
            current=on;following=after;replay=archive;refresh(now);
        }
        private void refresh(long now){
            LiveTvData.Programme on=current!=null&&current.current(now)?current:
                    following!=null&&following.current(now)?following:null;
            if(on!=null&&(on.title==null||on.title.trim().isEmpty()))on=null;
            boolean live=replay==null&&on!=null;
            boolean future=replay==null&&following!=null&&(on==null||following.start>=on.end)
                    &&next.bind(following.title,following.start,following.end,now);
            if(!future)next.bind(null,0,0,now);
            hasLiveTitle=live;updateLabel();
            title.setText(replay!=null?replay.title:live?on.title:L10n.text("Programme information unavailable"));
            remaining.setText(replay!=null?L10n.text("Past programme · ")+time(replay.start):
                    live?LiveTvActivity.remainingText(on.remainingMinutes(now)):"");
            boolean newRail=live&&rail.getVisibility()!=VISIBLE;
            rail.setVisibility(live?VISIBLE:GONE);
            rail.value(live?on.progress(now):0,future&&live&&following.start==on.end);
            if(newRail)rail.reveal();
            remaining.setVisibility(live||replay!=null?VISIBLE:GONE);
            requestLayout();
        }
        void present(){
            animate().cancel();setAlpha(1);setVisibility(VISIBLE);
            refresh(System.currentTimeMillis()/1000);
            rail.reveal();
        }
        void fadeOut(){
            if(!android.animation.ValueAnimator.areAnimatorsEnabled()){setVisibility(GONE);return;}
            animate().alpha(0).setDuration(180).withEndAction(()->setVisibility(GONE)).start();
        }
        @Override public void setVisibility(int visibility){
            if(visibility!=getVisibility()&&visibility!=VISIBLE){
                animate().cancel();setAlpha(1);
            }
            super.setVisibility(visibility);
        }
        private void place(View view,int x,int y,int w,int h,int size){
            LayoutParams p=(LayoutParams)view.getLayoutParams();
            p.width=Math.round(w*scale);p.height=Math.round(h*scale);
            p.leftMargin=Math.round(x*scale);p.topMargin=Math.round(y*scale);
            if(size>0){
                TextView t=(TextView)view;float px=size*scale;
                if(Math.abs(t.getTextSize()-px)>.1f)t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px);
            }
        }
        @Override protected void onMeasure(int w,int h){
            scale=Math.min(MeasureSpec.getSize(w)/1920f,MeasureSpec.getSize(h)/1080f);
            boolean withNext=next.getVisibility()==VISIBLE;
            int origin=100,span=1720,split=origin+Math.round(span*Timeline.NEXT);
            if(logo!=null)place(logo,origin,877,68,78,0);
            place(channel,194,873,(withNext?split:1820)-224,34,24);
            place(title,192,904,(withNext?split:1820)-220,66,57);
            place(next,split,877,1820-split,93,0);
            place(rail,origin,969,span,22,0);
            int head=origin+Math.round(span*rail.position());
            float timingWidth=remaining.getPaint().measureText(remaining.getText(),0,remaining.length());
            int labelWidth=replay==null?Math.min(span,24+Math.round(timingWidth*23/remaining.getTextSize())):800;
            int left=replay!=null?origin:Math.max(origin,Math.min(head-labelWidth/2,1820-labelWidth));
            remaining.setGravity(replay!=null?Gravity.LEFT|Gravity.CENTER_VERTICAL:Gravity.CENTER);
            place(remaining,left,996,labelWidth,34,23);
            super.onMeasure(w,h);
        }
    }
    static void legible(TextView label){
        label.setShadowLayer(3f,0,1.5f,0xEE000000);
    }
    static final class Signal extends TextView {
        private boolean replay;
        private String quality="";
        Signal(Context c){
            super(c);setSingleLine();setIncludeFontPadding(false);setGravity(Gravity.CENTER);
            setTextSize(TypedValue.COMPLEX_UNIT_PX,18);setTypeface(Ui.semibold);setTextColor(VelaTheme.WHITE);
            setLetterSpacing(.045f);setBackground(VelaTheme.shape(0xDF171A1E,100));drawLabel();
        }
        void reset(boolean archived){replay=archived;quality="";drawLabel();}
        void resolution(int height){
            String value=height==2160?"4K UHD":height>0?height+"p":"";
            if(!quality.equals(value)){quality=value;drawLabel();}
        }
        private void drawLabel(){
            String label=replay?Ui.tr("ARQUIVO","REPLAY","ARCHIVO"):Ui.tr("AO VIVO","LIVE","EN DIRECTO");
            SpannableStringBuilder line=new SpannableStringBuilder(replay?"↺  ":"●  ").append(label);
            line.setSpan(new ForegroundColorSpan(replay?VelaTheme.WHITE:0xFFFF817B),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(!quality.isEmpty()){
                int start=line.length();line.append("   ·   ").append(quality);
                line.setSpan(new ForegroundColorSpan(0xFFE1E4E8),start,line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            setText(line);setContentDescription(label+(quality.isEmpty()?"":", "+quality));
        }
        @Override protected void onMeasure(int w,int h){
            int pad=Math.round(getTextSize()*1.05f);
            if(getPaddingLeft()!=pad)setPadding(pad,0,pad,0);
            super.onMeasure(w,h);
        }
    }
    static final class Timeline extends View {
        static final float NEXT=.70f;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private android.graphics.LinearGradient tail;
        private int accent,progress;
        private boolean next;
        private float reveal=1;
        private android.animation.ValueAnimator entrance;
        Timeline(Context c){super(c);accent=VelaReading.accent(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void color(int color){
            int value=color==0?VelaReading.accent(getContext()):color;
            // Keep the playhead readable even with black, neutral or very dark station art.
            if(android.graphics.Color.luminance(value)<.24f)value=android.graphics.Color.rgb(
                    (android.graphics.Color.red(value)+255)/2,(android.graphics.Color.green(value)+255)/2,(android.graphics.Color.blue(value)+255)/2);
            if(accent!=value){accent=value;invalidate();}
        }
        void value(int elapsed,boolean hasNext){
            if(progress==elapsed&&next==hasNext)return;
            progress=Math.max(0,Math.min(1000,elapsed));next=hasNext;invalidate();
        }
        float position(){return (next?NEXT:1f)*progress/1000f;}
        void reveal(){
            if(entrance!=null)entrance.cancel();
            if(!isShown()||!android.animation.ValueAnimator.areAnimatorsEnabled()){reveal=1;invalidate();return;}
            if(entrance==null){
                entrance=android.animation.ValueAnimator.ofFloat(0,1);entrance.setDuration(400);
                entrance.setInterpolator(new android.view.animation.DecelerateInterpolator());
                entrance.addUpdateListener(a->{reveal=(float)a.getAnimatedValue();invalidate();});
            }
            entrance.start();
        }
        @Override protected void onVisibilityChanged(View v,int state){
            super.onVisibilityChanged(v,state);
            if(state!=VISIBLE&&entrance!=null){entrance.cancel();reveal=1;}
        }
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            if(w>0)tail=new android.graphics.LinearGradient(w*NEXT,0,w,0,0x66FFFFFF,0x00FFFFFF,android.graphics.Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),mid=getHeight()/2f,stroke=Math.max(1,mid*.15f),r=mid*.49f;
            float end=next?w*NEXT:w,head=Math.max(r,Math.min(w-r,w*position()));
            int save=c.save();c.clipRect(0,0,w*reveal,getHeight());
            paint.setColor(0x65FFFFFF);c.drawRect(0,mid-stroke,end,mid+stroke,paint);
            if(next){
                paint.setColor(0xFFFFFFFF);paint.setShader(tail);c.drawRect(end,mid-stroke,w,mid+stroke,paint);paint.setShader(null);
                paint.setColor(0xB0FFFFFF);c.drawRect(end-stroke,mid*.26f,end+stroke,mid+stroke,paint);
            }
            paint.setColor(0xC9F3F1EA);c.drawRect(0,mid-stroke,head,mid+stroke,paint);
            paint.setColor(accent);c.drawCircle(head,mid,r,paint);c.restoreToCount(save);
        }
    }
    static int logoColor(android.graphics.Bitmap bitmap){
        if(bitmap==null)return 0;
        float[] hsv=new float[3];float best=.12f;int chosen=0;
        for(int y=0;y<bitmap.getHeight();y+=Math.max(1,bitmap.getHeight()/8))
            for(int x=0;x<bitmap.getWidth();x+=Math.max(1,bitmap.getWidth()/8)){
                int pixel=bitmap.getPixel(x,y);if(android.graphics.Color.alpha(pixel)<160)continue;
                android.graphics.Color.colorToHSV(pixel,hsv);float score=hsv[1]*hsv[2];
                if(score>best){best=score;chosen=pixel;}
            }
        return chosen;
    }
    static String time(long seconds){return new java.text.SimpleDateFormat("HH:mm",Ui.locale()).format(new java.util.Date(seconds*1000));}
    static Drawable row(Context c,boolean focus){return VelaTheme.row(focus,VelaReading.accent(c));}
    static void tab(TextView v,boolean selected){boolean focus=v.hasFocus();v.setTextColor(focus?VelaTheme.INK:selected?VelaTheme.WHITE:VelaTheme.SECONDARY);v.setBackground(VelaTheme.shape(focus?VelaTheme.pearl(VelaReading.accent(v.getContext())):selected?VelaTheme.SELECTED:android.graphics.Color.TRANSPARENT,VelaTheme.CONTROL_RADIUS));}
}
