package tv.vela.launcher;

import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Installation-local UI language. Only the selected small catalogue is retained in RAM. */
final class L10n {
    static final String[] CODES={"auto","en","es","pt"};
    static final String[] NAMES={"","English","Español","Português"};
    private static volatile String language=normalize(Locale.getDefault().getLanguage());
    private static volatile String choice="auto";
    private static volatile JSONObject messages=new JSONObject();
    private static String loaded="";
    private L10n(){}
    static String normalize(String value){String v=value.toLowerCase(Locale.ROOT).replace('_','-').split("-",2)[0];return Arrays.asList(CODES).contains(v)&&!v.equals("auto")?v:"en";}
    static synchronized void init(Context context,String buildDefault){
        choice=context.getSharedPreferences("vela-language",0).getString("language","auto");
        // Home and the screensaver can live in separate Android processes. A tiny atomic
        // record avoids stale cross-process SharedPreferences, without a watcher or service.
        android.util.AtomicFile record=new android.util.AtomicFile(new File(context.getFilesDir(),"ui-language"));
        try(InputStream in=record.openRead()){byte[] value=new byte[32];int n=in.read(value);if(n>0&&in.read()==-1){String saved=new String(value,0,n,StandardCharsets.UTF_8).trim();if(Arrays.asList(CODES).contains(saved))choice=saved;}}catch(IOException absent){}
        String selected=choice.equals("auto")?buildDefault:choice;
        language=normalize(selected.equals("auto")?Locale.getDefault().getLanguage():selected);
        if(loaded.equals(language))return;
        JSONObject next=new JSONObject();
        if(!language.equals("en"))try(InputStream in=context.getAssets().open("i18n/"+language+".json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];for(int n;(n=in.read(buffer))!=-1;){if(out.size()+n>512*1024)throw new IOException("Language catalogue limit");out.write(buffer,0,n);}next=new JSONObject(out.toString("UTF-8"));
        }catch(IOException|JSONException unavailable){/* English is the deterministic fallback, never provider content. */}
        messages=next;loaded=language;
    }
    static String language(){return language;}
    static String choice(){return choice;}
    static Locale locale(){return Locale.forLanguageTag(language.equals("pt")?"pt-BR":language.equals("es")?"es-ES":"en-US");}
    static String text(String english){return language.equals("en")?english:messages.optString(english,english);}
    static String tr(String portuguese,String english,String spanish){if(language.equals("en"))return english;return messages.optString(english,language.equals("es")?spanish:language.equals("pt")?portuguese:english);}
    static String name(){for(int i=1;i<CODES.length;i++)if(CODES[i].equals(choice))return NAMES[i];return text("System language");}
    static boolean choose(Context context,String code){if(!Arrays.asList(CODES).contains(code))throw new IllegalArgumentException("Unsupported UI language");android.util.AtomicFile record=new android.util.AtomicFile(new File(context.getFilesDir(),"ui-language"));FileOutputStream out=null;try{out=record.startWrite();out.write(code.getBytes(StandardCharsets.UTF_8));record.finishWrite(out);}catch(IOException failed){if(out!=null)record.failWrite(out);return false;}context.getSharedPreferences("vela-language",0).edit().putString("language",code).commit();init(context,"auto");return true;}
    static JSONObject mobile(){JSONObject out=new JSONObject();for(String key:MOBILE)try{out.put(key,text(key));}catch(JSONException impossible){throw new AssertionError(impossible);}return out;}
    private static final String[] MOBILE={"Your television, your way. Temporary encrypted connection on your network.","Home","Folders","Folder name","Tap an app to add or remove it from the named folder. Arrows reorder your app drawer.","Previous","Next","Channels","Tap to add or remove a favourite.","Appearance","Text size","Save text size","Connect a source","Server or playlist address","Username","Password","Connect on this TV","Credentials stay on this television. They are excluded from backups and sync.","Saving…","Saved. Return to Vela to see your changes.","The session has ended. Open a new session in Vela.","Could not connect to the television.","Unpin","Pin","Sidebar ↑","Sidebar ↓"};
}
