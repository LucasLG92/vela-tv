package tv.vela.launcher;

import android.content.*;
import android.media.tv.*;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.regex.*;

/** Public TV input metadata only. CEC child labels describe devices; parent IDs describe ports. */
final class TvInputs {
    static final int UNKNOWN=-1,CONNECTED=TvInputManager.INPUT_STATE_CONNECTED,STANDBY=TvInputManager.INPUT_STATE_CONNECTED_STANDBY,DISCONNECTED=TvInputManager.INPUT_STATE_DISCONNECTED;
    static final String[] PRESETS=DeviceProfiles.presets();
    static final class Raw {
        final String id,parent,label,custom;final int state;
        Raw(String id,String parent,String label,String custom,int state){this.id=id;this.parent=parent;this.label=clean(label);this.custom=clean(custom);this.state=state;}
        String name(){return custom.isEmpty()?label:custom;}
    }
    static final class Entry {
        final String key,inputId,port,title,model;final int state;final boolean assigned;
        Entry(String key,String id,String port,String title,String model,int state,boolean assigned){this.key=key;inputId=id;this.port=port;this.title=title;this.model=model;this.state=state;this.assigned=assigned;}
    }
    interface Source {List<Entry> read();void start(Runnable changed);void stop();}
    static final class SystemSource implements Source {
        private final Context context;private final TvInputManager manager;private Runnable changed;private boolean registered;
        private final TvInputManager.TvInputCallback callback=new TvInputManager.TvInputCallback(){
            public void onInputAdded(String id){notifyChange();}public void onInputRemoved(String id){notifyChange();}public void onInputUpdated(String id){notifyChange();}public void onInputStateChanged(String id,int state){notifyChange();}public void onTvInputInfoUpdated(TvInputInfo info){notifyChange();}
        };
        SystemSource(Context c){context=c.getApplicationContext();manager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);}
        private void notifyChange(){if(changed!=null)changed.run();}
        public List<Entry> read(){return list(context);}
        public void start(Runnable listener){stop();changed=listener;if(manager!=null)try{manager.registerCallback(callback,new Handler(Looper.getMainLooper()));registered=true;}catch(RuntimeException unavailable){}}
        public void stop(){changed=null;if(registered){registered=false;try{manager.unregisterCallback(callback);}catch(RuntimeException ignored){}}}
    }
    static String clean(String value){if(value==null)return "";String text=value.replaceAll("[\\p{Cntrl}]"," ").trim().replaceAll("\\s+"," ");return text.substring(0,Math.min(120,text.length()));}
    static SharedPreferences prefs(Context c){return VelaWorkspace.prefs(c,"input_cards");}
    static String preset(Context c,String id){String value=prefs(c).getString("device_"+LiveTvData.hash(id),"auto");return Arrays.asList(PRESETS).contains(value)?value:"auto";}
    static void assign(Context c,String id,String model){if(!Arrays.asList(PRESETS).contains(model))return;SharedPreferences.Editor e=prefs(c).edit();String key="device_"+LiveTvData.hash(id);if(model.equals("auto"))e.remove(key);else e.putString(key,model);e.apply();}
    static String detect(String label){return DeviceProfiles.detect(label);}
    static String name(String model){return DeviceProfiles.name(model);}
    static List<Raw> read(Context c){
        List<Raw> result=new ArrayList<>();TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);if(manager==null)return result;
        try{for(TvInputInfo info:manager.getTvInputList()){
            if(info.getType()!=TvInputInfo.TYPE_HDMI||result.size()>=128)continue;
            String label="",custom="";int state=UNKNOWN;try{label=String.valueOf(info.loadLabel(c));}catch(RuntimeException ignored){}try{CharSequence v=info.loadCustomLabel(c);if(v!=null)custom=v.toString();}catch(RuntimeException ignored){}try{state=manager.getInputState(info.getId());}catch(RuntimeException ignored){}
            result.add(new Raw(info.getId(),info.getParentId(),label,custom,state));
        }}catch(RuntimeException unavailable){}return result;
    }
    static List<Entry> list(Context c){return resolve(c,read(c));}
    private static int rank(Raw r){return (r.state==CONNECTED?30:r.state==STANDBY?10:0)+(detect(r.name()).equals("other")?0:5)+(r.name().isEmpty()?0:1);}
    static List<Entry> resolve(Context c,List<Raw> raw){
        Map<String,Raw> all=new TreeMap<>();for(Raw r:raw)if(r.id!=null&&!r.id.isEmpty())all.put(r.id,r);
        Map<String,List<Raw>> groups=new TreeMap<>();for(Raw r:all.values()){String root=r.id;Set<String> seen=new HashSet<>();for(Raw current=r;current.parent!=null&&!current.parent.isEmpty()&&seen.add(current.id);){root=current.parent;Raw next=all.get(root);if(next==null)break;current=next;}groups.computeIfAbsent(root,k->new ArrayList<>()).add(r);}
        List<Entry> out=new ArrayList<>();int ordinal=0;for(Map.Entry<String,List<Raw>> group:groups.entrySet()){
            String key=group.getKey();Raw port=all.get(key),chosen=null;for(Raw r:group.getValue())if(!r.id.equals(key)&&(chosen==null||rank(r)>rank(chosen)))chosen=r;
            int index=++ordinal;if(port!=null){Matcher m=Pattern.compile("(?i)HDMI[\\s_-]*([0-9]{1,2})").matcher(port.label);if(m.find())index=Integer.parseInt(m.group(1));}
            String portName="HDMI "+index,override=preset(c,key);boolean manual=!override.equals("auto");
            // A stale CEC child must not make an unplugged port appear connected.
            int state=port!=null?port.state:chosen==null?UNKNOWN:chosen.state;
            if(port!=null&&port.state!=DISCONNECTED&&chosen!=null&&chosen.state==CONNECTED)state=CONNECTED;
            String device=chosen==null?"":chosen.name(),portLabel=port==null?"":port.name();
            if(!portLabel.isEmpty()&&!port.custom.isEmpty())device=portLabel;else if(device.isEmpty()||detect(device).equals("other")&&!detect(portLabel).equals("other"))device=portLabel;
            String model=manual?override:detect(device);String title=manual?name(model):!model.equals("other")?name(model):!device.isEmpty()&&!device.matches("(?i)HDMI[\\s_-]*[0-9]*(?:\\s*\\(.*\\))?")?device:portName;
            if(state!=CONNECTED){title=portName;model="other";}else if(model.equals("other"))title=portName;
            String launch=chosen!=null&&chosen.state==CONNECTED&&state==CONNECTED?chosen.id:port!=null?port.id:chosen==null?key:chosen.id;
            out.add(new Entry(key,launch,portName,title,model,state,manual));
        }
        out.sort(Comparator.comparingInt(e->Integer.parseInt(e.port.substring(5))));return out;
    }
    static String status(int state){return state==CONNECTED?Ui.tr("Conectado","Connected","Conectado"):state==STANDBY?Ui.tr("Em espera · sem sinal ativo","Standby · no active signal","En espera · sin señal activa"):state==DISCONNECTED?Ui.tr("Sem conexão","Disconnected","Sin conexión"):Ui.tr("Estado indisponível","Status unavailable","Estado no disponible");}
}
