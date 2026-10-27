# Optimisation pass: playback and shared controls

Available in **Vela 1.35.1-preview** and **Vela Lite 0.14.1-preview**.

The left navigation, graphite theme and existing source/library behaviour remain the foundation. Full adds optional [content frame-rate matching](FRAME-RATE.md); Lite keeps its native tuner and excludes the Full playback engine.

## Changes that remove repeated work

- Playback controls reuse existing row views. Updating an option rebinds its data and changes text only when the title/detail changes. Read-only details stay outside remote focus; closing the panel releases its children.
- Track restoration reads one preferences snapshot instead of looking up profile preferences and normalising the channel name for each track. Already selected tuner tracks are not selected again; explicit caption enable/disable intent is preserved.
- Media3 restores audio/text choices in one idempotent parameters update, skips video/unsupported tracks and avoids reapplying identical overrides.
- The Full player's fit/crop/stretch layout changes only when target dimensions change. Empty subtitle callbacks do not allocate a caption view.
- Playback item construction is split into source, track and transport helpers. Pause/seek actions and archive completion share one path; duplicate temporary-storage rows and unreachable replay branches are removed.
- An optional engine failure releases its decoder and display request before retrying the same signal with the system player. Existing quality alternatives stay available. Audio-only playback and a normally ended archive are handled separately from failure.

## Measured scope

An owned native emulator ran the same four-row playback panel against the previous official APKs and the candidates. Forty option changes created **164 distinct row views before** and reused **4 after**, in both editions. Remote selection, read-only focus skipping and panel teardown are checked in the same scenario.

This measures view creation in that interaction. It is not a percentage reduction in total RAM, a physical-TV benchmark or a claim that every screen has been optimised. Default playback keeps its existing lightweight path; the optional engine is created only when matching or local pause is enabled.

## Verification

```sh
python tests/test_video_cadence.py
python tests/run_optimization.py --serial <owned-emulator> --test controls
python tests/run_optimization.py --serial <owned-emulator> --test controls --lite
python tests/run_optimization.py --serial <owned-emulator> --test frame-rate --fixture <owned-loopback-fixture>
```

`--baseline` records the old control-view count using an older official APK. The native suites reject physical TVs. The frame-rate suite requires clear synthetic HLS, a finite HLS archive, TS archive and audio fixture on loopback; it checks requests/lifecycle and actual decoding, not a manufacturer's physical refresh switch.

Existing HLS pause/archive, Full/Lite UI/focus, antenna, localisation, package isolation and privacy checks remain applicable. [Build guide](BUILD.md).

The [design preview](DESIGN-UPDATE.md) adds visible-image release, bounded programme metadata, a backdrop-free Minimal style and a temporary measurement panel. Its measurements remain separate from the earlier control-view allocation comparison.
