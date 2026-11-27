package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Full-only real encrypted catalog and Xtream preference migration, without a network request. */
public final class AndroidWorkspaceDataTest extends Instrumentation {
    private void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    private static final class Sandbox extends ContextWrapper {
        Sandbox(Context c){super(c);}
        public Context getApplicationContext(){return this;}
        public SharedPreferences getSharedPreferences(String n,int mode){return super.getSharedPreferences("owned-workspace-data-"+n,mode);}
        public boolean deleteSharedPreferences(String n){return super.deleteSharedPreferences("owned-workspace-data-"+n);}
        public File getCacheDir(){File f=new File(super.getCacheDir(),"owned-workspace-data");f.mkdirs();return f;}
        public File getFilesDir(){File f=new File(super.getFilesDir(),"owned-workspace-data");f.mkdirs();return f;}
    }
    private String stamp(long seconds){SimpleDateFormat f=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(seconds*1000));}
    private void backupRoundTrip(Context c)throws Exception{
        String original=VelaWorkspace.active(c);
        String profile=VelaWorkspace.create(c,"Backup fixture");
        VelaWorkspace.select(c,profile);
        try{
            SharedPreferences visuals=VelaWorkspace.prefs(c,"visuals");
            visuals.edit().putInt("appearance_color",2).commit();
            List<SharedPreferences> sources=new ArrayList<>();
            for(int i=0;i<96;i++){
                sources.add(VelaWorkspace.prefs(c,"tv-personal-"+LiveTvData.hash("empty-"+i)));
            }
            check(VelaWorkspace.snapshot(c).getJSONObject("settings").has("visuals"),
                    "Empty channel sources excluded the profile appearance from backup");
            SidebarApps.prefs(c).edit().putString("slots","[\"com.example.first\",\"com.example.second\",\"com.example.last\"]").commit();
            SidebarApps.assign(c,1,"");
            JSONObject backup=VelaWorkspace.snapshot(c);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            VelaWorkspace.write(backup,bytes);
            JSONObject portable=VelaWorkspace.read(new ByteArrayInputStream(bytes.toByteArray()));
            SidebarApps.assign(c,1,"com.example.changed");
            visuals.edit().putInt("appearance_color",3).commit();
            VelaWorkspace.apply(c,portable,true);
            check(Arrays.equals(SidebarApps.slots(c),new String[]{"com.example.first","","com.example.last"}),"Restore moved or removed a hidden shortcut");
            check(visuals.getInt("appearance_color",-1)==2,"Restore lost the profile appearance");
            VelaWorkspace.undo(c);
            check(SidebarApps.slots(c)[1].equals("com.example.changed")&&visuals.getInt("appearance_color",-1)==3,"Undo lost pre-restore settings");
            for(Object invalid:new Object[]{"invalid package",JSONObject.NULL,123}){
                JSONObject malformed=new JSONObject(portable.toString());
                malformed.getJSONObject("settings").getJSONObject("sidebar_apps").put("slots",new JSONArray().put(invalid).toString());
                try{VelaWorkspace.read(new ByteArrayInputStream(malformed.toString().getBytes(StandardCharsets.UTF_8)));throw new AssertionError("Accepted invalid shortcut "+invalid);}
                catch(IOException expected){}
            }
            for(int i=0;i<94;i++)sources.get(i).edit().putString("hidden","[]").commit();
            check(VelaWorkspace.snapshot(c).getJSONObject("settings").length()==96,"A full-sized backup should contain all 96 sections");
            sources.get(94).edit().putString("hidden","[]").commit();
            try{VelaWorkspace.snapshot(c);throw new AssertionError("Oversized backup was silently truncated");}
            catch(IOException expected){}
        }finally{
            VelaWorkspace.select(c,original);
            VelaWorkspace.delete(c,profile);
        }
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){super.onStart();Bundle result=new Bundle();String phase="setup";Context base=getTargetContext(),c=new Sandbox(base);try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        phase="portable-backup-round-trip";backupRoundTrip(c);
        phase="catalog-v7-migration";File folder=new File(c.getCacheDir(),"tv-catalogs");folder.mkdirs();File database=new File(folder,"channels.db");database.delete();String source="m3u:"+LiveTvData.hash("owned-workspace-playlist");LiveTvData.prefs(c).edit().putString("mode","m3u").putString("m3u-id",source.substring(4)).commit();LiveTvData.Channel hd=new LiveTvData.Channel("demo-hd","La 1 HD","Generalistas","",Collections.singletonList("https://example.invalid/demo.m3u8"));
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(database,null)){db.execSQL("CREATE TABLE channels(source TEXT NOT NULL,id TEXT NOT NULL,category TEXT NOT NULL,name TEXT NOT NULL,search TEXT NOT NULL,ordinal INTEGER NOT NULL,payload BLOB NOT NULL,family TEXT NOT NULL DEFAULT '',quality INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(source,id))");ContentValues v=new ContentValues();v.put("source",source);v.put("id",hd.id);v.put("category",hd.group);v.put("name",hd.name);v.put("search",TvCatalogCache.normalize(hd.name));v.put("ordinal",0);v.put("payload",new TvPrivateData(c).encrypt(TvCatalogCache.json(hd)));v.put("family",TvChannelNames.family(hd));v.put("quality",TvChannelNames.rank(hd.name));db.insertOrThrow("channels",null,v);db.setVersion(7);}
        TvCatalogCache cache=new TvCatalogCache(c);check(cache.page(source,null,"La 1",null,0).channels.size()==1,"v7 catalogue lost its encrypted channel");try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor row=db.rawQuery("SELECT guide_name FROM channels",null)){check(db.getVersion()==9&&row.moveToFirst()&&row.getString(0).equals("la1"),"Guide name index did not migrate");}
        phase="programmes-use-active-source";long now=System.currentTimeMillis()/1000;String xml="<tv><channel id='la1'><display-name>La 1</display-name></channel><channel id='foreign'><display-name>Otro canal</display-name></channel><programme channel='la1' start='"+stamp(now+600)+"' stop='"+stamp(now+2400)+"'><title>Naturaleza T1 E3</title></programme><programme channel='foreign' start='"+stamp(now+600)+"' stop='"+stamp(now+2400)+"'><title>Naturaleza de otro proveedor</title></programme></tv>";XmlTvGuide guide=new XmlTvGuide(c);guide.importGuide(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),LiveTvData.hash(guide.url()),now);List<VelaShows.Airing> airings=cache.airings("naturaleza",0,new CancellationSignal());check(airings.size()==1&&airings.get(0).channel.equals("La 1"),"Programme search escaped the configured source or kept quality duplicates");VelaGuideFix.set(c,hd.name,"foreign",60);List<VelaShows.Airing> corrected=cache.airings("naturaleza",0,new CancellationSignal());check(corrected.size()==1&&corrected.get(0).title.equals("Naturaleza de otro proveedor")&&corrected.get(0).start==now+4200,"Manual guide identity/offset did not reach active catalog search");VelaGuideFix.reset(c,hd.name);new TvPersonalization(c,source).hide(TvChannelNames.family(hd),hd.name,true);check(cache.airings("naturaleza",0,new CancellationSignal()).isEmpty(),"Hidden channels remain in programme results");new TvPersonalization(c,source).hide(TvChannelNames.family(hd),hd.name,false);
        phase="xtream-portable-and-private";XtreamClient client=new XtreamClient(c);XtreamClient.Account account=new XtreamClient.Account("https://example.invalid","owned-demo-user","OWNED-DEMO-SECRET",UUID.randomUUID().toString(),true,true);client.save(account);LiveTvData.Channel one=new LiveTvData.Channel("xc-42","La 1 FHD","Generalistas","",Collections.emptyList(),"42",account.id);try(TvCatalogCache.Writer writer=cache.writer("xc:"+account.id+":*")){writer.add(one,false);writer.commit("{}");}client.toggleFavorite(one);check(client.favoriteIds().contains("xc-42"),"Xtream favourite was not saved");cache.removePrefix("xc:"+account.id+":");check(client.favoriteIds().contains("xc-42"),"Cache eviction destroyed favourite metadata");
        phase="profile-account-isolation";String profile=VelaWorkspace.create(c,"Owned profile");VelaWorkspace.select(c,profile);check(client.favoriteIds().isEmpty(),"A new profile inherited Xtream favourites");client.toggleFavorite(one);client.toggleFavorite(one);check(client.favoriteIds().isEmpty(),"Profile favourite removal failed");VelaWorkspace.select(c,VelaWorkspace.DEFAULT);check(client.favoriteIds().contains("xc-42"),"Another profile modified Principal");
        phase="legacy-source-migration";String legacy=client.source();VelaWorkspace.prefs(c,"tv-personal-"+LiveTvData.hash(legacy)).edit().putString("hidden","[{\"k\":\"old-hidden\",\"n\":\"Canal demo\"}]").commit();check(new TvPersonalization(c,legacy).hidden("old-hidden"),"Existing organisation lost in portable source migration");String firstPortable=VelaWorkspace.channelSource(c,legacy);JSONObject backup=VelaWorkspace.snapshot(c);check(!backup.toString().contains("OWNED-DEMO-SECRET")&&!backup.toString().contains("example.invalid")&&!backup.toString().contains("owned-demo-user"),"Xtream credentials or host leaked into the backup");
        phase="device-local-login-ids";XtreamClient.Account otherDevice=new XtreamClient.Account(account.base,account.user,account.password,UUID.randomUUID().toString(),true,true);client.save(otherDevice);LiveTvData.Channel replacement=new LiveTvData.Channel("xc-42",one.name,one.group,"",Collections.emptyList(),"42",otherDevice.id);try(TvCatalogCache.Writer writer=cache.writer("xc:"+otherDevice.id+":*")){writer.add(replacement,false);writer.commit("{}");}check(client.favoriteIds().contains("xc-42")&&VelaWorkspace.channelSource(c,client.source()).equals(firstPortable)&&new TvPersonalization(c,client.source()).hidden("old-hidden"),"Reauthorizing the same provider lost portable favourites or organisation");
        phase="different-account-isolation";client.save(new XtreamClient.Account(account.base,"another-demo-user",account.password,UUID.randomUUID().toString(),true,true));check(client.favoriteIds().isEmpty()&&!new TvPersonalization(c,client.source()).hidden("old-hidden"),"A different provider account inherited choices");
        result.putString("result","PASS portable backup, hidden shortcuts, section limits, restore and undo, Full real SQLite v7→v9 migration, encrypted channel reconstruction, active-source-only programme search, hidden-channel filtering, favourite metadata after cache eviction, profile isolation, portable source migration, same-provider reauthorization and separate provider accounts. No network or physical tuner request.");
    }catch(Throwable failure){result.putString("phase",phase);result.putString("error",android.util.Log.getStackTraceString(failure));}finally{remove(c.getCacheDir());remove(c.getFilesDir());File[] prefs=new File(base.getApplicationInfo().dataDir,"shared_prefs").listFiles();if(prefs!=null)for(File f:prefs)if(f.getName().startsWith("owned-workspace-data-")&&f.getName().endsWith(".xml")){String name=f.getName().substring(0,f.getName().length()-4);base.getSharedPreferences(name,0).edit().clear().commit();base.deleteSharedPreferences(name);}}finish(result.containsKey("error")?1:-1,result);}
    private void remove(File f){File[] files=f.listFiles();if(files!=null)for(File child:files)remove(child);f.delete();}
}
