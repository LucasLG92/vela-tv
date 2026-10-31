# A Home of your own, and voice search inside Vela

## Choose what belongs on Home

In Full Vela, hold OK on **Home** in the sidebar, or open **Appearance → Edit home sections**. Each local profile has its own draft. Arrange rows, show or hide them and change card sizes, then **Save layout** or **Cancel**.

| Row | Content |
| --- | --- |
| Continue watching | Your connected library, with optional Watch Next items shared by other apps |
| Recently added | Your latest library additions together |
| Recently added movies | A separate row of your latest films |
| Recently added series | A separate row of newly added series |
| Unwatched movies | Films your server reports as unwatched |
| Top-rated movies | Films ordered by the server's audience/community rating |
| Library | Your pinned collections and the existing library fallback |

Choose **Home library** to follow your current library selection, or use Plex, Jellyfin or both on Home. This choice does not change Movies or Series browsing. The selected accounts must already be linked. You can separately hide Continue watching from other apps.

Existing profiles retain their current rows. The four additional feeds start hidden and make no requests until enabled. Each keeps at most twelve titles. Plex uses bounded `/library/all` queries; Jellyfin uses bounded user-scoped `/Items` queries. With both providers, Vela merges and deduplicates the small results. Up to six pinned collections retain eight titles each.

Metadata refreshes share the existing cancellable library worker and a five-minute foreground freshness window. Artwork still uses the existing cache and loads only near the viewport. Moving between rows uses a short native scroll/text transition; Reduce motion disables it. No new background service, catalogue cache or artwork bitmap is created for these transitions.

Lite keeps its HDMI, antenna and app sections. It shares the editor and voice interface while leaving media-server clients inactive.

## Give a remote or keyboard button to Vela voice search

Open **System → Remote shortcuts**, add an available button and choose **Vela voice search** for its short or long press. Keyboard function keys such as F6 can be assigned when Android delivers them. No function key is taken over automatically.

Press the assigned button while Vela is on screen. A small microphone replaces the sidebar profile picture; a soft, flowing light stays inside the actual sidebar bounds and responds to speech levels. Home stays fully visible, without a listening panel, transcript or dimmed background. A quieter sidebar animation indicates processing, then fades before results open; Reduce motion keeps the light static. The active listening indication starts only after Android reports that the microphone is ready. Accessibility services still receive status descriptions.

Back cancels; OK or the voice shortcut finishes listening. A themed message with Retry, Keyboard and Cancel controls appears only when permission or error recovery needs your attention. Retrying hides it again. Successful speech follows your saved search-result preference.

Vela requests microphone permission when you first ask to use integrated voice search. It prefers Android's on-device recognizer when one is available; otherwise it uses the TV's default speech recognition service, which may process audio online. This is a Vela search interface, not a separate offline speech model. Vela does not store microphone buffers or transcripts, keep a recognizer resident, or record in the background.

Unassigned Google Assistant keys keep their normal behaviour. Android and the TV manufacturer may reserve some buttons or a remote's microphone path; Vela cannot promise that every physical button or remote microphone is available to applications. The Search key and explicitly assigned shortcuts open Vela's own interface. Volume and power remain available while listening.

Open **System → Voice and search** to choose **Show results** (the default) or **Open first match**. The choice belongs to the active profile and travels with credential-free settings backups. First match opens the same top available result that appears in the ranked result view; a programme opens its information. Explicit app/channel/input requests stay within that type, and disconnected inputs are never opened. No match keeps the search visible. Keyboard and Android ACTION_SEARCH text queries always remain selectable.

Search results use a dedicated surface inside the sidebar, without the secondary personalisation menu. Filter Library, Television, Apps or HDMI, navigate a two-column grid with the remote, and page through six cards at a time (four at the largest reading size). Moving between local card pages makes no provider request. Provider pagination keeps the original query and requested type. Cancellation, leaving the foreground or changing profile discards the old session. Granting microphone permission resumes only the still-pending request in the foreground.

Library search queries all accessible movie and series libraries in small server-side pages, rather than scanning only the first few sections. Say “find Alien in Plex” or “busca Alien en Jellyfin” to choose a linked service for that search. This leaves your Home and catalogue provider preferences unchanged. A guide or provider outage does not discard results from other sources; Vela marks the search as partial. No whole-library metadata or artwork cache is built for search.
