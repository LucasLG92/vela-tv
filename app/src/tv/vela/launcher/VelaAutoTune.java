package tv.vela.launcher;

import android.content.*;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.function.LongSupplier;

/** Explicit one-shot guide choices. Only the visible Activity owns a timer; no resident service. */
final class VelaAutoTune {
    static final int MAX_PLANS=16,MAX_DOCUMENT=24*1024;
    static final long HORIZON=48*60*60,GRACE=90;
    static final String NAMESPACE="vela-auto-tune";
    static final class Plan {
        final String scope,key,channel,title;final long start,end;
        Plan(String scope,String key,String channel,String title,long start,long end){
            this.scope=scope;this.key=key;this.channel=VelaWorkspace.clean(channel,120);
            this.title=VelaWorkspace.clean(title,240);this.start=start;this.end=end;
        }
        JSONObject json()throws JSONException{return new JSONObject().put("scope",scope).put("key",key).put("channel",channel).put("title",title).put("start",start).put("end",end);}
        JSONObject activity(String profile)throws JSONException{return new JSONObject().put("profile",profile).put("kind","channel").put("scope",scope).put("key",key).put("title",channel);}
        boolean matches(String source,String id,long at){return scope.equals(source)&&key.equals(id)&&start==at;}
    }
    interface Host {String source();boolean allowed();void tune(Plan plan);}
    private static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,NAMESPACE);}
    private static boolean id(String s,int limit){return s!=null&&!s.isEmpty()&&s.length()<=limit&&!s.contains("://")&&!s.matches("(?s).*[\\p{Cntrl}].*");}
    static List<Plan> decode(String text){
        List<Plan> out=new ArrayList<>();if(text==null||text.length()>MAX_DOCUMENT)return out;
        try{JSONArray a=new JSONArray(text);if(a.length()>MAX_PLANS)return out;
            for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);Plan p=new Plan(j.getString("scope"),j.getString("key"),j.getString("channel"),j.getString("title"),j.getLong("start"),j.getLong("end"));
                if(!id(p.scope,160)||!id(p.key,512)||p.channel.isEmpty()||p.title.isEmpty()||p.start<=0||p.end<=p.start||p.end-p.start>HORIZON)throw new JSONException("Invalid automatic switch");
                for(Plan old:out)if(Math.abs(old.start-p.start)<60)throw new JSONException("Conflicting automatic switches");out.add(p);
            }
        }catch(JSONException|ClassCastException invalid){out.clear();}out.sort(Comparator.comparingLong(p->p.start));return out;
    }
    static List<Plan> plans(Context c){try{return decode(prefs(c).getString("plans","[]"));}catch(ClassCastException invalid){return new ArrayList<>();}}
    private static void save(Context c,List<Plan> values)throws IOException{
        JSONArray a=new JSONArray();try{for(Plan p:values)a.put(p.json());}catch(JSONException invalid){throw new IOException(invalid);}
        String text=a.toString();if(text.length()>MAX_DOCUMENT||!prefs(c).edit().putString("plans",text).commit())throw new IOException("Could not save automatic switch");
    }
    static boolean scheduled(Context c,String scope,String key,long start){for(Plan p:plans(c))if(p.matches(scope,key,start))return true;return false;}
    static synchronized boolean toggle(Context c,Plan next,long now)throws IOException{
        List<Plan> values=plans(c);boolean removed=values.removeIf(p->p.matches(next.scope,next.key,next.start));values.removeIf(p->p.start<=now||p.start>now+HORIZON);
        if(!removed){
            if(next.start<=now||next.start>now+HORIZON||decode("["+json(next)+"]").size()!=1)throw new IOException("Programme is no longer upcoming");
            if(values.size()>=MAX_PLANS)throw new Limit();
            for(Plan p:values)if(Math.abs(p.start-next.start)<60)throw new Conflict();values.add(next);values.sort(Comparator.comparingLong(p->p.start));
        }
        save(c,values);return !removed;
    }
    private static String json(Plan p)throws IOException{try{return p.json().toString();}catch(JSONException bad){throw new IOException(bad);}}
    private static final class Conflict extends IOException {}
    private static final class Limit extends IOException {}
    static void action(VelaContentDetails card,Context c,String scope,String key,String channel,LiveTvData.Programme p){
        if(p.start<=System.currentTimeMillis()/1000)return;
        TextView[] button={null};String hint=L10n.text("Keep Vela on screen for automatic switching.");
        Runnable display=()->{boolean on=scheduled(c,scope,key,p.start);card.updateAction(button[0],L10n.text(on?"Cancel switch":"Switch when it starts"));card.notice(on?L10n.text("Switch scheduled")+" · "+android.text.format.DateFormat.getTimeFormat(c).format(new Date(p.start*1000)):"");card.hint(hint);};
        button[0]=card.action("","",()->{
            try{toggle(c,new Plan(scope,key,channel,p.title,p.start,p.end),System.currentTimeMillis()/1000);display.run();}
            catch(Conflict conflict){card.hint(L10n.text("Another channel is scheduled at this time. Cancel it first."));}
            catch(Limit limit){card.hint(L10n.text("You can schedule up to 16 channel switches."));}
            catch(IOException failed){card.hint(L10n.text("Could not schedule this programme. It may have already started."));}
        });
        card.clockAction(button[0]);display.run();
    }
    /** One bounded list and one main-loop callback, only while the owner is resumed. */
    static final class Session implements AutoCloseable {
        private final Context context;private final Host host;private final Handler main=new Handler(Looper.getMainLooper());private final LongSupplier clock;
        private SharedPreferences observed;private String profile="";private long visibleFrom;private boolean running,dispatching;
        private final Runnable rearm=this::refresh;
        private final Runnable due=this::deliver;
        private final SharedPreferences.OnSharedPreferenceChangeListener changed=(p,key)->{if(running){main.removeCallbacks(rearm);main.post(rearm);}};
        Session(Context context,Host host){this(context,host,System::currentTimeMillis);}
        Session(Context context,Host host,LongSupplier clock){this.context=context;this.host=host;this.clock=clock;}
        private void refresh(){if(running){if(!profile.equals(VelaWorkspace.active(context))){bind();visibleFrom=now();}arm();}}
        private long now(){return clock.getAsLong()/1000;}
        void resume(){if(running)return;running=true;visibleFrom=now();bind();VelaWorkspace.registry(context).registerOnSharedPreferenceChangeListener(changed);arm();}
        private void bind(){if(observed!=null)observed.unregisterOnSharedPreferenceChangeListener(changed);profile=VelaWorkspace.active(context);observed=prefs(context);observed.registerOnSharedPreferenceChangeListener(changed);}
        private void arm(){
            main.removeCallbacks(due);if(!running||dispatching)return;long now=now();List<Plan> values=plans(context);
            boolean pruned=values.removeIf(p->p.start<visibleFrom||p.end<=now||p.start<now-GRACE||p.start>now+HORIZON);
            if(pruned)try{save(context,values);}catch(IOException failed){return;}
            if(!values.isEmpty())main.postDelayed(due,Math.max(0,Math.min(60000,values.get(0).start*1000-clock.getAsLong())));
        }
        private void deliver(){
            if(!running||!profile.equals(VelaWorkspace.active(context))){rearm.run();return;}long now=now();List<Plan> values=plans(context);Plan selected=null;
            for(Plan p:values)if(p.start>=visibleFrom&&p.start<=now&&p.end>now&&now-p.start<=GRACE){selected=p;break;}
            if(selected!=null){final Plan chosen=selected;values.remove(chosen);dispatching=true;
                try{save(context,values);PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
                    if(running&&profile.equals(VelaWorkspace.active(context))&&chosen.scope.equals(host.source())&&host.allowed()&&(power==null||power.isInteractive()))host.tune(chosen);
                }catch(IOException failed){/* A failed durable consume must never launch twice. */}finally{dispatching=false;}
            }
            arm();
        }
        void pause(){running=false;main.removeCallbacksAndMessages(null);if(observed!=null){observed.unregisterOnSharedPreferenceChangeListener(changed);observed=null;}VelaWorkspace.registry(context).unregisterOnSharedPreferenceChangeListener(changed);}
        @Override public void close(){pause();}
    }
}
