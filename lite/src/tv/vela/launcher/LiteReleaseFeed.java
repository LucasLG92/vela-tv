package tv.vela.launcher;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Public release metadata only. An APK still needs hash, package and signer checks. */
final class LiteReleaseFeed {
    static final String REPO="https://github.com/LucasLG92/vela-tv";
    static final String API="https://api.github.com/repos/LucasLG92/vela-tv/releases?per_page=30";
    static final long MAX_APK=16L*1024*1024;
    private static final Pattern VERSION=Pattern.compile("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?");
    static final class Release {
        final String tag,version,url,sha256;final long size;final boolean preview;
        Release(String tag,String version,String url,String sha256,long size,boolean preview){this.tag=tag;this.version=version;this.url=url;this.sha256=sha256;this.size=size;this.preview=preview;}
        String page(){return REPO+"/releases/tag/"+tag;}
        JSONObject json()throws JSONException{return new JSONObject().put("tag_name",tag).put("prerelease",preview).put("draft",false).put("assets",new JSONArray().put(new JSONObject().put("name","Vela-Lite-"+version+".apk").put("browser_download_url",url).put("digest","sha256:"+sha256).put("size",size).put("state","uploaded")));}
    }
    static Release newest(JSONArray releases,String installed,boolean previews){
        if(!validVersion(installed))return null;Release best=null;
        for(int i=0;i<Math.min(30,releases.length());i++){
            JSONObject item=releases.optJSONObject(i);if(item==null||item.optBoolean("draft",true))continue;
            String tag=item.optString("tag_name");if(!tag.startsWith("lite-v"))continue;String version=tag.substring(6);
            if(!validVersion(version)||compare(version,installed)<=0)continue;
            boolean preview=item.optBoolean("prerelease")||version.contains("-");if(preview&&!previews)continue;
            String filename="Vela-Lite-"+version+".apk",expected=REPO+"/releases/download/"+tag+"/"+filename;JSONArray assets=item.optJSONArray("assets");
            for(int a=0;assets!=null&&a<Math.min(32,assets.length());a++){
                JSONObject asset=assets.optJSONObject(a);if(asset==null||!filename.equals(asset.optString("name"))||!expected.equals(asset.optString("browser_download_url"))||!asset.optString("state").equals("uploaded"))continue;
                String digest=asset.optString("digest").toLowerCase(Locale.ROOT);long size=asset.optLong("size");
                if(size<1024||size>MAX_APK||!digest.matches("sha256:[a-f0-9]{64}"))continue;
                if(best==null||compare(version,best.version)>0)best=new Release(tag,version,expected,digest.substring(7),size,preview);
            }
        }
        return best;
    }
    static boolean validVersion(String value){return value!=null&&value.length()<96&&VERSION.matcher(value).matches();}
    static int compare(String first,String second){
        Matcher a=VERSION.matcher(first),b=VERSION.matcher(second);if(!a.matches()||!b.matches())throw new IllegalArgumentException("Invalid release version");
        for(int i=1;i<=3;i++){int n=Long.compare(Long.parseLong(a.group(i)),Long.parseLong(b.group(i)));if(n!=0)return n;}
        String ap=a.group(4),bp=b.group(4);if(ap==null||bp==null)return ap==bp?0:ap==null?1:-1;
        String[] aa=ap.split("[.-]"),bb=bp.split("[.-]");
        for(int i=0;i<Math.min(aa.length,bb.length);i++){
            String x=aa[i],y=bb[i];boolean xn=x.matches("[0-9]+"),yn=y.matches("[0-9]+");int n;
            if(xn&&yn){x=x.replaceFirst("^0+(?!$)","");y=y.replaceFirst("^0+(?!$)","");n=x.length()==y.length()?x.compareTo(y):Integer.compare(x.length(),y.length());}
            else n=xn!=yn?(xn?-1:1):x.compareTo(y);
            if(n!=0)return n;
        }
        return Integer.compare(aa.length,bb.length);
    }
}
