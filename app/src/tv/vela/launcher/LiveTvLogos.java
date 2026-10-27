package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;

/** At most one page of decoded logos; no threads or images retained while hidden. */
final class LiveTvLogos {
    /** One layer: transparent logos replace the initials instead of revealing letters underneath. */
    static final class LogoView extends ImageView {
        private final Paint initials=new Paint(Paint.ANTI_ALIAS_FLAG);private String label;
        private boolean plated,lightPlate;
        void label(String name){label=name.substring(0,Math.min(2,name.length())).toUpperCase(Ui.locale());invalidate();}
        void plated(){plated=true;skinPlate();}
        private void skinPlate(){if(!plated)return;int side=Math.min(getWidth(),getHeight()),pad=Math.max(6,Math.round(side*.12f));setPadding(pad,pad,pad,pad);setBackground(VelaTheme.shape(lightPlate?0xFFF0F1F6:VelaTheme.CARD,Math.max(12,Math.round(side*.22f))));}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);skinPlate();}
        interface ColorListener{void color(int value);}private ColorListener listener;
        void setColorListener(ColorListener value){listener=value;}
        @Override public void setImageBitmap(Bitmap bitmap){super.setImageBitmap(bitmap);if(plated){int count=0,dark=0;if(bitmap!=null)for(int y=0;y<bitmap.getHeight();y+=Math.max(1,bitmap.getHeight()/8))for(int x=0;x<bitmap.getWidth();x+=Math.max(1,bitmap.getWidth()/8)){int color=bitmap.getPixel(x,y);if(Color.alpha(color)<160)continue;count++;if((Color.red(color)*.2126+Color.green(color)*.7152+Color.blue(color)*.0722)<64)dark++;}lightPlate=count>2&&dark>count*.65;skinPlate();}if(listener!=null&&bitmap!=null){float[] hsv=new float[3];int chosen=0xFF243449;float best=.1f;for(int y=0;y<bitmap.getHeight();y+=Math.max(1,bitmap.getHeight()/8))for(int x=0;x<bitmap.getWidth();x+=Math.max(1,bitmap.getWidth()/8)){int color=bitmap.getPixel(x,y);if(Color.alpha(color)<160)continue;Color.colorToHSV(color,hsv);float score=hsv[1]*hsv[2];if(score>best){best=score;chosen=color;}}listener.color(chosen);}}
        LogoView(Context c,String name){super(c);label=name.substring(0,Math.min(2,name.length())).toUpperCase(Ui.locale());initials.setColor(VelaTheme.SECONDARY);initials.setTextSize(33);initials.setTypeface(Ui.semibold);initials.setTextAlign(Paint.Align.CENTER);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(getDrawable()==null){initials.setTextSize(Math.max(18,Math.min(getWidth(),getHeight())*.32f));canvas.drawText(label,getWidth()/2f,getHeight()/2f-(initials.ascent()+initials.descent())/2f,initials);}}
    }
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor io=Visuals.worker(2);private final List<Future<?>> jobs=new ArrayList<>();
    private final Map<ImageView,String> bindings=new WeakHashMap<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(768){@Override protected int sizeOf(String key,Bitmap b){return Math.max(1,b.getAllocationByteCount()/1024);}};
    private int epoch;private boolean closed;
    LiveTvLogos(Context c){context=c.getApplicationContext();}
    void load(String url,ImageView view){load(url,view,false,false,null);}
    void load(LiveTvData.Channel channel,ImageView view){if(view instanceof LogoView)((LogoView)view).label(channel.antenna()&&!channel.number.isEmpty()?channel.number:TvChannelNames.display(channel.name));load(channel.logo,view,true,channel.antenna(),channel);}
    private void load(String url,ImageView view,boolean privateLogo,boolean local,LiveTvData.Channel channel){
        if(closed)return;String mapping=channel==null?"":VelaGuideFix.alias(context,channel.name);String key=channel==null?url:channel.id+":"+url+":"+mapping;String previous=bindings.put(view,key);if(!key.equals(previous))view.setImageDrawable(null);Bitmap ready=cache.get(key);if(ready!=null){view.setImageBitmap(ready);return;}final int generation=epoch;
        jobs.removeIf(Future::isDone);
        try{jobs.add(io.submit(()->{try{Bitmap decoded=null;if(channel!=null&&!mapping.isEmpty()){String corrected=new XmlTvGuide(context).logo(channel,mapping);decoded=image(corrected,true,false);}if(decoded==null&&!Thread.currentThread().isInterrupted())decoded=image(url,privateLogo,local);if(decoded==null&&channel!=null&&mapping.isEmpty()&&!Thread.currentThread().isInterrupted()){String fallback=new XmlTvGuide(context).logo(channel,mapping);if(!fallback.equals(url))decoded=image(fallback,true,false);}if(decoded==null)return;Bitmap bitmap=decoded;main.post(()->{if(!closed&&generation==epoch){cache.put(key,bitmap);if(key.equals(bindings.get(view)))view.setImageBitmap(bitmap);}else bitmap.recycle();});prune(context);}catch(Exception ignored){/* The name remains visible when no original logo is available. */}}));}catch(RejectedExecutionException ignored){}
    }
    private Bitmap image(String url,boolean privateLogo,boolean local){try{
        if(url.isEmpty()||Thread.currentThread().isInterrupted()||!(local?AntennaClient.localLogo(url):LiveTvData.https(url)||privateLogo&&url.startsWith("http://")))return null;
        File file=LiveTvData.cached(context,"logo-"+LiveTvData.hash(url)+".img");
        if(!LiveTvData.fresh(file,7*86400_000L)){if(local){try(InputStream in=context.getContentResolver().openInputStream(android.net.Uri.parse(url));OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("Logo unavailable");LiveTvData.copy(in,out,512*1024);}catch(Exception unavailable){file.delete();throw unavailable;}}else if(privateLogo)XtreamClient.logo(url,file);else LiveTvData.download(url,file,512*1024);}
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);if(options.outWidth<=0||options.outHeight<=0||options.outWidth>8192||options.outHeight>8192)return null;
        options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>256)options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap decoded=BitmapFactory.decodeFile(file.getPath(),options);if(decoded==null)return null;
        float scale=Math.min(1,128f/Math.max(decoded.getWidth(),decoded.getHeight()));Bitmap bitmap=scale<1?Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true):decoded;if(bitmap!=decoded)decoded.recycle();return bitmap;
    }catch(Exception unavailable){return null;}}
    void page(){epoch++;for(Future<?> job:jobs)job.cancel(true);jobs.clear();bindings.clear();io.purge();}
    void close(){closed=true;page();io.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
    static synchronized void prune(Context c){File folder=LiveTvData.cached(c,"epg.json").getParentFile();File[] parts=folder.listFiles((dir,name)->name.startsWith("fetch-")&&name.endsWith(".part"));if(parts!=null)for(File part:parts)if(System.currentTimeMillis()-part.lastModified()>3600_000)part.delete();File[] files=folder.listFiles((dir,name)->name.startsWith("logo-")&&name.endsWith(".img"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long bytes=0;for(int i=0;i<files.length;i++){bytes+=files[i].length();if(i>=64||bytes>4*1024*1024)files[i].delete();}}
}
