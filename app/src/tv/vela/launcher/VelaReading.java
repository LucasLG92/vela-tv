package tv.vela.launcher;

import android.content.*;
import android.graphics.*;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.*;
import android.widget.TextView;
import java.util.*;

/** Reading preferences change layout density; no animation, image or background task. */
final class VelaReading {
    private static final WeakHashMap<TextView,Float> BASE=new WeakHashMap<>();
    private static final WeakHashMap<TextView,Float> APPLIED=new WeakHashMap<>();
    private static final ColorStateList READABLE_TEXT=new ColorStateList(new int[][]{new int[0]},new int[]{0xFFE0E1E8});
    private static final WeakHashMap<TextView,ColorStateList> originalColors=new WeakHashMap<>();
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-reading");}
    static int choice(Context c,String key,int max){int n=prefs(c).getInt(key,0);return Math.max(0,Math.min(max,n));}
    private static final float[] TEXT_SCALE={1f,1.15f,1.3f};
    static float scale(Context c){return Math.max(TEXT_SCALE[choice(c,"text",2)],Math.min(1.3f,c.getResources().getConfiguration().fontScale));}
    static int rows(Context c){
        int size=VelaEdition.lite(c)?VelaLayout.size(c,"tv",1):1;
        int density=size==1?choice(c,"density",2):size==0?0:2;
        float text=scale(c);
        return 6-Math.max(density,text>=1.25f?2:text>=1.1f?1:0);
    }
    static float margin(Context c){return choice(c,"margin",3)*.015f;}
    static int accent(Context c){return Appearance.accent(c);}
    static void cycle(Context c,String key,int count){SharedPreferences p=prefs(c);p.edit().putInt(key,(choice(c,key,count-1)+1)%count).commit();VelaWorkspace.registry(c).edit().putLong("revision",System.nanoTime()).commit();}
    static void textSize(TextView v,float base){
        float size=base*scale(v.getContext());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        BASE.put(v,base);
        APPLIED.put(v,size);
    }
    private static void textContrast(TextView v,boolean enabled){
        ColorStateList colors=v.getTextColors();
        if(colors==READABLE_TEXT){
            if(!enabled){
                ColorStateList original=originalColors.remove(v);
                if(original!=null)v.setTextColor(original);
            }
            return;
        }
        // The view may have rebound its label or changed focus since the previous pass.
        originalColors.remove(v);
        if(!enabled||colors.isStateful())return;
        int color=colors.getDefaultColor(),r=Color.red(color),g=Color.green(color),b=Color.blue(color);
        int light=(r+g+b)/3;
        if(Color.alpha(color)==255&&light>100&&light<200&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<40){
            originalColors.put(v,colors);
            v.setTextColor(READABLE_TEXT);
        }
    }
    private static void fitText(TextView v,float scale,boolean contrast){
        Float base=BASE.get(v),last=APPLIED.get(v);if(base==null||last!=null&&Math.abs(last-v.getTextSize())>.1f){base=v.getTextSize();BASE.put(v,base);}float size=base*scale;
        int h=v.getHeight();if(h<=0&&v.getLayoutParams()!=null&&v.getLayoutParams().height>0)h=v.getLayoutParams().height;
        if(h>0){int lines=Math.max(1,Math.min(v.getMaxLines(),(int)(h/(base*1.25f))));size=Math.min(size,Math.max(base,(h-v.getPaddingTop()-v.getPaddingBottom())/(1.25f*lines)));}
        APPLIED.put(v,size);if(Math.abs(v.getTextSize()-size)>.1f)v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        textContrast(v,contrast);
    }
    static void apply(View root){walk(root,scale(root.getContext()),prefs(root.getContext()).getBoolean("contrast",false));}
    private static void walk(View root,float scale,boolean contrast){if(root instanceof VelaHub||root instanceof VelaContentDetails)return;contrast=contrast&&!root.isFocused();if(root instanceof TextView)fitText((TextView)root,scale,contrast);if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)walk(group.getChildAt(i),scale,contrast);}}
    static final class Stage extends android.widget.FrameLayout {
        private final int width,height;private final boolean safe;
        Stage(Context c,int width,int height){this(c,width,height,true);}
        Stage(Context c,int width,int height,boolean safe){super(c);this.width=width;this.height=height;this.safe=safe;setClipChildren(true);}
        protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));if(getChildCount()>0)getChildAt(0).measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));}
        protected void onLayout(boolean change,int l,int t,int r,int b){if(getChildCount()==0)return;View child=getChildAt(0);float pad=safe?margin(getContext()):0,s=Math.min(getWidth()*(1-2*pad)/width,getHeight()*(1-2*pad)/height);child.setPivotX(0);child.setPivotY(0);child.setScaleX(s);child.setScaleY(s);int x=(int)((getWidth()-width*s)/2),y=(int)((getHeight()-height*s)/2);child.layout(x,y,x+width,y+height);}
    }
}
