package tv.vela.launcher;

/** One foreground tune. Counts starts, retries transient faults once and never probes another stream in parallel. */
final class VelaPlaybackRecovery {
    enum Failure {NETWORK,STALLED,DECODER,ACCESS,MISSING,UNKNOWN}
    enum Action {RETRY,OTHER_ENGINE,NEXT_SIGNAL,STOP}
    static final int MAX_STARTS=10;
    static final long MAX_RECOVERY_MS=90_000;
    private String signal="";private int engines,retries,starts;private boolean accessDenied;private long began=-1;
    boolean start(String key,int engine){return start(key,engine,0);}
    boolean start(String key,int engine,long now){if(began<0)began=now;if(starts>=MAX_STARTS||now-began>=MAX_RECOVERY_MS)return false;starts++;if(!signal.equals(key)){signal=key;engines=0;retries=0;}engines|=1<<engine;return true;}
    Action failed(Failure reason,int engine,boolean automatic){
        if(starts>=MAX_STARTS)return Action.STOP;
        if(reason==Failure.ACCESS){accessDenied=true;return Action.NEXT_SIGNAL;}
        if(reason==Failure.MISSING)return Action.NEXT_SIGNAL;
        if(reason==Failure.NETWORK||reason==Failure.STALLED){if(retries++==0)return Action.RETRY;return Action.NEXT_SIGNAL;}
        int other=engine==VelaPlaybackPolicy.MEDIA3?VelaPlaybackPolicy.SYSTEM:VelaPlaybackPolicy.MEDIA3;
        if(automatic&&(engines&(1<<other))==0)return Action.OTHER_ENGINE;
        return Action.NEXT_SIGNAL;
    }
    boolean sameVariantOnly(){return accessDenied;}
    void success(){accessDenied=false;}
    void stable(int engine){accessDenied=false;starts=0;retries=0;engines=1<<engine;began=-1;}
    int starts(){return starts;}
}
