# Is Vela the right home for your TV?

Choose a launcher around what you actually watch. Vela's focus is a consistent home for **your Plex/Jellyfin library, a proper live-TV guide and your apps and HDMI devices**, with a simpler Lite mode in the same download.

## Where Vela earns its place

| What you want | What Vela offers |
| --- | --- |
| Your own collection at the front | Plex and Jellyfin together, sources per Home row, original artwork, paged libraries and server-backed filters. |
| A Home that follows your taste | Arrange, name, size or hide shelves; choose Continue watching services independently; use Cinema, Compact or Rows only. |
| Live TV that feels part of the launcher | M3U, Xtream or the television's antenna; horizontal guide, programme discovery, favourites, recent/previous channel and a drawer over playback. |
| A television that mainly displays an HDMI box | Lite keeps HDMI, antenna and apps at the front without starting Full's library Home or IPTV player. |
| Setup that works from the sofa | Remote-first settings, optional temporary phone configuration, profiles, backups and paired preference sync. |
| An inspectable project | MIT-licensed source, documented limits, build instructions and public preview releases. No Vela account or subscription. |

These are reasons to try Vela, not a claim that every feature is unique. [See the current screenshots](../gallery/README.md) and [device-dependent limits](COMPATIBILITY.md).

## Other launchers worth understanding

The following summaries describe each project's published emphasis. They are not an exhaustive feature audit, performance ranking or claim that an unlisted feature is absent. Reviewed **1 November 2026**; follow the linked project for its current details.

| Launcher | Published emphasis | How to think about the choice |
| --- | --- | --- |
| **[Projectivy](https://github.com/spocky/miproja1)** | Ad-free customisation, app categories/channels, wallpapers, icons, input shortcuts and device tools. Its [plugin ecosystem](https://projectivylauncher.com/plugins.html) extends wallpapers and icons. | Explore it if wallpaper, icon-pack and device customisation are your priority. Vela concentrates its presentation on personal libraries, live-TV navigation and a shared Full/Lite experience. |
| **[Dispatch](https://play.google.com/store/apps/details?id=com.spauldhaliwal.dispatch&hl=en)** | A modern media-feed launcher integrated with an existing Plex library. | A relevant option for a Plex-centred home. Try Vela if you want its particular combination of Plex/Jellyfin row choices, an integrated TV guide and Lite mode. |
| **[FLauncher](https://gitlab.com/flauncher/flauncher)** | An open-source Android TV launcher built with Flutter. The [osrosal fork](https://github.com/osrosal/flauncher) documents an ad-free app grid, categories, reordering, wallpaper and clock. | A useful reference for a straightforward app-first home. Vela Lite is aimed especially at HDMI and native antenna use, while Full adds library and television tools. Check which FLauncher fork/release you are evaluating. |
| **[Primal TV Launcher](https://play.google.com/store/apps/details?id=dxidev.primaltvlauncher&hl=en)** | Custom layouts, tiles, profiles, widgets, wallpapers and compatible Watch Next content. | Explore its layout approach if you want a highly visual app home. Compare it with Vela's actual gallery and the features you use daily. |

Vela is a **preview**. Established alternatives may suit your device or workflow better. There is no published cross-device RAM or frame-rate benchmark here, so “fastest” and “lowest RAM” would be unsupported claims. Vela instead documents its [cache, paging and lifecycle limits](COMPATIBILITY.md#memory-and-lifecycle).

## What people ask for, and what that means here

Recent public Android TV discussions show different preferences: some people want only their apps, others want a personal media home, and others value deep visual customisation. Ad-free navigation and useful shared viewing rows recur in the discussion. This is qualitative feedback, not a representative user survey. [Android TV launcher discussion, October 2026](https://www.reddit.com/r/AndroidTV/comments/1x4zhd8/does_anyone_know_what_the_best_launcher_for/).

An older [launcher discussion](https://www.reddit.com/r/AndroidTV/comments/1gcwjcx/) also starts from frustration with adverts. Vela's answer is to add no adverts or sponsored rows of its own, while being clear that external services keep their own content and policies.

Shared content is another source of confusion. A [Hydravion feature request](https://github.com/bmlzootown/Hydravion-AndroidTV/issues/32) illustrates how app-specific rows depend on cooperation between apps and launchers. Vela therefore distinguishes its direct Plex/Jellyfin integrations from the information other apps choose to publish through Android TV. It does not promise a universal private watch history.

The practical test is simple: try your remote, open your library, browse your channels and return to your original Home screen. Those everyday actions matter more than a checklist. [Install Vela](INSTALL.md) · [Report your findings](https://github.com/LucasLG92/vela-tv/issues).
