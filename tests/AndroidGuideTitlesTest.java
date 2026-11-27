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
                calls[0]++;requests.add(url.toString());
                if(failFanart[0]&&url.toString().equals(XmlTvGuide.DEFAULT_URL))return null;
                return bytes.toByteArray();
            },Collections.singleton("raw.githubusercontent.com"));
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
            checkSearch(c,full,lite,now);
            result.putString("result","PASS antenna search and discovery with ambiguous aliases, exact manual IDs and offsets, invalid starts and old caches, legacy guide artwork upgrade, offline retention and cold fallback, retry limits, custom sources, three XMLTV readers, now and next, repeated and alternative titles, CDATA and entities, legitimate repeats, both warm-cache upgrades.");
        }catch(Exception|AssertionError failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{
            SQLiteDatabase.deleteDatabase(fullDb);SQLiteDatabase.deleteDatabase(liteDb);
            c.getSharedPreferences("xmltv",0).edit().clear().commit();c.getSharedPreferences("lite_xmltv",0).edit().clear().commit();
        }
        finish(result.containsKey("error")?1:-1,result);
    }
}
