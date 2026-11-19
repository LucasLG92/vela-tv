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
            List<LiveTvData.Channel> channels=new ArrayList<>();StringBuilder xml=new StringBuilder("<tv>");
            for(int i=0;i<names.length;i++){
                channels.add(new LiveTvData.Channel(i+1,"test",""+(i+1),names[i],false,true,false));
                xml.append("<channel id='c").append(i).append("'><display-name>").append(names[i]).append("</display-name></channel>");
            }
            Set<String> ids=new HashSet<>();
            for(int i=0;i<names.length;i++){
                String id="c"+i;ids.add(id);
                xml.append(programme(id,now-300,now+300,"<title lang='en'>"+expected[i]+"</title><title lang='es'>"+expected[i]+"</title>"));
                xml.append(programme(id,now+300,now+900,"<title>  </title><title>Bora <![CDATA[Bora]]> &amp; [COLOR tomato]música[/COLOR]</title><title lang='fr'>Autre titre</title>"));
            }
            xml.append("</tv>");String document=xml.toString();byte[] raw=document.getBytes(StandardCharsets.UTF_8);
            Map<String,LiveTvData.Guide> playlist=M3uClient.xmlGuide(new StringReader(document),ids,now);
            XmlTvGuide full=new XmlTvGuide(c);LiteXmlTvGuide lite=new LiteXmlTvGuide(c);
            full.importGuide(new ByteArrayInputStream(raw),LiveTvData.hash(full.url()),now);
            lite.importGuide(new ByteArrayInputStream(raw),channels,now,new CancellationSignal());
            Map<String,List<LiveTvData.Programme>> timetable=full.window(channels,now);
            Map<String,List<LiveTvData.Programme>> antenna=lite.read(channels,now,now+900,new CancellationSignal());
            for(int i=0;i<channels.size();i++){
                String id=channels.get(i).id;LiveTvData.Guide guide=playlist.get("c"+i);
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
            RefinementHttp.install(url->{calls[0]++;return bytes.toByteArray();},Collections.singleton("raw.githubusercontent.com"));
            equal(expected[0],full.guide(channels).get(channels.get(0).id).now.title);
            if(!lite.refresh(channels,new CancellationSignal()))throw new AssertionError("Legacy antenna cache stayed fresh");
            equal(expected[0],lite.read(channels,now,now+900,new CancellationSignal()).get(channels.get(0).id).get(0).title);
            full.guide(channels);lite.refresh(channels,new CancellationSignal());
            if(calls[0]!=2)throw new AssertionError("Unexpected refresh count "+calls[0]);
            result.putString("result","PASS three XMLTV readers, now and next, repeated and alternative titles, CDATA and entities, legitimate repeats, both warm-cache upgrades.");
        }catch(Exception|AssertionError failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{
            SQLiteDatabase.deleteDatabase(fullDb);SQLiteDatabase.deleteDatabase(liteDb);
            c.getSharedPreferences("xmltv",0).edit().clear().commit();c.getSharedPreferences("lite_xmltv",0).edit().clear().commit();
        }
        finish(result.containsKey("error")?1:-1,result);
    }
}
