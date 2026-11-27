package tv.vela.launcher;

import android.content.Context;
import java.io.File;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** File-backed programme guides used by the playlist and public TV sources. */
final class TvGuideFileCache {
    interface Download { void write(File target) throws Exception; }
    interface Reader { Map<String,LiveTvData.Guide> read(File file) throws Exception; }

    private static final long FRESH=2*3600_000L, OFFLINE=24*3600_000L;
    private static final String PLAYLIST_PREFIX="m3u-epg-";
    private static final ReentrantLock lock=new ReentrantLock();

    static File playlist(Context context,String url){
        return LiveTvData.cached(context,PLAYLIST_PREFIX+LiveTvData.hash(url)+".data");
    }

    static Map<String,LiveTvData.Guide> load(File file,Download download,Reader reader)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        Map<String,LiveTvData.Guide> cached=readFresh(file,reader);
        if(cached!=null)return cached;
        try{lock.lockInterruptibly();}
        catch(InterruptedException cancelled){Thread.currentThread().interrupt();throw cancelled;}
        try{
            // A concurrent refresh may have finished while this caller was waiting.
            cached=readFresh(file,reader);
            if(cached!=null)return cached;
            boolean knownUnreadable=LiveTvData.fresh(file,FRESH);
            try{
                File candidate=File.createTempFile("guide-",".part",file.getParentFile());
                try{
                    download.write(candidate);
                    Map<String,LiveTvData.Guide> result=reader.read(candidate);
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    Files.move(candidate.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    if(file.getName().startsWith(PLAYLIST_PREFIX))trimPlaylists(file);
                    return result;
                }finally{candidate.delete();}
            }catch(Exception failed){
                if(knownUnreadable||Thread.currentThread().isInterrupted()||!LiveTvData.fresh(file,OFFLINE))throw failed;
                return reader.read(file);
            }
        }finally{lock.unlock();}
    }

    private static Map<String,LiveTvData.Guide> readFresh(File file,Reader reader)throws Exception{
        if(!LiveTvData.fresh(file,FRESH))return null;
        try{return reader.read(file);}
        catch(Exception unreadable){
            if(Thread.currentThread().isInterrupted())throw unreadable;
            // Older versions sometimes cached an error response. Fetch it again immediately.
            return null;
        }
    }

    private static void trimPlaylists(File current){
        File[] others=current.getParentFile().listFiles((dir,name)->name.startsWith(PLAYLIST_PREFIX)
                &&name.endsWith(".data")&&!name.equals(current.getName()));
        if(others==null||others.length<2)return;
        Arrays.sort(others,Comparator.comparingLong(File::lastModified).reversed());
        for(int i=1;i<others.length;i++)others[i].delete();
    }

    private TvGuideFileCache(){}
}
