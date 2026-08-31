# Task: Stream Huh? Audio to Android

## Objective

Implement the firmware side of the hardware-neutral Huh? Audio Protocol so the XIAO
ESP32S3 Sense can act as the first compatible external audio device for the Android app.

The firmware must capture canonical microphone audio, frame it safely, and send it to the
user-configured Android receiver over a trusted local connection. Explicit authenticated
association is a later milestone. It must not use cloud services, telemetry, or hard-coded
network credentials. The existing SD-free build and optional microSD recording test must
continue to work.

The Android protocol is defined in:

`../../mobile/android/docs/HUH_AUDIO_PROTOCOL_V1.md`

The broader integration and failure contract remains in `integration.md`.

## Next milestone: trusted-LAN streaming POC

The initial Android receiver is ready. Implement the minimum firmware path needed to use
it without committing Wi-Fi credentials to the repository.

The following POC decisions are now frozen:

- Both devices use the same trusted Wi-Fi LAN.
- Wi-Fi credentials are entered once through the USB serial console and stored in ESP32
  NVS; they are never committed to source, build flags, logs, or generated artifacts.
- The XIAO hosts the TCP audio server and Android connects as the client.
- The TCP server listens on port `8765`.
- Android uses manual IP/host entry and optional stable-device-ID checking initially.
- mDNS discovery, BLE onboarding/control, peer authentication, and application-layer
  encryption are follow-up work. The UI must continue to call this a trusted-LAN POC.
- No microSD backfill is required.

### Immediate implementation order

1. Add USB serial Wi-Fi provisioning and NVS-backed credential management.
2. Connect to the configured network and report the assigned IP address safely.
3. Implement the TCP server on port `8765`.
4. Stream the existing HUH1 messages and canonical PCM frames to Android.
5. Verify real audio reaches the normal Android VAD, session, and Whisper pipeline.

Success: after one-time serial setup, a user can enter the printed IP address and port
`8765` in **Huh? > Settings > Configure Huh? Puck**, receive a conversation, and see its
local transcript without any cloud service.

## Pending validation: TCP audio queue overruns

The first Pixel 8 Pro end-to-end test on 2026-08-24 proved that Wi-Fi, TCP, `HELLO`,
`START`, and real audio delivery work. The stream then failed repeatedly because the
firmware's bounded audio queue filled faster than the TCP sender drained it.

Observed evidence:

```text
Android receiver connected from 192.0.2.2.
State: READY
Receiver disconnected; completed=0 interrupted=8 overruns=273
```

Android independently reported `RemoteDeviceException: Audio queue overrun` for each
failed connection. The optional expected device ID was blank and is unrelated; the HUH1
handshake and audio-format validation had already succeeded.

Treat reliable sender throughput as the next implementation priority:

- [x] Attempt a bounded number of queued audio frames during each transport pass instead
  of transmitting at most one frame per main-loop iteration.
- [x] Skip or reduce the unconditional main-loop delay while a client is connected and
  audio frames are queued; retain an idle delay when no immediate work exists.
- [x] Give audio transmission a bounded time/frame budget per pass so serial provisioning,
  Wi-Fi lifecycle polling, heartbeats, and disconnect handling remain responsive.
- [x] Instrument numeric TCP write duration, bytes written, queued-frame count, queue
  high-water mark, captured-frame rate, transmitted-frame rate, and overrun count without
  logging audio or credentials.
- [x] Confirm partial socket writes are fully drained and failed/zero-byte writes terminate
  the stream promptly rather than letting the capture queue continue filling.
- [x] When the first overrun occurs in a stream, emit one `ERROR` with code `2`, emit
  `STOP(CAPTURE_FAILURE)` when the socket remains writable, close the client, stop capture,
  and clear the queue deterministically.
- [x] Detect a runtime PDM read failure after capture has started, emit an explicit
  capture-failure error/stop, and close the client instead of continuing heartbeat-only
  traffic while claiming readiness.
