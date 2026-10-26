# Your channels, inside Vela

Full Vela brings **M3U, Xtream and antenna** into the Home interface. Live TV sits directly below Home in the sidebar, with the shared clock and weather. The header changes source; Settings → Television holds source configuration and the preferred Guide/Channels view. Vela remembers navigation and starts video only when you choose a channel.

## M3U playlists

Open Settings → Television → Configure M3U playlist. Enter the playlist URL and optional XMLTV address. An EPG declared in the playlist header can be used automatically. Choose Save and use M3U.

HTTPS is preferred. An explicit HTTP option is available for playlists/signals that require it; HTTP traffic is not encrypted. Private URLs are entered in protected forms and stored encrypted on the device.

EXTINF metadata, EXTGRP categories, original logos, guide IDs and up to four direct signals per ID are supported. Same-ID quality variants remain alternatives of the channel. Browser pages and extraction rules are not executed. The initial public playlist is TDTChannels and can be replaced by your own.

## Xtream

Open Settings → Television → Xtream account. Enter server, username and password, connect, then open your channels. Each installation uses its own account; no personal account is bundled.

Use HTTPS when offered. HTTP requires an explicit choice. The account is validated before saving. Live categories and programmes depend on the server. Provider film/series VOD libraries are not imported. Compatible advertised catch-up archives are supported separately; Vela does not record the provider's channels.

## Antenna

Select Antenna to read already tuned, unblocked channels in the television's native order. Android supplies available logos and schedules, and the TV's tuner supplies playback. Vela does not retune, reorder or modify the manufacturer's database.

You need a compatible television tuner, previously tuned channels and an antenna cable carrying a signal. A TV box without a tuner cannot gain antenna reception from Vela. Tuning settings open only when you request the manufacturer's controls.

Saved channels may remain listed without reception; playback then reports No antenna signal. [Vela Lite](../lite/README.md) uses this native route without M3U/Xtream engines.

## Find and organise

Hold OK for the channel menu, including Add/remove favourite, guide correction and organisation. Open Organise to move favourites with OK and Up/Down, hide/restore channels, show/hide categories or clear recent history. Back cancels a pending move.

Preferences are source/account scoped, with up to 128 manual favourite positions, 256 hidden channels, 512 hidden categories and twelve recent successfully opened channels. Hidden categories do not remove their channels from All channels. Hidden channels stay in the source but leave visible lists and recent navigation. Previous channel uses the visible successful-playback history.

Catalogues are indexed on disk before paging; hidden-channel filters apply before counts. Quality grouping is conservative: country, region, category and time-shift variants stay distinct. Full Vela prefers a supported quality and can try another same-channel signal after failure. Exhausted alternatives require an explicit retry instead of an endless loop.

## Guide and EPG

Guide is the default view. It shows a three-hour window, normally six channels, with categories left of the logos and time controls below. Channels view uses large cards without the category column; Up/Down changes lists while retaining the column where possible.

Left/Right explores programmes and shifts the window at an edge. Now returns to the present. OK opens a live programme or details of a future/past programme. Hold OK opens its channel actions. The footer shows logo, title, time and valid remaining-time/progress data. Missing information remains a gap rather than a fabricated schedule.

The initial external XMLTV is [EPG dobleM](https://github.com/davidmuma/EPG_dobleM). It is streamed into a bounded private index, refreshed when used after four hours; a failed import keeps the previous index. Future listings cover up to 48 hours, with up to 24 previous hours for supported archives in Full. Per-block reads and decoded logos stay bounded. Channel IDs and normalised names are matched conservatively, and ambiguous aliases are refused. If an external current programme is missing, the source/native guide can fill it.

Guide corrections lets you explicitly choose an identity and adjust its time offset. What to watch uses saved EPG genre data. [Tools, matching and limits](YOUR-VELA.md).

## While watching

| Control | Action |
| --- | --- |
| OK | Show/hide programme information; retry if all signals failed |
| Hold OK | Channel actions, including favourites |
| Left | Show information; when visible, open playback controls |
| Right / Up / Down | Open the channel drawer without stopping video |
| OK in drawer | Switch to the selected channel |
| Left / Back in drawer | Close it and keep watching |
| CH+ / CH− | Change channel directly, across pages |
| Previous channel / LAST_CHANNEL | Return to the previous available channel |
| MENU | Toggle audio, supported subtitles, picture format, sleep timer and signal controls |
| Back | Close an overlay, then dismiss information; with neither visible, return to the guide |

The introductory live/quality badges disappear after three seconds once playback is ready; programme information hides after five seconds. Up next shows the next valid airing. Drawer rows include logo, current programme, progress and remaining time, using hours and minutes above an hour. Browsing reuses the current decoder. [Full and Lite remote reference](PLAYER.md).

## Pause, rewind and past programmes

Optional local HLS pause buffers only the selected clear compatible stream on private disk. It is off by default, bounded to 15 minutes/256 MiB/240 segments and cleared on exit. One Media3 decoder is created only for this path, replacing the framework decoder. Unsupported/protected HLS continues direct playback without claiming local rewind.

Antenna pause works only when the OEM tuner advertises time-shift support. Audio delay is controlled by the television's sound settings.

M3U archives require an explicit supported `catchup` mode and `catchup-source`; channel or header defaults can declare `catchup-days`. Supported default/append templates use known time/duration placeholders. Xtream archives require `tv_archive` and a valid duration, account identity and server time zone. Select an ended programme from the available past-day guide only when it is within that source's archive range. Formats not declared by a provider are not guessed.

No accounts, passwords, playlist URLs or recordings are published with Vela. [Privacy](PRIVACY.md) · [Compatibility](COMPATIBILITY.md).
