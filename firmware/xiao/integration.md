# Android Receiver Integration

This document defines what the Huh? Android application needs to know and do to
receive audio from the XIAO ESP32S3 Sense. It is an integration contract and implementation
guide. The Android trusted-LAN receiver and matching firmware transport are implemented;
the XIAO's real LAN connection and HUH1 `HELLO` have been verified. Android audio, VAD,
session persistence, and Whisper validation remain outstanding.

## Current baseline

- The firmware can initialize the Sense PDM microphone at 16 kHz, 16-bit mono.
- The optional `xiao_esp32s3_sense_sd_test` build records `/test.wav` to microSD.
- The default firmware build does not require an SD card.
- A microSD card may remain inserted during the default streaming build, but it is not
  mounted or used. It is used only by the explicit `xiao_esp32s3_sense_sd_test` build;
  there is no live-stream backfill yet.
- The firmware accepts one Android TCP client on port `8765`, sends `HELLO`, `START`,
  canonical `AUDIO`, five-second `HEARTBEAT`, and explicit `STOP`/`ERROR` messages.
- Wi-Fi credentials are provisioned through USB serial and stored in ESP32 NVS. They are
  not embedded in source/build flags or printed by the firmware.
- Each accepted TCP connection gets a fresh random stream UUID and sequence/sample indexes
  restart at zero. Capture stops and its bounded queue is cleared on disconnect.
- The Android app has a hardware-neutral `StreamingPcmSource`, HUH1 protocol parser,
  sequence/gap handling, and bounded local-TCP receiver.
- Android manually configures a trusted-LAN host and TCP port (default `8765`) for the POC,
  requests Android 17 local-network access, and runs reception in the existing foreground
  service without requiring phone-microphone permission.
- External captures use `SessionSource.EXTERNAL_DEVICE`; legacy stored `XIAO` values are
  mapped to that hardware-neutral source.
- Android persists device/transport diagnostics, queues eligible audio for local Whisper,
  and retains the existing delete-after-success behavior.
- No audio, transcript, identifier, or diagnostic may leave the phone/XIAO link or be sent
  to a cloud service.

### Verified hardware checkpoint (2026-08-24)

- The supplied external Wi-Fi/Bluetooth antenna was installed on the XIAO's IPEX connector.
- The XIAO joined a 2.4 GHz WPA2-PSK LAN and received DHCP address `192.0.2.1`.
- Signal strength measured `-47 dBm` after antenna installation.
- The TCP server listened on port `8765` and was reachable from another LAN computer.
- The first received envelope was a valid `HUH1` protocol-v1 `HELLO` message (`type=1`,
  payload length 83 bytes).
- The installed microSD card passed the physical recording test: `/test.wav` was reopened
  and verified at exactly 320,044 bytes (44-byte header plus 320,000 audio bytes).
- `192.0.2.1` is the address observed in this test session, not a protocol constant.
  Android should use the current address printed by `wifi status`; DHCP may change it.
- Before the antenna was installed, scans could see the SSID weakly but association ended
  with `AUTH_EXPIRE`/`ASSOC_EXPIRE`. Seeed warns that this model may be unable to connect
  without the external antenna, so antenna installation is a required bring-up check.

## Trusted-LAN POC connection model

For this milestone, the XIAO and phone join the same trusted Wi-Fi LAN. The XIAO hosts a
TCP server on port `8765`; Android connects using a manually entered IP/host and may check
the stable device ID from `HELLO`. An accepted TCP connection is receiver readiness, after
which firmware sends `START` and begins capture. No SD backfill is used.

Raw audio is 32,000 bytes/second before framing. BLE GATT may carry that under favorable
conditions, but Wi-Fi provides substantially more margin for sustained capture. BLE-only
audio can be evaluated later as a separate, explicitly tested transport. BLE
discovery/control and authenticated association remain the intended follow-up.

### Firmware provisioning and connection

