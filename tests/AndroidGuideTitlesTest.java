package tv.vela.launcher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.CancellationSignal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Exercise the three real XMLTV readers, including the upgrade of a still-fresh guide. */
public final class AndroidGuideTitlesTest extends Instrumentation {
    private static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static String programme(String id,long from,long to,String titles){
        return "<programme channel='"+id+"' start='"+STAMP.format(Instant.ofEpochSecond(from))+"' stop='"+STAMP.format(Instant.ofEpochSecond(to))+"'>"+titles+"</programme>";
    }
    private static void equal(String expected,String actual){
        if(!expected.equals(actual))throw new AssertionError("Expected ["+expected+"] but got ["+actual+"]");
    }
    private static void checkDescriptions(XmlTvGuide full, LiteXmlTvGuide lite, long now) throws Exception {
        LiveTvData.Channel channel=new LiveTvData.Channel(43,"test","43","Descripciones",false,true,false);
        List<LiveTvData.Channel> channels=Collections.singletonList(channel);
        String text="<title>Magazine</title><desc>  </desc><desc lang='es'>  Cine <![CDATA[y]]> música &amp; más.  </desc>"
                +"<desc lang='fr'>Cinéma et musique.</desc>";
        String xml="<tv><channel id='demo'><display-name>Descripciones</display-name></channel>"
                +programme("demo",now-60,now+600,text)
                +programme("demo",now+600,now+1200,"<title>Next</title><desc>Second programme.</desc>")+"</tv>";
        byte[] bytes=xml.getBytes(StandardCharsets.UTF_8);
        full.importGuide(new ByteArrayInputStream(bytes),LiveTvData.hash(full.url()),now);
        lite.importGuide(new ByteArrayInputStream(bytes),channels,now,new CancellationSignal());
        LiveTvData.Guide m3u=M3uClient.xmlGuide(new StringReader(xml),Collections.singleton("demo"),now).get("demo");
        for(List<LiveTvData.Programme> rows:Arrays.asList(full.window(channels,now).get(channel.id),
                lite.read(channels,now,now+1200,new CancellationSignal()).get(channel.id),Arrays.asList(m3u.now,m3u.next))){
            equal("Cine y música & más.",rows.get(0).description);
            equal("Second programme.",rows.get(1).description);
        }
    }

    private static final String PLAYLIST="https://playlist.example.invalid/tv.m3u";
    private static final String GUIDE_A="https://playlist.example.invalid/listed.xml";
    private static final String GUIDE_B="https://playlist.example.invalid/custom.xml";
    private static byte[] playlistReply(String url,long now)throws IOException{
        if(url.equals(PLAYLIST))return ("#EXTM3U x-tvg-url=\""+GUIDE_A+"\"\n#EXTINF:-1 tvg-id=\"demo\",Demo\nhttps://playlist.example.invalid/stream\n").getBytes(StandardCharsets.UTF_8);
        if(!url.equals(GUIDE_A)&&!url.equals(GUIDE_B))return null;
        String title=url.equals(GUIDE_A)?"Emisión de la lista":"Guía alternativa";
        String xml="<?xml version='1.0' encoding='ISO-8859-1'?><tv>"
                +programme("demo",now-60,now+600,"<title>"+title+"</title><desc>Información del día.</desc>")+"</tv>";
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(GZIPOutputStream zip=new GZIPOutputStream(bytes)){zip.write(xml.getBytes(StandardCharsets.ISO_8859_1));}
        return bytes.toByteArray();
    }
    private static void checkPlaylistSettings(Context c,List<String> requests)throws Exception{
        LiveTvData.prefs(c).edit().clear().commit();
        M3uClient playlist=new M3uClient(c);
        LiveTvData.Channel ch=new LiveTvData.Channel("demo","Demo","","",Collections.emptyList());
        List<LiveTvData.Channel> channels=Collections.singletonList(ch);
        playlist.configure(PLAYLIST,GUIDE_B,false);
        playlist.refresh(true);
        equal("Guía alternativa",playlist.guide(channels).get(ch.id).now.title);
        playlist.configure(PLAYLIST,"",false);
        equal("Emisión de la lista",playlist.guide(channels).get(ch.id).now.title);
        playlist.configure(PLAYLIST,GUIDE_B,false);
        LiveTvData.Programme programme=playlist.guide(channels).get(ch.id).now;
        equal("Guía alternativa",programme.title);
        equal("Información del día.",programme.description);
        if(Collections.frequency(requests,PLAYLIST)!=1)throw new AssertionError("Changing guides downloaded the playlist again");

        // A cache from an older release contains the effective URL, not its origin.
        String source=M3uClient.identity(c);
        try(TvCatalogCache.Writer writer=new TvCatalogCache(c).writer(source)){
            writer.add(ch,true);
            writer.commit(new org.json.JSONObject().put("headersVersion",1).put("epg",GUIDE_B).toString());
        }
        LiveTvData.prefs(c).edit().remove("m3u-epg").remove("m3u-guide-edited").commit();
        equal("Guía alternativa",playlist.guide(channels).get(ch.id).now.title);
        playlist.configure(PLAYLIST,GUIDE_B,false);
        playlist.configure(PLAYLIST,"",false);
        if(!playlist.guide(channels).isEmpty())throw new AssertionError("Cleared override survived in legacy guide cache");
        playlist.refresh(false);
        equal("Emisión de la lista",playlist.guide(channels).get(ch.id).now.title);
        playlist.refresh(false);
        if(Collections.frequency(requests,PLAYLIST)!=2)throw new AssertionError("Legacy guide did not recover with a single playlist refresh");
    }

