# Credits and third-party notices

Vela is derived from [Free My TV](https://github.com/simbasjungles/free-my-tv), released under the MIT licence. Its original copyright and licence remain in [LICENSE](LICENSE) and the notices shipped with the application.

Vela's name, interface and extensions do not imply affiliation with the original project, Apple, Plex, Jellyfin, YouTube, Amazon or any television provider or device manufacturer.

| Component | Source and licence |
| --- | --- |
| Original code and modifications | MIT; [LICENSE](LICENSE) |
| Inter | SIL Open Font License; [licence](app/assets/fonts/OFL.txt) |
| Material Symbols | Apache 2.0; [notice](app/assets/symbols/LICENSE-material-symbols.txt) |
| Public channel and guide references | [TDTChannels](https://github.com/LaQuay/TDTChannels), Apache 2.0; [sources](app/assets/licenses/live-tv-sources.txt). Users supply their own M3U, Xtream or antenna source. |
| Jellyfin icon | Jellyfin contributors, CC BY-SA 4.0; [original](https://github.com/jellyfin/jellyfin-ux/blob/master/branding/SVG/icon-transparent-white.svg), [licence](app/assets/brands/JELLYFIN-ARTWORK-LICENSE.txt). Original white shapes converted to Android Vector Drawable. The artwork licence is separate from the code licence. |
| App identifiers and logos | Trademarks of their owners; [sources](app/assets/brands/sources.txt) |
| Screensaver video links | No video files are bundled; [sources and personal-use terms](app/assets/ambient/sources.txt). MIT does not grant rights to third-party footage. |
| Weather and city search | [Open-Meteo](https://open-meteo.com/), CC BY 4.0; GeoNames as described in the app notices |
| Movie and series artwork in screenshots | Rights remain with the respective owners. Retrieved from Plex with the user's permission to illustrate Vela; [gallery credits](gallery/CREDITS.md). Not MIT-licensed. |
| Open test artwork | Blender shorts, CC BY 3.0/4.0; [credits](gallery/CREDITS.md). |

Screenshot profiles, library names, progress and pagination are demonstration data. Original movie posters are not redistributed as application resources. Open artwork in `tests/gallery/assets/` is retained as an alternative for testing and is not packaged in production APKs.

Device icons derive from Simple Icons 9.21.0, converted into Android vectors and rendered with brand accents. [Sources and trademarks](app/assets/licenses/device-icons.txt), [CC0](app/assets/licenses/simple-icons-CC0.txt), [authors' disclaimer](app/assets/licenses/simple-icons-DISCLAIMER.txt).

The local QR generator uses [Nayuki's QR Code generator](https://github.com/nayuki/QR-Code-generator/tree/3c6d0b3cefb4e049dc337e82237c9644399716a8/java), MIT; [copyright and licence](app/assets/licenses/qr-generator-MIT.txt). It does not contact a QR service.

Full's optional local HLS pause includes AndroidX Media3 1.11.1, Apache 2.0, with checksum-pinned dependencies. [Dependency inventory](app/assets/licenses/media3-dependencies.txt), [Apache 2.0](app/assets/licenses/Apache-2.0.txt), [Checker Framework MIT](app/assets/licenses/checker-qual-MIT.txt). Lite does not include this playback engine.
