package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native first-run and reversible mode selection. No library, player or IPTV client is constructed. */
public class EditionActivity extends Activity {
    static final int IMPORT=9061;
    private TextView message;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> importing;private boolean busy;
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!VelaEdition.mode(this).isEmpty()&&VelaSessions.blocked(this)){startActivity(new Intent().setClassName(getPackageName(),VelaEdition.home(this)).putExtra("velaTools",4));finish();return;}Ui.init(this);render();}
    private void render(){
        FrameLayout root=new FrameLayout(this);root.setBackground(VelaTheme.canvas());setContentView(root);
        VelaReading.Stage stage=new VelaReading.Stage(this,1920,1080);root.addView(stage,new FrameLayout.LayoutParams(-1,-1));FrameLayout page=new FrameLayout(this);stage.addView(page);
        put(page,label(L10n.text("Choose your Vela"),48),160,110,1600,90);
        put(page,label(L10n.text("One app. Choose the experience that fits this television."),25),164,215,1590,64);
        View full=card(L10n.text("Full"),L10n.text("Plex, Jellyfin, IPTV, antenna, HDMI and apps."),()->choose("full"));
        full.setEnabled(VelaEdition.fullAvailable());full.setAlpha(VelaEdition.fullAvailable()?1:.45f);full.setFocusable(VelaEdition.fullAvailable());put(page,full,160,350,780,310);
        View lite=card(L10n.text("Lite"),L10n.text("HDMI, antenna and apps. Only the essentials start."),()->choose("lite"));put(page,lite,980,350,780,310);
        message=label(VelaEdition.fullAvailable()?L10n.text("You can change modes in Settings. Your profiles and accounts stay saved."):L10n.text("This Android version supports Lite. Full requires Android 11 or newer."),23);message.setTextColor(VelaTheme.SECONDARY);put(page,message,164,710,1590,90);
        if(legacyInstalled()){View migration=button(L10n.text("Import current Lite profile"),this::importLegacy);put(page,migration,160,845,780,76);}
        if(!VelaEdition.mode(this).isEmpty()){View back=button(L10n.text("Back"),()->openHome());put(page,back,980,845,780,76);}
        (VelaEdition.mode(this).equals("lite")||!VelaEdition.fullAvailable()?lite:full).requestFocus();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(VelaTheme.WHITE);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);v.setMaxLines(2);return v;}
    private View card(String title,String detail,Runnable action){
        FrameLayout box=new FrameLayout(this);box.setFocusable(true);box.setFocusableInTouchMode(true);box.setDefaultFocusHighlightEnabled(false);
        TextView heading=label(title,40),body=label(detail,25);heading.setTypeface(Ui.displaySemibold);put(box,heading,44,44,690,70);body.setGravity(Gravity.TOP);body.setMaxLines(3);put(box,body,44,142,690,130);
        box.setBackground(VelaTheme.panel());box.setOnFocusChangeListener((v,focus)->{box.setBackground(focus?VelaTheme.shape(VelaReading.accent(this),30):VelaTheme.panel());heading.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);body.setTextColor(focus?0xFF555A65:VelaTheme.SECONDARY);});box.setOnClickListener(v->action.run());return box;
    }
    private View button(String text,Runnable action){TextView v=label(text,24);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(VelaTheme.row(false,VelaTheme.WHITE));v.setOnFocusChangeListener((w,f)->{v.setBackground(VelaTheme.row(f,VelaTheme.WHITE));v.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);});v.setOnClickListener(w->action.run());return v;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private void choose(String mode){if(busy)return;try{VelaEdition.select(this,mode);openHome();}catch(RuntimeException failed){message.setText(L10n.text("Could not save mode. Try again."));}}
    private void openHome(){if(busy)return;String mode=VelaEdition.mode(this);if(mode.isEmpty())return;Intent next=new Intent(getIntent());next.removeExtra("changeMode");next.removeExtra("importLegacy");next.setClassName(getPackageName(),VelaEdition.home(this));next.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);startActivity(next);finish();}
    private boolean legacyInstalled(){return getPackageManager().checkSignatures(getPackageName(),"tv.vela.launcher.lite")==PackageManager.SIGNATURE_MATCH;}
    private void importLegacy(){if(busy)return;try{startActivityForResult(new Intent().setClassName("tv.vela.launcher.lite","tv.vela.launcher.LiteExportActivity"),IMPORT);}catch(ActivityNotFoundException|SecurityException unavailable){message.setText(L10n.text("Update the old Lite app to the migration release first."));}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=IMPORT||result!=RESULT_OK||data==null||data.getData()==null||busy)return;
        Uri uri=data.getData();if(!"content".equals(uri.getScheme())||!"tv.vela.launcher.lite.migration".equals(uri.getAuthority())||!legacyInstalled())return;
        String original=VelaWorkspace.active(this),previousMode=VelaEdition.mode(this),name=VelaWorkspace.clean(data.getStringExtra("profileName"),24);busy=true;message.setText(L10n.text("Importing Lite settings…"));
        importing=worker.submit(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)){
                JSONObject backup=VelaWorkspace.read(input);if(!backup.optString("edition").equals("lite"))throw new IOException("Not a Lite profile");
                main.post(()->{if(isFinishing()||isDestroyed()||!VelaWorkspace.active(this).equals(original)||VelaSessions.blocked(this)){busy=false;return;}
                    String created=null;boolean reuse=previousMode.isEmpty()&&VelaWorkspace.profiles(this).size()==1&&pristine();
                    try{
                        if(!reuse){String base=VelaWorkspace.clean("Lite · "+(name.isEmpty()?"Profile":name),26),candidate=base;Set<String> names=new HashSet<>();for(String existing:VelaWorkspace.profiles(this).values())names.add(VelaWorkspace.normal(existing));int n=2;while(names.contains(VelaWorkspace.normal(candidate)))candidate=base+" "+n++;created=VelaWorkspace.create(this,candidate);VelaWorkspace.select(this,created);}
                        VelaEdition.select(this,"lite");VelaWorkspace.apply(this,backup,true);VelaEdition.prefs(this).edit().putBoolean("imported",true).commit();busy=false;openHome();
                    }catch(Exception failed){try{VelaWorkspace.select(this,original);if(created!=null)VelaWorkspace.delete(this,created);if(previousMode.isEmpty())VelaEdition.prefs(this).edit().remove("mode").commit();else VelaEdition.select(this,previousMode);}catch(Exception ignored){}busy=false;message.setText(L10n.text("Could not import. Your existing profiles were kept."));}
                });
            }catch(Exception failed){main.post(()->{if(!isFinishing()&&!isDestroyed()){busy=false;message.setText(L10n.text("Could not import. Your existing profiles were kept."));}});}
        });
    }
    private boolean pristine(){try{return !VelaEdition.prefs(this).getBoolean("imported",false)&&VelaWorkspace.snapshot(this).getJSONObject("settings").length()==0;}catch(Exception invalid){return false;}}
    @Override public void onBackPressed(){if(!busy&&!VelaEdition.mode(this).isEmpty())openHome();}
    @Override protected void onDestroy(){if(importing!=null)importing.cancel(true);worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
