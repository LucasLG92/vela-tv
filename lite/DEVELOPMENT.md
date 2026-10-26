# Vela Lite: development and compatibility

Package `tv.vela.launcher.lite`, Android 8.0/API 26+. Lite can coexist with Full Vela. It deliberately excludes Plex, Jellyfin, M3U, Xtream, Media3 and the Full screensaver.

## Native television and HDMI

The firmware must publish its ports, tuner and listings through Android APIs. Opening HDMI/native TV uses the manufacturer's input handling. Wake, sound, remote, HDMI-CEC and tuning remain OEM responsibilities.

Guide draws a three-hour window in one Canvas view, normally six channels, or twelve channel cards in the alternative view. Larger reading/density choices can reduce visible rows. The native player reuses one TvView session and a bounded six-row channel drawer.

Lite reads, but never rewrites, the TV channel database. Provider observers run while the guide is visible. After retuning, new Android channel IDs replace old URIs; stable DVB identity or an unambiguous name fallback preserves preferences. Locked channels remain blocked, and the original TV is offered when integrated playback is unavailable.

Hold OK toggles favourites in the guide, cards, drawer or current programme. Organise shares Full Vela's graphite panel and bounded preferences: up to 128 favourite positions, 256 hidden channels, 512 hidden categories and twelve recent successful channels. The same session supports previous-channel navigation. These preferences affect Lite only.

## Programme guide

The public dobleM XMLTV supplements already tuned channels. It is streamed and indexed on disk, retaining up to 48 future hours only for tuned or manually mapped identities. It does not add IPTV channels, subscriptions or provider credentials.

The guide cache is capped at 8 MiB; disk logos at 2 MiB. Guide/app decoded icon caches are 512 KiB each with small images and are released on exit. Programme reads are bounded, the UI appears before slow logo downloads, and progress/clock update once a minute. Failed/truncated/cancelled imports preserve the previous index. Native schedule data remains the offline fallback.

Manual EPG mapping/time correction, source-provided genres and programme follow/alerts use shared bounded tools. Antenna pause is offered only if the native tuner advertises time-shift capability. Lite adds no independent recording or rewind engine.

## Shared tools and language

Lite shares layout/folders, local profiles, reading, remote tools, validated backups and compatible preference sync. HDMI/TV/Apps navigation can be reordered/hidden, with Settings retained. Phone setup is a foreground local HTTPS session; Full-only source login actions are excluded.

English, Spanish and Portuguese catalogues are shared with Full Vela. The selected catalogue alone is loaded; English uses source text. Language persists per installation through a tiny atomic record, not a service or observer. It does not change the system or provider content.

## Updates

Visible opening can trigger a daily check and automatic download; there is no resident update service or polling alarm. Lite filters out Full releases and verifies HTTPS origin, size, SHA-256, package, version, minimum Android and signer. A requested install survives Android's permission flow and resumes on return. Android confirms installation; the next opening cleans the installed APK.

Publish a new matching version with a tag such as `lite-v0.8.0-preview` and an uploaded `Vela-Lite-0.8.0-preview.apk` asset with GitHub's SHA-256 digest. Then update the auxiliary feed from the published release metadata:

```sh
python lite/tools/update_feed.py --release release.json --apk Vela-Lite-0.8.0-preview.apk
```

The tool rejects draft releases and mismatched local size/hash. Commit `lite/updates.json` only after the actual release/asset is verified. Never publish signing keys.

## Permissions and lifecycle

Lite declares TV listings, Internet, network status, package installation and the notification/boot permissions used for optional cached programme alerts. It asks for neither location, accessibility nor overlays. There are no services. One private reminder receiver handles cached alerts/reboot/package replacement without downloading; one private provider grants the installer temporary read access to one APK.

Views, queries, observers, workers, icons and tuner sessions release on section change/pause. Android may retain a launcher process for fast return; Lite does not claim zero RAM use.

## Build and test

From the repository root with JDK 17 and Android SDK platform 34:

```powershell
./lite/build.ps1 -SdkPath '<Android SDK>' -JdkPath '<JDK 17>'
python lite/tests/check_package.py
python lite/tests/run_device.py --serial '<owned-emulator>' --adb-port 5038 --test integration
python lite/tests/run_device.py --serial '<owned-emulator>' --adb-port 5038 --test antenna
python lite/tests/run_device.py --serial '<owned-emulator>' --adb-port 5038 --test xmltv
python tests/run_localization.py --serial '<owned-emulator>' --adb-port 5038 --lite
```

Integration tests simulate hardware metadata in an owned emulator and intercept external intents. They do not prove physical Xiaomi reception or alter a user's tuning database. Shared workspace/expansion suites cover preferences, local HTTPS/certificate pins, conflict handling and screen lifecycle. [Full build/test guide](../guide/BUILD.md).