1. Upload the default `xiao_esp32s3_sense` environment.
2. Attach the supplied external Wi-Fi/Bluetooth antenna securely to the XIAO IPEX
   connector before diagnosing network behavior.
3. Open USB serial at 115200 baud and follow the SSID/password prompt. The firmware does
   not echo the password.
4. Use `wifi status` to read the assigned IP and confirm that the TCP server is listening.
5. On Android, open **Huh? > Settings > Configure Huh? Puck**, enter that IP and `8765`,
   and optionally enter the expected stable device ID.
6. Start external-device listening. Android connects; the XIAO then starts capture.

Use `wifi setup` to replace credentials and `wifi reset` to erase them. Connection attempts
time out after 15 seconds and retry every 30 seconds without erasing valid credentials or
rebooting. Firmware logs may show the SSID but never the password.

As a convenience, firmware developers may copy `wifi.local.example.ini` to the Git-ignored
`wifi.local.ini` and run PlatformIO's `provision-wifi` target. This first uploads the normal
credential-free image and then sends the local values over USB serial into NVS. The file is
never compiled into the firmware or printed to build output. It remains a plaintext local
secret and may be deleted after provisioning. Interactive serial provisioning remains the
fallback. Exclamation marks and other ordinary INI characters do not require shell
escaping. Matching outer single or double quotes are accepted and removed by the helper.

## Wireless firmware diagnostics and updates

The production firmware exposes two development-only services on the same trusted LAN.
Both use the XIAO's current numeric IP address; mDNS discovery and advertisement are
explicitly disabled.

| Service | Transport | Port | Purpose |
| --- | --- | --- | --- |
| HUH1 audio/control | TCP | `8765` | Android audio capture and retained-file transfer |
| Wireless diagnostics | TCP | `8766` | Read-only PlatformIO-compatible status stream |
| Firmware OTA | UDP/TCP callback | `3232` | Password-protected PlatformIO firmware upload |

The diagnostics endpoint accepts one client and emits a bounded text status line about
every two seconds. Its current fields are:

- `uptime_ms`: firmware uptime, not a UTC timestamp;
- `state`: the same high-level state published over BLE (`DISCONNECTED`, `READY`,
  `CONNECTED`, or `CAPTURING`);
- `ip` and `rssi`: current LAN address and Wi-Fi signal strength;
- `sd`: removable-storage availability;
- `ota`: direct-IP OTA readiness;
- `completed` and `interrupted`: capture-stream counters since boot;
- `heap`: currently free ESP32 heap bytes.

Diagnostics writes are non-blocking. A slow or disconnected monitor must never delay
microphone capture, SD persistence, HUH1 control, or transfer recovery. The endpoint does
not send audio, transcript content, Wi-Fi credentials, OTA credentials, or other secrets.

BLE lifecycle events are emitted as separate lines when a diagnostics client is connected:

```text
uptime_ms=<monotonic-ms> event=ble connected authentication_required=true
uptime_ms=<monotonic-ms> event=ble authentication result=success
uptime_ms=<monotonic-ms> event=ble command received=PLAY_TEST_SOUND request_id=<id>
uptime_ms=<monotonic-ms> event=ble response request_id=<id> result=OK
uptime_ms=<monotonic-ms> event=ble disconnected
```

The bounded firmware queue retains only the newest eight pending BLE events and discards
older ones if a burst occurs. Events are not persisted, and only report lifecycle state,
command names, request IDs, and safe result codes—not BLE addresses, pairing codes, or
command payload contents.

Connect a PlatformIO monitor directly to the current address:

```text
pio device monitor --port socket://192.0.2.1:8766
```

`192.0.2.1` is the currently observed DHCP address, not a protocol guarantee. If it
changes, obtain the new address from the router/DHCP client list or USB `wifi status`.
Because mDNS is intentionally disabled, clients must not depend on a `.local` hostname.

After an initial USB installation, PlatformIO can update the firmware directly by IP:

