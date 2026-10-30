package tv.vela.launcher;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import javax.net.ssl.*;

/** Test transport only. No request can leave the reserved fixture host. */
final class RefinementHttp {
    interface Reply {byte[] get(URL url)throws Exception;}
    static byte[] text(String text){return text.getBytes(StandardCharsets.UTF_8);}
    static byte[] asset(Context c,String name)throws IOException{try(InputStream in=c.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);byte[] raw=out.toByteArray();if(!name.endsWith(".jpg"))return raw;android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1280||o.outHeight/o.inSampleSize>960)o.inSampleSize*=2;o.inJustDecodeBounds=false;android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,o);if(image==null)throw new IOException("Fixture image unavailable");out.reset();image.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,out);image.recycle();return out.toByteArray();}}
    static void install(Reply reply){install(reply,java.util.Collections.singleton("vela-fixture.invalid"));}
    static void install(Reply reply,java.util.Set<String> fixtureHosts){URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new HttpsURLConnection(u){byte[] body;boolean read;
        public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public String getCipherSuite(){return "fixture";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
        public int getResponseCode()throws IOException{if(!fixtureHosts.contains(url.getHost()))throw new IOException("External network disabled");if(!read){read=true;try{body=reply.get(url);}catch(Exception e){throw new IOException(e);}}return body==null?404:200;}
        public String getContentType(){return url.getPath().contains("image")||url.getPath().contains("photo")?"image/jpeg":"application/json";}
        public InputStream getInputStream()throws IOException{if(getResponseCode()!=200)throw new FileNotFoundException();return new ByteArrayInputStream(body);}
    };}}:null);}
}
