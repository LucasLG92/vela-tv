# Your channel, without leaving Vela

Available in **Vela 1.30.1-preview** and **Vela Lite 0.10.1-preview**.

Both editions share a calmer information strip: a readable channel logo, programme title, schedule, remaining time, progress and clock. The following programme occupies a separate column with a thin divider, rather than a second floating card. Live/quality badges appear briefly at the start; programme information hides after five seconds when playback is ready.

| Remote action | Behaviour |
| --- | --- |
| OK | Show or hide programme information. |
| Left | Show information; while visible, open playback controls. |
| Right | Browse channels over the current video. |
| Up / Down | Browse channels; playback changes only with OK. |
| CH+ / CH− | Change channel directly. |
| Hold OK | Channel actions, including favourites. |
| Back | Close controls or the channel drawer; dismiss visible information before returning to the guide. Errors still allow a direct return. |
| MENU | Toggle playback controls, when the remote has that key. |

The channel drawer uses quieter rows and filter tabs, a distinct playing-channel indicator, readable remaining time and logo contrast on focused rows. Informational playback details are readable without becoming empty stops in D-pad navigation.

Lite retains its current logo through metadata-only updates. Full retains filter focus during background page refreshes. The existing page bounds, decoder, caches and disposable workers remain; the shared styling adds no service, request, bitmap cache or decoder.

Native emulator validation covers real public playback in Full, remote key-down/key-up routing, Back/Left/MENU behaviour, current/next data, logo contrast, non-interactive details and 4K/720p geometry. Lite's tuner metadata and availability are simulated for UI checks. Physical tuner/CEC behaviour and hardware RAM usage require testing on the televisions.

Lite uses the evenly spaced gradient API on Android 8–9, preserving contrast without calling the custom-offset API introduced in Android 10. Both drawing paths are tested for a transparent top and readable text area. [Android gradient API reference](https://developer.android.com/reference/android/graphics/drawable/GradientDrawable).
