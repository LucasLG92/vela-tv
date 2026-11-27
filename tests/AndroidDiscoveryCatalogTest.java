package tv.vela.launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

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
    interface AfterGuideRead {void run()throws Exception;}
    private VelaHub.Host reminderGuide(Context context,AfterGuideRead afterRead){
        Activity[] owner={null};
        runOnMainSync(()->owner[0]=new Activity(){ {attachBaseContext(context);} });
        return new FullToolsData(owner[0]){
            public void activate(VelaHub.Hit hit){throw new AssertionError();}
            public void closed(boolean changed){throw new AssertionError();}
            public String scope(){return LiveTvData.source(context);}
            @Override List<VelaShows.Airing> airings(VelaGuideSearch.Titles titles,CancellationSignal signal)throws Exception{
                List<VelaShows.Airing> rows=super.airings(titles,signal);
                afterRead.run();
                return rows;
            }
        };
    }
    private static void checkRecurringRefresh(Context c,VelaHub.Host guide,String scenario)throws Exception{
        SharedPreferences prefs=VelaShows.prefs(c);
        prefs.edit().remove("scan").commit();
        VelaProgrammeRefresh refresh=new VelaProgrammeRefresh(c);
        try{
            refresh.check(guide);
            long until=SystemClock.elapsedRealtime()+10000;
            while(!prefs.contains("scan")&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);
            check(prefs.contains("scan"),scenario+" refresh did not complete");
            List<String> titles=new ArrayList<>();
            for(VelaShows.Airing airing:VelaShows.cached(c))titles.add(airing.title);
            check(titles.equals(Arrays.asList("Órbita Episodio 4","Órbita Episodio 5")),
                    scenario+" should find both episodes and exclude Órbita abierta, got "+titles);
        }finally{refresh.close();}
    }
    private static void awaitScan(VelaProgrammeRefresh refresh)throws Exception{
        java.lang.reflect.Field job=VelaProgrammeRefresh.class.getDeclaredField("job");
        job.setAccessible(true);
        ((Future<?>)job.get(refresh)).get(15,TimeUnit.SECONDS);
    }
    private void changedDuringScan(Context c,VelaHub.Host guide,AfterGuideRead update,String expected,boolean remembered)throws Exception{
        SharedPreferences prefs=VelaShows.prefs(c);
        prefs.edit().remove("scan").commit();
        VelaProgrammeRefresh refresh=new VelaProgrammeRefresh(c);
        try{
            refresh.check(reminderGuide(c,update));
            awaitScan(refresh);
            check(!prefs.contains("scan"),"An outdated scan was marked fresh after changing "+expected);
            if(remembered)check(hasTitle(c,expected),"An outdated scan overwrote a newly followed airing");
            refresh.check(guide);
            awaitScan(refresh);
            check(prefs.contains("scan"),"The next check did not accept the updated guide");
            check(hasTitle(c,expected),"Newly matched programme missing: "+expected);
        }finally{refresh.close();}
    }
    private static boolean hasTitle(Context c,String title){
        for(VelaShows.Airing airing:VelaShows.cached(c))if(title.equals(airing.title))return true;
        return false;
    }
    private static JSONObject saveBroadcast(Context c,VelaHub.Host guide,VelaShows.Airing airing)throws Exception{
        VelaHub.Hit hit=new VelaHub.Hit("Programme",airing.title,airing.channel,airing);
        JSONObject ref=guide.reference(hit);
        VelaWatchlist.toggle(c,ref);
        return ref;
    }
    private static void savedChannelSources(Context c,TvCatalogCache cache,VelaHub.Host guide)throws Exception{
        CancellationSignal signal=new CancellationSignal();
        VelaShows.Airing original=guide.airings("Programme 0",0,signal).get(0);
        JSONObject first=saveBroadcast(c,guide,original);
        new M3uClient(c).configure("https://vela-fixture.invalid/second-playlist.m3u","",false);
        try(TvCatalogCache.Writer writer=cache.writer(LiveTvData.source(c))){
            for(String country:Arrays.asList("ES","MX")){
                LiveTvData.Channel channel=new LiveTvData.Channel(country,"["+country+"] Canal 0 HD","Demo","",Collections.singletonList("http://127.0.0.1:9/owned.ts"));
                writer.add(channel,false);
            }
            writer.commit("{}");
        }
        List<VelaShows.Airing> matches=guide.airings("Programme 0",0,signal);
        check(matches.size()==2&&matches.get(0).channel.equals(original.channel)&&matches.get(1).channel.equals(original.channel),
                "The real catalogue did not produce two same-name broadcasts");
        VelaShows.toggle(c,original.title);
        VelaShows.Airing legacyRow=new VelaShows.Airing(original.title,original.channel,original.start,original.end);
        VelaShows.replace(c,Collections.singletonList(legacyRow));
        VelaShows.remember(c,matches);
        VelaShows.remember(c,matches);
        List<VelaShows.Airing> followed=VelaShows.cached(c);
        check(followed.size()==2,"Following a show lost a same-name regional channel or duplicated a reread");
        Set<String> followedChannels=new HashSet<>();
        for(VelaShows.Airing airing:followed)followedChannels.add(airing.channelKey);
        check(followedChannels.contains(matches.get(0).channelKey)&&followedChannels.contains(matches.get(1).channelKey),
                "Followed programmes did not keep the original tuning references");
        Set<String> resolved=new HashSet<>();
        for(VelaShows.Airing airing:matches){
            check(airing.title.equals(original.title)&&airing.start==original.start&&airing.end==original.end,"Different EPG slot in fixture");
            JSONObject saved=saveBroadcast(c,guide,airing);
            VelaHub.Hit hit=guide.resolve(saved,signal);
            check(hit!=null&&hit.value instanceof LiveTvData.Channel,"Saved channel did not reopen in its source");
            resolved.add(((LiveTvData.Channel)hit.value).id);
        }
        check(resolved.equals(new HashSet<>(Arrays.asList("ES","MX")))&&VelaWatchlist.read(c).size()==3,
                "Two regions or two playlists replaced the same saved broadcast");
        check(guide.resolve(first,signal)==null,"Saved broadcast tuned a namesake on the second playlist");
        new M3uClient(c).configure("https://vela-fixture.invalid/discovery-query.m3u","",false);
        VelaHub.Hit again=guide.resolve(first,signal);
        check(again!=null&&((LiveTvData.Channel)again.value).id.equals("fixture-0"),"Returning to the first playlist lost the saved channel");
        JSONObject legacy=VelaWatchlist.airing(new VelaShows.Airing(original.title,original.channel,original.start,original.end));
        JSONObject unknown=new JSONObject(legacy.toString()).put("source","antenna").put("channelKey","");
        String nameOnly=VelaWatchlist.airingId(VelaWatchlist.asAiring(unknown));
        unknown.put("channel","Another unavailable channel");
        check(!nameOnly.equals(VelaWatchlist.airingId(VelaWatchlist.asAiring(unknown)))&&guide.resolve(unknown,signal)==null,
                "Unresolved channels shared an ID or opened by name");
        VelaWatchlist.toggle(c,legacy);
        check(guide.resolve(legacy,signal)==null,"A broadcast saved without a source resolved by name");
        VelaWatchlist.toggle(c,legacy);
        check(VelaWatchlist.read(c).size()==3,"Removing a legacy reference removed a source-bound broadcast");
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
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(new File(folder,"xmltv.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
            for(int i=0;i<30;i++){
                db.execSQL("INSERT INTO programmes(channel,start,end,title) VALUES(?,?,?,?)",
                        new Object[]{"epg-2",now+i*60,now+(i+1)*60,"Órbita abierta "+i});
            }
            long start=now+3600;
            for(String title:Arrays.asList("Órbita Episodio 4","Órbita Episodio 5","Órbita abierta")){
                db.execSQL("INSERT INTO programmes(channel,start,end,title) VALUES(?,?,?,?)",new Object[]{"epg-2",start,start+1800,title});
                start+=3600;
            }
        }
        VelaHub.Host guide=reminderGuide(c,()->{});
        List<VelaShows.Airing> broad=guide.airings("Órbita",0,new CancellationSignal());
        check(broad.size()==24,"General search must still return a bounded page");
        for(VelaShows.Airing airing:broad)check(airing.title.startsWith("Órbita abierta"),"Unexpected partial-match page");
        VelaShows.toggle(c,"Órbita Episodio 4");
        checkRecurringRefresh(c,guide,"Following an episode from the guide");
        VelaShows.prefs(c).edit().putString("rules","[\"Órbita Capítulo 9\"]").commit();
        checkRecurringRefresh(c,guide,"Existing chapter-specific rule");
        CancellationSignal stop=new CancellationSignal();stop.cancel();boolean cancelled=false;try{cache.discover(0,0,0,stop);}catch(OperationCanceledException expected){cancelled=true;}check(cancelled,"Query ignored cancellation");
        final Fixture fixture=c;
        changedDuringScan(c,guide,()->{
            VelaShows.toggle(fixture,"Órbita abierta");
            VelaShows.remember(fixture,guide.followedAirings(Collections.singleton(VelaShows.identity("Órbita abierta")),new CancellationSignal()));
        },"Órbita abierta",true);
        File guideFile=new File(folder,"xmltv.db");
        changedDuringScan(c,guide,()->{
            long previous=guideFile.lastModified();
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(guideFile.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                db.execSQL("INSERT INTO programmes(channel,start,end,title) VALUES(?,?,?,?)",
                        new Object[]{"epg-2",now+5*3600,now+6*3600,"Órbita Episodio 6"});
            }
            check(guideFile.setLastModified(previous+2000),"Fixture could not advance the guide timestamp");
        },"Órbita Episodio 6",false);
        savedChannelSources(c,cache,guide);
        result.putString("result","PASS actual encrypted catalogue with 6000 channels and 20000 EPG rows in "+elapsed+" ms; indexed matching, 24-record pages, genres, hidden channels, explicit aliases, offsets, cancellation and recurring reminders across episodes for new and previously saved rules. Owned isolated fixture only.");
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}finally{if(c!=null)c.cleanup();}finish(result.containsKey("error")?1:-1,result);}
}
