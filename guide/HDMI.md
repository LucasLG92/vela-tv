# Your HDMI devices, inside Vela

Inputs shares Vela's sidebar, header, graphite palette and remote navigation. The selected device has a prominent brand icon, with connections arranged in a horizontal row.

- **OK:** open the input through Android.
- **Hold OK:** choose a device family/model or return to Automatic.
- **Left from the first input:** return to the sidebar.

## What detection means

Vela reads ports, labels and logical HDMI-CEC inputs published by `TvInputManager`. It groups a device with its physical port and listens for system changes while the screen is visible. It does not scan the network or identify devices by IP address.

A model name and icon appear only when Android reports an active connection. Disconnected, standby, unknown or unidentified inputs keep their HDMI port name. A saved manual profile never turns an inactive port into a connected console.

Explicit labels cover Nintendo Switch/OLED/2, Wii/Wii U, GameCube, N64, NES/SNES; PlayStation/PS1–PS5; Xbox/360/One/Series families; Sega; Steam Deck and gaming PCs; PC/Mac; Apple TV, Fire TV, Chromecast, Google TV, Shield, Roku, Android TV; Blu-ray/DVD, set-top boxes, AV receivers and soundbars. Other connections remain generic HDMI devices.

Detection depends on the name shared by the television. An adapter for an older console may not identify the console behind it; choose a manual profile when needed. Vela cannot reliably distinguish every model or confirm more than the state Android publishes.

## Icons, without a photo library

Original brand outlines are converted to Android vectors and shown over their brand colours. The surrounding interface stays graphite. Variants are distinguished by name; related models can share their family symbol. Generic types use connection icons.

This screen adds no product photos, image loader or permanent animation. It releases icons and HDMI listeners on exit. Input switching and manufacturer controls still use Android's native interface.

[Icon sources and notices](../app/assets/licenses/device-icons.txt) · [Privacy](PRIVACY.md)
