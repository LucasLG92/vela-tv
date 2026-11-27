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
    /** Keep the first card in feed order and all identity evidence for its matched copies. */
    static final class Row {
        final List<PlexClient.Film> films=new ArrayList<>();
        private final List<Match> matches=new ArrayList<>();
        private final int limit;
        Row(int limit){this.limit=limit;}

        void add(PlexClient.Film film){
            Match candidate=new Match(film,films.size()), match=null;
            for(Match existing:matches){
                if(!existing.matches(candidate))continue;
                if(match!=null){
                    // Two distinct cards share this incomplete set of IDs.
                    match=null;
                    break;
                }
                match=existing;
            }
            if(match!=null){
                match.remember(candidate);
                PlexClient.Film card=films.get(match.index);
                if(card.alternative==null&&!card.packageName.equals(film.packageName)){
                    films.set(match.index,card.withAlternative(film));
                }
            }else if(films.size()<limit){
                matches.add(candidate);
                films.add(film);
            }
        }

        private static final class Match {
            final int index;
            final String type;
            final Set<String> localKeys=new HashSet<>();
            final Map<String,String> ids=new HashMap<>();
            Match(PlexClient.Film film,int index){
                this.index=index;
                type=film.type;
                record(film);
                if(film.alternative!=null)record(film.alternative);
            }
            private void record(PlexClient.Film film){
                localKeys.add(film.packageName+"|"+film.key);
                rememberIds(film.externalIds);
            }
            void remember(Match other){
                localKeys.addAll(other.localKeys);
                rememberIds(other.ids);
            }
            private void rememberIds(Map<String,String> other){
                for(Map.Entry<String,String> id:other.entrySet()){
                    String previous=ids.get(id.getKey());
                    ids.put(id.getKey(),previous==null||previous.equals(id.getValue())?id.getValue():"");
                }
            }
            boolean matches(Match other){
                if(!Objects.equals(type,other.type))return false;
                boolean shared=!Collections.disjoint(localKeys,other.localKeys);
                if(ids.containsValue("")||other.ids.containsValue(""))return false;
                for(Map.Entry<String,String> id:ids.entrySet()){
                    String value=other.ids.get(id.getKey());
                    if(value==null)continue;
                    if(value.isEmpty()||!value.equals(id.getValue()))return false;
                    shared=true;
                }
                return shared;
            }
        }
    }
    static List<PlexClient.Film> merge(String row,List<PlexClient.Film> first,List<PlexClient.Film> second)throws IOException{
        String type=type(row);List<PlexClient.Film> all=new ArrayList<>(first);all.addAll(second);
        all.sort(row.equals("top_rated")?(a,b)->Double.compare(b.homeRating,a.homeRating):(a,b)->Long.compare(b.addedAtMs,a.addedAtMs));
        Row out=new Row(LIMIT);
        for(PlexClient.Film film:all){
            if(!type.equals(film.type))continue;
            film.recentlyAdded=row.startsWith("recent_");
            out.add(film);
        }
        return out.films;
    }
    static double rating(double value){return Double.isNaN(value)||Double.isInfinite(value)?0:Math.max(0,Math.min(10,value));}
}
