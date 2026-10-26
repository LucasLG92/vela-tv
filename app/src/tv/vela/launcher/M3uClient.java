package tv.vela.launcher;

import android.content.*;
import android.util.Xml;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

/** Streaming M3U import, indexed local pages, optional bounded XMLTV. No website playback. */
final class M3uClient {
    private final Context context;private final TvCatalogCache cache;private final TvPrivateData secrets;
    private static final Pattern ATTRIBUTE=Pattern.compile("([\\w-]+)\\s*=\\s*([\"'])(.*?)\\2");
    M3uClient(Context c){context=c.getApplicationContext();cache=new TvCatalogCache(c);secrets=new TvPrivateData(c);}
    static String identity(Context c){SharedPreferences p=LiveTvData.prefs(c);String id=p.getString("m3u-id","");if(!id.isEmpty())return "m3u:"+id;String legacy=p.getString("source","").trim();return "m3u:"+LiveTvData.hash(legacy.isEmpty()||legacy.equals(LiveTvData.CATALOG)?LiveTvData.DEFAULT_M3U:legacy);}
    String url()throws Exception{SharedPreferences p=LiveTvData.prefs(context);String encoded=p.getString("m3u-private","");if(!encoded.isEmpty())return secrets.decode(encoded);String legacy=p.getString("source","").trim();return legacy.isEmpty()||legacy.equals(LiveTvData.CATALOG)?LiveTvData.DEFAULT_M3U:legacy;}
    String epgUrl()throws Exception{String value=LiveTvData.prefs(context).getString("m3u-epg","");return value.isEmpty()?"":secrets.decode(value);}
    boolean allowHttp(){return LiveTvData.prefs(context).getBoolean("m3u-http",false);}
    private static void valid(String value,boolean http)throws IOException{if(!LiveTvData.httpUrl(value)||!http&&!LiveTvData.https(value))throw new IOException("Invalid playlist URL");}
    void configure(String address,String epg,boolean http)throws Exception{
        save(address,epg,http,true);
    }
    private void save(String address,String epg,boolean http,boolean select)throws Exception{
        String value=address.trim(),guide=epg.trim();valid(value,http);if(!guide.isEmpty())valid(guide,http);
        SharedPreferences p=LiveTvData.prefs(context);String old=identity(context),next="m3u:"+LiveTvData.hash(value);
        SharedPreferences.Editor e=p.edit().putString("m3u-private",secrets.encode(value)).putString("m3u-id",LiveTvData.hash(value)).putString("m3u-epg",guide.isEmpty()?"":secrets.encode(guide)).putBoolean("m3u-http",http).remove("source");
        if(old.equals(next)&&!p.contains("m3u-private")){Set<String> favorites=LiveTvData.favorites(context,LiveTvData.CATALOG);if(!favorites.isEmpty())e.putStringSet("favorites-"+LiveTvData.hash(next),favorites);}
        if(!e.commit())throw new IOException("Could not save playlist");
        File[] legacy=LiveTvData.cached(context,"migration").getParentFile().listFiles((dir,name)->name.startsWith("catalog-")&&name.endsWith(".txt"));if(legacy!=null)for(File file:legacy)if(file.isFile())file.delete();
        if(select)LiveTvData.select(context,"m3u");
    }
    boolean refresh(boolean force)throws Exception{
        if(!LiveTvData.prefs(context).contains("m3u-private"))save(url(),epgUrl(),allowHttp(),false);
        String source=identity(context);if(!force&&cache.fresh(source,4*3600_000L))return false;
        try{String address=url();valid(address,allowHttp());String epg;
            try(XtreamClient.Response response=new XtreamClient.Response(address,32*1024*1024);TvCatalogCache.Writer writer=cache.writer(source,response.connection.getContentLengthLong())){
                epg=parse(new InputStreamReader(response.in,StandardCharsets.UTF_8),allowHttp(),writer);
                String custom=epgUrl();if(!custom.isEmpty())epg=custom;if(writer.size()==0)throw new IOException("No direct channels");writer.commit(new JSONObject().put("epg",epg).put("default",address.equals(LiveTvData.DEFAULT_M3U)).toString());
            }
            return false;
        }catch(Exception failure){if(Thread.currentThread().isInterrupted())throw failure;if(cache.fresh(source,7*86400_000L))return true;throw new IOException("Playlist unavailable");}
    }
    List<TvCatalogCache.Group> groups()throws Exception{return cache.groups(identity(context));}
    TvCatalogCache.Page page(String group,String query,Set<String> favorites,int page)throws Exception{return cache.page(identity(context),group,query,favorites,page);}
    private static String attribute(String line,String key){Matcher matcher=ATTRIBUTE.matcher(line);while(matcher.find())if(matcher.group(1).equalsIgnoreCase(key))return matcher.group(3);return "";}
    private static String limit(String value,int max){return value.length()>max?value.substring(0,max):value;}
    private static String line(Reader reader)throws IOException{StringBuilder value=new StringBuilder();int n;while((n=reader.read())!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(n=='\n')break;if(n!='\r'){if(value.length()>=16384)throw new IOException("Playlist line limit");value.append((char)n);}}return n<0&&value.length()==0?null:value.toString().trim();}
    static String parse(Reader input,boolean http,TvCatalogCache.Writer writer)throws Exception{
        BufferedReader reader=new BufferedReader(input,8192);String first=line(reader);if(first==null||!first.replace("\uFEFF","").startsWith("#EXTM3U"))throw new IOException("Not an M3U playlist");
        String epg=attribute(first,"x-tvg-url");if(epg.isEmpty())epg=attribute(first,"url-tvg");if(epg.contains(","))epg=epg.substring(0,epg.indexOf(','));if(!epg.isEmpty())try{valid(epg,http);}catch(Exception bad){epg="";}
        String defaultReplay=attribute(first,"catchup-source"),defaultMode=attribute(first,"catchup"),defaultDays=attribute(first,"catchup-days");String name="",id="",group="",logo="",replay="",replayMode="",days="",current;boolean unsupported=false;int entries=0;
        while((current=line(reader))!=null){
            if(current.startsWith("#EXTINF:")){
                if(++entries>TvCatalogCache.MAX_CHANNELS)throw new IOException("Channel limit");boolean quoted=false;char quote=0;int comma=-1;
                for(int i=0;i<current.length();i++){char ch=current.charAt(i);if(ch=='\"'||ch=='\''){if(!quoted){quoted=true;quote=ch;}else if(ch==quote)quoted=false;}if(ch==','&&!quoted){comma=i;break;}}
                name=limit(comma<0?attribute(current,"tvg-name"):current.substring(comma+1).trim(),160);id=limit(attribute(current,"tvg-id"),256);group=limit(attribute(current,"group-title"),120);logo=limit(attribute(current,"tvg-logo"),4096);if(!LiveTvData.httpUrl(logo))logo="";replay=limit(attribute(current,"catchup-source"),4096);replayMode=attribute(current,"catchup");days=attribute(current,"catchup-days");if(replay.isEmpty())replay=defaultReplay;if(replayMode.isEmpty())replayMode=defaultMode;if(days.isEmpty())days=defaultDays;unsupported=false;
            }else if(current.startsWith("#EXTGRP:"))group=limit(current.substring(8).trim(),120);
            else if(current.startsWith("#EXTV-streamtype:")||current.startsWith("#EXTV-headers:")||current.startsWith("#KODIPROP:"))unsupported=true;
            else if(!current.isEmpty()&&!current.startsWith("#")&&!name.isEmpty()){
                if(!unsupported&&LiveTvData.httpUrl(current)&&(http||LiveTvData.https(current))&&!current.contains("|")){
                    String channelId=id.isEmpty()?LiveTvData.hash(name+"|"+group):id;
                    LiveTvData.Channel ch=new LiveTvData.Channel(channelId,name,group.isEmpty()?Ui.tr("Minha lista","My playlist","Mi lista"):group,logo,Collections.singletonList(current),"","","","",id);if(!replay.isEmpty()&&Arrays.asList("default","append").contains(replayMode)){try{ch.archiveDays=Math.max(0,Math.min(7,Integer.parseInt(days)));}catch(NumberFormatException missing){ch.archiveDays=1;}ch.replayTemplate=replay;ch.replayMode=replayMode;}writer.add(ch,true);
                }name="";
            }
        }return epg;
    }
    Map<String,LiveTvData.Guide> guide(List<LiveTvData.Channel> channels)throws Exception{
        String data=cache.metadata(identity(context));if(data.isEmpty())return Collections.emptyMap();JSONObject metadata=new JSONObject(data);String url=metadata.optString("epg");
        if(url.isEmpty())return metadata.optBoolean("default")?index(channels,LiveTvData.publicGuide(context,channels)):Collections.emptyMap();valid(url,allowHttp());
        File file=LiveTvData.cached(context,"m3u-epg-"+LiveTvData.hash(url)+".data");
        if(!LiveTvData.fresh(file,2*3600_000L)){
            File part=File.createTempFile("fetch-",".part",file.getParentFile());try{
                try(XtreamClient.Response response=new XtreamClient.Response(url,8*1024*1024);OutputStream out=new FileOutputStream(part)){LiveTvData.copy(response.in,out,8*1024*1024);}
                java.nio.file.Files.move(part.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }catch(Exception failed){if(!LiveTvData.fresh(file,24*3600_000L))throw failed;}finally{part.delete();}
        }
        File[] guides=file.getParentFile().listFiles((dir,name)->name.startsWith("m3u-epg-")&&name.endsWith(".data"));if(guides!=null){Arrays.sort(guides,Comparator.comparingLong(File::lastModified).reversed());for(int i=2;i<guides.length;i++)if(!guides[i].equals(file))guides[i].delete();}
        Set<String> wanted=new HashSet<>();for(LiveTvData.Channel channel:channels)if(!channel.epgId.isEmpty())wanted.add(channel.epgId);
        if(wanted.isEmpty())return Collections.emptyMap();
        try(PushbackInputStream in=new PushbackInputStream(new FileInputStream(file),2)){
            int a=in.read(),b=in.read();if(b>=0)in.unread(b);if(a>=0)in.unread(a);InputStream decoded=a==31&&b==139?new GZIPInputStream(in):in;
            return index(channels,xmlGuide(new InputStreamReader(new LimitedInput(decoded,32*1024*1024),StandardCharsets.UTF_8),wanted,System.currentTimeMillis()/1000));
        }
    }
    static Map<String,LiveTvData.Guide> index(List<LiveTvData.Channel> channels,Map<String,LiveTvData.Guide> byEpg){Map<String,LiveTvData.Guide> result=new HashMap<>();for(LiveTvData.Channel ch:channels)if(byEpg.containsKey(ch.epgId))result.put(ch.id,byEpg.get(ch.epgId));return result;}
    static Map<String,LiveTvData.Guide> xmlGuide(Reader input,Set<String> wanted,long now)throws Exception{
        XmlPullParser p=Xml.newPullParser();p.setInput(input);Map<String,LiveTvData.Guide> result=new HashMap<>();String id="",title="";long start=0,end=0;boolean keep=false,inTitle=false;int events=0;
        for(int event=p.nextToken();event!=XmlPullParser.END_DOCUMENT;event=p.nextToken()){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(++events>1_000_000||event==XmlPullParser.DOCDECL)throw new IOException("Unsupported XMLTV");
            if(event==XmlPullParser.START_TAG&&p.getName().equals("programme")){id=p.getAttributeValue(null,"channel");keep=wanted.contains(id);title="";if(keep){start=timestamp(p.getAttributeValue(null,"start"));end=timestamp(p.getAttributeValue(null,"stop"));}}
            else if(keep&&event==XmlPullParser.START_TAG&&p.getName().equals("title"))inTitle=true;
            else if(keep&&inTitle&&(event==XmlPullParser.TEXT||event==XmlPullParser.ENTITY_REF)&&p.getText()!=null)title=limit(title+p.getText(),512);
            else if(event==XmlPullParser.END_TAG&&p.getName().equals("title"))inTitle=false;
            else if(event==XmlPullParser.END_TAG&&p.getName().equals("programme")){if(keep){LiveTvData.Guide entry=result.get(id);if(entry==null){entry=new LiveTvData.Guide();result.put(id,entry);}entry.add(new LiveTvData.Programme(start,end,title),now);}keep=false;inTitle=false;}
        }return result;
    }
    /** XMLTV's numeric timestamp and explicit offset, without a formatter/regex per programme. */
    static long timestamp(String value){try{if(value==null||value.length()>40)return 0;String text=value.trim();int n=0;while(n<text.length()&&text.charAt(n)>='0'&&text.charAt(n)<='9')n++;if(n<12||n>14)return 0;int year=number(text,0,4),month=number(text,4,2),day=number(text,6,2),hour=number(text,8,2),minute=number(text,10,2),second=n>12?(text.charAt(12)-'0')*10+(n==14?text.charAt(13)-'0':0):0;int at=n;while(at<text.length()&&Character.isWhitespace(text.charAt(at)))at++;int offset=0;if(at<text.length()){String zone=text.substring(at);if(!zone.equals("Z")){if(zone.length()!=5||zone.charAt(0)!='+'&&zone.charAt(0)!='-')return 0;int zh=number(zone,1,2),zm=number(zone,3,2);if(zm>59)return 0;offset=(zh*60+zm)*60*(zone.charAt(0)=='-'?-1:1);}}return java.time.LocalDateTime.of(year,month,day,hour,minute,second).toEpochSecond(java.time.ZoneOffset.ofTotalSeconds(offset));}catch(Exception invalid){return 0;}}
    private static int number(String value,int at,int count){int out=0;for(int i=at;i<at+count;i++){int digit=value.charAt(i)-'0';if(digit<0||digit>9)throw new IllegalArgumentException();out=out*10+digit;}return out;}
    static final class LimitedInput extends FilterInputStream {int remaining;LimitedInput(InputStream in,int limit){super(in);remaining=limit;}private void count(int n)throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(n>0&&(remaining-=n)<0)throw new IOException("Guide limit");}public int read()throws IOException{int n=super.read();count(n<0?0:1);return n;}public int read(byte[] b,int o,int l)throws IOException{int n=in.read(b,o,l);count(n);return n;}}
}