```text
pio run -e xiao_esp32s3_sense_ota -t upload
```

The `xiao_esp32s3_sense_ota` environment currently targets `192.0.2.1`, UDP port
`3232`, and the development OTA PIN `REDACTED_CREDENTIAL`. The firmware default and PlatformIO
`--auth` value must be changed together. This fixed PIN is suitable only for the present
private trusted-LAN prototype; a production credential must be unique, locally
provisioned, and excluded from source control and logs.

Firmware calls `ArduinoOTA.setMdnsEnabled(false)` before starting OTA. New OTA invitations
are handled only when no audio capture or HUH1 controller connection is active. If an
update has already started, it retains control until completion or failure; the HUH1
transport yields during that interval. A successful update reboots the XIAO. USB-C remains
the recovery path for a failed, incompatible, or network-inaccessible image.

### Verified wireless checkpoint (2026-09-02)

- PlatformIO connected to the diagnostics endpoint at `192.0.2.1:8766` while the
  XIAO was powered only by its LiPo battery.
- The device reported `READY`, mounted SD storage, stable RSSI near `-52 dBm`, OTA ready,
  and no completed or interrupted streams.
- An authenticated full firmware upload to `192.0.2.1:3232` completed successfully.
- The device rebooted and the diagnostics endpoint became reachable again without USB or
  mDNS.

## Audio contract

The Android receiver must accept this canonical stream:

| Property | Value |
| --- | --- |
| Encoding | Signed linear PCM |
| Sample rate | 16,000 Hz |
| Channels | 1 (mono) |
| Sample width | 16 bits |
| Byte order | Little-endian |
| Nominal frame duration | 20 ms |
| Samples per frame | 320 |
| PCM bytes per frame | 640 |

The wire stream contains PCM only, not a WAV header. WAV is a removable-card test format;
the Android pipeline already persists headerless little-endian `.pcm` files for Whisper.

The Android side must reject unsupported audio formats during the handshake rather than
silently resampling or reinterpreting them. If resampling is added later, it belongs in an
explicit source adapter with dedicated tests.

## Protocol-v1 envelope

The exact representation is frozen in `../../mobile/android/docs/HUH_AUDIO_PROTOCOL_V1.md`.
It carries:

- protocol version;
- message type;
- stable device ID and firmware version during `HELLO`;
- stream/session ID;
- monotonically increasing audio sequence number;
- first-sample index or equivalent monotonic stream position;
- payload length;
- audio format declaration;
- `START`, `AUDIO`, `HEARTBEAT`, `STOP`, and `ERROR` messages;
- an explicit end reason such as user stop, pause, disconnect, storage failure, or reboot.

Do not infer boundaries from TCP reads. TCP is a byte stream: one read may contain part of
a message or several messages. Use length-prefixed messages and validate all lengths before
allocating or copying. Put an upper bound on every message and reject malformed input.

The receiver must treat sequence numbers as authoritative. Duplicate frames are ignored.
Out-of-order frames may be reordered only within a small bounded window. A sequence gap is
recorded as a degraded stream; it must never be hidden from diagnostics.

Suggested gap behavior:

- For a known gap of 200 ms or less, insert the exact number of zero samples to preserve the
  timeline and mark the capture degraded.
- For a larger or unknown gap, finalize the valid partial conversation as interrupted and
  reconnect. Do not concatenate audio from two connection epochs into one conversation.

TCP already detects transport corruption and preserves ordering. Do not add a per-frame CRC
unless testing demonstrates a need; sequence and sample-position fields are still required
to detect application-level loss, reboot, and replay.

## Android architecture

Introduce a source abstraction instead of teaching VAD or transcription about networking.
A useful boundary is conceptually:

```kotlin
interface StreamingPcmSource {
    suspend fun capture(onFrame: (ShortArray) -> Unit)
    fun stop()
}
```

The existing phone-microphone capture and a new `XiaoAudioReceiver` should implement the
same contract. The source selected by the user feeds the existing 20 ms frame pipeline:

