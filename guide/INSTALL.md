# Install Vela

Full Vela needs **Android TV or Google TV, Android 11 or later**. Samsung and LG televisions running their own operating system need an external Android TV device over HDMI. For an Android television used mainly with an HDMI box or console, consider [Vela Lite](../lite/README.md), Android 8.0+.

## Install or update

1. Download the APK for your edition from [Releases](https://github.com/LucasLG92/vela-tv/releases).
2. Transfer it to the television and open it with your usual file manager. Allow that manager to install apps if Android asks.
3. Open Vela. The optional setup guide covers accounts, apps and weather location.
4. Test navigation, apps, artwork and playback before changing your default home.

An existing installation keeps its data when updated with the same signing identity. Do not uninstall Vela or clear its data to update it.

If you already use ADB:

```sh
adb -s <TV_IP>:5555 install -r Vela.apk
```

## Update from the television

Open **Settings → Updates**. Automatic checking and downloading are enabled by default; explicit previous choices are preserved. Vela checks GitHub at most daily when opened, or when you choose Check now.

The APK stays in private cache and is checked for SHA-256, package, version and signer. Android may ask you to allow Vela to install apps. Return to Vela afterwards; the pending install resumes, then Android asks for installation confirmation. If you cancel, choose Install again.

After Android confirms that the downloaded version is installed, Vela cleans its updater APK. It also checks on the next opening, including offline or with automatic checks disabled. Pending/cancelled installations keep their APK for retry. Abandoned temporary updater files are cleaned without touching a current download, account data or APKs you saved in Downloads.

Preview builds can receive subsequent previews. The Full edition filters out Lite releases; Lite filters out Full releases and has an auxiliary public feed if GitHub's API is unavailable. Builds signed with a different key cannot update the official APK.

Versions older than Full 1.12 need one manual APK update to gain the updater. Older versions that do not resume after granting installation permission can return to Vela and choose Install again.

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
