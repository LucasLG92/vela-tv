package tv.vela.launcher;
import org.json.*;
public final class ReleaseFeedTest {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static JSONObject release(String version,boolean preview)throws Exception{return new ReleaseFeed.Release("v"+version,version,ReleaseFeed.REPO+"/releases/download/v"+version+"/Vela-"+version+".apk",new String(new char[64]).replace('\0','a'),2048,preview).json();}
    static ReleaseFeed.Release newest(JSONObject item,boolean previews){return ReleaseFeed.newest(new JSONArray().put(item),"1.11.0-preview",previews);}
    public static void main(String[] args)throws Exception{
        check(ReleaseFeed.compare("1.12.0","1.12.0-preview")>0,"Stable must beat preview");
        check(ReleaseFeed.compare("1.12.0-preview.10","1.12.0-preview.9")>0,"Numeric prerelease order");
        check(ReleaseFeed.compare("1.10.0","1.9.9")>0,"Numeric version order");
        check(newest(release("1.12.0-preview",true),true)!=null,"Preview users need preview updates");
        check(newest(release("1.12.0-preview",true),false)==null,"Stable channel received a preview");
        check(newest(release("1.12.0-beta",false),false)==null,"Tag preview bypassed stable channel");
        check(newest(release("1.12.0",false),false)!=null,"Stable release missing");
        check(newest(release("1.11.0-preview",true),true)==null,"Same release offered again");
        check(newest(release("1.10.0",false),true)==null,"Downgrade offered");
        check(newest(release("1.12.0",false).put("draft",true),true)==null,"Draft offered");
        JSONArray unordered=new JSONArray().put(release("1.12.0-preview",true)).put(release("1.14.0",false)).put(release("1.13.0",false));
        check(ReleaseFeed.newest(unordered,"1.11.0-preview",true).version.equals("1.14.0"),"API ordering assumed");
        for(String invalid:new String[]{"../1.12.0","1.2","01.12.0","1.12.0/evil","1.12.0?x","1.12.0#x","999999999999999.0.0"})check(!ReleaseFeed.validVersion(invalid),"Unsafe tag: "+invalid);
        for(String key:new String[]{"browser_download_url","digest","name","size","state"}){
            JSONObject item=release("1.12.0",false),asset=item.getJSONArray("assets").getJSONObject(0);asset.remove(key);check(newest(item,true)==null,"Missing "+key+" accepted");
        }
        for(String url:new String[]{"http://github.com/LucasLG92/vela-tv/releases/download/v1.12.0/Vela-1.12.0.apk","https://github.com/evil/vela-tv/releases/download/v1.12.0/Vela-1.12.0.apk","https://github.com/LucasLG92/vela-tv/releases/download/v1.12.0/Vela-1.12.0.apk?token=x"}){
            JSONObject item=release("1.12.0",false);item.getJSONArray("assets").getJSONObject(0).put("browser_download_url",url);check(newest(item,true)==null,"Untrusted asset accepted");
        }
        JSONObject huge=release("1.12.0",false);huge.getJSONArray("assets").getJSONObject(0).put("size",ReleaseFeed.MAX_APK+1);check(newest(huge,true)==null,"Oversize APK accepted");
        System.out.println("PASS "+checks+" release metadata, channel, version and origin checks");
    }
}