    private static void checkCategories(Context c, XmlTvGuide full, LiteXmlTvGuide lite, long now) throws Exception {
        String label = "Category station", id = "category.test";
        String longLabel = "<category>" + String.join("", Collections.nCopies(250, "x")) + "</category>";
        String genres = "<category>De<![CDATA[por]]>tes</category><category>Documental</category>";
        String xml = "<tv><channel id='" + id + "'><display-name>" + label + "</display-name></channel>"
                + programme(id, now - 60, now + 600, "<title>Genres first</title>" + genres + longLabel)
                + programme(id, now + 600, now + 1200, "<title>Genres last</title>" + longLabel + genres)
                + programme(id, now + 1200, now + 1800, "<title>Separate labels</title><category>De</category><category>portes</category>")
                + "</tv>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        List<LiveTvData.Channel> stations = Collections.singletonList(new LiveTvData.Channel(12,"test","12",label,false,true,false));
        Map<Long,String> names = Collections.singletonMap(12L,label);
        CancellationSignal signal = new CancellationSignal();
        full.importGuide(new ByteArrayInputStream(bytes), LiveTvData.hash(full.url()), now);
        lite.importGuide(new ByteArrayInputStream(bytes), stations, now, signal);
        for (int genre : new int[]{2, 3}) {
            for (List<VelaShows.Airing> rows : Arrays.asList(full.discover(names,2,genre,0,signal), lite.discover(names,2,genre,0,signal))) {
                List<String> titles = new ArrayList<>();
                for (VelaShows.Airing airing : rows) titles.add(airing.title);
                if (!titles.equals(Arrays.asList("Genres first", "Genres last")))
                    throw new AssertionError("XMLTV genre " + genre + " returned " + titles + " for reversed category order");
            }
        }
        for (String file : Arrays.asList("tv-guide/xmltv.db", "antenna-epg/programmes.db")) {
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(c.getCacheDir(),file).getPath(),null,SQLiteDatabase.OPEN_READONLY);
                 android.database.Cursor rows = db.rawQuery("SELECT max(length(category)) FROM programmes",null)) {
                if (!rows.moveToFirst() || rows.getInt(0) > 240) throw new AssertionError("Oversized category in " + file);
            }
        }
        LiveTvData.Guide guide = M3uClient.xmlGuide(new StringReader(xml), Collections.singleton(id), now).get(id);
        equal("Genres first", guide.now.title);
        equal("Genres last", guide.next.title);
    }

    private static void checkTimeFormats(Context c, XmlTvGuide full, LiteXmlTvGuide lite, long now) throws Exception {
        long start=now-now%60-60, end=start+600;
        DateTimeFormatter minutes=DateTimeFormatter.ofPattern("yyyyMMddHHmm",Locale.ROOT);
        String begin=minutes.withZone(ZoneOffset.ofHoursMinutes(5,30)).format(Instant.ofEpochSecond(start))+" +0530";
        String stop=minutes.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(end));
        String british=minutes.withZone(ZoneOffset.ofHours(1)).format(Instant.ofEpochSecond(end))+" BST";
        String finish=minutes.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(end+600))+" UTC";
        String xml="<tv><channel id='clock'><display-name>Clock TV</display-name></channel>"
                +"<programme channel='clock' start='"+begin+"' stop='"+stop+"'><title>Now</title></programme>"
                +"<programme channel='clock' start='"+british+"' stop='"+finish+"'><title>Next</title></programme>"
                +programme("clock",end+600,end+1200,"<title>Later</title>")+"</tv>";
        LiveTvData.Channel station=new LiveTvData.Channel(77,"test","77","Clock TV",false,true,false);
        List<LiveTvData.Channel> stations=Collections.singletonList(station);
        byte[] bytes=xml.getBytes(StandardCharsets.UTF_8);
        full.importGuide(new ByteArrayInputStream(bytes),LiveTvData.hash(full.url()),now);
        lite.importGuide(new ByteArrayInputStream(bytes),stations,now,new CancellationSignal());
        List<LiveTvData.Programme> fullRows=full.window(stations,now).get(station.id);
        List<LiveTvData.Programme> liteRows=lite.read(stations,now,end+1200,new CancellationSignal()).get(station.id);
        LiveTvData.Guide streamed=M3uClient.xmlGuide(new StringReader(xml),Collections.singleton("clock"),now).get("clock");
        for(List<LiveTvData.Programme> rows:Arrays.asList(fullRows,liteRows,Arrays.asList(streamed.now,streamed.next))){
            if(rows==null||rows.size()<2||rows.get(0)==null||rows.get(1)==null
                    ||!rows.get(0).title.equals("Now")||rows.get(0).start!=start||rows.get(0).end!=end
                    ||!rows.get(1).title.equals("Next")||rows.get(1).start!=end||rows.get(1).end!=end+600)
                throw new AssertionError("Full, Lite and playlist guides must agree on minute-precision times and explicit zones");
        }
        for(String invalid:Arrays.asList("202602300900 +0100","202601010900 +1260","202601010900 BAD","202601010900 +0A00"))
            if(M3uClient.timestamp(invalid)!=0||LiteXmlTvGuide.timestamp(invalid)!=0)
                throw new AssertionError("Accepted an invalid programme time: "+invalid);
    }
    private static void checkProgrammeOnlyGuide(Context c, XmlTvGuide full, LiteXmlTvGuide lite, long now) throws Exception {
        String guideId="Station1.tv", label="Antenna 7";
        LiveTvData.Channel playlist=new LiveTvData.Channel(guideId,"Different playlist name","","",Collections.emptyList());
        LiveTvData.Channel antenna=new LiveTvData.Channel(7,"test","7","Station 1",false,true,false);
        LiveTvData.Channel manual=new LiveTvData.Channel(17,"test","17",label,false,true,false);
        List<LiveTvData.Channel> tuned=Arrays.asList(antenna,manual);
        CancellationSignal signal=new CancellationSignal();
        String source=LiveTvData.hash(full.url());
        String events=programme(guideId,now-60,now+600,"<title>Morning news</title>")
                +programme(guideId,now+600,now+1200,"<title>Local weather</title>")
                +programme("other.network",now-60,now+600,"<title>Regional news</title>");
        try{
            VelaGuideFix.set(c,label,"other.network",0);
            for(String declarations:Arrays.asList("","<channel id='listed'><display-name>Listed TV</display-name></channel>")){
                byte[] xml=("<tv>"+declarations+events+"</tv>").getBytes(StandardCharsets.UTF_8);
                boolean fullAccepted=true,liteAccepted=true;
                try{full.importGuide(new ByteArrayInputStream(xml),source,now);}catch(IOException missing){fullAccepted=false;}
                try{lite.importGuide(new ByteArrayInputStream(xml),tuned,now,signal);}catch(IOException missing){liteAccepted=false;}
                if(!fullAccepted||!liteAccepted)throw new AssertionError("Programme-only XMLTV accepted by Full="+fullAccepted+", Lite="+liteAccepted);
                LiveTvData.Guide visible=full.read(Collections.singletonList(playlist),now).get(guideId);
                if(visible==null)throw new AssertionError("Full did not resolve the original playlist channel ID");
                equal("Morning news",visible.now.title);
                equal("Local weather",visible.next.title);
                Map<String,List<LiveTvData.Programme>> rows=lite.read(tuned,now,now+1200,signal);
                equal("Local weather",rows.get(antenna.id).get(1).title);
                equal("Regional news",rows.get(manual.id).get(0).title);
            }
            // Older feeds can contain blank display names. An unassigned tuner has no exact EPG ID.
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(new File(c.getCacheDir(),"tv-guide/xmltv.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                db.execSQL("INSERT OR IGNORE INTO aliases(alias,channel) VALUES('id:',?)",new Object[]{guideId});
            }
            LiveTvData.Channel unknown=new LiveTvData.Channel(30,"test","30","Unrelated station",false,true,false);
            if(full.read(Collections.singletonList(unknown),now).containsKey(unknown.id))
                throw new AssertionError("An empty legacy guide alias supplied another station's programmes");
            String badRoot="<catalog><channel id='wrong'><display-name>Wrong</display-name></channel>"
                    +programme("wrong",now-60,now+600,"<title>Wrong feed</title>")+"</catalog>";
            try{
                full.importGuide(new ByteArrayInputStream(badRoot.getBytes(StandardCharsets.UTF_8)),source,now);
                throw new AssertionError("A non-TV document replaced a working guide");
            }catch(IOException expected){}
            equal("Morning news",full.read(Collections.singletonList(playlist),now).get(guideId).now.title);
        }finally{VelaGuideFix.reset(c,label);}
    }
    private static void checkSearch(Context c, XmlTvGuide full, LiteXmlTvGuide lite, long now) throws Exception {
        String label = "Demo HD";
        List<LiveTvData.Channel> tuned = Collections.singletonList(new LiveTvData.Channel(21,"test","21",label,false,true,false));
        Map<Long,String> names = Collections.singletonMap(21L,label);
        CancellationSignal signal = new CancellationSignal();
        String listings = "<tv><channel id='station.HD'><display-name>Demo HD</display-name></channel>"
                + "<channel id='station.SD'><display-name>Demo HD</display-name></channel>"
                + programme("station.HD",now+900,now+1800,"<title>Fixture A</title>")
                + programme("station.SD",now+900,now+1800,"<title>Fixture B</title>") + "</tv>";
        byte[] data = listings.getBytes(StandardCharsets.UTF_8);
        full.importGuide(new ByteArrayInputStream(data),LiveTvData.hash(full.url()),now);
        lite.importGuide(new ByteArrayInputStream(data),tuned,now,signal);
        if (!full.antennaAirings(names,"Fixture",0,signal).isEmpty()
                || !lite.airings(names,"Fixture",0,signal).isEmpty())
            throw new AssertionError("An ambiguous antenna alias supplied a search result");
        try {
            VelaGuideFix.set(c,label,"station.SD",15);
            for (List<VelaShows.Airing> rows : Arrays.asList(
                    full.antennaAirings(names,"Fixture",0,signal), lite.airings(names,"Fixture",0,signal),
                    full.discover(names,2,0,0,signal), lite.discover(names,2,0,0,signal))) {
                if (rows.size()!=1 || rows.get(0).start!=now+1800 || !rows.get(0).title.equals("Fixture B"))
                    throw new AssertionError("Antenna search lost the chosen XMLTV ID or time correction");
            }
        } finally { VelaGuideFix.reset(c,label); }

        StringBuilder episodes=new StringBuilder("<tv><channel id='demo'><display-name>Demo HD</display-name></channel>");
        for(int i=0;i<30;i++){
            long start=now+60L*i;
            episodes.append(programme("demo",start,start+60,"<title>Órbita abierta</title>"));
        }
        episodes.append(programme("demo",now+3600,now+5400,"<title>Órbita Episodio 5</title>"));
        episodes.append(programme("demo",now+5400,now+7200,"<title>Luna Capítulo 2</title>")).append("</tv>");
        byte[] schedule=episodes.toString().getBytes(StandardCharsets.UTF_8);
        full.importGuide(new ByteArrayInputStream(schedule),LiveTvData.hash(full.url()),now);
        lite.importGuide(new ByteArrayInputStream(schedule),tuned,now,signal);
        VelaGuideSearch.Titles followed=VelaGuideSearch.Titles.followed(new HashSet<>(Arrays.asList("orbita","luna")));
        for(List<VelaShows.Airing> rows:Arrays.asList(full.antennaAirings(names,followed,signal),lite.airings(names,followed,signal))){
            if(rows.size()!=2)throw new AssertionError("Antenna reminders lost episodes after a page of similar titles");
            equal("Órbita Episodio 5",rows.get(0).title);
            equal("Luna Capítulo 2",rows.get(1).title);
        }
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Context base=getTargetContext();
        File root=new File(base.getCacheDir(),"guide-title-test");
        Context c=new ContextWrapper(base){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getCacheDir(){return root;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("guide-titles-test-"+name,mode);}
        };
        File fullDb=new File(root,"tv-guide/xmltv.db"),liteDb=new File(root,"antenna-epg/programmes.db");
        try{
            long now=System.currentTimeMillis()/1000;
            String[] names={"La 2","Telecinco","24 horas"};
            String[] expected={"Serás para siempre","Love Shopping TV","Noticias 24H"};
            String next="Bora Bora & música";
            String image="https://vela-fixture.invalid/art/programme.jpg";
            List<LiveTvData.Channel> channels=new ArrayList<>();StringBuilder xml=new StringBuilder("<tv>");
            for(int i=0;i<names.length;i++){
                channels.add(new LiveTvData.Channel(i+1,"test",""+(i+1),names[i],false,true,false));
                xml.append("<channel id='c").append(i).append("'><display-name>").append(names[i]).append("</display-name></channel>");
            }
            Set<String> ids=new HashSet<>();
            for(int i=0;i<names.length;i++){
                String id="c"+i;ids.add(id);
                xml.append(programme(id,now-300,now+300,"<title lang='en'>"+expected[i]+"</title><title lang='es'>"+expected[i]+"</title><icon src='"+image+"'/><rating><icon src='https://vela-fixture.invalid/age.png'/></rating>"));
                xml.append(programme(id,now+300,now+900,"<title>  </title><title>Bora <![CDATA[Bora]]> &amp; [COLOR tomato]música[/COLOR]</title><title lang='fr'>Autre titre</title>"));
            }
            // Eight bad starts used to evict the valid programme from the Full now/next query.
            String stop=STAMP.format(Instant.ofEpochSecond(now+300));
            for(int i=0;i<8;i++)
                xml.append("<programme channel='c0' stop='").append(stop).append("'><title>Invalid start</title></programme>");
            ids.add("no-start");
            xml.append("<programme channel='no-start' start='invalid' stop='").append(stop).append("'><title>Broken</title></programme>");
            xml.append("</tv>");String document=xml.toString();byte[] raw=document.getBytes(StandardCharsets.UTF_8);
            Map<String,LiveTvData.Guide> playlist=M3uClient.xmlGuide(new StringReader(document),ids,now);
            if(playlist.containsKey("no-start"))throw new AssertionError("M3U accepted an invalid programme start");
            XmlTvGuide full=new XmlTvGuide(c);LiteXmlTvGuide lite=new LiteXmlTvGuide(c);
            full.importGuide(new ByteArrayInputStream(raw),LiveTvData.hash(full.url()),now);
            lite.importGuide(new ByteArrayInputStream(raw),channels,now,new CancellationSignal());
            for(File file:Arrays.asList(fullDb,liteDb)){
                try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY);
                    android.database.Cursor rows=db.rawQuery("SELECT 1 FROM programmes WHERE start<=0 LIMIT 1",null)){
                    if(rows.moveToFirst())throw new AssertionError("XMLTV importer retained an invalid start");
                }
            }
            // Reopen caches written by the previous release, without a network refresh.
            for(File file:Arrays.asList(fullDb,liteDb)){
                try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                    db.execSQL("INSERT INTO programmes(channel,start,end,title) VALUES ('c0',0,?,'Cached invalid start')",new Object[]{now+300});
                    db.setVersion(file.equals(fullDb)?5:1);
                }
            }
            Map<String,List<LiveTvData.Programme>> timetable=full.window(channels,now);
            Map<String,List<LiveTvData.Programme>> antenna=lite.read(channels,now,now+900,new CancellationSignal());
            for(int i=0;i<channels.size();i++){
                String id=channels.get(i).id;LiveTvData.Guide guide=playlist.get("c"+i);
                equal(image,guide.now.image);equal("",guide.next.image);
                equal(image,antenna.get(id).get(0).image);
                List<LiveTvData.Programme> listings=timetable.get(id);
                LiveTvData.Programme nativeOnly=new LiveTvData.Programme(listings.get(0).start,listings.get(0).end,listings.get(0).title);
                equal(image,VelaAntennaListings.merge(listings,Collections.singletonList(nativeOnly),32).get(0).image);
                equal(image,VelaAntennaListings.merge(Collections.singletonList(nativeOnly),listings,32).get(0).image);
                LiveTvData.Programme different=new LiveTvData.Programme(nativeOnly.start,nativeOnly.end,"Different show","",image);
                equal("",VelaAntennaListings.merge(Collections.singletonList(nativeOnly),Collections.singletonList(different),32).get(0).image);
                equal(expected[i],guide.now.title);equal(next,guide.next.title);
                equal(expected[i],timetable.get(id).get(0).title);equal(next,timetable.get(id).get(1).title);
                equal(expected[i],antenna.get(id).get(0).title);equal(next,antenna.get(id).get(1).title);
            }
            // Previous builds recorded the concatenation and a recent successful fetch.
            Set<String> keys=new TreeSet<>();for(String name:names)keys.add(LiteXmlTvGuide.match(name));
            String legacyCatalog=LiveTvData.hash(LiteXmlTvGuide.URL+"|"+keys);
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(fullDb.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                db.execSQL("UPDATE programmes SET title=title||title");db.setVersion(3);
            }
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(liteDb.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                db.execSQL("UPDATE programmes SET title=title||title");db.execSQL("UPDATE meta SET catalog=?",new Object[]{legacyCatalog});
            }
            c.getSharedPreferences("xmltv",0).edit().putLong("attempt",now*1000).putString("attempt-source",LiveTvData.hash(full.url())).commit();
            c.getSharedPreferences("lite_xmltv",0).edit().putLong("attempt",now*1000).putString("attempt_catalog",legacyCatalog).commit();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(GZIPOutputStream gzip=new GZIPOutputStream(bytes)){gzip.write(raw);}
            int[] calls={0};
            boolean[] failFanart={false};
            List<String> requests=new ArrayList<>();
            RefinementHttp.install(url->{
                if(url.getHost().equals("playlist.example.invalid")){requests.add(url.toString());return playlistReply(url.toString(),now);}
                calls[0]++;requests.add(url.toString());
                if(failFanart[0]&&url.toString().equals(XmlTvGuide.DEFAULT_URL))return null;
                return bytes.toByteArray();
            },new HashSet<>(Arrays.asList("raw.githubusercontent.com","playlist.example.invalid")));
            equal(expected[0],full.guide(channels).get(channels.get(0).id).now.title);
            if(!lite.refresh(channels,new CancellationSignal()))throw new AssertionError("Legacy antenna cache stayed fresh");
            equal(expected[0],lite.read(channels,now,now+900,new CancellationSignal()).get(channels.get(0).id).get(0).title);
            full.guide(channels);lite.refresh(channels,new CancellationSignal());
            if(calls[0]!=2)throw new AssertionError("Unexpected refresh count "+calls[0]);
            String plain="https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv.xml";
            equal(XmlTvGuide.DEFAULT_URL,XmlTvGuide.artworkFeed(plain+".gz"));
            for(String custom:Arrays.asList("",plain+"?key=example","https://example.invalid/guiatv.xml",XmlTvGuide.DEFAULT_URL))
                equal(custom,XmlTvGuide.artworkFeed(custom));
            full.configure(plain);
            String station=channels.get(0).id;
            full.importGuide(new ByteArrayInputStream(raw),LiveTvData.hash(plain),now);
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(fullDb.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                db.execSQL("UPDATE programmes SET image=''");
            }
            requests.clear();failFanart[0]=true;
            equal(expected[0],full.guide(channels).get(station).now.title);
            equal("",full.window(channels,now).get(station).get(0).image);
            full.guide(channels);
            if(!requests.equals(Collections.singletonList(XmlTvGuide.DEFAULT_URL)))throw new AssertionError("Warm cache or retry throttle lost");
            failFanart[0]=false;full.invalidate();
            equal(image,full.guide(channels).get(station).now.image);
            equal(image,full.window(channels,now).get(station).get(0).image);
            full.guide(channels);
            if(requests.size()!=2)throw new AssertionError("Unnecessary second artwork import");
            failFanart[0]=true;requests.clear();full.configure(plain+".gz");
            equal(expected[0],full.guide(channels).get(station).now.title);
            if(!requests.equals(Arrays.asList(XmlTvGuide.DEFAULT_URL,plain+".gz")))throw new AssertionError("Missing cold-cache schedule fallback");
            equal(plain+".gz",full.url());
            checkPlaylistSettings(c,requests);
            checkDescriptions(full,lite,now);
            checkCategories(c,full,lite,now);
            checkSearch(c,full,lite,now);
            checkTimeFormats(c,full,lite,now);
            checkProgrammeOnlyGuide(c,full,lite,now);
            result.putString("result","PASS custom and advertised playlist guides with accented text, single XMLTV descriptions across all three readers, genre filters with split text and reversed category order, bounded category caches, programme-only feeds with exact, antenna and manual channel matches, antenna search and discovery with ambiguous aliases, exact manual IDs and offsets, invalid starts and old caches, legacy guide artwork upgrade, offline retention and cold fallback, retry limits, custom sources, three XMLTV readers, now and next, repeated and alternative titles, CDATA and entities, legitimate repeats, both warm-cache upgrades.");
        }catch(Exception|AssertionError failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{
            SQLiteDatabase.deleteDatabase(fullDb);SQLiteDatabase.deleteDatabase(liteDb);
            SQLiteDatabase.deleteDatabase(new File(root,"tv-catalogs/channels.db"));
            LiveTvData.prefs(c).edit().clear().commit();
            for(String url:Arrays.asList(GUIDE_A,GUIDE_B))LiveTvData.cached(c,"m3u-epg-"+LiveTvData.hash(url)+".data").delete();
            c.getSharedPreferences("xmltv",0).edit().clear().commit();c.getSharedPreferences("lite_xmltv",0).edit().clear().commit();
        }
        finish(result.containsKey("error")?1:-1,result);
    }
}
