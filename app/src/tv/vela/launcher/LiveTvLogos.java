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
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor io=Visuals.worker(2);private final List<Future<?>> jobs=new ArrayList<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(768){@Override protected int sizeOf(String key,Bitmap b){return Math.max(1,b.getAllocationByteCount()/1024);}};
    private int epoch;private boolean closed;
    LiveTvLogos(Context c){context=c.getApplicationContext();}
    void load(String url,ImageView view){load(url,view,false);}
    void load(LiveTvData.Channel channel,ImageView view){load(channel.logo,view,!channel.xtreamId.isEmpty());}
    private void load(String url,ImageView view,boolean privateLogo){
        if(closed||url.isEmpty()||!(LiveTvData.https(url)||privateLogo&&url.startsWith("http://")))return;Bitmap ready=cache.get(url);if(ready!=null){view.setImageBitmap(ready);return;}final int generation=epoch;
        try{jobs.add(io.submit(()->{try{
            File file=LiveTvData.cached(context,"logo-"+LiveTvData.hash(url)+".img");
            if(!LiveTvData.fresh(file,7*86400_000L)){if(privateLogo)XtreamClient.logo(url,file);else LiveTvData.download(url,file,512*1024);}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
            if(options.outWidth<=0||options.outHeight<=0||options.outWidth>8192||options.outHeight>8192)return;
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>256)options.inSampleSize*=2;options.inJustDecodeBounds=false;
            Bitmap decoded=BitmapFactory.decodeFile(file.getPath(),options);if(decoded==null)return;
            float scale=Math.min(1,128f/Math.max(decoded.getWidth(),decoded.getHeight()));Bitmap bitmap=scale<1?Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true):decoded;if(bitmap!=decoded)decoded.recycle();
            main.post(()->{if(!closed&&generation==epoch){cache.put(url,bitmap);view.setImageBitmap(bitmap);}else bitmap.recycle();});
            prune(context);
        }catch(Exception ignored){/* The real channel name remains readable when its logo is unavailable. */}}));}catch(RejectedExecutionException ignored){}
    }
    void page(){epoch++;for(Future<?> job:jobs)job.cancel(true);jobs.clear();io.purge();}
    void close(){closed=true;page();io.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
    static synchronized void prune(Context c){File folder=LiveTvData.cached(c,"epg.json").getParentFile();File[] parts=folder.listFiles((dir,name)->name.startsWith("fetch-")&&name.endsWith(".part"));if(parts!=null)for(File part:parts)if(System.currentTimeMillis()-part.lastModified()>3600_000)part.delete();File[] files=folder.listFiles((dir,name)->name.startsWith("logo-")&&name.endsWith(".img"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long bytes=0;for(int i=0;i<files.length;i++){bytes+=files[i].length();if(i>=64||bytes>4*1024*1024)files[i].delete();}}
}
