# Settings that belong in Vela

Vela's configuration shares Home's sidebar, header, clock and chosen accent. Plex/Jellyfin accounts, M3U/Xtream sources, updates, apps and inputs live in the same space.

- **OK on a pinned app:** launch the app.
- **Right on YouTube:** explore the rows it shares with Android TV.
- **Hold OK:** manage the pinned shortcut.
- **Left from settings:** return to the category/sidebar; text fields retain cursor movement.
- **Back:** return to the previous page. At a root, return to its sidebar destination.
- **Home:** return to Vela Home.

Android permissions, the package installer, manufacturer settings and HDMI playback belong to the system and keep their native interfaces. External apps keep their own sessions and screens.

## Resource handling

Only one internal screen is retained. Navigation history stores up to eight routes and positions, without views, passwords or playlist URLs. Screen workers are cancelled on exit. The app grid recycles rows and releases its cache; hidden Home posters are released while configuration is open.

Private account/list forms use a protected window. Fields are excluded from saved state and cleared when hidden. Existing provider-specific private storage remains responsible for credentials.

## Verification

Build a neutral APK and use an owned emulator. The native `integrated-settings`, `apps-grid`, `plex-link`, `personalization`, `updates` and `integrated-update-permission` checks exercise navigation, lifecycle and permissions. Update checks require `tests/build_update_fixtures.py` after building.

`tests/run_localization.py` checks English, Spanish, Portuguese, French, German and Italian, actual language selection/recreation, System language, deterministic English fallback and shared settings/tools. Gallery pairing codes are non-working DEMO data.

[Build and tests](BUILD.md) · [Personalisation tools](YOUR-VELA.md)
