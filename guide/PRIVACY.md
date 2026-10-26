# Privacy

Vela starts with neutral settings and bundles no personal accounts, servers or location. Configuration stays in Android's private app storage, with automatic Android backup disabled. IPTV connections and cached URLs receive additional device-key encryption.

| Feature | Data and destination |
| --- | --- |
| Plex | Plex receives device authorisation. Your selected server receives library/artwork requests with its token. The session stays local and can be disconnected. |
| Jellyfin | Your chosen server receives Quick Connect authorisation or the login request. The password is not saved; token, user and server address stay private on-device. Requests do not forward authorisation through redirects. Public servers require valid HTTPS; local HTTP is unencrypted. Disconnect attempts server-side revocation and removes Vela's local session. |
| Library filters | Title queries, filters and sorting go only to the authorised library server. These are read-only, bounded queries. Selection is temporary and is not published. |
| Mark as watched | Only the explicit menu action writes the selected film/episode's watched state to the connected Plex/Jellyfin user. Holding OK, cancelling or browsing artwork does not write history. Other apps' history is not modified. |
| Avatar | Plex profile artwork is fetched without forwarding its token to unrelated image hosts. Jellyfin profile artwork uses the selected server/token without following redirects. |
| Other apps / Continue watching | With TV permission, Vela reads titles, images, launch links and progress published through Android TV Watch Next. It does not import private sessions or passwords. |
| Weather | Open-Meteo receives rounded approximate coordinates, or city-search text. No precise/background location permission is requested. Weather location is not included in preference backups/sync. |
| M3U / Xtream | Your configured server receives account/list, guide, logo and playback requests. Account credentials and private source URLs stay device-key encrypted; catalogue names/categories are indexed locally while sensitive payload URLs are encrypted. Protected forms exclude credentials from screenshots and saved fields. |
| External XMLTV | The default public dobleM guide is requested from GitHub without IPTV account data attached. A custom HTTPS guide is requested only from its host; private guide URLs are encrypted locally. |
| Antenna / HDMI | Vela reads the tuner/input information the manufacturer exposes. Native tuning and input switching stay local. Vela does not automatically retune or write the manufacturer's channel database, and does not identify HDMI devices through network scans. |
| Updates | Visible checks query public GitHub releases without a user login or Vela account data. GitHub/CDN receives the connection, including IP address. APKs are held in private cache and checked for hash, identity, version and signer. The installer gets temporary read access to one APK. Android requires confirmation. Installed updater files are cleaned. Automatic checks/downloads can be disabled. |
| Screensaver | Library images come from your selected server; aerials come from their published authors/hosts. Video files and personal library assets are not bundled in the production APK. |

## Local tools

Layouts, folders, reading, remote shortcuts, followed programmes and channel corrections are private per-profile preferences. Folder records contain names and package identifiers, not copied apps or image libraries. What to watch reads existing EPG metadata in bounded pages rather than uploading interests to a recommendation service.

Full profiles link independent Plex/Jellyfin accounts and retain account scope during in-flight requests. The default profile preserves its existing sessions; deleting another profile removes its local library sessions. IPTV sources remain installation-scoped. Profiles and the settings PIN are not content-rating or Android parental-control boundaries. Channel history remains source/account scoped; M3U/Xtream recent identifiers are encrypted and excluded from exports and sync.

Backups and peer sync contain validated preferences only. They exclude accounts, passwords, tokens, provider URLs, location, artwork, playback history, pairing secrets and APKs. A local recovery copy supports Undo. Unknown/reserved fields and excessive values are rejected. Review a backup before sharing it: folder names and other user-authored preference labels are still your data.

The app language is an installation-local choice and does not change Android or other apps. A tiny atomic private record keeps Home and screensaver processes consistent. Only the selected catalogue is loaded; no language service is contacted.

Watch later stores up to 64 small account-bound references per profile, with no credentials, copied images or media. Guest mode uses separate local preferences. A salted, rate-limited settings PIN stays private and is excluded from backup/sync. Voice search delegates to the installed Android recognizer, which handles its own processing and permissions; Vela adds no microphone permission or resident listener.

## Phone setup and TV pairing

Phone setup runs a temporary HTTPS server on the local network, protected by a random session link, a ten-minute limit and foreground lifetime. Android Keystore protects the TLS key. The certificate is local/self-signed, so the browser may require explicit acceptance; verify the address shown by the television.

The page loads no third-party scripts, fonts or QR services. Credentials entered for a Full source travel encrypted to that TV and are not echoed in responses or request logs. The session closes when its screen leaves the foreground.

Peer preference sync is explicit and checks a certificate fingerprint in the pairing link. The destination stores its link encrypted with AES-GCM. The source listens only while Vela is visible; the destination checks when Vela opens. Both-side conflicts keep local edits pending review. Unpairing or deleting a profile removes its private link.

## Optional playback buffering

Full Vela can buffer only the selected supported clear HLS stream in private disk storage, capped at 256 MiB, 15 minutes and 240 segments. The decoder receives a random loopback endpoint whose local manifest contains no provider URLs. Leaving/changing the signal removes its files; a later buffer session cleans abandoned remnants. No recording service or permanent downloader is added.

Past programmes are requested only from the configured M3U/Xtream provider using its existing authorisation and advertised archive support. Lite contains no IPTV network buffer or archive client.

## Notifications and reports

Programme alerts are opt-in, require Android notification permission where applicable and use one approximate alarm for cached schedule data. The receiver does not download or tune. Local device reports exclude addresses, account names, tokens and credentials; review any data you choose to share.

Vela adds no analytics or advertising of its own. External apps, servers, media hosts, weather and GitHub apply their own privacy policies. Public documentation screenshots use demonstration profiles/progress, a labelled demonstration weather city and authorised or openly licensed artwork, without personal account sessions.

[Security reporting](../SECURITY.md) · [Credits](../NOTICE.md)
