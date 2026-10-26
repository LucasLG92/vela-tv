package tv.vela.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/** Shared, passive now/next chrome. No requests, bitmaps, decoder, animation or timer. */
final class UpNextView extends FrameLayout {
    private final TextView label,title,time;private final android.graphics.Paint divider=new android.graphics.Paint();
    private final SimpleDateFormat clock=new SimpleDateFormat("HH:mm",Ui.locale());
    UpNextView(Context context){
        super(context);Ui.init(context);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        GradientDrawable background=new GradientDrawable();background.setColor(android.graphics.Color.TRANSPARENT);setBackground(background);setWillNotDraw(false);
        label=text(Ui.tr("A SEGUIR","UP NEXT","A CONTINUACIÓN"),18,VelaTheme.SECONDARY);label.setLetterSpacing(.08f);
        title=text("",28,VelaTheme.WHITE);title.setTypeface(Ui.displaySemibold);title.setSingleLine(false);title.setMaxLines(2);title.setGravity(android.view.Gravity.TOP);
        time=text("",20,VelaTheme.SECONDARY);setVisibility(GONE);
    }
    private TextView text(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(v);return v;}
    boolean bind(String value,long start,long end,long now){
        boolean available=value!=null&&!value.trim().isEmpty()&&start>now&&end>start;
        if(!available){title.setText("");time.setText("");setContentDescription(null);setVisibility(GONE);return false;}
        clock.setTimeZone(TimeZone.getDefault());String timing=clock.format(new Date(start*1000))+"–"+clock.format(new Date(end*1000));title.setText(value);time.setText(timing);setContentDescription(label.getText()+" · "+value+" · "+timing);setVisibility(VISIBLE);return true;
    }
    @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float s=Math.min(getWidth()/620f,getHeight()/194f);divider.setColor(0x30FFFFFF);c.drawRect(0,18*s,Math.max(1,s),getHeight()-18*s,divider);}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){float scale=Math.min(width/620f,height/194f);((GradientDrawable)getBackground()).setCornerRadius(22*scale);place(label,24,18,572,28,18,scale);place(title,24,54,572,76,28,scale);place(time,24,146,572,30,20,scale);}
    private void place(TextView view,int x,int y,int w,int h,int size,float scale){LayoutParams p=new LayoutParams(Math.round(w*scale),Math.round(h*scale));p.leftMargin=Math.round(x*scale);p.topMargin=Math.round(y*scale);view.setLayoutParams(p);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*scale);}
}
