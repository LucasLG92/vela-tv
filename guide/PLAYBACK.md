# M3U and Xtream playback

Available in Full mode from **Vela 2.3.0 Preview**. Open **Settings → Television** or hold OK while watching and open playback controls. The guide and player keep Vela's existing design.

## Start automatically, adjust when needed

| Engine | Behaviour |
| --- | --- |
| Automatic (default) | Start with Media3, or the last successful system-player choice. A codec/format failure can try the other engine after releasing the first. |
| Media3 | Use Media3 with the selected buffering profile and optional frame-rate matching. |
| System player | Use Android's player for device-specific compatibility. Android controls its buffering. |

Compatible local HLS pause requires Media3 even when System is selected. Only one playback engine is active. No second decoder, parallel channel probe, preloaded stream or new background service is added.

## Buffer profiles

| Profile | Startup / rebuffer threshold | Minimum / maximum time target | Encoded-data target |
| --- | --- | --- | --- |
| Fast | 0.3 / 0.7 seconds | 1 / 3 seconds | 2 MiB |
| Balanced (default) | 0.7 / 1.5 seconds | 2 / 6 seconds | 4 MiB |
| Stable | 1.2 / 3 seconds | 5 / 12 seconds | 8 MiB |

These are Media3 load-control targets, not guaranteed start times. The byte target takes precedence when reached. Light mode and Android's low-RAM flag clamp it to 4 MiB. Network responses, codecs, video surfaces and the rest of Home use additional memory; these numbers do not cap total process RAM. Source segment lengths and throughput affect behaviour. The system player manages its own buffer.

## Remember what works

After five seconds of actual advancing playback, Vela remembers the successful format and engine for that channel, source and local profile. At most 32 entries are kept for seven days; entries contain hashes, format, engine and time, with no raw signal URLs or credentials. **Forget working signals** clears these hints without disconnecting a source.

The preference does not pin a lower-quality variant above the normal channel-quality order. It reorders formats inside each variant. Explicit engine settings take precedence.

## Recover without looping

A transient network failure or stopped progress gets one application-level retry before the next existing signal. Media3 may also retry a failed HTTP load once. Missing resources skip to another signal; access refusals only try the current variant's remaining formats and then stop. Automatic mode can try the compatibility engine for codec/format failures. Recovery has a ten-start and 90-second incident budget. Stable advancing playback starts a fresh incident budget for a later interruption.

Vela respects an HLS source's advertised live position. A behind-window failure gets one seek back to the live window before normal recovery. A closed live stream is a network interruption; an ended archive stays ended. Quality alternatives, archives, codecs and audio tracks depend on the provider and device.

## Playlist compatibility and privacy

M3U imports accept allow-listed User-Agent, Referer/Referrer and Origin values from `#EXTVLCOPT`, `#EXTV-headers`, passive Kodi stream/manifest-header options and URL pipe options. Each signal retains its own headers through catalogue merging, HLS child requests and compatible local pause. Unsupported executable or DRM rules are not evaluated.

Values are bounded and control characters are rejected. Headers stay inside the encrypted source catalogue. Personal sources, keys, learned hints and signal headers are excluded from preference export/sync and release files. Provider-specific authentication not represented by these passive headers may still require a compatible source URL.

Lite keeps native antenna playback and does not start the Full network player. The antenna service controls its own buffering and supported tracks.

Implementation references: [Media3 load-control targets](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder), [Media3 live windows and recovery](https://developer.android.com/media/media3/exoplayer/live-streaming).
