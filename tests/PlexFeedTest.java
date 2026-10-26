package tv.vela.launcher;
import org.json.*;
import java.util.*;

public class PlexFeedTest {
    public static void main(String[] args) throws Exception {
        require(PlexClient.avatarAllowed("https://plex.tv/users/example/avatar")&&PlexClient.avatarAllowed("https://secure.gravatar.com/avatar/example"),"Plex avatar sources");
        for(String url:new String[]{"http://plex.tv/users/example/avatar","https://plex.tv.attacker.invalid/avatar","https://user@plex.tv/avatar","https://plex.tv:8080/avatar","file:///avatar.png","https://127.0.0.1/avatar"})require(!PlexClient.avatarAllowed(url),"Unsafe avatar source accepted");
        require(PlexClient.profileKey("").isEmpty(),"Unlinked account must not share a previous avatar");
        require(PlexClient.profileKey("sample-account").equals(PlexClient.profileKey("sample-account"))&&!PlexClient.profileKey("sample-account").equals(PlexClient.profileKey("other-account"))&&!PlexClient.profileKey("sample-account").contains("sample-account"),"Avatar cache must separate accounts without embedding tokens");
        JSONObject movie = new JSONObject().put("ratingKey","11").put("type","movie").put("title","Library movie")
                .put("thumb","/library/metadata/11/thumb/1").put("art","/library/metadata/11/art/1")
                .put("duration",600000).put("viewOffset",300000).put("year",2020)
                .put("Image",new JSONArray().put(new JSONObject().put("type","clearLogo").put("url","/library/metadata/11/clearLogo/1")));
        JSONObject episode = new JSONObject().put("ratingKey","12").put("type","episode").put("title","Episode title")
                .put("grandparentTitle","Library series").put("grandparentRatingKey","8").put("grandparentThumb","/library/metadata/8/thumb/1")
                .put("parentIndex",2).put("index",4).put("duration",100).put("viewOffset",80);
        JSONObject show = new JSONObject().put("ratingKey","13").put("type","show").put("title","New series")
                .put("thumb","/library/metadata/13/thumb/1")
                .put("Image",new JSONArray().put(new JSONObject().put("type","clearLogo").put("url","https://example.invalid/logo.png")));
        JSONObject music = new JSONObject().put("ratingKey","14").put("type","track").put("title","Music");
        JSONObject repeatedSeries = new JSONObject().put("ratingKey","8").put("type","show").put("title","Library series");
        JSONObject recent = new JSONObject().put("hubIdentifier","home.movies.recent")
                .put("Metadata",new JSONArray().put(movie).put(show).put(music).put(repeatedSeries));
        JSONObject resume = new JSONObject().put("hubIdentifier","home.continue")
                .put("Metadata",new JSONArray().put(episode).put(movie));
        List<PlexClient.Film> list = PlexClient.parseHubs(new JSONObject().put("MediaContainer",
                new JSONObject().put("Hub",new JSONArray().put(recent).put(resume))));
        require(list.size()==5,"row-local duplicates or unsupported media types");
        require(list.get(0).title.equals("Library series"),"episode must display its series name");
        require(list.get(0).progress==.8f,"episode progress");
        require(list.get(1).progress==.5f,"watch progress");
        require(list.get(1).thumb.equals("/library/metadata/11/thumb/1"),"poster must come from server metadata");
        require(list.get(1).logo.equals("/library/metadata/11/clearLogo/1"),"title logo must come from real server artwork");
        require(list.get(0).section.equals(Ui.problem("Continue watching","Seguir viendo")),"continue watching must come first in the selected UI language");
        require(list.get(0).continuing&&list.get(1).continuing&&!list.get(2).continuing,"continue-watching rows must use the hub, not just an offset");
        require(list.get(2).key.equals("11")&&!list.get(2).continuing&&list.get(2).recentlyAdded,"a recently added film may also appear in resume without merging the rows");
        require(list.get(3).type.equals("show"),"new series must remain visible");
        require(list.get(3).logo.isEmpty(),"external artwork must not receive Plex credentials");
        require(PlexClient.parseHubs(new JSONObject()).isEmpty(),"empty library must stay empty");
        List<PlexClient.Section> sections=PlexClient.parseSections(new JSONObject().put("MediaContainer",new JSONObject().put("Directory",new JSONArray()
                .put(new JSONObject().put("key","1").put("type","movie").put("title","Movies"))
                .put(new JSONObject().put("key","2").put("type","show").put("title","Shows"))
                .put(new JSONObject().put("key","3").put("type","artist"))
                .put(new JSONObject().put("key","../other").put("type","movie"))
                .put(new JSONObject().put("key","1").put("type","movie")))));
        require(sections.size()==2&&sections.get(1).type.equals("show"),"all movie/show sections, without duplicates, music or unsafe paths");
        JSONArray items=new JSONArray();for(int i=0;i<PlexClient.PAGE_SIZE;i++)items.put(new JSONObject(movie.toString()).put("ratingKey",String.valueOf(100+i)));
        PlexClient.LibraryPage first=PlexClient.parsePage(page(items,0,1001),0);
        require(first.films.size()==24&&first.hasNext()&&first.nextOffset()==24,"large libraries must expose a bounded first page");
        PlexClient.LibraryPage middle=PlexClient.parsePage(page(items,24,1001),24);
        require(middle.offset==24&&middle.nextOffset()==48&&middle.hasNext(),"middle page offsets");
        PlexClient.LibraryPage last=PlexClient.parsePage(page(new JSONArray().put(show),1000,1001),1000);
        require(last.films.size()==1&&!last.hasNext(),"last page must not repeat forever");
        PlexClient.LibraryPage empty=PlexClient.parsePage(page(new JSONArray(),0,0),0);
        require(empty.films.isEmpty()&&!empty.hasNext(),"empty catalog navigation");
        PlexClient.LibraryPage shortPage=PlexClient.parsePage(page(new JSONArray().put(movie).put(movie).put(music),24,100),24);
        require(shortPage.films.size()==1&&shortPage.nextOffset()==27,"pagination must count server entries before filtering and deduplication");
        JSONObject unknown=page(items,0,1001);unknown.getJSONObject("MediaContainer").remove("totalSize");
        require(PlexClient.parsePage(unknown,0).hasNext(),"missing total must still permit a full next page");
        boolean rejected=false;try{PlexClient.parsePage(page(items,0,1001),24);}catch(Exception expected){rejected=true;}
        require(rejected,"servers ignoring the requested offset must not repeat page one");
        items.put(show);rejected=false;try{PlexClient.parsePage(page(items,0,1001),0);}catch(Exception expected){rejected=true;}
        require(rejected,"servers ignoring page size must not load the whole library");
        require(!last.films.get(0).continuing,"catalog browsing must not mark every title as continue watching");
        JSONObject home=new JSONObject().put("MediaContainer",new JSONObject().put("Hub",new JSONArray().put(recent)));
        PlexClient.mergeContinue(home,new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",new JSONArray().put(episode))));
        require(PlexClient.parseHubs(home).get(0).continuing,"dedicated continue-watching response must precede recent hubs");
        JSONObject homeHub=new JSONObject().put("MediaContainer",new JSONObject());
        PlexClient.mergeContinue(homeHub,new JSONObject().put("MediaContainer",new JSONObject().put("Hub",new JSONArray().put(resume))));
        require(PlexClient.parseHubs(homeHub).size()==2,"continue-watching hub response shape");
        JSONObject season=new JSONObject().put("ratingKey","season-1").put("type","season").put("parentRatingKey","series-1").put("parentTitle","Recent series").put("parentThumb","/series/poster").put("addedAt",300);
        JSONObject older=new JSONObject(movie.toString()).put("addedAt",100),newer=new JSONObject(show.toString()).put("addedAt",400);
        JSONObject orderedHome=new JSONObject().put("MediaContainer",new JSONObject().put("Hub",new JSONArray().put(resume).put(recent)));
        PlexClient.mergeRecent(orderedHome,new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",new JSONArray().put(older).put(season).put(newer).put(older))));
        List<PlexClient.Film> ordered=PlexClient.parseHubs(orderedHome);require(ordered.size()==5&&ordered.get(2).key.equals("13")&&ordered.get(3).key.equals("series-1")&&ordered.get(4).key.equals("11"),"recent feed must sort by addedAt, deduplicate within the row and preserve resume");
        require(ordered.get(3).thumb.equals("/series/poster")&&ordered.get(3).title.equals("Recent series")&&ordered.get(3).addedAtMs==300000,"new season must use the series title and original poster");
        PlexClient.mergeRecent(orderedHome,new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",new JSONArray())));require(PlexClient.parseHubs(orderedHome).size()==2,"an empty fresh feed must not resurrect cached recent titles");
        JSONArray tooMany=new JSONArray();for(int i=0;i<25;i++)tooMany.put(movie);rejected=false;try{PlexClient.mergeRecent(orderedHome,new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",tooMany)));}catch(Exception expected){rejected=true;}require(rejected,"unbounded recently-added response was accepted");
        JSONObject upcoming=new JSONObject(episode.toString()).put("ratingKey","102").put("viewOffset",0).put("grandparentRatingKey","8");
        JSONObject nextHome=new JSONObject().put("MediaContainer",new JSONObject().put("Hub",new JSONArray().put(resume)));
        PlexClient.mergeContinue(nextHome,new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",new JSONArray().put(upcoming))));List<PlexClient.Film> nextList=PlexClient.parseHubs(nextHome);require(nextList.size()==1&&nextList.get(0).nextEpisode&&nextList.get(0).positionMs==0,"fresh next episode must replace stale continuation for the same series");
        PlexClient.mergeContinue(nextHome,new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",new JSONArray())));require(PlexClient.parseHubs(nextHome).isEmpty(),"empty authoritative continue feed resurrected watched content");
        JSONObject finished=new JSONObject(episode.toString()).put("viewOffset",100);PlexClient.mergeContinue(nextHome,new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",new JSONArray().put(finished).put(upcoming))));require(PlexClient.parseHubs(nextHome).size()==1&&PlexClient.parseHubs(nextHome).get(0).key.equals("102"),"finished metadata hid the next pending episode");
        require(PlexClient.watchedPath("102").equals("/:/scrobble?identifier=com.plexapp.plugins.library&key=102"),"watched endpoint");for(String bad:new String[]{"", "../42", "42&key=other", "movie", "999999999999999999999"}){rejected=false;try{PlexClient.watchedPath(bad);}catch(Exception expected){rejected=true;}require(rejected,"unsafe Plex watched item accepted");}
        System.out.println("PASS Plex feed and catalog: real artwork, hub-based continue watching, sections, bounded pages, offset validation, deduplication, unknown totals and empty/last pages");
    }
    private static JSONObject page(JSONArray items,int offset,int total)throws Exception{return new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",items).put("offset",offset).put("totalSize",total));}
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
