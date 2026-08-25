# External Device Setup (Trusted-LAN POC)

The Android app can receive HUH1 PCM audio from any compatible device over a trusted local
network. This path is local-only and does not use a cloud service.

## Android

1. Put the phone and device on the same trusted Wi-Fi network.
2. In Huh?, open **Settings > Configure Huh? Puck**.
3. Enter the device's local IP address, TCP port (`8765` by default), and optionally the
   stable device ID reported by its `HELLO` message.
4. Select **Save and connect** and grant local-network and notification access when asked.
5. Keep an Ear Out will show Connecting, Waiting, Receiving, Reconnecting, Paused, or Error
   honestly in the app and its persistent notification.

Eligible conversations enter the same local Whisper queue as phone captures. Gemma is not
run automatically. Successfully transcribed audio follows the existing automatic deletion
policy.

## Firmware requirement

The XIAO must run a TCP server on the configured port and emit the framing defined in
`HUH_AUDIO_PROTOCOL_V1.md`. The current firmware transport stubs must be completed before
the phone can establish an end-to-end connection.

BLE discovery, authenticated association, mDNS discovery, and encrypted control are not
part of this POC. Until those are implemented, use this only on a trusted LAN and supply the
expected device ID when available.
