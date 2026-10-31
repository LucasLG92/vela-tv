# Changelog

## 2.0.0-preview / Lite transition 0.18.0-preview

- One unified APK: first-run Full/Lite choice and reversible, PIN-protected mode selection in System settings. Preserve configured Full installs. Android 8–10 runs Lite; Full remains Android 11+.
- Reuse the native Lite HDMI, antenna and apps controllers with shared models; keep multimedia construction, extra font variants, Full screensaver and activation jobs inactive in Lite. Both modes update from the unified release channel.
- Final standalone Lite transition release: explicit same-signer consent and a temporary, one-use private profile grant. Preserve existing profiles, retain portable antenna/EPG and remote preferences, and exclude account/PIN/pairing material.
- Add native Android 8/16 mode, HOME, PIN and cross-package migration regressions and fresh English screenshots.


- Full: per-profile Home library selection, optional external Continue watching, separate latest-film/latest-series rows, unwatched and top-rated films, and up to six pinned collections. Preserve existing layouts; additional feeds are opt-in, bounded to twelve titles and use the existing cancellable worker and artwork cache.
- Full: a short, cancellable vertical scroll and text transition between Home rows. Respect reduced motion and retain row size choices and lazy poster loading.
- Both: integrated Vela voice search for assigned remote/keyboard buttons and the Search key. Show microphone readiness, partial text and an accent-coloured microphone halo and voice-responsive sidebar/panel glow in the existing theme; leave unassigned Google Assistant keys alone.
- Both: request microphone permission only on use, prefer an available on-device recognizer and otherwise use the installed speech service. Destroy the one-shot recognizer on completion, cancellation, profile change or leaving the foreground. No stored audio/transcript or background microphone service.

## 1.38.3-preview / Lite 0.17.2-preview — 2026-10-30

- Both: shorten the empty programme widget to Upcoming, keeping followed programme titles when available. Centre the weather symbol on the left and align temperature and location in one column on the right, within the existing widget bounds and cache budget.

## 1.38.2-preview / Lite 0.17.1-preview — 2026-10-30

- Both: show only the time in the clock widget, vertically centred in its existing card. Keep AM/PM on the same line when the 12-hour format is selected. No subtitle, extra worker or layout growth.
- Full includes the large-catalogue programme query fix from 1.38.1.

## 1.38.1-preview — 2026-10-30

- Full: resolve local channel aliases before programme rows in What to watch. Keep the indexed channel/start lookup with EPG offsets, encrypted payloads, genre filters, hidden channels and twenty-four-record pages. This avoids an expensive cross-product on large catalogues and older TV SQLite planners, without a new cache or worker.
- Lite remains at 0.17.0.

## 1.38.0-preview / Lite 0.17.0-preview — 2026-10-30

- Both: keep TV actions below the widget header and connect Up/Down navigation between the toolbar and widgets. What to watch now uses the TV canvas, direct time and genre filters, six programme cards per page, bounded 24-record batches and the shared programme sheet. Small thumbnails decode separately from large detail artwork, with one cancellable worker and no additional service. Lite omits the unused English asset catalogue; English continues to use the source fallback.

- Both: integrate automatic channel switching into a compact programme sheet, with grouped controls, an inline scheduled-time badge and stable remote focus. Toggle in place without restarting artwork requests or showing system toasts; keep the guide visible beneath one shared graphite treatment and omit empty artwork wells.

- Both: choose Switch when it starts on a future guide programme, cancel it from the same card, and change to its channel automatically while Vela is visible. Save at most sixteen one-shot choices per local profile, keep source/channel identity checks, skip missed starts and stop the single timer on pause. No service, alarm, new permission, URL storage, backup or sync of viewing plans.

- Both: native header widgets for clock, optional weather, date, next followed programme and a TV/HDMI/Apps shortcut. Select and order up to four in Appearance → Home widgets. Clock format, city search and per-profile portable choices stay in the integrated theme.
- Both: reuse the foreground minute tick and existing cached guide/weather data. Hidden weather closes its worker; no new service, alarm, permission, background widget provider or artwork cache. Lite includes only the shared weather client and neutral defaults.
- Full: keep the clock updating across integrated settings and tools, and fit update hints below the page title without overlapping the library badge.

## 1.37.1-preview — 2026-10-28

