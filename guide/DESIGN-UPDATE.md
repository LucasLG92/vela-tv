# Design and usability

Available in **Vela 1.35.0-preview** and **Vela Lite 0.14.0-preview**.

## One interface in both editions

| Improvement | Where to find it | Behaviour |
| --- | --- | --- |
| Visual styles | Your Vela → Home design → Visual styles | Preview Cinema, Compact or Minimal, then Save or Cancel. Previewing does not write settings. |
| Shared focus | Home cards, apps and HDMI inputs | A short, consistent lift and shadow inside reserved card space. Reduced motion and Android's animation preference are respected. |
| Content information | Full: Details, INFO or hold OK on a library card. Both: future broadcasts and Programme details in channel actions. | Native information and actions use Vela's graphite surfaces. Guide information closes back to the same programme position. |
| Home collections | Full: Your Vela → Home design → Home collections | Browse connected Plex libraries or Jellyfin collections in pages of twelve. Pin at most three collections; each Home row loads at most eight titles. |
| Programme artwork | Your Vela → What to watch; programme information | Use actual EPG artwork when supplied, otherwise the channel logo or a text fallback. No generated programme images. |
| Expanded guide selection | Television → Guide | The selected programme uses more of its reserved row space, can wrap its title and shows current progress. Channel positions stay fixed. |
| Last activity | Full Home; Lite HDMI home | Return to the last app, connected HDMI input or successful channel opened through Vela. A missing app/input is not offered. |
| Performance lab | Your Vela → Remote & diagnostics | Explicit measurements of process memory, disk cache, last Home first draw and actual window frame durations. Save a numeric report as a local baseline. |

Full also exposes styles in **Settings → Appearance → Customize appearance**. Lite exposes them directly in **Settings → Appearance**. Individual appearance and home-layout controls remain available. Applying a preset resets card-size overrides while preserving section order, visibility, folders, reading preferences and reduced motion.

## Library information and collections

Movie and series information uses the connected library's own artwork, overview, year, duration and playback progress. Seasons and episodes load on request in pages of at most 24. Actual playback continues to open the connected provider's Android TV app. Existing per-item watched and Watch later actions remain available; holding OK on resumable content still puts Mark as watched first.

Collection references contain a provider, item ID, display title and hashed account identity. They do not store a provider URL or session token. Pins stay with the local profile, are excluded from portable settings and sync, and cannot be queried through another account. Home displays pins from its selected library provider(s); the collection manager can manage any connected provider. Disconnected or switched accounts do not expose the preceding account's rows.

The clients use the [Plex collection and child-item endpoints](https://developer.plex.tv/pms/) and user-scoped Jellyfin item queries. The server remains responsible for the contents visible to its user. Pagination and response-size limits are checked rather than silently importing an entire collection or series.

## Image and resource limits

- Programme artwork has one temporary worker, a queue of at most eight requests and a **512 KiB cache**. Views release their image when they leave the visible area; returning can use the cache or request it again.
- Each image transfer is limited to 2 MiB. Decode dimensions are bounded to approximately 640 × 360; unsafe file URLs and URL credentials are rejected. Redirects are limited and HTTPS is not downgraded.
- XMLTV is still parsed as a stream. Full retains its database limit and separately bounds added descriptions to a 2 MiB estimated text budget and artwork references to 4 MiB. Titles and schedules remain available when optional metadata is omitted. Lite keeps its existing tuned-channel and database bounds.
- Full library details reuse the existing artwork cache and worker. Minimal skips full-screen backdrop requests. Collections are bounded to three rows and 24 additional item records, with visible-poster loading.
- Last activity is one local reference per profile, excluded from backup/sync. No global usage permission or activity-monitoring service is added. Channel references are resolved against the current source and available channels.
- Frame capture stops after 30 seconds, on replacement, on pause or on leaving the lab. A later measurement can start a new session. No monitoring service or permanent frame callback runs outside the panel.

PSS measures the selected process, not all of Android or the whole television. Java/native heap, disk cache and frame timings describe different quantities. An idle screen produces few frames. Emulator measurements are not a physical-TV performance certification or evidence of total RAM savings.

Lite still excludes Plex, Jellyfin, IPTV clients, the Full player and Media3. Both editions retain six UI languages. Programme/library titles retain their source text.

## Native verification

Use only an explicitly selected, owned emulator with neutral app state:

```sh
python tests/run_refinement.py --serial <emulator> --adb-port 5038 --art <authorised-fixture-art>
python tests/run_refinement.py --serial <emulator> --adb-port 5038 --lite --art <authorised-fixture-art>
python tests/run_refinement.py --serial <emulator> --adb-port 5038 --test library --art <authorised-fixture-art>
```

The artwork directory is private test input, never a production asset. It contains `catalog.json` (an array of movie/series titles, summaries, years and durations), plus matching `poster-1.jpg`, `backdrop-1.jpg` and subsequent numbered artwork. The shared suite uses `backdrop-2.jpg`. The runner only copies those whitelisted names into the disposable test APK.

The tests exercise the actual remote, preset cancellation and saving, profile isolation, old SQLite schema migration, streaming XMLTV metadata, visible-image release/reload, disposable measurements, fixed guide geometry, real client endpoint parsing, collection limits and native movie/season/episode navigation. HTTPS is intercepted inside the disposable test process and confined to a reserved `.invalid` fixture host. No personal provider, library or physical tuner is contacted. The runner stops that process and removes its instrumentation APK afterwards.

Keep the existing feed, watched-state, guide, antenna, settings, localisation, package-isolation and privacy regressions alongside these checks. Screenshots are native UI captures with demonstration state and authorised source artwork.