- [x] Measure throughput before increasing `kAudioQueueFrames`. Increase the queue only if
  bounded batching and scheduling fixes still cannot absorb normal short Wi-Fi stalls; do
  not use a larger queue to hide a sustained sender deficit.
- [x] Preserve capture priority: network writes must never execute on or indefinitely
  block the microphone capture task.

Suggested initial sender policy:

```text
per main-loop pass:
  resume any partially accepted HUH1 message with a non-blocking socket send
  attempt up to 8 queued frames
  OR stop after a small elapsed-time budget
  send heartbeat when due
  yield only when no queued audio remains
```

Tune the batch and time budget from measurements rather than treating `8` as a permanent
product constant.

Latest failing measurement (2026-08-24): capture held approximately 49.2 frames/second while
the sender sustained only approximately 18.4 frames/second; a framework TCP write took as
long as 696 ms, the 50-frame queue reached its limit, and the stream ended with an explicit
overrun. The sender now bypasses the framework's retrying blocking write path for live audio,
uses `MSG_DONTWAIT`, and retains partially accepted HUH1 messages across bounded transport
passes. TCP keepalive is explicitly enabled in addition to configuring its probe timers. Both
firmware targets compile and the production build was flashed to the XIAO on 2026-08-24, but
the ten-minute success criterion remains open. The queue was deliberately not enlarged
because the measured deficit was sustained throughput, not merely a short Wi-Fi stall.

Success criteria on the Pixel 8 Pro:

- [ ] Sustain at least 10 minutes of canonical 50-frame/second streaming with zero queue
  overruns under normal trusted-LAN conditions.
- [ ] Android VAD receives frames continuously and creates a conversation from speech.
- [ ] Fifteen seconds of observed no-speech finalizes the conversation normally.
- [ ] Local Whisper produces a transcript and the session reports
  `SessionSource.EXTERNAL_DEVICE`.
- [ ] The XIAO reports `completed >= 1`, `interrupted = 0`, and `overruns = 0` for the clean
  validation run.
- [ ] A deliberate network stall still fails honestly without blocking microphone capture,
  silently dropping frames, or wedging the next Android reconnection.

## Required architecture

Refactor the current `src/main.cpp` bring-up into focused components rather than adding
networking and framing directly to the existing recording loop:

```text
src/
  main.cpp
  audio/
    PdmAudioCapture.*
    AudioFrame.*
  protocol/
    HuhAudioProtocol.*
    HuhMessageWriter.*
  transport/
    TcpAudioTransport.*
  device/
    DeviceIdentity.*
  runtime/
    StreamingController.*
    ConnectionState.*
```

Names may adapt to the existing code style, but microphone capture, protocol encoding,
transport, device identity, and runtime state must remain independently testable. BLE
pairing components should be introduced only with the shared Android authentication contract.

## Phase 1: Preserve and extract microphone capture

- [x] Move PDM setup and reads out of `main.cpp` behind a reusable capture component.
- [x] Keep the canonical format at 16,000 Hz, mono, signed PCM16.
- [x] Produce exact 20 ms frames: 320 samples / 640 bytes.
- [x] Verify the ESP/I2S byte order is little-endian before transmission.
- [x] Handle short reads by accumulating bytes until one complete frame exists.
- [x] Never reinterpret an incomplete frame as audio.
- [x] Keep microphone capture independent from network write latency.
- [x] Preserve the `xiao_esp32s3_sense_sd_test` WAV behavior as a separate build mode.
- [x] Preserve default operation without a microSD card.

Success: a deterministic test or serial diagnostic confirms continuous 640-byte canonical
frames without changing the existing SD recording result.

## Phase 2: Implement Huh? Audio Protocol v1