- Full: fix local live pause startup for redirected HLS sources. Follow bounded media redirects and resolve all relative media addresses against the final playlist location. Preserve account transport, normal TLS validation, disk limits and one-player behaviour.
- Add a native regression that fails with the published 1.37.0 buffer and passes with the fix. Check playlist, segment and initialisation URLs, redirect loops, invalid schemes and cleanup.
- Lite remains at 0.16.0; no change to antenna playback.

## 1.37.0-preview / Lite 0.16.0-preview — 2026-10-28

- Learn a remote button in Settings → System → Remote shortcuts, then choose separate short and long press actions.
- Open Home, Live TV, Apps, HDMI inputs, Search, voice search, Settings, Profiles or any installed app, in the existing Vela theme.
- Keep mappings private to each profile and portable in validated, credential-free backups. Previous Search, Voice and Profiles shortcuts are preserved.
- Retain the original short press when only a long press is customised. Repeated, cancelled and interrupted presses do not run duplicate actions; backgrounding clears the pending timer.
- This uses buttons Android delivers while Vela is on screen. Navigation, Back, power, volume and channel controls stay available. Firmware-reserved buttons and global remapping inside other apps are not promised.
- No new permission, accessibility service, background service or artwork cache. Available in all six menu languages.


## 1.36.0-preview / Lite 0.15.0-preview — 2026-10-28

- Full: compact the cinematic Home header and Continue watching cards. Keep original platform logos and remaining time inside the artwork, above progress, and expose Latest uploads on entry. Preserve remote row headings, artwork proportions, row-size choices and bounded image loading.

- Both: route the remote voice/search keys and Android search intents into the integrated search page. Keep one recognizer request, preserve its profile through Activity recreation, and deliver accepted speech only after foreground restoration. Cancelled keys/results do not execute commands.
- Both: assign an available remote button to Search by voice from Remote & diagnostics when firmware reserves the Google Assistant key. Keep shortcut capture, collision handling, backup and compatible preference sync in the existing theme.
- Both: separate search from explicit open commands, understand common app/channel/input phrases and spoken HDMI numbers, keep ambiguous choices in the themed results, and route Tonight/genre requests directly to programme discovery. Let the installed speech service choose its spoken language independently of Vela's menu language.
- Both: prioritise exact app/channel matches before partial names while keeping bounded, gap-free search pages. No resident microphone, new service, image cache or permission. Native tests use synthetic speech and an actual external Activity result round trip; physical Google Assistant interception remains device-dependent.
- Both: explicit app/input/channel/library voice requests only query the requested domain. Full searches existing M3U/Xtream records without refreshing a playlist, including category-only caches; SQLite/tuner search cancellation lets a new request replace an expensive guide query on the existing worker.
- Full: reopen channel Recent activity by its stored source and channel identity, rather than searching the first page of a display name. Reuse indexed M3U/Xtream records and the existing encrypted shortlist, preserving quality alternatives and hidden-channel rules.
- Lite: resolve the recent antenna identity against the full tuner catalogue before applying visibility, so hiding an identically named channel cannot break the shortcut. Enter the TV destination when reopening playback.
- Both: discard asynchronous shortcut results after leaving the originating screen, changing profile or losing the foreground. Native remote regressions reproduce the failures with the published APKs and pass with these corrections. No new cache or background service.

## Vela 1.35.1 Preview and Vela Lite 0.14.1 Preview · 2026-10-27

- Move the profile-local Recent activity action into Apps, beside drawer controls, with original app/device artwork and explicit remote focus routes. Keep Home artwork and Lite HDMI cards clear.
- Reserve the in-app Vela mark for the main Settings header; use a neutral profile affordance in Lite and text-only personalisation headers.
- Refine the existing native vector identity and matching Android launcher assets without adding raster artwork, a cache or background work.

