package tv.vela.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Shared TV navigation. Only the capsule outline and label opacity animate. */
final class VelaNavigation {
    static final int LEFT=31, TOP=72, HEIGHT=936, RAIL=78, DRAWER=322, RADIUS=39;
    static final int INSET=9, ROW=60, OPEN_ROW=DRAWER-2*INSET, LABEL=238;

    private VelaNavigation(){}

    static final class Capsule extends FrameLayout {
        private final GradientDrawable skin=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2B3030,0xFF202526,0xFF1B1F20});
        private final ValueAnimator slide=new ValueAnimator();
        private int edge=RAIL;
        private boolean expanded;

        Capsule(Context context){
            super(context);
            setTag("vela-sidebar");
            skin.setCornerRadius(RADIUS);
            skin.setStroke(1,0x24EFF2F0);
            setWillNotDraw(false);
            setOutlineProvider(new ViewOutlineProvider(){
                @Override public void getOutline(View view,Outline outline){
                    outline.setRoundRect(0,0,edge,view.getHeight(),RADIUS);
                }
            });
            setClipToOutline(true);
            setElevation(10);
            slide.setInterpolator(new DecelerateInterpolator(1.5f));
            slide.addUpdateListener(animation->edge((Integer)animation.getAnimatedValue()));
            slide.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator animation){
                    if(!expanded&&edge==RAIL)layoutWidth(RAIL);
                }
            });
        }

        void expand(boolean open){
            expanded=open;
            slide.cancel();
            int target=open?DRAWER:RAIL;
            if(open)layoutWidth(DRAWER);
            if(edge==target||!isLaidOut()||VelaFocus.reduced(getContext())){
                edge(target);layoutWidth(target);return;
            }
            slide.setIntValues(edge,target);
            slide.setDuration(open?180:150);
            slide.start();
        }
        private void layoutWidth(int width){
            ViewGroup.LayoutParams p=getLayoutParams();
            if(p!=null&&p.width!=width){p.width=width;setLayoutParams(p);}
        }
        private void edge(int next){edge=next;invalidateOutline();invalidate();}
        @Override protected void onDraw(Canvas canvas){
            skin.setBounds(0,0,edge,getHeight());skin.draw(canvas);
        }
        @Override protected void onDetachedFromWindow(){
            slide.cancel();int width=expanded?DRAWER:RAIL;edge(width);layoutWidth(width);
            super.onDetachedFromWindow();
        }
    }
    static GradientDrawable shade(){
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFA080B0C,0xF2080B0C,0xAC080B0C,0x48080B0C,0x00080B0C});
    }
    static void width(View row,boolean expanded){
        ViewGroup.LayoutParams p=row.getLayoutParams();
        int width=expanded?OPEN_ROW:ROW;
        if(p.width!=width){p.width=width;row.setLayoutParams(p);}
    }
    static void reveal(View view,boolean visible){
        view.animate().cancel();
        if(VelaFocus.reduced(view.getContext()))view.setAlpha(visible?1f:0f);
        else view.animate().alpha(visible?1f:0f).setDuration(visible?130:90).start();
    }
    static int foreground(boolean focused,boolean active){return focused?0xFF1D2425:active?0xFFF3F4EF:0xFFC0C4C2;}
    static void style(View row,boolean focused,boolean active,boolean expanded){
        Item item=row.getBackground() instanceof Item?(Item)row.getBackground():new Item();
        if(row.getBackground()!=item)row.setBackground(item);
        item.update(focused,active,expanded,Appearance.accent(row.getContext()));
    }
    private static final class Item extends GradientDrawable {
        private boolean focused,active,expanded;
        private int accent;
        void update(boolean focus,boolean selected,boolean open,int color){
            if(focused==focus&&active==selected&&expanded==open&&color==accent)return;
            focused=focus;active=selected;expanded=open;accent=color;
            setCornerRadius(26);
            if(focus){
                int pearl=color==0xFFF7F7FA?0xFFE2E7E2:color;
                int shade=Color.rgb(Color.red(pearl)*90/100,Color.green(pearl)*91/100,Color.blue(pearl)*91/100);
                setOrientation(Orientation.LEFT_RIGHT);setColors(new int[]{pearl,shade});
            }else setColor(selected&&!open?0x20D5DFDB:Color.TRANSPARENT);
        }
    }
}
