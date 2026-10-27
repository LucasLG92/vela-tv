# Interface refinement

Available in **Vela 1.35.1-preview** and **Vela Lite 0.14.1-preview**.

## A common visual language

Both editions retain their left sidebar, original service icons and original HDMI device marks. Graphite surfaces, rounded controls, quieter secondary text and the same light focus state connect Home, settings, tools, programme details and the player. Provider and device artwork retains its own colours.

The shared surfaces are native vector drawables. This pass adds no UI library, blur pass, image pack, permanent animation or background service. App icons still use the recycled, bounded icon grid. Programme artwork, collection limits and image-cache budgets from the [design update](DESIGN-UPDATE.md) are retained.

## Navigation that stays readable

The expanded sidebar now has its own opaque graphite surface. Film and series artwork stays in the content area; selecting another title no longer repaints the menu. The former scene ImageView and its blur path are removed rather than covered with another bitmap. Full and Lite share the same native navigation drawable.

The expanded panel is 332 logical pixels wide. Primary destinations, the account/profile header and pinned apps share consistent hit areas. The open section has a quiet persistent marker; remote focus uses the configured light/accent state. Moving focus does not activate a different destination. Full separates pinned apps with a short group heading, preserves original brand colours and decodes those sidebar icons to at most 96 pixels on their longest edge.

Labels reveal without clipping the six supported translations at either normal or large reading size. Long user/server names retain ellipsis and full accessible descriptions. The disconnected library header uses a short title with a server-selection subtitle. Full also places its content-information action after the measured playback/provider action, preventing longer resume captions from covering it.

## Product Design audit follow-up

Port labels on HDMI cards now have a small dark plate and light text so bright original brand colours do not reduce their contrast. The existing text node is reused.

Fresh native captures covered Home, expanded navigation and integrated settings in Full, and HDMI, expanded navigation and settings in Lite. The audit found two bounded issues: expanded navigation cut through the underlying headings/actions, and the recent-activity card promised session continuation for an app that it only reopens.

While navigation is expanded, the inactive content now becomes an 84% preview anchored to the right edge. Full covers Home, its embedded screens, television guide and shared tools. Lite groups its heading, clock, content and footer in one scene, with the same treatment for shared tools. Returning to content restores the original scale and position. Lite also remembers the selected content control for Back and Right, so leaving the sidebar does not rebuild HDMI cards or lose the selected device. Navigation labels retain their full reading size; the preview is inactive context. Reduced motion applies the state immediately.

This is a View transform of existing content, not another render capture or bitmap. It adds no decoder, image request, worker, cache or background service. The recent card now says **Recent activity**, translated in all six languages, and retains its original action and profile isolation.

The native navigation regression checks that the visible page heading clears the expanded panel, the right edge stays anchored, and the original scale/position return after Right and Back. Embedded page headings are checked separately so shared headers cannot stay behind the drawer. These checks run with all six languages, normal/large text and reduced motion. Whole-TV RAM and physical remote comfort remain hardware validation tasks.

## What changed

| Area | Behaviour |
| --- | --- |
| Home | A more compact hero hierarchy, better text contrast over artwork and a smaller last-activity card. Existing poster proportions, shelf ordering, paging and horizontal fades remain. |
| App library | Original square app artwork remains prominent. Focus is visible through a brief lift and a calm label plate, including when motion is reduced. Sidebar favourites have a small badge and an accessible label. |
| HDMI | Full and Lite share the same featured-device composition, alignment and primary action. Device detection and connected-state rules are unchanged. |
| Appearance | Full opens visual customization directly. Picture quality and performance remain accessible from that page. |
| Lite settings | The fixed header and category list accompany one scrollable page body. Row placement prevents options overlapping, and long pages remain reachable with the remote. |
| Returning to options | Settings and tools remember the last focused option when moving between categories. Nested previews start at their primary action instead of inheriting an unrelated category position. |
| Content information | Posters use the same rounded treatment. The complete synopsis can be focused and scrolled with the D-pad; actions retain their separate scroll region and close back to their caller. |
| Television | Full guide categories use the same focus treatment as other menus. The time marker is painted behind programme cells to avoid crossing text. The channel keeps a legible initials fallback while original logo artwork is unavailable. Narrow current-programme cells prioritise remaining time over a truncated time range; the selected programme's information area retains full timing. Very short cells defer readable details to that area. |
| Player | Upcoming programming is grouped in a passive card; channel drawers and playback controls share the same surfaces. Information still hides using the existing player lifecycle. |

