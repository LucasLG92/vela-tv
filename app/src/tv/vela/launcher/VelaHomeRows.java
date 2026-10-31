package tv.vela.launcher;

import java.io.IOException;
import java.util.*;

/** Optional home feeds: one small request per enabled row/provider, no resident catalogue. */
final class VelaHomeRows {
    static final int LIMIT=12;
    private VelaHomeRows(){}
    static String type(String row)throws IOException{
        if(!Arrays.asList("recent_movies","recent_series","unwatched","top_rated").contains(row))throw new IOException("Unknown home row");
        return row.equals("recent_series")?"show":"movie";
    }
    static CatalogFilters.Query query(String row,boolean jellyfin)throws IOException{
        type(row);CatalogFilters.Query q=new CatalogFilters.Query();
        if(row.equals("top_rated"))q.sort=jellyfin?"CommunityRating":"audienceRating";
        if(row.equals("unwatched"))q.values.put("$state",Arrays.asList(new CatalogFilters.Choice("unwatched","unwatched")));
        return q;
    }
    static List<PlexClient.Film> merge(String row,List<PlexClient.Film> first,List<PlexClient.Film> second)throws IOException{
        String type=type(row);List<PlexClient.Film> all=new ArrayList<>(first);all.addAll(second);
        all.sort(row.equals("top_rated")?(a,b)->Double.compare(b.homeRating,a.homeRating):(a,b)->Long.compare(b.addedAtMs,a.addedAtMs));
        Set<String> seen=new HashSet<>();List<PlexClient.Film> out=new ArrayList<>();
        for(PlexClient.Film film:all){
            if(!type.equals(film.type))continue;
            String id=film.externalId.isEmpty()?film.packageName+"|"+film.key:film.type+"|"+film.externalId;
            if(!seen.add(id))continue;
            film.recentlyAdded=row.startsWith("recent_");
            out.add(film);if(out.size()==LIMIT)break;
        }
        return out;
    }
    static double rating(double value){return Double.isNaN(value)||Double.isInfinite(value)?0:Math.max(0,Math.min(10,value));}
}
