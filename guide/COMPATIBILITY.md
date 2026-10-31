# Compatibility and limits

The unified Vela APK supports Android TV/Google TV from Android 8.0. Choose Lite on Android 8–10; Full requires Android 11+. Televisions with another operating system need an external Android TV device for Full Vela; installing Lite on a box cannot expose the screen's own antenna or HDMI ports.

## Libraries and service apps

Plex authorises a device code and supplies the selected server's titles, artwork, libraries, latest additions and Continue Watching/On Deck. Vela opens content in the Plex app. Watched changes affect only the selected film/episode and connected user.

Jellyfin supports Quick Connect or username/password, including URL prefixes. Remote HTTPS requires a certificate trusted by Android; only local-network HTTP is accepted. Resume and Next Up supply watch progress and available pending episodes. Install Jellyfin for Android TV with the same server/user to open title details. Vela passes the item ID, never its token.

Only one personal library is active at once, while both authorisations can be kept. Home rows retain at most twelve titles; the catalogue uses pages of up to 24. Series represented by recent seasons use the parent series artwork. Missing durations or positions are not invented.

YouTube, Prime Video and other apps manage their own accounts. Shared Android TV rows and Watch Next are best-effort, permission-dependent sources. They do not guarantee access to private recommendations, subscriptions or Watch Later, and Vela does not implement a YouTube OAuth account/history integration.

Library filters use the server's own metadata/descriptors and bounded option pages. Nested Plex query groups remain in its original editor. [Filter details](LIBRARY-FILTERS.md).

## Live TV

M3U/Xtream use direct streams and Android-supported codecs. Clear compatible HLS can opt into the disk-backed Media3 pause path. MPEG-TS, audio/subtitle tracks and archive availability depend on the signal/provider. Browser-only streams or extraction pages are not included.

Antenna depends on the manufacturer's TV input service, shared channel database, permissions, tuning and physical reception. Locked channels remain blocked. A saved channel list is not proof of a signal. Native pause is shown only when the tuner advertises that capability.

Same-channel fallback does not assume that different countries, regions or +1 channels are interchangeable. Past programmes require an explicitly advertised compatible archive; unsupported templates are refused. [Live TV guide](IPTV.md).

## HDMI and Home

HDMI/CEC detection is limited to what Android publishes. Inactive/unknown ports retain their HDMI label. Adapters may hide the actual console model. A manual profile changes a label/icon, not connection state or signal routing.

Default Home selection, dedicated remote buttons, wake and native settings vary by manufacturer. Public builds do not disable packages. Test recovery to Original before relying on Vela as your permanent launcher.

## Memory and lifecycle

Home artwork cache is bounded at 6 MiB, or 3 MiB in light mode. App icons use bounded local caches and scaled images; visible views also retain their own references. These figures are not total process RAM. Closing a panel releases its grid, loader and cached/visible images.

The guide draws programmes in a single canvas and reads a bounded visible block from disk. Large text/density settings can reduce the normal six visible rows. Playback uses one decoder or tuner session and releases it on exit.

Optional HLS history is stored on disk, capped at 15 minutes, 256 MiB and 240 segments. Media3's 4 MiB queue target does not cap codecs, graphics or total process memory. Lite contains no Media3 or IPTV/library clients and declares no background service.

The selected language catalogue alone is loaded. English uses source strings. Phone sessions and preference sync stop outside their intended foreground lifecycle; alerts use one cached approximate system alarm rather than a resident guide service.

## Verification scope

The current language, configuration, privacy, native UI, lifecycle and playback regressions use an owned Android 16 emulator with demonstration data, public feeds or synthetic local streams. Actual language selection and Activity recreation are tested in both editions. No personal IPTV account is required for mutation tests.

Earlier designs were also exercised on physical Android televisions, but that does not certify every firmware, provider or new version. Testing on the destination TV is still required for its Home button, HDMI, tuner picture/sound and native time-shift support.

[Install and recovery](INSTALL.md) · [Privacy](PRIVACY.md) · [Build and tests](BUILD.md)
