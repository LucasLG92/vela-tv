package tv.vela.launcher;

import android.content.*;
import android.util.JsonReader;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Shared channel metadata and bounded programme timing. TV has M3U, Xtream and antenna sources. */
final class LiveTvData {
    static final String CATALOG="https://www.tdtchannels.com/lists/tv.json";
    static final String DEFAULT_M3U="https://www.tdtchannels.com/lists/tv.m3u8";
    static final String EPG="https://www.tdtchannels.com/epg/TV.json";
    static final int PAGE_SIZE=12,MAX_CHANNELS=1200;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("live-tv",0);}
    // An explicit Settings choice replaces the old last-used-view flag. Existing installs start in Guide too.
    static boolean guideView(Context c){return !"channels".equals(prefs(c).getString("tv-view","guide"));}
    static void guideView(Context c,boolean guide){prefs(c).edit().putString("tv-view",guide?"guide":"channels").apply();}
    static String mode(Context c){String mode=prefs(c).getString("mode","");if(mode.equals("antenna"))return mode;return new XtreamClient(c).active()?"xtream":"m3u";}
    static String source(Context c){String mode=mode(c);return mode.equals("antenna")?"antenna":mode.equals("xtream")?new XtreamClient(c).source():M3uClient.identity(c);}
    static void select(Context c,String mode){prefs(c).edit().putString("mode",mode).putBoolean("xtream",mode.equals("xtream")).apply();}
    static String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));char[] digits="0123456789abcdef".toCharArray(),out=new char[bytes.length*2];for(int i=0;i<bytes.length;i++){out[i*2]=digits[(bytes[i]&255)>>>4];out[i*2+1]=digits[bytes[i]&15];}return new String(out);}catch(Exception e){throw new IllegalStateException(e);}}
    static boolean https(String value){return httpUrl(value)&&value.regionMatches(true,0,"https:",0,6);}
    static boolean httpUrl(String value){try{if(value==null||value.length()>8192||value.indexOf('\\')>=0||value.matches("(?s).*[\\x00-\\x20\\x7f].*"))return false;URI u=new URI(value);return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))&&u.getUserInfo()==null&&u.getHost()!=null&&u.getFragment()==null&&u.getPort()!=0&&u.getPort()<=65535;}catch(Exception e){return false;}}
    static final class Channel {
        final String id,name,group,logo,xtreamId,xtreamAccount,inputId,number,epgId;
        final List<String> streams;
        List<Channel> variants=Collections.emptyList();
        Channel(String id,String name,String group,String logo,List<String> streams){this(id,name,group,logo,streams,"","","","",id);}
        Channel(String id,String name,String group,String logo,List<String> streams,String xc,String account){this(id,name,group,logo,streams,xc,account,"","",id);}
        Channel(String id,String name,String group,String logo,List<String> streams,String xc,String account,String input,String number,String epg){this.id=id.isEmpty()?hash(name+"|"+group):id;this.name=name;this.group=group;this.logo=logo;this.streams=Collections.unmodifiableList(new ArrayList<>(streams));xtreamId=xc;xtreamAccount=account;inputId=input;this.number=number;epgId=epg;}
        boolean antenna(){return !inputId.isEmpty();}
        boolean playable(){return !streams.isEmpty()||!xtreamId.isEmpty()||antenna();}
    }
    static final class Catalog {
        final String source;final List<Channel> channels;final boolean cached;
        Catalog(String source,List<Channel> channels,boolean cached){this.source=source;this.channels=Collections.unmodifiableList(channels);this.cached=cached;}
    }
    static final class Programme {
        final long start,end;final String title;
        Programme(long start,long end,String title){this.start=start;this.end=end;this.title=title;}
        boolean current(long now){return start<=now&&end>now;}
        long remainingMinutes(long now){return current(now)?1+(end-now-1)/60:0;}
        int progress(long now){return current(now)?(int)Math.max(0,Math.min(1000,1000d*((double)now-start)/((double)end-start))):0;}
    }
    static final class Guide {
        Programme now,next;
        void add(Programme p,long time){if(p.title.isEmpty()||p.end<=p.start)return;if(p.current(time)&&(now==null||p.start>now.start))now=p;else if(p.start>time&&(next==null||p.start<next.start))next=p;}
        Programme current(long time){return now!=null&&now.current(time)?now:next!=null&&next.current(time)?next:null;}
    }
    static Map<String,Guide> publicGuide(Context c,List<Channel> channels)throws Exception{
        File file=cached(c,"epg.json");if(!fresh(file,2*3600_000L))try{download(EPG,file,8*1024*1024);}catch(Exception e){if(!fresh(file,24*3600_000L))throw e;}
        Set<String> ids=new HashSet<>();for(Channel ch:channels)ids.add(ch.epgId);
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
    static void toggle(Context c,String source,String id){Set<String> chosen=favorites(c,source);if(!chosen.remove(id)){if(chosen.size()>=128)return;chosen.add(id);}prefs(c).edit().putStringSet("favorites-"+hash(source),chosen).apply();}
    static File cached(Context c,String name){File folder=new File(c.getCacheDir(),"live-tv");folder.mkdirs();return new File(folder,name);}
    static boolean fresh(File file,long limit){long age=System.currentTimeMillis()-file.lastModified();return file.isFile()&&age>=0&&age<limit;}
    static byte[] read(File file,int limit)throws IOException{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out,limit);return out.toByteArray();}}
    static void copy(InputStream in,OutputStream out,int limit)throws IOException{byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if((total+=n)>limit)throw new IOException("Response too large");out.write(buffer,0,n);}}
    static HttpURLConnection connect(String address)throws Exception{
        for(int i=0;i<5;i++){
            if(!https(address))throw new IOException("HTTPS required");HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Vela/1.17");
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
