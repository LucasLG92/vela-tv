package tv.vela.launcher;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.media.tv.*;
import org.json.*;
import java.util.*;

/** Portability for Full's provider row IDs: DVB service first, an unambiguous station name second. */
final class VelaAntennaBackup {
    private static final String SOURCE="tv-personal-"+LiveTvData.hash("antenna"),FAVOURITES="favorites-"+LiveTvData.hash("antenna");
    private static Map<String,JSONObject> stations(Context c){Map<String,JSONObject> out=new HashMap<>();if(c.checkSelfPermission("android.permission.READ_TV_LISTINGS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return out;
        TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);Set<String> inputs=new HashSet<>();if(manager==null)return out;
        try{for(TvInputInfo info:manager.getTvInputList())if(info.getType()==TvInputInfo.TYPE_TUNER&&info.getServiceInfo()!=null&&(info.getServiceInfo().applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)inputs.add(info.getId());
            try(Cursor row=c.getContentResolver().query(TvContract.Channels.CONTENT_URI,new String[]{"_id","input_id","display_name","browsable","locked","service_type","type","original_network_id","transport_stream_id","service_id"},null,null,null)){
                for(int scanned=0;row!=null&&row.moveToNext()&&scanned<5000;scanned++){if(Thread.currentThread().isInterrupted())break;if(!inputs.contains(row.getString(1))||row.getInt(3)!=1||row.getInt(4)!=0)continue;String name=VelaWorkspace.normal(VelaWorkspace.clean(row.getString(2),120));if(name.contains("://"))continue;boolean radio=TvContract.Channels.SERVICE_TYPE_AUDIO.equals(row.getString(5));String service=row.getInt(7)>0&&row.getInt(8)>0&&row.getInt(9)>0?LiveTvData.hash((radio?"r:":"t:")+row.getString(6)+":"+row.getInt(7)+":"+row.getInt(8)+":"+row.getInt(9)):"";out.put("ant-"+row.getLong(0),new JSONObject().put("name",name).put("service",service).put("radio",radio));}
            }
        }catch(Exception unavailable){out.clear();}return out;
    }
    static void describe(Context c,JSONObject root)throws JSONException{
        if(c.getPackageName().endsWith(".lite"))return;JSONObject data=root.getJSONObject("settings"),personal=data.optJSONObject(SOURCE);JSONArray fav=data.optJSONObject("live-tv")==null?null:data.getJSONObject("live-tv").optJSONArray(FAVOURITES);Set<String> wanted=new HashSet<>();if(fav!=null)for(int i=0;i<fav.length();i++)wanted.add(fav.optString(i));if(personal!=null)for(String key:new String[]{"hidden","order"}){JSONArray rows=new JSONArray(personal.optString(key,"[]"));for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)wanted.add(row.optString("k"));}}
        JSONObject descriptors=new JSONObject();if(!wanted.isEmpty())for(Map.Entry<String,JSONObject> station:stations(c).entrySet())if(wanted.contains(station.getKey()))descriptors.put(station.getKey(),station.getValue());root.put("antenna",descriptors);
    }
    static Map<String,String> match(JSONObject exported,Map<String,JSONObject> available)throws JSONException{
        Map<String,List<String>> services=new HashMap<>(),names=new HashMap<>();for(Map.Entry<String,JSONObject> e:available.entrySet()){JSONObject d=e.getValue();String service=d.optString("service");if(!service.isEmpty())services.computeIfAbsent(service,k->new ArrayList<>()).add(e.getKey());names.computeIfAbsent(d.optBoolean("radio")+":"+d.optString("name"),k->new ArrayList<>()).add(e.getKey());}
        Map<String,String> result=new HashMap<>();if(exported==null||exported.length()>512)return result;for(Iterator<String> it=exported.keys();it.hasNext();){String id=it.next();if(!id.matches("ant-[0-9]{1,18}"))continue;JSONObject description=exported.optJSONObject(id);if(description==null)continue;String service=description.optString("service"),name=description.optString("name");List<String> candidates=service.matches("[a-f0-9]{64}")?services.get(service):null;if(candidates==null||candidates.size()!=1)candidates=names.get(description.optBoolean("radio")+":"+name);if(candidates!=null&&candidates.size()==1)result.put(id,candidates.get(0));}return result;
    }
    static JSONObject prepare(Context c,JSONObject backup,JSONObject previous)throws JSONException{
        JSONObject data=new JSONObject(backup.getJSONObject("settings").toString());if(c.getPackageName().endsWith(".lite"))return data;
        Map<String,String> mapping=match(backup.optJSONObject("antenna"),stations(c));JSONObject live=data.optJSONObject("live-tv");if(live!=null&&live.has(FAVOURITES)){JSONArray converted=new JSONArray(),old=live.getJSONArray(FAVOURITES);for(int i=0;i<old.length();i++){String next=mapping.get(old.getString(i));if(next!=null)converted.put(next);}if(converted.length()>0||old.length()==0)live.put(FAVOURITES,converted);else{JSONObject before=previous.optJSONObject("live-tv");if(before!=null&&before.has(FAVOURITES))live.put(FAVOURITES,before.getJSONArray(FAVOURITES));else live.remove(FAVOURITES);}}
        JSONObject personal=data.optJSONObject(SOURCE);if(personal!=null){JSONObject old=previous.optJSONObject(SOURCE);for(String key:new String[]{"hidden","order"})if(personal.has(key)){JSONArray source=new JSONArray(personal.getString(key)),out=new JSONArray();for(int i=0;i<source.length();i++){JSONObject row=source.optJSONObject(i);if(row==null)continue;String next=mapping.get(row.optString("k"));if(next!=null)out.put(new JSONObject(row.toString()).put("k",next));}if(out.length()>0||source.length()==0)personal.put(key,out.toString());else if(old!=null&&old.has(key))personal.put(key,old.getString(key));else personal.remove(key);}}
        return data;
    }
}
