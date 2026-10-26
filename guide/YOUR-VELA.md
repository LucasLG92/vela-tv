# Your Vela

Open **Settings → System → Your Vela**. Search lives here and on supported remote search keys. Hold OK on Full Vela's avatar to open Profiles; Lite uses the top sidebar icon.

These tools share Vela's graphite panels, Inter typeface and remote focus. Up/Down chooses a category, Right enters its options and Left returns to the menu. Back closes a panel, cancels a pending restore or finishes a remote test.

## Backups and recovery

Save the active profile's preferences on the television, or export a JSON document through Android's file picker. Import supports review before applying and a recovery copy for Undo. If the firmware has no document picker, the local backup still works; USB import/export needs a compatible file manager.

Backups include appearance, shortcuts, reading, remote controls, followed programmes, layout/folders, guide corrections and channel organisation. Accounts, sessions, tokens, passwords, provider URLs, weather location, images, playback history and downloaded APKs are excluded. There is no Vela cloud account.

Invalid, unknown, oversized or incorrectly typed settings are rejected before application. A failed write restores previous preferences. Undo restores the last recovery copy.

On another television, connect the same sources first and load their channels. Xtream favourites use a stable source identity for the same server/account, independent of a local login ID. Credentials must be entered separately on each device. Other accounts remain isolated.

Antenna organisation uses a DVB service identity or an unambiguous channel-name fallback after retuning. Ambiguous matches are not guessed. Manual HDMI profiles apply only to matching destination input IDs. Between Full and Lite, only compatible reading, remote, layout/folder and input preferences are applied.

## Search across your television

Search installed apps, HDMI inputs, channels and programmes from the saved EPG. Full Vela also searches the active Plex/Jellyfin library, in bounded pages and groups of library sections. Open an app, input or channel from its result, or follow a programme title.

Use Search by voice, or the search key when Android forwards it to Vela. Exact, unambiguous app/channel/input commands can open a destination. Recognition depends on the installed Android recognizer, which handles its own permissions and processing. Vela adds no permanent microphone listener.

This does not search every streaming app's private catalogue or import its accounts. Local results can remain available when a connected library is offline.

## Reading

- Text at 100%, 115% or 130%, respecting fixed-height label limits.
- Compact, comfortable or spacious guide density; large text can reduce visible rows to five or four.
- Screen margins of 0%, 1.5%, 3% or 4.5% for televisions that crop edges.
- Higher contrast for secondary text.

Tool rows grow with their content and scroll inside their panel. Changes apply to Home on closing. Reading changes keep an Undo copy.

## Follow programmes

Choose a future programme in the guide or enter a title in Programmes. Vela matches recurring titles and common season/episode suffixes against available EPG data. It keeps up to 32 followed titles and 128 matching airings.

Programme alerts are optional. Android notification permission is requested when needed; missing permission is shown in the panel. One approximate system alarm serves the next saved airing. It uses cached data, does not download on delivery, never changes the channel or wakes the TV deliberately, and opens Programmes when selected. Sleep and guide availability can affect delivery.

## Local profiles

Up to six local profiles separate appearance, shortcuts, reading, followed programmes, Watch later and channel organisation. In Full, each profile links its own Plex/Jellyfin accounts; the default profile retains its existing authorizations. Deleting a profile removes those local library authorizations and pairing links. IPTV sources remain installation-scoped.

Protect settings with a local PIN and enter a separate guest space from Profiles. PIN material is salted, rate-limited and excluded from backup/sync. This protects Vela settings; it is not a content-rating filter, Android parental control or an account switcher for external apps.

## Your remote and device

Test the buttons Android delivers to Vela, or assign allowed buttons to Search and Profiles. Navigation, volume and power buttons remain reserved. Back exits the test; Reset restores shortcuts.

The local device report shows version, Android/device information, current process PSS, Home selection, permissions, network availability and exposed HDMI/tuner counts. It excludes addresses, accounts and credentials and does not prove physical picture/sound reception. A firmware-change marker helps identify system updates that may affect compatibility.

## Arrange Home and apps

Full Vela can reorder, show/hide and resize its Home rows. Lite can reorder or hide Inputs, Television and Apps; Settings remains accessible. Layout is saved per profile. Choose Edit home sections, or hold OK on Home in Full / a main navigation item in Lite, for an explicit Save/Cancel editor. Changes remain a small in-memory draft until saved.

