# A Home of your own, and voice search inside Vela

## Choose what belongs on Home

Hold OK on **Home** in the sidebar, or open **Appearance → Edit home sections**. The editor stays inside Vela's sidebar. Each local profile has its own draft; **Save layout** applies it and **Cancel** discards it.

In **Sections**, arrange rows, show or hide them, rename them and change their card size. Hold OK on a row and use up/down to move it; OK places it, while Back undoes that move. Move up/down buttons are available too. Full Vela also lets each media row follow the Home library, the current catalogue library, Plex, Jellyfin or both. An empty chosen source stays empty instead of showing another service's titles.

| Row | Content |
| --- | --- |
| Continue watching | Your connected library, with optional Watch Next items shared by other apps |
| Recently added | Your latest library additions together |
| Recently added movies | A separate row of your latest films |
| Recently added series | A separate row of newly added series |
| Unwatched movies | Films your server reports as unwatched |
| Top-rated movies | Films ordered by the server's audience/community rating |
| Library | Your pinned collections and the existing library fallback |

In **Continue watching services**, choose Plex, Jellyfin and individual installed apps independently of the Home library. Use automatic selection to return to the previous provider and external-app defaults. An explicit empty selection shows no Continue watching items. Plex and Jellyfin must already be linked. Other apps supply only the Watch Next content they actually share with Android TV: enabling YouTube or Prime Video cannot give Vela access to private histories that those apps do not expose.

In **Appearance**, choose the Home library and a **Cinema**, **Compact** or **Rows only** presentation. Rows only removes the hero image and gives the rows more space. Choose a soft outline or high-contrast focus. Moving between posters slightly enlarges the selected card with a single fine accent outline and a small dark separator. Neighbouring pictures in that row receive a light native tint. A short underline marks the active heading; the item count keeps Vela's graphite surface. Leaving the posters restores the row instead of retaining a false selection. Reduce motion keeps these contrast cues without the movement. Clock, weather and shortcuts remain configurable in **Appearance → Home widgets**.

These choices leave Movies, Series and account settings unchanged. Home preferences travel with credential-free profile backups and settings sync. If every row is hidden, Home retains an **Edit home sections** button.

Existing profiles retain their current rows. The four additional feeds start hidden and make no requests until enabled. Each keeps at most twelve titles. Plex uses bounded `/library/all` queries; Jellyfin uses bounded user-scoped `/Items` queries. With both providers, Vela merges and deduplicates the small results. Up to six pinned collections retain eight titles each.

Metadata refreshes share the existing cancellable library worker and a five-minute foreground freshness window. Artwork still uses the existing cache and loads only near the viewport. Moving between rows uses a short native scroll/text transition; Reduce motion disables it. No new background service, catalogue cache or artwork bitmap is created for these transitions.

Lite keeps its HDMI, antenna and app sections. Its shared editor offers order, visibility, names and sizes for those destinations, without media-service controls. Media-server clients remain inactive.

## Give a remote or keyboard button to Vela voice search

Open **System → Remote shortcuts**, add an available button and choose **Vela voice search** for its short or long press. Keyboard function keys such as F6 can be assigned when Android delivers them. No function key is taken over automatically.

Press the assigned button while Vela is on screen. A small, neutral microphone replaces the sidebar profile picture; a soft light follows only the exterior contour of the actual sidebar and responds to speech levels. Home stays fully visible, without a listening panel, transcript or dimmed background. A quieter sidebar animation indicates processing, then fades before results open; Reduce motion keeps the light static. The active listening indication starts only after Android reports that the microphone is ready. Accessibility services still receive status descriptions.

When the sidebar is hidden, unavailable behind a modal, or outside the current screen, the microphone uses a compact capsule near the bottom centre instead. There is no detached full-height outline. The same exterior light follows the capsule; returning to an available sidebar restores its actual collapsed or expanded bounds. Visibility changes are checked during drawing, including with reduced motion, without adding a polling timer. Closing the session removes its window observers and animation.

Back cancels; OK or the voice shortcut finishes listening. A themed message with Retry, Keyboard and Cancel controls appears only when permission or error recovery needs your attention. Retrying hides it again. Successful speech follows your saved search-result preference.

Vela requests microphone permission when you first ask to use integrated voice search. It prefers Android's on-device recognizer when one is available; otherwise it uses the TV's default speech recognition service, which may process audio online. This is a Vela search interface, not a separate offline speech model. Vela does not store microphone buffers or transcripts, keep a recognizer resident, or record in the background.

Unassigned Google Assistant keys keep their normal behaviour. Android and the TV manufacturer may reserve some buttons or a remote's microphone path; Vela cannot promise that every physical button or remote microphone is available to applications. The Search key and explicitly assigned shortcuts open Vela's own interface. Volume and power remain available while listening.

Open **System → Voice and search** to choose **Show results** (the default) or **Open first match**. The choice belongs to the active profile and travels with credential-free settings backups. First match opens the same top available result that appears in the ranked result view; a programme opens its information. Explicit app/channel/input requests stay within that type, and disconnected inputs are never opened. No match keeps the search visible. Keyboard and Android ACTION_SEARCH text queries always remain selectable.

Search results use a dedicated surface inside the sidebar, without the secondary personalisation menu. Filter Library, Television, Apps or HDMI, navigate a two-column grid with the remote, and page through six cards at a time (four at the largest reading size). Moving between local card pages makes no provider request. Provider pagination keeps the original query and requested type. Cancellation, leaving the foreground or changing profile discards the old session. Granting microphone permission resumes only the still-pending request in the foreground.

Cards show Plex/Jellyfin posters, channel logos and programme images when supplied by the source. An unavailable image retains a readable type icon. Full reuses Home's authenticated artwork loader and its existing cache budget; Lite never initialises a media-server client. TV images use one disposable worker and a 512 KiB cache for the displayed page. Changing the query, filter or page, pausing, or closing search cancels its image requests and clears the corresponding views.

Library search queries all accessible movie and series libraries in small server-side pages, rather than scanning only the first few sections. Say “find Alien in Plex” or “busca Alien en Jellyfin” to choose a linked service for that search. This leaves your Home and catalogue provider preferences unchanged. A guide or provider outage does not discard results from other sources; Vela marks the search as partial. No whole-library metadata or artwork cache is built for search.