- [x] Implement the exact `HUH1` 12-byte envelope from the Android protocol document.
- [x] Encode envelope integers in big-endian network order.
- [x] Keep PCM sample bytes little-endian.
- [x] Enforce the 65,536-byte maximum payload.
- [x] Implement `HELLO`, `START`, `AUDIO`, `HEARTBEAT`, `STOP`, and `ERROR`.
- [x] Generate a fresh random UUID for every stream/connection epoch.
- [x] Start each stream with sequence number `0` and first-sample index `0`.
- [x] Increment sequence once per `AUDIO` frame and sample position by exactly `320`.
- [x] Include explicit stop/error reasons rather than relying on socket closure.
- [x] Reject or stop safely if an outgoing message cannot be represented within bounds.
- [x] Never place credentials, pairing secrets, or raw audio in logs.

### Required HELLO fields

- Stable, non-secret device ID
- User-facing display name
- Manufacturer and model
- Firmware version
- Protocol version 1
- Sample rate 16,000
- One channel
- 16-bit sample width
- 320 samples per frame

Success: firmware-generated byte fixtures decode correctly in Android
`HuhAudioFrameReaderTest`, including known positive and malformed cases.

## Phase 3: Bounded capture and flow control

- [x] Use a bounded RAM frame queue between PDM capture and TCP transmission.
- [x] Never block microphone reads indefinitely on a socket write.
- [x] Track and expose an overrun counter.
- [x] Define POC receiver readiness as an accepted TCP connection; explicit advertised
  receive-window flow control is deferred until measurements show it is needed.
- [x] Prioritize live frames over any future recovery/backfill frames.
- [x] Treat queue overflow as a visible degraded/interrupted stream, not silent loss.
- [x] Send monotonic sequence and sample-position values even after transient write delays.
- [x] Confirm that no-SD loss beyond the RAM window is permanent and reported honestly.

Initial target: buffer no more than a small, explicitly configured number of seconds. The
exact size must be measured against PSRAM use, capture stability, and reconnection goals.

## Phase 4: Local Wi-Fi audio transport

- [x] Add an interactive USB serial provisioning mode for Wi-Fi SSID and password.
- [x] Do not echo or log the Wi-Fi password.
- [x] Store provisioned credentials in ESP32 NVS rather than source or build flags.
- [x] Add a serial `wifi status` command that reports connection state, SSID, local IP,
  signal strength, and TCP-server state without exposing the password.
- [x] Add a serial `wifi reset` command that erases stored credentials and returns to
  provisioning mode.
- [x] Handle missing, invalid, and changed credentials without blocking forever or
  repeatedly rebooting.
- [x] Connect to the provisioned trusted LAN and print the assigned local IP clearly.
- [x] Add a XIAO-hosted TCP server on port `8765`.
- [ ] Advertise the service through mDNS when using shared-LAN discovery.
- [x] Use a fixed, documented TCP port (`8765`).
- [x] Send `HELLO` immediately after the Android TCP client connects.
- [x] For this one-way POC, treat an accepted TCP connection as receiver readiness before
  `START` and `AUDIO`; define an explicit bidirectional readiness message in a later
  protocol revision if measurements show it is needed.
- [x] Send heartbeats even when no conversation audio is being sent.
- [x] Detect half-open connections with bounded timeouts.
- [x] Close the socket and start a new stream epoch after reboot or unrecoverable loss.
- [x] Never concatenate audio across connection epochs.
- [x] Keep the server available for a new Android connection after an unexpected client
  disconnect, using a bounded accept loop that does not block microphone capture.
- [ ] Stop streaming and release the client promptly after an explicit Pause or Turn Off.
- [x] Do not embed SSIDs or passwords in source, build flags, logs, or firmware binaries.

For the trusted-LAN POC, `HELLO` plus Android's expected-device-ID validation identifies
the intended device but does not authenticate it. Do not describe this as secure pairing.
Authenticated association remains Phase 5 work.

