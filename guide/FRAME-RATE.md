# Match the video, keep your home comfortable

Available in **Vela 1.35.1-preview**. Frame-rate matching is exclusive to Full Vela's network player.

Open **Settings → Television → Match content frame rate**, or the same option in playback controls:

| Setting | Behaviour |
| --- | --- |
| Off, the default | Use the selected playback engine without an explicit display-rate request. Automatic playback starts with Media3 unless a successful compatibility choice is remembered. |
| Seamless only | Request the video's rate without opting into disruptive display switches. The device may keep its current refresh rate. |
| Allow display switching | Permit a compatible refresh-rate change. Some televisions briefly show a black screen. |

Android 12+ uses `Surface.setFrameRate` with the selected switching policy. Android 11 uses its two-argument Surface API; explicit display-switch opt-in can additionally choose a compatible mode at the current resolution. Vela restores the window's previous preference when its request ends, without writing Android's saved display settings. Another component's later window preference is not overwritten.

The display/firmware decides which rates it supports and whether it switches. Frame-rate matching does not change video speed. Plex, Jellyfin and other external apps control their own playback. HDMI and the native antenna tuner retain their own display controls.

## One player, no extra probe

Full uses Media3 for matching on direct channels and advertised archives. Select Media3 explicitly if a remembered compatibility choice uses the system player. It does not run alongside MediaPlayer. Compatible local HLS pause still uses the same engine and its existing bounded disk window.

Video format metadata supplies the rate where available. Otherwise a small timestamp sample needs 32 stable frame intervals, stops after at most 240 frames per format, and never downloads a second copy of the stream. Fractional rates such as 23.976 and 59.94 remain distinct from 24 and 60. Uncertain timing does not cause a guessed switch.

Identical requests are suppressed. Pause, format uncertainty and exit clear the active request; playback can restore a known rate when it resumes. Short buffering and seek transitions retain the known request instead of repeatedly switching the display. Surface recreation starts a fresh playback owner. Audio-only channels do not request a video rate. If the optional engine cannot play a signal, Vela releases it and tries the system player before exhausting the channel's existing alternatives.

Media3 uses the selected Fast, Balanced or Stable encoded-buffer target (2, 4 or 8 MiB); light mode and Android low-RAM devices clamp it to 4 MiB. This is not total application RAM. No dependency, service, background probe, permanent sampling job or second decoder is added. The setting stays local to the device and is excluded from preference sync.

References: [Android frame-rate guidance](https://developer.android.com/media/optimize/performance/frame-rate), [TV display settings](https://developer.android.com/training/tv/playback/adjust-display-settings), [Media3 switching strategy](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder#setVideoChangeFrameRateStrategy(int)).
