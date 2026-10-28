package tv.vela.launcher;

import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.net.*;import java.lang.reflect.*;import java.nio.charset.StandardCharsets;import java.security.cert.Certificate;import javax.net.ssl.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;

/** Redirected HLS on a synthetic loopback origin only; no provider, account or decoder. */
public final class AndroidHlsRedirectTest extends Instrumentation {
    private Fixture fixture;private VelaHlsBuffer buffer;private Context context;private String phase="setup";private final AtomicInteger redirected=new AtomicInteger();
    void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    interface Ready{boolean ready()throws Exception;}
    void await(Ready value)throws Exception{long end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){if(value.ready())return;Thread.sleep(30);}throw new AssertionError("Timeout: "+phase);}
    static byte[] text(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static final class Fixture implements AutoCloseable {
        final ServerSocket server=new ServerSocket();final Set<String> paths=Collections.synchronizedSet(new HashSet<>());final Thread worker;volatile boolean closed;
        Fixture()throws IOException{server.bind(new InetSocketAddress("127.0.0.1",0));worker=new Thread(this::serve,"hls-test-origin");worker.setDaemon(true);worker.start();}
        String url(String path){return "http://127.0.0.1:"+server.getLocalPort()+path;}
        void serve(){while(!closed)try(Socket socket=server.accept()){socket.setSoTimeout(2000);String[] first=VelaConnect.line(socket.getInputStream(),1024).split(" ");if(first.length<2)continue;String path=first[1];paths.add(path);int status=200;String location="";byte[] body;
            if(path.equals("/p/master/live.m3u8"))body=text("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nlevels/live.m3u8\n");
            else if(path.equals("/p/master/levels/live.m3u8")){status=307;location="../../edge/live.m3u8";body=new byte[0];}
            else if(path.equals("/p/edge/live.m3u8"))body=text("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:10\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4,\none.ts\n#EXTINF:4,\ntwo.ts\n#EXT-X-ENDLIST\n");
            else if(path.equals("/p/edge/two.ts")){status=302;location="chunks/two.ts";body=new byte[0];}
            else if(path.equals("/p/edge/one.ts")||path.equals("/p/edge/chunks/two.ts")||path.equals("/p/edge/init.mp4")){body=new byte[188*4];Arrays.fill(body,(byte)0x47);}
            else{status=404;body=new byte[0];}
            OutputStream out=socket.getOutputStream();out.write(text("HTTP/1.1 "+status+" Test\r\n"+(location.isEmpty()?"":"Location: "+location+"\r\n")+"Content-Length: "+body.length+"\r\nConnection: close\r\n\r\n"));out.write(body);out.flush();
        }catch(IOException ignored){}}
        public void close(){closed=true;try{server.close();worker.join(1000);}catch(Exception ignored){}}
    }
    void transport(){URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new HttpsURLConnection(url){
        public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public String getCipherSuite(){return "fixture";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
        public int getResponseCode()throws IOException{if(!url.getHost().equals("vela-fixture.invalid"))throw new IOException("External network disabled");redirected.incrementAndGet();return 302;}
        public String getHeaderField(String name){if(!name.equalsIgnoreCase("Location"))return null;return url.getPath().equals("/cycle.m3u8")?"/cycle.m3u8":url.getPath().equals("/unsafe.m3u8")?"file:///never-request-this":fixture.url("/p/master/live.m3u8");}
        public InputStream getInputStream(){return new ByteArrayInputStream(new byte[0]);}
    };}}:null);}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();context=getTargetContext();boolean success=false;try{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("getprop ro.kernel.qemu");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){check(in.read()=='1',"Owned emulator required");}
        fixture=new Fixture();transport();phase="redirected-playlist";buffer=new VelaHlsBuffer(context,"https://vela-fixture.invalid/entry.m3u8");await(()->buffer.seconds()>=8||(Boolean)field(buffer,"failed"));check(!(Boolean)field(buffer,"failed"),"Redirected HLS was rejected before segments could load");check(buffer.bytes()==188*8,"Redirected segments were not stored intact");
        check(fixture.paths.containsAll(Arrays.asList("/p/master/live.m3u8","/p/master/levels/live.m3u8","/p/edge/live.m3u8","/p/edge/init.mp4","/p/edge/one.ts","/p/edge/two.ts","/p/edge/chunks/two.ts")),"Relative URLs were not resolved against the final playlist location");
        String playlist;URLConnection local=new URL(buffer.url()).openConnection();local.setConnectTimeout(3000);local.setReadTimeout(3000);try(InputStream in=local.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[1024];for(int n;(n=in.read(bytes))!=-1;)out.write(bytes,0,n);playlist=out.toString("UTF-8");}check(playlist.contains("10.seg")&&playlist.contains("11.seg")&&playlist.contains("#EXT-X-MAP")&&playlist.contains("#EXT-X-ENDLIST"),"Loopback playlist lost buffered media");File dir=(File)field(buffer,"directory");buffer.close();buffer=null;check(!dir.exists(),"Closed buffer retained its files");
        phase="redirect-loop";int before=redirected.get();buffer=new VelaHlsBuffer(context,"https://vela-fixture.invalid/cycle.m3u8");await(()->(Boolean)field(buffer,"failed"));check(redirected.get()-before<=6,"Redirect loop was not bounded");buffer.close();buffer=null;
        phase="unsafe-redirect";buffer=new VelaHlsBuffer(context,"https://vela-fixture.invalid/unsafe.m3u8");await(()->(Boolean)field(buffer,"failed"));check(buffer.bytes()==0,"Unsupported redirect scheme loaded data");buffer.close();buffer=null;success=true;result.putString("result","PASS HTTPS-to-HTTP media redirect, master/media/segment redirects, final URI resolution, bounded loop, invalid scheme and disk cleanup");
    }catch(Throwable e){result.putString("result","FAIL "+phase+": "+e.getClass().getSimpleName()+" · "+e.getMessage());}finally{if(buffer!=null)buffer.close();if(fixture!=null)fixture.close();}finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
