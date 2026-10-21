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
```

The fixture builder produces synthetic future APKs inside `out/`, including intentionally wrong signers and packages. Never distribute them. The native test refuses physical devices or a linked Plex account, checks signed archives, bounded/cancelled transfers, the read-only installer grant, daily checks and remote controls, then restores update preferences. It does not confirm a system installation. The separate release validation for 1.12.0 completed an actual self-update through Android's installer on a clean emulator and verified retained preferences.

Official update releases must keep the existing signing key and a strictly increasing Android versionCode. Use a semantic tag such as `v1.13.0-preview`, the matching versionName `1.13.0-preview`, and the asset name `Vela-1.13.0-preview.apk`. Publish it as a release, mark previews accordingly, and verify GitHub's `sha256` asset digest. The updater ignores drafts, assets without that digest, unrelated paths, downgrades and mismatched signatures. CI artifacts use an ephemeral key and are not official updates.
