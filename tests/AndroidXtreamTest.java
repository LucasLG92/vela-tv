package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** An owned loopback fixture only. Never reads a personal IPTV subscription data. */
public final class AndroidXtreamTest extends Instrumentation {
    private void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
    private interface Ready{boolean get()throws Exception;}
    private void await(Ready r,String why)throws Exception{long end=SystemClock.uptimeMillis()+35_000;while(SystemClock.uptimeMillis()<end){boolean[] yes={false};ui(()->{try{yes[0]=r.get();}catch(Exception e){throw new RuntimeException(e);}});if(yes[0])return;Thread.sleep(40);}throw new AssertionError(why);}
    private void call(Object a,String name,Class<?> type,Object value){Exception[] failed={null};ui(()->{try{Method m=type==null?a.getClass().getDeclaredMethod(name):a.getClass().getDeclaredMethod(name,type);m.setAccessible(true);if(type==null)m.invoke(a);else m.invoke(a,value);}catch(Exception e){failed[0]=e;}});if(failed[0]!=null)throw new RuntimeException(failed[0]);}
    private void page(LiveTvActivity a,int number){Exception[] failed={null};ui(()->{try{Method m=LiveTvActivity.class.getDeclaredMethod("renderPage",int.class,boolean.class);m.setAccessible(true);m.invoke(a,number,true);}catch(Exception e){failed[0]=e;}});if(failed[0]!=null)throw new RuntimeException(failed[0]);}
    private void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Set)e.putStringSet(item.getKey(),new HashSet<>((Set<String>)v));}e.commit();}
    private void key(int k){sendKeyDownUpSync(k);waitForIdleSync();}
    private JSONObject metrics(String base)throws Exception{try(XtreamClient.Response response=new XtreamClient.Response(base+"/metrics",64*1024);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){LiveTvData.copy(response.in,out,64*1024);return new JSONObject(out.toString("UTF-8"));}}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences epgPrefs=c.getSharedPreferences("xmltv",0);Map<String,?> epgBefore=new HashMap<>(epgPrefs.getAll());epgPrefs.edit().putBoolean("native",true).commit();SharedPreferences tv=LiveTvData.prefs(c),secret=c.getSharedPreferences("xtream",0);Map<String,Object> previousTv=new HashMap<>(tv.getAll()),previousSecret=new HashMap<>(secret.getAll());XtreamLinkActivity link=null;LiveTvActivity activity=null;Activity pause=null;
        try{
            check(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"),"Clean emulator required");check(!new PlexClient(c).connected()&&!new JellyfinClient(c).connected(),"Personal account linked");
            Map<String,?> plex=new HashMap<>(new PlexClient(c).prefs.getAll()),jellyfin=new HashMap<>(JellyfinClient.prefs(c).getAll()),appearance=new HashMap<>(Visuals.prefs(c).getAll());String[] sidebar=SidebarApps.slots(c);
            File fixture=new File(c.getExternalFilesDir(null),"xtream-fixture.json");byte[] raw=new byte[(int)fixture.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(fixture))){in.readFully(raw);}JSONObject f=new JSONObject(new String(raw,"UTF-8"));String base=f.getString("base"),user=f.getString("username"),pw=f.getString("password");check(base.startsWith("http://127.0.0.1:"),"Loopback fixture required");
            XtreamClient stale=new XtreamClient(c);if(stale.connected()){XtreamClient.Account own=stale.account();check(own.base.equals(base)&&own.user.equals(user)&&own.password.equals(pw),"Personal IPTV account linked");stale.disconnect();previousSecret.clear();previousTv.put("xtream",false);}
            for(String invalid:new String[]{"https://user:secret@example.invalid","https://example.invalid/?password=x","file:///data/private","https://example.invalid/../secret","https://example.invalid/%2e%2e/a","https://example.invalid/#fragment","https://example.invalid:0"}){boolean refused=false;try{XtreamClient.server(invalid,true);}catch(Exception e){refused=true;}check(refused,"Unsafe server accepted");}
            boolean refused=false;try{XtreamClient.server(base,false);}catch(Exception e){refused=true;}check(refused,"HTTP did not require opt-in");check(XtreamClient.enc("a/b +").equals("a%2Fb%20%2B"),"Credentials not path-encoded");check(XtreamClient.server("https://example.invalid/player_api.php",false).equals("https://example.invalid"),"API suffix not normalized");
            XtreamClient client=new XtreamClient(c);refused=false;try{client.login(base,user,"not-the-password",true);}catch(Exception e){refused=true;}check(refused&&!client.connected(),"Bad credentials saved");refused=false;try{client.login(base+"/redirect",user,pw,true);}catch(Exception e){refused=true;}check(refused,"Authenticated redirect followed");
            link=(XtreamLinkActivity)startActivitySync(new Intent(c,XtreamLinkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));XtreamLinkActivity a=link;
            check((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0,"Account screen permits screenshots");EditText address=(EditText)field(a,"address"),username=(EditText)field(a,"username"),password=(EditText)field(a,"password");check(!address.isSaveEnabled()&&!username.isSaveEnabled()&&!password.isSaveEnabled(),"Secret fields saved in view state");
            ui(()->{address.setText(base);username.setText(user);password.setText(pw);((TextView)unchecked(a,"http")).performClick();});call(a,"connect",null,null);
            await(client::connected,"Native Xtream login did not save account");check(password.getText().length()==0,"Password remained in input");XtreamClient.Account account=client.account();check(account.base.equals(base)&&account.user.equals(user)&&account.password.equals(pw),"Encrypted account did not round-trip");
            for(Object v:secret.getAll().values())check(!v.toString().contains(base)&&!v.toString().contains(user)&&!v.toString().contains(pw),"Plaintext credentials persisted");
            check(XtreamClient.safeLogo(account,"https://other.invalid/logo?password="+pw).isEmpty(),"Provider password forwarded to unrelated logo host");check(LiveTvData.source(c).startsWith("xtream:")&&!LiveTvData.source(c).contains(user),"Secret source identity");
            ui(()->a.finish());link=null;
            activity=(LiveTvActivity)startActivitySync(new Intent(c,LiveTvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LiveTvActivity tvActivity=activity;
            await(()->tvActivity.catalog!=null&&!((Boolean)field(tvActivity,"loading"))&&((List<?>)field(tvActivity,"cards")).size()==12,"Native channel page did not load");
            check(tvActivity.catalog.channels.size()==12&&((List<?>)field(tvActivity,"filtered")).size()==12,"Whole IPTV catalog retained in memory");check((Integer)field(tvActivity,"total")==30&&tvActivity.playback==null,"Wrong count or unsolicited playback");
            await(()->!((Map<?,?>)field(tvActivity,"guide")).isEmpty(),"Now/next guide missing");Map<String,LiveTvData.Guide> epg=(Map<String,LiveTvData.Guide>)field(tvActivity,"guide");check(epg.size()<=12&&epg.values().iterator().next().now.title.equals("Programa de prueba"),"Guide not bounded or Base64 title incorrect");
            String remaining=((List<TextView>)field(tvActivity,"remainingLabels")).get(0).getText().toString();check(remaining.contains("2 h")&&remaining.contains("min"),"Programme duration not integrated: "+remaining);
            await(()->{for(ImageView image:(List<ImageView>)field(tvActivity,"images"))if(image.getDrawable()!=null)return true;return false;},"Provider logo missing");
            int requests=metrics(base).optInt("streams-1");client.page("1","Pruebas",1);client.page("1","Pruebas",0,"00030",false);check(metrics(base).optInt("streams-1")==requests,"Xtream paging/search downloaded the category again");
            List<View> cards=(List<View>)field(tvActivity,"cards");ui(()->cards.get(0).requestFocus());LiveTvData.Channel first=tvActivity.selected;check(first.xtreamId.equals("101")&&first.streams.isEmpty(),"Credential URLs stored in catalog rows");
            List<String> urls=client.streams(first);check(urls.size()==2&&urls.get(0).endsWith("/101.m3u8")&&urls.get(1).endsWith("/101.ts")&&!urls.get(0).contains("ignored.invalid"),"Stream URL not scoped to chosen server/output");
            call(tvActivity,"toggleFavorite",LiveTvData.Channel.class,first);check(client.favoriteIds().contains(first.id)&&tvActivity.playback==null,"Favorite did not persist or started video");check(!secret.getString("favorites","").contains(first.name),"Favorite metadata not encrypted");
            android.widget.ListView groups=(android.widget.ListView)field(tvActivity,"groupList");ui(()->groups.performItemClick(null,1,1));await(()->!((Boolean)field(tvActivity,"loading"))&&tvActivity.catalog.channels.size()==1,"Favorites category failed");check((Integer)field(tvActivity,"total")==1,"Wrong favorite count");
            ui(()->groups.performItemClick(null,2,2));await(()->!((Boolean)field(tvActivity,"loading"))&&tvActivity.catalog.channels.size()==12,"Could not return to category");
            ui(()->((TextView)unchecked(tvActivity,"next")).performClick());await(()->!((Boolean)field(tvActivity,"loading"))&&(Integer)field(tvActivity,"page")==1,"Next page failed");check(tvActivity.catalog.channels.size()==12&&tvActivity.catalog.channels.get(0).xtreamId.equals("113"),"Incorrect server page");
            ui(()->groups.performItemClick(null,3,3));await(()->!((Boolean)field(tvActivity,"loading"))&&(Integer)field(tvActivity,"total")==10001,"Large category failed");check(tvActivity.catalog.channels.size()==12&&((List<?>)field(tvActivity,"filtered")).size()==12,"Large category allocated entire list");
            page(tvActivity,833);await(()->!((Boolean)field(tvActivity,"loading"))&&(Integer)field(tvActivity,"page")==833,"Last page failed");check(tvActivity.catalog.channels.size()==5&&!((TextView)field(tvActivity,"next")).isEnabled(),"Last page/total incorrect");
            ui(()->groups.performItemClick(null,4,4));await(()->!((Boolean)field(tvActivity,"loading"))&&tvActivity.catalog.channels.isEmpty(),"Empty category failed");check(tvActivity.playback==null,"Empty category started decoder");
            ui(()->groups.performItemClick(null,2,2));await(()->!((Boolean)field(tvActivity,"loading"))&&tvActivity.catalog.channels.size()==12,"Could not reload fixture category");
            XtreamClient.Page search=client.page("*","All",0,"10031",false);check(search.total==1&&search.channels.size()==1,"Global Xtream name search failed");int allRequests=metrics(base).optInt("streams-");client.page("*","All",0,"10030",false);check(metrics(base).optInt("streams-")==allRequests,"Global search downloaded the catalog again");
            LiveTvData.Channel last=tvActivity.catalog.channels.get(11);ui(()->tvActivity.play(last));await(()->tvActivity.playback!=null&&tvActivity.playback.rendered,"Synthetic HLS did not decode");LiveTvPlayer old=tvActivity.playback;MediaPlayer oldDecoder=old.player;check(oldDecoder.getVideoWidth()==320&&oldDecoder.isPlaying(),"No decoded fixture video");
            key(KeyEvent.KEYCODE_CHANNEL_UP);await(()->tvActivity.playback!=null&&tvActivity.playback.rendered&&tvActivity.selected.xtreamId.equals("113"),"Zapping across page failed");check(tvActivity.playback.player!=oldDecoder,"Old decoder retained when zapping");
            key(KeyEvent.KEYCODE_BACK);check(tvActivity.playback==null&&old.player==null&&(Integer)field(tvActivity,"page")==1,"Back retained decoder or lost remote page");
            LiveTvData.Channel selected=tvActivity.selected;ui(()->tvActivity.play(selected));await(()->tvActivity.playback!=null&&tvActivity.playback.rendered,"Playback retry failed");LiveTvPlayer video=tvActivity.playback;
            pause=startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->video.player==null&&!video.active,"Pause retained decoder/audio");Activity over=pause;ui(over::finish);pause=null;await(()->tvActivity.playback!=null&&tvActivity.playback.rendered,"Playback resume failed");key(KeyEvent.KEYCODE_BACK);
            ui(tvActivity::finish);activity=null;
            link=(XtreamLinkActivity)startActivitySync(new Intent(c,XtreamLinkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));XtreamLinkActivity remove=link;List<View> buttons=(List<View>)field(remove,"controls");ui(()->buttons.get(2).performClick());List<View> confirm=(List<View>)field(remove,"controls");ui(()->confirm.get(0).performClick());check(!client.connected()&&!secret.contains("account")&&!secret.contains("favorites")&&!LiveTvData.source(c).startsWith("xtream:"),"Disconnect retained account or favorites");
            check(plex.equals(new PlexClient(c).prefs.getAll())&&jellyfin.equals(JellyfinClient.prefs(c).getAll())&&appearance.equals(Visuals.prefs(c).getAll())&&Arrays.equals(sidebar,SidebarApps.slots(c)),"IPTV changed other accounts or personalization");
            result.putString("result","PASS owned loopback Xtream: native protected login, device-key encryption, invalid auth/URL/redirect rejection, no plaintext credentials or channel playback URLs in catalog, 12-row pages across 10001 channels, categories/favorites/empty/last pages, bounded now/next and remaining time, logos, actual HLS decoding, cross-page zapping, Back/pause/decoder release and unlink. Other accounts and appearance preserved. No personal IPTV data accessed.");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{restore(epgPrefs,epgBefore);if(pause!=null){Activity x=pause;ui(x::finish);}if(activity!=null){LiveTvActivity x=activity;ui(x::finish);}if(link!=null){XtreamLinkActivity x=link;ui(x::finish);}restore(tv,previousTv);restore(secret,previousSecret);}
        finish(result.containsKey("error")?1:-1,result);
    }
    private Object unchecked(Object o,String name){try{return field(o,name);}catch(Exception e){throw new RuntimeException(e);}}
}
