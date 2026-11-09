package tv.vela.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.*;
import java.util.concurrent.*;

/** Coalesced, cancellable requests with bounded memory, disk and work queues. */
final class CinemaImages {
    interface Ready { void accept(Bitmap image); }
    private interface Download { byte[] get() throws Exception; }
    final class Request {
        private Ready ready;
        private Work owner;
        private Request(Ready ready) { this.ready=ready; }
        void cancel() {
            synchronized(pending) {
                ready=null;
                Work work=owner;owner=null;
                if(work==null)return;
                work.requests.remove(this);
                if(!work.requests.isEmpty())return;
                if(pending.get(work.key)==work)pending.remove(work.key);
                if(work.future!=null){work.future.cancel(true);if(work.future instanceof Runnable)io.remove((Runnable)work.future);}
            }
        }
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
            if(work!=null){ticket.owner=work;work.requests.add(ticket);return ticket;}
            work=new Work(key,width,height,download,generation);ticket.owner=work;work.requests.add(ticket);pending.put(key,work);
            try { work.future=io.submit(work); }
            catch(RejectedExecutionException ignored){pending.remove(key);ticket.ready=null;ticket.owner=null;work.requests.clear();}
        }
        return ticket;
    }
    private final class Work implements Runnable {
        final String key;final int width,height,epoch;final Download download;
        final List<Request> requests=new ArrayList<>();Future<?> future;
        Work(String key,int w,int h,Download download,int epoch){this.key=key;width=w;height=h;this.download=download;this.epoch=epoch;}
        private boolean wanted() {
            synchronized(pending) {
                if(closed||epoch!=generation||pending.get(key)!=this||Thread.currentThread().isInterrupted())return false;
                for(Request r:requests)if(r.ready!=null)return true;
                return false;
            }
        }
        @Override public void run() {
            boolean delivering=false;
            Bitmap decoded=null;
            File temporary=null;
            try {
                if(!wanted())return;
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));
                char[] hex="0123456789abcdef".toCharArray(),name=new char[hash.length*2];
                for(int i=0;i<hash.length;i++){name[i*2]=hex[(hash[i]&255)>>>4];name[i*2+1]=hex[hash[i]&15];}
                File file=new File(folder,new String(name)+".img");
                if(file.exists()&&System.currentTimeMillis()-file.lastModified()<=86400_000L) {
                    try{decoded=decode(file,width,height);}catch(IOException|IllegalArgumentException badCache){file.delete();}
                }
                if(decoded==null) {
                    if(!wanted())return;
                    temporary=File.createTempFile(new String(name)+"-",".part",folder);
                    try(FileOutputStream out=new FileOutputStream(temporary)){out.write(download.get());}
                    if(!wanted())return;
                    // Validate before replacing a cache entry; independent loaders never share a temporary file.
                    decoded=decode(temporary,width,height);
                    if(!wanted())return;
                    try{Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
                    prune();
                }
                if(!wanted())return;
                Bitmap bitmap=decoded;
                delivering=main.post(()->{
                    synchronized(pending) {
                        try {
                            if(!wanted()){bitmap.recycle();return;}
                            if(bitmap.getAllocationByteCount()/1024<=images.maxSize()/2)images.put(key,bitmap);
                            // Detach before callbacks: a callback may clear/reload the same uncached image.
                            pending.remove(key);
                            List<Request> recipients=new ArrayList<>(requests);requests.clear();
                            for(Request r:recipients)r.owner=null;
                            for(Request r:recipients){Ready callback=r.ready;r.ready=null;if(callback!=null&&!closed&&epoch==generation)callback.accept(bitmap);}
                        } finally { if(pending.get(key)==this)pending.remove(key); }
                    }
                });
            } catch(Exception ignored) { /* A missing image never blocks navigation. */ }
            finally {
                if(temporary!=null)temporary.delete();
                if(!delivering){if(decoded!=null)decoded.recycle();synchronized(pending){for(Request r:requests){r.ready=null;r.owner=null;}requests.clear();if(pending.get(key)==this)pending.remove(key);}}
            }
        }
    }
    private static Bitmap decode(File file,int width,int height)throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),(decoder,info,source)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();
            if(w<=0||h<=0||w>16384||h>16384)throw new IllegalArgumentException("Image bounds");
            // Fit without stretching or cropping, including transparent logos and landscape episode art.
            float scale=Math.min(1f,Math.min(Math.max(1,width)/(float)w,Math.max(1,height)/(float)h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE);
        });
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
            for(Work work:pending.values()){for(Request r:work.requests){r.ready=null;r.owner=null;}work.requests.clear();if(work.future!=null)work.future.cancel(true);}
            // Posted deliveries run once to release their unpublished bitmap; no view callback survives the epoch.
            pending.clear();io.getQueue().clear();images.evictAll();images.resize(Visuals.cacheKb(context));
        }
    }
    void close(){synchronized(pending){closed=true;clear();io.shutdownNow();}}
}
