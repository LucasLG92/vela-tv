package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.database.sqlite.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual native views and persistence, with isolated profiles on an explicitly owned emulator. */
public final class AndroidNextTest extends Instrumentation {
    private Activity activity;private Context context;private String phase="setup",previous,profile,other,guestId;private VelaHub hub;private final Bundle result=new Bundle();
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private Object quiet(Object o,String name){try{return field(o,name);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(Runnable r){
        Throwable[] error={null};
        runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});
        waitForIdleSync();
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    interface Ready{boolean ready()throws Exception;}
    private void await(Ready r)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){waitForIdleSync();if(r.ready())return;Thread.sleep(40);}throw new AssertionError("Timeout: "+phase);}
    private TextView text(View root,String label){if(root instanceof TextView&&(((TextView)root).getText().toString().equals(label)||((TextView)root).getText().toString().startsWith(label+"\n")))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void click(String key){TextView view=text(hub,L10n.text(key));check(view!=null,"Missing native action: "+key);ui(view::performClick);}
    private void tools(int page)throws Exception{ui(()->((VelaHub.Owner)activity).tools(page));hub=(VelaHub)field(activity,"hub");check(hub!=null,"Tools not integrated");}
    private void searchWatchlist(VelaShows.Airing airing)throws Exception{
        tools(0);
        VelaSearchView search=(VelaSearchView)field(hub,"searchView");
        VelaHub.Hit hit=new VelaHub.Hit(L10n.text("Programme"),airing.title,airing.channel,airing);
        ui(()->search.results(Collections.singletonList(hit),0,false,false));
        View tile=((List<View>)field(search,"tiles")).get(0);
        String id=VelaWatchlist.airingId(airing);
        ui(tile::performLongClick);
        check(VelaWatchlist.contains(context,id),"Holding OK on a programme search result did not save the broadcast");
        ui(tile::performLongClick);
        check(!VelaWatchlist.contains(context,id),"Holding OK again did not remove the saved broadcast");
    }
    private void sameSlotEpisodes(long start)throws Exception{
        VelaShows.Airing first=new VelaShows.Airing("Serial T1 E1","Demo",start,start+1800).onChannel("antenna","channel-one");
        VelaShows.Airing tenth=new VelaShows.Airing("Serial T1 E10","Demo",start,start+1800,"","Tenth episode","","").onChannel(first.source,first.channelKey);
        VelaShows.Airing refreshed=new VelaShows.Airing(first.title,"Demo HD",start,start+2700,"","First episode","","").onChannel(first.source,first.channelKey);
        List<VelaShows.Airing> merged=VelaToolsData.merge(Collections.singletonList(first),Collections.singletonList(tenth));
        check(merged.size()==2&&merged.get(0).description.isEmpty()&&merged.get(1).description.equals(tenth.description),
                "Native and online guides merged different episodes in the same slot");

        // The previous ID removed the episode suffix. The saved title still identifies the original.
        String oldId="tv2:"+LiveTvData.hash("antenna|channel-one|"+start+"|serial");
        String previousItems=VelaWatchlist.prefs(context).getString("items","[]");
        VelaHub[] fixture={null};
        try{
            JSONObject legacy=VelaWatchlist.airing(first).put("id",oldId);
            VelaWatchlist.prefs(context).edit().putString("items",new JSONArray().put(legacy).toString()).commit();
            check(VelaWatchlist.contains(context,VelaWatchlist.airingId(refreshed))&&!VelaWatchlist.contains(context,VelaWatchlist.airingId(tenth)),
                    "A replacement episode inherited the saved marker from an older build");
            VelaWatchlist.toggle(context,VelaWatchlist.airing(tenth));
            check(VelaWatchlist.read(context).size()==2,"Saving a replacement removed the original episode");
            VelaWatchlist.toggle(context,VelaWatchlist.airing(tenth));
            ui(()->{
                fixture[0]=new VelaHub(activity,new VelaHub.Host(){
                    public List<VelaHub.Hit> search(String q,int page,CancellationSignal signal){return Collections.emptyList();}
                    public List<VelaShows.Airing> airings(String q,int page,CancellationSignal signal){return Arrays.asList(tenth,refreshed);}
                    public String broadcastSource(){return first.source;}
                    public void activate(VelaHub.Hit hit){} public String diagnosis(CancellationSignal signal){return "";}
                    public void repair(){} public void closed(boolean changed){}
                },3,true);
                activity.addContentView(fixture[0],new FrameLayout.LayoutParams(-1,-1));
                text(fixture[0],L10n.text("Watch later")).performClick();
                text(fixture[0],first.title).performClick();
            });
            await(()->text(fixture[0],refreshed.description)!=null);
            check(text(fixture[0],tenth.description)==null,"Saved details used the replacement episode's description");
            ui(()->text(fixture[0],L10n.text("Remove from Watch later")).performClick());
            check(VelaWatchlist.read(context).isEmpty(),"Removing the migrated broadcast left its legacy reference");
        }finally{
            if(fixture[0]!=null)ui(fixture[0]::close);
            VelaWatchlist.prefs(context).edit().putString("items",previousItems).commit();
        }
    }
    private void guestProfileSelection()throws Exception{
        Map<String,String> profiles=VelaWorkspace.profiles(context);
        guestId=VelaSessions.prefs(context).getString("guest","");
        String guestName=profiles.get(guestId);
        tools(4);
        click(profiles.get(other));
        tools(4);
        click(guestName);
        check(VelaSessions.guest(context),"Selecting the guest profile by name did not enter guest mode");
        tools(4);
        EditText pin=(EditText)field(hub,"input");
        ui(()->pin.setText("2468"));
        click("Unlock");
        await(()->field(activity,"hub")==null);
        check(VelaWorkspace.active(context).equals(other),"Leaving guest returned to the previous guest session's owner");
        tools(4);
        click("Remove settings PIN");
        check(!VelaSessions.prefs(context).contains("guest")&&!VelaSessions.prefs(context).contains("return"),
                "Removing the PIN retained the protected guest session");
        click(guestName);
        // Older versions left this marker behind when the owner removed the PIN.
        VelaSessions.prefs(context).edit().putString("guest",guestId).putString("return",other).commit();
        tools(4);
        check(!VelaSessions.blocked(context)&&text(hub,L10n.text("Protect settings with a PIN"))!=null,
                "Selecting a former guest profile required a PIN after it was removed");
        click(profiles.get(profile));
        check(VelaWorkspace.active(context).equals(profile),"Could not leave the former guest profile");
    }
    private void countdownInOpenPanel()throws Exception{
        VelaSleepTimer timer=new VelaSleepTimer(()->{});
        VelaPlaybackPanel[] panel={null};
        View[] focused={null};
        String title=L10n.text("Sleep timer");
        String pattern=L10n.text("Stops playback in %d min");
        String twoMinutes=title+"\n"+String.format(Ui.locale(),pattern,2);
        String oneMinute=title+"\n"+String.format(Ui.locale(),pattern,1);
        try{
            ui(()->{
                timer.after(62_000);
                panel[0]=new VelaPlaybackPanel(activity,"Demo",()->{
                    List<VelaPlaybackPanel.Item> items=new ArrayList<>();
                    timer.items(items,0);
                    items.add(new VelaPlaybackPanel.Item("Other control","",()->{}));
                    return items;
                },()->{});
                ((FrameLayout)activity.findViewById(android.R.id.content)).addView(panel[0],new FrameLayout.LayoutParams(-1,-1));
                focused[0]=text(panel[0],"Other control");
                check(focused[0].requestFocus()&&text(panel[0],twoMinutes)!=null,"Initial visible countdown or focus missing");
            });
            boolean[] updated={false};
            await(()->{runOnMainSync(()->updated[0]=text(panel[0],oneMinute)!=null);return updated[0];});
            ui(()->check(focused[0].hasFocus(),"Countdown refresh moved D-pad focus"));
        }finally{
            ui(()->{if(panel[0]!=null)panel[0].close();timer.close();});
        }
    }
    private void guideChangesInOpenControls()throws Exception{
        long[] end={0};
        boolean[] bufferAvailable={false};
        VelaSleepTimer timer=new VelaSleepTimer(()->{});
        VelaPlaybackTray[] tray={null};
        String endOption=L10n.text("At the end of this programme");
        String preset=String.format(Ui.locale(),L10n.text("Stop in %d min"),30);
        try{
            ui(()->{
                tray[0]=new VelaPlaybackTray(activity,"Demo","Demo",null,()->{
                    List<VelaPlaybackPanel.Item> items=new ArrayList<>();
                    if(bufferAvailable[0])items.add(new VelaPlaybackPanel.Item(L10n.text("Pause live TV"),"",()->{}));
                    timer.items(items,end[0]);
                    return items;
                },()->{});
                activity.addContentView(tray[0],new FrameLayout.LayoutParams(-1,-1));
            });
            ui(()->{
                ((View)text(tray[0],L10n.text("More")).getParent()).performClick();
                text(tray[0],L10n.text("Sleep timer")).performClick();
                check(text(tray[0],endOption)==null&&text(tray[0],preset).requestFocus(),"Unexpected initial sleep choices");
            });
            await(()->text(tray[0],preset).isLaidOut()&&text(tray[0],preset).getHeight()>0);
            ui(()->{
                tray[0].dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
                check(text(tray[0],preset).isPressed(),"Timer preset did not receive OK down");
                bufferAvailable[0]=true;
                end[0]=System.currentTimeMillis()/1000+1800;
                tray[0].tracksChanged();
            });
            ui(()->{
                check(text(tray[0],endOption)!=null&&text(tray[0],L10n.text("Pause live TV"))!=null,
                        "Open playback controls ignored the updated guide and buffer");
                check(text(tray[0],preset).hasFocus(),"Inserting a tuner action moved focus away from the timer preset");
                tray[0].dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
            });
            ui(()->{
                check(timer.remaining()>1700_000&&timer.remaining()<=1800_000,"Refreshing the guide swallowed a held OK");
                text(tray[0],L10n.text("Sleep timer")).performClick();
                end[0]+=1800;
                bufferAvailable[0]=false;
                tray[0].tracksChanged();
            });
            ui(()->{
                check(text(tray[0],L10n.text("Pause live TV"))==null,"Unavailable tuner action stayed visible");
                text(tray[0],endOption).performClick();
                check(timer.remaining()>3500_000&&timer.remaining()<=3600_000,"Timer used the old guide end time");
            });
        }finally{
            ui(()->{if(tray[0]!=null)tray[0].close();timer.close();});
        }
    }
    private void expiredProgrammeChoice()throws Exception{
        long end=1_900_000_000L;
        long[] wallTime={end*1000-30_000};
        AtomicInteger stopped=new AtomicInteger();
        VelaSleepTimer timer=new VelaSleepTimer(stopped::incrementAndGet,()->wallTime[0]);
        ui(()->{
            timer.after(35);
            List<VelaPlaybackPanel.Item> items=new ArrayList<>();
            timer.items(items,end);
            items.get(0).action.run();
            items.clear();
            timer.items(items,end);
            VelaPlaybackPanel.Item choice=items.get(3);
            check(choice.title.equals(L10n.text("At the end of this programme")),"Programme end choice missing");
            wallTime[0]=end*1000;
            choice.action.run();
            check(stopped.get()==0,"Expired choice stopped playback immediately");
        });
        await(()->stopped.get()==1);
        ui(()->check(timer.remaining()==0,"Fired timer retained its deadline"));
    }
    private void capture(String name)throws Exception{Thread.sleep(250);Bitmap image=getUiAutomation().takeScreenshot();check(image!=null&&image.getWidth()==3840,"Native 4K screenshot required");File out=new File(context.getExternalFilesDir(null),"next-"+name+".png");try(OutputStream stream=new FileOutputStream(out)){image.compress(Bitmap.CompressFormat.PNG,100,stream);}image.recycle();}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){super.onStart();context=getTargetContext();try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator only");}
        VelaSessions.prefs(context).edit().clear().commit();VelaSessions.lock();previous=VelaWorkspace.active(context);profile=VelaWorkspace.create(context,"Vela · Demo");VelaWorkspace.select(context,profile);L10n.choose(context,"es");Ui.init(context);
        phase="bounded-watchlist";long now=System.currentTimeMillis()/1000;VelaShows.Airing airing=new VelaShows.Airing("Documentales · Planeta azul","La 2 · Demo",now+1800,now+5400);JSONObject ref=VelaWatchlist.airing(airing);VelaWatchlist.toggle(context,ref);check(VelaWatchlist.contains(context,VelaWatchlist.airingId(airing)),"Broadcast not saved");other=VelaWorkspace.create(context,L10n.text("Guest").toLowerCase(Locale.ROOT));VelaWorkspace.select(context,other);check(VelaWatchlist.read(context).isEmpty(),"Watchlist crossed profiles");for(int i=0;i<VelaWatchlist.MAX;i++)VelaWatchlist.toggle(context,new JSONObject().put("id","demo-"+i).put("title","Demo "+i).put("kind","tv"));boolean limited=false;try{VelaWatchlist.toggle(context,new JSONObject().put("id","extra").put("title","Extra"));}catch(IOException expected){limited=true;}check(limited&&VelaWatchlist.read(context).size()==64,"Watchlist is unbounded");VelaWorkspace.select(context,profile);
        phase="sleep-lifecycle";AtomicInteger stopped=new AtomicInteger();VelaSleepTimer[] timer={null};ui(()->{timer[0]=new VelaSleepTimer(stopped::incrementAndGet);timer[0].after(35);});await(()->stopped.get()==1);ui(()->{timer[0].after(50);timer[0].close();});Thread.sleep(90);check(stopped.get()==1&&timer[0].remaining()==0,"Cancelled timer fired");List<VelaPlaybackPanel.Item> options=new ArrayList<>();ui(()->timer[0].items(options,now+3600));check(options.size()==1&&options.get(0).action!=null,"Timer choices must be collapsed");ui(options.get(0).action);options.clear();ui(()->timer[0].items(options,now+3600));check(options.size()==4&&options.get(3).action!=null,"Known programme end not offered");options.clear();ui(()->timer[0].items(options,0));check(options.size()==3,"Unknown end time invented");
        phase="expired-programme-choice";expiredProgrammeChoice();
        phase="voice-contract";check(VelaVoice.query("Pon La 1").equals("La 1")&&VelaVoice.query("open HDMI 2").equals("HDMI 2")&&VelaVoice.query("ouvre Plex").equals("Plex")&&VelaVoice.query("cerca documentari").equals("documentari"),"Voice command stripping changed query");check(VelaVoice.genre("documentales esta noche")==3&&VelaVoice.genre("documentari")==3&&VelaVoice.genre("Documentales TV")==0,"Voice genre route guessed a channel name");check(VelaVoice.result(Activity.RESULT_CANCELED,new Intent()).isEmpty(),"Voice cancellation produced action");
        phase="epg-identities";check(VelaDiscovery.eventId("dd_progid","SP12345678.0001").equals("dd:SP12345678.0001"),"Stable sports identity not accepted");check(VelaDiscovery.eventId("onscreen","Final").isEmpty()&&VelaDiscovery.eventId("dd_progid","SH12345678.0001").isEmpty(),"Title or series treated as an event identity");try(SQLiteDatabase db=SQLiteDatabase.create(null)){db.execSQL("CREATE TABLE meta(id INTEGER,updated INTEGER)");db.execSQL("CREATE TABLE programmes(channel TEXT,start INTEGER,end INTEGER,title TEXT,category TEXT)");db.execSQL("CREATE TABLE aliases(alias TEXT,channel TEXT)");VelaDiscovery.ensureEvents(db);db.execSQL("INSERT INTO aliases VALUES ('demo','one')");db.execSQL("INSERT INTO programmes VALUES ('one',?,?,?,'Sports',?)",new Object[]{now-600,now+1800,"Final · Demo","dd:SP12345678.0001"});List<VelaShows.Airing> page=VelaGuideSearch.readWindow(context,db,Collections.singletonMap("demo","Demo"),"alias","",0,new CancellationSignal(),now,now+1,2);check(page.size()==1&&page.get(0).eventId.equals("dd:SP12345678.0001"),"Event ID lost in guide query");}
        String cls=context.getPackageName().endsWith(".lite")?"tv.vela.launcher.LiteHomeActivity":"tv.vela.launcher.HomeActivity";activity=startActivitySync(new Intent().setClassName(context.getPackageName(),cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("velaTools",6));await(()->field(activity,"hub")!=null);hub=(VelaHub)field(activity,"hub");
        phase="countdown-in-open-panel";countdownInOpenPanel();
        phase="guide-changes-in-open-controls";guideChangesInOpenControls();
        phase="draft-editor-cancel";
        String original=VelaLayout.read(context).toString();
        click("Edit home sections");
        await(()->field(hub,"homeEditor")!=null);
        VelaHomeEditor editor=(VelaHomeEditor)field(hub,"homeEditor");
        String key=((List<String>)field(editor,"keys")).get(0);
        EditText draftName=(EditText)((LinearLayout)field(editor,"detail")).getChildAt(2);
        ui(()->{
            draftName.setText("Demo section");
            editor.list.getOnItemLongClickListener().onItemLongClick(editor.list,editor.list.getChildAt(0),0,0);
            editor.list.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        });
        check(!key.equals(editor.draft.getJSONArray("order").getString(0)),"D-pad did not move the selected section");
        check(VelaLayout.read(context).toString().equals(original),"Draft edit wrote before Save");
        capture("01-editor");ui(editor.cancel::performClick);
        check(VelaLayout.read(context).toString().equals(original),"Cancel changed home");
        click("Edit home sections");await(()->field(hub,"homeEditor")!=null);
        VelaHomeEditor saved=(VelaHomeEditor)field(hub,"homeEditor");
        EditText savedName=(EditText)((LinearLayout)field(saved,"detail")).getChildAt(2);
        ui(()->{savedName.setText("Demo section");saved.save.performClick();});
        await(()->"Demo section".equals(VelaLayout.title(context,key)));
        phase="programme-search-watchlist";searchWatchlist(new VelaShows.Airing("Search broadcast · Demo","Demo TV",now+3600,now+7200));
        phase="same-slot-episodes";sameSlotEpisodes(now+7200);
        phase="watchlist-native";tools(3);click("Watch later");check(text(hub,airing.title)!=null,"Saved airing missing from UI");capture("02-watch-later");
        if(!VelaEdition.lite(context)){
            JSONObject legacy=new JSONObject().put("id","legacy").put("kind","media").put("title","Legacy series").put("provider","Plex");
            VelaWatchlist.toggle(context,legacy);
            for(int i=1;i<=2;i++)VelaWatchlist.toggle(context,new JSONObject().put("id","episode-"+i).put("kind","media")
                    .put("title","Saved series").put("episode","T1 · E"+i).put("provider","Plex"));
            tools(3);click("Watch later");
            check(text(hub,"Saved series\nT1 · E1 · Plex")!=null&&text(hub,"Saved series\nT1 · E2 · Plex")!=null,"Watch later rows hide episode numbers");
            check(text(hub,"Legacy series\nPlex")!=null,"Older watchlist row lost its title or provider");
        }
        phase="diagnostics-native";tools(5);click("Compatibility assistant");check(text(hub,L10n.text("HDMI connections"))!=null&&text(hub,L10n.text("Antenna and guide"))!=null,"Guided compatibility actions missing");capture("03-compatibility");
        phase="channel-context";ui(hub::close);hub=null;AtomicInteger fav=new AtomicInteger();ui(()->((VelaHub.Owner)activity).channelOptions("La 2 · Demo",fav::incrementAndGet,airing));VelaPlaybackPanel menu=(VelaPlaybackPanel)field(activity,"channelMenu");check(menu!=null&&text(menu,L10n.text("Correct programme guide"))!=null,"Channel correction missing from hold menu");capture("04-channel-menu");ui(()->text(menu,L10n.text("Toggle favourite")).performClick());check(fav.get()==1&&field(activity,"channelMenu")==null,"Favourite menu did not release");
        phase="timer-native";final VelaPlaybackPanel[] panel={null};ui(()->{panel[0]=new VelaPlaybackPanel(activity,"La 2 · Demo",()->{List<VelaPlaybackPanel.Item> items=new ArrayList<>();timer[0].items(items,System.currentTimeMillis()/1000+2700);return items;},()->{});((FrameLayout)activity.findViewById(android.R.id.content)).addView(panel[0],new FrameLayout.LayoutParams(-1,-1));});ui(()->((List<View>)quiet(panel[0],"buttons")).get(0).performClick());capture("05-sleep-timer");ui(panel[0]::close);
        phase="pin-and-guest";tools(4);click("Protect settings with a PIN");List<View> pins=(List<View>)field(hub,"controls");ui(()->{((EditText)pins.get(0)).setText("2468");((EditText)pins.get(1)).setText("2468");});click("Save PIN");await(()->VelaSessions.configured(context)&&text(hub,L10n.text("Enter guest mode"))!=null);String material=VelaSessions.prefs(context).getAll().toString();check(!material.contains("2468")&&!VelaWorkspace.snapshot(context).toString().contains("vela-lock"),"PIN was stored raw or exported");click("Enter guest mode");await(()->VelaSessions.guest(context)&&field(activity,"hub")==null);tools(6);check(hub.page()==4&&text(hub,L10n.text("Guest mode"))!=null,"Guest opened protected editor");capture("06-guest-lock");EditText pin=(EditText)field(hub,"input");ui(()->pin.setText("0000"));click("Unlock");await(()->((TextView)field(hub,"status")).getText().toString().contains(L10n.text("Incorrect PIN. After five attempts, wait 30 seconds.")));check(VelaSessions.guest(context),"Wrong PIN left guest mode");ui(()->pin.setText("2468"));click("Unlock");await(()->!VelaSessions.guest(context)&&field(activity,"hub")==null);check(VelaWorkspace.active(context).equals(profile),"Guest exit restored wrong profile");
        phase="guest-profile-selection";guestProfileSelection();
        phase="remote-voice-roundtrip";
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("pm grant "+context.getPackageName()+" android.permission.RECORD_AUDIO");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}
        runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SEARCH)));
        VelaVoiceSearch before=(VelaVoiceSearch)field(activity,"voiceSearchUI");
        check(before==null||!before.active(),"Voice started on key down");
        runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_SEARCH)));
        VelaVoiceSearch voice=(VelaVoiceSearch)field(activity,"voiceSearchUI");
        check(voice!=null&&voice.active(),"Search key did not open the integrated voice panel");
        String words="voice fixture";
        VelaVoiceSearch.Host callbacks=(VelaVoiceSearch.Host)field(voice,"host");
        ui(()->{voice.cancel();callbacks.phrase(words);});
        hub=(VelaHub)field(activity,"hub");
        check(hub!=null&&hub.page()==0&&((EditText)field(hub,"input")).getText().toString().equals(words),"Voice result did not return to integrated search");
        check(text(hub,L10n.text("Speak"))!=null,"Voice entry missing");capture("07-search");ui(hub::close);hub=null;
        result.putString("result","PASS bounded/profile-isolated Watch later; one-shot/cancelled sleep timer; conditional programme end; multilingual voice parsing and cancellation; safe EPG event IDs; native draft Save/Cancel and D-pad reorder; themed programme/channel/compatibility/PIN panels; salted PIN and denied guest edits; native 4K screenshots. Owned emulator only.");
    }catch(Throwable error){result.putString("phase",phase);result.putString("error",android.util.Log.getStackTraceString(error));}finally{if(activity!=null)ui(()->{if(hub!=null)hub.close();activity.finish();});if(guestId==null)guestId=VelaSessions.prefs(context).getString("guest","");VelaSessions.prefs(context).edit().clear().commit();VelaSessions.lock();try{VelaWorkspace.select(context,previous==null?"default":previous);for(String id:new String[]{profile,other,guestId})if(id!=null&&VelaWorkspace.profiles(context).containsKey(id))VelaWorkspace.delete(context,id);for(Map.Entry<String,String> p:VelaWorkspace.profiles(context).entrySet())if(p.getValue().equals(L10n.text("Guest")))VelaWorkspace.delete(context,p.getKey());}catch(Exception ignored){}}finish(result.containsKey("error")?1:-1,result);}
}
