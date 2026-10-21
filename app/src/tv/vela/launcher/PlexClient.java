package tv.vela.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Plex library and explicit per-item watched actions. Credentials stay in private app storage, never in the APK or logs. */
final class PlexClient {
    static final String PRODUCT = "Vela";
    final SharedPreferences prefs;
    PlexClient(Context context) {
        prefs = context.getSharedPreferences("plex", Context.MODE_PRIVATE);
        if (!prefs.contains("client")) prefs.edit().putString("client", UUID.randomUUID().toString()).commit();
    }
    String name() { return prefs.getString("serverName", "Plex"); }
    boolean connected() { return !prefs.getString("serverToken", "").isEmpty(); }
    String identity(){return prefs.getString("serverId","")+":"+profileKey(prefs.getString("serverToken",""));}
    String clientId() { return prefs.getString("client", ""); }
    static String profileKey(String token) {
        if(token.isEmpty())return "";
        try{return Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    byte[] avatarImage(String accountToken) throws Exception {
        if(accountToken.isEmpty())throw new IOException("Perfil no disponible.");
        JSONObject user=new JSONObject(new String(bytes("https://plex.tv/api/v2/user",accountToken,null,10000,256*1024),StandardCharsets.UTF_8));
        URL address=new URL(user.optString("thumb"));
        // The avatar and each redirect are public image requests; never forward Plex credentials.
        for(int redirects=0;redirects<4;redirects++){
            if(!avatarAllowed(address.toString()))throw new IOException("Origen de avatar no admitido.");
            HttpURLConnection connection=(HttpURLConnection)address.openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept","image/*");
            try{
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){String next=connection.getHeaderField("Location");if(next==null)throw new IOException("Avatar no disponible.");address=new URL(address,next);continue;}
                String type=connection.getContentType();
                if(status!=200||type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))throw new IOException("Avatar no disponible.");
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int count;
                    while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+count>1024*1024)throw new IOException("Avatar demasiado grande.");out.write(buffer,0,count);}
                    return out.toByteArray();
                }
            }finally{connection.disconnect();}
        }
        throw new IOException("Demasiadas redirecciones de avatar.");
    }
    static boolean avatarAllowed(String value) {
        try{URL url=new URL(value);String host=url.getHost().toLowerCase(Locale.ROOT);
            return url.getProtocol().equals("https")&&url.getUserInfo()==null&&(url.getPort()==-1||url.getPort()==443)
                    &&(host.equals("plex.tv")||host.endsWith(".plex.tv")||host.equals("plexusercontent.com")||host.endsWith(".plexusercontent.com")||host.equals("gravatar.com")||host.endsWith(".gravatar.com"));
        }catch(MalformedURLException e){return false;}
    }
    void disconnect() {
        String client = clientId();
        prefs.edit().clear().putString("client", client).commit();
    }
    JSONObject createPin() throws Exception {
        return new JSONObject(request("https://plex.tv/api/v2/pins", "", "strong=false", 15000));
    }
    boolean checkPin(long id, String code) throws Exception {
        JSONObject pin = new JSONObject(request("https://plex.tv/api/v2/pins/" + id + "?code=" + encode(code), "", null, 10000));
        String token = pin.optString("authToken", "");
        if (token.isEmpty() || token.equals("null")) return false;
        return prefs.edit().putString("accountToken", token).commit();
    }
    static final class Server {
        String name, id, token;
        final List<String> urls = new ArrayList<>();
    }
    List<Server> servers() throws Exception {
        JSONArray resources = new JSONArray(request("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1",
                prefs.getString("accountToken", ""), null, 15000));
        List<Server> servers = new ArrayList<>();
        for (int i = 0; i < resources.length(); i++) {
            JSONObject item = resources.getJSONObject(i);
            if (!item.optString("provides").contains("server")) continue;
            Server server = new Server();
            server.name = item.optString("name", "Plex");
            server.id = item.optString("clientIdentifier");
            server.token = item.optString("accessToken");
            JSONArray connections = item.optJSONArray("connections");
            if (connections != null) for (int pass = 0; pass < 2; pass++) {
                for (int j = 0; j < connections.length(); j++) {
                    JSONObject connection = connections.getJSONObject(j);
                    String uri = connection.optString("uri");
                    if (uri.startsWith("https://") && connection.optBoolean("local") == (pass == 0)) server.urls.add(uri);
                }
            }
            if (!server.token.isEmpty() && !server.urls.isEmpty()) servers.add(server);
        }
        return servers;
    }
    void select(Server server) throws Exception {
        for (String url : server.urls) {
            try {
                request(url + "/identity", server.token, null, 4000);
                if (!prefs.edit().putString("serverUri", trimSlash(url)).putString("serverToken", server.token)
                        .putString("serverId", server.id).putString("serverName", server.name)
                        .remove("libraryCache").remove("libraryAt").commit()) {
                    throw new IOException("No se ha podido guardar la conexión.");
                }
                return;
            } catch (IOException ignored) {}
        }
        throw new IOException("No se ha podido conectar de forma segura. Comprueba que el servidor esté encendido.");
    }
    static final class Film {
        String key, title, summary, thumb, art, meta, type;
        String logo = "";
        String provider = "Plex", intent;
        String packageName = "com.plexapp.android", episodeLabel = "";
        String section = "Tu biblioteca";
        float progress = -1,thumbAspect;
        long durationMs,positionMs=-1,lastPlayedMs,addedAtMs;
        boolean continuing,tvSource,landscape,recentlyAdded,nextEpisode;
        String seriesKey="";
        long remainingMs(){return durationMs>0&&positionMs>=0&&positionMs<durationMs?durationMs-positionMs:-1;}
    }
    static final int PAGE_SIZE = 24;
    static final class Section {
        final String key, title, type;
        Section(String key,String title,String type){this.key=key;this.title=title;this.type=type;}
    }
    static final class LibraryPage {
        final List<Film> films;
        final int offset,total,received;
        LibraryPage(List<Film> films,int offset,int total,int received){this.films=films;this.offset=offset;this.total=total;this.received=received;}
        boolean hasNext(){return received>0&&(total<0?received==PAGE_SIZE:(long)offset+received<total);}
        int nextOffset(){return offset+received;}
    }
    List<Section> sections() throws Exception {
        return parseSections(new JSONObject(request(prefs.getString("serverUri","")+"/library/sections",
                prefs.getString("serverToken",""),null,15000)));
    }
    static List<Section> parseSections(JSONObject root) {
        List<Section> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        JSONObject container=root.optJSONObject("MediaContainer");
        JSONArray sections=container==null?null:container.optJSONArray("Directory");
        for(int i=0;sections!=null&&i<sections.length();i++){
            JSONObject item=sections.optJSONObject(i);if(item==null)continue;
            String key=item.optString("key"),type=item.optString("type");
            if(key.matches("[0-9]+")&&(type.equals("movie")||type.equals("show"))&&seen.add(key))
                result.add(new Section(key,item.optString("title",type),type));
        }
        return result;
    }
    LibraryPage page(Section section,int offset) throws Exception {
        if(!connected()||offset<0||!section.key.matches("[0-9]+"))throw new IOException("Biblioteca no disponible.");
        String url=prefs.getString("serverUri","")+"/library/sections/"+section.key+"/all?sort=titleSort%3Aasc"
                +"&includeExternalMetadata=0&X-Plex-Container-Start="+offset+"&X-Plex-Container-Size="+PAGE_SIZE;
        return parsePage(new JSONObject(request(url,prefs.getString("serverToken",""),null,15000)),offset);
    }
    static LibraryPage parsePage(JSONObject root,int offset) throws Exception {
        JSONObject container=root.optJSONObject("MediaContainer");
        if(container==null||container.optInt("offset",offset)!=offset)throw new IOException("Página no disponible.");
        JSONArray metadata=container.optJSONArray("Metadata");int received=metadata==null?0:metadata.length();
        if(received>PAGE_SIZE)throw new IOException("El servidor no ha respetado el límite de página.");
        int total=container.has("totalSize")?Math.max(0,container.optInt("totalSize")):-1;
        List<Film> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<received;i++){
            JSONObject item=metadata.optJSONObject(i);if(item==null)continue;
            Film film=parseFilm(item,false);
            if(film!=null&&(film.type.equals("movie")||film.type.equals("show"))&&seen.add(film.key))result.add(film);
        }
        return new LibraryPage(result,offset,total,received);
    }
    List<Film> library() throws Exception {
        if (!connected()) return Collections.emptyList();
        String server=identity();
        String uri=prefs.getString("serverUri",""),token=prefs.getString("serverToken","");
        JSONObject root=new JSONObject(request(uri+"/hubs?count=8&includeExternalMetadata=0",token,null,15000));
        try{mergeContinue(root,new JSONObject(request(uri+"/hubs/continueWatching?count=12",token,null,6000)));}
        catch(Exception ignored){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        try{mergeRecent(root,new JSONObject(request(uri+"/library/recentlyAdded?includeExternalMetadata=0&X-Plex-Container-Start=0&X-Plex-Container-Size=24",token,null,6000)));}
        catch(Exception ignored){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        String body=root.toString();List<Film> films=parseHubs(root);
        if(server.equals(identity()))prefs.edit().putString("libraryCache", body).putLong("libraryAt", System.currentTimeMillis()).apply();
        return films;
    }
    static void mergeContinue(JSONObject root,JSONObject response) throws Exception {
        JSONObject container=response.optJSONObject("MediaContainer"),home=root.optJSONObject("MediaContainer");
        if(container==null||home==null)return;
        JSONArray metadata=container.optJSONArray("Metadata"),hubs=container.optJSONArray("Hub");
        if(metadata==null&&hubs!=null&&hubs.length()>0){JSONObject hub=hubs.optJSONObject(0);if(hub!=null)metadata=hub.optJSONArray("Metadata");}
        if(metadata==null)return;
        JSONArray bounded=new JSONArray();for(int i=0;i<Math.min(12,metadata.length());i++)bounded.put(metadata.get(i));
        JSONArray merged=new JSONArray().put(new JSONObject().put("hubIdentifier","vela.continue").put("Metadata",bounded));
        JSONArray existing=home.optJSONArray("Hub");for(int i=0;existing!=null&&i<existing.length();i++){JSONObject hub=existing.optJSONObject(i);if(hub!=null){String id=hub.optString("hubIdentifier").toLowerCase(Locale.ROOT);if(!id.contains("continue")&&!id.contains("ondeck"))merged.put(hub);}}
        home.put("Hub",merged);
    }
    static void mergeRecent(JSONObject root,JSONObject response) throws Exception {
        JSONObject container=response.optJSONObject("MediaContainer"),home=root.optJSONObject("MediaContainer");if(container==null||home==null)return;
        JSONArray metadata=container.optJSONArray("Metadata");if(metadata==null)return;
        if(metadata.length()>PAGE_SIZE)throw new IOException("El servidor no ha respetado el límite de novedades.");
        JSONArray existing=home.optJSONArray("Hub"),merged=new JSONArray().put(new JSONObject().put("hubIdentifier","vela.recent").put("Metadata",metadata));
        // The dedicated feed replaces recent hubs, avoiding stale fallback titles after removals.
        for(int i=0;existing!=null&&i<existing.length();i++){JSONObject hub=existing.optJSONObject(i);if(hub!=null&&!recentHub(hub.optString("hubIdentifier").toLowerCase(Locale.ROOT)))merged.put(hub);}
        home.put("Hub",merged);
    }
    List<Film> cached() {
        try { return parseHubs(new JSONObject(prefs.getString("libraryCache", "{}"))); }
        catch (Exception ignored) { return Collections.emptyList(); }
    }
    static List<Film> parseHubs(JSONObject root) throws Exception {
        List<Film> films = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> seriesSeen = new HashSet<>();
        int resumed=0,others=0;boolean hasRecent=false;
        JSONObject container = root.optJSONObject("MediaContainer");
        JSONArray hubs = container == null ? null : container.optJSONArray("Hub");
        if (hubs == null) return films;
        for(int i=0;i<hubs.length();i++){JSONObject hub=hubs.optJSONObject(i);if(hub!=null&&recentHub(hub.optString("hubIdentifier").toLowerCase(Locale.ROOT)))hasRecent=true;}
        // Continue watching first; preserve the server's ordering within each row.
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < hubs.length() && films.size() < 48; i++) {
            JSONObject hub = hubs.getJSONObject(i);
            String identifier = hub.optString("hubIdentifier", "").toLowerCase(Locale.ROOT);
            boolean resume = identifier.contains("continue") || identifier.contains("ondeck");
            if (resume != (pass == 0)) continue;
            boolean recent=recentHub(identifier);if(!resume&&hasRecent&&!recent)continue;
            JSONArray metadata = hub.optJSONArray("Metadata");
            for (int j = 0; metadata != null && j < Math.min(metadata.length(),24) && films.size() < 48; j++) {
                JSONObject item = metadata.getJSONObject(j);
                String type = item.optString("type");
                if(recent&&type.equals("season"))item=new JSONObject(item.toString()).put("type","show").put("ratingKey",item.optString("parentRatingKey")).put("title",item.optString("parentTitle")).put("thumb",item.optString("parentThumb",item.optString("thumb")));
                String key = item.optString("ratingKey");
                type=item.optString("type");String group=resume?"resume:":"library:";
                if ((!type.equals("movie") && !type.equals("episode") && !type.equals("show")) || key.isEmpty()) continue;
                Film film=parseFilm(item,resume);if(resume&&((film.durationMs>0&&film.positionMs>=film.durationMs)||(item.optInt("viewCount")>0&&film.positionMs<=0)))continue;if(!seen.add(group+key))continue;
                String seriesKey=type.equals("episode")?item.optString("grandparentRatingKey"):type.equals("show")?key:"";
                if(!seriesKey.isEmpty() && !seriesSeen.add(group+seriesKey))continue;
                if(resume){if(resumed>=12)continue;resumed++;}else{if(others>=36)continue;others++;}
                film.recentlyAdded=recent;films.add(film);
            }
        }
        // Stable sorting merges recent movie/series hubs using Plex's actual added date.
        films.sort((a,b)->a.continuing!=b.continuing?(a.continuing?-1:1):!a.continuing&&a.recentlyAdded&&b.recentlyAdded?Long.compare(b.addedAtMs,a.addedAtMs):0);
        List<Film> bounded=new ArrayList<>();resumed=0;others=0;
        for(Film film:films)if(film.continuing?resumed++<12:others++<12)bounded.add(film);
        return bounded;
    }
    private static boolean recentHub(String id){return id.equals("vela.recent")||id.endsWith(".recent")||id.contains("recentlyadded");}
    private static Film parseFilm(JSONObject item,boolean resume) {
        String type=item.optString("type"),key=item.optString("ratingKey");
        if(key.isEmpty()||(!type.equals("movie")&&!type.equals("show")&&!type.equals("episode")))return null;
        Film film=new Film();film.key=key;film.type=type;film.continuing=resume;
        film.title=item.optString(type.equals("episode")?"grandparentTitle":"title",item.optString("title"));
        film.summary=item.optString("summary");
        film.thumb=item.optString(type.equals("episode")?"grandparentThumb":"thumb",item.optString("thumb"));
        film.art=item.optString("art",item.optString("grandparentArt",film.thumb));
        JSONArray artwork=item.optJSONArray("Image");
        for(int a=0;artwork!=null&&a<artwork.length();a++){
            JSONObject image=artwork.optJSONObject(a);if(image==null)continue;
            String path=image.optString("url");
            if(image.optString("type").equalsIgnoreCase("clearLogo")&&path.startsWith("/")&&!path.startsWith("//"))film.logo=path;
        }
        int year=item.optInt("year"),minutes=item.optInt("duration")/60000;
        film.meta="Plex"+(year>0?"  ·  "+year:"")+(minutes>0?"  ·  "+minutes+" min":"");
        if(type.equals("episode"))film.meta+="  ·  T"+item.optInt("parentIndex")+" E"+item.optInt("index");
        long duration=item.optLong("duration"),offset=item.optLong("viewOffset");
        film.durationMs=Math.max(0,duration);film.positionMs=item.has("viewOffset")&&!item.isNull("viewOffset")?Math.max(-1,offset):-1;
        film.seriesKey=type.equals("episode")?item.optString("grandparentRatingKey"):"";film.nextEpisode=resume&&type.equals("episode")&&offset<=0&&item.optInt("viewCount")==0;if(film.nextEpisode){film.positionMs=0;if(duration>0)film.progress=0;}
        long last=item.optLong("lastViewedAt");if(last>0&&last<=Long.MAX_VALUE/1000)film.lastPlayedMs=last*1000;
        long added=item.optLong("addedAt");if(added>0&&added<=Long.MAX_VALUE/1000)film.addedAtMs=added*1000;
        if(type.equals("episode"))film.episodeLabel="T"+item.optInt("parentIndex")+" · E"+item.optInt("index");
        if(duration>0&&offset>0)film.progress=Math.min(1f,(float)offset/duration);
        film.section=resume?"Seguir viendo":"En tu biblioteca";
        return film;
    }
    static String watchedPath(String key)throws IOException {if(key==null||!key.matches("[0-9]{1,20}"))throw new IOException("Invalid Plex item");return "/:/scrobble?identifier=com.plexapp.plugins.library&key="+key;}
    void markWatched(String key,String expectedIdentity)throws IOException {
        String path=watchedPath(key);Map<String,?> snapshot=prefs.getAll();String base=(String)snapshot.get("serverUri"),token=(String)snapshot.get("serverToken"),id=(String)snapshot.get("serverId");
        if(base==null||token==null||token.isEmpty()||id==null||!expectedIdentity.equals(id+":"+profileKey(token)))throw new IOException("Plex account changed");
        bytes(base+path,token,null,8000,64*1024,"PUT");if(expectedIdentity.equals(identity()))prefs.edit().remove("libraryCache").remove("libraryAt").commit();
    }
    String openUri(Film film) {
        return "plex://preplay/?metadataKey=" + encode("/library/metadata/" + film.key)
                + "&server=" + encode(prefs.getString("serverId", ""));
    }
    byte[] image(String path, int width, int height) throws IOException {
        return image(path,width,height,false);
    }
    byte[] image(String path, int width, int height, boolean transparent) throws IOException {
        if (!connected() || path == null || !path.startsWith("/") || path.startsWith("//")) throw new IOException("Imagen no disponible.");
        String url = prefs.getString("serverUri", "") + "/photo/:/transcode?width=" + width + "&height=" + height
                + "&minSize="+(transparent?"0":"1")+"&upscale=0"+(transparent?"&format=png":"")+"&url=" + encode(path);
        return bytes(url, prefs.getString("serverToken", ""), null, 12000, 4 * 1024 * 1024);
    }
    private String request(String url, String token, String body, int timeout) throws IOException {
        return new String(bytes(url, token, body, timeout, 4 * 1024 * 1024), StandardCharsets.UTF_8);
    }
    private byte[] bytes(String url,String token,String body,int timeout,int limit)throws IOException{return bytes(url,token,body,timeout,limit,body==null?"GET":"POST");}
    private byte[] bytes(String url, String token, String body, int timeout, int limit,String method) throws IOException {
        URL address = new URL(url);
        if (!address.getProtocol().equals("https")) throw new IOException("La conexión debe usar HTTPS.");
        HttpURLConnection connection = (HttpURLConnection) address.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(timeout); connection.setReadTimeout(timeout);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Plex-Client-Identifier", clientId());
        connection.setRequestProperty("X-Plex-Product", PRODUCT);
        connection.setRequestProperty("X-Plex-Version", "1.0.0");
        connection.setRequestProperty("X-Plex-Platform", "Android");
        if (!token.isEmpty()) connection.setRequestProperty("X-Plex-Token", token);
        try {
            connection.setRequestMethod(method);
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int status = connection.getResponseCode();
            if (status == 401) throw new IOException("La sesión de Plex ha caducado. Vuelve a vincularla.");
            if (status != 200 && status != 201 && status != 204) throw new IOException("Plex no está disponible (" + status + ").");
            if(status==204)return new byte[0];
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = in.read(buffer)) != -1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    if (out.size() + read > limit) throw new IOException("La respuesta de Plex es demasiado grande.");
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } finally { connection.disconnect(); }
    }
    private static String trimSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    static String encode(String value) { return Uri.encode(value); }
}
