package tv.vela.launcher;

import android.content.Context;
import android.content.pm.*;
import java.io.*;
import java.nio.channels.*;

/** Only Vela's two private download files. A file lock also protects the separate receiver process. */
final class UpdateFiles {
    private static boolean cleaned,leased;
    static File directory(Context c){return new File(c.getCacheDir(),"updates");}
    static File apk(Context c){return new File(directory(c),"update.apk");}
    static File partial(Context c){return new File(directory(c),"incoming.part");}
    static final class Lease implements AutoCloseable {
        private final RandomAccessFile file;private final FileLock lock;
        private Lease(RandomAccessFile file,FileLock lock){this.file=file;this.lock=lock;}
        public void close()throws IOException{try{lock.release();}finally{try{file.close();}finally{released();}}}
    }
    private static synchronized void released(){leased=false;}
    static Lease acquire(Context c,boolean wait)throws IOException{
        // Do not open a second descriptor for the same lock in this process: closing it can
        // release POSIX locks owned through another channel on some Android versions.
        synchronized(UpdateFiles.class){if(leased){if(wait)throw new IOException("Update download busy");return null;}leased=true;}
        RandomAccessFile file=null;
        try{
            File folder=directory(c);if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Update cache unavailable");
            file=new RandomAccessFile(new File(folder,"update.lock"),"rw");
            FileLock lock=wait?file.getChannel().lock():file.getChannel().tryLock();
            if(lock==null){file.close();released();return null;}return new Lease(file,lock);
        }catch(IOException|RuntimeException failed){try{if(file!=null)file.close();}finally{released();}throw failed;}
    }
    static synchronized boolean cleanupOnce(Context c){if(cleaned)return true;if(!cleanup(c))return false;cleaned=true;return true;}
    /** PackageManager confirms completion; pressing Install or cancelling never deletes a pending APK. */
    static boolean cleanup(Context c){
        File apk=apk(c),part=partial(c);if(!apk.exists()&&!part.exists())return true;
        try(Lease lease=acquire(c,false)){
            if(lease==null)return false; // Another process is still downloading: leave both files alone.
            if(part.exists()&&!part.delete())return false;
            if(apk.isFile()){
                PackageManager pm=c.getPackageManager();PackageInfo candidate=pm.getPackageArchiveInfo(apk.getPath(),0),installed=pm.getPackageInfo(c.getPackageName(),0);
                if(candidate!=null&&c.getPackageName().equals(candidate.packageName)&&candidate.getLongVersionCode()<=installed.getLongVersionCode()&&!apk.delete())return false;
            }
            return true;
        }catch(IOException|PackageManager.NameNotFoundException|RuntimeException unavailable){return false;}
    }
}
