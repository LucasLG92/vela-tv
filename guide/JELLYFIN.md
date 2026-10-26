# Connect Jellyfin

1. Open **Settings → Accounts → Jellyfin**.
2. Enter the complete server address, including a URL prefix if used, for example `https://media.example.org/jellyfin`.
3. Choose Connect and authorise the code through **Quick Connect** in another signed-in Jellyfin session. Quick Connect must be enabled on the server.
4. Alternatively, choose username/password. The password is used to log in and is not saved. No administrator API key is required.
5. Choose Use on Home. Vela uses the server's name, artwork, libraries, recently added titles and available watch progress.

Public servers require HTTPS with a certificate trusted by Android. HTTP is accepted only for local-network servers; it is not encrypted. Vela does not bypass invalid HTTPS certificates.

Install **Jellyfin for Android TV** and sign in to the same server/user there. Choosing a title in Vela opens its details in that app. Vela passes the item identifier, not its session token. Playback and format support belong to Jellyfin.

Hold OK in Continue watching to mark the selected film or episode as watched. The change affects the authorised Jellyfin user and refreshes the row. Next Up supplies available pending episodes; partly watched episodes take priority.

Keep Plex and Jellyfin connected and switch the active library from **Accounts → Home library**. Switching keeps both authorisations and also selects the library artwork used by the screensaver. The two catalogues are not mixed.

Disconnect from Vela removes the local Jellyfin session and metadata and attempts to revoke that session on the server. Server content, Plex configuration and the official Jellyfin app's session are kept.

References: [Jellyfin Quick Connect](https://jellyfin.org/docs/general/post-install/quick-connect/) and [network/URL-prefix configuration](https://jellyfin.org/docs/general/post-install/networking/).

[Compatibility](COMPATIBILITY.md) · [Privacy](PRIVACY.md)
