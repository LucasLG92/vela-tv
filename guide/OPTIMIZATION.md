# Optimisation: artwork, playback and shared controls

## Artwork and navigation fixes — Vela 2.2.1 Preview

This pass is included in Vela 2.2.1 Preview and the standalone Lite 0.18.4 transition update. It keeps the existing layout and cache budgets; it does not add a service or a background timer.

- Full decodes artwork to fit the requested display size, preserving aspect ratio, transparent logos and hardware-backed bitmaps. Power-of-two sampling previously retained unnecessary pixels, especially for unusually wide images.
- Cancelling the last consumer removes its queued work immediately. Cancelling one consumer of a shared request leaves the other consumers intact. A replacement request cannot join an abandoned delivery.
- A corrupt disk entry gets one bounded download attempt instead of remaining unusable until the 24-hour expiry. Independent loaders use unique temporary files and validate images before replacing an entry. Temporary files are removed on failure/cancellation.
- Full and Lite clear the previous programme's image when a card is rebound. An unchanged binding is reused. Visibility checks are coalesced, cancelled queued tasks are removed and unpublished stale results are released.
- The shared clock fits localised AM/PM text inside its existing card. Native checks exercise both suffixes in all six languages; card dimensions stay unchanged.

### Measurements

The same generated PNGs were loaded through the real Full artwork loader on a native API 36 emulator, first with the official 2.2.0 APK and then with this candidate. `Bitmap.getAllocationByteCount()` includes the emulator's bitmap allocation overhead.

| Synthetic source → requested size | Before | Candidate |
| --- | ---: | ---: |
| Poster 2048×3072 → 180×270 | 256×384; 393,536 bytes | 180×270; 194,720 bytes |
| Panorama 4096×256 → 352×198 | 4096×256; 4,194,624 bytes | 352×22; 31,296 bytes |
| Transparent logo 2000×400 → 650×182 | 1000×200; 800,320 bytes | 650×130; 338,320 bytes |
| Cancelled requests occupying the 32-slot queue | 32 | 0 |
| Newly visible image delivered after that cancellation | 0 | 1 |
| Fresh corrupt disk entry recovered | No | Yes |

These are per-image allocations and controlled queue behaviour, **not a percentage reduction in total app RAM**. Aspect ratio is preserved: a panorama or logo is fitted rather than stretched to fill the requested rectangle. Full's memory cache remains 3/6 MiB in lightweight/normal mode, and the shared programme-art cache remains 512 KiB. Physical-TV RAM and playback have not been measured in this pass.

### Regression checks

```sh
python tests/run_refinement.py --serial <owned-emulator> --test efficiency --art gallery
```

The runner refuses physical devices. It uses generated artwork and a reserved HTTPS test transport; no Plex/Jellyfin account, IPTV source or external network is needed. `--fixture baseline` records the previous behaviour without enforcing the candidate's assertions. On Android 8 the same unified APK runs only the shared programme-image and clock checks; the Full-only loader is not initialised.

Additional coverage includes independent writers for the same cache file, original logo alpha/pixels, cancellation after decoding but before UI delivery, shared-consumer cancellation, and clearing pending callbacks. The existing native Home personalisation and discovery suites check real layouts, remote focus, bounded feeds and programme detail images. Android 8's unified-mode suite checks Lite startup and routing.

## Earlier playback and controls pass

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
