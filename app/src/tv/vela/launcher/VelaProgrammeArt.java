package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Visible EPG images only: one disposable worker, 512 KiB cache, bounded decode and cancellation. */
final class VelaProgrammeArt implements AutoCloseable {
    private final Context context;private final View root;private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,3,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"vela-programme-art");t.setPriority(Thread.MIN_PRIORITY);return t;});
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(512*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    private final Map<ImageView,Binding> bindings=new WeakHashMap<>();private final Rect visible=new Rect();private boolean closed;
    private final ViewTreeObserver.OnScrollChangedListener scroll=this::check;private final ViewTreeObserver.OnGlobalLayoutListener layout=this::check;
    private static final class Binding {final String url;boolean attempted;Bitmap shown;Future<?> task;final CancellationSignal signal=new CancellationSignal();Binding(String u){url=u;}void cancel(){signal.cancel();if(task!=null)task.cancel(true);}}
    VelaProgrammeArt(Context c,View root){context=c.getApplicationContext();this.root=root;root.getViewTreeObserver().addOnScrollChangedListener(scroll);root.getViewTreeObserver().addOnGlobalLayoutListener(layout);}
    static String url(String value){if(value==null||value.length()>2048)return "";try{URI u=new URI(value);if(u.getRawUserInfo()!=null||u.getFragment()!=null)return "";if(("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null)return value;if("content".equals(u.getScheme())&&"android.media.tv".equals(u.getAuthority()))return value;}catch(Exception ignored){}return "";}
    void bind(ImageView view,String url){Binding old=bindings.remove(view);if(old!=null)old.cancel();String safe=url(url);if(safe.isEmpty())return;bindings.put(view,new Binding(safe));root.post(this::check);}
    private void check(){if(closed)return;for(Map.Entry<ImageView,Binding> e:new ArrayList<>(bindings.entrySet())){ImageView v=e.getKey();Binding b=e.getValue();if(v==null||!v.isAttachedToWindow()||!v.isShown()||!v.getGlobalVisibleRect(visible)){if(v!=null&&(b.shown!=null||b.task!=null&&!b.task.isDone())){b.cancel();v.setImageDrawable(null);bindings.put(v,new Binding(b.url));worker.purge();}continue;}Bitmap hit=cache.get(b.url);if(hit!=null){if(b.shown!=hit){b.shown=hit;v.setImageBitmap(hit);}continue;}if(b.attempted)continue;b.attempted=true;try{b.task=worker.submit(()->{Bitmap image=null;try{image=decode(load(b.url,b.signal));}catch(Exception ignored){}Bitmap result=image;main.post(()->{if(closed||b.signal.isCanceled()||bindings.get(v)!=b)return;if(result!=null){cache.put(b.url,result);b.shown=result;v.setImageBitmap(result);}check();});});}catch(RejectedExecutionException busy){b.attempted=false;}}}
    private byte[] load(String value,CancellationSignal signal)throws IOException{String target=value;for(int redirects=0;redirects<4;redirects++){signal.throwIfCanceled();if(target.startsWith("content:")){try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(target))){return bytes(in,signal);}}HttpURLConnection c=(HttpURLConnection)new URL(target).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Vela-Programme-Art");signal.setOnCancelListener(c::disconnect);try{int code=c.getResponseCode();if(code>=300&&code<400){String next=url(new URL(new URL(target),c.getHeaderField("Location")).toString());if(next.isEmpty()||target.startsWith("https:")&&!next.startsWith("https:"))throw new IOException("Invalid image redirect");target=next;continue;}if(code!=200||c.getContentLengthLong()>2*1024*1024)throw new IOException("Image unavailable");try(InputStream in=c.getInputStream()){return bytes(in,signal);}}finally{signal.setOnCancelListener(null);c.disconnect();}}throw new IOException("Image redirects");}
    private static byte[] bytes(InputStream in,CancellationSignal signal)throws IOException{if(in==null)throw new IOException();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];for(int n;(n=in.read(block))!=-1;){signal.throwIfCanceled();if(Thread.currentThread().isInterrupted()||out.size()+n>2*1024*1024)throw new IOException("Image limit");out.write(block,0,n);}return out.toByteArray();}
    private static Bitmap decode(byte[] data){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);if(o.outWidth<=0||o.outHeight<=0||o.outWidth>16384||o.outHeight>16384)return null;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>640||o.outHeight/o.inSampleSize>360)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeByteArray(data,0,data.length,o);}
    void clear(){for(Binding b:bindings.values())b.cancel();for(ImageView v:bindings.keySet())if(v!=null)v.setImageDrawable(null);bindings.clear();worker.purge();}
    public void close(){if(closed)return;closed=true;clear();worker.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();if(root.getViewTreeObserver().isAlive()){root.getViewTreeObserver().removeOnScrollChangedListener(scroll);root.getViewTreeObserver().removeOnGlobalLayoutListener(layout);}}
}