Create up to twelve folders, each with up to 64 installed-app shortcuts. Open them from Apps → Folders and layout. Original icons are drawn at a small fixed size, twelve apps per page. Removing a shortcut or folder does not uninstall apps.

The phone interface can also reorder the app drawer. Full Vela additionally supports pinning, unpinning and reordering sidebar apps. User-created names and provider content are never translated automatically.

## Configure from your phone

Open Mobile & sync → Set up from your phone and scan the QR on the same network. Vela serves a local HTTPS page; no remote QR service, Vela account, third-party scripts or cloud storage is used.

Your browser may ask you to accept the local self-signed certificate. Check that its address matches the TV. The session ends on exit, app switching or after ten minutes. It edits layout, folders, apps, favourites and text size. Full Vela can also receive an M3U/Xtream connection through this page. Credentials are encrypted in transit to that TV and excluded from responses, backups and sync.

## Share preferences with another TV

Enable this TV as a preferences source and show its QR/link. On the receiving Vela, enter the link, review its backup and confirm pairing. The link contains a certificate fingerprint that Vela verifies and stores encrypted on the destination.

The receiving TV checks once when entering Vela. The source must be visible on the same network; its listener closes on leaving or sleep. If both sides have changed, local preferences are kept and another review is required. There is no permanent polling. Unpairing keeps current preferences; each profile pairs separately.

Accounts, passwords, provider addresses, location and viewing history are never part of preference sync. Language is an installation choice, separate from profile sync and Android's language.

## Correct and explore your guide

Guide corrections lets you find the EPG identity, preview three airings and assign it to a channel. Hold OK on a channel → Correct programme guide, or use INFO from the guide, to bring the selected channel to this tool. The per-channel offset ranges from −12 to +14 hours and does not change Android's clock.

Matching uses a normalised name independent of HD/FHD variants and Android row numbers. Channels with the same normalised name share the correction. Available programmes still depend on the cached guide's time window.

What to watch explores Now, Tonight (18:00–04:00) and the next 48 hours. Movie, sports, documentary and kids filters use real EPG categories; absent metadata is not invented. Exact same-channel/title/time duplicates are grouped within a page. Cross-channel events are grouped only when the feed provides a trusted programme-level `dd_progid`; similar names alone are never treated as the same event.

## Playback controls

Left opens information, then controls over the current video; MENU toggles them directly. Controls include audio, supported text subtitles, picture format and safe signal details. Source/tuner capabilities decide which tracks exist. Audio delay stays in the television's sound settings because these player APIs do not expose it to Vela.

Full Vela's optional local HLS pause is off by default. One selected compatible clear HLS signal uses Media3 with a short queue (4 MiB target) and a private disk history capped at 15 minutes, 256 MiB and 240 segments. The queue target is not total process RAM. Pause, rewind and Return to live navigate what has been buffered since opening the channel. Leaving or changing signals cleans storage. Protected, byte-range or unsupported sources keep direct playback.

Both editions offer antenna time shifting only when the manufacturer's tuner declares support. Lite contains no HLS/IPTV player or recording engine.

Full Vela offers past programmes only where M3U/Xtream explicitly advertises a compatible archive. Its EPG retains up to 24 previous hours for selection, within the provider's archive range. Supported M3U templates or the account's Xtream route/time zone are used. An archive failure is not silently replaced by the live programme.

## Watch later and combined libraries

Hold OK on a library/search item or save from programme details. Open Programmes → Watch later to revisit it; hold OK to remove. Each profile stores at most 64 small references, with a 64K UTF-16 character cap. Credentials, artwork and media files are not copied into this list. Media resolves against the original account; broadcast entries open the channel and are not recordings.

In Full, Settings → Home library → Plex + Jellyfin together keeps both libraries available. Home merges at most twelve continuing and twelve recent items. Each library retains its own server filters and pages. Stable external identifiers determine duplicates; an alternate provider remains in the item menu.

## Playback and compatibility

Playback controls include a sleep timer: 30/60 minutes or the known end of the current live programme. One player-owned callback stops Vela playback and returns to the launcher. Leaving playback cancels it; it does not turn off the television or control another app.

Remote & diagnostics → Compatibility assistant explains Home selection, HDMI state, antenna availability and installation permission. The local report contains no account credentials or network addresses. It does not silently alter firmware settings. [Player navigation](PLAYER.md).
