# Contributing to Vela

Use Java 8 and native Android APIs. Full Vela requires Android 11; Lite requires Android 8.0. Guard newer APIs and keep Full-only clients out of Lite's build whitelist.

## Keep it light

Do not add autoplay video, continuous drawing, service polling or unbounded lists to Home. Bound images, pages, queues and response sizes; cancel stale work. Release images, players, tuner sessions, listeners and temporary local servers when their screen leaves the foreground.

Keep new menus inside Vela's existing graphite surfaces and Activity. Preserve remote focus, Back behaviour, reading size and screen margins.

## Localisation

Owned UI text belongs in `app/assets/i18n/en.json`, `es.json` and `pt.json`. Use `L10n.text("English source text")`; existing `Ui.tr` calls also use the selected catalogue. Update every catalogue and preserve formatting placeholders. Never translate server names, channel names, programme titles, user-created folders or provider protocol values.

Run `python tools/check_localization.py`. Language selection is installation-local, supports System language and uses a tiny atomic record so Home and screensaver processes see the same choice. Only the selected translation catalogue is retained in memory.

## Before submitting

1. Build with neutral defaults using [the build guide](guide/BUILD.md).
2. Run checks appropriate to the change. Recovery changes require `python tests/test_home_mode.py`.
3. Run `python tools/audit_publication.py` and review the actual files you will publish.
4. Inspect native screenshots, accessibility labels and licence notices. Use an owned clean emulator and demonstration data for public captures.

Describe the concrete problem, resulting behaviour and verification. Never include personal credentials, provider URLs, signing keys or private Android data in code, tests, issues, images or commits.

The gallery uses real native screens and authorised or openly licensed artwork. Test APK assets do not belong in a production APK. Demo labels and non-working pairing codes must be explicit; never imply that a simulated provider session proves access to a real account.
