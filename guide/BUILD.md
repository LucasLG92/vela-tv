# Build and test

Requirements: JDK 17+, Android SDK platform **34**, build-tools 34+ and platform-tools. The app uses Java 8, minSdk 30 and targetSdk 33. There is no Gradle project or external runtime library.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash build.sh
```

Windows PowerShell:

```powershell
.\build.ps1 -JdkPath 'C:\path\to\jdk-17' -SdkPath 'C:\path\to\Android\Sdk'
```

The normal build always reads `config.example.properties`, even when a private `config.properties` exists. It produces `Vela.apk`. Device accounts, server selection, apps and location are runtime preferences. Never distribute a build made with the legacy `--local-config` / `-UseLocalConfig` option.

The scripts create `vela.keystore` locally on the first build. Keep it privately: Android requires the same signing identity for updates. The local development password in the scripts is not a release secret and is not a substitute for protecting the keystore. CI uses its own disposable key and does not sign official releases or upload signing material.

## Checks

```sh
python tests/test_home_mode.py
python tests/test_plex_feed.py
python tests/test_jellyfin_feed.py
python tests/test_updates.py
python tools/audit_publication.py
```

Native device tests require an explicit serial and an installed APK:

```sh
python tests/test_artwork_device.py --serial <serial> --test appearance
python tests/test_artwork_device.py --serial <serial> --test personalization
python tests/test_artwork_device.py --serial <serial> --test live-tv
python tests/test_artwork_device.py --serial <serial> --test tv-web
```

`--adb-port` selects a separate ADB server for an emulator. `gallery` requires a clean emulator without a personal Plex session or location and uses openly licensed fixture images. It writes screenshots to the app's external files directory. The demo marker is deliberately non-functional; it never authorizes Plex.

Appearance tests exercise remote navigation, all poster sizes, clipping, original aspect ratio, chooser persistence, cache limits and reset isolation. TV tests distinguish loading a broadcaster page from decoding its actual broadcast. HTML5 fixture playback is not evidence that every broadcaster works on a physical TV.

This preview's appearance and publication gallery were tested on an Android 16 emulator. Earlier launcher features were exercised on an Android 11 TCL; that does not certify the entire preview build for that model or other manufacturers. Validate before changing a real device's preferred home.

La prueba de la cuadrícula se ejecuta con `python tests/test_artwork_device.py --serial <emulador> --adb-port <puerto> --test apps-grid`. Comprueba iconos, navegación, apertura de la app elegida y liberación de recursos.

## Update checks on a clean emulator

Build and install Vela with the same local key, then run:

```sh
python tests/build_update_fixtures.py
python tests/test_artwork_device.py --serial <emulator-serial> --adb-port <port> --test updates
python tests/test_artwork_device.py --serial <emulator-serial> --adb-port <port> --test update-permission
```

The fixture builder produces synthetic future APKs inside `out/`, including intentionally wrong signers and packages. Never distribute them. The native test refuses physical devices or a linked Plex account, checks signed archives, bounded/cancelled transfers, the read-only installer grant, daily checks and remote controls, then restores update preferences. It does not confirm a system installation. The native suite also checks persisted requests, activity recreation, expiry, APK identity, post-permission tampering, cancellation and duplicate launches. The `update-permission` host test uses a clean emulator in Spanish and the real Android Settings switch: it grants the permission, forces background process death while Settings is open, then checks the recreated process reaches the system install confirmation, denial, retry and cold Home/Settings entry. It never confirms the synthetic installation and restores update preferences and the previous app-op. The separate release validation for 1.12.0 completed an actual self-update through Android's installer on a clean emulator and verified retained preferences.

Official update releases must keep the existing signing key and a strictly increasing Android versionCode. Use a semantic tag such as `v1.16.0-preview`, the matching versionName `1.16.0-preview`, and the asset name `Vela-1.16.0-preview.apk`. Publish it as a release, mark previews accordingly, and verify GitHub's `sha256` asset digest. The updater ignores drafts, assets without that digest, unrelated paths, downgrades and mismatched signatures. CI artifacts use an ephemeral key and are not official updates.

## Jellyfin integration test

`tests/test_jellyfin_feed.py` validates synthetic metadata, ticks, pagination inputs, image IDs and URL rules without a server. The native test uses an explicitly selected clean emulator and an owned Jellyfin fixture with 30 movies, a series, artwork and seeded playback positions. Install the official Jellyfin Android TV app on that emulator to validate the item route. Forward the fixture port with `adb -s <emulator-serial> reverse tcp:<port> tcp:<port>`.

```sh
python tests/test_artwork_device.py --serial <emulator-serial> --adb-port <port> --test jellyfin --jellyfin-fixture /private/path/session.json
```

The private JSON contains `base` (HTTP loopback plus port), `username`, `password`, `token` and `userId` for the disposable fixture only. Never use a personal server or account. The native test refuses physical devices, non-loopback fixtures and linked personal libraries, restores app preferences and removes the runtime fixture file. It authorizes Quick Connect with that fixture token, exercises both login paths, pages, original images, source switching, screensaver and unlink. It intercepts the actual home action to check the item ID routed to the installed official app; this does not certify playback of every codec. No fixture videos, credentials or movie artwork are packaged in Vela.

## Watched actions on a disposable emulator

`--test watched` intercepts HTTPS inside the instrumentation process. It verifies the Plex per-item PUT request and token header, real remote long-press and release behavior, duplicate submission, errors, account changes, cache and next-episode replacement. It never contacts a Plex account. The runner refuses physical devices and stops the test app process afterwards so the fake transport cannot survive that test.

The Jellyfin fixture test now writes watched state to its disposable user and verifies advancement to the next episode using the real server. Reset the fixture progress before repeating it. Never run these mutation checks against a personal library. Both native tests preserve Vela preferences; no fixture credentials, videos or original movie art belong in the public APK.

## Catalog filter checks

The Plex feed script also runs CatalogFiltersTest: native descriptors, default recent order, safe routes, reserved query fields, AND/OR encoding and bounded value pages. `--test catalog-filters` uses a clean emulator and isolated Plex HTTPS transport to exercise actual remote controls, drafts, reset, global filters, independent movies/series, sorting, lazy metadata, recycled option rows and pause/release. The runner stops that process to remove its test transport.

The real Jellyfin fixture requires 30 movie entries split between years 2020/2025 and genres Action/Drama, plus a series and seeded resume positions. The integration test verifies actual filter metadata (including name-based fallback), combined year/genre, OR between genres, empty favorites, native sort, recent defaults and independent category state. Prepare those synthetic fixture metadata before running it; do not use a personal library. Captures are native 4K with original authorized artwork and synthetic fixture metadata. No fixtures belong in the APK.

## Xtream live-TV integration checks

Build/install Vela, then run on a clean emulator without personal Plex, Jellyfin or IPTV accounts:

```sh
python tests/test_artwork_device.py --serial <emulator-serial> --adb-port <port> --test xtream --xtream-fixture /private/path/session.json
```

The private fixture JSON contains `base` (HTTP loopback plus port), `username` and `password` for an owned disposable API. Never pass a personal provider. Forward its port with ADB reverse. The fixture needs live categories 1/2/3 with 30/10001/0 channels, IDs 101 onwards in category 1, Base64 now/next listings ending about 135 minutes ahead, a small PNG logo and synthetic 320px HLS video. No provider credentials, recordings or fixture videos belong in Vela or the public repository.

The test exercises protected native login, device-key encryption, failed auth and redirects, bounded pages and large/empty/last categories, favorites, logo decoding, guide timing, actual HLS decoding, cross-page channel zapping, pause/release and unlink. It restores preferences and the runner removes the private fixture file. TiviMate's own session, history and private storage are never inspected. The ordinary live-TV and progress suites cover the public catalog and existing channel cards.