- Both: continuous full-screen backgrounds when opening navigation and switching menu tabs. Full reuses the existing artwork and fades outside the shrinking foreground; integrated settings, live TV and tools share a stationary graphite canvas. Lite keeps its existing black/graphite choice. No extra bitmap, image cache, service or decoder.
- Native regression coverage checks stationary background bounds, settings tabs, embedded tools, twelve language/text-size combinations per edition and cinematic artwork with expanded navigation.
- Lite: return to the current guide after asynchronous metadata rebuilds, discarding detached focus references and retaining the selected channel.
- Full: enter the first channel when paging down in the guide and the last when paging up, including catalog boundaries. Preserve selection when programme data arrives. Shared timeline data resets stale row positions when channel identities change.
- Full: make What to watch reachable by explicit remote focus routes from the source/organise controls and return to Guide or Watch now according to the chosen view.
- Both: explicit EPG corrections select the matching guide logo before provider/native artwork; decoded caches include the chosen EPG identity. Restoring automatic matching restores the original logo. Existing image budgets remain unchanged.
- Product Design audit follow-up: keep the entire inactive screen visible beside expanded navigation with one shared right-anchored View transform. Restore full size and position on return; apply immediately with reduced motion. No extra bitmap, image load or background runtime.
- Lite: Back closes expanded navigation and restores the selected device/control; Right on the active section reuses that content instead of rebuilding it. Clear stale focus references when content is replaced.
- Both: improve HDMI port-label contrast on bright original brand colours using the existing text node and one passive background.
- Both: use the accurate Recent activity caption for app/input/channel reopening, with six translations.
- Both: give expanded navigation an opaque graphite surface independent of content artwork; remove Full's sidebar scene image and blur path. Share passive native chrome, readable labels, profile hit areas and distinct active/focus states.
- Full: separate pinned apps, bound original brand-icon decoding to 96 pixels, and position the information action after variable playback captions to prevent overlap.
- Native navigation checks cover six languages, normal/large text, D-pad return, opacity and bounded images. Full additionally verifies the expanded panel remains identical when actual movie artwork changes.
- Refine the shared graphite surfaces, hierarchy, remote focus and Home composition. Keep the sidebar and original provider/device artwork.
- Open visual customization directly from Appearance in Full. Quality and performance remain available from that page.
- Make Lite settings scrollable, remove overlapping appearance options, and retain the last focused option in settings and tools.
- Allow remote scrolling through complete synopses; keep native detail and broadcast actions in the existing shell.
- Align HDMI feature panels across editions, clarify focused app icons, reduce the TV discovery action and prevent the EPG time marker crossing programme text. Group next-programme information in a passive player card.

- Both: previewable Cinema, Compact and Minimal styles; consistent focus; richer programme information and original EPG artwork; expanded guide selection without moving channel rows.
- Full: native movie/series information, on-demand paged seasons and episodes, and account-bound Plex/Jellyfin collections pinned to Home with three-row/eight-title limits.
- Both: one profile-local last-activity card and a temporary performance lab with process memory, actual frame timings, first-draw timing and a local baseline. No monitoring service.
- Visible-only programme images, bounded transfers/decodes, cancelled work and an ephemeral 512 KiB artwork cache. Full optional XMLTV metadata has separate storage budgets; Lite retains its independent tuner runtime.
- Full: optional content frame-rate matching, with Off, Seamless only and Allow display switching in integrated TV settings and playback controls. Device-local policy; one existing engine, bounded timestamp inference and restoration on pause/exit.
- Direct HLS/TS/archive playback with the optional engine, same-signal system-player fallback, audio-only handling and normal archive completion.
- Both: reusable playback rows, fewer repeated preference/track operations, preserved focus and caption intent. Full additionally avoids unchanged surface layouts and empty-caption allocations; shared transport helpers remove duplicate branches.
- Native old/new control comparison: 164 versus 4 row views for forty option changes. This measures view creation, not whole-app RAM. Cadence, lifecycle, source fallback and existing playback/UI regressions are checked separately.

## Vela 1.30.1 Preview and Vela Lite 0.10.1 Preview · 2026-10-26

- Settings and personalisation tools remain inside the launcher sidebar, with quieter graphite rows, clearer typography, consistent focus and preserved positions during asynchronous work.
- Shared in-channel information: contrast-aware logo, clock, progress and next programme. Left reaches controls, Right opens channels, Back closes information before leaving. Introductory badges and information hide after playback is ready.
- Lite: prevent a focus-triggered category reset when opening or returning from the language picker; verified with the remote and all six languages.
- Cleaner channel drawer and playback controls, on-demand sleep timer, persistent Lite logos during metadata updates, and Android 8–9 gradient compatibility.
- Explicit Save/Cancel home editor, own Watch later, settings PIN and guest space, Android voice search and a compatibility assistant in both editions.
- Full: independent Plex/Jellyfin accounts per profile, optional combined libraries, bounded merged Home rows and account-bound Watch later resolution.
- Trusted EPG event identifiers for cross-channel grouping; similar programme titles alone do not identify one event.
- Native layout, focus, lifecycle, playback, profile and bounded-storage tests; refreshed English Full/Lite galleries and six-language previews. No new service or background decoder.

