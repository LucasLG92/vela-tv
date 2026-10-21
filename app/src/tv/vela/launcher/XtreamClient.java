package tv.vela.launcher;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;

/** User-owned Xtream live TV. Streamed pages, device-key encryption, no bundled account. */
final class XtreamClient {
    static final String PACKAGE="ar.tvplayer.tv",KEY="vela.xtream.v1";
    static final int MAX_CATEGORIES=512,MAX_FAVORITES=128,MAX_STREAMS=100_000;
    private final Context context;
    private final SharedPreferences prefs;
    XtreamClient(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("xtream",Context.MODE_PRIVATE);}
    static final class Account {
        final String base,user,password,id;final boolean hls,ts;
        Account(String b,String u,String p,String i,boolean h,boolean t){base=b;user=u;password=p;id=i;hls=h;ts=t;}
        JSONObject json()throws Exception{return new JSONObject().put("base",base).put("user",user).put("password",password).put("id",id).put("hls",hls).put("ts",ts);}
    }
    static final class Category {
        final String id,name;Category(String i,String n){id=i;name=n;}
    }
    static final class Page {
        final List<LiveTvData.Channel> channels;final int page,total;
        Page(List<LiveTvData.Channel> c,int p,int t){channels=Collections.unmodifiableList(c);page=p;total=t;}
        boolean more(){return (page+1)*LiveTvData.PAGE_SIZE<total;}
    }
    boolean connected(){return !prefs.getString("account","").isEmpty()&&!prefs.getString("id","").isEmpty();}
    String source(){return "xtream:"+prefs.getString("id","");}
    boolean active(){return connected()&&LiveTvData.prefs(context).getBoolean("xtream",false);}
    Account account()throws Exception{
        JSONObject j=new JSONObject(decrypt(prefs.getString("account","")));Account a=new Account(j.getString("base"),j.getString("user"),j.getString("password"),j.getString("id"),j.optBoolean("hls",true),j.optBoolean("ts",true));
        if(!a.id.equals(prefs.getString("id","")))throw new IOException("Account unavailable");return a;
    }
    static String server(String input,boolean allowHttp)throws Exception{
        String value=input.trim();if(value.length()>2048||value.indexOf('\\')>=0||value.matches("(?s).*[\\x00-\\x20\\x7f].*"))throw new IOException("Invalid server");
        URI u=new URI(value);if((!"https".equalsIgnoreCase(u.getScheme())&&!(allowHttp&&"http".equalsIgnoreCase(u.getScheme())))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getPort()==0||u.getPort()>65535)throw new IOException("Invalid server");
        String path=u.getRawPath()==null?"":u.getRawPath();if(path.toLowerCase(Locale.ROOT).matches(".*(%2e|%2f|%5c).*"))throw new IOException("Invalid server path");
        for(String part:path.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Invalid server path");
        value=u.toASCIIString();while(value.endsWith("/"))value=value.substring(0,value.length()-1);if(value.endsWith("/player_api.php"))value=value.substring(0,value.length()-15);return value;
    }
    static boolean credential(String value){return value!=null&&!value.isEmpty()&&value.length()<=512&&!value.matches("(?s).*[\\x00-\\x1f\\x7f].*");}
    static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}
    private static String endpoint(Account a,String action,String suffix)throws Exception{return a.base+"/player_api.php?username="+enc(a.user)+"&password="+enc(a.password)+(action.isEmpty()?"":"&action="+action)+suffix;}
    Account login(String base,String user,String password,boolean allowHttp)throws Exception{
        if(!credential(user)||!credential(password))throw new IOException("Missing credentials");Account draft=new Account(server(base,allowHttp),user,password,UUID.randomUUID().toString(),true,true);
        JSONObject j=json(draft,"", "",128*1024);JSONObject info=j.optJSONObject("user_info");
        if(info==null||info.optInt("auth")!=1||!"Active".equalsIgnoreCase(info.optString("status")))throw new IOException("Account not active");
        JSONArray formats=info.optJSONArray("allowed_output_formats");boolean h=formats==null||formats.length()==0,t=h;
        for(int i=0;formats!=null&&i<formats.length();i++){String f=formats.optString(i);h|=f.equalsIgnoreCase("m3u8");t|=f.equalsIgnoreCase("ts");}
        if(!h&&!t)throw new IOException("Unsupported output");return new Account(draft.base,user,password,draft.id,h,t);
    }
    synchronized void save(Account a)throws Exception{
        String encrypted=encrypt(a.json().toString());if(!prefs.edit().clear().putString("account",encrypted).putString("id",a.id).commit())throw new IOException("Cannot save account");
        activate();
    }
    void activate(){LiveTvData.prefs(context).edit().putBoolean("xtream",true).apply();}
    synchronized void disconnect(){prefs.edit().clear().commit();LiveTvData.prefs(context).edit().putBoolean("xtream",false).apply();}
    private javax.crypto.SecretKey key()throws Exception{
        synchronized(XtreamClient.class){KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(KEY))return (javax.crypto.SecretKey)store.getKey(KEY,null);
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();}
    }
    private String encrypt(String value)throws Exception{if(value.getBytes(StandardCharsets.UTF_8).length>160*1024)throw new IOException("Private data limit");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+"."+Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    private String decrypt(String value)throws Exception{if(value.length()>256*1024)throw new IOException("Private data limit");String[] parts=value.split("\\.");if(parts.length!=2)throw new IOException("Private data unavailable");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
    static boolean id(String value){return value!=null&&value.matches("[0-9]{1,18}");}
    private static String limited(String s,int n){return s.length()>n?s.substring(0,n):s;}
    private static String string(JsonReader r)throws IOException{if(r.peek()==JsonToken.NULL){r.nextNull();return "";}if(r.peek()==JsonToken.STRING||r.peek()==JsonToken.NUMBER)return r.nextString();r.skipValue();return "";}
    List<Category> categories()throws Exception{
        Account a=account();try(Response response=new Response(endpoint(a,"get_live_categories",""),1024*1024)){JsonReader r=response.reader;List<Category> out=new ArrayList<>();Set<String> seen=new HashSet<>();r.beginArray();int count=0;
            while(r.hasNext()){if(++count>MAX_CATEGORIES)throw new IOException("Category limit");String id="",name="";r.beginObject();while(r.hasNext()){String k=r.nextName();if(k.equals("category_id"))id=string(r);else if(k.equals("category_name"))name=limited(string(r),120);else r.skipValue();}r.endObject();if(id(id)&&!name.trim().isEmpty()&&seen.add(id))out.add(new Category(id,name));}r.endArray();if(out.isEmpty())throw new IOException("No live categories");return out;}
    }
    Page page(String category,String name,int page)throws Exception{
        if(!id(category)||page<0||page>MAX_STREAMS/LiveTvData.PAGE_SIZE)throw new IOException("Invalid page");Account a=account();List<LiveTvData.Channel> out=new ArrayList<>();int total=0,count=0,offset=page*LiveTvData.PAGE_SIZE;
        try(Response response=new Response(endpoint(a,"get_live_streams","&category_id="+enc(category)),16*1024*1024)){JsonReader r=response.reader;r.beginArray();
            while(r.hasNext()){if(++count>MAX_STREAMS)throw new IOException("Channel limit");String id="",title="",logo="",type="live";r.beginObject();while(r.hasNext()){String k=r.nextName();if(k.equals("stream_id"))id=string(r);else if(k.equals("name"))title=limited(string(r),160);else if(k.equals("stream_icon"))logo=limited(string(r),4096);else if(k.equals("stream_type"))type=string(r);else r.skipValue();}r.endObject();
                if(!id(id)||title.trim().isEmpty()||!type.equals("live"))continue;if(total>=offset&&out.size()<LiveTvData.PAGE_SIZE)out.add(channel(a,id,title,name,safeLogo(a,logo)));total++;
            }r.endArray();}
        return new Page(out,page,total);
    }
    private static LiveTvData.Channel channel(Account a,String id,String name,String group,String logo){return new LiveTvData.Channel("xc-"+id,name,group,logo,"",Collections.emptyList(),id,a.id);}
    static String safeLogo(Account a,String url){
        try{URI u=new URI(url);if(url.length()>4096||u.getHost()==null||u.getUserInfo()!=null||url.indexOf('\\')>=0||!(u.getScheme().equalsIgnoreCase("https")||a.base.startsWith("http:")&&u.getScheme().equalsIgnoreCase("http")))return "";
            URI base=new URI(a.base);if(!u.getHost().equalsIgnoreCase(base.getHost())){String decoded=URLDecoder.decode(url,"UTF-8").toLowerCase(Locale.ROOT);if(decoded.matches(".*[?&](username|password|token|auth|pass)=.*")||a.password.length()>=4&&decoded.contains(a.password.toLowerCase(Locale.ROOT)))return "";}return url;
        }catch(Exception invalid){return "";}
    }
    List<String> streams(LiveTvData.Channel ch)throws Exception{
        Account a=account();if(!a.id.equals(ch.xtreamAccount)||!id(ch.xtreamId))throw new IOException("Account changed");String base=a.base+"/live/"+enc(a.user)+"/"+enc(a.password)+"/"+ch.xtreamId;List<String> urls=new ArrayList<>();if(a.hls)urls.add(base+".m3u8");if(a.ts)urls.add(base+".ts");return urls;
    }
    Map<String,LiveTvData.Guide> guide(List<LiveTvData.Channel> channels)throws Exception{
        Account a=account();Map<String,LiveTvData.Guide> out=new HashMap<>();long deadline=System.nanoTime()+25_000_000_000L,now=System.currentTimeMillis()/1000;int count=0;
        for(LiveTvData.Channel ch:channels){if(++count>LiveTvData.PAGE_SIZE||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())break;if(!a.id.equals(ch.xtreamAccount)||!id(ch.xtreamId))continue;
            try{JSONObject data=json(a,"get_short_epg","&stream_id="+ch.xtreamId+"&limit=2",64*1024);JSONArray listings=data.optJSONArray("epg_listings");LiveTvData.Guide g=new LiveTvData.Guide();for(int i=0;listings!=null&&i<Math.min(8,listings.length());i++){JSONObject p=listings.getJSONObject(i);long start=p.optLong("start_timestamp"),end=p.optLong("stop_timestamp",p.optLong("end_timestamp"));g.add(new LiveTvData.Programme(start,end,title(p.optString("title"))),now);}if(g.now!=null||g.next!=null)out.put(ch.id,g);}catch(Exception unavailable){if(Thread.currentThread().isInterrupted())break;}
        }return out;
    }
    static String title(String value){if(value.length()>4096)return "";try{if(value.length()%4==0&&value.matches("[A-Za-z0-9+/]+={0,2}")){String decoded=new String(Base64.decode(value,Base64.DEFAULT),StandardCharsets.UTF_8);if(decoded.indexOf('\uFFFD')<0&&!decoded.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f].*"))return limited(decoded,512);}}catch(Exception ignored){}return limited(value,512);}
    private JSONObject json(Account a,String action,String suffix,int limit)throws Exception{try(Response response=new Response(endpoint(a,action,suffix),limit);ByteArrayOutputStream out=new ByteArrayOutputStream()){LiveTvData.copy(response.in,out,limit);return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}
    private List<LiveTvData.Channel> favorites()throws Exception{
        if(!prefs.contains("favorites"))return new ArrayList<>();Account a=account();JSONArray values=new JSONArray(decrypt(prefs.getString("favorites","")));if(values.length()>MAX_FAVORITES)throw new IOException("Favorite limit");List<LiveTvData.Channel> out=new ArrayList<>();for(int i=0;i<values.length();i++){JSONObject j=values.getJSONObject(i);String id=j.optString("id");if(id(id))out.add(channel(a,id,limited(j.optString("name"),160),limited(j.optString("group"),120),safeLogo(a,j.optString("logo"))));}return out;
    }
    Set<String> favoriteIds(){Set<String> out=new LinkedHashSet<>();try{for(LiveTvData.Channel ch:favorites())out.add(ch.id);}catch(Exception unavailable){}return out;}
    Page favoritePage(int requested)throws Exception{List<LiveTvData.Channel> all=favorites();int page=Math.max(0,Math.min(requested,Math.max(0,(all.size()-1)/LiveTvData.PAGE_SIZE))),start=page*LiveTvData.PAGE_SIZE;return new Page(new ArrayList<>(all.subList(start,Math.min(start+LiveTvData.PAGE_SIZE,all.size()))),page,all.size());}
    void toggleFavorite(LiveTvData.Channel ch)throws Exception{
        Account a=account();if(!a.id.equals(ch.xtreamAccount))throw new IOException("Account changed");List<LiveTvData.Channel> all=favorites();boolean removed=all.removeIf(x->x.id.equals(ch.id));if(!removed){if(all.size()>=MAX_FAVORITES)throw new IOException("Favorite limit");all.add(ch);}JSONArray values=new JSONArray();for(LiveTvData.Channel item:all)values.put(new JSONObject().put("id",item.xtreamId).put("name",item.name).put("group",item.group).put("logo",item.logo));if(!prefs.edit().putString("favorites",encrypt(values.toString())).commit())throw new IOException("Cannot save favorites");
    }
    static final class Response implements AutoCloseable {
        final HttpURLConnection connection;final InputStream in;final JsonReader reader;
        Response(String url,int limit)throws Exception{
            connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(6000);connection.setRequestProperty("User-Agent","Vela");
            try{if(connection.getResponseCode()!=200||connection.getContentLengthLong()>limit)throw new IOException("Provider unavailable");long deadline=System.nanoTime()+25_000_000_000L;
                in=new FilterInputStream(connection.getInputStream()){int bytes;private void check(int n)throws IOException{if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new InterruptedIOException();if(n>0&&(bytes+=n)>limit)throw new IOException("Response limit");}public int read()throws IOException{check(0);int n=super.read();check(n<0?0:1);return n;}public int read(byte[] b,int o,int l)throws IOException{check(0);int n=super.read(b,o,l);check(n);return n;}};reader=new JsonReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            }catch(Exception failure){connection.disconnect();throw new IOException("Provider unavailable");}
        }
        public void close(){try{in.close();}catch(Exception ignored){}connection.disconnect();}
    }
    static void logo(String url,File file)throws Exception{
        // No account headers; redirects are deliberately not followed for private provider artwork.
        try(Response response=new Response(url,512*1024);OutputStream out=new FileOutputStream(file)){LiveTvData.copy(response.in,out,512*1024);}catch(Exception unavailable){file.delete();throw new IOException("Logo unavailable");}
    }
}
