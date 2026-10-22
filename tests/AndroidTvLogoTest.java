package tv.vela.launcher;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.widget.ImageView;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/** Synthetic artwork only. Does not read or change any connected account. */
public final class AndroidTvLogoTest extends Instrumentation {
    private interface Condition {boolean ready()throws Exception;}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void await(Condition condition)throws Exception{long until=System.currentTimeMillis()+8000;while(System.currentTimeMillis()<until){waitForIdleSync();if(condition.ready())return;Thread.sleep(40);}throw new AssertionError("Artwork job did not finish");}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private int pixels(Bitmap bitmap){int found=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(Color.alpha(bitmap.getPixel(x,y))>0)found++;return found;}
    public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    public void onStart(){Bundle result=new Bundle();LiveTvLogos[] loader={null};File[] files=new File[2];try{
        Context c=getTargetContext();XtreamClient.Account account=new XtreamClient.Account("https://provider.invalid","fixture-user","fixture-password","fixture",true,true);
        check(XtreamClient.safeLogo(account,"http://images.invalid/logo.png").equals("http://images.invalid/logo.png"),"Public HTTP artwork rejected for HTTPS account");
        check(!XtreamClient.safeLogo(account,"https://images.invalid/logo.png").isEmpty(),"Public HTTPS artwork rejected");
        check(!XtreamClient.safeLogo(account,"http://provider.invalid/logo.png").isEmpty(),"Public same-host artwork rejected");
        check(!XtreamClient.safeLogo(account,"https://provider.invalid:443/logo?token=own").isEmpty(),"Same HTTPS origin changed");
        for(String bad:new String[]{"http://images.invalid/logo?password=fixture-password","http://provider.invalid/logo?username=fixture-user","http://provider.invalid/live/fixture-user/fixture-password/logo.png","https://images.invalid/fixture-password.png","https://images.invalid/%66ixture-user.png","https://provider.invalid:8443/logo?token=own","https://user:pass@images.invalid/logo.png","file:///logo.png","javascript:alert(1)","https://images.invalid:0/logo.png","https://images.invalid:65536/logo.png","https://images.invalid/logo.png#fragment","https://images.invalid/a b.png","https://images.invalid/a\\b.png"})check(XtreamClient.safeLogo(account,bad).isEmpty(),"Unsafe artwork URL accepted");
        check(!XtreamClient.safeLogo(new XtreamClient.Account("http://provider.invalid","fixture-user","fixture-password","fixture",true,true),"http://provider.invalid/logo?token=own").isEmpty(),"Explicit HTTP account origin changed");
        String url="http://fixture.invalid/"+UUID.randomUUID()+".png",cancelUrl="http://fixture.invalid/"+UUID.randomUUID()+".png";
        Bitmap source=Bitmap.createBitmap(440,264,Bitmap.Config.ARGB_8888);
        for(int i=0;i<2;i++){files[i]=LiveTvData.cached(c,"logo-"+LiveTvData.hash(i==0?url:cancelUrl)+".img");try(OutputStream out=new FileOutputStream(files[i])){source.compress(Bitmap.CompressFormat.PNG,100,out);}}
        source.recycle();LiveTvLogos.LogoView[] view={null},cancelled={null};Bitmap canvas=Bitmap.createBitmap(134,68,Bitmap.Config.ARGB_8888);
        runOnMainSync(()->{Ui.init(c);view[0]=new LiveTvLogos.LogoView(c,"Fixture");view[0].layout(0,0,134,68);view[0].draw(new Canvas(canvas));loader[0]=new LiveTvLogos(c);});
        check(pixels(canvas)>0,"Missing-logo initials disappeared");
        runOnMainSync(()->loader[0].load(new LiveTvData.Channel("fixture","Fixture","Tests",url,Collections.emptyList(),"1","fixture"),view[0]));
        await(()->view[0].getDrawable()!=null);Bitmap decoded=((BitmapDrawable)view[0].getDrawable()).getBitmap();
        check(decoded.getWidth()<=128&&decoded.getHeight()<=128,"Oversized decoded logo retained");
        canvas.eraseColor(Color.TRANSPARENT);runOnMainSync(()->view[0].draw(new Canvas(canvas)));check(pixels(canvas)==0,"Initials bleed through transparent artwork");canvas.recycle();
        check(((android.util.LruCache<?,?>)field(loader[0],"cache")).size()<=768,"Artwork cache budget changed");
        runOnMainSync(()->{cancelled[0]=new LiveTvLogos.LogoView(c,"Cancelled");loader[0].load(new LiveTvData.Channel("cancelled","Cancelled","Tests",cancelUrl,Collections.emptyList(),"2","fixture"),cancelled[0]);loader[0].page();});
        await(()->((ThreadPoolExecutor)field(loader[0],"io")).getActiveCount()==0);waitForIdleSync();check(cancelled[0].getDrawable()==null,"Old page received stale artwork");
        result.putString("result","PASS public HTTP/HTTPS logos, unchanged account transport, cross-origin credential and downgrade rejection, transparent artwork replaces initials, 128px decoded images, 768KiB memory cache, stale-page cancellation. Synthetic images only; no personal account accessed.");
    }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
    finally{if(loader[0]!=null)runOnMainSync(()->loader[0].close());for(File file:files)if(file!=null)file.delete();}
    finish(result.containsKey("error")?1:-1,result);}
}
