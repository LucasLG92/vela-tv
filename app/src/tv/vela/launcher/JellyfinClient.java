package tv.vela.launcher;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** A user's Jellyfin session. Only the chosen server receives credentials; only explicit per-item watched writes. */
final class JellyfinClient {
    static final String PACKAGE="org.jellyfin.androidtv";
    private static final String FIELDS="Overview,DateCreated,PrimaryImageAspectRatio,ProviderIds";
    final SharedPreferences prefs;
    JellyfinClient(Context c){prefs=prefs(c);if(!prefs.contains("device"))prefs.edit().putString("device",UUID.randomUUID().toString()).commit();}
    static SharedPreferences prefs(Context c){return VelaAccountScope.prefs(c,"jellyfin");}
    boolean connected(){return !prefs.getString("token","").isEmpty()&&id(prefs.getString("userId","")).length()==32;}
    String name(){return prefs.getString("serverName","Jellyfin");}
    private static String identity(Session s){return s.server.base+"|"+s.userId+"|"+PlexClient.profileKey(s.token);}
    String identity(){return identity(stored());}
    // A single preferences snapshot prevents sending a new account's token to an old server.
    private Session stored(){Map<String,?> values=prefs.getAll();return new Session(new Server(value(values,"base"),value(values,"serverName"),value(values,"serverId")),value(values,"token"),value(values,"userId"),value(values,"userName"),value(values,"imageTag"));}
    private static String value(Map<String,?> values,String key){Object v=values.get(key);return v instanceof String?(String)v:"";}
    static String id(String value){String s=value==null?"":value.replace("-","");return s.matches("[0-9a-fA-F]{32}")?s.toLowerCase(Locale.ROOT):"";}
    static String guid(String value){String s=id(value);if(s.isEmpty())return "";return s.substring(0,8)+"-"+s.substring(8,12)+"-"+s.substring(12,16)+"-"+s.substring(16,20)+"-"+s.substring(20);}
    static String normalizeBase(String input)throws IOException{
        try{String s=input.trim();if(!s.contains("://"))s="https://"+s;URI uri=new URI(s);String scheme=uri.getScheme();
            if(s.length()>2048||!("https".equals(scheme)||"http".equals(scheme))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getPort()==0||uri.getPort()>65535)throw new IOException();
            String path=uri.getPath();if(path.contains("\\")||Arrays.asList(path.split("/")).contains("..")||Arrays.asList(path.split("/")).contains(".")||s.matches(".*[\\r\\n].*"))throw new IOException();
            return s.replaceAll("/+$","");
        }catch(Exception invalid){throw new IOException("Invalid Jellyfin server address");}
    }
    static boolean localAddress(InetAddress address){byte[] b=address.getAddress();return address.isLoopbackAddress()||address.isSiteLocalAddress()||address.isLinkLocalAddress()||(b.length==16&&(b[0]&0xfe)==0xfc);}
    static void validateAddress(String base)throws IOException{
        URL url=new URL(normalizeBase(base));if(url.getProtocol().equals("http")){InetAddress[] addresses=InetAddress.getAllByName(url.getHost());if(addresses.length==0)throw new IOException();for(InetAddress address:addresses)if(!localAddress(address))throw new IOException("HTTP is only allowed on the local network");}
    }
    static final class Server {final String base,name,serverId;Server(String b,String n,String id){base=b;name=n;serverId=id;}}
    static final class Session {final Server server;final String token,userId,userName,imageTag;Session(Server s,String token,String id,String name,String tag){server=s;this.token=token;userId=id;userName=name;imageTag=tag;}}
    Server server(String address)throws Exception{String base=normalizeBase(address);JSONObject info=json(base,"/System/Info/Public","",null);if(info.optString("Id").isEmpty()||info.optString("ServerName").isEmpty())throw new IOException("Not a Jellyfin server");return new Server(base,shortText(info.optString("ServerName"),180),shortText(info.optString("Id"),128));}
    boolean quickEnabled(Server server)throws Exception{return "true".equals(new String(bytes(server.base,"/QuickConnect/Enabled","",null,1024,false),StandardCharsets.UTF_8).trim());}
    JSONObject initiate(Server server)throws Exception{return json(server.base,"/QuickConnect/Initiate","",new JSONObject());}
    boolean authorized(Server server,String secret)throws Exception{return json(server.base,"/QuickConnect/Connect?secret="+encode(secret),"",null).optBoolean("Authenticated");}
    Session quickLogin(Server server,String secret)throws Exception{return session(server,json(server.base,"/Users/AuthenticateWithQuickConnect","",new JSONObject().put("Secret",secret)));}
    Session passwordLogin(Server server,String username,String password)throws Exception{return session(server,json(server.base,"/Users/AuthenticateByName","",new JSONObject().put("Username",username).put("Pw",password)));}
    private Session session(Server server,JSONObject result)throws IOException{
        JSONObject user=result.optJSONObject("User");String token=result.optString("AccessToken"),uid=user==null?"":id(user.optString("Id"));
        if(token.isEmpty()||token.length()>512||(token.indexOf(13)>=0||token.indexOf(10)>=0||token.indexOf(34)>=0||token.indexOf(92)>=0)||uid.isEmpty()||!server.serverId.equals(result.optString("ServerId",server.serverId)))throw new IOException("Invalid Jellyfin session");
        return new Session(server,token,uid,shortText(user.optString("Name"),160),shortText(user.optString("PrimaryImageTag"),128));
    }
    void save(Session session)throws IOException{String device=prefs.getString("device","");if(!prefs.edit().clear().putString("device",device).putString("base",session.server.base).putString("serverName",session.server.name).putString("serverId",session.server.serverId).putString("userId",session.userId).putString("userName",session.userName).putString("imageTag",session.imageTag).putString("token",session.token).commit())throw new IOException("Cannot save Jellyfin session");}
    void disconnect(){String device=prefs.getString("device","");prefs.edit().clear().putString("device",device).commit();}
    void logout()throws Exception{Session s=stored();if(!s.token.isEmpty())json(s.server.base,"/Sessions/Logout",s.token,new JSONObject());}
    private JSONObject authenticated(Session s,String path)throws Exception{if(s.token.isEmpty()||id(s.userId).isEmpty())throw new IOException("Jellyfin not linked");return json(s.server.base,path,s.token,null);}
    private JSONObject compatible(Session s,String current,String legacy)throws Exception{try{return authenticated(s,current);}catch(Status error){if(error.status!=404)throw error;return authenticated(s,legacy);}}
    List<PlexClient.Section> sections()throws Exception{Session s=stored();return parseSections(compatible(s,"/UserViews?userId="+s.userId,"/Users/"+s.userId+"/Views"));}
    static List<PlexClient.Section> parseSections(JSONObject root){
        List<PlexClient.Section> out=new ArrayList<>();Set<String> seen=new HashSet<>();JSONArray items=root.optJSONArray("Items");
        for(int i=0;items!=null&&i<Math.min(64,items.length());i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String key=id(item.optString("Id")),kind=item.optString("CollectionType"),name=shortText(item.optString("Name"),180);if(key.isEmpty()||!seen.add(key))continue;
            if(kind.equals("movies"))out.add(new PlexClient.Section(key+".movie",name,"movie"));else if(kind.equals("tvshows"))out.add(new PlexClient.Section(key+".show",name,"show"));else if(kind.isEmpty()||kind.equals("mixed")){out.add(new PlexClient.Section(key+".movie",name,"movie"));out.add(new PlexClient.Section(key+".show",name,"show"));}}
        return out;
    }
    PlexClient.LibraryPage page(PlexClient.Section section,int offset)throws Exception{return page(section,offset,new CatalogFilters.Query());}
    PlexClient.LibraryPage page(PlexClient.Section section,int offset,CatalogFilters.Query query)throws Exception{
        Session scope=stored();String[] key=section.key.split("\\.");if(offset<0||key.length!=2||id(key[0]).isEmpty()||!(key[1].equals("movie")||key[1].equals("show")))throw new IOException("Invalid library page");
        String path="/Items?userId="+scope.userId+"&parentId="+key[0]+"&recursive=true&includeItemTypes="+(key[1].equals("movie")?"Movie":"Series")+CatalogFilters.jellyfinQuery(query)+"&startIndex="+offset+"&limit="+PlexClient.PAGE_SIZE+"&fields="+FIELDS+"&enableImages=true&imageTypeLimit=1&enableUserData=true";
        JSONObject root=authenticated(scope,path);JSONArray items=root.optJSONArray("Items");int count=items==null?0:items.length();if(count>PlexClient.PAGE_SIZE||root.optInt("StartIndex",offset)!=offset)throw new IOException("Server did not respect pagination");
        return new PlexClient.LibraryPage(parse(items,false,false,PlexClient.PAGE_SIZE),offset,root.has("TotalRecordCount")?Math.max(0,root.optInt("TotalRecordCount")):-1,count);
    }
    CatalogFilters.Options filters(PlexClient.Section section)throws Exception{
        Session scope=stored();String[] key=section.key.split("\\.");if(key.length!=2||id(key[0]).isEmpty())throw new IOException("Invalid library");String query="?userId="+scope.userId+"&parentId="+key[0]+"&includeItemTypes="+(section.type.equals("show")?"Series":"Movie")+"&recursive=true";
        JSONObject legacy=authenticated(scope,"/Items/Filters"+query),modern;try{modern=authenticated(scope,"/Items/Filters2"+query);}catch(Status error){if(error.status!=404)throw error;modern=new JSONObject();}return CatalogFilters.jellyfinOptions(legacy,modern,section);
    }
    CatalogFilters.Choices choices(PlexClient.Section section,CatalogFilters.Facet facet,int offset,String search)throws Exception{
        if(!facet.fixed.isEmpty()||facet.path.isEmpty())return CatalogFilters.slice(facet.fixed,offset,search);Session scope=stored();String parent=section.key.split("\\.")[0];if(id(parent).isEmpty()||offset<0||search.length()>80||!Arrays.asList("/Studios","/Persons").contains(facet.path))throw new IOException("Invalid filter options");String path=facet.path+"?userId="+scope.userId+"&parentId="+parent+"&startIndex="+offset+"&limit="+CatalogFilters.CHOICES+"&enableImages=false&enableTotalRecordCount=true"+(facet.path.equals("/Studios")?"&includeItemTypes="+(section.type.equals("show")?"Series":"Movie"):"");if(!search.isEmpty())path+="&searchTerm="+CatalogFilters.encode(search);return CatalogFilters.jellyfinChoices(authenticated(scope,path),offset);
    }
    List<LibraryCollection> collections(int offset)throws Exception{if(offset<0)throw new IOException("Invalid collection page");Session s=stored();JSONObject root=authenticated(s,"/Items?userId="+s.userId+"&includeItemTypes=BoxSet&recursive=true&startIndex="+offset+"&limit=12&sortBy=SortName&sortOrder=Ascending&enableImages=false");JSONArray a=root.optJSONArray("Items");if(a!=null&&a.length()>12)throw new IOException("Collection page limit");List<LibraryCollection> out=new ArrayList<>();String account=PlexClient.profileKey(identity(s));for(int i=0;a!=null&&i<a.length();i++){JSONObject j=a.getJSONObject(i);if(j.optString("Type").equals("BoxSet"))out.add(new LibraryCollection("Jellyfin",id(j.getString("Id")),j.optString("Name"),account));}return out;}
    PlexClient.LibraryPage collectionItems(String key,int offset,int limit,String types)throws Exception{key=id(key);if(key.isEmpty()||offset<0||limit<1||limit>PlexClient.PAGE_SIZE||!Arrays.asList("Movie,Series","Season","Episode").contains(types))throw new IOException("Invalid collection");Session s=stored();JSONObject root=authenticated(s,"/Items?userId="+s.userId+"&parentId="+key+"&recursive=false&includeItemTypes="+types+"&startIndex="+offset+"&limit="+limit+"&fields="+FIELDS+"&enableImages=true&imageTypeLimit=1&enableUserData=true&sortBy=ParentIndexNumber,IndexNumber,SortName&sortOrder=Ascending");JSONArray a=root.optJSONArray("Items");int received=a==null?0:a.length();if(received>limit||root.optInt("StartIndex",offset)!=offset)throw new IOException("Child page limit");return new PlexClient.LibraryPage(parse(a,false,false,limit),offset,root.has("TotalRecordCount")?Math.max(0,root.optInt("TotalRecordCount")):-1,received);}
    PlexClient.LibraryPage children(PlexClient.Film parent,int offset)throws Exception{return collectionItems(parent.key,offset,PlexClient.PAGE_SIZE,parent.type.equals("show")?"Season":"Episode");}
    List<PlexClient.Film> library()throws Exception{
        Session scope=stored();String before=identity(scope),uid=scope.userId,query="&includeItemTypes=Movie,Episode&limit=24&fields="+FIELDS+"&enableImages=true&imageTypeLimit=1&enableUserData=true";
        JSONObject resume=compatible(scope,"/UserItems/Resume?userId="+uid+query,"/Users/"+uid+"/Items/Resume?"+query.substring(1));
        List<PlexClient.Film> out=parse(resume.optJSONArray("Items"),true,false,12);
        try{out=withNextEpisodes(out,authenticated(scope,"/Shows/NextUp?userId="+uid+"&limit=24&fields="+FIELDS+"&enableImages=true&imageTypeLimit=1&enableUserData=true&enableResumable=false&enableRewatching=false&enableTotalRecordCount=false"));}catch(Exception unavailable){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        String latest="&limit=24&fields="+FIELDS+"&includeItemTypes=Movie,Episode&groupItems=true&enableImages=true&imageTypeLimit=1&enableUserData=true";
        byte[] bytes;try{bytes=bytes(scope.server.base,"/Items/Latest?userId="+uid+latest,scope.token,null,2*1024*1024,false);}catch(Status error){if(error.status!=404)throw error;bytes=bytes(scope.server.base,"/Users/"+uid+"/Items/Latest?"+latest.substring(1),scope.token,null,2*1024*1024,false);}
        List<PlexClient.Film> recent=parse(new JSONArray(new String(bytes,StandardCharsets.UTF_8)),false,true,24);recent.sort((a,b)->Long.compare(b.addedAtMs,a.addedAtMs));out.addAll(recent.subList(0,Math.min(12,recent.size())));
        if(before.equals(identity()))prefs.edit().putString("cache",cache(out).toString()).apply();return out;
    }
    static List<PlexClient.Film> withNextEpisodes(List<PlexClient.Film> resume,JSONObject response)throws IOException{
        JSONArray items=response.optJSONArray("Items");if(items!=null&&items.length()>24)throw new IOException("Too many next episodes");List<PlexClient.Film> out=new ArrayList<>();Set<String> keys=new HashSet<>(),series=new HashSet<>();
        for(PlexClient.Film film:resume){if(out.size()==12)break;if(!keys.add(film.key)||(!film.seriesKey.isEmpty()&&!series.add(film.seriesKey)))continue;out.add(film);}
        for(int i=0;items!=null&&i<items.length()&&out.size()<12;i++){JSONObject item=items.optJSONObject(i);if(item==null||!item.optString("Type").equals("Episode"))continue;JSONObject data=item.optJSONObject("UserData");if(data!=null&&(data.optBoolean("Played")||data.optLong("PlaybackPositionTicks")>0))continue;PlexClient.Film film=parseFilm(item,false,false);if(film==null||film.seriesKey.isEmpty()||!keys.add(film.key)||!series.add(film.seriesKey))continue;film.continuing=true;film.nextEpisode=true;film.positionMs=0;film.progress=film.durationMs>0?0:-1;out.add(film);}return out;
    }
    static String watchedPath(String item,String user)throws IOException{if(id(item).isEmpty()||id(user).isEmpty())throw new IOException("Invalid Jellyfin item");return "/UserPlayedItems/"+id(item)+"?userId="+id(user);}
    void markWatched(String item,String expectedIdentity)throws Exception{Session scope=stored();String path=watchedPath(item,scope.userId);if(scope.token.isEmpty()||!expectedIdentity.equals(identity(scope)))throw new IOException("Jellyfin account changed");try{json(scope.server.base,path,scope.token,new JSONObject());}catch(Status error){if(error.status!=404)throw error;json(scope.server.base,"/Users/"+scope.userId+"/PlayedItems/"+id(item),scope.token,new JSONObject());}if(expectedIdentity.equals(identity()))prefs.edit().remove("cache").commit();}
    static List<PlexClient.Film> parse(JSONArray items,boolean resume,boolean recent,int limit)throws IOException{
        if(items!=null&&items.length()>24)throw new IOException("Too many Jellyfin items");List<PlexClient.Film> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;items!=null&&i<items.length()&&out.size()<limit;i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;PlexClient.Film film=parseFilm(item,resume,recent);if(film!=null&&seen.add(film.key))out.add(film);}return out;
    }
    PlexClient.Film item(String key)throws Exception{String id=id(key);if(id.isEmpty())throw new IOException("Invalid item");Session s=stored();return parseFilm(compatible(s,"/Items/"+id+"?userId="+s.userId+"&fields="+FIELDS,"/Users/"+s.userId+"/Items/"+id),false,false);}
    static PlexClient.Film parseFilm(JSONObject item,boolean resume,boolean recent){
        String key=id(item.optString("Id")),type=item.optString("Type");boolean episode=type.equals("Episode"),season=type.equals("Season");if(key.isEmpty()||!Arrays.asList("Movie","Series","Episode","Season").contains(type)||season&&resume)return null;
        String series=id(item.optString("SeriesId"));if(season&&series.isEmpty())return null;PlexClient.Film f=new PlexClient.Film();f.provider="Jellyfin";f.packageName=PACKAGE;f.key=season&&recent?series:key;f.type=episode?"episode":season&&!recent?"season":type.equals("Series")||season?"show":"movie";
        JSONObject ids=item.optJSONObject("ProviderIds");for(String source:new String[]{"Tmdb","Imdb","Tvdb"}){String value=ids==null?"":ids.optString(source);if(value.matches("[A-Za-z0-9_-]{1,40}")){f.externalId=source.toLowerCase(Locale.ROOT)+"://"+value;break;}}f.seriesKey=episode||season?series:"";f.detailTitle=shortText(item.optString("Name"),320);f.title=shortText(item.optString(episode||season?"SeriesName":"Name",item.optString("Name")),320);if(f.title.isEmpty())return null;if(season&&!recent)f.title=shortText(item.optString("Name"),320);f.summary=shortText(item.optString("Overview"),1600);
        JSONObject tags=item.optJSONObject("ImageTags");String primary=tags==null?"":tags.optString("Primary");String posterId=f.key;if((episode||season)&&!series.isEmpty()&&!item.optString("SeriesPrimaryImageTag").isEmpty()){posterId=series;primary=item.optString("SeriesPrimaryImageTag");}
        f.thumb=primary.isEmpty()?"":imagePath(posterId,"Primary",primary);JSONArray backgrounds=item.optJSONArray("BackdropImageTags");String backdropId=key,backdrop=backgrounds==null?"":backgrounds.optString(0);
        if(backdrop.isEmpty()){backdropId=id(item.optString("ParentBackdropItemId"));JSONArray parent=item.optJSONArray("ParentBackdropImageTags");backdrop=parent==null?"":parent.optString(0);}
        f.art=backdrop.isEmpty()||backdropId.isEmpty()?f.thumb:imagePath(backdropId,"Backdrop",backdrop);String logo=tags==null?"":tags.optString("Logo");if(!logo.isEmpty())f.logo=imagePath(key,"Logo",logo);else if(!item.optString("ParentLogoImageTag").isEmpty())f.logo=imagePath(id(item.optString("ParentLogoItemId")),"Logo",item.optString("ParentLogoImageTag"));
        JSONObject data=item.optJSONObject("UserData");f.durationMs=Math.max(0,item.optLong("RunTimeTicks"))/10000;f.positionMs=data!=null&&data.has("PlaybackPositionTicks")?Math.max(0,data.optLong("PlaybackPositionTicks"))/10000:-1;
        if(resume&&(f.positionMs<=0||(f.durationMs>0&&f.positionMs>=f.durationMs)||(data!=null&&data.optBoolean("Played"))))return null;
        f.lastPlayedMs=data==null?0:time(data.optString("LastPlayedDate"));f.addedAtMs=time(item.optString("DateCreated"));f.continuing=resume;f.recentlyAdded=recent;f.progress=f.durationMs>0&&f.positionMs>0?Math.min(1,(float)f.positionMs/f.durationMs):-1;
        if(episode&&item.has("ParentIndexNumber")&&item.has("IndexNumber"))f.episodeLabel="T"+item.optInt("ParentIndexNumber")+" · E"+item.optInt("IndexNumber");int year=item.optInt("ProductionYear");f.meta="Jellyfin"+(year>0?" · "+year:"")+(f.episodeLabel.isEmpty()?f.durationMs>0?" · "+f.durationMs/60000+" min":"":" · "+f.episodeLabel);f.section="Jellyfin";return f;
    }
    static String imagePath(String item,String type,String tag){return id(item).isEmpty()||!tag.matches("[A-Za-z0-9_-]{1,128}")?"":"/Items/"+id(item)+"/Images/"+type+"?tag="+tag;}
    static long time(String text){try{return Math.max(0,Instant.parse(text).toEpochMilli());}catch(Exception invalid){return 0;}}
    private static String shortText(String value,int length){return value.length()>length?value.substring(0,length):value;}
    private static JSONArray cache(List<PlexClient.Film> films)throws JSONException{JSONArray out=new JSONArray();for(PlexClient.Film f:films)out.put(new JSONObject().put("k",f.key).put("t",f.title).put("s",f.summary).put("p",f.thumb).put("a",f.art).put("l",f.logo).put("m",f.meta).put("y",f.type).put("e",f.episodeLabel).put("d",f.durationMs).put("v",f.positionMs).put("r",f.continuing).put("n",f.recentlyAdded).put("at",f.addedAtMs).put("last",f.lastPlayedMs).put("next",f.nextEpisode).put("series",f.seriesKey).put("external",f.externalId));return out;}
    List<PlexClient.Film> cached(){try{JSONArray items=new JSONArray(prefs.getString("cache","[]"));List<PlexClient.Film> out=new ArrayList<>();for(int i=0;i<Math.min(24,items.length());i++){JSONObject o=items.getJSONObject(i);PlexClient.Film f=new PlexClient.Film();f.key=id(o.optString("k"));if(f.key.isEmpty())continue;f.provider="Jellyfin";f.packageName=PACKAGE;f.title=o.optString("t");f.summary=o.optString("s");f.thumb=o.optString("p");f.art=o.optString("a");f.logo=o.optString("l");f.meta=o.optString("m");f.type=o.optString("y");f.episodeLabel=o.optString("e");f.durationMs=o.optLong("d");f.positionMs=o.optLong("v",-1);f.continuing=o.optBoolean("r");f.recentlyAdded=o.optBoolean("n");f.addedAtMs=o.optLong("at");f.lastPlayedMs=o.optLong("last");f.nextEpisode=o.optBoolean("next");f.seriesKey=id(o.optString("series"));f.externalId=o.optString("external");f.progress=f.durationMs>0&&f.positionMs>=0?Math.min(1,(float)f.positionMs/f.durationMs):-1;out.add(f);}return out;}catch(Exception invalid){return Collections.emptyList();}}
    byte[] image(String path,int width,int height,boolean transparent)throws IOException{
        if(!connected()||path==null||!path.matches("/Items/[a-f0-9]{32}/Images/(Primary|Backdrop|Logo)\\?tag=[A-Za-z0-9_-]{1,128}"))throw new IOException("Invalid Jellyfin image");
        Session scope=stored();return bytes(scope.server.base,path+"&maxWidth="+Math.min(1920,Math.max(1,width))+"&maxHeight="+Math.min(1080,Math.max(1,height))+"&quality=88"+(transparent?"&format=png":""),scope.token,null,4*1024*1024,true);
    }
    byte[] avatar()throws IOException{Session scope=stored();if(scope.token.isEmpty()||scope.imageTag.isEmpty())throw new IOException("No avatar");String base=scope.server.base,token=scope.token;try{return bytes(base,"/UserImage?userId="+scope.userId+"&maxWidth=96&maxHeight=96",token,null,1024*1024,true);}catch(Status error){if(error.status!=404)throw error;return bytes(base,"/Users/"+scope.userId+"/Images/Primary?maxWidth=96&maxHeight=96",token,null,1024*1024,true);}}
    static final class Status extends IOException{final int status;Status(int value){super("Jellyfin request failed ("+value+")");status=value;}}
    private JSONObject json(String base,String path,String token,JSONObject body)throws Exception{byte[] bytes=bytes(base,path,token,body,2*1024*1024,false);return bytes.length==0?new JSONObject():new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
    private byte[] bytes(String base,String path,String token,JSONObject body,int limit,boolean image)throws IOException{
        validateAddress(base);if(!path.startsWith("/")||path.startsWith("//")||path.contains("..")||path.contains("\\")||path.matches(".*[\\r\\n].*"))throw new IOException("Invalid Jellyfin endpoint");if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setRequestProperty("Accept",image?"image/*":"application/json");
        c.setRequestProperty("Authorization","MediaBrowser Client=\"Vela\", Device=\"Android TV\", DeviceId=\""+prefs.getString("device","")+"\", Version=\"1.16.0\""+(token.isEmpty()?"":", Token=\""+token+"\""));
        try{if(body!=null){byte[] encoded=body.toString().getBytes(StandardCharsets.UTF_8);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(encoded.length);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(encoded);}}
            int status=c.getResponseCode();if(status!=200&&status!=204)throw new Status(status);if(c.getContentLengthLong()>limit)throw new IOException("Jellyfin response too large");String mime=c.getContentType();if(image&&(mime==null||!mime.toLowerCase(Locale.ROOT).startsWith("image/")))throw new IOException("Not an image");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[8192];int count;long start=System.nanoTime();while((count=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+count>limit||System.nanoTime()-start>20_000_000_000L)throw new IOException("Jellyfin response limit");out.write(block,0,count);}return out.toByteArray();}
        }finally{c.disconnect();}
    }
    private static String encode(String s)throws UnsupportedEncodingException{return URLEncoder.encode(s,"UTF-8");}
}
