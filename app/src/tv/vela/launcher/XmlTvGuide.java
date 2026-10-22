package tv.vela.launcher;

import android.content.*;import android.database.*;import android.database.sqlite.*;import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;import java.io.*;import java.net.*;import java.util.*;import java.util.zip.GZIPInputStream;

/** Disk-indexed external XMLTV. Only the visible channel/time window enters memory. */
final class XmlTvGuide {
    static final String DEFAULT_URL="https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv.xml";
    static final int MAX_BYTES=64*1024*1024,MAX_DB=24*1024*1024;
    private final Context context;private final SharedPreferences settings;private final TvPrivateData secrets;
    XmlTvGuide(Context c){context=c.getApplicationContext();settings=c.getSharedPreferences("xmltv",0);secrets=new TvPrivateData(c);}
    String url()throws Exception{return settings.getBoolean("native",false)?"":settings.contains("url")?secrets.decode(settings.getString("url","")):DEFAULT_URL;}
    void configure(String value)throws Exception{String address=value.trim();if(!address.isEmpty()&&!LiveTvData.https(address))throw new IOException("HTTPS XMLTV required");SharedPreferences.Editor e=settings.edit().putBoolean("native",address.isEmpty()).remove("attempt").remove("attempt-source");if(address.isEmpty())e.remove("url");else e.putString("url",secrets.encode(address));if(!e.commit())throw new IOException("Could not save guide");}
    void invalidate(){settings.edit().putBoolean("refresh",true).remove("attempt").remove("attempt-source").apply();}
    private SQLiteDatabase open(){File folder=new File(context.getCacheDir(),"tv-guide");folder.mkdirs();SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(new File(folder,"xmltv.db"),null);try{db.setMaximumSize(MAX_DB);db.execSQL("CREATE TABLE IF NOT EXISTS meta (id INTEGER PRIMARY KEY, source TEXT, updated INTEGER)");db.execSQL("CREATE TABLE IF NOT EXISTS aliases (alias TEXT, channel TEXT, PRIMARY KEY(alias,channel))");db.execSQL("CREATE TABLE IF NOT EXISTS programmes (channel TEXT, start INTEGER, end INTEGER, title TEXT)");db.execSQL("CREATE TABLE IF NOT EXISTS logos (channel TEXT PRIMARY KEY, image TEXT)");if(db.getVersion()<2){db.execSQL("UPDATE meta SET updated=0");db.setVersion(2);}db.execSQL("CREATE INDEX IF NOT EXISTS programme_time ON programmes(channel,start,end)");return db;}catch(RuntimeException e){db.close();throw e;}}
    Map<String,LiveTvData.Guide> guide(List<LiveTvData.Channel> visible)throws Exception{
        String address=url();if(address.isEmpty()||visible.isEmpty())return Collections.emptyMap();String source=LiveTvData.hash(address);synchronized(XmlTvGuide.class){
            long now=System.currentTimeMillis(),updated=0;String cached="";try(SQLiteDatabase db=open();Cursor row=db.rawQuery("SELECT source,updated FROM meta WHERE id=1",null)){if(row.moveToFirst()){cached=row.getString(0);updated=row.getLong(1);}}
            boolean fresh=!settings.getBoolean("refresh",false)&&cached.equals(source)&&now-updated>=0&&now-updated<4*3600_000L;
            if(!fresh&&(!source.equals(settings.getString("attempt-source",""))||now-settings.getLong("attempt",0)>15*60_000||now<settings.getLong("attempt",0))){
                settings.edit().putBoolean("refresh",false).putString("attempt-source",source).putLong("attempt",now).commit();File[] leftover=new File(context.getCacheDir(),"tv-guide").listFiles((folder,name)->(name.startsWith("xmltv-")||name.startsWith("fetch-"))&&name.endsWith(".part"));if(leftover!=null)for(File old:leftover)if(old.isFile())old.delete();File file=File.createTempFile("xmltv-",".part",new File(context.getCacheDir(),"tv-guide"));try{LiveTvData.download(address,file,MAX_BYTES);try(InputStream input=new FileInputStream(file)){importGuide(input,source,now/1000);}cached=source;}catch(Exception failed){if(Thread.currentThread().isInterrupted()){settings.edit().putBoolean("refresh",true).remove("attempt").remove("attempt-source").commit();throw failed;}}finally{file.delete();}
            }
            if(!cached.equals(source))return Collections.emptyMap();return read(visible,now/1000);
        }
    }
    private static void bind(SQLiteStatement statement,String alias,String channel){statement.bindString(1,alias);statement.bindString(2,channel);statement.executeInsert();}
    static String exact(String text){return "id:"+TvCatalogCache.normalize(text.trim());}
    void importGuide(InputStream raw,String source,long now)throws Exception{
        try(PushbackInputStream peek=new PushbackInputStream(raw,2)){int a=peek.read(),b=peek.read();if(b>=0)peek.unread(b);if(a>=0)peek.unread(a);InputStream decoded=a==31&&b==139?new GZIPInputStream(peek):peek;
            XmlPullParser parser=Xml.newPullParser();parser.setInput(new M3uClient.LimitedInput(decoded,MAX_BYTES),null);
            try(SQLiteDatabase db=open();SQLiteStatement alias=db.compileStatement("INSERT OR IGNORE INTO aliases(alias,channel) VALUES (?,?)");SQLiteStatement programme=db.compileStatement("INSERT INTO programmes(channel,start,end,title) VALUES (?,?,?,?)")){
                db.beginTransaction();try{db.delete("aliases",null,null);db.delete("programmes",null,null);db.delete("logos",null,null);String channel="",title="",kind="";long start=0,end=0;boolean keep=false,inTitle=false,inName=false;StringBuilder name=new StringBuilder();int channels=0,events=0,programmes=0,inserted=0,names=0;
                    for(int event=parser.nextToken();event!=XmlPullParser.END_DOCUMENT;event=parser.nextToken()){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(++events>8_000_000||event==XmlPullParser.DOCDECL)throw new IOException("XMLTV limit");
                        if(event==XmlPullParser.START_TAG){String tag=parser.getName();
                            if(tag.equals("channel")){if(++channels>4096)throw new IOException("Channel limit");channel=parser.getAttributeValue(null,"id");if(channel==null||channel.length()>256)throw new IOException("Channel ID limit");kind="channel";names=0;bind(alias,exact(channel),channel);bind(alias,"name:"+TvChannelNames.match(channel),channel);}
                            else if(tag.equals("icon")&&kind.equals("channel")){String image=parser.getAttributeValue(null,"src");if(image!=null&&image.length()<=4096&&LiveTvData.https(image)){ContentValues logo=new ContentValues();logo.put("channel",channel);logo.put("image",secrets.encode(image));db.insertWithOnConflict("logos",null,logo,SQLiteDatabase.CONFLICT_REPLACE);}}
                            else if(tag.equals("display-name")&&kind.equals("channel")){name.setLength(0);inName=true;}
                            else if(tag.equals("programme")){if(++programmes>150000)throw new IOException("Programme limit");channel=parser.getAttributeValue(null,"channel");start=M3uClient.timestamp(parser.getAttributeValue(null,"start"));end=M3uClient.timestamp(parser.getAttributeValue(null,"stop"));keep=channel!=null&&channel.length()<=256&&end>now&&start<now+48*3600&&end>start;title="";kind="programme";}
                            else if(tag.equals("title")&&keep)inTitle=true;
                        }else if(event==XmlPullParser.TEXT||event==XmlPullParser.ENTITY_REF){String text=parser.getText();if(text!=null){if(inName&&name.length()<160)name.append(text,0,Math.min(text.length(),160-name.length()));if(inTitle&&title.length()<512)title+=text.substring(0,Math.min(text.length(),512-title.length()));}}
                        else if(event==XmlPullParser.END_TAG){String tag=parser.getName();if(tag.equals("display-name")){inName=false;if(kind.equals("channel")&&++names<=32){bind(alias,"name:"+TvChannelNames.match(name.toString()),channel);bind(alias,exact(name.toString()),channel);}}
                            else if(tag.equals("title"))inTitle=false;
                            else if(tag.equals("programme")){if(keep&&!title.isEmpty()){if(++inserted>100000)throw new IOException("Programme storage limit");programme.bindString(1,channel);programme.bindLong(2,start);programme.bindLong(3,end);programme.bindString(4,title);programme.executeInsert();}keep=false;inTitle=false;kind="";}
                            else if(tag.equals("channel")){kind="";inName=false;}
                        }
                    }
                    if(channels==0||inserted==0)throw new IOException("No current XMLTV schedule");ContentValues value=new ContentValues();value.put("id",1);value.put("source",source);value.put("updated",System.currentTimeMillis());db.insertWithOnConflict("meta",null,value,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
        }
    }
    private static String resolve(SQLiteDatabase db,String key){try(Cursor row=db.rawQuery("SELECT channel FROM aliases WHERE alias=? LIMIT 2",new String[]{key})){if(!row.moveToFirst())return key.equals("name:fdf")?resolve(db,"name:factoriadeficcion"):key.equals("name:trecetv")||key.equals("name:eltrece")?resolve(db,"name:trece"):"";String id=row.getString(0);return row.moveToNext()?"":id;}}
    String logo(LiveTvData.Channel ch)throws Exception{String address=url();if(address.isEmpty())return "";try(SQLiteDatabase db=open();Cursor meta=db.rawQuery("SELECT source FROM meta WHERE id=1",null)){if(!meta.moveToFirst()||!meta.getString(0).equals(LiveTvData.hash(address)))return "";String id=resolve(db,exact(ch.epgId)),country=TvChannelNames.country(ch);if(id.isEmpty()&&(country.isEmpty()||country.equals("ES")))id=resolve(db,"name:"+TvChannelNames.match(ch.name));if(id.isEmpty())return "";try(Cursor logo=db.rawQuery("SELECT image FROM logos WHERE channel=?",new String[]{id})){return logo.moveToFirst()?secrets.decode(logo.getString(0)):"";}}}
    Map<String,LiveTvData.Guide> read(List<LiveTvData.Channel> visible,long now){Map<String,LiveTvData.Guide> result=new HashMap<>();try(SQLiteDatabase db=open()){int count=0;for(LiveTvData.Channel ch:visible){if(++count>LiveTvData.PAGE_SIZE||Thread.currentThread().isInterrupted())break;String id=resolve(db,exact(ch.epgId));String country=TvChannelNames.country(ch);if(id.isEmpty()&&(country.isEmpty()||country.equals("ES")))id=resolve(db,"name:"+TvChannelNames.match(ch.name));if(id.isEmpty())continue;LiveTvData.Guide guide=new LiveTvData.Guide();try(Cursor rows=db.rawQuery("SELECT start,end,title FROM programmes WHERE channel=? AND end>? ORDER BY start LIMIT 8",new String[]{id,Long.toString(now)})){while(rows.moveToNext())guide.add(new LiveTvData.Programme(rows.getLong(0),rows.getLong(1),rows.getString(2)),now);}if(guide.now!=null||guide.next!=null)result.put(ch.id,guide);}}return result;}
    Map<String,List<LiveTvData.Programme>> window(List<LiveTvData.Channel> visible,long start)throws Exception{
        Map<String,List<LiveTvData.Programme>> result=new HashMap<>();String address=url();if(address.isEmpty())return result;
        synchronized(XmlTvGuide.class){try(SQLiteDatabase db=open();Cursor meta=db.rawQuery("SELECT source FROM meta WHERE id=1",null)){
            if(!meta.moveToFirst()||!meta.getString(0).equals(LiveTvData.hash(address)))return result;
            int count=0;for(LiveTvData.Channel ch:visible){if(++count>TvTimelineView.ROWS||Thread.currentThread().isInterrupted())break;
                String id=resolve(db,exact(ch.epgId)),country=TvChannelNames.country(ch);if(id.isEmpty()&&(country.isEmpty()||country.equals("ES")))id=resolve(db,"name:"+TvChannelNames.match(ch.name));if(id.isEmpty())continue;
                List<LiveTvData.Programme> list=new ArrayList<>();try(Cursor rows=db.rawQuery("SELECT start,end,title FROM programmes WHERE channel=? AND end>? AND start<? ORDER BY start LIMIT "+TvTimelineView.MAX_PROGRAMMES,new String[]{id,Long.toString(start),Long.toString(start+TvTimelineView.SECONDS)})){
                    while(rows.moveToNext())list.add(new LiveTvData.Programme(rows.getLong(0),rows.getLong(1),rows.getString(2)));
                }if(!list.isEmpty())result.put(ch.id,list);
            }
        }}return result;
    }
}
