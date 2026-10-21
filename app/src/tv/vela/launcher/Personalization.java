package tv.vela.launcher;

import android.content.*;

/** Installation-local setup. Upgrades keep existing accounts, shortcuts and recovery data. */
final class Personalization {
    private Personalization() {}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("personalization",Context.MODE_PRIVATE);}
    static synchronized void prepare(Context c){
        SharedPreferences p=prefs(c);if(p.getBoolean("initialized",false))return;
        boolean existing=!c.getSharedPreferences("plex",0).getString("accountToken","").isEmpty()
                ||!JellyfinClient.prefs(c).getString("token","").isEmpty()||SidebarApps.prefs(c).contains("slots")||AmbientWeather.prefs(c).contains("city")
                ||HomeMode.FREE.equals(HomeMode.state(c).optString("mode"));
        // Older private builds supplied a city at build time. Preserve it before moving to a neutral APK.
        if(existing&&!AmbientWeather.prefs(c).contains("city")){
            AmbientWeather.Place place=AmbientWeather.manual(c);if(place!=null)AmbientWeather.select(c,place);
        }
        if(!SidebarApps.prefs(c).contains("slots"))SidebarApps.detect(c);
        p.edit().putBoolean("initialized",true).putBoolean("complete",existing).commit();
    }
    static boolean needed(Context c){prepare(c);return !prefs(c).getBoolean("complete",false);}
    static void complete(Context c){prefs(c).edit().putBoolean("complete",true).commit();}
    static boolean launchIfNeeded(android.app.Activity activity){
        if(!needed(activity))return false;
        activity.startActivity(new Intent(activity,SetupActivity.class));return true;
    }
}
