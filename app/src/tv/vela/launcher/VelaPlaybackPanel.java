package tv.vela.launcher;

import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.media.tv.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Disposable native control centre above the one existing decoder. */
final class VelaPlaybackPanel extends FrameLayout {
    static final class Item {final String title,detail;final Runnable action;Item(String t,String d,Runnable a){title=t;detail=d;action=a;}}
    interface Model {List<Item> items();}
    private final Context context;private final Model model;private final Runnable exit;private final LinearLayout list;private final List<View> buttons=new ArrayList<>();private boolean closed;
    VelaPlaybackPanel(Context c,String channel,Model m,Runnable close){this(c,channel,m,close,L10n.text("Playback controls"),L10n.text("Back · Return to programme"));}
    VelaPlaybackPanel(Context c,String channel,Model m,Runnable close,String heading,String footer){super(c);context=c;model=m;exit=close;setBackgroundColor(0x60000000);VelaReading.Stage stage=new VelaReading.Stage(c,1920,1080);addView(stage,new LayoutParams(-1,-1));FrameLayout canvas=new FrameLayout(c);stage.addView(canvas);FrameLayout panel=new FrameLayout(c);panel.setBackground(VelaTheme.panel());put(canvas,panel,1132,36,742,1008);TextView title=text(heading,34),subtitle=text(channel,22);put(panel,title,34,24,674,56);subtitle.setTextColor(VelaTheme.SECONDARY);put(panel,subtitle,34,90,674,52);ScrollView scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(false);list=new LinearLayout(c);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,0,0,20);scroll.addView(list,new ScrollView.LayoutParams(-1,-2));put(panel,scroll,30,168,682,748);TextView hint=text(footer,20);hint.setTextColor(VelaTheme.MUTED);put(panel,hint,34,938,674,40);refresh(0);}
    private TextView text(String s,int size){TextView v=new TextView(context);v.setText(s);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*VelaReading.scale(context));v.setTextColor(VelaTheme.WHITE);v.setTypeface(Ui.medium);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    private static GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(VelaTheme.PANEL_RADIUS);return d;}
    private void put(FrameLayout p,View v,int x,int y,int w,int h){LayoutParams lp=new LayoutParams(w,h);lp.leftMargin=x;lp.topMargin=y;p.addView(v,lp);}
    private void refresh(int selected){
        List<Item> items=model.items();buttons.clear();
        for(int i=0;i<items.size();i++){
            Row row;if(i<list.getChildCount())row=(Row)list.getChildAt(i);else{row=new Row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=4;list.addView(row,lp);}
            row.bind(items.get(i));if(row.item.action!=null)buttons.add(row);
        }
        while(list.getChildCount()>items.size())list.removeViewAt(list.getChildCount()-1);
        if(!buttons.isEmpty())buttons.get(Math.max(0,Math.min(selected,buttons.size()-1))).requestFocus();
    }
    private final class Row extends TextView {
        Item item;
        Row(){super(context);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,22*VelaReading.scale(context));setTextColor(VelaTheme.WHITE);setTypeface(Ui.medium);setGravity(Gravity.CENTER_VERTICAL);setIncludeFontPadding(false);setPadding(22,18,22,18);setMinHeight((int)(78*VelaReading.scale(context)));setDefaultFocusHighlightEnabled(false);setOnFocusChangeListener((v,f)->style());setOnClickListener(v->{Item chosen=item;if(chosen==null||chosen.action==null)return;int at=buttons.indexOf(this);try{chosen.action.run();}catch(RuntimeException unavailable){Toast.makeText(context,L10n.text("This option is unavailable for the current signal."),Toast.LENGTH_SHORT).show();}if(!closed)refresh(at);});}
        void bind(Item value){boolean changed=item==null||!item.title.equals(value.title)||!item.detail.equals(value.detail);item=value;setFocusable(value.action!=null);setFocusableInTouchMode(value.action!=null);setClickable(value.action!=null);if(changed)style();}
        private void style(){if(item==null)return;VelaTheme.rowText(this,item.title,item.detail,hasFocus());setBackground(VelaTheme.row(hasFocus(),VelaReading.accent(context)));}
    }
    boolean remote(KeyEvent e){int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_MENU){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)close();return true;}if(k==KeyEvent.KEYCODE_BACK||k==KeyEvent.KEYCODE_DPAD_LEFT){if(e.getAction()==KeyEvent.ACTION_UP)close();return true;}if(k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_DPAD_UP){if(e.getAction()==KeyEvent.ACTION_DOWN&&!buttons.isEmpty()){int at=buttons.indexOf(findFocus());buttons.get(Math.max(0,Math.min(buttons.size()-1,at+(k==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)))).requestFocus();}return true;}return false;}
    void close(){if(closed)return;closed=true;buttons.clear();list.removeAllViews();if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);exit.run();}
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-playback");}
    static String key(String name,String field){return VelaGuideFix.key(name)+"-"+field;}
    static void tvItems(Context c,TvView tv,String channel,List<Item> out){if(tv==null)return;for(int type:new int[]{TvTrackInfo.TYPE_AUDIO,TvTrackInfo.TYPE_SUBTITLE}){List<TvTrackInfo> tracks=tv.getTracks(type);String label=type==TvTrackInfo.TYPE_AUDIO?L10n.text("Audio"):L10n.text("Subtitles");if(type==TvTrackInfo.TYPE_SUBTITLE)out.add(new Item(L10n.text("Turn subtitles off"),"",()->{tv.setCaptionEnabled(false);tv.selectTrack(type,null);prefs(c).edit().putString(key(channel,"sub"),"off").apply();}));if(tracks!=null)for(TvTrackInfo track:tracks){String lang=track.getLanguage()==null?"":track.getLanguage();String title=track.getDescription()==null?lang:track.getDescription().toString();if(title.isEmpty())title=label+" "+(tracks.indexOf(track)+1);out.add(new Item((track.getId().equals(tv.getSelectedTrack(type))?"✓ ":"")+label+" · "+title,"",()->{if(type==TvTrackInfo.TYPE_SUBTITLE)tv.setCaptionEnabled(true);tv.selectTrack(type,track.getId());prefs(c).edit().putString(key(channel,type==TvTrackInfo.TYPE_AUDIO?"audio":"sub"),track.getId()).apply();}));}}}
    static void restoreTv(Context c,TvView tv,String channel){SharedPreferences saved=prefs(c);String prefix=VelaGuideFix.key(channel)+"-";for(int type:new int[]{TvTrackInfo.TYPE_AUDIO,TvTrackInfo.TYPE_SUBTITLE}){String value=saved.getString(prefix+(type==TvTrackInfo.TYPE_AUDIO?"audio":"sub"),"");if(value.isEmpty())continue;String selected=tv.getSelectedTrack(type);if(type==TvTrackInfo.TYPE_SUBTITLE&&value.equals("off")){tv.setCaptionEnabled(false);if(selected!=null)tv.selectTrack(type,null);continue;}if(value.equals(selected)){if(type==TvTrackInfo.TYPE_SUBTITLE)tv.setCaptionEnabled(true);continue;}List<TvTrackInfo> tracks=tv.getTracks(type);if(tracks!=null)for(TvTrackInfo track:tracks)if(track.getId().equals(value)){if(type==TvTrackInfo.TYPE_SUBTITLE)tv.setCaptionEnabled(true);tv.selectTrack(type,value);break;}}}
}
