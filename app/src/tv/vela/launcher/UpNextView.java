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
    private final TextView label,title;
    private final SimpleDateFormat clock=new SimpleDateFormat("HH:mm",Ui.locale());
    UpNextView(Context context){
        super(context);Ui.init(context);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        label=text("",24,VelaTheme.SECONDARY);
        title=text("",35,VelaTheme.WHITE);title.setTypeface(Ui.medium);
        VelaPlayerUi.legible(title);VelaPlayerUi.legible(label);setVisibility(GONE);
    }
    private TextView text(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(v);return v;}
    boolean bind(String value,long start,long end,long now){
        boolean available=value!=null&&!value.trim().isEmpty()&&start>now&&end>start;
        if(!available){title.setText("");label.setText("");setContentDescription(null);setVisibility(GONE);return false;}
        clock.setTimeZone(TimeZone.getDefault());
        String begins=clock.format(new Date(start*1000)),ends=clock.format(new Date(end*1000));
        label.setText(L10n.text("Next")+" · "+begins);title.setText(value);
        setContentDescription(label.getText()+" · "+value+" · "+begins+"–"+ends);setVisibility(VISIBLE);return true;
    }
    @Override protected void onMeasure(int w,int h){
        float scale=MeasureSpec.getSize(h)/93f;
        place(label,0,3,512,34,24,scale);place(title,0,38,512,50,35,scale);super.onMeasure(w,h);
    }
    private void place(TextView view,int x,int y,int w,int h,int size,float s){
        LayoutParams p=(LayoutParams)view.getLayoutParams();p.width=Math.round(w*s);p.height=Math.round(h*s);
        p.leftMargin=Math.round(x*s);p.topMargin=Math.round(y*s);
        if(Math.abs(view.getTextSize()-size*s)>.1f)view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*s);
    }
}