```text
XIAO transport
  -> validate and sequence frames
  -> convert little-endian bytes to ShortArray
  -> existing rolling pre-roll
  -> existing VAD and conversation state machine
  -> existing app-private .pcm writer
  -> existing durable Whisper queue
  -> SessionSource.XIAO
```

Do not route received bytes through Android `AudioRecord`, `AudioTrack`, or the media audio
route. The XIAO is a data source, not an Android microphone device, unless a future firmware
version deliberately implements a standard Bluetooth or USB audio profile.

Connection and capture state must be service-owned, not owned by a Compose screen or
activity. Expose immutable state to the UI using the app's existing unidirectional state
flow. Rotating the device, navigating away, or recreating the activity must not discard an
active stream.

Keep transport work, disk writes, VAD, and decoding off the main thread using structured,
cancellable coroutines. Use a bounded channel or buffer between socket reads and audio
processing. Never use an unbounded frame queue. If the consumer cannot keep up, report a
receiver-overrun error and preserve/finalize valid audio rather than silently dropping data.

## Android service behavior

The existing `ActiveListeningService` is microphone-specific. Refactor its audio source
behind the shared abstraction before adding XIAO reception. The conversation detector,
writer, persistence, and transcription queue should remain shared.

When XIAO capture is active, the service must:

- show a persistent notification naming the XIAO as the source;
- provide Pause/Resume and Turn Off actions;
- show Connecting, Waiting, Receiving, Reconnecting, Paused, and Error states honestly;
- stop receiving promptly when the user turns the mode off;
- use `START_NOT_STICKY` and never auto-start after boot;
- close sockets/GATT objects and finalize or discard partial files on cancellation;
- survive activity recreation but assume process death can end a live connection;
- recover already-finalized `.pcm` files through the existing durable transcription queue;
- avoid presenting a disconnected device as actively listening.

The service should not declare the `microphone` foreground-service type when it only
receives XIAO data. Select and validate an appropriate connected-device/data-transfer
foreground-service type against the target SDK before implementation. Keep phone-microphone
capture on its existing microphone service path.

## Android permissions and product decisions

### Wi-Fi audio

Direct LAN sockets require `android.permission.INTERNET`, even when all traffic stays local
and the product works without internet access. Huh? uses this permission only for a direct
connection to a user-selected local device; audio and transcripts must never be sent to an
internet or cloud service.

For apps targeting Android 17 / SDK 37, local-network access is blocked by default. The
implementation must either:

- use a system-mediated `NsdManager` device picker that grants access only to the selected
  device; or
- declare and request the `ACCESS_LOCAL_NETWORK` runtime permission, handle denial and
  revocation, and explain why the nearby device is needed.

Prefer the system-mediated picker if it can support stable reconnection to the XIAO. Test
the actual Android 17 behavior on the target Pixel 8 Pro; do not rely only on older Android
versions or an emulator.

### BLE control

On current Android versions, BLE discovery/connection generally requires appropriately
scoped `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` runtime permissions. Consider
`CompanionDeviceManager` for explicit user association and background-presence behavior.
Handle denial, Bluetooth being off, lost association, and permission revocation without a
dead-end screen.

### Phone microphone

The app still needs `RECORD_AUDIO` for its existing phone-microphone modes. XIAO-only
reception must not require microphone permission merely because the shared pipeline used to
be fed by `AudioRecord`.

## Pairing, identity, and privacy

- Pair only through an explicit user action from a visible screen.
- Assign each XIAO a stable, non-secret device ID and a user-editable display name.
- Do not use a BLE MAC address as the durable identity; addresses can change.
- Authenticate the selected device before accepting audio. A prototype may use a one-time
  code or secret exchanged during visible pairing, but unauthenticated LAN audio is not an
  acceptable final design.
- Store secrets in Android Keystore-backed storage and protected firmware storage where
  feasible. Never print credentials, pairing secrets, or raw audio to logs.
