package tv.vela.launcher;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import org.json.JSONArray;
import java.text.Collator;
import java.util.*;

/** Local shortcuts. Only the picker inventories apps; icons remain small regardless of list length. */
final class SidebarApps {
    static final String YOUTUBE="com.google.android.youtube.tv";
    private static final String[] DEFAULTS={"com.plexapp.android",YOUTUBE,"com.amazon.amazonvideo.livingroom","com.tv.tdtspain"};
    private SidebarApps() {}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("sidebar_apps",Context.MODE_PRIVATE);}
    static String[] slots(Context c){
        try {
            String saved=prefs(c).getString("slots",null);
            if(saved==null)return detected(c);
            JSONArray data=new JSONArray(saved);
            String[] out=new String[data.length()];Set<String> seen=new HashSet<>();
            for(int i=0;i<out.length;i++){
                String pkg=data.optString(i,"");
                out[i]=pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")&&!pkg.equals(c.getPackageName())&&seen.add(pkg)?pkg:"";
            }
            return out;
        }catch(Exception ignored){return detected(c);}
    }
    static void assign(Context c,int slot,String pkg){
        String[] out=slots(c);validate(c,pkg);
        if(slot<0||slot>=out.length)throw new IllegalArgumentException("Invalid shortcut position");
        String previous=out[slot];
        // Selecting an existing shortcut swaps positions instead of creating duplicates.
        if(!pkg.isEmpty())for(int i=0;i<out.length;i++)if(i!=slot&&pkg.equals(out[i]))out[i]=previous;
        out[slot]=pkg;save(c,out);
    }
    static int add(Context c,String pkg){
        validate(c,pkg);if(pkg.isEmpty())throw new IllegalArgumentException("Cannot add an empty shortcut");
        String[] current=slots(c);for(int i=0;i<current.length;i++)if(pkg.equals(current[i]))return i;
        String[] out=Arrays.copyOf(current,current.length+1);out[current.length]=pkg;save(c,out);return current.length;
    }
    static void remove(Context c,int slot){
        List<String> out=new ArrayList<>(Arrays.asList(slots(c)));out.remove(slot);save(c,out.toArray(new String[0]));
    }
    /** Context actions use the captured selection, never a stale numeric position. */
    static boolean remove(Context c,String[] expected,String pkg){
        String[] current=slots(c);if(!Arrays.equals(current,expected))return false;
        int slot=Arrays.asList(current).indexOf(pkg);if(slot<0||pkg.isEmpty())return false;
        remove(c,slot);return true;
    }
    /** A draft may only reorder the unchanged selection, including hidden and unavailable entries. */
    static boolean reorder(Context c,String[] expected,String[] order){
        if(!Arrays.equals(slots(c),expected)||expected.length!=order.length)return false;
        String[] before=expected.clone(),after=order.clone();Arrays.sort(before);Arrays.sort(after);
        if(!Arrays.equals(before,after))return false;
        save(c,order);return true;
    }
    private static void validate(Context c,String pkg){
        if(pkg==null||(!pkg.isEmpty()&&(!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||pkg.equals(c.getPackageName()))))throw new IllegalArgumentException("Invalid app");
    }
    private static void save(Context c,String[] slots){prefs(c).edit().putString("slots",new JSONArray(Arrays.asList(slots)).toString()).apply();}
    static void reset(Context c){detect(c);}
    static void detect(Context c){save(c,detected(c));}
    private static String[] detected(Context c){
        List<String> out=new ArrayList<>();
        for(String pkg:DEFAULTS)if(launchable(c,pkg))out.add(pkg);
        if(launchable(c,JellyfinClient.PACKAGE))out.add(JellyfinClient.PACKAGE);
        if(out.isEmpty())for(Choice choice:choices(c)){out.add(choice.pkg);if(out.size()==4)break;}
        return out.toArray(new String[0]);
    }
    static boolean launchable(Context c,String pkg){
        if(pkg.isEmpty()||pkg.equals(c.getPackageName()))return false;
        try{return HomeApps.launchIntent(c.getPackageManager(),pkg)!=null;}catch(RuntimeException ignored){return false;}
    }
    static String label(Context c,String pkg){
        if(pkg.isEmpty())return Ui.tr("Sem acesso","No shortcut","Sin acceso");
        if(pkg.equals(DEFAULTS[0]))return "Plex";
        if(pkg.equals(YOUTUBE))return "YouTube";
        if(pkg.equals(DEFAULTS[2]))return "Prime Video";
        if(pkg.equals(DEFAULTS[3]))return "TDT España";
        try{return String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)));}
        catch(PackageManager.NameNotFoundException ignored){return pkg;}
    }
    static final class Choice {
        final String pkg,label;
        Choice(String pkg,String label){this.pkg=pkg;this.label=label;}
    }
    static List<Choice> choices(Context c){
        PackageManager pm=c.getPackageManager();Map<String,Choice> found=new HashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                if(info.activityInfo==null)continue;String pkg=info.activityInfo.packageName;
                if(!found.containsKey(pkg)&&launchable(c,pkg))found.put(pkg,new Choice(pkg,label(c,pkg)));
            }
        }
        List<Choice> out=new ArrayList<>(found.values());Collator order=Collator.getInstance(Ui.locale());
        out.sort((a,b)->{int compare=order.compare(a.label,b.label);return compare==0?a.pkg.compareTo(b.pkg):compare;});
        return out;
    }
    static Drawable icon(Context c,String pkg){
        for(int i=0;i<3;i++)if(pkg.equals(DEFAULTS[i])){
            String file="brands/icon-"+(i==0?"plex":i==1?"youtube":"prime")+".png";
            try(java.io.InputStream in=c.getAssets().open(file)){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;
                Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
                if(bitmap!=null)return new BitmapDrawable(c.getResources(),bitmap);
            }catch(java.io.IOException ignored){}
        }
        if(pkg.isEmpty())return null;
        try{
            Drawable original=c.getPackageManager().getApplicationIcon(pkg);
            Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
            int w=Math.max(1,original.getIntrinsicWidth()),h=Math.max(1,original.getIntrinsicHeight());
            float scale=64f/Math.max(w,h);int width=Math.round(w*scale),height=Math.round(h*scale);
            original.setBounds((64-width)/2,(64-height)/2,(64+width)/2,(64+height)/2);original.draw(canvas);
            return new BitmapDrawable(c.getResources(),bitmap);
        }catch(PackageManager.NameNotFoundException|RuntimeException ignored){return null;}
    }
}
