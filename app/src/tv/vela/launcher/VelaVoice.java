package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.speech.RecognizerIntent;
import java.util.*;

/** Starts Android's recognizer only on request. No microphone permission or resident recognizer. */
final class VelaVoice {
    static final int REQUEST=8340;
    static boolean key(int code){return code==android.view.KeyEvent.KEYCODE_SEARCH||code==android.view.KeyEvent.KEYCODE_VOICE_ASSIST||code==android.view.KeyEvent.KEYCODE_ASSIST;}
    static boolean start(Activity a){try{a.startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,Ui.locale().toLanguageTag()).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1).putExtra(RecognizerIntent.EXTRA_PROMPT,L10n.text("Search or say a channel, app or HDMI input")),REQUEST);return true;}catch(ActivityNotFoundException|SecurityException unavailable){return false;}}
    static String result(int result,Intent data){if(result!=Activity.RESULT_OK||data==null)return "";ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);return words==null||words.isEmpty()?"":VelaWorkspace.clean(words.get(0),80);}
    static String query(String text){return text.replaceFirst("(?iu)^(?:pon|abre|abrir|busca|buscar|play|open|find|search(?: for)?|mets|ouvre|cherche|öffne|suche|apri|cerca|abrir|procura|pesquisa)\\s+","").trim();}
    static int genre(String text){String q=VelaWorkspace.normal(text).replaceFirst(" (?:tonight|esta noche|ce soir|heute abend|stasera|esta noite)$","");String[][] words={{},{"movies","peliculas","filmes","films","filme","film"},{"sports","deportes","desporto","sport","sportivi"},{"documentaries","documentales","documentarios","documentaires","dokumentationen","documentari"},{"kids","infantiles","infantis","enfants","kinder","bambini"}};for(int i=1;i<words.length;i++)for(String w:words[i])if(q.equals(w))return i;return 0;}
    static boolean searches(String text){return text.matches("(?iu)^(?:busca|buscar|search|find|cherche|suche|cerca|pesquisa|procura) .*");}
    static boolean tonight(String text){return VelaWorkspace.normal(text).matches(".*(?:tonight|esta noche|ce soir|heute abend|stasera|esta noite)$");}
}