- Bind every audio stream to a fresh random session/connection nonce to prevent accidental
  replay or cross-device mixing.
- Accept audio only from the device the user selected, and provide a clear Forget Device
  action that removes credentials and association state.

Application-layer encryption is a release decision. WPA2/WPA3 protects a SoftAP link from
casual radio interception but does not authenticate every application peer on a shared LAN.
Threat-model the final topology before claiming the stream is secure.

## Receiver lifecycle and recovery

The receiver should use an explicit state machine such as:

```text
UNPAIRED -> DISCONNECTED -> CONNECTING -> READY -> STREAMING
                                  |          |         |
                                  +------ RECONNECTING -+
                                             |
                                           ERROR
```

- Use bounded exponential backoff with jitter for unexpected disconnections.
- Stop reconnecting immediately after Pause, Turn Off, Forget Device, or permission denial.
- A device reboot creates a new connection epoch and stream ID.
- Heartbeats detect a half-open connection when no audio/control messages arrive.
- Do not mistake quiet PCM samples for a dead connection; heartbeat and socket state are
  separate from VAD silence.
- Record connection loss, sequence gaps, reconnect count, and overrun locally as numeric
  diagnostics. Do not retain extra audio solely for diagnostics.
- If the XIAO later supports SD backfill, deduplicate by device ID, stream ID, and sample
  range before merging. Never transcribe the same recovered segment twice.

## Flow control

For this one-way POC, an accepted TCP connection is the ready state. The Android receiver
uses a 50-frame bounded channel and the XIAO uses a separate 50-frame (one-second) bounded
RAM queue. Socket writes never run on the microphone task. A firmware queue overflow emits
an explicit error/stop and ends that connection rather than silently dropping audio.

For a no-SD device, loss beyond the RAM window is permanent and must be surfaced as a gap.
For an SD-equipped device, firmware may spool unsent frames and later offer a backfill
range. Live frames take priority over backfill so recovery cannot destabilize current
capture.

Pixel testing exposed a sustained TCP sender deficit: the latest failing run captured about
49.2 frames/second while transmitting about 18.4 frames/second, a framework TCP write took up
to 696 ms, and the one-second queue saturated. Firmware now sends live audio through a
resumable non-blocking socket path, retains partially accepted HUH1 messages across bounded
transport passes, removes the active-stream loop delay, explicitly enables TCP keepalive,
records throughput/write/queue diagnostics, and emits `ERROR(2)` plus
`STOP(CAPTURE_FAILURE)` on the first overrun. The updated production build was flashed on
2026-08-24 but has not yet passed the ten-minute reliability criterion. The queue must not be
enlarged merely to conceal a sustained sender deficit.

## Recommended BLE control and SD role

Use BLE as a low-bandwidth control plane, not as a replacement for SD-backed audio capture
and resumable Wi-Fi file transfer. After explicit association, Android should write versioned commands for
`START`, `PAUSE`, `RESUME`, and `STOP`; the XIAO should expose state and error notifications.
An unknown or merely nearby phone must not start the microphone. Require an encrypted,
authenticated BLE connection (and later bind the selected BLE identity to the TCP peer).

Implemented TCP reference behavior:

1. Connecting sends only `HELLO`; a controller-generated capture UUID in `START` opens a
   new epoch and begins canonical PCM/WAV recording on SD.
2. `PAUSE` stops capture after closing the current segment cleanly; `RESUME` begins a new
   segment/stream boundary rather than hiding elapsed time.
3. `STOP` or a 15-second control-lease expiry flushes and atomically finalizes the WAV,
   releases the microphone, and reports `STOP` before file transfer begins.
4. `LIST_CAPTURES` returns zero or more `CAPTURE_INFO(capture_id, total_bytes)` messages and
   a terminating `CAPTURE_LIST_END(count)`, including validated captures found after reboot.
