# Security and privacy

Do not attach passwords, active pairing codes, Plex/Jellyfin tokens, private playlist URLs, signing keys, recovery files or personal account screenshots to an issue.

To report a vulnerability, use **Security → Report a vulnerability** when available. Otherwise, open an issue without sensitive details or an actionable exploit so a private reporting route can be agreed.

Useful reports include the Vela version, Android version, reproduction steps and observed behaviour. Remove personal data from logs before sharing them.

Official releases must use neutral configuration. Never publish `config.properties`, signing material, private Android storage or personal work history. Do not distribute a build made with `--local-config` or `-UseLocalConfig`.

Phone configuration is a temporary local HTTPS session. Check the address shown on the TV before accepting its self-signed certificate. Peer preference sync checks a certificate fingerprint from the pairing link; pairing links themselves should be kept private. Sessions stop when their screen leaves the foreground.

IPTV source credentials stay on the configured device and are excluded from preference backups and sync. Update APKs are checked for hash, package, version and signer before installation. Android still requires user confirmation.

Vela is a preview. Manufacturer-specific Home, HDMI and tuner behaviour requires device testing; universal compatibility is not claimed. See [privacy](guide/PRIVACY.md) and [compatibility](guide/COMPATIBILITY.md).