## Vela 1.28.1 Preview and Vela Lite 0.8.1 Preview · 2026-10-26

- Add complete French, German and Italian catalogues alongside English, Spanish and Portuguese in both editions. All six languages support explicit selection and Android system-language detection.
- Locale-aware dates and metadata requests, translated mobile setup and legacy weather/recommendation labels. Content titles and connected accounts retain their source text.
- The picker fits all seven choices, including System language. One bounded catalogue remains loaded; no translation network service runs in Vela.
- Native six-language settings and recreation tests, catalogue/placeholder checks, and refreshed selector screenshots in both galleries.

## Vela 1.28.0 Preview and Vela Lite 0.8.0 Preview · 2026-10-26

- Complete English, Spanish and Portuguese menus in both editions, including shared tools, guide actions, playback controls and local phone setup. Choose an installation language or follow Android; unsupported system languages fall back to English. Provider content keeps its original text.
- A bounded catalogue for the selected language only, an atomic cross-process language record and immediate Activity recreation when the choice changes. No translation service or permanent worker.
- English project pages, installation, feature, privacy and development guides. Refreshed native 4K English galleries with demonstration data and per-image provenance.
- Includes the shared tools and playback work prepared in 1.26/1.27 and Lite 0.6/0.7 below. Those local preparations were not separate public releases.
- Localisation parity and placeholder checks, real language selection and recreation tests for both APKs, and existing library, update, guide and package checks.

## Vela 1.27.0 / Lite 0.7.0 · local development · 2026-10-26

- Home row order, visibility and size in Full; HDMI/TV/apps sections in Lite. App folders and drawer ordering with original icons.
- Temporary ten-minute local HTTPS phone setup for apps, folders, favourites and reading size; Full also supports sidebar and M3U/Xtream connection setup.
- Explicitly paired preference sync on opening Vela, certificate pinning and local-conflict review. Accounts, provider URLs and history are excluded.
- Manual EPG identity/time corrections with search and preview. Discovery covers Now, Tonight and the next 48 hours using actual source categories and paged queries.
- Themed audio, supported subtitle, aspect and signal controls. Native antenna pause is offered only when the manufacturer declares support.
- Optional Full HLS pause/rewind: one Media3 decoder and private disk ring bounded to 15 minutes/256 MiB, cleared on exit. Unsupported or protected streams retain direct playback.
- Past programmes only for M3U/Xtream sources advertising a compatible archive; up to 24 previous guide hours. Lite has neither IPTV nor Media3.
- Cancellable sessions, sockets and jobs; native HTTPS, origin checks, sync/conflicts, SQL, real pause/seek and synthetic archive playback tests.

## Vela 1.26.0 / Lite 0.6.0 · local development · 2026-10-25

- Shared Your Vela tools: unified search, backups, reading controls, programme follows, profiles and remote diagnostics in the existing theme and Activity.
- Reviewed local JSON import/export, validation, recovery and undo. Accounts, tokens, passwords, provider URLs and history are excluded.
- Portable local Xtream organisation and stable antenna identity recovery after a rescan; ambiguous matches are not guessed.
- Paged search across apps, HDMI, channels and guide; Full adds Plex/Jellyfin. Text size, guide density, margins and contrast controls.
- Up to six local profiles with separate layouts and organisation. Device accounts remain shared.
- Programme follows and optional approximate reminders, local remote diagnostics and configurable shortcuts. No permanent service.

## Vela 1.25.2 / Lite 0.5.1 · 2026-10-25

- Channel organisation adopts Vela's graphite settings theme, category icons, rounded rows and configured accent.
- Guide underlay is hidden while editing; stable focus, recycled rows, clean cancellation and resource release in both editions.

## Vela 1.25.1 · 2026-10-25

- Source and Organise controls stay inside the category header without overlapping the guide date.

## Vela 1.25.0 / Lite 0.5.0 · 2026-10-25

- Reorder favourites with OK and arrows; hide and restore channels/categories without changing the source or tuner database.
- Recent successful channels, bounded to twelve per source, plus Previous channel. Hidden entries are skipped.
- Encrypted M3U/Xtream history; filtering before counting/pagination; stable antenna identity handling after rescanning.

## Vela 1.24.0 / Lite 0.4.0 · 2026-10-25

- Hold OK for favourites throughout guide and playback without also triggering the short action.
- List/Favourites drawer, stable focus on refresh/removal, browsing without retuning, direct CH+/CH− and return to the selected channel.
- Bounded pages, one player and native 720p/4K drawer geometry checks.

