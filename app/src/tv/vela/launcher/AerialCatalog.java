package tv.vela.launcher;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** A small list of author-published streams; the app ships no video files. */
final class AerialCatalog {
    static final class Scene {final String title,url,credit;Scene(String title,String url,String credit){this.title=title;this.url=url;this.credit=credit;}}
    static List<Scene> read(Context c,String theme)throws Exception{
        byte[] bytes;try(InputStream in=c.getAssets().open("ambient/aerials.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);bytes=out.toByteArray();
        }
        JSONArray array=new JSONArray(new String(bytes,"UTF-8"));List<Scene> result=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            JSONObject item=array.getJSONObject(i);if(!"all".equals(theme)&&!theme.equals(item.optString("theme")))continue;
            String url=item.getString("url");
            if(!url.startsWith("https://github.com/glouel/AerialCommunity/releases/download/"))continue;
            result.add(new Scene(item.getString("title"),url,item.optString("credit")));
        }
        return result;
    }
}
