package tv.vela.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Two live presentations sharing one selected port and at most one TV input session. */
final class VelaHdmiBrowser extends FrameLayout {
    interface Actions {
        void selected(TvInputs.Entry entry);
        void open(TvInputs.Entry entry);
        void identify(TvInputs.Entry entry);
    }
    final List<View> rows=new ArrayList<>();
    final View initial;
    private final FrameLayout backdropHost,preview;
    private final FrameLayout placeholder;
    private final TextView state,headline,port;
    private final ImageView device;
    private final VelaSpotlight light;
    private final DeviceIcons icons;
    private final boolean cinema;
    private final Actions actions;
    private TvInputs.Entry selected;
    private View selectedRow;
    private TvView video;
    private boolean disposed;
    private final Runnable start=this::tune;
    private boolean waitingForVideo;
    private final Runnable timeout=this::unavailable;
    private void awaitVideo(){
        if(!waitingForVideo){waitingForVideo=true;postDelayed(timeout,5000);}
    }
    private void cancelTimeout(){waitingForVideo=false;removeCallbacks(timeout);}

    VelaHdmiBrowser(Context c,TvInputs.Layout style,List<TvInputs.Entry> entries,String preferred,
                    DeviceIcons icons,FrameLayout backdropHost,Actions actions){
        super(c);
        this.icons=icons;this.actions=actions;
        cinema=style==TvInputs.Layout.CINEMA;
        this.backdropHost=cinema?backdropHost:null;
        preview=new FrameLayout(c);
        preview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(this.backdropHost!=null)this.backdropHost.addView(preview,0,new LayoutParams(-1,-1));
        else if(cinema)put(this,preview,0,0,1790,940);
        else {
            preview.setBackground(VelaTheme.shape(VelaTheme.CARD,26));preview.setClipToOutline(true);
            put(this,preview,592,90,1104,621);
        }
        placeholder=new FrameLayout(c);placeholder.setBackground(VelaTheme.canvas());
        preview.addView(placeholder,new LayoutParams(-1,-1));
        light=new VelaSpotlight(c);placeholder.addView(light,new LayoutParams(-1,-1));
        device=new DeviceIcons.Artwork(c,"other");
        LayoutParams image=new LayoutParams(cinema?296:152,cinema?216:124,Gravity.CENTER);
        if(cinema)image.leftMargin=180;
        placeholder.addView(device,image);
        state=text("",21,VelaTheme.SECONDARY);state.setGravity(Gravity.CENTER);
        LayoutParams status=new LayoutParams(-1,52,Gravity.CENTER);
        state.setTranslationY(cinema?148:108);if(cinema)state.setTranslationX(180);
        placeholder.addView(state,status);
        if(cinema){
            shade(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xe60b0e10,0x880b0e10,0x000b0e10});
            shade(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xf00b0e10,0x200b0e10,0x000b0e10});
        }
        headline=text("",cinema?74:31,VelaTheme.WHITE);headline.setTypeface(Ui.displayMedium);
        port=text("",23,VelaTheme.SECONDARY);
        if(cinema){put(this,port,78,399,1070,45);put(this,headline,74,450,1220,110);}
        else {put(this,headline,592,717,690,55);put(this,port,1284,723,412,45);port.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);}
        LinearLayout list=new LinearLayout(c);list.setOrientation(cinema?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        if(cinema){
            HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);scroll.setFocusable(false);scroll.addView(list);
            put(this,scroll,68,613,1630,120);
        }else{
            ScrollView scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(false);scroll.setFocusable(false);scroll.addView(list);
            put(this,scroll,68,92,474,603);
        }
        int choice=0;boolean preferredFound=false;
        for(int i=0;i<entries.size()&&i<32;i++){
            TvInputs.Entry entry=entries.get(i);
            if(entry.key.equals(preferred)){choice=i;preferredFound=true;}
            else if(!preferredFound&&entries.get(choice).state!=TvInputs.CONNECTED&&entry.state==TvInputs.CONNECTED)choice=i;
            FrameLayout row=new FrameLayout(c);row.setId(View.generateViewId());row.setFocusable(true);row.setFocusableInTouchMode(true);row.setDefaultFocusHighlightEnabled(false);
            LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(cinema?268:460,cinema?93:106);
            if(cinema)bounds.rightMargin=14;else bounds.bottomMargin=10;
            list.addView(row,bounds);rows.add(row);
            ImageView glyph=new DeviceIcons.Artwork(c,entry.model);icons.load(entry.model,glyph);
            glyph.setPadding(7,7,7,7);glyph.setBackground(icons.surface(entry.model,false,10));
            put(row,glyph,18,cinema?20:25,52,52);
            TextView label=text(cinema?entry.port:entry.title,cinema?25:31,VelaTheme.WHITE);
            TextView detail=text(entry.port.equals(entry.title)||cinema?TvInputs.status(entry.state):entry.port+" · "+TvInputs.status(entry.state),19,VelaTheme.SECONDARY);
            put(row,label,86,9,cinema?168:352,48);put(row,detail,86,54,cinema?168:352,33);
            row.setContentDescription(entry.title+", "+entry.port+", "+TvInputs.status(entry.state));
            row.setOnFocusChangeListener((v,focus)->{
                row.setBackground(focus?VelaTheme.shape(VelaTheme.pearl(VelaReading.accent(c)),18):null);
                label.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);detail.setTextColor(focus?0xff414848:VelaTheme.SECONDARY);
                if(focus){select(entry,row);actions.selected(entry);queue();}
                else if(selectedRow==row)stopVideo();
            });
            row.setOnClickListener(v->{stopVideo();actions.open(entry);});
            row.setOnLongClickListener(v->{stopVideo();actions.identify(entry);return true;});
        }
        initial=rows.get(choice);select(entries.get(choice),initial);
    }
    void connectLayoutButton(View button){
        button.setId(View.generateViewId());
        button.setNextFocusDownId(initial.getId());
        if(cinema){for(View row:rows)row.setNextFocusUpId(button.getId());}
        else rows.get(0).setNextFocusUpId(button.getId());
    }
    private void shade(GradientDrawable.Orientation direction,int[] colors){
        View scrim=new View(getContext());scrim.setBackground(new GradientDrawable(direction,colors));preview.addView(scrim,preview.indexOfChild(placeholder),new LayoutParams(-1,-1));
    }
    private void select(TvInputs.Entry entry,View row){
        stopVideo();selected=entry;selectedRow=row;
        headline.setText(entry.title);port.setText(entry.port+" · "+TvInputs.status(entry.state));
        light.tint(icons.lightColor(entry.model));icons.load(entry.model,device,true);
        state.setText(TvInputs.status(entry.state));
    }
    private boolean canPreview(){
        return !disposed&&selected!=null&&selected.state==TvInputs.CONNECTED&&selectedRow.hasFocus()
                &&isAttachedToWindow()&&isShown()&&hasWindowFocus();
    }
    private void queue(){
        removeCallbacks(start);
        if(video==null&&canPreview())postDelayed(start,550);
    }
    private void tune(){
        if(!canPreview())return;
        final String id=selected.inputId;
        try{
            TvView session=new TvView(getContext());video=session;
            session.setFocusable(false);session.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            preview.addView(session,0,new LayoutParams(-1,-1));
            session.setCallback(new TvView.TvInputCallback(){
                private boolean current(String input){return video==session&&id.equals(input);}
                @Override public void onVideoAvailable(String input){if(current(input)){cancelTimeout();placeholder.setVisibility(INVISIBLE);}}
                @Override public void onVideoUnavailable(String input,int reason){
                    if(!current(input))return;
                    placeholder.setVisibility(VISIBLE);
                    if(reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING||reason==TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING){
                        awaitVideo();
                    }else unavailable();
                }
                @Override public void onConnectionFailed(String input){if(current(input))unavailable();}
                @Override public void onDisconnected(String input){if(current(input))unavailable();}
                @Override public void onContentBlocked(String input,TvContentRating rating){if(current(input))unavailable();}
            });
            session.setStreamVolume(0f);
            session.tune(id,TvContract.buildChannelUriForPassthroughInput(id));
            if(video==session&&placeholder.getVisibility()==VISIBLE)awaitVideo();
        }catch(RuntimeException unsupported){unavailable();}
    }
    private void unavailable(){stopVideo();state.setText(L10n.text("Preview unavailable"));}
    private void stopVideo(){
        removeCallbacks(start);cancelTimeout();
        TvView previous=video;video=null;
        if(previous!=null){
            previous.setCallback(null);
            try{previous.reset();}catch(RuntimeException ignored){}
            preview.removeView(previous);
        }
        if(placeholder!=null)placeholder.setVisibility(VISIBLE);
    }
    @Override protected void onVisibilityChanged(View changed,int visibility){
        super.onVisibilityChanged(changed,visibility);
        if(placeholder!=null){if(isShown())queue();else stopVideo();}
    }
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused)queue();else stopVideo();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();queue();}
    @Override protected void onDetachedFromWindow(){stopVideo();super.onDetachedFromWindow();}
    void close(){
        disposed=true;stopVideo();
        if(backdropHost!=null&&preview.getParent()==backdropHost)backdropHost.removeView(preview);
    }
    private TextView text(String value,int size,int color){
        TextView label=new TextView(getContext());label.setText(value);label.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        label.setTextColor(color);label.setTypeface(Ui.medium);label.setSingleLine();label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_VERTICAL);return label;
    }
    private static void put(FrameLayout parent,View view,int x,int y,int w,int h){
        LayoutParams lp=new LayoutParams(w,h);lp.leftMargin=x;lp.topMargin=y;parent.addView(view,lp);
    }
}
