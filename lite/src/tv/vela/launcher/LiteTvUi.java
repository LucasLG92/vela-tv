package tv.vela.launcher;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Small, local-only building blocks. Decoder surfaces and programme databases are never cached. */
final class LiteTvUi {
    static String t(String en,String es){return Ui.tr(en,en,es);}
    static GradientDrawable plate(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static void put(FrameLayout parent,View child,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(child,p);}
    static TextView text(Context c,String value,int size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);v.setTypeface(Ui.medium);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);v.setSingleLine();v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
    static TextView button(Context c,String label,Runnable action){TextView v=text(c,label,22,VelaTheme.WHITE);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(plate(VelaTheme.CARD,27));v.setOnFocusChangeListener((view,focus)->{v.setTextColor(focus?VelaTheme.INK:VelaTheme.WHITE);v.setBackground(plate(focus?VelaTheme.WHITE:VelaTheme.CARD,27));});v.setOnClickListener(view->action.run());return v;}
    static String time(long second){return new SimpleDateFormat("HH:mm",Ui.locale()).format(new Date(second*1000));}
    static LiveTvData.Programme current(Map<String,List<LiveTvData.Programme>> data,LiveTvData.Channel channel,long now){for(LiveTvData.Programme p:data.getOrDefault(channel.id,Collections.emptyList()))if(p.current(now))return p;return null;}
    static final class Progress extends View {
        private final Paint paint=new Paint(3);private int value;
        Progress(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void value(int value){this.value=Math.max(0,Math.min(1000,value));invalidate();}
        protected void onDraw(Canvas c){paint.setColor(0x30FFFFFF);c.drawRoundRect(0,0,getWidth(),getHeight(),3,3,paint);paint.setColor(VelaTheme.WHITE);c.drawRoundRect(0,0,getWidth()*value/1000f,getHeight(),3,3,paint);}
    }
    static final class Logo extends FrameLayout {
        final ImageView image;final TextView fallback;
        Logo(Context c,LiveTvData.Channel channel){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);fallback=text(c,channel.number.isEmpty()?channel.name.substring(0,1):channel.number,28,VelaTheme.SECONDARY);fallback.setGravity(Gravity.CENTER);addView(fallback,new LayoutParams(-1,-1));image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);addView(image,new LayoutParams(-1,-1));}
        void bitmap(Bitmap b){image.setImageBitmap(b);fallback.setVisibility(b==null?VISIBLE:GONE);int count=0,dark=0;if(b!=null)for(int y=0;y<b.getHeight();y+=Math.max(1,b.getHeight()/10))for(int x=0;x<b.getWidth();x+=Math.max(1,b.getWidth()/10)){int color=b.getPixel(x,y);if(Color.alpha(color)<160)continue;count++;if((.2126*Color.red(color)+.7152*Color.green(color)+.0722*Color.blue(color))/255<.25)dark++;}boolean needsContrast=count>2&&dark>count*.65;setBackground(needsContrast?plate(0xFFF0F1F6,10):null);int pad=needsContrast?5:0;image.setPadding(pad,pad,pad,pad);}
        void clear(){image.setImageDrawable(null);}
    }
    static final class Logos {
        private final Context context;
        private final Set<String> missing=java.util.concurrent.ConcurrentHashMap.newKeySet();
        final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(512*1024){protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
        Logos(Context c){context=c.getApplicationContext();}
        private volatile boolean closed;
        private InputStream open(LiveTvData.Channel channel,String asset)throws IOException {return asset.isEmpty()?context.getContentResolver().openInputStream(Uri.parse(channel.logo)):context.getAssets().open("channel-logos/"+asset+"-es.png");}
        private Bitmap decode(LiveTvData.Channel channel,String asset)throws IOException {BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=open(channel,asset)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth<1||o.outHeight<1||o.outWidth>16000||o.outHeight>16000)return null;o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>256)o.inSampleSize*=2;try(InputStream in=open(channel,asset)){return BitmapFactory.decodeStream(in,null,o);}}
        Bitmap read(LiveTvData.Channel channel){if(closed)return null;Bitmap b=cache.get(channel.id);if(b!=null||missing.contains(channel.id))return b;try{b=decode(channel,"");}catch(IOException|RuntimeException absent){}if(b==null)try{File f=new LiteXmlTvGuide(context).logoFile(channel);if(f!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);if(o.outWidth>0&&o.outHeight>0&&o.outWidth<=16000&&o.outHeight<=16000){o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>256)o.inSampleSize*=2;b=BitmapFactory.decodeFile(f.getPath(),o);}}}catch(IOException|RuntimeException absent){}if(b==null){String asset=asset(channel);if(!asset.isEmpty())try{b=decode(channel,asset);}catch(IOException|RuntimeException absent){}}if(b==null){if(!closed)missing.add(channel.id);return null;}if(Math.max(b.getWidth(),b.getHeight())>128){int edge=Math.max(b.getWidth(),b.getHeight());Bitmap scaled=Bitmap.createScaledBitmap(b,Math.max(1,128*b.getWidth()/edge),Math.max(1,128*b.getHeight()/edge),true);if(scaled!=b)b.recycle();b=scaled;}if(!closed)cache.put(channel.id,b);return b;}
        void close(){closed=true;clear();}
        private static String asset(LiveTvData.Channel c){if(c.radio)return "";String n=c.name.toLowerCase(Locale.ROOT).replaceAll("(?i)\\b(uhd|hd|sd|4k|8k)\\b","").replaceAll("[^a-z0-9]","");switch(n){
            case "la1":case "tve1":return "tve-1";case "la2":case "tve2":return "tve-2";case "antena3":return "antena-3";case "cuatro":return "cuatro";case "telecinco":return "telecinco";case "lasexta":return "lasexta";case "dmax":return "dmax";case "mega":return "mega";case "ten":return "ten";case "realmadridtv":return "real-madrid-tv";case "bemadtv":case "bemad":return "be-mad-tv";case "a3series":case "atreseries":return "atreseries";case "divinity":return "divinity";case "fdf":case "factoriadeficcion":return "fdf";case "boing":return "boing";case "energy":return "energy";case "trece":case "13tv":return "trece";case "hdcastillalamancha":case "castillalamancha":case "cmm":return "castilla-de-mancha";case "veo7":return "veo7";case "squirrel2":return "squirrel-dos";case "24h":case "24horas":return "24h";case "nova":return "nova";case "neox":return "neox";case "clan":return "clan";default:return "";}}
        void clear(){cache.evictAll();missing.clear();}
    }
    /** Stable DVB service identity survives provider row-id changes. Unique names provide a fallback. */
    static final class Favourites {
        private final SharedPreferences prefs;private Set<String> keys;
        Favourites(Context c){prefs=c.getSharedPreferences("lite_tv",0);keys=new HashSet<>(prefs.getStringSet("favourites",Collections.emptySet()));}
        boolean has(LiveTvData.Channel c,List<LiveTvData.Channel> all){if(keys.contains(c.identity))return true;if(!keys.contains(c.alias))return false;int matches=0;for(LiveTvData.Channel other:all)if(c.alias.equals(other.alias))matches++;return matches==1;}
        void toggle(LiveTvData.Channel c,List<LiveTvData.Channel> all){if(has(c,all)){keys.remove(c.identity);keys.remove(c.alias);}else{keys.add(c.identity);keys.add(c.alias);}prefs.edit().putStringSet("favourites",new HashSet<>(keys)).apply();}
    }
}