Success: the Android `TcpExternalPcmSource` validates `HELLO`, accepts sustained audio,
detects heartbeats, and cleanly observes `STOP` on a local network with internet disabled.

### USB serial provisioning behavior

On a fresh device or after `wifi reset`, the serial console should present a bounded,
explicit setup flow similar to:

```text
Huh? Puck Wi-Fi setup
SSID: <user input>
Password: <hidden/not echoed>
Saving credentials…
Connecting…
Connected
IP: 192.168.1.123
HUH1 TCP server: 8765
```

- Do not print the password, even at debug log levels.
- Do not erase valid credentials during an ordinary network outage.
- Permit reprovisioning without reflashing firmware.
- Keep the serial parser bounded; reject oversized input and clear sensitive temporary
  buffers after saving.
- If NVS initialization or persistence fails, report the failure and do not claim the
  network is configured.

## Phase 5: BLE discovery, association, and control

- [x] Implement explicit TCP `START`, `HEARTBEAT`, and `STOP` control; accepting a socket
  no longer starts the microphone.
- [x] Enforce a renewable 15-second TCP capture lease with monotonic heartbeat counters.
- [x] Record active captures to a temporary WAV on microSD and finalize atomically on
  explicit stop or lease expiry.
- [x] Separate capture completion from file transfer with resumable `FETCH`, offset-tagged
  `FILE_CHUNK`, `FILE_END`, and deletion-authorizing `ACK` messages.
- [x] Add a Python reference controller and an end-to-end socket contract test.

### Hardware validation run (2026-08-29)

- [x] Android-parity Python client completed an explicit-stop capture and verified
  `START`/heartbeat/`STOP`/`FETCH`/`FILE_CHUNK`/`FILE_END`/`ACK` on the connected XIAO.
- [x] Heartbeat-drop test finalized a recording after the 15-second control lease expired,
  transferred it, and acknowledged SD deletion with `STOP(CONTROL_LEASE_EXPIRED)`.
- [x] Injected TCP disconnect during transfer reconnected and resumed from the exact local
  partial-file offset before acknowledging deletion.
- [ ] Repeat the same flow through the Android app on the Pixel 8 Pro, including VAD and
  session persistence; this remains the next integration gate.

- [x] Define and advertise a versioned Huh? device-information/control BLE service.
- [x] Advertise the versioned service UUID, display name, and public stable application identity needed for explicit companion-device selection.
- [ ] Support all commands for status, configure network, start, pause, resume, and turn off.
  `STATUS`, `PAUSE`, and `STOP` are wired; `START`/`RESUME` explicitly require the TCP
  stream handshake, and BLE Wi-Fi configuration remains pending.
- [ ] Require an authenticated BLE `KEEP_ALIVE` every 5 seconds while listening and expire
  the capture lease after 15 seconds without renewal.
- [ ] On BLE disconnect or capture-lease expiry, stop PDM capture and TCP audio, close the
  current SD segment cleanly, clear queued frames, and require a new explicit `START`.
- [ ] Keep the BLE capture lease distinct from the HUH1 TCP heartbeat; neither transport
  alone may silently renew the other's authorization/liveness contract.
- [ ] Report microphone, storage, queue-overrun, Wi-Fi, and authentication errors.
- [x] Keep the stable application device ID separate from the BLE MAC address.
- [ ] Store pairing material in protected nonvolatile storage where feasible.
- [ ] Require authenticated selection before accepting TCP audio/control peers.
- [ ] Implement a physical or otherwise explicit reset/forget-pairing path.
- [ ] Do not automatically begin capture merely because an unknown phone is nearby.

### BLE hardware validation run (2026-08-29)

- [x] Windows discovered `Huh? XIAO` advertising the version-1 service UUID.
- [x] Windows read the public identity containing the stable device ID, model, and firmware version.
- [x] An unpaired Windows client was rejected when reading the authenticated status characteristic.
- [x] BLE advertising and the existing Wi-Fi HUH1 capture-list service operated concurrently.

