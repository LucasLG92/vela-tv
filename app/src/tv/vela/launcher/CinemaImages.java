package tv.vela.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Coalesced, cancellable requests with bounded memory, disk and work queues. */
final class CinemaImages {
    interface Ready { void accept(Bitmap image); }
    private interface Download { byte[] get() throws Exception; }
    final class Request {
        private Ready ready;
        private Request(Ready ready) { this.ready=ready; }
        void cancel() { synchronized(pending) { ready=null; } }
        boolean pending() { synchronized(pending) { return ready!=null; } }
    }
    private final LibraryClient plex;
    private final Context context;
    private final File folder;
    private final ThreadPoolExecutor io=Visuals.worker(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Work> pending=new HashMap<>();
    private final LruCache<String,Bitmap> images;
    private int generation;
    private boolean closed;

    CinemaImages(Context c,PlexClient plex){this(c,new LibraryClient(c,plex));}
    CinemaImages(Context c,LibraryClient plex) {
        this.plex=plex;context=c.getApplicationContext();
        folder=new File(c.getCacheDir(),"plex-art");folder.mkdirs();
        images=new LruCache<String,Bitmap>(Visuals.cacheKb(c)) {
            @Override protected int sizeOf(String key,Bitmap b) { return Math.max(1,(b.getAllocationByteCount()+1023)/1024); }
        };
    }
    Request load(String path,int width,int height,Ready ready) {
        if(path==null||path.isEmpty())return new Request(null);
        return enqueue(plex.imageIdentity(path)+path+":"+width+"x"+height,
            width,height,()->plex.image(path,width,height),ready);
    }
    Request loadLogo(String path,Ready ready) {
        if(path==null||path.isEmpty())return new Request(null);
        return enqueue(plex.imageIdentity(path)+path+":logo:650x182",650,182,
            ()->plex.image(path,650,182,true),ready);
    }
    Request loadAvatar(Ready ready) {
        String key=plex.profileKey();if(key.isEmpty())return new Request(null);
        return enqueue("library-avatar:"+key,96,96,()->plex.avatar(),ready);
    }
    Request loadTv(String path,Ready ready) {
        return loadTv(path,352,198,ready);
    }
    Request loadTv(String path,int width,int height,Ready ready) {
        if(path==null||path.isEmpty())return new Request(null);
        return enqueue("tv:"+path+":"+width+"x"+height,width,height,()->{
            android.net.Uri uri=android.net.Uri.parse(path);
            InputStream input;java.net.HttpURLConnection connection=null;
            if("content".equals(uri.getScheme()))input=context.getContentResolver().openInputStream(uri);
            else {
                String host=uri.getHost();
                if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||
                    (uri.getPort()!=-1&&uri.getPort()!=443))
                    throw new IOException("Origen de imagen no admitido.");
                connection=(java.net.HttpURLConnection)new java.net.URL(path).openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
                input=connection.getInputStream();
            }
            try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                if(in==null)throw new IOException("Imagen no disponible.");
                byte[] buffer=new byte[8192];int size;
                while((size=in.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    if(out.size()+size>4*1024*1024)throw new IOException("Imagen demasiado grande.");
                    out.write(buffer,0,size);
                }
                return out.toByteArray();
            } finally { if(connection!=null)connection.disconnect(); }
        },ready);
    }
    private Request enqueue(String key,int width,int height,Download download,Ready ready) {
        Request ticket=new Request(ready);
        synchronized(pending) {
            if(closed)return new Request(null);
            Bitmap cached=images.get(key);
            if(cached!=null){ready.accept(cached);ticket.ready=null;return ticket;}
            Work work=pending.get(key);
            if(work!=null){work.requests.add(ticket);return ticket;}
            work=new Work(key,width,height,download,generation);work.requests.add(ticket);pending.put(key,work);
            try { work.future=io.submit(work); }
            catch(RejectedExecutionException ignored){pending.remove(key);ticket.ready=null;}
        }
        return ticket;
    }
    private final class Work implements Runnable {
        final String key;final int width,height,epoch;final Download download;
        final List<Request> requests=new ArrayList<>();Future<?> future;
        Work(String key,int w,int h,Download download,int epoch){this.key=key;width=w;height=h;this.download=download;this.epoch=epoch;}
        private boolean wanted() {
            synchronized(pending) {
                if(closed||epoch!=generation||Thread.currentThread().isInterrupted())return false;
                for(Request r:requests)if(r.ready!=null)return true;
                return false;
            }
        }
        @Override public void run() {
            boolean delivering=false;
            try {
                if(!wanted())return;
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));
                char[] hex="0123456789abcdef".toCharArray(),name=new char[hash.length*2];
                for(int i=0;i<hash.length;i++){name[i*2]=hex[(hash[i]&255)>>>4];name[i*2+1]=hex[hash[i]&15];}
                File file=new File(folder,new String(name)+".img");
                if(!file.exists()||System.currentTimeMillis()-file.lastModified()>86400_000L) {
                    byte[] data=download.get();if(!wanted())return;
                    File temporary=new File(folder,new String(name)+"."+epoch+".part");
                    try(FileOutputStream out=new FileOutputStream(temporary)){out.write(data);}
                    if(file.exists())file.delete();
                    if(!temporary.renameTo(file)){temporary.delete();return;}
                    prune();
                }
                if(!wanted())return;
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(file.getPath(),bounds);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(bounds.outWidth/(options.inSampleSize*2)>=width&&bounds.outHeight/(options.inSampleSize*2)>=height)options.inSampleSize*=2;
                // Immutable pixels stay on the GPU; logos retain their original alpha channel.
                options.inPreferredConfig=Bitmap.Config.HARDWARE;
                Bitmap bitmap;
                if(key.startsWith("library-avatar:"))bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),(decoder,info,source)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,96f/Math.max(w,h));
                    decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE);
                });
                else bitmap=BitmapFactory.decodeFile(file.getPath(),options);
                if(bitmap==null)return;
                delivering=true;
                main.post(()->{
                    synchronized(pending) {
                        try {
                            if(!wanted())return;
                            if(bitmap.getAllocationByteCount()/1024<=images.maxSize()/2)images.put(key,bitmap);
                            for(Request r:requests)if(r.ready!=null){Ready callback=r.ready;r.ready=null;callback.accept(bitmap);}
                        } finally { if(pending.get(key)==this)pending.remove(key); }
                    }
                });
            } catch(Exception ignored) { /* A missing image never blocks navigation. */ }
            finally { if(!delivering)synchronized(pending){for(Request r:requests)r.ready=null;if(pending.get(key)==this)pending.remove(key);} }
        }
    }
    private synchronized void prune() {
        File[] files=folder.listFiles((dir,name)->name.endsWith(".img"));if(files==null)return;
        long bytes=0;for(File f:files)bytes+=f.length();
        if(files.length<=48&&bytes<=24*1024*1024)return;
        Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));int remaining=files.length;
        for(File f:files){if(remaining<=48&&bytes<=24*1024*1024)break;long size=f.length();if(f.delete()){remaining--;bytes-=size;}}
    }
    void trim(){images.evictAll();}
    static void purgeDisk(Context c){
        File folder=new File(c.getCacheDir(),"plex-art");File[] files=folder.listFiles();if(files==null)return;
        for(File file:files)if(file.isFile()&&(file.getName().endsWith(".img")||file.getName().endsWith(".part")))file.delete();
    }
    void clear() {
        synchronized(pending) {
            generation++;
            for(Work work:pending.values()){for(Request r:work.requests)r.ready=null;if(work.future!=null)work.future.cancel(true);}
            pending.clear();io.getQueue().clear();main.removeCallbacksAndMessages(null);images.evictAll();images.resize(Visuals.cacheKb(context));
        }
    }
    void close(){synchronized(pending){closed=true;clear();io.shutdownNow();}}
}
