# Vela

## Your TV. Your way.

Your movies, series, apps and live TV, brought together in a home screen made for your remote. Connect Plex or Jellyfin, pick your favorites and make Vela feel like yours.

**[Download Vela](https://github.com/LucasLG92/vela-tv/releases/tag/v1.25.2-preview)** · [52 screenshots in 4K](../gallery/README.md) · [Installation](INSTALL.es.md)

![Vela home screen](../gallery/01-inicio.webp)

- **Connect with ease.** Link Plex with a device code and pick your server in a native screen that matches Vela. The selected server is easy to recognize and longer lists scroll with your remote.
- **Put your library first.** Original Plex posters and backdrops, a paged library, panoramic Continue watching cards with remaining time, and Recently added posters underneath.
- **Bring your own Jellyfin server.** Quick Connect or username/password, original artwork, exact resume progress, recent additions and a paged library. Choose Plex or Jellyfin without unlinking the other account. The official Jellyfin Android TV app opens item details and manages playback; sign into the same server and user there.
- **Recognize every app.** Large original icons in a spacious square grid, with your favorites first in sidebar order. Explore everything with your remote.
- **Make it yours.** Six accent colors, three poster sizes, backdrop choices and a sidebar you can reorder with a long press of OK.
- **Find something live.** Browse channels, see what's on and how much time is left in each program. Hold OK to add or remove a favorite. While watching, arrows open the channel list, OK confirms, CH+/CH− switch directly, and Back closes the panel. Switch between List and Favorites without stopping the current stream. Add your own HTTPS M3U playlist.
- **Bring your IPTV.** Choose M3U, Xtream or the antenna tuner on compatible televisions. Original channel logos, search, favorites, now/next listings and native playback. Indexed local catalogs keep navigation light, with twelve channel records per page. [Setup and limits](IPTV.es.md).
- **Stay up to date.** Daily GitHub release checks when Vela opens and automatic downloads enabled by default, with installation confirmed by you. Both options can be disabled; existing explicit choices are kept. Older versions need one manual update to add this feature.
- **Enjoy the pause.** Moving landscapes or Plex backdrops with a clock and weather.

Vela loads artwork when needed and releases resources when you leave. The home screen has no continuous video or animation loop.

For Android TV and Google TV running Android 11 or later. **1.25.2 Preview.** Streaming availability depends on each service and device; see [compatibility](COMPATIBILITY.es.md). YouTube and Prime Video manage sign-in in their own apps. Vela can also display Continue watching items that installed apps publish through Android TV Watch Next; availability depends on the app and device. Missing playback times are never invented.

[Privacy](PRIVACY.es.md) · [Credits and media information](../NOTICE.md) · [Build](BUILD.md) · [MIT license](../LICENSE)

Hold OK on Continue watching to mark the selected movie or episode as watched in the linked Plex or Jellyfin account. Upcoming available episodes come from Plex Continue Watching / On Deck or Jellyfin Next Up. Other apps may share next episodes through Android TV; their watched state is managed in their own app.

Movies and Series now default to latest server additions. Filter and sort the entire server catalog with independent per-library query state, real server values and paged, recycled option lists. Plex descriptors and Jellyfin equivalents are supported; Plex's nested advanced-condition editor remains in its original app.

## Live TV, your way

Live TV sits directly below Home in Vela's shared sidebar, keeping your clock and weather in view. **Guide is the default**: six enlarged rows, categories on the left, a three-hour timeline and Now controls underneath. The selected programme footer includes its channel logo, schedule, remaining time and progress. Choose Guide or Channels only in **Settings → Live TV**. Channels uses the full width for large logos; Up/Down changes the channel list.

![Live TV integrated into Vela](../gallery/40-guia-horizontal.webp)

Press **Right during playback** to browse channels, programme titles and remaining times while the video keeps playing. OK changes channel; Back or Left closes the drawer. Introductory live/quality badges disappear after four seconds and programme information after six; OK brings the information back. One decoder handles playback, with indexed twelve-record pages and recycled drawer rows. [Live TV setup](IPTV.es.md).

![Browse channels without leaving playback](../gallery/47-canales-durante-directo.webp)

If an IPTV signal fails, Vela tries alternatives for the same channel, including a lower quality when available. Your preferred quality stays saved. Startup and playback stalls have a 20-second grace period; explicit pauses are respected. If every alternative fails, press OK to retry. No extra player or background video preview is created.

After Android confirms an update is installed, Vela removes its private downloaded APK. Pending or cancelled installations keep their APK for retry, and active downloads are protected. Cleanup also works offline with automatic checks disabled.

## Integrated settings and HDMI inputs

Settings, accounts, updates, TV sources, apps and inputs share Home and its graphite theme. Pinned apps open with OK; Right on YouTube keeps its available content in Vela. HDMI inputs use native brand vectors and group physical ports with CEC devices. Device identities appear only when Android reports an active connection; otherwise the port stays HDMI 1, HDMI 2, and so on. Hold OK to assign a profile when the TV or an adapter does not identify it. Hardware information depends on the television; arbitrary console generations cannot be reliably fingerprinted.
