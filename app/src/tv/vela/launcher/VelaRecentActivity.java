package tv.vela.launcher;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;

/** One profile-local activity reference. No provider URLs, accounts, tokens or intent serialization. */
final class VelaRecentActivity {
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-last-activity");}
    static JSONObject read(Context c){try{JSONObject j=new JSONObject(prefs(c).getString("item","{}"));return j.optString("profile").equals(VelaWorkspace.active(c))?j:new JSONObject();}catch(JSONException bad){return new JSONObject();}}
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
    static final class Card extends FrameLayout {
        Card(Context c,JSONObject item,Runnable open){super(c);setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);setPadding(18,10,18,10);setBackground(VelaTheme.shape(VelaTheme.PANEL,22));android.graphics.drawable.Drawable mark=null;if(item.optString("kind").equals("app"))try{mark=c.getPackageManager().getApplicationIcon(item.optString("key"));}catch(PackageManager.NameNotFoundException ignored){}ImageView icon=new ImageView(c);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LayoutParams ip=new LayoutParams(40,40);ip.gravity=Gravity.RIGHT|Gravity.CENTER_VERTICAL;addView(icon,ip);if(mark!=null)icon.setImageDrawable(mark);else if(item.optString("kind").equals("hdmi"))for(TvInputs.Entry entry:TvInputs.list(c))if(entry.key.equals(item.optString("key"))){DeviceIcons devices=new DeviceIcons(c);devices.load(entry.model,icon);break;}TextView title=new TextView(c);title.setText(L10n.text("Recent activity"));title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14);title.setTextColor(VelaTheme.SECONDARY);title.setTypeface(Ui.medium);LayoutParams tp=new LayoutParams(-1,25);tp.rightMargin=52;addView(title,tp);TextView detail=new TextView(c);detail.setText(item.optString("title")+"  ›");detail.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,19);detail.setTypeface(Ui.medium);detail.setTextColor(VelaTheme.WHITE);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams lp=new LayoutParams(-1,30);lp.topMargin=26;lp.rightMargin=52;addView(detail,lp);setContentDescription(title.getText()+" · "+detail.getText());setOnClickListener(v->open.run());setOnFocusChangeListener((v,f)->{setBackground(VelaTheme.shape(f?Appearance.accent(c):VelaTheme.PANEL,22));title.setTextColor(f?0xFF626773:VelaTheme.SECONDARY);detail.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);VelaFocus.card(this,f);});}
    }
}