Success: Android can associate one device, reconnect to it, forget it, and associate a
second device without accepting audio from an unselected peer.

## Phase 6: Runtime state and failure behavior

Implement an explicit state machine similar to:

```text
UNPAIRED -> DISCONNECTED -> CONNECTING -> READY -> STREAMING
                                  |          |         |
                                  +------ RECONNECTING -+
                                             |
                                           ERROR
```

- [ ] Pause stops audio transmission/capture behavior as defined by the control contract.
- [ ] Turn Off safely emits `STOP`, closes transport, and releases the microphone.
- [x] A device reboot creates a new connection epoch and stream UUID.
- [x] Wi-Fi or phone loss does not permanently wedge capture; hardware recovery timing
  remains to be measured.
- [x] Optional SD absence is informational, not fatal.
- [x] Microphone/capture failure emits an error and stops claiming readiness.
- [x] Storage failure is reported only when SD-backed behavior is actually enabled.
- [x] Logs contain numeric counters and state changes, never audio or secrets.

## microSD recovery

The active finalized capture can now be fetched by capture ID and byte offset and is kept
until acknowledged. Remaining work is a persistent boot-time manifest/list command for
discovering and recovering older unacknowledged captures after device restart.

## Verification

- [x] Default SD-free build compiles and boots without a card.
- [x] SD test build still produces a valid 320,044-byte ten-second WAV file.
- [ ] Known PCM pattern arrives on Android with exact sample values and ordering.
- [x] Protocol fixtures match Android for every message type.
- [x] Python socket integration covers connect-without-capture, explicit start, renewable
  heartbeats, stop, fetch, length verification, and acknowledgement.
- [x] TCP writes split/coalesce without affecting Android framing in host fixtures.
- [ ] Duplicate-frame behavior is deterministic.
- [ ] Deliberate short gaps are reported and repaired by Android as specified.
- [ ] Gaps over 200 ms interrupt rather than hide loss.
- [ ] Quiet PCM does not look like a dead network connection.
- [x] Queue saturation increments an overrun counter and never silently drops data.
- [ ] Phone screen-off/background operation sustains a stream.
- [ ] Wi-Fi loss, BLE loss, device reboot, and phone disconnect recover within bounds.
- [ ] No traffic leaves the local device link and operation works without internet access.
- [ ] Extended testing records battery, temperature, reconnect count, and packet-gap data.
- [x] Fresh-device serial provisioning persists across reboot.
- [x] Wrong credentials produce a recoverable error and can be replaced without reflashing.
- [ ] `wifi reset` removes the saved network and re-enters provisioning mode.
- [ ] Serial logs and build artifacts do not contain the test Wi-Fi password.
- [x] The printed IP and port allow the Pixel 8 Pro to reach the XIAO TCP server.

## Definition of done

The firmware milestone is complete when an explicitly associated Android phone can discover
the device, authenticate it, receive correctly framed canonical audio for an extended run,
observe honest pause/stop/error behavior, and recover from ordinary connection loss without
blocking microphone capture or silently losing/reordering audio.

Gemma interpretation remains entirely on Android and is never invoked or represented by
the firmware protocol.

## Robustness and future hardware-control work

- [x] Scan `/captures` at boot and expose a `captures` serial command so finalized WAVs retained across reset are visible and recoverable.
- [x] Add a bounded, transport-neutral `HHC1` control-message parser for future BLE/control adapters (status/start/pause/resume/stop); no unauthenticated mutating transport is enabled yet.
- [ ] Bind the control protocol to authenticated BLE pairing and Android controls.
- [x] Add protocol-level `LIST_CAPTURES`/`CAPTURE_INFO` recovery and ID/offset transfer for files retained across a device reboot.
- [x] Validate finalized WAV headers before advertising them and repair aligned `.part` captures after interrupted power at boot.
