package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A bounded, native content sheet. Owners supply existing metadata and own every request. */
final class VelaContentDetails extends FrameLayout {
    final ImageView image;final TextView heading,metadata,description;final LinearLayout actions;private final ScrollView scroll,synopsis;private ProgressBar progressBar;private TextView eventState,eventHint;private boolean broadcast,hasArtwork;private Drawable artworkBackground;private int artworkLeft=-1;java.util.function.BooleanSupplier ready=()->true;
    VelaContentDetails(Context c,String title,String meta,String summary,float progress){super(c);setBackground(VelaTheme.panel());setPadding(34,28,34,28);image=new Artwork(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(VelaTheme.shape(VelaTheme.CARD,18));image.setClipToOutline(true);put(image,0,0,320,480);heading=text(title,34,VelaTheme.WHITE);heading.setTypeface(Ui.displaySemibold);heading.setMaxLines(2);put(heading,358,0,962,92);metadata=text(meta,20,VelaTheme.SECONDARY);metadata.setMaxLines(2);put(metadata,358,104,962,62);description=text(summary.isEmpty()?L10n.text("No description supplied by this source."):summary,22,VelaTheme.SECONDARY);description.setGravity(Gravity.TOP);description.setPadding(12,10,12,14);synopsis=new ScrollView(c);synopsis.setVerticalScrollBarEnabled(true);synopsis.setScrollBarSize(3);synopsis.setOverScrollMode(OVER_SCROLL_NEVER);synopsis.setFocusable(true);synopsis.setDefaultFocusHighlightEnabled(false);synopsis.setContentDescription(L10n.text("Synopsis"));synopsis.addView(description,new ScrollView.LayoutParams(-1,-2));synopsis.setOnFocusChangeListener((v,f)->{synopsis.setBackground(VelaTheme.shape(f?Appearance.accent(c):Color.TRANSPARENT,16));description.setTextColor(f?VelaTheme.INK:VelaTheme.SECONDARY);});put(synopsis,346,172,974,166);
        if(progress>=0){ProgressBar bar=new ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal);progressBar=bar;bar.setMax(1000);bar.setProgress((int)(Math.max(0,Math.min(1,progress))*1000));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.accent(c)));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x28FFFFFF));put(bar,358,350,962,6);}
        scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(OVER_SCROLL_NEVER);actions=new ActionLayout(c);actions.setOrientation(LinearLayout.VERTICAL);actions.setPadding(2,4,2,22);scroll.addView(actions);put(scroll,358,378,962,338);if(VelaReading.scale(c)>1.1f){heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,Math.min(40,34*VelaReading.scale(c)));move(heading,358,0,962,104);move(metadata,358,118,962,66);move(synopsis,346,188,974,150);}}
    /** Guide detail, in the same canvas: two primary controls and one quiet secondary row. */
    void broadcast(boolean artwork){
        broadcast=true;hasArtwork=artwork;artworkBackground=image.getBackground();((ActionLayout)actions).compact=true;actions.setPadding(0,0,0,0);
        move(image,0,0,264,149);move(synopsis,-12,218,1332,96);move(scroll,0,340,1320,196);
        if(eventState==null){eventState=text("",18,VelaTheme.WHITE);eventState.setPadding(12,0,16,0);eventState.setBackground(VelaTheme.shape(VelaTheme.SELECTED,12));eventState.setCompoundDrawablePadding(10);eventState.setCompoundDrawablesRelative(new ClockMark(VelaTheme.WHITE),null,null,null);eventState.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);eventState.setVisibility(INVISIBLE);addView(eventState);eventHint=text("",17,VelaTheme.SECONDARY);eventHint.setMaxLines(2);put(eventHint,0,550,1320,48);}
        positionArtwork();
        if(progressBar!=null)move(progressBar,0,326,1320,4);
    }
    private void positionArtwork(){if(!broadcast)return;boolean show=hasArtwork&&image.getDrawable()!=null;image.setVisibility(hasArtwork?VISIBLE:GONE);image.setBackground(show?artworkBackground:null);int left=show?296:0;if(left==artworkLeft)return;artworkLeft=left;move(heading,left,0,1320-left,100);move(metadata,left,110,1320-left,44);if(eventState!=null){LayoutParams state=new LayoutParams(-2,38);state.leftMargin=left;state.topMargin=166;eventState.setLayoutParams(state);}}
    private final class Artwork extends ImageView {Artwork(Context c){super(c);}@Override public void setImageDrawable(Drawable d){super.setImageDrawable(d);positionArtwork();}@Override public void setImageBitmap(Bitmap b){super.setImageBitmap(b);positionArtwork();}}
    static Drawable programmeScrim(){return new ProgrammeShade();}
    /** Edge-transparent shader: dim the guide without a rectangular seam or a blurred bitmap. */
    private static final class ProgrammeShade extends Drawable {
        private final Paint paint=new Paint();
        @Override protected void onBoundsChange(Rect b){RadialGradient shade=new RadialGradient(0,0,1,new int[]{0xB3090A0D,0xB3090A0D,0},new float[]{0,.62f,1},Shader.TileMode.CLAMP);Matrix matrix=new Matrix();matrix.setScale(Math.max(1,b.width()/2f),Math.max(1,b.height()/2f));matrix.postTranslate(b.exactCenterX(),b.exactCenterY());shade.setLocalMatrix(matrix);paint.setShader(shade);}
        public void draw(Canvas c){c.drawRect(getBounds(),paint);}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    void listActions(){((ActionLayout)actions).compact=false;actions.setPadding(2,4,2,22);scroll.setVerticalScrollBarEnabled(true);}
    void notice(String value){if(eventState!=null){eventState.setText(value);eventState.setVisibility(value.isEmpty()?INVISIBLE:VISIBLE);}}
    void hint(String value){if(eventHint!=null)eventHint.setText(value);}
    void updateAction(TextView view,String label){if(view instanceof Action){Action a=(Action)view;a.title=label;a.paintFocus(a.hasFocus());}}
    void clockAction(TextView view){if(view instanceof Action){((Action)view).clock=true;((Action)view).paintFocus(view.hasFocus());}}
    @Override public View focusSearch(View focused,int direction){View next=FocusFinder.getInstance().findNextFocus(this,focused,direction);return next==null?focused:next;}
    private void move(View v,int x,int y,int w,int h){LayoutParams lp=new LayoutParams(w,h);lp.leftMargin=x;lp.topMargin=y;v.setLayoutParams(lp);}
    private TextView text(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*VelaReading.scale(getContext()));v.setTextColor(color);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void put(View v,int x,int y,int w,int h){LayoutParams p=new LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;addView(v,p);}
    TextView action(String title,String detail,Runnable click){Action v=new Action(getContext(),title,detail,actions.getChildCount());v.setOnClickListener(w->{if(ready.getAsBoolean())click.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=4;actions.addView(v,lp);return v;}
    private final class Action extends TextView {
        String title,detail;boolean clock;final boolean primary;
        Action(Context c,String title,String detail,int index){super(c);this.title=title;this.detail=detail;primary=index<2;setTypeface(Ui.medium);setIncludeFontPadding(false);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(broadcast&&!primary?20:22)*VelaReading.scale(c));setPadding(20,broadcast?12:15,20,broadcast?12:15);setMinHeight(broadcast?(primary?72:62):72);setGravity(broadcast?Gravity.CENTER:Gravity.CENTER_VERTICAL);if(broadcast){setMaxLines(2);setEllipsize(TextUtils.TruncateAt.END);}setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);setId(View.generateViewId());setOnFocusChangeListener((v,f)->paintFocus(f));paintFocus(false);}
        void paintFocus(boolean focused){if(broadcast){setText(title);setTextColor(focused?VelaTheme.INK:VelaTheme.WHITE);setBackground(focused?VelaTheme.shape(Appearance.accent(getContext()),18):primary?VelaTheme.shape(VelaTheme.RAISED,18):VelaTheme.row(false,Appearance.accent(getContext())));if(clock){setCompoundDrawablePadding(12);setCompoundDrawablesRelative(new ClockMark(focused?VelaTheme.INK:VelaTheme.WHITE),null,null,null);}}else{VelaTheme.rowText(this,title,detail,focused);setBackground(VelaTheme.row(focused,Appearance.accent(getContext())));}setContentDescription(title);}
    }
    private static final class ClockMark extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int color;
        ClockMark(int color){this.color=color;setBounds(0,0,22,22);}
        public void draw(Canvas c){paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setStrokeCap(Paint.Cap.ROUND);Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/22f,b.height()/22f);c.drawCircle(11,11,8,paint);c.drawLine(11,6.5f,11,11,paint);c.drawLine(11,11,14.5f,13,paint);c.restore();}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private final class ActionLayout extends LinearLayout {
        boolean compact;private int firstHeight,secondHeight;
        ActionLayout(Context c){super(c);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){if(!compact){super.onMeasure(widthSpec,heightSpec);return;}int width=MeasureSpec.getSize(widthSpec),count=getChildCount(),first=Math.min(2,count),second=Math.max(0,count-first);firstHeight=secondHeight=0;for(int i=0;i<count;i++){int columns=i<first?first:second;View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec((width-12*(columns-1))/columns,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));if(i<first)firstHeight=Math.max(firstHeight,child.getMeasuredHeight());else secondHeight=Math.max(secondHeight,child.getMeasuredHeight());}setMeasuredDimension(width,firstHeight+(second>0?12+secondHeight:0));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){if(!compact){super.onLayout(changed,l,t,r,b);return;}int count=getChildCount(),first=Math.min(2,count),second=Math.max(0,count-first);for(int i=0;i<count;i++){boolean top=i<first;int columns=top?first:second,column=top?i:i-first,width=(getWidth()-12*(columns-1))/columns,x=column*(width+12),y=top?0:firstHeight+12;getChildAt(i).layout(x,y,x+width,y+(top?firstHeight:secondHeight));}}
    }
}
