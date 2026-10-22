package tv.vela.launcher;
import android.content.Context;import android.graphics.*;import android.widget.FrameLayout;

/** A small drawn gradient, without blur, shadows or off-screen bitmap layers. */
final class TvLogoTile extends FrameLayout {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private int color=0xFF243449;private boolean focused;
    TvLogoTile(Context c){super(c);setWillNotDraw(false);}
    void color(int value){color=value;shade();}
    void focused(boolean value){focused=value;shade();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){shade();}
    private void shade(){float[] hsv=new float[3];Color.colorToHSV(color,hsv);hsv[1]=Math.min(.65f,hsv[1]);hsv[2]=focused?.35f:.24f;int top=Color.HSVToColor(hsv);hsv[2]=focused?.22f:.14f;paint.setShader(new LinearGradient(0,0,getWidth(),getHeight(),top,Color.HSVToColor(hsv),Shader.TileMode.CLAMP));invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(1,1,getWidth()-1,getHeight()-1,29,29,paint);if(focused){Shader shader=paint.getShader();paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0x88DCE8FA);c.drawRoundRect(1,1,getWidth()-1,getHeight()-1,29,29,paint);paint.setShader(shader);}}
}
