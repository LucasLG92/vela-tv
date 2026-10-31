package tv.vela.launcher;

import android.content.*;
import android.graphics.*;
import android.view.View;

/** Profile-local presets. Preview is a drawing, never a temporary preference mutation. */
final class VelaStyle {
    static final int CINEMA=0,COMPACT=1,MINIMAL=2;
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"visuals");}
    static int selected(Context c){int v=prefs(c).getInt("appearance_style",-1);return v>=0&&v<=2?v:-1;}
    static String name(int id){return L10n.text(id==CINEMA?"Cinema":id==COMPACT?"Compact":id==MINIMAL?"Minimal":"Custom");}
    static int value(Context c,String key,int fallback){int style=selected(c);if(style<0)return fallback;if(key.equals("size"))return style==CINEMA?2:style==COMPACT?0:1;if(key.equals("backdrop"))return style==CINEMA?2:style==COMPACT?1:0;return 0;}
    static boolean minimal(Context c){return selected(c)==MINIMAL;}
    static void apply(Context c,int style){
        if(style<0||style>2)throw new IllegalArgumentException();
        org.json.JSONObject before=VelaLayout.read(c);
        try{org.json.JSONObject layout=new org.json.JSONObject(before.toString());layout.remove("sizes");VelaLayout.save(c,layout);}
        catch(Exception failure){throw new IllegalStateException("Could not save layout",failure);}
        if(!prefs(c).edit().putInt("appearance_style",style).remove("appearance_color").remove("appearance_size").remove("appearance_backdrop").commit()){
            try{VelaLayout.save(c,before);}catch(Exception unavailable){}
            throw new IllegalStateException("Could not save style");
        }
        if(VelaEdition.lite(c))VelaWorkspace.prefs(c,"lite_home").edit().remove("black_background").apply();
        VelaWorkspace.registry(c).edit().putLong("revision",System.nanoTime()).apply();
    }
    static final class Preview extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF box=new RectF();private final int style;
        Preview(Context c,int style){super(c);this.style=style;setContentDescription(L10n.text("Style preview")+" · "+name(style));}
        private void rect(Canvas c,float x,float y,float w,float h,int color,float radius){p.setColor(color);box.set(x,y,x+w,y+h);c.drawRoundRect(box,radius,radius,p);}
        protected void onDraw(Canvas raw){super.onDraw(raw);raw.save();raw.scale(getWidth()/1100f,getHeight()/250f);rect(raw,0,0,1100,250,VelaTheme.BACKGROUND,20);rect(raw,14,14,42,222,VelaTheme.CARD,18);for(int i=0;i<5;i++)rect(raw,29,40+i*35,12,12,i==0?VelaTheme.WHITE:VelaTheme.MUTED,4);
            if(style!=MINIMAL){p.setShader(new LinearGradient(680,0,1100,250,new int[]{0xFF273348,0xFF15181F},null,Shader.TileMode.CLAMP));raw.drawRect(650,0,1100,250,p);p.setShader(null);}p.setTypeface(Ui.displaySemibold);p.setTextSize(25);p.setColor(VelaTheme.WHITE);raw.drawText("Vela",84,51,p);p.setTypeface(Ui.medium);p.setTextSize(18);p.setColor(VelaTheme.SECONDARY);raw.drawText(name(style),84,80,p);rect(raw,84,96,118,29,VelaTheme.WHITE,12);
            int width=style==CINEMA?95:style==COMPACT?56:74,height=style==CINEMA?103:style==COMPACT?73:88;for(int i=0;i<(style==COMPACT?12:9);i++)rect(raw,84+i*(width+15),234-height,width,height,i==0?VelaTheme.RAISED:VelaTheme.CARD,10);raw.restore();}
    }
}
