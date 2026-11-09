package tv.vela.launcher;

import java.net.*;
import java.util.*;
import org.json.*;

/** A small allow-list, stored only inside the encrypted source catalogue. No executable playlist options. */
final class VelaStreamHeaders {
    private VelaStreamHeaders(){}
    static Map<String,String> clean(Map<String,String> input){
        Map<String,String> out=new LinkedHashMap<>();
        if(input!=null)for(Map.Entry<String,String> e:input.entrySet())put(out,e.getKey(),e.getValue());
        return Collections.unmodifiableMap(out);
    }
    static void put(Map<String,String> out,String name,String raw){
        if(name==null||raw==null)return;String key=name.trim().toLowerCase(Locale.ROOT),value=raw.trim();
        if(value.isEmpty()||value.matches("(?s).*[\\x00-\\x1f\\x7f].*"))return;
        if(key.equals("user-agent")&&value.length()<=256)out.put("User-Agent",value);
        else if((key.equals("referer")||key.equals("referrer"))&&value.length()<=1024&&LiveTvData.httpUrl(value))out.put("Referer",value);
        else if(key.equals("origin")&&value.length()<=512&&LiveTvData.httpUrl(value))try{URI u=new URI(value);if(u.getRawQuery()==null&&(u.getRawPath().isEmpty()||u.getRawPath().equals("/")))out.put("Origin",u.getScheme()+"://"+u.getRawAuthority());}catch(URISyntaxException ignored){}
    }
    static void options(Map<String,String> out,String value){
        if(value==null||value.length()>4096)return;
        for(String item:value.split("&",9)){String[] pair=item.split("=",2);if(pair.length!=2)continue;try{put(out,URLDecoder.decode(pair[0],"UTF-8"),URLDecoder.decode(pair[1],"UTF-8"));}catch(Exception ignored){}}
    }
    static void jsonHeaders(Map<String,String> out,String value){
        if(value==null||value.length()>4096)return;
        try{JSONObject j=new JSONObject(value);int n=0;for(Iterator<String> keys=j.keys();keys.hasNext()&&n++<8;){String key=keys.next();Object v=j.opt(key);if(v instanceof String)put(out,key,(String)v);}}catch(JSONException invalid){options(out,value);}
    }
    static Map<String,String> forSignal(LiveTvData.Channel channel,String url){return channel==null?Collections.emptyMap():clean(channel.requestHeaders.get(LiveTvData.hash(url)));}
    static JSONObject write(LiveTvData.Channel channel)throws JSONException{
        JSONObject out=new JSONObject();for(String url:channel.streams){Map<String,String> headers=forSignal(channel,url);if(!headers.isEmpty())out.put(LiveTvData.hash(url),new JSONObject(headers));}return out;
    }
    static void read(LiveTvData.Channel channel,JSONObject values){
        if(values==null)return;for(String url:channel.streams){String key=LiveTvData.hash(url);JSONObject j=values.optJSONObject(key);if(j==null)continue;Map<String,String> headers=new LinkedHashMap<>();int n=0;for(Iterator<String> keys=j.keys();keys.hasNext()&&n++<8;){String name=keys.next();Object v=j.opt(name);if(v instanceof String)put(headers,name,(String)v);}if(!headers.isEmpty())channel.requestHeaders.put(key,clean(headers));}
    }
    static Map<String,String> transport(Map<String,String> values){Map<String,String> headers=new LinkedHashMap<>();headers.put("User-Agent","Vela");headers.putAll(clean(values));return headers;}
}
