package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Owned loopback fixture on an emulator. Never reads a personal playlist or account. */
public final class AndroidM3uTest extends Instrumentation {
    private interface Ready {boolean ready()throws Exception;}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void await(Ready ready,String reason)throws Exception{long end=SystemClock.elapsedRealtime()+40000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(ready.ready())return;Thread.sleep(100);}throw new AssertionError(reason);}
    private JSONObject get(String url)throws Exception{try(XtreamClient.Response response=new XtreamClient.Response(url,64*1024);ByteArrayOutputStream out=new ByteArrayOutputStream()){LiveTvData.copy(response.in,out,64*1024);return new JSONObject(out.toString("UTF-8"));}}
    private void restore(SharedPreferences prefs,Map<String,?> saved){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object value=entry.getValue();String k=entry.getKey();if(value instanceof String)edit.putString(k,(String)value);else if(value instanceof Boolean)edit.putBoolean(k,(Boolean)value);else if(value instanceof Integer)edit.putInt(k,(Integer)value);else if(value instanceof Long)edit.putLong(k,(Long)value);else if(value instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)value));}edit.commit();}
    private void invoke(Object target,String name)throws Exception{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);ui(()->{try{m.invoke(target);}catch(Exception failure){throw new RuntimeException(failure);}});}
    private boolean contains(File file,String value)throws Exception{if(!file.isFile())return false;byte[] needle=value.getBytes("UTF-8"),buffer=new byte[8192+needle.length];int kept=0;try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer,kept,8192))>0){int length=kept+n;for(int i=0;i+needle.length<=length;i++){boolean same=true;for(int j=0;j<needle.length;j++)same&=buffer[i+j]==needle[j];if(same)return true;}kept=Math.min(needle.length-1,length);System.arraycopy(buffer,length-kept,buffer,0,kept);}}return false;}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences epgPrefs=c.getSharedPreferences("xmltv",0);Map<String,?> epgBefore=new HashMap<>(epgPrefs.getAll());epgPrefs.edit().putBoolean("native",true).commit();SharedPreferences prefs=LiveTvData.prefs(c);Map<String,?> before=new HashMap<>(prefs.getAll());LiveTvActivity activity=null;Activity overlay=null;String base="";
        Map<String,?> plex=new HashMap<>(new PlexClient(c).prefs.getAll()),xtream=new HashMap<>(c.getSharedPreferences("xtream",0).getAll());String[] sidebar=SidebarApps.slots(c);
        try{
            check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"),"Emulator required");
            File fixture=new File(c.getExternalFilesDir(null),"xtream-fixture.json");JSONObject config=new JSONObject(new String(LiveTvData.read(fixture,8192),"UTF-8"));base=config.getString("base");String password=config.getString("password");check(base.startsWith("http://127.0.0.1:"),"Owned loopback fixture required");String address=base+"/m3u?auth="+XtreamClient.enc(password);
            M3uClient client=new M3uClient(c);client.configure(address,"",true);check(LiveTvData.mode(c).equals("m3u"),"M3U not selected");
            for(Object v:prefs.getAll().values())check(!v.toString().contains(password),"Plaintext M3U credential in preferences");
            int downloads=get(base+"/metrics").optInt("/m3u");long started=SystemClock.elapsedRealtime();check(!client.refresh(true),"Fresh list reported offline");long imported=SystemClock.elapsedRealtime()-started;
            TvCatalogCache.Page first=client.page(null,"",null,0);check(first.total==10001&&first.channels.size()==12&&first.channels.get(0).streams.size()==2,"Streamed import, duplicate signals or website exclusion failed");
            check(client.page(null,"",null,1).channels.get(0).id.equals("channel-13"),"Second page incorrect");TvCatalogCache.Page last=client.page(null,"",null,833);check(last.page==833&&last.channels.size()==5&&last.total==10001,"Last M3U page incorrect");
            check(client.page("Pruebas","",null,0).total==30&&client.page(null,"10001",null,0).total==1&&client.page(null,"%",null,0).total==0,"Indexed category/name/literal search failed");
            check(get(base+"/metrics").optInt("/m3u")==downloads+1,"Paging downloaded the playlist again");
            File db=new File(c.getCacheDir(),"tv-catalogs/channels.db");check(db.length()<=TvCatalogCache.MAX_DISK_BYTES&&!contains(db,password),"Disk budget or catalog credential encryption failed");
            Map<String,LiveTvData.Guide> guide=client.guide(first.channels);check(guide.size()==12&&guide.get("channel-1").now.title.equals("Programa & prueba"),"XMLTV now/next/escaped title failed");
            check(!M3uClient.xmlGuide(new StringReader("<tv><programme channel='x' start='20260101000000 +0000' stop='20270101000000 +0000'><title>Future</title></programme></tv>"),Collections.singleton("x"),1).containsKey("other"),"Unwanted guide retained");
            boolean refused=false;try{M3uClient.xmlGuide(new StringReader("<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///private'>]><tv/>"),Collections.singleton("x"),1);}catch(Exception expected){refused=true;}check(refused,"XMLTV doctype accepted");
            check(M3uClient.timestamp("20261021080000 +0200")==M3uClient.timestamp("20261021060000 +0000")&&M3uClient.timestamp("bad")==0,"XMLTV timezone parsing failed");
            get(base+"/control?broken=1");check(client.refresh(true)&&client.page(null,"",null,0).total==10001,"Failed refresh destroyed the previous catalog");get(base+"/control?broken=0");
            TvCatalogCache store=new TvCatalogCache(c);String rollback="test-rollback";try(TvCatalogCache.Writer writer=store.writer(rollback)){writer.add(first.channels.get(0),false);writer.commit("{}");}try(TvCatalogCache.Writer writer=store.writer(rollback)){writer.add(first.channels.get(1),false);}check(store.page(rollback,null,"",null,0).channels.get(0).id.equals("channel-1"),"Uncommitted import replaced a valid page");store.remove(rollback);
            try{c.getPackageManager().getActivityInfo(new ComponentName(c,"tv.vela.launcher.LiveTvWebActivity"),0);throw new AssertionError("Website player remains installed");}catch(android.content.pm.PackageManager.NameNotFoundException expected){}
            activity=(LiveTvActivity)startActivitySync(new Intent(c,LiveTvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LiveTvActivity tv=activity;
            await(()->tv.catalog!=null&&!(Boolean)field(tv,"loading")&&tv.catalog.channels.size()==12,"M3U browser did not load");
            check(((Map<?,?>)field(tv,"sourceTabs")).size()==3&&field(tv,"groupList") instanceof ListView&&((ListView)field(tv,"groupList")).getChildCount()<=12,"Source tabs/category recycling incorrect");
            List<View> cards=(List<View>)field(tv,"cards");View favoriteCard=cards.get(0);ui(()->{try{Method m=LiveTvActivity.class.getDeclaredMethod("toggleFavorite",LiveTvData.Channel.class);m.setAccessible(true);m.invoke(tv,tv.catalog.channels.get(0));}catch(Exception e){throw new RuntimeException(e);}});await(()->!(Boolean)field(tv,"loading"),"Favorite page refresh stuck");check(LiveTvData.favorites(c,LiveTvData.source(c)).contains("channel-1")&&tv.playback==null,"Favorite changed source or started video");
            cards=(List<View>)field(tv,"cards");View lastCard=cards.get(11);ui(lastCard::performClick);await(()->tv.playback!=null&&tv.playback.rendered,"M3U HLS did not render");MediaPlayer previous=tv.playback.player;sendKeyDownUpSync(KeyEvent.KEYCODE_CHANNEL_UP);
            await(()->tv.playback!=null&&tv.playback.rendered&&tv.selected.id.equals("channel-13"),"M3U cross-page channel change failed");check(tv.playback.player!=previous,"Old decoder reused after channel change");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();check(tv.playback==null&&!tv.isFinishing(),"Back did not release playback");
            invoke(tv,"showSearch");FrameLayout dialog=(FrameLayout)field(tv,"dialog");EditText search=null;TextView submit=null;for(int i=0;i<dialog.getChildCount();i++){View child=dialog.getChildAt(i);if(child instanceof EditText)search=(EditText)child;if(child instanceof TextView&&((TextView)child).getText().toString().matches("Buscar canais|Search channels|Buscar canales"))submit=(TextView)child;}check(search!=null&&submit!=null,"Native search controls missing");EditText entry=search;TextView button=submit;ui(()->{entry.setText("10001");button.performClick();});await(()->!(Boolean)field(tv,"loading")&&tv.catalog.channels.size()==1,"Native search not applied");check(tv.catalog.channels.get(0).id.equals("channel-10001"),"Wrong native search result");
            Map<String,TextView> tabs=(Map<String,TextView>)field(tv,"sourceTabs");ui(()->tabs.get("antenna").performClick());await(()->!(Boolean)field(tv,"loading")&&LiveTvData.mode(c).equals("antenna"),"Antenna source failed on device without tuner");check(tv.playback==null,"Selecting antenna started a decoder");ui(()->tabs.get("m3u").performClick());await(()->!(Boolean)field(tv,"loading")&&LiveTvData.mode(c).equals("m3u"),"Could not return to saved M3U");
            check(get(base+"/metrics").optInt("/m3u")==downloads+2,"UI paging/search/source switch redownloaded a fresh playlist");
            File legacy=LiveTvData.cached(c,"catalog-migration.txt");try(java.io.FileOutputStream output=new java.io.FileOutputStream(legacy)){output.write(password.getBytes("UTF-8"));}prefs.edit().remove("m3u-private").remove("m3u-id").putString("source",address).putString("mode","antenna").commit();client.refresh(false);check(LiveTvData.mode(c).equals("antenna")&&!prefs.contains("source")&&!legacy.exists(),"Legacy playlist migration changed the selected source or retained plaintext data");
            check(plex.equals(new PlexClient(c).prefs.getAll())&&xtream.equals(c.getSharedPreferences("xtream",0).getAll())&&Arrays.equals(sidebar,SidebarApps.slots(c)),"M3U changed other accounts or shortcuts");
            result.putLong("import_10001_channels_ms",imported);result.putString("result","PASS streamed 10001-channel M3U, 12-row disk pages and indexed filters, no paging downloads, duplicate signals, direct-only entries, encrypted URLs and source, bounded XMLTV/doctype rejection, atomic rollback/offline fallback, three native sources, recycled categories, favorites, HLS decoding and cross-page zap, search, antenna-empty state, Back release. Other accounts preserved. Owned fixture only.");
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{restore(epgPrefs,epgBefore);if(!base.isEmpty())try{get(base+"/control?broken=0");}catch(Exception ignored){}if(overlay!=null){Activity a=overlay;ui(a::finish);}if(activity!=null){LiveTvActivity a=activity;ui(a::finish);}restore(prefs,before);}
        finish(result.containsKey("error")?1:-1,result);
    }
}
