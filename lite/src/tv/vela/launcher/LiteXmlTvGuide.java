package tv.vela.launcher;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.CancellationSignal;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;

/** Streaming public programme metadata. No playlists, stream URLs, IPTV accounts or playback code. */
final class LiteXmlTvGuide {
    static final String URL="https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv_light.xml.gz";
    static final int MAX_COMPRESSED=16*1024*1024,MAX_XML=128*1024*1024,MAX_DB=8*1024*1024;
    private static final ReentrantLock IMPORT=new ReentrantLock();
    private final Context context;private final File file;
    LiteXmlTvGuide(Context c){this(c,"programmes.db");}
    LiteXmlTvGuide(Context c,String name){context=c.getApplicationContext();File folder=new File(context.getCacheDir(),"antenna-epg");folder.mkdirs();file=new File(folder,name);}
    private boolean enabled(){return context.getSharedPreferences("lite_tv",0).getBoolean("online_guide",true);}
    void invalidate(){context.getSharedPreferences("lite_xmltv",0).edit().remove("attempt").putBoolean("refresh",true).apply();}
    private SQLiteDatabase open(){SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null);db.setMaximumSize(MAX_DB);db.execSQL("CREATE TABLE IF NOT EXISTS meta (id INTEGER PRIMARY KEY, catalog TEXT, updated INTEGER)");db.execSQL("CREATE TABLE IF NOT EXISTS aliases (name TEXT, channel TEXT, PRIMARY KEY(name,channel))");db.execSQL("CREATE TABLE IF NOT EXISTS logos (channel TEXT PRIMARY KEY, image TEXT)");db.execSQL("CREATE TABLE IF NOT EXISTS programmes (channel TEXT, start INTEGER, end INTEGER, title TEXT, description TEXT)");db.execSQL("CREATE INDEX IF NOT EXISTS schedule ON programmes(channel,start,end)");return db;}
    static String match(String value){String n=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceFirst("\\.tv$","").replaceAll("\\b(uhd|fhd|hd|sd|4k|8k|720p?|1080p?)\\b","").replaceAll("[^a-z0-9+]","");switch(n){case "a3series":return "atreseries";case "bemadtv":return "bemad";case "realmadridtv":return "realmadrid";case "tdp":return "teledeporte";case "24horas":return "24h";case "hdcastillalamancha":case "cmm":return "castillalamancha";case "factoriadeficcion":return "fdf";case "13tv":case "trecetv":return "trece";default:return n;}}
    private static Set<String> wanted(List<LiveTvData.Channel> channels){Set<String> keys=new TreeSet<>();for(LiveTvData.Channel c:channels)keys.add(match(c.name));return keys;}
    private static String signature(Set<String> keys){return LiveTvData.hash(keys.toString());}
    private static void check(CancellationSignal signal)throws InterruptedIOException{if(signal.isCanceled()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
    boolean refresh(List<LiveTvData.Channel> channels,CancellationSignal signal)throws Exception {
        if(!enabled()||channels.isEmpty()||!IMPORT.tryLock())return false;
        android.content.SharedPreferences prefs=context.getSharedPreferences("lite_xmltv",0);HttpURLConnection connection=null;
        try{check(signal);long now=System.currentTimeMillis(),updated=0;String catalog="",key=signature(wanted(channels));try(SQLiteDatabase db=open();Cursor c=db.rawQuery("SELECT catalog,updated FROM meta WHERE id=1",null)){if(c.moveToFirst()){catalog=c.getString(0);updated=c.getLong(1);}}
            if(!prefs.getBoolean("refresh",false)&&catalog.equals(key)&&now>=updated&&now-updated<4*3600_000L)return false;
            long attempt=prefs.getLong("attempt",0);if(key.equals(prefs.getString("attempt_catalog",""))&&now>=attempt&&now-attempt<15*60_000L)return false;prefs.edit().putLong("attempt",now).putString("attempt_catalog",key).apply();
            connection=(HttpURLConnection)new URL(URL).openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("User-Agent","Vela-Lite-Guide/0.2");final HttpURLConnection active=connection;signal.setOnCancelListener(active::disconnect);
            if(connection.getResponseCode()!=200||connection.getContentLengthLong()>MAX_COMPRESSED)throw new IOException("Guide unavailable");
            try(InputStream in=new GZIPInputStream(new Limited(connection.getInputStream(),MAX_COMPRESSED,signal),16384)){importGuide(in,channels,System.currentTimeMillis()/1000,signal);}prefs.edit().putBoolean("refresh",false).apply();return true;
        }catch(Exception failed){if(signal.isCanceled()||Thread.currentThread().isInterrupted()||failed instanceof InterruptedIOException)prefs.edit().remove("attempt").apply();throw failed;}
        finally{signal.setOnCancelListener(null);if(connection!=null)connection.disconnect();IMPORT.unlock();}
    }
    private static final ThreadLocal<SimpleDateFormat> DATES=ThreadLocal.withInitial(()->{SimpleDateFormat d=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.ROOT);d.setLenient(false);return d;});
    static long timestamp(String value){if(value==null)return 0;String date=value.trim();if(!date.matches("[0-9]{14}( [+-][0-9]{4})?"))return 0;try{SimpleDateFormat format=DATES.get();return format.parse(date.length()==14?date+" +0000":date).getTime()/1000;}catch(ParseException bad){return 0;}}
    void importGuide(InputStream raw,List<LiveTvData.Channel> channels,long now,CancellationSignal signal)throws Exception {
        Set<String> wanted=wanted(channels),ids=new HashSet<>();XmlPullParser xml=Xml.newPullParser();xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);xml.setInput(new Limited(raw,MAX_XML,signal),null);
        try(SQLiteDatabase db=open();SQLiteStatement alias=db.compileStatement("INSERT OR IGNORE INTO aliases(name,channel) VALUES (?,?)");SQLiteStatement programme=db.compileStatement("INSERT INTO programmes(channel,start,end,title,description) VALUES (?,?,?,?,?)")){
            db.beginTransaction();try{db.delete("aliases",null,null);db.delete("logos",null,null);db.delete("programmes",null,null);String id="",kind="",image="",title="",description="";StringBuilder value=new StringBuilder();Set<String> names=new HashSet<>();boolean inName=false,inTitle=false,inDescription=false,keep=false,complete=false;long from=0,to=0;int events=0,declared=0,scanned=0,stored=0;
                for(int event=xml.nextToken();event!=XmlPullParser.END_DOCUMENT;event=xml.nextToken()){check(signal);if(++events>8_000_000||event==XmlPullParser.DOCDECL)throw new IOException("Unsupported XMLTV");
                    if(event==XmlPullParser.START_TAG){String tag=xml.getName();if(xml.getDepth()>32||xml.getDepth()==1&&!tag.equals("tv"))throw new IOException("Unsupported XMLTV root");if(tag.equals("channel")){if(++declared>8192)throw new IOException("Channel limit");id=xml.getAttributeValue(null,"id");if(id==null||id.length()>256)throw new IOException("Channel ID limit");kind="channel";names.clear();names.add(match(id));image="";}
                        else if(tag.equals("icon")&&kind.equals("channel")){String u=xml.getAttributeValue(null,"src");if(logoUrl(u))image=u;}
                        else if(tag.equals("display-name")&&kind.equals("channel")){inName=true;value.setLength(0);}
                        else if(tag.equals("programme")){if(++scanned>400000)throw new IOException("Programme limit");id=xml.getAttributeValue(null,"channel");boolean relevant=ids.contains(id);from=relevant?timestamp(xml.getAttributeValue(null,"start")):0;to=relevant?timestamp(xml.getAttributeValue(null,"stop")):0;keep=relevant&&to>now-3600&&from<now+48*3600&&to>from;title="";description="";kind="programme";}
                        else if(tag.equals("title")&&keep)inTitle=true;else if(tag.equals("desc")&&keep)inDescription=true;
                    }else if(event==XmlPullParser.TEXT||event==XmlPullParser.ENTITY_REF){String text=xml.getText();if(text!=null){if(inName&&value.length()<160)value.append(text,0,Math.min(160-value.length(),text.length()));if(inTitle&&title.length()<512)title+=text.substring(0,Math.min(512-title.length(),text.length()));if(inDescription&&description.length()<1000)description+=text.substring(0,Math.min(1000-description.length(),text.length()));}}
                    else if(event==XmlPullParser.END_TAG){String tag=xml.getName();if(tag.equals("tv")&&xml.getDepth()==1)complete=true;if(tag.equals("display-name")){inName=false;if(names.size()<32)names.add(match(value.toString()));}
                        else if(tag.equals("channel")){boolean found=false;for(String n:names)if(wanted.contains(n)){alias.bindString(1,n);alias.bindString(2,id);alias.executeInsert();found=true;}if(found){ids.add(id);if(!image.isEmpty()){ContentValues row=new ContentValues();row.put("channel",id);row.put("image",image);db.insertWithOnConflict("logos",null,row,SQLiteDatabase.CONFLICT_REPLACE);}}kind="";}
                        else if(tag.equals("title"))inTitle=false;else if(tag.equals("desc"))inDescription=false;
                        else if(tag.equals("programme")){if(keep&&!title.isEmpty()){if(++stored>16000)throw new IOException("Storage limit");programme.bindString(1,id);programme.bindLong(2,from);programme.bindLong(3,to);programme.bindString(4,title);programme.bindString(5,description);programme.executeInsert();}keep=inTitle=inDescription=false;kind="";}
                    }
                }
                if(!complete||declared==0||ids.isEmpty()||stored==0)throw new IOException("No complete matching schedule");ContentValues meta=new ContentValues();meta.put("id",1);meta.put("catalog",signature(wanted));meta.put("updated",System.currentTimeMillis());db.insertWithOnConflict("meta",null,meta,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
    }
    private static String resolve(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT channel FROM aliases WHERE name=? LIMIT 2",new String[]{match(name)})){if(!c.moveToFirst())return "";String id=c.getString(0);return c.moveToNext()?"":id;}}
    Map<String,List<LiveTvData.Programme>> read(List<LiveTvData.Channel> visible,long from,long to,CancellationSignal signal){Map<String,List<LiveTvData.Programme>> result=new HashMap<>();if(!enabled()||!file.exists())return result;try(SQLiteDatabase db=open()){int count=0;for(LiveTvData.Channel channel:visible){check(signal);if(++count>13)break;String id=resolve(db,channel.name);if(id.isEmpty())continue;List<LiveTvData.Programme> list=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT start,end,title,description FROM programmes WHERE channel=? AND end>? AND start<? ORDER BY start LIMIT 32",new String[]{id,Long.toString(from),Long.toString(to)})){while(c.moveToNext())list.add(new LiveTvData.Programme(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)));}if(!list.isEmpty())result.put(channel.id,list);}}catch(Exception unavailable){}return result;}
    String logo(LiveTvData.Channel channel){if(!enabled()||!file.exists())return "";try(SQLiteDatabase db=open()){String id=resolve(db,channel.name);try(Cursor c=db.rawQuery("SELECT image FROM logos WHERE channel=?",new String[]{id})){return c.moveToFirst()?c.getString(0):"";}}catch(RuntimeException unavailable){return "";}}
    File logoFile(LiveTvData.Channel channel)throws IOException {String url=logo(channel);if(!logoUrl(url)||Thread.currentThread().isInterrupted())return null;File folder=new File(file.getParentFile(),"logos");folder.mkdirs();File cached=new File(folder,LiveTvData.hash(url)+".png");if(cached.isFile()&&cached.length()<=256*1024&&System.currentTimeMillis()-cached.lastModified()<7*86400_000L)return cached;HttpURLConnection connection=null;File part=null;try{connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(6000);connection.setInstanceFollowRedirects(false);if(connection.getResponseCode()!=200||connection.getContentLengthLong()>256*1024)throw new IOException("Logo unavailable");part=File.createTempFile("logo-",".part",folder);try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(part)){byte[] buffer=new byte[8192];int total=0;for(int n;(n=in.read(buffer))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if((total+=n)>256*1024)throw new IOException("Logo limit");out.write(buffer,0,n);}}if(cached.exists())cached.delete();if(!part.renameTo(cached))throw new IOException("Logo cache unavailable");File[] files=folder.listFiles((dir,name)->name.endsWith(".png"));if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long bytes=0;for(int i=0;i<files.length;i++){bytes+=files[i].length();if(i>=48||bytes>2*1024*1024)files[i].delete();}}return cached;}catch(IOException offline){return cached.isFile()&&cached.length()<=256*1024?cached:null;}finally{if(part!=null)part.delete();if(connection!=null)connection.disconnect();}}
    static boolean logoUrl(String url){if(url==null||url.length()>1024)return false;try{URI uri=new URI(url);return "https".equals(uri.getScheme())&&"raw.githubusercontent.com".equals(uri.getHost())&&uri.getRawUserInfo()==null&&uri.getPort()==-1&&uri.getRawPath().startsWith("/davidmuma/picons_dobleM/")&&uri.getRawQuery()==null;}catch(URISyntaxException bad){return false;}}
    static final class Limited extends FilterInputStream {private final long limit;private final CancellationSignal signal;private long bytes;Limited(InputStream in,long limit,CancellationSignal signal){super(in);this.limit=limit;this.signal=signal;}private void count(int n)throws IOException{check(signal);if(n>0&&(bytes+=n)>limit)throw new IOException("XMLTV byte limit");}public int read()throws IOException{int n=in.read();count(n<0?0:1);return n;}public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);count(n);return n;}}
}
