# Pick up your story

Continue watching brings together partly watched films and the next available episodes of your series.

1. Choose a title in Home's Continue watching row.
2. Hold **OK** until its menu opens. Releasing the button does not confirm an action.
3. Choose **Mark as watched** to save that film or episode to the connected Plex or Jellyfin user.
4. Vela refreshes the row. If another pending episode is available, it appears as Next episode, with season, number and duration when supplied.

Cancel, Close or Back returns without changing history. The menu can also open the title in its provider app. If the server cannot confirm a watched change, Vela reports it and keeps the visible content.

Plex Continue Watching / On Deck and Jellyfin Resume / Next Up determine the available titles. This is not an episode release calendar: a programme must already exist in the server library. A partly watched episode takes priority over a new episode of the same series.

The row keeps at most twelve cards, with a consistent landscape shape and remaining time when duration and position are available. Home refreshes its data without a permanent tracking service.

Other Android TV apps can contribute through Watch Next, including NEXT or NEW episodes. Their private history is managed inside those apps. Vela does not import their accounts, change their watched state or invent progress they have not supplied.

[Compatibility](COMPATIBILITY.md) · [Privacy](PRIVACY.md) · [Vela](../README.md)

## Opening a title in Plex

Catalogue cards retain the selected Plex server and local item key. Current Android Plex clients use library-detail routes, with a distinct series route; older clients use their Android server-item route. The metadata path is encoded once and handoffs include no account token or server address. Series additions reported as seasons retain their parent series. Existing Continue watching handoffs remain preferred when Android can resolve them.

If Android cannot open an item-specific intent, Vela reports the failure instead of disguising it as a successful launch of Plex Home. Plex’s client still controls its destination screen. This preview includes native Vela handoff regressions; validation of the destination screen with installed Plex clients on physical televisions remains pending.
