package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Bounded recurring titles and cached airings. One inexact alarm, never a resident service. */
final class VelaShows {
    static final int MAX_RULES=32,MAX_AIRINGS=128;
    static final class Airing {
        final String title,channel,eventId,description,image,logo;final long start,end;
        Airing(String title,String channel,long start,long end){this(title,channel,start,end,"");}
        Airing(String title,String channel,long start,long end,String id){this(title,channel,start,end,id,"","","");}
        Airing(String title,String channel,long start,long end,String id,String description,String image,String logo){this.description=VelaWorkspace.clean(description==null?"":description,1600);this.image=VelaProgrammeArt.url(image);this.logo=VelaProgrammeArt.url(logo);eventId=id==null?"":id;this.title=VelaWorkspace.clean(title,240);this.channel=VelaWorkspace.clean(channel,120);this.start=start;this.end=end;}
    }
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"vela-programmes");}
    static String identity(String title){return VelaWorkspace.normal(title).replaceFirst("\\s+(?:t\\s*\\d+\\s*e\\s*\\d+|s\\d+e\\d+|temporada\\s+\\d+.*|episodio\\s+\\d+.*|cap[ií]tulo\\s+\\d+.*)$","").trim();}
    static List<String> rules(Context c){List<String> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs(c).getString("rules","[]"));for(int i=0;i<Math.min(MAX_RULES,a.length());i++){String s=VelaWorkspace.clean(a.getString(i),160);if(!s.isEmpty()&&!out.contains(s))out.add(s);}}catch(JSONException invalid){}return out;}
    static boolean follows(Context c,String title){for(String rule:rules(c))if(identity(rule).equals(identity(title)))return true;return false;}
    static void toggle(Context c,String title){List<String> values=rules(c);String key=identity(title);boolean removed=values.removeIf(s->identity(s).equals(key));if(!removed&&values.size()<MAX_RULES&&!key.isEmpty())values.add(VelaWorkspace.clean(title.replaceFirst("(?i)\\s+(?:T\\d+\\s*E\\d+|S\\d+E\\d+).*$",""),160));prefs(c).edit().putString("rules",new JSONArray(values).toString()).remove("scan").commit();schedule(c);}
    static boolean enabled(Context c){return prefs(c).getBoolean("notifications",false);}
    static boolean permitted(Context c){NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null||!m.areNotificationsEnabled()||Build.VERSION.SDK_INT>=33&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return false;NotificationChannel channel=m.getNotificationChannel("vela-programmes");return channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    static void invalidate(Context c){cancel(c);prefs(c).edit().remove("airings").remove("scan").remove("shown").commit();}
    static void enabled(Context c,boolean enabled){prefs(c).edit().putBoolean("notifications",enabled).commit();if(enabled)schedule(c);else cancel(c);}
    private static Set<String> ruleKeys(Context c){Set<String> out=new HashSet<>();for(String title:rules(c))out.add(identity(title));return out;}
    static List<Airing> cached(Context c){Set<String> keys=ruleKeys(c);List<Airing> result=new ArrayList<>();try{JSONArray a=new JSONArray(prefs(c).getString("airings","[]"));for(int i=0;i<Math.min(a.length(),MAX_AIRINGS);i++){JSONObject j=a.getJSONObject(i);Airing p=new Airing(j.getString("title"),j.getString("channel"),j.getLong("start"),j.getLong("end"));if(p.end>System.currentTimeMillis()/1000&&keys.contains(identity(p.title)))result.add(p);}}catch(JSONException invalid){}return result;}
    static synchronized void remember(Context c,List<Airing> airings){List<Airing> merged=new ArrayList<>(cached(c));merged.addAll(airings);replace(c,merged);}
    static synchronized void replace(Context c,List<Airing> airings){Set<String> keys=ruleKeys(c);JSONArray a=new JSONArray();Set<String> seen=new HashSet<>();try{List<Airing> sorted=new ArrayList<>(airings);sorted.sort(Comparator.comparingLong(p->p.start));for(Airing p:sorted)if(p.end>System.currentTimeMillis()/1000&&p.start<System.currentTimeMillis()/1000+48*3600&&p.end>p.start&&keys.contains(identity(p.title))&&seen.add(p.channel+":"+p.start+":"+p.title)&&a.length()<MAX_AIRINGS)a.put(new JSONObject().put("title",p.title).put("channel",p.channel).put("start",p.start).put("end",p.end));prefs(c).edit().putString("airings",a.toString()).commit();schedule(c);}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,701,new Intent(c,VelaReminderReceiver.class).setAction(c.getPackageName()+".PROGRAMME"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void cancel(Context c){AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(m!=null)m.cancel(alarm(c));}
    static void schedule(Context c){cancel(c);if(!enabled(c)||!permitted(c))return;long now=System.currentTimeMillis()/1000,shown=prefs(c).getLong("shown",0);for(Airing p:cached(c))if(p.start>shown&&p.start>now){AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(m!=null)m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(System.currentTimeMillis()+1000,(p.start-120)*1000),alarm(c));return;}}
    static void deliver(Context c){if(!enabled(c))return;long now=System.currentTimeMillis()/1000,shown=prefs(c).getLong("shown",0),latest=shown;int sent=0;for(Airing p:cached(c))if(p.start>shown&&p.start<=now+150&&p.end>now&&sent++<4){latest=Math.max(latest,p.start);NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null&&manager.areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED)){manager.createNotificationChannel(new NotificationChannel("vela-programmes",VelaWorkspace.t("Programmes","Programas"),NotificationManager.IMPORTANCE_DEFAULT));String cls=VelaEdition.lite(c)?"tv.vela.launcher.LiteHomeActivity":"tv.vela.launcher.HomeActivity";Intent open=new Intent().setClassName(c.getPackageName(),cls).putExtra("velaTools",3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(c,702,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);manager.notify(703+(sent%4),new Notification.Builder(c,"vela-programmes").setSmallIcon(R.drawable.icon).setContentTitle(p.title).setContentText(p.channel+" · "+(p.start>now?VelaWorkspace.t("Starting soon","Empieza pronto"):VelaWorkspace.t("On now","En emisión"))).setContentIntent(pi).setAutoCancel(true).build());}}if(latest>shown)prefs(c).edit().putLong("shown",latest).commit();schedule(c);}
}
