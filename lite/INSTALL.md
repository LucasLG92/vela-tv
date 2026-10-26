# Install Vela Lite

Vela Lite needs an Android TV/Google TV television running Android 8.0 or later. Install it on the television itself when another HDMI device handles your content; installing it on that box cannot expose the television's own ports or antenna.

1. Download the Lite APK from the [official release](https://github.com/LucasLG92/vela-tv/releases/tag/lite-v0.8.1-preview) and transfer it with your usual APK installation method.
2. Open it and confirm installation. Android may ask to allow the file manager to install apps.
3. Open Vela Lite. In Settings → System → Home screen, select it as the default when your firmware allows.
4. Open Television and allow access to the TV guide. Channels must already be tuned in the manufacturer's television app.
5. Online programme listings and automatic updates are enabled by default; change them in Settings if preferred.

Choose English, Spanish, Portuguese, French, German, Italian or System language in Settings → Appearance → Language. This changes Vela Lite only.

## Remote

| Action | Control |
| --- | --- |
| Browse inputs/apps/programmes | Arrows |
| Open or choose | OK |
| Identify an HDMI device | Hold OK on the input |
| Channel actions and favourites | Hold OK |
| Explore channels during playback | Right, Up or Down |
| Show/hide programme information | OK |
| Audio/subtitles/picture controls | Left while information is visible, or MENU |
| Close overlay/information, then return | Back |

## Updates

Settings → Updates can check manually, change automatic check/download preferences and install a prepared APK. If Android requests installation permission for Vela Lite, grant it and return to resume. Confirm the installation when asked.

Lite receives only Lite releases. Keep the package and signing identity to retain preferences; do not uninstall or clear app data. Installed updater APKs are cleaned on reopening.

## Missing inputs or programmes

Ports, tuning and permissions depend on the manufacturer. Tune channels in the original TV app; Lite rereads them when you return. dobleM supplements recognised tuned channels and native schedule data remains the offline fallback. You can correct guide identities/time offsets in Your Vela.

Some firmware prioritises the factory launcher. Keep a tested recovery path before any manual ADB changes. Do not disable TV, audio, remote, HDMI or firmware-update services to install a launcher.

[Vela Lite](README.md) · [Development/compatibility](DEVELOPMENT.md) · [Privacy](../guide/PRIVACY.md)
