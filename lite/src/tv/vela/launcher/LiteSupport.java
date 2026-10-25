package tv.vela.launcher;

import android.content.Context;
import android.graphics.Typeface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** The small contracts used by the shared HDMI resolver and vectors. No full Vela runtime. */
final class Ui {
    static Typeface regular,medium,displaySemibold;
    static synchronized void init(Context context){if(medium!=null)return;try{regular=new Typeface.Builder(context.getAssets(),"fonts/InterVariable.ttf").setFontVariationSettings("'opsz' 14, 'wght' 400").build();medium=new Typeface.Builder(context.getAssets(),"fonts/InterVariable.ttf").setFontVariationSettings("'opsz' 14, 'wght' 500").build();displaySemibold=new Typeface.Builder(context.getAssets(),"fonts/InterVariable.ttf").setFontVariationSettings("'opsz' 32, 'wght' 600").build();}catch(RuntimeException unavailable){regular=Typeface.create("sans-serif",0);medium=Typeface.create("sans-serif-medium",0);displaySemibold=Typeface.create("sans-serif",Typeface.BOLD);}}
    static Locale locale(){return Locale.getDefault();}
    static String tr(String pt,String en,String es){String language=Locale.getDefault().getLanguage();return language.equals("es")?es:language.equals("pt")?pt:en;}
}
final class Appearance {static int accent(Context c){return VelaTheme.WHITE;}static int tint(Context c,int alpha){return (alpha<<24)|0xFFFFFF;}}
final class LiveTvActivity {
    static String displayName(String name){return name;}
    static String remainingText(long minutes){String duration=minutes>=60?(minutes/60)+" h"+(minutes%60==0?"":" "+(minutes%60)+" min"):minutes+" min";return Ui.tr("Faltam ","Remaining ","Quedan ")+duration;}
}
final class TvChannelNames {static String quality(String name){return "";}}
final class LiveTvData {
    static final class Channel {
        final long providerId;final String id,inputId,number,name,logo,identity,alias;final boolean radio,hd,locked;
        Channel(long providerId,String inputId,String number,String name,boolean radio,boolean hd,boolean locked){this(providerId,inputId,number,name,radio,hd,locked,"");}
        Channel(long providerId,String inputId,String number,String name,boolean radio,boolean hd,boolean locked,String service){this.providerId=providerId;id="ant-"+providerId;this.inputId=inputId;this.number=number;this.name=name;this.radio=radio;this.hd=hd;this.locked=locked;logo=android.media.tv.TvContract.buildChannelLogoUri(providerId).toString();alias="name:"+hash((radio?"r:":"t:")+java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim());identity=service.isEmpty()?"local:"+hash(inputId+":"+providerId+":"+alias):"dvb:"+hash((radio?"r:":"t:")+service);}
        boolean antenna(){return true;}
    }
    static final class Programme {
        final long start,end;final String title,description;
        Programme(long start,long end,String title,String description){this.start=start;this.end=end;this.title=title;this.description=description;}
        boolean current(long now){return start<=now&&end>now;}
        long remainingMinutes(long now){return current(now)?1+(end-now-1)/60:0;}
        int progress(long now){return end<=start?0:(int)Math.max(0,Math.min(1000,1000d*(now-start)/(end-start)));}
    }
    static String hash(String value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
