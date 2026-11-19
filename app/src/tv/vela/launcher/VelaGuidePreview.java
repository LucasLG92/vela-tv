package tv.vela.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A small native illustration of the two guide layouts in the view picker. */
final class VelaGuidePreview extends View {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean guide;
    private final int accent;
    VelaGuidePreview(Context context,boolean guide,int accent){
        super(context);
        this.guide=guide;
        this.accent=accent;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void block(Canvas c,float x,float y,float w,float h,int color){
        ink.setColor(color);
        c.drawRoundRect(x,y,x+w,y+h,3,3,ink);
    }
    @Override protected void onDraw(Canvas c){
        int saved=c.save();c.scale(getWidth()/256f,getHeight()/108f);
        ink.setColor(0xff14191e);c.drawRoundRect(0,0,256,108,10,10,ink);
        if(guide){
            for(int i=0;i<3;i++){
                int y=18+26*i,split=60+22*((i+1)%3);
                block(c,12,y,41,19,0xff566471);
                block(c,58,y,split,19,i==0?accent:0xff455260);
                block(c,62+split,y,181-split,19,0xff303b47);
            }
            block(c,82,12,2,82,accent);
        }else{
            for(int i=0;i<4;i++){
                int x=13+59*i;
                block(c,x,19,52,55,i==0?0xff566471:0xff303b47);
                ink.setColor(i==0?accent:0xff87939e);c.drawCircle(x+26,47,11,ink);
                block(c,x+6,81,40,4,0xff667480);
            }
        }
        c.restoreToCount(saved);
    }
}
