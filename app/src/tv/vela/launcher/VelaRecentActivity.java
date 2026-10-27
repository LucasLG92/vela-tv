package tv.vela.launcher;

import android.content.*;
import android.content.pm.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** One profile-local activity reference. No provider URLs, accounts, tokens or intent serialization. */
final class VelaRecentActivity {
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-last-activity");}
    static JSONObject read(Context c){try{JSONObject j=new JSONObject(prefs(c).getString("item","{}"));return j.optString("profile").equals(VelaWorkspace.active(c))?j:new JSONObject();}catch(JSONException bad){return new JSONObject();}}
    static JSONObject available(Context c){JSONObject item=read(c);String kind=item.optString("kind");return kind.equals("channel")||intent(c,item)!=null?item:new JSONObject();}
    static void channel(Context c,String key,String name,String scope){save(c,"channel",key,name,scope);}
    static void save(Context c,String kind,String key,String title,String scope){if(key==null||key.isEmpty()||key.length()>512||title==null)return;try{prefs(c).edit().putString("item",new JSONObject().put("kind",kind).put("key",key).put("title",VelaWorkspace.clean(title,120)).put("scope",scope).put("profile",VelaWorkspace.active(c)).toString()).apply();}catch(JSONException impossible){}}
    static void launched(Context c,Intent intent){
        if(intent==null)return;try{
        if(Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null&&"android.media.tv".equals(intent.getData().getAuthority())&&intent.getData().getPath()!=null&&intent.getData().getPath().startsWith("/passthrough/")){for(TvInputs.Entry e:TvInputs.list(c))if(e.state==TvInputs.CONNECTED&&intent.getData().equals(android.media.tv.TvContract.buildChannelUriForPassthroughInput(e.inputId))){save(c,"hdmi",e.key,e.title,e.port);return;}}
        String pkg=intent.getComponent()!=null?intent.getComponent().getPackageName():intent.getPackage();if(pkg==null||pkg.equals(c.getPackageName())||!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))return;
        PackageManager pm=c.getPackageManager();if(pm.getLeanbackLaunchIntentForPackage(pkg)==null&&pm.getLaunchIntentForPackage(pkg)==null)return;try{save(c,"app",pkg,pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString(),"");}catch(PackageManager.NameNotFoundException ignored){}
        }catch(RuntimeException unavailable){/* Bookkeeping never interrupts a successful launch. */}
    }
    static Intent intent(Context c,JSONObject j){String kind=j.optString("kind"),key=j.optString("key");if(kind.equals("app")){PackageManager pm=c.getPackageManager();Intent i=pm.getLeanbackLaunchIntentForPackage(key);return i==null?pm.getLaunchIntentForPackage(key):i;}if(kind.equals("hdmi"))for(TvInputs.Entry e:TvInputs.list(c))if(e.key.equals(key)&&e.state==TvInputs.CONNECTED)return new Intent(Intent.ACTION_VIEW,android.media.tv.TvContract.buildChannelUriForPassthroughInput(e.inputId));return null;}
    /** One compact launcher action in Apps, with no artwork over the Home hero. */
    static final class Card extends FrameLayout {
        Card(Context c,JSONObject item,Runnable open){
            super(c);setId(View.generateViewId());setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);
            ImageView icon=new ImageView(c);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams ip=new LayoutParams(44,44);ip.leftMargin=16;ip.gravity=Gravity.CENTER_VERTICAL;addView(icon,ip);
            android.graphics.drawable.Drawable mark=null;
            if(item.optString("kind").equals("app"))try{mark=c.getPackageManager().getApplicationIcon(item.optString("key"));}catch(PackageManager.NameNotFoundException ignored){}
            if(mark!=null)icon.setImageDrawable(mark);
            else if(item.optString("kind").equals("hdmi")){for(TvInputs.Entry entry:TvInputs.list(c))if(entry.key.equals(item.optString("key"))){new DeviceIcons(c).load(entry.model,icon);break;}}
            if(icon.getDrawable()==null){icon.setImageResource(android.R.drawable.ic_media_play);icon.setColorFilter(VelaTheme.SECONDARY);}
            TextView caption=new TextView(c);caption.setText(L10n.text("Recent activity"));caption.setSingleLine(true);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,15*VelaReading.scale(c));caption.setTextColor(VelaTheme.SECONDARY);caption.setTypeface(Ui.medium);caption.setIncludeFontPadding(false);
            LayoutParams cp=new LayoutParams(-1,25);cp.leftMargin=78;cp.rightMargin=24;cp.topMargin=8;addView(caption,cp);
            TextView title=new TextView(c);title.setText(item.optString("title"));title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,21*VelaReading.scale(c));title.setTypeface(Ui.medium);title.setTextColor(VelaTheme.WHITE);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setIncludeFontPadding(false);
            LayoutParams tp=new LayoutParams(-1,32);tp.leftMargin=78;tp.rightMargin=24;tp.topMargin=35;addView(title,tp);
            setContentDescription(caption.getText()+" · "+title.getText());setOnClickListener(v->open.run());
            setOnFocusChangeListener((v,f)->{setBackground(f?VelaTheme.shape(Appearance.accent(c),VelaTheme.CONTROL_RADIUS):null);caption.setTextColor(f?0xFF626773:VelaTheme.SECONDARY);title.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);});
        }
    }
}
