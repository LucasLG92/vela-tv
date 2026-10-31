# Install Vela

**One APK supports Android TV and Google TV from Android 8.0.** Choose **Lite** for HDMI, antenna and apps, or **Full** for Plex, Jellyfin and IPTV as well. Full requires Android 11+. Non-Android Samsung/LG televisions still need an external Android TV device.

## Install or update

1. Download **Vela-2.0.0-preview.apk** from [Vela Releases](https://github.com/LucasLG92/vela-tv/releases/tag/v2.0.0-preview).
2. Transfer it to the television and open it with your usual file manager. Allow installation from that manager if Android asks.
3. Open Vela and choose Full or Lite. Android 8–10 offers Lite. You can change the choice in **Settings → System → Launcher mode**; profiles and accounts remain saved.
4. Existing Full users can install over their current signed app or use its updater. Their configured Full mode is preserved automatically.
5. If you use the old separate Lite app, follow the [one-time migration steps](UNIFIED.md#moving-from-the-old-standalone-lite-app). Android cannot replace both old package identities with one APK in a normal update.

Both modes then check and download the same official Vela release. Android's installer still asks for installation permission and confirmation when required. APK size, package, version and signing identity are verified before installation; completed downloads are cleaned after an update.

## Choose your Home screen

Open **Settings → System → Home screen** to choose Vela or return to Original. Devices that need the local ADB connection may request authorisation on the television. Recovery of the original home is saved before activation completes.

Public builds do not disable packages. The change does not automatically alter picture, sound, network or service accounts. Test the return to Original before relying on Vela as your permanent home; Home and dedicated remote buttons vary by manufacturer.

## Choose your language

Open **Settings → Appearance → Customize appearance → Language** in Full Vela, or **Settings → Appearance → Language** in Lite. Choose English, Español, Português or System language. This changes Vela's menus, not Android or another app. Unsupported system languages use English. Accounts, channel names and library titles keep their original text.

## Connect your sources

- **Plex:** authorise the device code at `plex.tv/link`, then choose a server.
- **Jellyfin:** open Accounts → Jellyfin, enter the full server address and use Quick Connect or username/password. Install the official Jellyfin Android TV app with the same server and user for title playback. [Jellyfin guide](JELLYFIN.md).
- **YouTube and Prime Video:** sign in inside their own apps. Vela launches those sessions; shared Android TV rows depend on the service.
- **Live TV:** configure your M3U list, Xtream account or native antenna from Television → Playlists and settings. The initial public list is TDTChannels; the external EPG is dobleM. [Live TV guide](IPTV.md).
- **Weather:** allow approximate location or search for a city. No personal city is bundled.

## Remote controls

Open **Settings → System → Remote shortcuts** in either edition. Choose **Add a button**, press the remote button, then assign a **Short press** and/or **Long press** action. You can open a Vela section, search by voice or launch an installed app. Remove one button or reset all mappings from the same screen; resetting asks for confirmation.

Mappings work while Vela is in the foreground, including its integrated TV screen. They use the button code delivered by Android, rather than a remote-brand list or a changing Bluetooth device ID. Vendor buttons with a delivered scan code can also be learned. Other remotes sending the same code share that mapping. The app keeps navigation, Back, volume, power and channel controls available. Android/firmware may intercept Home, Assistant or branded app buttons before Vela receives them. These shortcuts do not remap keys globally inside other applications. No accessibility service or additional permission is required.

If only the long press is assigned, a short press retains its original function. When only a short press is assigned, holding the button performs that shortcut once on release. Custom long presses run once after 650 ms. Mappings are private to each Vela profile, are included in credential-free backups and are limited to 24 actions. If a restored button does not match your new remote, learn it again on that television.

| Where | Action |
| --- | --- |
| Sidebar app | OK opens it; hold OK opens shortcut management |
| YouTube sidebar app | Right opens the rows shared with Vela |
| Channel / during playback | Hold OK adds or removes its favourite |
| Playing a channel | Right, Up or Down opens the channel drawer |
| Channel list or guide | OK opens the selected channel |
| CH+ / CH− | Change channel directly |
| Channel drawer | Back closes it and keeps the current video |
| During playback | Left shows information, then controls; MENU toggles controls |

If Lite's tuner is blocked or unavailable, its recovery action can still open the original television app. [Personalisation and backups](YOUR-VELA.md) · [Compatibility](COMPATIBILITY.md).
