package tv.vela.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import androidx.media3.common.*;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.util.*;

/** One optional engine for disk-backed HLS or frame-rate matching; never alongside MediaPlayer. */
final class VelaDvrPlayer {
    interface Events {void ready();void frame();void size(int height);void failed();void captions(String text);default void ended(){}}
    private final ExoPlayer engine;private final Context context;private final String channel;private boolean ready;private volatile boolean closed;int seeks;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final VelaFrameRate frameRate;
    private final VideoCadence cadence=new VideoCadence();
    private Format observedFormat;private boolean sampled;private volatile boolean timingUnavailable;
    VelaDvrPlayer(android.app.Activity c,String name,Surface surface,String loopback,Events events){
        context=c;channel=name;
        DefaultLoadControl limits=new DefaultLoadControl.Builder().setBufferDurationsMs(1000,3000,300,600).setTargetBufferBytes(4*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build();
        int matching=VelaFrameRate.mode(c);frameRate=matching==VelaFrameRate.OFF?null:new VelaFrameRate(c,surface,matching);
        engine=new ExoPlayer.Builder(c).setLoadControl(limits).setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF).build();
        engine.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),false);
        engine.setVideoSurface(surface);
        if(frameRate!=null)engine.setVideoFrameMetadataListener((presentationUs,releaseNs,format,mediaFormat)->{
            if(closed)return;
            if(format!=observedFormat){observedFormat=format;cadence.reset();timingUnavailable=false;sampled=VideoCadence.valid(format.frameRate);if(sampled)postRate(format.frameRate);else main.post(()->{if(!closed)frameRate.unknown();});}
            if(!sampled){float rate=cadence.sample(presentationUs);if(rate>0){sampled=true;postRate(rate);}else if(cadence.finished()){sampled=true;timingUnavailable=true;}}
        });
        engine.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){if(closed)return;if(state==Player.STATE_READY&&!ready){ready=true;events.ready();}matchingState();if(state==Player.STATE_ENDED)events.ended();}
            @Override public void onRenderedFirstFrame(){if(!closed)events.frame();}
            @Override public void onVideoSizeChanged(VideoSize size){if(!closed)events.size(size.height);}
            @Override public void onPlayerError(PlaybackException error){if(!closed)events.failed();}
            @Override public void onCues(CueGroup cues){StringBuilder text=new StringBuilder();for(androidx.media3.common.text.Cue cue:cues.cues)if(cue.text!=null){if(text.length()>0)text.append('\n');if(text.length()<4096)text.append(cue.text);}if(!closed)events.captions(text.toString());}
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK)seeks++;}
            @Override public void onTracksChanged(Tracks tracks){if(closed)return;restore();if(frameRate!=null&&audioOnly()){timingUnavailable=true;frameRate.unknown();}}
            @Override public void onIsPlayingChanged(boolean playing){if(!closed&&playing&&audioOnly())events.frame();}
            @Override public void onPlayWhenReadyChanged(boolean playWhenReady,int reason){matchingState();}
        });
        MediaItem item=new MediaItem.Builder().setUri(loopback).setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2500).setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()).build();
        try{engine.setMediaSource(new DefaultMediaSourceFactory(new DefaultHttpDataSource.Factory().setUserAgent("Vela")).createMediaSource(item));engine.prepare();engine.play();}
        catch(RuntimeException failure){close();throw failure;}
    }
    private void postRate(float rate){main.post(()->{if(!closed)frameRate.rate(rate);});}
    private void matchingState(){if(closed||frameRate==null)return;int state=engine.getPlaybackState();frameRate.playing(ready&&engine.getPlayWhenReady()&&(state==Player.STATE_READY||state==Player.STATE_BUFFERING));}
    private boolean audioOnly(){Tracks tracks=engine.getCurrentTracks();return tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)&&!tracks.isTypeSelected(C.TRACK_TYPE_VIDEO);}
    String frameRateDetail(){return frameRate==null?VelaFrameRate.name(VelaFrameRate.OFF):timingUnavailable?L10n.text("Unavailable for this signal"):frameRate.detail();}
    float frameRate(){return frameRate==null?0:frameRate.rate();}
    void play(){engine.play();}void pause(){engine.pause();}boolean playing(){return engine.isPlaying();}
    long position(){return engine.getCurrentPosition();}long duration(){return engine.getDuration();}boolean seekable(){return engine.isCurrentMediaItemSeekable();}
    void seek(long position){engine.seekTo(Math.max(0,position));}void live(){engine.seekToDefaultPosition();engine.play();}
    int width(){return engine.getVideoSize().width;}int height(){return engine.getVideoSize().height;}
    void close(){if(closed)return;closed=true;main.removeCallbacksAndMessages(null);try{engine.release();}finally{if(frameRate!=null)frameRate.close();}}
    private void restore(){
        SharedPreferences prefs=VelaPlaybackPanel.prefs(context);
        String audio=prefs.getString(VelaPlaybackPanel.key(channel,"audio"),""),sub=prefs.getString(VelaPlaybackPanel.key(channel,"sub"),"");
        TrackSelectionParameters before=engine.getTrackSelectionParameters();TrackSelectionParameters.Builder next=before.buildUpon();boolean audioFound=false,textFound=false;
        for(Tracks.Group group:engine.getCurrentTracks().getGroups()){
            int type=group.getType();if(type!=C.TRACK_TYPE_AUDIO&&type!=C.TRACK_TYPE_TEXT||type==C.TRACK_TYPE_AUDIO&&audioFound||type==C.TRACK_TYPE_TEXT&&textFound)continue;
            String wanted=type==C.TRACK_TYPE_AUDIO?audio:sub;if(wanted.isEmpty()||wanted.equals("off"))continue;
            for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)&&wanted.equals("lang:"+group.getTrackFormat(i).language)){
                next.setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),i)).setTrackTypeDisabled(type,false);
                if(type==C.TRACK_TYPE_AUDIO)audioFound=true;else textFound=true;break;
            }
        }
        if(sub.equals("off"))next.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true);
        TrackSelectionParameters updated=next.build();if(!before.equals(updated))engine.setTrackSelectionParameters(updated);
    }
    void items(List<VelaPlaybackPanel.Item> items){
        Set<String> formats=new LinkedHashSet<>();for(Tracks.Group group:engine.getCurrentTracks().getGroups()){int type=group.getType();for(int i=0;i<group.length;i++){final int index=i;Format f=group.getTrackFormat(i);if(f.sampleMimeType!=null)formats.add(f.sampleMimeType);if((type!=C.TRACK_TYPE_AUDIO&&type!=C.TRACK_TYPE_TEXT)||!group.isTrackSupported(i))continue;String language=f.language==null?"und":f.language;String label=language.equals("und")?L10n.text("Default"):new java.util.Locale(language).getDisplayLanguage(Ui.locale());items.add(new VelaPlaybackPanel.Item((group.isTrackSelected(i)?"✓ ":"")+(type==C.TRACK_TYPE_AUDIO?L10n.text("Audio"):L10n.text("Subtitles"))+" · "+label,"",()->{engine.setTrackSelectionParameters(engine.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),index)).setTrackTypeDisabled(type,false).build());VelaPlaybackPanel.prefs(context).edit().putString(VelaPlaybackPanel.key(channel,type==C.TRACK_TYPE_AUDIO?"audio":"sub"),"lang:"+language).apply();}));}}
        items.add(new VelaPlaybackPanel.Item(L10n.text("Turn subtitles off"),"",()->{engine.setTrackSelectionParameters(engine.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());VelaPlaybackPanel.prefs(context).edit().putString(VelaPlaybackPanel.key(channel,"sub"),"off").apply();}));
        items.add(new VelaPlaybackPanel.Item(L10n.text("Video signal"),width()+" × "+height()+" · "+android.text.TextUtils.join(" · ",formats),null));
    }
}
