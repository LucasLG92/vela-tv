package tv.vela.launcher;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.text.Normalizer;
import java.util.*;

/** Indexed, bounded disk catalogs. Only twelve decoded records leave a query. */
final class TvCatalogCache {
    static final int MAX_CHANNELS=100_000,MAX_GROUPS=512,MAX_DISK_BYTES=24*1024*1024;
    private final Context context;private final TvPrivateData secrets;
    TvCatalogCache(Context c){context=c.getApplicationContext();secrets=new TvPrivateData(c);}
    static final class Group {final String id,name;final int count;Group(String i,String n,int c){id=i;name=n;count=c;}}
    static final class Page {final List<LiveTvData.Channel> channels;final int page,total;Page(List<LiveTvData.Channel> c,int p,int t){channels=Collections.unmodifiableList(c);page=p;total=t;}}
    private SQLiteDatabase open(){synchronized(TvCatalogCache.class){
        File folder=new File(context.getCacheDir(),"tv-catalogs");folder.mkdirs();SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(new File(folder,"channels.db"),null);
        try{db.setMaximumSize(MAX_DISK_BYTES);try(Cursor pragma=db.rawQuery("PRAGMA secure_delete=ON",null)){pragma.moveToFirst();}
        db.execSQL("CREATE TABLE IF NOT EXISTS catalogs (source TEXT PRIMARY KEY, updated INTEGER NOT NULL, touched INTEGER NOT NULL, meta BLOB)");
        db.execSQL("CREATE TABLE IF NOT EXISTS channels (source TEXT NOT NULL, id TEXT NOT NULL, category TEXT NOT NULL, name TEXT NOT NULL, search TEXT NOT NULL, ordinal INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(source,id))");
        if(db.getVersion()<7){db.beginTransaction();try{boolean family=false;try(Cursor columns=db.rawQuery("PRAGMA table_info(channels)",null)){while(columns.moveToNext())if(columns.getString(1).equals("family"))family=true;}if(!family){db.execSQL("ALTER TABLE channels ADD COLUMN family TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE channels ADD COLUMN quality INTEGER NOT NULL DEFAULT 0");}try(Cursor old=db.rawQuery("SELECT source,id,name,category,family,quality FROM channels",null)){while(old.moveToNext()){String key=TvChannelNames.family(new LiveTvData.Channel(old.getString(1),old.getString(2),old.getString(3),"",Collections.emptyList()));int rank=TvChannelNames.rank(old.getString(2));if(key.equals(old.getString(4))&&rank==old.getInt(5))continue;ContentValues v=new ContentValues();v.put("family",key);v.put("quality",rank);db.update("channels",v,"source=? AND id=?",new String[]{old.getString(0),old.getString(1)});}}db.setVersion(7);db.setTransactionSuccessful();}finally{db.endTransaction();}}
        if(db.getVersion()<8){db.beginTransaction();try{db.execSQL("ALTER TABLE channels ADD COLUMN guide_name TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE channels ADD COLUMN guide_country TEXT NOT NULL DEFAULT ''");try(Cursor old=db.rawQuery("SELECT source,id,name,category FROM channels",null)){while(old.moveToNext()){ContentValues v=new ContentValues();v.put("guide_name",TvChannelNames.match(old.getString(2)));String country=TvChannelNames.country(old.getString(2));if(country.isEmpty())country=TvChannelNames.country(old.getString(3));v.put("guide_country",country);db.update("channels",v,"source=? AND id=?",new String[]{old.getString(0),old.getString(1)});}}db.setVersion(8);db.setTransactionSuccessful();}finally{db.endTransaction();}}
        db.execSQL("CREATE INDEX IF NOT EXISTS channel_page ON channels(source,category,ordinal)");db.execSQL("CREATE INDEX IF NOT EXISTS channel_order ON channels(source,ordinal)");db.execSQL("CREATE INDEX IF NOT EXISTS channel_family ON channels(source,family,quality DESC,ordinal)");db.execSQL("CREATE INDEX IF NOT EXISTS channel_guide ON channels(source,guide_name)");return db;}catch(RuntimeException failure){db.close();throw failure;}}
    }
    boolean fresh(String source,long age){try(SQLiteDatabase db=open();Cursor c=db.rawQuery("SELECT updated FROM catalogs WHERE source=?",new String[]{source})){return c.moveToFirst()&&System.currentTimeMillis()-c.getLong(0)>=0&&System.currentTimeMillis()-c.getLong(0)<age;}}
    String metadata(String source)throws Exception{try(SQLiteDatabase db=open();Cursor c=db.rawQuery("SELECT meta FROM catalogs WHERE source=?",new String[]{source})){return c.moveToFirst()&&!c.isNull(0)?secrets.decrypt(c.getBlob(0)):"";}}
    static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    static String json(LiveTvData.Channel ch)throws Exception{return new JSONObject().put("logo",ch.logo).put("streams",new JSONArray(ch.streams)).put("xc",ch.xtreamId).put("account",ch.xtreamAccount).put("input",ch.inputId).put("number",ch.number).put("epg",ch.epgId).put("archiveDays",ch.archiveDays).put("replay",ch.replayTemplate).put("replayMode",ch.replayMode).toString();}
    static LiveTvData.Channel channel(String id,String name,String group,String payload)throws Exception{
        JSONObject j=new JSONObject(payload);List<String> urls=new ArrayList<>();JSONArray a=j.optJSONArray("streams");for(int i=0;a!=null&&i<Math.min(4,a.length());i++)urls.add(a.getString(i));
        LiveTvData.Channel c=new LiveTvData.Channel(id,name,group,j.optString("logo"),urls,j.optString("xc"),j.optString("account"),j.optString("input"),j.optString("number"),j.optString("epg",id));c.archiveDays=Math.max(0,Math.min(7,j.optInt("archiveDays",0)));c.replayTemplate=j.optString("replay","");c.replayMode=j.optString("replayMode","");return c;
    }
    final class Writer implements AutoCloseable {
        private final SQLiteDatabase db;private final String source;private int ordinal;
        Writer(String value,int incoming){source=value;db=open();try{reserve(db,source,incoming);db.beginTransaction();db.delete("channels","source=?",new String[]{source});}catch(RuntimeException failure){if(db.inTransaction())db.endTransaction();db.close();throw failure;}}
        int size(){return ordinal;}
        void add(LiveTvData.Channel channel,boolean merge)throws Exception{
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(ordinal>=MAX_CHANNELS)throw new IOException("Channel limit");
            if(merge)try(Cursor existing=db.rawQuery("SELECT payload,name FROM channels WHERE source=? AND id=?",new String[]{source,channel.id})){if(existing.moveToFirst()){
                if(TvChannelNames.match(existing.getString(1)).equals(TvChannelNames.match(channel.name))&&!TvChannelNames.quality(existing.getString(1)).equals(TvChannelNames.quality(channel.name))){LiveTvData.Channel variant=new LiveTvData.Channel(channel.id+"~"+LiveTvData.hash(channel.name).substring(0,12),channel.name,channel.group,channel.logo,channel.streams,channel.xtreamId,channel.xtreamAccount,channel.inputId,channel.number,channel.epgId);variant.archiveDays=channel.archiveDays;variant.replayTemplate=channel.replayTemplate;variant.replayMode=channel.replayMode;add(variant,true);return;}
                JSONObject data=new JSONObject(secrets.decrypt(existing.getBlob(0)));JSONArray signals=data.getJSONArray("streams");Set<String> found=new LinkedHashSet<>();for(int i=0;i<signals.length();i++)found.add(signals.getString(i));for(String signal:channel.streams)if(found.size()<4)found.add(signal);data.put("streams",new JSONArray(found));ContentValues v=new ContentValues();v.put("payload",secrets.encrypt(data.toString()));db.update("channels",v,"source=? AND id=?",new String[]{source,channel.id});return;}}
            ContentValues v=new ContentValues();v.put("source",source);v.put("id",channel.id);v.put("category",channel.group);v.put("name",channel.name);v.put("search",normalize(channel.name));v.put("guide_name",TvChannelNames.match(channel.name));v.put("guide_country",TvChannelNames.country(channel));v.put("family",TvChannelNames.family(channel));v.put("quality",TvChannelNames.rank(channel.name));v.put("ordinal",ordinal++);v.put("payload",secrets.encrypt(json(channel)));db.insertWithOnConflict("channels",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        void commit(String metadata)throws Exception{
            try(Cursor c=db.rawQuery("SELECT COUNT(DISTINCT category) FROM channels WHERE source=?",new String[]{source})){c.moveToFirst();if(c.getInt(0)>MAX_GROUPS)throw new IOException("Category limit");}
            long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("source",source);v.put("updated",now);v.put("touched",now);v.put("meta",secrets.encrypt(metadata));db.insertWithOnConflict("catalogs",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            try(Cursor old=db.rawQuery("SELECT source FROM catalogs ORDER BY touched DESC LIMIT -1 OFFSET 4",null)){while(old.moveToNext()){String key=old.getString(0);if(!key.equals(source)){db.delete("channels","source=?",new String[]{key});db.delete("catalogs","source=?",new String[]{key});}}}
            db.setTransactionSuccessful();
        }
        public void close(){try{db.endTransaction();}finally{db.close();}}
    }
    Writer writer(String source){return new Writer(source,1024*1024);}
    Writer writer(String source,long responseBytes){int estimated=responseBytes<0?12*1024*1024:(int)Math.max(1024*1024,Math.min(12*1024*1024,responseBytes*5));return new Writer(source,estimated);}
    private static long scalar(SQLiteDatabase db,String query){try(Cursor c=db.rawQuery(query,null)){c.moveToFirst();return c.getLong(0);}}
    private static void reserve(SQLiteDatabase db,String source,int incoming){
        if((scalar(db,"PRAGMA page_count")-scalar(db,"PRAGMA freelist_count"))*scalar(db,"PRAGMA page_size")+incoming<MAX_DISK_BYTES)return;
        try(Cursor c=db.rawQuery("SELECT source FROM catalogs WHERE source<>? ORDER BY touched ASC",new String[]{source})){while(c.moveToNext()){String old=c.getString(0);db.delete("channels","source=?",new String[]{old});db.delete("catalogs","source=?",new String[]{old});if((scalar(db,"PRAGMA page_count")-scalar(db,"PRAGMA freelist_count"))*scalar(db,"PRAGMA page_size")+incoming<MAX_DISK_BYTES)break;}}
    }
    List<Group> groups(String source)throws IOException{List<Group> groups=new ArrayList<>();try(SQLiteDatabase db=open();Cursor c=db.rawQuery("SELECT category,COUNT(*) FROM channels WHERE source=? GROUP BY category ORDER BY MIN(ordinal)",new String[]{source})){while(c.moveToNext()){if(groups.size()>=MAX_GROUPS)throw new IOException("Category limit");groups.add(new Group(c.getString(0),c.getString(0),c.getInt(1)));}}return groups;}
    Page page(String source,String group,String query,Set<String> favorites,int requested)throws Exception{
        List<String> args=new ArrayList<>();args.add(source);String where="source=?";
        if(group!=null&&!group.isEmpty()){where+=" AND category=?";args.add(group);}
        if(!query.isEmpty()){where+=" AND search LIKE ? ESCAPE '\\'";args.add("%"+normalize(query).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");}
        if(favorites!=null){if(favorites.isEmpty())return new Page(new ArrayList<>(),0,0);StringBuilder values=new StringBuilder();for(String id:favorites){if(args.size()>640)break;if(values.length()>0)values.append(',');values.append('?');args.add(id);}where+=" AND id IN ("+values+")";}
        TvPersonalization state=new TvPersonalization(context,publicSource(source));Set<String> hidden=state.hiddenKeys();
        if(!hidden.isEmpty()){StringBuilder marks=new StringBuilder();for(String id:hidden){if(marks.length()>0)marks.append(',');marks.append('?');args.add(id);}where+=" AND family NOT IN ("+marks+")";}
        String[] selection=args.toArray(new String[0]);List<LiveTvData.Channel> out=new ArrayList<>();
        String ordering="first ASC";List<String> orderedArgs=new ArrayList<>(args);
        if(favorites!=null){StringBuilder priority=new StringBuilder("CASE family ");List<String> order=state.order();for(int i=0;i<order.size();i++){priority.append("WHEN ? THEN ").append(i).append(' ');orderedArgs.add(order.get(i));}if(!order.isEmpty())ordering=priority.append("ELSE 128 END,first ASC").toString();}
        try(SQLiteDatabase db=open()){
            int total;try(Cursor c=db.rawQuery("SELECT COUNT(DISTINCT family) FROM channels WHERE "+where,selection)){c.moveToFirst();total=c.getInt(0);}
            int page=Math.max(0,Math.min(requested,Math.max(0,(total-1)/LiveTvData.PAGE_SIZE)));
            try(Cursor c=db.query("channels",new String[]{"family","MIN(ordinal) AS first"},where,orderedArgs.toArray(new String[0]),"family",null,ordering,LiveTvData.PAGE_SIZE+" OFFSET "+page*LiveTvData.PAGE_SIZE)){while(c.moveToNext()){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.add(TvChannelNames.choose(context,publicSource(source),variants(db,"source=? AND family=?",new String[]{source,c.getString(0)})));}}
            ContentValues touched=new ContentValues();touched.put("touched",System.currentTimeMillis());db.update("catalogs",touched,"source=?",new String[]{source});return new Page(out,page,total);
        }
    }
    List<LiveTvData.Channel> favoriteItems(String source,Set<String> ids)throws Exception{
        if(ids.isEmpty())return new ArrayList<>();List<String> args=new ArrayList<>();args.add(source);StringBuilder marks=new StringBuilder();for(String id:ids){if(args.size()>128)break;if(marks.length()>0)marks.append(',');marks.append('?');args.add(id);}List<LiveTvData.Channel> out=new ArrayList<>();
        try(SQLiteDatabase db=open();Cursor c=db.rawQuery("SELECT id,name,category FROM channels WHERE source=? AND id IN ("+marks+") GROUP BY family ORDER BY MIN(ordinal) LIMIT 128",args.toArray(new String[0]))){while(c.moveToNext())out.add(new LiveTvData.Channel(c.getString(0),c.getString(1),c.getString(2),"",Collections.emptyList()));}return out;
    }
    List<LiveTvData.Channel> restoreHistory(String source,List<LiveTvData.Channel> saved)throws Exception{
        List<LiveTvData.Channel> out=new ArrayList<>();try(SQLiteDatabase db=open()){for(LiveTvData.Channel ch:saved){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();List<LiveTvData.Channel> choices=source.startsWith("xtream:")?variants(db,"substr(source,1,?)=? AND family=?",new String[]{Integer.toString(("xc:"+source.substring(7)+":").length()),"xc:"+source.substring(7)+":",TvChannelNames.family(ch)}):variants(db,"source=? AND family=?",new String[]{source,TvChannelNames.family(ch)});out.add(choices.isEmpty()?ch:TvChannelNames.choose(context,source,choices));}}return out;
    }
    private static String publicSource(String value){return value.startsWith("xc:")?"xtream:"+value.split(":")[1]:value;}
    private List<LiveTvData.Channel> variants(SQLiteDatabase db,String where,String[] args)throws Exception{Map<String,LiveTvData.Channel> primary=new LinkedHashMap<>();List<LiveTvData.Channel> extra=new ArrayList<>();Set<String> seen=new HashSet<>();try(Cursor c=db.query("channels",new String[]{"id","name","category","payload"},where,args,null,null,"quality DESC,ordinal ASC","32")){while(c.moveToNext())if(seen.add(c.getString(0))){LiveTvData.Channel ch=channel(c.getString(0),c.getString(1),c.getString(2),secrets.decrypt(c.getBlob(3)));String q=TvChannelNames.quality(ch.name);if(!primary.containsKey(q)&&primary.size()<TvChannelNames.MAX_VARIANTS)primary.put(q,ch);else if(extra.size()<TvChannelNames.MAX_VARIANTS)extra.add(ch);}}List<LiveTvData.Channel> out=new ArrayList<>(primary.values());for(LiveTvData.Channel ch:extra)if(out.size()<TvChannelNames.MAX_VARIANTS)out.add(ch);out.sort((a,b)->Integer.compare(TvChannelNames.rank(b.name),TvChannelNames.rank(a.name)));return out;}
    LiveTvData.Channel favoriteVariant(String prefix,LiveTvData.Channel saved)throws Exception{try(SQLiteDatabase db=open()){List<LiveTvData.Channel> values=variants(db,"substr(source,1,?)=? AND family=?",new String[]{Integer.toString(prefix.length()),prefix,TvChannelNames.family(saved)});return values.isEmpty()?saved:TvChannelNames.choose(context,"xtream:"+prefix.split(":")[1],values);}}
    void remove(String source){try(SQLiteDatabase db=open()){db.delete("channels","source=?",new String[]{source});db.delete("catalogs","source=?",new String[]{source});}}
    void removePrefix(String prefix){try(SQLiteDatabase db=open()){db.delete("channels","substr(source,1,?)=?",new String[]{Integer.toString(prefix.length()),prefix});db.delete("catalogs","substr(source,1,?)=?",new String[]{Integer.toString(prefix.length()),prefix});}}
    List<LiveTvData.Channel> favoritePrefix(String prefix,Set<String> ids)throws Exception{
        List<LiveTvData.Channel> out=new ArrayList<>();if(ids.isEmpty())return out;List<String> args=new ArrayList<>();args.add(Integer.toString(prefix.length()));args.add(prefix);StringBuilder marks=new StringBuilder();for(String id:ids){if(marks.length()>0)marks.append(',');marks.append('?');args.add(id);if(args.size()>=130)break;}
        try(SQLiteDatabase db=open();Cursor rows=db.rawQuery("SELECT id,name,category,payload FROM channels WHERE substr(source,1,?)=? AND id IN ("+marks+") GROUP BY id ORDER BY MIN(ordinal) LIMIT 128",args.toArray(new String[0]))){while(rows.moveToNext())out.add(channel(rows.getString(0),rows.getString(1),rows.getString(2),secrets.decrypt(rows.getBlob(3))));}return out;
    }
    List<VelaShows.Airing> airings(String query,int page,android.os.CancellationSignal signal)throws Exception{
        long now=System.currentTimeMillis()/1000;return events(query,page,signal,now,now+48*3600,0);
    }
    List<VelaShows.Airing> discover(int mode,int genre,int page,android.os.CancellationSignal signal)throws Exception{long[] w=VelaDiscovery.bounds(mode);return events("",page,signal,w[0],w[1],genre);}
    private List<VelaShows.Airing> events(String query,int page,android.os.CancellationSignal signal,long from,long to,int genre)throws Exception{
        File file=new File(context.getCacheDir(),"tv-guide/xmltv.db");if(!file.isFile())return Collections.emptyList();String url=new XmlTvGuide(context).url();if(url.isEmpty())return Collections.emptyList();String source=LiveTvData.source(context),prefix=source.startsWith("xtream:")?"xc:"+source.substring(7)+":":source;
        List<VelaShows.Airing> out=new ArrayList<>();try(SQLiteDatabase db=open()){
            db.execSQL("ATTACH DATABASE ? AS epg",new Object[]{file.getAbsolutePath()});try(Cursor meta=db.rawQuery("SELECT source FROM epg.meta WHERE id=1",null)){if(!meta.moveToFirst()||!LiveTvData.hash(url).equals(meta.getString(0)))return out;}
            VelaGuideFix.sql(context,db);String scope=source.startsWith("xtream:")?"substr(c.source,1,?)=?":"c.source=?";List<String> args=new ArrayList<>();if(source.startsWith("xtream:"))args.add(Integer.toString(prefix.length()));args.add(prefix);args.add(Long.toString(from));args.add(Long.toString(to));if(genre>0&&!VelaDiscovery.categoryColumn(db,"epg."))return out;
            Set<String> hidden=new TvPersonalization(context,source).hiddenKeys();int skipped=0;String normalized=VelaWorkspace.normal(query);
            try(Cursor rows=db.rawQuery("SELECT p.start+coalesce(f.offset,0),p.end+coalesce(f.offset,0),p.title,c.name,c.family FROM epg.programmes p JOIN epg.aliases a ON a.channel=p.channel JOIN channels c LEFT JOIN vela_guide_fixes f ON c.guide_name=f.name WHERE a.alias=CASE WHEN f.alias<>'' THEN f.alias ELSE 'name:'||c.guide_name END AND "+scope+" AND (c.guide_country='' OR c.guide_country='ES' OR f.alias<>'') AND p.end+coalesce(f.offset,0)>CAST(? AS INTEGER) AND p.start+coalesce(f.offset,0)<CAST(? AS INTEGER) "+VelaDiscovery.predicate(genre,"p.category")+" GROUP BY p.channel,p.start,p.title,c.family ORDER BY p.start+coalesce(f.offset,0) LIMIT 100000",args.toArray(new String[0]))){while(rows.moveToNext()){signal.throwIfCanceled();if(hidden.contains(rows.getString(4))||!VelaWorkspace.normal(rows.getString(2)).contains(normalized))continue;if(skipped++<page*24)continue;out.add(new VelaShows.Airing(rows.getString(2),TvChannelNames.display(rows.getString(3)),rows.getLong(0),rows.getLong(1)));if(out.size()>=24)break;}}
        }return out;
    }
}
