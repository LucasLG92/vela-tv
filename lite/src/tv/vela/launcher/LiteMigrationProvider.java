package tv.vela.launcher;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Not exported. Android grants one exact random URI after the user accepts the export screen. */
public final class LiteMigrationProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "application/json";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        SharedPreferences prefs=getContext().getSharedPreferences("vela-migration",0);long expires=prefs.getLong("expires",0),now=System.currentTimeMillis();
        String token=prefs.getString("token","");
        if(!"r".equals(mode)||token.isEmpty()||now>expires||expires-now>120000||!("/"+token).equals(uri.getPath())||uri.getQuery()!=null||uri.getFragment()!=null)throw new FileNotFoundException();
        File file=new File(getContext().getCacheDir(),"unified-profile.json");if(!file.isFile()||file.length()>VelaWorkspace.MAX_BYTES)throw new FileNotFoundException();
        ParcelFileDescriptor result=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);prefs.edit().clear().commit();file.delete();return result;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
