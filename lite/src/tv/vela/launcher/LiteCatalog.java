package tv.vela.launcher;

import android.content.*;
import android.content.pm.*;
import android.media.tv.*;
import java.util.*;

/** Installed local apps and the television's tuner. No network clients or provider database scan. */
final class LiteCatalog {
    static final class App {
        final String key,title;final ComponentName component;
        App(String key,String title,ComponentName component){this.key=key;this.title=title;this.component=component;}
        Intent intent(){return new Intent(Intent.ACTION_MAIN).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);}
    }
    static final class Tuner {
        final String title;final Intent intent;
        Tuner(String title,Intent intent){this.title=title;this.intent=intent;}
    }
    static List<App> apps(Context c){
        PackageManager pm=c.getPackageManager();Map<String,App> result=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            List<ResolveInfo> found=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0);
            for(ResolveInfo ri:found){ActivityInfo info=ri.activityInfo;if(info==null||!info.enabled||!info.exported||info.packageName.equals(c.getPackageName())||result.containsKey(info.packageName))continue;
                // Factory calibration and first-boot legal screens stay installed for the firmware,
                // but are not ordinary applications for a television's app drawer.
                if(info.packageName.equals("com.duokan.factorytest")||info.packageName.equals("mediatek.factorymenu.ui")||info.packageName.equals("com.mstar.android.tv.disclaimercustomization"))continue;
                CharSequence label=ri.loadLabel(pm);String title=TvInputs.clean(label==null?"":label.toString());
                if(title.isEmpty()||title.equals(info.name)){CharSequence appLabel=info.applicationInfo.loadLabel(pm);title=TvInputs.clean(appLabel==null?info.packageName:appLabel.toString());}
                if(info.name.equals("com.mstar.netflixobserver.NetflixLauncher"))title="Netflix";
                if(title.isEmpty())title=info.packageName;result.put(info.packageName,new App(info.packageName,title,new ComponentName(info.packageName,info.name)));
            }
        }
        List<App> out=new ArrayList<>(result.values());java.text.Collator collator=java.text.Collator.getInstance();out.sort((a,b)->collator.compare(a.title,b.title));return out;
    }
    static Tuner tuner(Context c){
        PackageManager pm=c.getPackageManager();TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
        if(manager!=null)try{for(TvInputInfo input:manager.getTvInputList())if(input.getType()==TvInputInfo.TYPE_TUNER&&!input.isPassthroughInput()){
            Intent intent=new Intent(Intent.ACTION_VIEW,TvContract.buildChannelsUriForInput(input.getId()));
            if(intent.resolveActivity(pm)!=null){CharSequence label=input.loadLabel(c);return new Tuner(TvInputs.clean(label==null?"TV":label.toString()),intent);}
        }}catch(RuntimeException ignored){}
        // Restrict fallback to a television activity which advertises tuner content. Never select
        // a generic OEM home or video app simply because the manufacturer name matches.
        Intent channels=new Intent(Intent.ACTION_VIEW,TvContract.Channels.CONTENT_URI);
        for(ResolveInfo ri:pm.queryIntentActivities(channels,0))if(ri.activityInfo!=null&&ri.activityInfo.exported){
            ActivityInfo info=ri.activityInfo;if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)return new Tuner("TV",new Intent(channels).setComponent(new ComponentName(info.packageName,info.name)));
        }
        return null;
    }
}
