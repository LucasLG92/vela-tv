package tv.vela.launcher;

import android.content.*;
import android.util.JsonReader;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Public catalog metadata, optional personal M3U and a streamed, bounded now/next guide. */
final class LiveTvData {
    static final String CATALOG="https://www.tdtchannels.com/lists/tv.json";
    static final String EPG="https://www.tdtchannels.com/epg/TV.json";
    static final int PAGE_SIZE=12,MAX_CHANNELS=1200;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("live-tv",0);}
    static String source(Context c){String value=prefs(c).getString("source","").trim();return value.isEmpty()?CATALOG:value;}
    static String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static boolean https(String value){try{URL u=new URL(value);return u.getProtocol().equals("https")&&u.getUserInfo()==null&&!u.getHost().isEmpty();}catch(Exception e){return false;}}
    static boolean webUrl(String value){return value!=null&&value.length()<=4096&&https(value)&&value.indexOf('\\')<0&&!value.matches("(?s).*[\\x00-\\x20\\x7f].*");}
    static final class Channel {
        final String id,name,group,logo,web;
        final List<String> streams;
        Channel(String id,String name,String group,String logo,String web,List<String> streams){this.id=id.isEmpty()?hash(name+"|"+web):id;this.name=name;this.group=group;this.logo=logo;this.web=web;this.streams=Collections.unmodifiableList(streams);}
        boolean playable(){return !streams.isEmpty();}
    }
    static final class Catalog {
        final String source;final List<Channel> channels;final boolean cached;
        Catalog(String source,List<Channel> channels,boolean cached){this.source=source;this.channels=Collections.unmodifiableList(channels);this.cached=cached;}
    }
    static final class Programme {
        final long start,end;final String title;
        Programme(long start,long end,String title){this.start=start;this.end=end;this.title=title;}
        boolean current(long now){return start<=now&&end>now;}
    }
    static final class Guide {
        Programme now,next;
        void add(Programme p,long time){if(p.title.isEmpty()||p.end<=p.start)return;if(p.current(time)&&(now==null||p.start>now.start))now=p;else if(p.start>time&&(next==null||p.start<next.start))next=p;}
        Programme current(long time){return now!=null&&now.current(time)?now:next!=null&&next.current(time)?next:null;}
    }
    static Catalog catalog(Context c,boolean force)throws Exception{
        String source=source(c);File file=cached(c,"catalog-"+hash(source)+".txt");boolean old=false;
        if(force||!fresh(file,12*3600_000L))try{download(source,file,2*1024*1024);}catch(Exception e){if(!fresh(file,7*86400_000L))throw e;old=true;}
        byte[] data=read(file,2*1024*1024);String body=new String(data,StandardCharsets.UTF_8);
        List<Channel> channels=source.equals(CATALOG)?parseJson(body):parseM3u(body);
        if(channels.isEmpty())throw new IOException("Empty catalog");
        return new Catalog(source,channels,old);
    }
    static List<Channel> parseJson(String body)throws Exception{
        List<Channel> out=new ArrayList<>();Set<String> seen=new HashSet<>();JSONArray countries=new JSONObject(body).optJSONArray("countries");
        for(int country=0;countries!=null&&country<countries.length();country++){
            JSONArray groups=countries.getJSONObject(country).optJSONArray("ambits");
            for(int group=0;groups!=null&&group<groups.length();group++){
                JSONObject item=groups.getJSONObject(group);String category=item.optString("name");JSONArray channels=item.optJSONArray("channels");
                for(int i=0;channels!=null&&i<channels.length()&&out.size()<MAX_CHANNELS;i++){
                    JSONObject ch=channels.getJSONObject(i);String name=ch.optString("name").trim(),id=ch.optString("epg_id");if(id.isEmpty()||id.equals("-")||id.equals("null"))id=hash(name+"|"+category);
                    if(name.isEmpty()||!seen.add(id))continue;
                    List<String> streams=new ArrayList<>();JSONArray options=ch.optJSONArray("options");
                    for(int j=0;options!=null&&j<options.length()&&streams.size()<4;j++){
                        JSONObject option=options.getJSONObject(j);String format=option.optString("format"),url=option.optString("url");
                        if((format.equals("m3u8")||format.equals("mp4"))&&https(url)&&!url.contains("[")&&!streams.contains(url))streams.add(url);
                    }
                    out.add(new Channel(id,name,category,ch.optString("logo"),ch.optString("web"),streams));
                }
            }
        }return out;
    }
    private static final Pattern ATTRIBUTE=Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
    static List<Channel> parseM3u(String body)throws Exception{
        if(!body.replace("\uFEFF","").trim().startsWith("#EXTM3U"))throw new IOException("Not an M3U playlist");
        List<Channel> out=new ArrayList<>();Map<String,Channel> seen=new LinkedHashMap<>();String name="",id="",group="",logo="";boolean dynamic=false;
        for(String raw:body.split("\\r?\\n")){
            String line=raw.trim();if(line.startsWith("#EXTINF:")){
                Map<String,String> attrs=new HashMap<>();Matcher m=ATTRIBUTE.matcher(line);while(m.find())attrs.put(m.group(1),m.group(2));
                boolean quoted=false;int comma=-1;for(int i=0;i<line.length();i++){if(line.charAt(i)=='"')quoted=!quoted;if(line.charAt(i)==','&&!quoted){comma=i;break;}}
                name=comma<0?attrs.getOrDefault("tvg-name",""):line.substring(comma+1).trim();id=attrs.getOrDefault("tvg-id","");if(id.isEmpty())id=hash(name);
                group=attrs.getOrDefault("group-title",Ui.tr("Minha lista","My playlist","Mi lista"));logo=attrs.getOrDefault("tvg-logo","");dynamic=false;
            }else if(line.startsWith("#EXTV-streamtype:")||line.startsWith("#EXTV-headers:")||line.startsWith("#KODIPROP:")){dynamic=true;
            }else if(!line.isEmpty()&&!line.startsWith("#")&&!name.isEmpty()){
                if(out.size()>=MAX_CHANNELS)break;
                List<String> streams=new ArrayList<>();if(!dynamic&&https(line)&&!line.contains("|"))streams.add(line);
                Channel previous=seen.get(id);if(previous!=null){if(!streams.isEmpty()&&!previous.streams.contains(line)){List<String> merged=new ArrayList<>(previous.streams);if(merged.size()<4)merged.add(line);Channel replacement=new Channel(id,previous.name,previous.group,previous.logo,previous.web,merged);out.set(out.indexOf(previous),replacement);seen.put(id,replacement);}}
                else{Channel ch=new Channel(id,name,group,logo,dynamic&&https(line)?line:"",streams);out.add(ch);seen.put(id,ch);}name="";
            }
        }return out;
    }
    static Map<String,Guide> guide(Context c,Catalog catalog)throws Exception{
        if(!CATALOG.equals(catalog.source))return Collections.emptyMap();File file=cached(c,"epg.json");
        if(!fresh(file,2*3600_000L))try{download(EPG,file,8*1024*1024);}catch(Exception e){if(!fresh(file,24*3600_000L))throw e;}
        Set<String> ids=new HashSet<>();for(Channel ch:catalog.channels)ids.add(ch.id);
        try(Reader reader=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){return parseGuide(reader,ids,System.currentTimeMillis()/1000);}
    }
    static Map<String,Guide> parseGuide(Reader input,Set<String> wanted,long time)throws Exception{
        Map<String,Guide> result=new HashMap<>();JsonReader reader=new JsonReader(input);reader.beginArray();int count=0;
        while(reader.hasNext()){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(++count>MAX_CHANNELS*2)throw new IOException("Too many EPG channels");
            String id="";Guide guide=new Guide();reader.beginObject();
            while(reader.hasNext()){
                String key=reader.nextName();if(key.equals("name"))id=reader.nextString();
                else if(key.equals("events")){
                    reader.beginArray();int events=0;while(reader.hasNext()){
                        if(++events>5000)throw new IOException("Too many programmes");long start=0,end=0;String title="";reader.beginObject();
                        while(reader.hasNext()){String field=reader.nextName();if(field.equals("hi"))start=reader.nextLong();else if(field.equals("hf"))end=reader.nextLong();else if(field.equals("t")){title=reader.nextString();if(title.length()>512)title=title.substring(0,512);}else reader.skipValue();}
                        reader.endObject();guide.add(new Programme(start,end,title),time);
                    }reader.endArray();
                }else reader.skipValue();
            }reader.endObject();if(wanted.contains(id)&&(guide.now!=null||guide.next!=null))result.put(id,guide);
        }reader.endArray();return result;
    }
    static Set<String> favorites(Context c,String source){return new LinkedHashSet<>(prefs(c).getStringSet("favorites-"+hash(source),Collections.emptySet()));}
    static void toggle(Context c,String source,String id){Set<String> chosen=favorites(c,source);if(!chosen.remove(id))chosen.add(id);prefs(c).edit().putStringSet("favorites-"+hash(source),chosen).apply();}
    static void setSource(Context c,String url){prefs(c).edit().putString("source",url.trim()).apply();File folder=new File(c.getCacheDir(),"live-tv");File[] files=folder.listFiles((dir,name)->name.startsWith("catalog-"));if(files!=null)for(File file:files)if(file.isFile())file.delete();}
    static File cached(Context c,String name){File folder=new File(c.getCacheDir(),"live-tv");folder.mkdirs();return new File(folder,name);}
    static boolean fresh(File file,long limit){long age=System.currentTimeMillis()-file.lastModified();return file.isFile()&&age>=0&&age<limit;}
    static byte[] read(File file,int limit)throws IOException{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out,limit);return out.toByteArray();}}
    static void copy(InputStream in,OutputStream out,int limit)throws IOException{byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if((total+=n)>limit)throw new IOException("Response too large");out.write(buffer,0,n);}}
    static HttpURLConnection connect(String address)throws Exception{
        for(int i=0;i<5;i++){
            if(!https(address))throw new IOException("HTTPS required");HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Vela/1.9");
            int status=c.getResponseCode();if(status==200)return c;
            String next=c.getHeaderField("Location");c.disconnect();if((status==301||status==302||status==303||status==307||status==308)&&next!=null){address=new URL(new URL(address),next).toString();continue;}throw new IOException("Source unavailable");
        }throw new IOException("Too many redirects");
    }
    static void download(String url,File file,int limit)throws Exception{
        File temporary=File.createTempFile("fetch-",".part",file.getParentFile());HttpURLConnection connection=null;
        try{connection=connect(url);try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(temporary)){copy(in,out,limit);}java.nio.file.Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        finally{if(connection!=null)connection.disconnect();temporary.delete();}
    }
}
