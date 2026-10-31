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
    static final String KEY="vela.xtream.v1";
    static final int MAX_CATEGORIES=512,MAX_FAVORITES=128,MAX_STREAMS=100_000;
    private final Context context;
    private final SharedPreferences prefs;
    private final TvCatalogCache cache;
    private final android.util.LruCache<String,GuideEntry> guideCache=new android.util.LruCache<>(24);
    private static final class GuideEntry {final LiveTvData.Guide guide;final long at;GuideEntry(LiveTvData.Guide g){guide=g;at=System.currentTimeMillis();}}
    XtreamClient(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("xtream",Context.MODE_PRIVATE);cache=new TvCatalogCache(context);}
    static final class Account {
        final String base,user,password,id;String zone="UTC";final boolean hls,ts;
        Account(String b,String u,String p,String i,boolean h,boolean t){base=b;user=u;password=p;id=i;hls=h;ts=t;}
        JSONObject json()throws Exception{return new JSONObject().put("base",base).put("user",user).put("password",password).put("id",id).put("hls",hls).put("ts",ts).put("zone",zone);}
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
    String source(){if(connected()&&!prefs.contains("portable-source"))try{account();}catch(Exception unavailable){}return "xtream:"+prefs.getString("id","");}
    private String favoriteKey(){String source=source(),key="favorites-"+LiveTvData.hash(VelaWorkspace.channelSource(context,source)),legacy="favorites-"+LiveTvData.hash(source);SharedPreferences local=VelaWorkspace.prefs(context,"live-tv");if(!key.equals(legacy)&&local.contains(legacy)){SharedPreferences.Editor edit=local.edit();if(!local.contains(key))edit.putStringSet(key,new LinkedHashSet<>(local.getStringSet(legacy,Collections.emptySet())));edit.remove(legacy).commit();}return key;}
    boolean active(){SharedPreferences p=LiveTvData.prefs(context);return connected()&&p.getString("mode",p.getBoolean("xtream",false)?"xtream":"m3u").equals("xtream");}
    Account account()throws Exception{
        JSONObject j=new JSONObject(decrypt(prefs.getString("account","")));Account a=new Account(j.getString("base"),j.getString("user"),j.getString("password"),j.getString("id"),j.optBoolean("hls",true),j.optBoolean("ts",true));
        a.zone=j.optString("zone","UTC");if(!a.id.equals(prefs.getString("id","")))throw new IOException("Account unavailable");if(!prefs.contains("portable-source"))prefs.edit().putString("portable-source",LiveTvData.hash(a.base+"|"+a.user)).commit();return a;
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
        if(!h&&!t)throw new IOException("Unsupported output");Account result=new Account(draft.base,user,password,draft.id,h,t);JSONObject serverInfo=j.optJSONObject("server_info");String zone=serverInfo==null?"UTC":serverInfo.optString("timezone","UTC");if(Arrays.asList(TimeZone.getAvailableIDs()).contains(zone))result.zone=zone;return result;
    }
    synchronized void save(Account a)throws Exception{synchronized(VelaWorkspace.class){
        String encrypted=encrypt(a.json().toString());if(!prefs.edit().clear().putString("account",encrypted).putString("id",a.id).putString("portable-source",LiveTvData.hash(a.base+"|"+a.user)).commit())throw new IOException("Cannot save account");
        activate();
        }
    }
    void activate(){LiveTvData.select(context,"xtream");}
    synchronized void disconnect(){synchronized(VelaWorkspace.class){String id=prefs.getString("id","");prefs.edit().clear().commit();guideCache.evictAll();cache.removePrefix("xc:"+id+":");LiveTvData.select(context,"m3u");}}
    private javax.crypto.SecretKey key()throws Exception{
        synchronized(XtreamClient.class){KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(KEY))return (javax.crypto.SecretKey)store.getKey(KEY,null);
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();}
    }
    private String encrypt(String value)throws Exception{if(value.getBytes(StandardCharsets.UTF_8).length>160*1024)throw new IOException("Private data limit");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+"."+Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    private String decrypt(String value)throws Exception{if(value.length()>256*1024)throw new IOException("Private data limit");String[] parts=value.split("\\.");if(parts.length!=2)throw new IOException("Private data unavailable");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
    static boolean id(String value){return value!=null&&value.matches("[0-9]{1,18}");}
    private static String limited(String s,int n){return s.length()>n?s.substring(0,n):s;}
    private static String string(JsonReader r)throws IOException{if(r.peek()==JsonToken.BOOLEAN)return r.nextBoolean()?"1":"0";if(r.peek()==JsonToken.NULL){r.nextNull();return "";}if(r.peek()==JsonToken.STRING||r.peek()==JsonToken.NUMBER)return r.nextString();r.skipValue();return "";}
    List<Category> categories()throws Exception{return categories(false);}
    List<Category> categories(boolean force)throws Exception{
        long age=System.currentTimeMillis()-prefs.getLong("categories-at",0);String saved=prefs.getString("categories-cache","");
        if(!force&&age>=0&&age<15*60_000&&!saved.isEmpty())try{return categoryList(new JSONArray(decrypt(saved)));}catch(Exception ignored){}
        Account a=account();try(Response response=new Response(endpoint(a,"get_live_categories",""),1024*1024)){
            JsonReader r=response.reader;JSONArray values=new JSONArray();r.beginArray();int count=0;
            while(r.hasNext()){if(++count>MAX_CATEGORIES)throw new IOException("Category limit");String id="",name="";r.beginObject();while(r.hasNext()){String k=r.nextName();if(k.equals("category_id"))id=string(r);else if(k.equals("category_name"))name=limited(string(r),120);else r.skipValue();}r.endObject();if(id(id)&&!name.trim().isEmpty())values.put(new JSONObject().put("id",id).put("name",name));}r.endArray();
            List<Category> out=categoryList(values);prefs.edit().putString("categories-cache",encrypt(values.toString())).putLong("categories-at",System.currentTimeMillis()).apply();return out;
        }catch(Exception failure){if(!Thread.currentThread().isInterrupted()&&age>=0&&age<7*86400_000L&&!saved.isEmpty())return categoryList(new JSONArray(decrypt(saved)));throw failure;}
    }
    private static List<Category> categoryList(JSONArray values)throws Exception{
        if(values.length()>MAX_CATEGORIES)throw new IOException("Category limit");List<Category> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<values.length();i++){JSONObject v=values.getJSONObject(i);String id=v.getString("id"),name=v.getString("name");if(id(id)&&!name.isEmpty()&&seen.add(id))out.add(new Category(id,limited(name,120)));}if(out.isEmpty())throw new IOException("No live categories");return out;
    }
    Page page(String category,String name,int page)throws Exception{return page(category,name,page,"",false);}
    Page page(String category,String name,int requested,String query,boolean force)throws Exception{
        if(!(id(category)||category.equals("*"))||requested<0||requested>MAX_STREAMS/LiveTvData.PAGE_SIZE)throw new IOException("Invalid page");
        Account a=account();String source="xc:"+a.id+":"+category;
        if(force||!cache.fresh(source,15*60_000)){
            try{Map<String,String> names=new HashMap<>();if(category.equals("*"))for(Category item:categories())names.put(item.id,item.name);
                try(Response response=new Response(endpoint(a,"get_live_streams",category.equals("*")?"":"&category_id="+enc(category)),16*1024*1024);TvCatalogCache.Writer writer=cache.writer(source,response.connection.getContentLengthLong())){
                    JsonReader r=response.reader;r.beginArray();int count=0;long importDeadline=System.nanoTime()+120_000_000_000L;
                    while(r.hasNext()){
                        if(Thread.currentThread().isInterrupted()||System.nanoTime()>importDeadline)throw new InterruptedIOException("Import cancelled or timed out");if(++count>MAX_STREAMS)throw new IOException("Channel limit");String id="",title="",logo="",group=category,type="live",archive="",archiveDays="";
                        r.beginObject();while(r.hasNext()){String k=r.nextName();if(k.equals("stream_id"))id=string(r);else if(k.equals("name"))title=limited(string(r),160);else if(k.equals("stream_icon"))logo=limited(string(r),4096);else if(k.equals("category_id"))group=string(r);else if(k.equals("stream_type"))type=string(r);else if(k.equals("tv_archive"))archive=string(r);else if(k.equals("tv_archive_duration"))archiveDays=string(r);else r.skipValue();}r.endObject();
                        if(id(id)&&!title.trim().isEmpty()&&type.equals("live")){LiveTvData.Channel ch=channel(a,id,title,category.equals("*")?names.getOrDefault(group,name):name,safeLogo(a,logo));try{if(archive.equals("1"))ch.archiveDays=Math.max(0,Math.min(7,Integer.parseInt(archiveDays)));}catch(NumberFormatException absent){}writer.add(ch,false);}
                    }r.endArray();writer.commit("{}");
                }
            }catch(Exception failure){if(Thread.currentThread().isInterrupted()||!cache.fresh(source,7*86400_000L))throw failure;}
        }
        TvCatalogCache.Page result=cache.page(source,null,query,null,requested);return new Page(result.channels,result.page,result.total);
    }
    private static LiveTvData.Channel channel(Account a,String id,String name,String group,String logo){return new LiveTvData.Channel("xc-"+id,name,group,logo,Collections.emptyList(),id,a.id);}
    static String safeLogo(Account a,String url){
        try{URI u=new URI(url);if(url.length()>4096||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||u.getPort()==0||u.getPort()>65535||url.indexOf('\\')>=0||url.matches("(?s).*[\\x00-\\x20\\x7f].*")||!("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme())))return "";
            URI base=new URI(a.base);boolean sameOrigin=u.getScheme().equalsIgnoreCase(base.getScheme())&&u.getHost().equalsIgnoreCase(base.getHost())&&port(u)==port(base);
            // Public artwork may use HTTP even when account requests use HTTPS. Never send account
            // details to a different origin or downgrade them to a public image connection.
            if(!sameOrigin){String decoded=URLDecoder.decode(url,"UTF-8").toLowerCase(Locale.ROOT);if(decoded.matches(".*[?&](username|password|token|auth|pass)=.*")||a.password.length()>=4&&decoded.contains(a.password.toLowerCase(Locale.ROOT))||a.user.length()>=4&&decoded.contains(a.user.toLowerCase(Locale.ROOT)))return "";}return url;
        }catch(Exception invalid){return "";}
    }
    private static int port(URI uri){return uri.getPort()<0?("https".equalsIgnoreCase(uri.getScheme())?443:80):uri.getPort();}
    List<String> streams(LiveTvData.Channel ch)throws Exception{
        Account a=account();if(!a.id.equals(ch.xtreamAccount)||!id(ch.xtreamId))throw new IOException("Account changed");String base=a.base+"/live/"+enc(a.user)+"/"+enc(a.password)+"/"+ch.xtreamId;List<String> urls=new ArrayList<>();if(a.hls)urls.add(base+".m3u8");if(a.ts)urls.add(base+".ts");return urls;
    }
    Map<String,LiveTvData.Guide> guide(List<LiveTvData.Channel> channels)throws Exception{
        Account a=account();Map<String,LiveTvData.Guide> out=new HashMap<>();long deadline=System.nanoTime()+25_000_000_000L,now=System.currentTimeMillis()/1000;int count=0;
        for(LiveTvData.Channel ch:channels){if(++count>LiveTvData.PAGE_SIZE||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())break;if(!a.id.equals(ch.xtreamAccount)||!id(ch.xtreamId))continue;
            try{GuideEntry cached=guideCache.get(a.id+":"+ch.xtreamId);if(cached!=null&&System.currentTimeMillis()-cached.at<3*60_000&&cached.guide.current(now)!=null){out.put(ch.id,cached.guide);continue;}JSONObject data=json(a,"get_short_epg","&stream_id="+ch.xtreamId+"&limit=2",64*1024);JSONArray listings=data.optJSONArray("epg_listings");LiveTvData.Guide g=new LiveTvData.Guide();for(int i=0;listings!=null&&i<Math.min(8,listings.length());i++){JSONObject p=listings.getJSONObject(i);long start=p.optLong("start_timestamp"),end=p.optLong("stop_timestamp",p.optLong("end_timestamp"));g.add(new LiveTvData.Programme(start,end,title(p.optString("title"))),now);}if(g.now!=null||g.next!=null){out.put(ch.id,g);guideCache.put(a.id+":"+ch.xtreamId,new GuideEntry(g));}}catch(Exception unavailable){if(Thread.currentThread().isInterrupted())break;}
        }return out;
    }
    static String title(String value){if(value.length()>4096)return "";try{if(value.length()%4==0&&value.matches("[A-Za-z0-9+/]+={0,2}")){String decoded=new String(Base64.decode(value,Base64.DEFAULT),StandardCharsets.UTF_8);if(decoded.indexOf('\uFFFD')<0&&!decoded.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f].*"))return limited(decoded,512);}}catch(Exception ignored){}return limited(value,512);}
    private JSONObject json(Account a,String action,String suffix,int limit)throws Exception{try(Response response=new Response(endpoint(a,action,suffix),limit);ByteArrayOutputStream out=new ByteArrayOutputStream()){LiveTvData.copy(response.in,out,limit);return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}
    private List<LiveTvData.Channel> favorites()throws Exception{synchronized(VelaWorkspace.class){
        Account a=account();String key=favoriteKey(),detailKey="items-"+LiveTvData.hash(source());SharedPreferences local=VelaWorkspace.prefs(context,"live-tv"),details=VelaWorkspace.prefs(context,"xtream-favorites");String stored=details.getString(detailKey,"");if(stored.isEmpty()&&VelaWorkspace.active(context).equals(VelaWorkspace.DEFAULT))stored=prefs.getString("favorites","");
        Set<String> ids=local.contains(key)?new LinkedHashSet<>(local.getStringSet(key,Collections.emptySet())):null;List<LiveTvData.Channel> out=new ArrayList<>();Set<String> found=new HashSet<>();
        if(!stored.isEmpty()){JSONArray values=new JSONArray(decrypt(stored));if(values.length()>MAX_FAVORITES)throw new IOException("Favorite limit");for(int i=0;i<values.length();i++){JSONObject j=values.getJSONObject(i);String id=j.optString("id");if(id(id)&&(ids==null||ids.contains("xc-"+id))&&found.add("xc-"+id))out.add(channel(a,id,limited(j.optString("name"),160),limited(j.optString("group"),120),safeLogo(a,j.optString("logo"))));}}
        if(ids==null){ids=new LinkedHashSet<>();for(LiveTvData.Channel ch:out)ids.add(ch.id);local.edit().putStringSet(key,ids).commit();}
        Set<String> missing=new LinkedHashSet<>(ids);missing.removeAll(found);for(LiveTvData.Channel ch:cache.favoritePrefix("xc:"+a.id+":",missing))if(found.add(ch.id))out.add(ch);
        return out;
        }
    }
    List<LiveTvData.Channel> favoriteItems()throws Exception{return favorites();}
    Set<String> favoriteIds(){Set<String> out=new LinkedHashSet<>();try{for(LiveTvData.Channel ch:favorites())out.add(ch.id);}catch(Exception unavailable){}return out;}
    Page favoritePage(int requested)throws Exception{return favoritePage(requested,"");}
    Page favoritePage(int requested,String query)throws Exception{Map<String,LiveTvData.Channel> unique=new LinkedHashMap<>();for(LiveTvData.Channel ch:favorites())if(query.isEmpty()||TvCatalogCache.normalize(ch.name).contains(TvCatalogCache.normalize(query)))unique.putIfAbsent(TvChannelNames.family(ch),ch);List<LiveTvData.Channel> all=new ArrayList<>(unique.values());TvPersonalization state=new TvPersonalization(context,source());Set<String> hidden=state.hiddenKeys();all.removeIf(ch->hidden.contains(TvChannelLibrary.key(ch)));Map<String,Integer> rank=new HashMap<>();List<String> order=state.order();for(int i=0;i<order.size();i++)rank.put(order.get(i),i);all.sort(Comparator.comparingInt(ch->rank.getOrDefault(TvChannelLibrary.key(ch),128)));int page=Math.max(0,Math.min(requested,Math.max(0,(all.size()-1)/LiveTvData.PAGE_SIZE))),start=page*LiveTvData.PAGE_SIZE;List<LiveTvData.Channel> out=new ArrayList<>();for(int i=start;i<Math.min(start+LiveTvData.PAGE_SIZE,all.size());i++)out.add(cache.favoriteVariant("xc:"+account().id+":",all.get(i)));return new Page(out,page,all.size());}
    void toggleFavorite(LiveTvData.Channel ch)throws Exception{synchronized(VelaWorkspace.class){
        Account a=account();if(!a.id.equals(ch.xtreamAccount))throw new IOException("Account changed");List<LiveTvData.Channel> all=favorites();boolean removed=all.removeIf(x->TvChannelNames.family(x).equals(TvChannelNames.family(ch)));if(!removed){if(all.size()>=MAX_FAVORITES)throw new IOException("Favorite limit");all.add(ch);}JSONArray values=new JSONArray();for(LiveTvData.Channel item:all)values.put(new JSONObject().put("id",item.xtreamId).put("name",item.name).put("group",item.group).put("logo",item.logo));Set<String> ids=new LinkedHashSet<>();for(LiveTvData.Channel item:all)ids.add(item.id);if(!VelaWorkspace.prefs(context,"live-tv").edit().putStringSet(favoriteKey(),ids).commit())throw new IOException("Cannot save favorites");if(!VelaWorkspace.prefs(context,"xtream-favorites").edit().putString("items-"+LiveTvData.hash(source()),encrypt(values.toString())).commit())throw new IOException("Cannot save favourites");if(VelaWorkspace.active(context).equals(VelaWorkspace.DEFAULT))prefs.edit().putString("favorites",encrypt(values.toString())).commit();
        }
    }
    static final class Response implements AutoCloseable {
        final HttpURLConnection connection;final InputStream in;final JsonReader reader;
        Response(String url,int limit)throws Exception{
            connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(6000);connection.setRequestProperty("User-Agent","Vela");
            try{if(connection.getResponseCode()!=200||connection.getContentLengthLong()>limit)throw new IOException("Provider unavailable");
                in=new NetworkBudget(connection.getInputStream(),limit,25_000_000_000L,System::nanoTime);reader=new JsonReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            }catch(Exception failure){connection.disconnect();throw new IOException("Provider unavailable");}
        }
        public void close(){try{in.close();}catch(Exception ignored){}connection.disconnect();}
    }
    /** Bound wire time independently of the CPU time spent encrypting and indexing each channel. */
    static final class NetworkBudget extends FilterInputStream {
        interface Clock {long now();}
        final Clock clock;final int limit;final long budget;private long spent,bytes;
        NetworkBudget(InputStream input,int limit,long budget,Clock clock){super(input);this.limit=limit;this.budget=budget;this.clock=clock;}
        private void check()throws IOException{if(Thread.currentThread().isInterrupted()||spent>=budget)throw new InterruptedIOException("Network budget exceeded");}
        private void finish(long start,int count)throws IOException{spent+=Math.max(0,clock.now()-start);if(count>0)bytes+=count;if(bytes>limit)throw new IOException("Response limit");check();}
        public int read()throws IOException{check();long start=clock.now();int n=in.read();finish(start,n<0?0:1);return n;}
        public int read(byte[] b,int offset,int length)throws IOException{check();long start=clock.now();int n=in.read(b,offset,length);finish(start,n);return n;}
        public long skip(long count)throws IOException{throw new IOException("Skipping provider data is unsupported");}
    }
    static void logo(String url,File file)throws Exception{
        // No account headers; redirects are deliberately not followed for private provider artwork.
        try(Response response=new Response(url,512*1024);OutputStream out=new FileOutputStream(file)){LiveTvData.copy(response.in,out,512*1024);}catch(Exception unavailable){file.delete();throw new IOException("Logo unavailable");}
    }
}
