# Vela TV

A native, remote-friendly launcher for Android TV and Google TV, Android 11+. It offers real Plex artwork, a paged library, separate Continue Watching, configurable app shortcuts, live TV, screensavers and compact clock/weather.

Version **1.10.0 is a preview**. Appearance adds six accent colors, three poster sizes and three background intensities without continuous home-screen animation. Resetting appearance preserves accounts, apps and location.

Download the APK from [Releases](https://github.com/LucasLG92/vela-tv/releases/tag/v1.10.0-preview). Configure your own accounts and location on the device. The public APK contains no personal server, city, credentials or signing material. [Build instructions](BUILD.md) · [Screenshot gallery](../gallery/README.md).

Plex authorization uses its device code flow. YouTube and Prime sessions remain in their own apps; Vela does not claim private YouTube recommendations or Watch Later through its own OAuth integration. The integrated TV website player preserves each broadcaster's login, consent and playback requirements. Loading a page is not proof that its actual broadcast works on a TV.

The home screen uses bounded image caches and releases artwork on exit. WebView is loaded only for a selected web channel in a dedicated process that ends after closing. Total memory varies; there is no zero-RAM claim.

Screenshots are actual emulator captures using fictional demo metadata and openly licensed Blender film artwork. No personal Plex library, avatar, city or valid account credential was used. The space-video screensaver illustration uses NASA/JSC demo footage rather than redistributing community screensaver footage. See [credits](../gallery/CREDITS.md).

Code is MIT, derived from Free My TV with its copyright retained. Fonts, artwork, trademarks, catalogs and remote videos have their own licenses and terms: [notices](../NOTICE.md).
