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

Lite keeps its HDMI, antenna and app sections. It shares the editor and voice interface without including media-server clients.

## Give a remote or keyboard button to Vela voice search

Open **System → Remote shortcuts**, add an available button and choose **Vela voice search** for its short or long press. Keyboard function keys such as F6 can be assigned when Android delivers them. No function key is taken over automatically.

Press the assigned button while Vela is on screen. Vela shows a themed listening strip, a microphone symbol over the sidebar profile position and a soft accent-coloured glow around the microphone, sidebar and listening panel. The light responds smoothly to speech levels; Reduce motion keeps a static listening glow. Partial words appear in the strip. The listening indication starts only after the recognition service reports that the microphone is ready. Back cancels; pressing the voice shortcut again ends listening. You can retry an error or use the keyboard.

Vela requests microphone permission when you first ask to use integrated voice search. It prefers Android's on-device recognizer when one is available; otherwise it uses the TV's default speech recognition service, which may process audio online. This is a Vela search interface, not a separate offline speech model. Vela does not store microphone buffers or transcripts, keep a recognizer resident, or record in the background.

Unassigned Google Assistant keys keep their normal behaviour. Android and the TV manufacturer may reserve some buttons or a remote's microphone path; Vela cannot promise that every physical button or remote microphone is available to applications. The Search key and explicitly assigned shortcuts open Vela's own interface. Volume and power remain available while listening.

Results retain the current profile and the existing voice-command rules: text searches show results, exact app/channel/input commands may open an unambiguous destination, and ambiguous commands remain selectable. Cancellation, leaving the foreground or changing profile discards the old session. Granting microphone permission resumes only the still-pending request in the foreground.
