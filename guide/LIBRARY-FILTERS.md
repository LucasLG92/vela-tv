# Find your next film or episode

Movies and Series open with **newest additions first**, using the connected server's added date, not the release date. Plex uses `addedAt`; Jellyfin uses `DateCreated`, with title as a stable secondary order.

Open the filter control to search by title, combine fields and choose an order. Each section keeps its query while you browse and turn pages. The choice is discarded when the library account changes or Home is recreated.

## Server-backed options

Plex supplies filter and sort descriptors for the chosen section and media type. Only local paths belonging to that section are accepted. Jellyfin supplies filter values through its own endpoints and equivalent Items criteria.

Available metadata can include genre, year, watched state, favourites, collections, labels, countries, languages, studios, people, minimum rating, subtitles, HD, 4K and 3D. Series may also expose their continuing/ended status. Fields without server metadata are not invented, and availability depends on server version and library data.

Value lists load when opened and support search and paging. Large responses must respect limits; otherwise Vela asks you to refine the search rather than retaining an unlimited list. A query supports up to 24 selections. Random sorting is excluded from the paged catalogue.

Vela combines filters by field. Nested logical groups and free-form conditions in Plex's advanced editor remain in Plex itself.

Filtering and searching are read-only queries to the authorised server. They do not modify files, metadata or viewing history.

[Privacy](PRIVACY.md) · [Compatibility](COMPATIBILITY.md) · [Vela](../README.md)
