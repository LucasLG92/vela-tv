package tv.vela.launcher;

/** A bounded timestamp sample, not a frame counter or a permanent sampling task. */
final class VideoCadence {
    private static final float[] RATES={24000f/1001,24,25,30000f/1001,30,48,50,60000f/1001,60,100,120000f/1001,120};
    private long previous=Long.MIN_VALUE,total;
    private int samples,frames;
    private double interval;
    private boolean complete;

    static boolean valid(float fps){return !Float.isNaN(fps)&&!Float.isInfinite(fps)&&fps>=10&&fps<=240;}
    static float canonical(float fps){
        if(Math.abs(fps-240)<0.012f)return 240;
        if(!valid(fps))return 0;
        // Keep fractional broadcast rates distinct from their integer neighbours.
        for(float rate:RATES)if(Math.abs(fps-rate)<0.012f)return rate;
        return fps;
    }
    static boolean compatible(float fps,float hz){
        if(!valid(fps)||!valid(hz))return false;
        int multiple=Math.round(hz/fps);
        return multiple>=1&&multiple<=10&&Math.abs(hz-fps*multiple)<0.025f;
    }
    void reset(){previous=Long.MIN_VALUE;total=0;samples=frames=0;interval=0;complete=false;}
    boolean finished(){return complete;}
    float sample(long presentationUs){
        if(complete)return 0;
        if(++frames>240){complete=true;return 0;}
        long old=previous;previous=presentationUs;
        if(old==Long.MIN_VALUE)return 0;
        long delta=presentationUs-old;
        if(delta<4000||delta>100000){samples=0;total=0;interval=0;return 0;}
        if(samples>0&&Math.abs(delta-interval)>Math.max(120,interval*0.015)){samples=0;total=0;}
        total+=delta;samples++;interval=total/(double)samples;
        if(samples<32)return 0;
        complete=true;
        return canonical((float)(1000000d/interval));
    }
}
