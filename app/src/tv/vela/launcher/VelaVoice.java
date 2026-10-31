package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import java.util.*;
import java.util.regex.*;

/** Voice-command parser and legacy external-intent compatibility. Integrated capture uses VelaVoiceSearch. */
final class VelaVoice {
    static final int REQUEST=8340,ANY=0,APP=1,CHANNEL=2,INPUT=3,MEDIA=4,DISCOVERY=5;
    private static final Pattern VERB=Pattern.compile("(?iu)^(busca(?:r)?|search(?: for)?|find|cherche|suche|cerca|pesquisa|procura|pon(?:me)?|abre|abrir|reproduce|quiero ver|quiero escuchar|cambia a|play|open|watch|switch to|mets|ouvre|regarde|öffne|spiele|apri|riproduci|metti|reproduz|coloca)\\s+(.+)$");
    private static final Pattern SUBJECT=Pattern.compile("(?iu)^(?:(?:el|la|los|las|the|a|o|le|les|der|die|das|il|lo)\\s+)?(aplicaci[oó]n|app(?:lication)?|canal|channel|cha[iî]ne|sender|canale|entrada|input|entr[eé]e|eingang|ingresso|pel[ií]cula|movie|film|filme|serie|series)\\s+(.+)$");
    static final class Request {
        final String spoken,query;final int target,genre;final boolean open,tonight;
        Request(String s,String q,int t,boolean o,int g,boolean n){spoken=s;query=q;target=t;open=o;genre=g;tonight=n;}
    }
    static boolean key(int code){return code==android.view.KeyEvent.KEYCODE_SEARCH;}
    static Intent recognizer(Activity a){
        // The speech service owns its spoken language, independently of Vela's menu language.
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,a.getPackageName())
            .putExtra(RecognizerIntent.EXTRA_PROMPT,L10n.text("Search or say a channel, app or HDMI input"));
    }
    static boolean start(Activity a){try{a.startActivityForResult(recognizer(a),REQUEST);return true;}catch(ActivityNotFoundException|SecurityException unavailable){return false;}}
    static String result(int result,Intent data){
        if(result!=Activity.RESULT_OK||data==null)return "";
        try{ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);for(int i=0;words!=null&&i<Math.min(3,words.size());i++){String text=VelaWorkspace.clean(words.get(i),80);if(!text.isEmpty())return text;}}catch(RuntimeException invalid){}
        return "";
    }
    static Request parse(String text){
        String spoken=VelaWorkspace.clean(text,80),q=spoken.replaceAll("[.!?。！？]+$","").trim();
        q=q.replaceFirst("(?iu)^(?:por favor[, ]+|please[, ]+|s['’]il vous pla[iî]t[, ]+|bitte[, ]+|per favore[, ]+)","")
            .replaceFirst("(?iu)[, ]+(?:por favor|please|s['’]il vous pla[iî]t|bitte|per favore)$","").trim();
        boolean open=false;int target=ANY;Matcher verb=VERB.matcher(q);
        if(verb.matches()){open=!verb.group(1).matches("(?iu)busca(?:r)?|search(?: for)?|find|cherche|suche|cerca|pesquisa|procura");q=verb.group(2).trim();Matcher noun=SUBJECT.matcher(q);if(noun.matches()){String kind=VelaWorkspace.normal(noun.group(1));target=kind.matches("aplicacion|app|application")?APP:kind.matches("canal|channel|chaine|sender|canale")?CHANNEL:kind.matches("entrada|input|entree|eingang|ingresso")?INPUT:MEDIA;q=noun.group(2).trim();}}
        int genre=genre(q);boolean night=tonight(q);String normalized=VelaWorkspace.normal(q);
        if(genre>0&&!open||normalized.matches("que (?:hay|ver) esta noche|what(?: is|'s) on tonight|ce soir|heute abend|stasera|esta noite"))return new Request(spoken,q,DISCOVERY,false,genre,night);
        if(target==INPUT||q.matches("(?iu)^hdmi\\s*.*")){target=INPUT;q=inputQuery(q);}
        else if(target==CHANNEL||target==ANY&&open)q=channelQuery(q);
        if(target==APP||target==ANY)if(keyText(q).equals("youtube"))q="YouTube";
        if(target==ANY){String key=appKey(q);if(key.matches("youtube|plex|jellyfin|primevideo|netflix|disneyplus|spotify|kodi"))target=APP;else if(open&&keyText(q).matches("la1|la2|antena3|telecinco|lasexta|cuatro"))target=CHANNEL;}
        return new Request(spoken,q,target,open,0,false);
    }
    static String query(String text){return parse(text).query;}
    static String keyText(String text){return VelaWorkspace.normal(text).replaceAll("[^\\p{L}\\p{N}]+","");}
    static String appKey(String text){String key=keyText(text);return key.equals("amazonprimevideo")?"primevideo":key.equals("disney")?"disneyplus":key;}
    static String channelQuery(String text){String key=keyText(text);if(key.equals("launo"))return "La 1";if(key.equals("lados"))return "La 2";if(key.equals("antenatres"))return "Antena 3";if(key.equals("telecinco"))return "Telecinco";if(key.equals("lasexta"))return "La Sexta";return text;}
    static String inputQuery(String text){String q=VelaWorkspace.normal(text).replaceFirst("^hdmi\\s*","");String[][] names={{"uno","un","one","um","eins","ein","une"},{"dos","two","deux","zwei","due","dois"},{"tres","three","trois","drei","tre"},{"cuatro","four","quatre","vier","quattro","quatro"}};for(int i=0;i<names.length;i++)for(String name:names[i])if(q.equals(name))return "HDMI "+(i+1);return text.matches("(?iu)^hdmi\\s*[1-9]$")?"HDMI "+q:text;}
    static boolean exact(Request r,VelaHub.Hit hit){
        Object value=hit.value;if(value==null||value instanceof VelaShows.Airing)return false;
        boolean app=value instanceof Intent,input=value instanceof TvInputs.Entry,channel=value instanceof LiveTvData.Channel;
        if(input&&((TvInputs.Entry)value).state!=TvInputs.CONNECTED)return false;
        if(r.target==APP&&!app||r.target==INPUT&&!input||r.target==CHANNEL&&!channel||r.target==MEDIA&&(app||input||channel))return false;
        if(r.target==ANY&&!app&&!input&&!channel)return false;
        String q=keyText(r.query);if(q.equals(keyText(hit.title)))return true;
        if(app)return appKey(r.query).equals(appKey(hit.title));
        if(input)return ((TvInputs.Entry)value).state==TvInputs.CONNECTED&&q.equals(keyText(((TvInputs.Entry)value).port));
        if(channel){LiveTvData.Channel ch=(LiveTvData.Channel)value;return TvChannelNames.match(ch.name).equals(TvChannelNames.match(r.query))||!ch.number.isEmpty()&&q.equals(keyText(ch.number));}
        return false;
    }
    static int genre(String text){String q=VelaWorkspace.normal(text).replaceFirst(" (?:tonight|esta noche|ce soir|heute abend|stasera|esta noite|ahora|now|maintenant|jetzt|ora|agora)$","");String[][] words={{},{"movies","peliculas","filmes","films","filme","film"},{"sports","deportes","desporto","sport","sportivi"},{"documentaries","documentales","documentarios","documentaires","dokumentationen","documentari"},{"kids","infantiles","infantis","enfants","kinder","bambini"}};for(int i=1;i<words.length;i++)for(String w:words[i])if(q.equals(w))return i;return 0;}
    static boolean searches(String text){Matcher m=VERB.matcher(VelaWorkspace.clean(text,80));return m.matches()&&m.group(1).matches("(?iu)busca(?:r)?|search(?: for)?|find|cherche|suche|cerca|pesquisa|procura");}
    static boolean tonight(String text){return VelaWorkspace.normal(text).matches(".*(?:tonight|esta noche|ce soir|heute abend|stasera|esta noite)$");}
    /** One Activity-owned request, delivered only after foreground restoration. */
    static final class Session {
        private boolean listening;private String profile="",pending="",pendingProfile="";private boolean searchOnly;
        boolean start(Activity activity){if(listening)return true;profile=VelaWorkspace.active(activity);listening=true;if(VelaVoice.start(activity))return true;listening=false;profile="";return false;}
        void result(Context c,int code,Intent data){String text=VelaVoice.result(code,data);listening=false;if(!profile.isEmpty()&&profile.equals(VelaWorkspace.active(c))&&!text.isEmpty()){pending=text;pendingProfile=profile;searchOnly=false;}profile="";}
        boolean intent(Context c,Intent intent){if(intent==null||!Intent.ACTION_SEARCH.equals(intent.getAction()))return false;listening=false;profile="";String text="";try{text=VelaWorkspace.clean(intent.getStringExtra(android.app.SearchManager.QUERY),80);}catch(RuntimeException invalid){}if(!text.isEmpty()){pending=text;pendingProfile=VelaWorkspace.active(c);searchOnly=true;}return true;}
        Request take(Context c){String text=pending,owner=pendingProfile;boolean search=searchOnly;pending="";pendingProfile="";searchOnly=false;if(text.isEmpty()||!owner.equals(VelaWorkspace.active(c)))return null;return search?new Request(text,text,ANY,false,0,false):parse(text);}
        void save(Bundle state){state.putBoolean("vela-voice-listening",listening);state.putString("vela-voice-profile",profile);state.putString("vela-voice-pending",pending);state.putString("vela-voice-owner",pendingProfile);state.putBoolean("vela-voice-search-only",searchOnly);}
        void restore(Bundle state){if(state==null)return;listening=state.getBoolean("vela-voice-listening");profile=VelaWorkspace.clean(state.getString("vela-voice-profile",""),32);pending=VelaWorkspace.clean(state.getString("vela-voice-pending",""),80);pendingProfile=VelaWorkspace.clean(state.getString("vela-voice-owner",""),32);searchOnly=state.getBoolean("vela-voice-search-only");}
    }
}
