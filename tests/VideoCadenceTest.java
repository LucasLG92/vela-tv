package tv.vela.launcher;

/** Timestamp-level tests: no decoder, network or device is needed. */
public final class VideoCadenceTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static float estimate(float fps){VideoCadence sample=new VideoCadence();float result=0;for(int i=0;i<100;i++){float value=sample.sample(Math.round(i*1000000d/fps));if(value>0)result=value;}return result;}
    public static void main(String[] args){
        for(float fps:new float[]{10,24000f/1001,24,25,30000f/1001,30,48,50,60000f/1001,60,100,120,240})check(Math.abs(estimate(fps)-fps)<0.02f,"Wrong cadence: "+fps);
        check(VideoCadence.canonical(59.94f)!=60,"Fractional broadcast rate rounded to 60");
        check(VideoCadence.compatible(24,120)&&VideoCadence.compatible(25,50),"Compatible integer cadence rejected");
        check(!VideoCadence.compatible(24,60)&&!VideoCadence.compatible(25,60),"Judder-prone cadence accepted");
        check(!VideoCadence.compatible(60000f/1001,60),"59.94 and 60 were treated as equivalent");
        for(float bad:new float[]{0,-1,9,241,Float.NaN,Float.POSITIVE_INFINITY})check(VideoCadence.canonical(bad)==0,"Invalid FPS accepted");
        VideoCadence variable=new VideoCadence();long pts=0;for(int i=0;i<250;i++){pts+=i%2==0?20000:40000;check(variable.sample(pts)==0,"Variable timestamps guessed a fixed rate");}
        for(int i=0;i<60;i++)check(variable.sample(pts+=40000)==0,"Sampling continued after its 240-frame budget");
        variable.reset();float found=0;for(int i=0;i<40;i++){float value=variable.sample(i*40000L);if(value>0)found=value;}check(found==25,"New format did not reset the bounded sampler");
        VideoCadence discontinuous=new VideoCadence();discontinuous.sample(Long.MAX_VALUE-10);check(discontinuous.sample(1)==0,"Timestamp discontinuity generated a cadence");
        System.out.println("PASS "+checks+" cadence, fractional-rate, discontinuity and bounded-sampling checks");
    }
}