## Remote and accessibility checks

The shared native UI test checks actual option bounds, access to the last appearance option, focus restoration, and D-pad scrolling through an unshortened synopsis. It uses a separate local profile on an explicitly selected owned emulator.

```sh
python tests/run_refinement.py --serial <owned-emulator> --adb-port 5038 --test uiux --art <authorised-test-art>
python tests/run_refinement.py --serial <owned-emulator> --adb-port 5038 --test uiux --lite --art <authorised-test-art>
```

The navigation regression additionally checks twelve language/text combinations in each edition, actual Up/Down/Right routing, label/hit-area bounds, opaque menu rendering over two different underlays and bounded menu bitmaps. The Full library test changes actual artwork while the menu is expanded and compares its rendered pixels.

```sh
python tests/run_refinement.py --serial <owned-emulator> --adb-port 5038 --test navigation --art <authorised-test-art>
python tests/run_refinement.py --serial <owned-emulator> --adb-port 5038 --test navigation --lite --art <authorised-test-art>
```

Keep the existing native library, style-preview, apps, HDMI, guide, antenna, player, watched-state, settings and control-reuse regressions alongside it. Both APKs retain six UI languages and their existing signing identity. Lite remains independent of Full's library clients and network player.

Native 4K screenshots use demonstration profiles and authorised library artwork. They show actual Views rather than image mockups. Physical-TV remote feel, video composition and memory still require a hardware check; emulator process measurements do not establish whole-TV RAM savings.

## Design references

The layout follows TV-specific focus, legibility and navigation principles described by [Android TV](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv) and [Apple's tvOS guidance](https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos/). Vela retains its own navigation and native Android implementation.

## Television regressions

Guide paging now selects the first visible channel on Down and the last on Up, including Full catalog boundaries. Asynchronous programme completion keeps that selection. The shared timeline resets a row from another page while preserving a selected channel on metadata refresh. Full has explicit remote routes to What to watch from source/organise controls, with return to the guide or Watch now. Late catalogue refreshes do not steal the focus from those controls. Lite restores the current timeline after metadata rebuilding, even when the previous focused View is detached; guide dialogs retain Back priority over expanded navigation.

An explicit EPG match now also chooses its original channel logo, ahead of the provider or native tuner logo. Logo caches include the corrected identity; automatic matching restores the default image. Decoded 128-pixel images, Full 768 KiB and Lite 512 KiB cache limits are unchanged. Lite negative-logo entries are capped at 128. Missing corrected artwork retains the existing provider/native/initials fallback.

`tests/run_refinement.py --test tv-fixes` reproduces paging, remote discovery and EPG/logo consistency in Full; add `--lite` for the read-only antenna and logo tests. These run only on an explicitly selected owned emulator, with generated metadata and synthetic red/blue logo fixtures, no live stream or personal account.

## Continuous backgrounds

Home artwork and its fades belong to a stationary full-screen layer. Opening navigation scales the foreground controls only, so the wallpaper has no inset rectangular edge. Integrated Full settings, TV and tools use one fixed graphite canvas. Lite retains its screen-sized black/graphite renderer and embedded tools stay transparent. These changes reuse the existing image and caches, without an extra bitmap or background service.

## Identity refinement · Full 1.35.1 / Lite 0.14.1

The profile-local Recent activity action now belongs to the Apps header, beside drawer controls. Home artwork and Lite HDMI composition stay clear. Up from the app grid reaches the recent action; Left/Right connects it with folder controls, and Down returns to the grid. Unavailable apps and disconnected inputs are omitted.

Only the main Settings header shows the in-app Vela mark. Lite uses the existing profile symbol in its sidebar; personalisation shows the active profile with no repeated brand icon. The existing vector identity and matching Android launcher assets are refined with graphite and one restrained accent. No raster logo, additional cache, decoder, service or monitoring is introduced. Native checks cover both launch routes and actual remote focus.