## Vela 1.23.1 / Lite 0.3.3 · 2026-10-25

- Next programme card beside current playback information, timed from the guide and hidden when unavailable.
- Bounded current/next retention, long-programme lookup and responsive controls.

## Lite 0.3.4 · 2026-10-25

- Public repository fallback feed when GitHub's unauthenticated release API is rate-limited. Size, SHA-256, package and signature checks remain required.

## Vela 1.23.0 / Lite 0.3.2 · 2026-10-25

- Categorised graphite settings, integrated update panel, stable focus and state restoration.
- Lite compatibility for Android 9/10 package managers that omit SigningInfo for downloaded APKs; exact signature, integrity and package validation retained.

## Lite 0.3.1 · 2026-10-25

- First public Lite release: HDMI device profiles, native antenna guide with optional dobleM schedules, favourites, apps and integrated settings.
- Automatic Lite update checks/downloads, permission-return continuation and installed APK cleanup.

## Vela 1.22.0 · 2026-10-24

- Shared graphite palette and accent across Home, settings, TV and inputs. HDMI device icons use brand colours as local vectors.
- Event-driven physical/CEC port grouping, connected-device detection and optional manual device profiles.
- OK launches pinned apps; Right keeps shared YouTube rows in Vela. Settings, accounts, apps, inputs and sources mount in Home with bounded navigation history.
- Private forms block screenshots; system permissions, installer, OEM settings and recovery remain Android-owned surfaces.

## Vela 1.21.0 · 2026-10-22

- Live TV inside Home with shared sidebar, clock and weather; TV immediately follows Home.
- Guide is the migration default; Guide/Channels choice moves to Settings. Six enlarged rows, left categories, bottom time controls, logo, progress and remaining time.
- Full-width channel cards with vertical category switching. Playback information auto-hides; Right opens paged channels without replacing the decoder.

## Vela 1.20.2 / 1.20.1 / 1.20.0 · 2026-10-22

- Themed Plex linking/server selection. Downloaded update APK cleanup after installation.
- Reliable Android installer hand-off and continuation after unknown-source permission, including process recreation, expiry and retry checks.

## Vela 1.19.1 · 2026-10-22

- Live sources narrowed to M3U, Xtream and antenna; obsolete third-party web and TiviMate integration removed.
- Indexed grouping of same-channel qualities, bounded disk guide, original channel logos and public dobleM XMLTV default.

## Vela 1.16.0 · 2026-10-21

- Native Xtream connection, encrypted credentials/favourites, provider categories and paged guide/playback. Synthetic API and video regression tests.
- Horizontal guide development and bounded remote navigation.

## Vela 1.15.1 / 1.15.0 · 2026-10-21

- Resume installation after Android permission with an expiring request tied to the exact verified APK.
- Server-backed Plex/Jellyfin filters and ordering, independent Movies/Series selection, newest additions first and paged filter values.

## Vela 1.14.0 / 1.13.0 · 2026-10-21

- Hold OK to mark a Plex/Jellyfin film or episode watched; server-backed next episodes and refreshed Continue watching.
- Jellyfin connection, original artwork, resume progress, latest additions, paged libraries and official Android TV app hand-off.

## Vela 1.12.1 / 1.12.0 · 2026-10-21

- Automatic update checks/downloads default on while respecting existing opt-outs. Daily foreground checks, bounded download and SHA-256/package/version/signature validation.
- Android installation confirmation remains required. Home scrolling keeps selected content and headings visible.

## Vela 1.11.0 · 2026-10-21

- Uniform widescreen Continue watching, separate recently added posters, row-edge fades and seamless full-height artwork background.

## Vela 1.10.4 / 1.10.3 · 2026-10-21

- Live programme progress and remaining duration on channel cards, refreshed by the existing minute timer. Durations above an hour use hours and minutes.

## Vela 1.10.2 / 1.10.1 · 2026-10-21

- Original adaptive app icons, large rounded square layout, sidebar favourites first and recycled cells with bounded local icon caches.

## Native 4K gallery / Vela 1.10.0 · 2026-10-21

- Authorised original movie artwork with anonymised demonstration state, Movies and Series previews, six accent colours, three poster sizes and background intensity controls.
- Earlier features include paged Plex libraries, separate Continue watching, bounded artwork caches, configurable sidebar, clock/weather, two screensaver styles and live TV favourites/guide. Historical web integrations were subsequently removed.
