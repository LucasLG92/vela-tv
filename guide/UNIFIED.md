# One Vela, two modes

**Vela uses one APK.** On first launch, choose Full or Lite and pick a Home layout. Change modes later in **Settings → System → Launcher mode**. Both modes offer Television and HDMI and apps. Full also offers Library.

| | Full | Lite |
| --- | --- | --- |
| Android version | Android 11+ | Android 8.0+ |
| HDMI and installed apps | Yes | Yes |
| Antenna television and programme guide | Yes | Yes |
| Plex and Jellyfin libraries | Yes | Remain saved, not started |
| M3U and Xtream | Yes | Remain saved, not started |
| Media artwork and cinema Home | Yes | Not constructed |
| Profiles, appearance, voice and remote shortcuts | Yes | Yes |
| Updates | Unified Vela APK | The same unified Vela APK |

The choice belongs to this television. Profiles retain their own accounts, layout, favourites and shortcuts. Switching modes does not unlink accounts or erase preferences. The settings PIN also protects mode changes.

Lite uses the existing native HDMI, antenna and apps interface. It does not construct the cinema Home or start media-library clients. The Full screensaver is disabled in Lite, and pending Full activation/measurement jobs stay dormant; recovery remains available. Extra font variants are loaded only for Full. Both modes are packaged in the APK, so the download is larger than the old standalone Lite APK.

On Android 8–10, the Full card explains its Android 11 requirement and cannot be selected. Vela does not enable an unsupported Full interface on an older TV.

## Updating an existing Full installation

Update from Vela or install the latest unified APK from [GitHub Releases](https://github.com/LucasLG92/vela-tv/releases) over the existing app. The package and signing identity are unchanged. An already configured Full installation keeps Full automatically, with its accounts and profiles intact. Open Launcher mode whenever you want to try Lite.

## Moving from the old standalone Lite app

The standalone Lite app has reached end of development. [Lite 0.19.0 Preview](https://github.com/LucasLG92/vela-tv/releases/tag/lite-v0.19.0-preview) is the final archived build. Future releases use the unified **Vela** APK.

Android treats **tv.vela.launcher.lite** and **tv.vela.launcher** as separate apps. Use the included profile transfer to make the switch.

1. Update the old Lite app to [**0.19.0 Preview**](https://github.com/LucasLG92/vela-tv/releases/tag/lite-v0.19.0-preview) if it is on an earlier version.
2. Install the unified **Vela** APK from [Vela Releases](https://github.com/LucasLG92/vela-tv/releases). On its mode screen, choose **Import current Lite profile**. You can reopen this screen from Settings → System → Launcher mode.
3. The old app opens its own confirmation screen. Choose **Import settings** to grant a temporary, one-use read of that profile.
4. Vela opens Lite with the imported layout, remote shortcuts, antenna favourites and guide corrections. If you already configured the new app, the import creates a separate profile. Existing profiles are preserved. Importing into a pristine new installation also saves a recovery copy.
5. In Lite settings, open **System → Home screen** and select **Vela** as Android's Home app. Check HDMI, antenna and sound before removing the old **Vela Lite** app yourself.

The import transfers the **currently selected old Lite profile**. Repeat with another old profile if needed, within the six-profile limit. Unlock settings in both apps first if you use a PIN. The original app stays installed and its data stays available throughout the move.

Accounts, passwords, PINs, phone-pairing secrets, downloaded APKs, cached programme data and viewing history are not transferred. The migration uses the existing credential-free settings format; configure location or other device-specific preferences again if needed. Existing accounts in the unified app are never exported or replaced.

The direct transfer requires matching signing identities. Forks can use the normal settings export/import instead. [Build and signing](BUILD.md).

## Verification

The first-run choice, settings entry, HOME routing, profile retention and import flow are tested with actual Android views on owned Android 8 and Android 16 emulators. The migration test confirms the private provider refuses an ungranted URI, the native confirmation returns a one-use grant, and account material is excluded. Full/Lite voice and bounded Home rows retain their native regression checks.

TV input state, tuner playback, microphones and reserved remote buttons still depend on the manufacturer. [Compatibility](COMPATIBILITY.md).
