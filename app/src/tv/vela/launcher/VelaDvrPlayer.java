package tv.vela.launcher;

import android.content.Context;
import android.view.Surface;
import androidx.media3.common.*;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import java.util.*;

/** Loaded only for the opt-in disk-backed HLS window; never alongside MediaPlayer. */
final class VelaDvrPlayer {
    interface Events {void ready();void frame();void size(int height);void failed();void captions(String text);}
    private final ExoPlayer engine;private final Context context;private final String channel;private boolean ready,closed;int seeks;
    VelaDvrPlayer(Context c,String name,Surface surface,String loopback,Events events){
        context=c;channel=name;
        DefaultLoadControl limits=new DefaultLoadControl.Builder().setBufferDurationsMs(1000,3000,300,600).setTargetBufferBytes(4*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build();
        engine=new ExoPlayer.Builder(c).setLoadControl(limits).build();
        engine.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),false);
        engine.setVideoSurface(surface);
        engine.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){if(!closed&&state==Player.STATE_READY&&!ready){ready=true;events.ready();}}
            @Override public void onRenderedFirstFrame(){if(!closed)events.frame();}
            @Override public void onVideoSizeChanged(VideoSize size){if(!closed)events.size(size.height);}
            @Override public void onPlayerError(PlaybackException error){if(!closed)events.failed();}
            @Override public void onCues(CueGroup cues){StringBuilder text=new StringBuilder();for(androidx.media3.common.text.Cue cue:cues.cues)if(cue.text!=null){if(text.length()>0)text.append('\n');if(text.length()<4096)text.append(cue.text);}if(!closed)events.captions(text.toString());}
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK)seeks++;}
            @Override public void onTracksChanged(Tracks tracks){restore();}
        });
        MediaItem item=new MediaItem.Builder().setUri(loopback).setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2500).setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()).build();
        engine.setMediaSource(new HlsMediaSource.Factory(new DefaultHttpDataSource.Factory().setUserAgent("Vela")).createMediaSource(item));engine.prepare();engine.play();
    }
    void play(){engine.play();}void pause(){engine.pause();}boolean playing(){return engine.isPlaying();}
    long position(){return engine.getCurrentPosition();}long duration(){return engine.getDuration();}boolean seekable(){return engine.isCurrentMediaItemSeekable();}
    void seek(long position){engine.seekTo(Math.max(0,position));}void live(){engine.seekToDefaultPosition();engine.play();}
    int width(){return engine.getVideoSize().width;}int height(){return engine.getVideoSize().height;}
    void close(){if(closed)return;closed=true;engine.release();}
    private void restore(){for(Tracks.Group g:engine.getCurrentTracks().getGroups())for(int i=0;i<g.length;i++){Format f=g.getTrackFormat(i);String wanted=VelaPlaybackPanel.prefs(context).getString(VelaPlaybackPanel.key(channel,g.getType()==C.TRACK_TYPE_AUDIO?"audio":"sub"),"");if(wanted.equals("lang:"+f.language)){engine.setTrackSelectionParameters(engine.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(),i)).setTrackTypeDisabled(g.getType(),false).build());break;}}if(VelaPlaybackPanel.prefs(context).getString(VelaPlaybackPanel.key(channel,"sub"),"").equals("off"))engine.setTrackSelectionParameters(engine.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}
    void items(List<VelaPlaybackPanel.Item> items){
        Set<String> formats=new LinkedHashSet<>();for(Tracks.Group group:engine.getCurrentTracks().getGroups()){int type=group.getType();for(int i=0;i<group.length;i++){final int index=i;Format f=group.getTrackFormat(i);if(f.sampleMimeType!=null)formats.add(f.sampleMimeType);if((type!=C.TRACK_TYPE_AUDIO&&type!=C.TRACK_TYPE_TEXT)||!group.isTrackSupported(i))continue;String language=f.language==null?"und":f.language;String label=language.equals("und")?L10n.text("Default"):new java.util.Locale(language).getDisplayLanguage(Ui.locale());items.add(new VelaPlaybackPanel.Item((group.isTrackSelected(i)?"✓ ":"")+(type==C.TRACK_TYPE_AUDIO?L10n.text("Audio"):L10n.text("Subtitles"))+" · "+label,"",()->{engine.setTrackSelectionParameters(engine.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),index)).setTrackTypeDisabled(type,false).build());VelaPlaybackPanel.prefs(context).edit().putString(VelaPlaybackPanel.key(channel,type==C.TRACK_TYPE_AUDIO?"audio":"sub"),"lang:"+language).apply();}));}}
        items.add(new VelaPlaybackPanel.Item(L10n.text("Turn subtitles off"),"",()->{engine.setTrackSelectionParameters(engine.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());VelaPlaybackPanel.prefs(context).edit().putString(VelaPlaybackPanel.key(channel,"sub"),"off").apply();}));
        items.add(new VelaPlaybackPanel.Item(L10n.text("Video signal"),width()+" × "+height()+" · "+android.text.TextUtils.join(" · ",formats),null));
    }
}