5. `FETCH(capture_id, offset)` transfers offset-tagged `FILE_CHUNK` messages followed by
   `FILE_END(total_bytes)`. `ACK(capture_id)` authorizes deletion from SD.
6. A network failure never invalidates the finalized WAV. Retained captures remain
   discoverable and fetchable by ID and offset after reconnect or reboot.

### BLE capture lease

The provisional version-1 BLE service is implemented with these UUIDs:

- Service: `7d2e0001-6f9b-4af7-ae8c-5e4f48554831`
- Public identity (read): `7d2e0002-6f9b-4af7-ae8c-5e4f48554831`
- Authenticated status (read/notify): `7d2e0003-6f9b-4af7-ae8c-5e4f48554831`
- Authenticated command (write): `7d2e0004-6f9b-4af7-ae8c-5e4f48554831`
- Authenticated response (read/notify): `7d2e0005-6f9b-4af7-ae8c-5e4f48554831`

The identity value is `v1|device_id|model|firmware_version`. Commands use the bounded
`HHC1 | request_id_u32_le | verb` envelope. Pairing requires bonding, Secure Connections,
MITM protection, and a configurable six-digit passkey. Development builds currently use
static PIN `REDACTED_CREDENTIAL`; `HUH_BLE_USE_STATIC_PASSKEY=0` restores a boot-generated PIN shown only
over USB serial.
`STATUS`, `PAUSE`, `STOP`, and `PLAY_TEST_SOUND` are wired; `START` and `RESUME` currently return
`TCP_START_REQUIRED` because stream ownership and the renewable control lease must be
established with the companion app before microphone capture starts.

`PLAY_TEST_SOUND` produces a short, non-blocking LED pattern and optionally an ascending
chime through a passive piezo connected between XIAO D1/GPIO2 and GND. The Sense expansion
board has no speaker, so audible output requires the external piezo. The firmware rejects the command with `BUSY` while recording
or already playing, and responds with `AUDIO_OUTPUT_UNAVAILABLE` when compiled with
`HUH_TEST_TONE_PIN=-1`. The Android button should issue one command per tap and treat the
matching response request ID as delivery confirmation.

Treat active listening as a renewable control lease rather than a command that remains active
indefinitely. The TCP reference controller sends `HEARTBEAT`; the future authenticated
BLE service will send the equivalent `KEEP_ALIVE` control message every 5 seconds. Each
valid message renews a 15-second lease measured using the
XIAO's monotonic clock.

If the BLE connection closes or the lease expires, the XIAO must:

1. stop accepting new microphone frames immediately;
2. emit `STOP(CONTROL_LEASE_EXPIRED)` over TCP when the socket is still writable;
3. flush and close the current SD recording so it remains structurally valid;
4. clear queued live frames, release the microphone, and return to a non-listening state;
5. keep BLE advertising available for an explicit authenticated reconnection and `START`.

Android's foreground listening service owns lease renewal. UI/activity recreation must not
interrupt it, while Pause, Turn Off, Bluetooth permission loss, association removal, or
service cancellation must stop renewal deliberately. Reconnecting does not implicitly
restart capture: Android must issue a new `START`, preventing a phone that has moved out of
range from leaving the XIAO recording indefinitely.

BLE link supervision/disconnect callbacks provide the fast path, but the application lease
is still required because an apparently connected GATT session can become stale. TCP and
BLE renewals will feed the same capture-lease manager, but a capture has only one selected
controller/transport owner at a time; unrelated transport traffic cannot renew its lease.

This hybrid keeps internet connectivity available through the user's LAN, gives the phone
reliable start/stop control, and uses SD as durable capture rather than forcing high-rate
uncompressed audio through BLE. Exact service/characteristic UUIDs, command acknowledgments,
pairing material, and recovery manifests remain to be frozen before implementation.
The TCP implementation and Python reference tests use 5-second renewal / 15-second expiry.
Android background behavior and the future BLE implementation still require validation.

