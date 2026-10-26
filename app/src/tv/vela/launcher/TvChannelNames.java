package tv.vela.launcher;
import android.content.*;import java.util.*;import java.util.regex.*;

/** Conservative quality grouping: country, category, region and time-shift remain distinct. */
final class TvChannelNames {
    static final int MAX_VARIANTS=8;
    private static final String ISO="ES|PT|FR|DE|IT|UK|US|CA|AR|MX|BR|NL|BE|CH|AT|PL|RO";
    private static final Pattern COUNTRY=Pattern.compile("(?i)^(?:\\[("+ISO+")\\]\\s*|("+ISO+")(?:\\s+(?:MV|HD|FHD))?\\s*[|:_-]+\\s*)");
    private static final Pattern QUALITY=Pattern.compile("(?i)(?:\\s+|\\s*[\\[(])(FHD|FULL\\s*HD|UHD|HD|SD|4K|8K|HEVC|H[ .]?265|H[ .]?264|1080P?|720P?)[\\])]?$");
    private static final Pattern TRANSPORT=Pattern.compile("(?i)\\s*\\((ZAP|AV|VDF|RAW|HLS|TS)\\)$");
    static String display(String value){String out=COUNTRY.matcher(value.trim()).replaceFirst("");for(int i=0;i<4;i++){String next=TRANSPORT.matcher(QUALITY.matcher(out).replaceFirst("")).replaceFirst("").trim();if(next.equals(out))break;out=next;}return out;}
    static String quality(String value){String out=TRANSPORT.matcher(value.trim()).replaceFirst("").trim(),codec="";for(int i=0;i<4;i++){Matcher m=QUALITY.matcher(out);if(!m.find())break;String q=m.group(1).toUpperCase(Locale.ROOT).replaceAll("FULL\\s*HD","FHD");if(!q.equals("HEVC")&&!q.matches("H[ .]?26[45]"))return q;codec=q;out=out.substring(0,m.start()).trim();}return codec;}
    static String match(String value){String out=display(value).replaceFirst("(?i)\\.tv$","");return TvCatalogCache.normalize(out).replaceAll("[^a-z0-9+]","");}
    static String country(String value){Matcher m=COUNTRY.matcher(value.trim());return m.find()?(m.group(1)==null?m.group(2):m.group(1)).toUpperCase(Locale.ROOT):"";}
    static String country(LiveTvData.Channel ch){String value=country(ch.name);return value.isEmpty()?country(ch.group):value;}
    static String family(LiveTvData.Channel ch){String name=match(ch.name),country=country(ch);if(name.equals("factoriadeficcion"))name="fdf";if(country.equals("ES")&&(name.equals("trecetv")||name.equals("eltrece")))name="trece";return LiveTvData.hash(country+"|"+TvCatalogCache.normalize(ch.group)+"|"+name);}
    static int rank(String value){String q=quality(value);return q.equals("FHD")||q.startsWith("1080")?60:q.equals("HD")||q.startsWith("720")?50:q.equals("UHD")||q.equals("4K")?40:q.equals("SD")?20:30;}
    static List<LiveTvData.Channel> variants(LiveTvData.Channel ch){return ch.variants.isEmpty()?Collections.singletonList(ch):ch.variants;}
    static boolean favorite(LiveTvData.Channel ch,Set<String> ids){for(LiveTvData.Channel item:variants(ch))if(ids.contains(item.id))return true;return false;}
    static LiveTvData.Channel choose(Context c,String source,List<LiveTvData.Channel> values){
        if(values.isEmpty())return null;String saved=LiveTvData.prefs(c).getString("quality-"+LiveTvData.hash(source+family(values.get(0))),"");LiveTvData.Channel chosen=values.get(0);for(LiveTvData.Channel v:values)if(v.id.equals(saved)){chosen=v;break;}
        LiveTvData.Channel out=new LiveTvData.Channel(chosen.id,chosen.name,chosen.group,chosen.logo,chosen.streams,chosen.xtreamId,chosen.xtreamAccount,chosen.inputId,chosen.number,chosen.epgId);out.archiveDays=chosen.archiveDays;out.replayTemplate=chosen.replayTemplate;out.replayMode=chosen.replayMode;out.variants=Collections.unmodifiableList(new ArrayList<>(values));return out;
    }
    static void prefer(Context c,String source,LiveTvData.Channel chosen){SharedPreferences p=LiveTvData.prefs(c);SharedPreferences.Editor e=p.edit();String key="quality-"+LiveTvData.hash(source+family(chosen));int count=0;for(String old:p.getAll().keySet())if(old.startsWith("quality-")&&++count>=128)e.remove(old);e.putString(key,chosen.id).apply();}
}
