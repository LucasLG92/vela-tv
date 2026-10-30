package tv.vela.launcher;

import android.app.Instrumentation;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.io.*;
import java.util.*;

/** Exercise the actual encrypted catalogue/attached EPG query, without a TV, account or network. */
public final class AndroidDiscoveryCatalogTest extends Instrumentation {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static final class Fixture extends ContextWrapper {
        final File root;final Set<String> preferences=new HashSet<>();
        Fixture(Context c){super(c);root=new File(c.getCacheDir(),"owned-discovery-query");root.mkdirs();}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getCacheDir(){return root;}
        @Override public File getFilesDir(){File f=new File(root,"files");f.mkdirs();return f;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){String key="owned-query-"+name;preferences.add(key);return super.getSharedPreferences(key,mode);}
        void cleanup(){for(String p:preferences)getBaseContext().deleteSharedPreferences(p);remove(root);}
        void remove(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)remove(c);}f.delete();}
    }
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();Fixture c=null;try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        RefinementHttp.install(url->null);c=new Fixture(getTargetContext());c.cleanup();c.root.mkdirs();
        new M3uClient(c).configure("https://vela-fixture.invalid/discovery-query.m3u","",false);
        String epg="https://vela-fixture.invalid/discovery-query.xml";new XmlTvGuide(c).configure(epg);
        TvCatalogCache cache=new TvCatalogCache(c);String source=LiveTvData.source(c);List<LiveTvData.Channel> known=new ArrayList<>();
        try(TvCatalogCache.Writer writer=cache.writer(source)){
            for(int i=0;i<6000;i++){LiveTvData.Channel ch=new LiveTvData.Channel("fixture-"+i,"Canal "+i,"Demo","https://vela-fixture.invalid/logo.png",Collections.singletonList("http://127.0.0.1:9/owned.ts"));writer.add(ch,false);if(i<60)known.add(ch);}writer.commit("{}");
        }
        File folder=new File(c.getCacheDir(),"tv-guide");folder.mkdirs();long now=System.currentTimeMillis()/1000;
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(new File(folder,"xmltv.db"),null)){
            db.execSQL("CREATE TABLE meta(id INTEGER PRIMARY KEY,source TEXT)");db.execSQL("INSERT INTO meta VALUES(1,?)",new Object[]{LiveTvData.hash(epg)});
            db.execSQL("CREATE TABLE aliases(alias TEXT,channel TEXT,PRIMARY KEY(alias,channel))");
            db.execSQL("CREATE TABLE programmes(channel TEXT,start INTEGER,end INTEGER,title TEXT,category TEXT,event_id TEXT,description TEXT,image TEXT)");
            db.execSQL("CREATE INDEX programme_time ON programmes(channel,start,end)");db.beginTransaction();
            try{for(int i=0;i<20000;i++){
                String channel="epg-"+i;db.execSQL("INSERT INTO aliases VALUES(?,?)",new Object[]{"name:"+TvChannelNames.match("Canal "+(i<60?i:i+6000)),channel});
                db.execSQL("INSERT INTO programmes VALUES(?,?,?,?,?,?,?,?)",new Object[]{channel,now-900,now+3600,"Programme "+i,i%2==0?"sport":"movie","event-"+i,"Owned demonstration",""});
            }db.execSQL("INSERT INTO aliases VALUES('id:fixture-zero','epg-0')");db.setTransactionSuccessful();}finally{db.endTransaction();}
        }
        VelaGuideFix.set(c,"Canal 0","fixture-zero",30);new TvPersonalization(c,source).hide(TvChannelNames.family(known.get(1)),"Canal 1",true);
        long started=SystemClock.elapsedRealtime();List<VelaShows.Airing> first=cache.discover(0,0,0,new CancellationSignal());long elapsed=SystemClock.elapsedRealtime()-started;
        check(first.size()==24,"Discovery did not return a bounded page");check(elapsed<8000,"Large local catalogue took "+elapsed+" ms");
        boolean corrected=false;Set<String> titles=new HashSet<>();for(VelaShows.Airing p:first){check(!p.channel.equals("Canal 1"),"Hidden channel escaped");titles.add(p.title);if(p.channel.equals("Canal 0")){check(p.start==now-900+1800,"EPG offset lost");corrected=true;}}
        // Corrected channel 0 is future at +15 minutes, so it belongs to the 48-hour view, not Now.
        check(!corrected,"Future corrected programme leaked into Now");
        List<VelaShows.Airing> second=cache.discover(0,0,1,new CancellationSignal());check(second.size()==24,"Second page was lost");for(VelaShows.Airing p:second)check(!titles.contains(p.title),"Paging repeated an airing");
        List<VelaShows.Airing> sports=cache.discover(2,2,0,new CancellationSignal());check(sports.size()==24,"Genre filter lost its page");for(VelaShows.Airing p:sports)check(Integer.parseInt(p.title.substring(10))%2==0,"Non-sport programme escaped");
        List<VelaShows.Airing> matches=cache.airings("Programme 0",0,new CancellationSignal());check(matches.size()==1&&matches.get(0).start==now+900,"Explicit EPG identity or time correction lost");
        CancellationSignal stop=new CancellationSignal();stop.cancel();boolean cancelled=false;try{cache.discover(0,0,0,stop);}catch(OperationCanceledException expected){cancelled=true;}check(cancelled,"Query ignored cancellation");
        result.putString("result","PASS actual encrypted catalogue with 6000 channels and 20000 EPG rows in "+elapsed+" ms; indexed matching, 24-record pages, genres, hidden channels, explicit aliases, offsets and cancellation. Owned isolated fixture only.");
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}finally{if(c!=null)c.cleanup();}finish(result.containsKey("error")?1:-1,result);}
}