## Timestamps and session metadata

- Audio ordering is based on sequence/sample position, not phone wall-clock arrival time.
- The phone assigns the persisted session's UTC timestamp when it accepts the stream or
  detects the conversation start.
- Firmware uptime may be included for diagnostics but is not a UTC clock.
- A device-reported UTC time is untrusted unless a future time-synchronization contract is
  explicitly implemented.
- Persist `SessionSource.XIAO`, stable device ID, firmware version, protocol version,
  connection type, gap count, and interrupted/degraded status with the session diagnostics.

## Failure behavior

The UI and logs must distinguish at least:

- device not paired;
- Bluetooth disabled or permission denied;
- local-network permission denied;
- Wi-Fi unavailable or wrong network;
- external XIAO antenna missing, damaged, or poorly seated;
- configured SSID absent from the XIAO's 2.4 GHz scan;
- access point visible but association rejected or expired, including the numeric ESP32
  disconnect reason;
- device found but authentication failed;
- incompatible firmware/protocol/audio format;
- connection lost and reconnecting;
- sequence gap or receiver overrun;
- insufficient phone storage;
- XIAO SD unavailable (informational when SD is optional);
- XIAO microphone or internal capture failure.

Preserve a valid partial conversation when it contains enough detected speech and can be
finalized safely. Retain its `.pcm` file if transcription fails, matching the app's existing
retry behavior. Delete malformed, empty, or ineligible temporary files.

## Verification checklist

Before declaring the integration complete, verify on the Pixel 8 Pro running Android 17:

- current manual endpoint for the next test: `192.0.2.1:8765` (reconfirm with
  `wifi status` first because DHCP may change it);

- pairing, forgetting, and pairing a second XIAO;
- default firmware with no microSD inserted;
- exact 16 kHz mono sample interpretation using a known tone/test vector;
- frame boundaries split and coalesced across arbitrary TCP reads;
- duplicate, missing, malformed, oversized, and out-of-order messages;
- phone screen off and app backgrounded during a sustained stream;
- Activity/process recreation and explicit service stop;
- Bluetooth/Wi-Fi toggled off and back on;
- permission denial, revocation, and later grant;
- device power loss, reboot, range loss, and bounded reconnection;
- quiet audio versus a dead connection;
- low phone storage and slow consumer/backpressure behavior;
- VAD segmentation and Whisper output from XIAO audio;
- `SessionSource.XIAO` persistence and history presentation;
- no internet connectivity and no traffic beyond the local device link;
- battery, thermal, packet-gap, and reconnect measurements during an extended run.

Unit-test frame parsing, endianness, length validation, sequence handling, gap accounting,
state transitions, and backoff deterministically. Add integration tests around partial-file
finalization and durable transcription handoff. Treat a successful socket connection or
compile as insufficient evidence of end-to-end capture reliability.

## Decisions still to freeze

The protocol document and the first firmware foundation now freeze:

- the exact HUH1 protocol-v1 envelope and message payloads;
- a one-second / 50-frame bounded live-audio RAM queue for initial measurement;
- no microSD backfill in the first implementation;
- canonical 20 ms PCM frames and sequence/sample-position behavior.

The Android POC now freezes these initial choices:

1. Shared trusted LAN with manual host entry until discovery is implemented.
2. Android 17 `ACCESS_LOCAL_NETWORK` permission rather than a system device picker.
3. XIAO hosts a TCP server on port `8765`; Android connects as the client.
4. Android uses bounded exponential reconnect delays with jitter and stops after eight
   unsuccessful reconnect attempts.

The following still must be agreed before the integration can be considered paired and
secure rather than a trusted-LAN POC:

1. mDNS service type/name for replacing manual address entry.
2. BLE service/characteristic UUIDs and versioned command payloads.
3. Pairing/authentication, nonce exchange, protected secret storage, and application-layer
   encryption approach.
4. Whether BLE-only compressed audio is worth a separate experiment.
