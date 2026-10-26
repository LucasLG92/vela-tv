package tv.vela.launcher;

import android.content.*;
import org.json.*;

/** Freeze a profile when a client is created. An old request can never write into a new account. */
final class VelaAccountScope extends ContextWrapper {
    private final String profile;
    private VelaAccountScope(Context c,String id){super(c.getApplicationContext());profile=id;}
    static Context freeze(Context c){return c instanceof VelaAccountScope?c:new VelaAccountScope(c,active(c));}
    private static String active(Context c){
        if(c instanceof VelaAccountScope)return ((VelaAccountScope)c).profile;
        SharedPreferences p=c.getSharedPreferences("vela-workspace",0);String id=p.getString("active","default");
        try{JSONArray all=new JSONArray(p.getString("profiles","[]"));for(int i=0;id.matches("[a-f0-9]{16}")&&i<all.length();i++)if(id.equals(all.getJSONObject(i).optString("id")))return id;}catch(Exception ignored){}
        return "default";
    }
    static SharedPreferences prefs(Context c,String name){String id=active(c);return c.getSharedPreferences(id.equals("default")?name:"vp-"+id+"-"+name,0);}
    @Override public Context getApplicationContext(){return this;}
}
