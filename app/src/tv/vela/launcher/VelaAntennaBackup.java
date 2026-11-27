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
        if(VelaEdition.lite(c))return;JSONObject data=root.getJSONObject("settings"),personal=data.optJSONObject(SOURCE);JSONArray fav=data.optJSONObject("live-tv")==null?null:data.getJSONObject("live-tv").optJSONArray(FAVOURITES);Set<String> wanted=new HashSet<>();if(fav!=null)for(int i=0;i<fav.length();i++)wanted.add(fav.optString(i));if(personal!=null)for(String key:new String[]{"hidden","order"}){JSONArray rows=new JSONArray(personal.optString(key,"[]"));for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)wanted.add(row.optString("k"));}}
        JSONObject descriptors=new JSONObject();if(!wanted.isEmpty())for(Map.Entry<String,JSONObject> station:stations(c).entrySet())if(wanted.contains(station.getKey()))descriptors.put(station.getKey(),station.getValue());root.put("antenna",descriptors);
    }
    private static final java.util.regex.Pattern NAME_SPACES=java.util.regex.Pattern.compile("[\\s\\p{Z}]+");

    private static String nameKey(JSONObject station){
        String name=NAME_SPACES.matcher(station.optString("name")).replaceAll(" ").trim();
        return name.isEmpty()?"":station.optBoolean("radio")+":"+name;
    }

    static Map<String,String> match(JSONObject exported,Map<String,JSONObject> available)throws JSONException{
        Map<String,String> result=new HashMap<>();
        if(exported==null||exported.length()>512)return result;
        Map<String,List<String>> services=new HashMap<>(),names=new HashMap<>();
        for(Map.Entry<String,JSONObject> entry:available.entrySet()){
            JSONObject station=entry.getValue();
            String service=station.optString("service"),name=nameKey(station);
            if(!service.isEmpty())services.computeIfAbsent(service,k->new ArrayList<>()).add(entry.getKey());
            if(!name.isEmpty())names.computeIfAbsent(name,k->new ArrayList<>()).add(entry.getKey());
        }
        for(Iterator<String> it=exported.keys();it.hasNext();){
            String id=it.next();
            if(!FULL_KEY.matcher(id).matches())continue;
            JSONObject description=exported.optJSONObject(id);
            if(description==null)continue;
            String service=description.optString("service");
            List<String> candidates=service.matches("[a-f0-9]{64}")?services.get(service):null;
            if(candidates==null||candidates.size()!=1)candidates=names.get(nameKey(description));
            if(candidates!=null&&candidates.size()==1)result.put(id,candidates.get(0));
        }
        return result;
    }
    private static final java.util.regex.Pattern LITE_KEY=java.util.regex.Pattern.compile("(?:dvb|name|local):[a-f0-9]{64}");
    private static final java.util.regex.Pattern FULL_KEY=java.util.regex.Pattern.compile("ant-[0-9]{1,18}");

    private static void restoreRows(JSONObject personal,String field,Map<String,String> mapping,JSONObject previous)throws JSONException{
        JSONArray rows=new JSONArray(personal.getString(field)),restored=new JSONArray();
        boolean unresolvedFull=false,mappedFull=false;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)continue;
            String key=row.optString("k"),next=mapping.get(key);
            if(next!=null){
                restored.put(row.put("k",next));
                mappedFull=true;
            }else if(LITE_KEY.matcher(key).matches()){
                // Lite resolves its own identity against its complete tuner catalogue when opened.
                restored.put(row);
            }else if(FULL_KEY.matcher(key).matches())unresolvedFull=true;
        }

        if(rows.length()>0&&restored.length()==0){
            if(previous!=null&&previous.has(field))personal.put(field,previous.getString(field));
            else personal.remove(field);
            return;
        }
        if(unresolvedFull&&!mappedFull&&previous!=null&&previous.has(field)){
            // Lite rows can be restored even when Full must keep its destination provider rows.
            JSONArray local=new JSONArray(previous.getString(field));
            int limit=field.equals("hidden")?TvPersonalization.MAX_HIDDEN:TvPersonalization.MAX_ORDER;
            for(int i=0;i<local.length()&&restored.length()<limit;i++){
                JSONObject row=local.optJSONObject(i);
                if(row!=null&&FULL_KEY.matcher(row.optString("k")).matches())restored.put(row);
            }
        }
        personal.put(field,restored.toString());
    }

    static JSONObject prepare(Context c,JSONObject backup,JSONObject previous)throws JSONException{
        JSONObject data=new JSONObject(backup.getJSONObject("settings").toString());if(VelaEdition.lite(c))return data;
        Map<String,String> mapping=match(backup.optJSONObject("antenna"),stations(c));JSONObject live=data.optJSONObject("live-tv");if(live!=null&&live.has(FAVOURITES)){JSONArray converted=new JSONArray(),old=live.getJSONArray(FAVOURITES);for(int i=0;i<old.length();i++){String next=mapping.get(old.getString(i));if(next!=null)converted.put(next);}if(converted.length()>0||old.length()==0)live.put(FAVOURITES,converted);else{JSONObject before=previous.optJSONObject("live-tv");if(before!=null&&before.has(FAVOURITES))live.put(FAVOURITES,before.getJSONArray(FAVOURITES));else live.remove(FAVOURITES);}}
        JSONObject personal=data.optJSONObject(SOURCE);
        if(personal!=null)for(String field:new String[]{"hidden","order"})
            if(personal.has(field))restoreRows(personal,field,mapping,previous.optJSONObject(SOURCE));
        return data;
    }
}
