# Huh Hardware Firmware

Initial firmware bring-up for the Seeed Studio XIAO ESP32S3 Sense.

## Build variants

The default `xiao_esp32s3_sense` build provides the trusted-LAN HUH1 control/file-transfer
server and requires a writable microSD card before capture can start. A TCP connection by
itself never starts the microphone.

The optional `xiao_esp32s3_sense_sd_test` build performs the first recording milestone.
On every boot, it:

1. Mounts the Sense expansion board's microSD card.
2. Starts the built-in PDM microphone at 16 kHz, 16-bit mono.
3. Records ten seconds of audio.
4. Replaces `/test.wav` with the new recording.
5. Finalizes the WAV header and safely closes the file.

The SD test excludes the Wi-Fi provisioning and TCP transport sources. Neither build uses
Bluetooth, a cloud service, or telemetry.

## Protocol foundation

The default build captures exact 20 ms / 640-byte PCM frames on a dedicated task and writes
them directly to a temporary WAV on microSD. HUH1 is bidirectional: the controller sends
`START`, renewable `HEARTBEAT`, `STOP`, `FETCH`, and `ACK`; firmware responds with `HELLO`,
`START`, `STOP`, `FILE_CHUNK`, `FILE_END`, or `ERROR`. Metadata is network-order and PCM is
untouched 16 kHz, 16-bit, little-endian mono.

`START` arms a 15-second capture lease. A matching heartbeat normally arrives every five
seconds. Explicit `STOP` or lease expiry stops the microphone, finalizes and atomically
renames the WAV, and immediately reports `STOP`. File transfer is a separate explicit
`FETCH` operation and may resume at a byte offset. Firmware deletes the WAV only after a
matching `ACK`. This is still an unauthenticated trusted-LAN POC; BLE pairing/control is
future work.

## One-time Wi-Fi setup

After uploading the default build, open the PlatformIO serial monitor at 115200 baud. A
fresh device prompts for the Wi-Fi SSID and password. The firmware does not echo or log the
password and stores the credentials in ESP32 NVS.

Available serial commands:

- `wifi status` reports the SSID, connection state, IP address, signal strength, and TCP
  server/client state without showing the password.
- `wifi setup` replaces the saved network through the bounded interactive prompt.
- `wifi reset` erases the saved network and returns to setup mode.

### Optional local configuration file

To upload and provision without typing credentials into the serial monitor:

1. Copy `wifi.local.example.ini` to `wifi.local.ini`.
2. Enter the SSID and password in `wifi.local.ini`.
3. Run the default environment's **Upload and provision Wi-Fi** PlatformIO target, or run:

   ```text
   platformio run -e xiao_esp32s3_sense -t provision-wifi
   ```

The target uploads the ordinary credential-free firmware, then reads the local file and
sends its values privately over USB serial for storage in NVS. It does not print the
password or add it to the firmware binary. `wifi.local.ini` is ignored by Git; the example
file contains placeholders and is safe to commit. The local file itself is plaintext, so
keep it only on a trusted computer and delete it after provisioning if desired.

Once connected, enter the printed IP and port `8765` in **Huh? > Settings > Configure Huh?
Puck**. The phone and puck must be on the same trusted Wi-Fi LAN. mDNS, BLE onboarding,
peer authentication, and application-layer encryption remain later work.

## Prepare the hardware

- Insert a FAT32-formatted microSD card of 32 GB or smaller.
- Attach the Sense expansion board firmly to the XIAO ESP32S3.
- Connect the XIAO to the computer over a data-capable USB-C cable.

## Build and upload with PlatformIO

1. In VS Code, choose **File > Open Folder** and open this `firmware` folder.
2. Wait for PlatformIO to install the pinned ESP32 Arduino toolchain.
3. Connect the board and select **PlatformIO: Upload** for the default SD-backed TCP build,
   or use **Upload and provision Wi-Fi** with the optional local file above.
4. Open **PlatformIO: Serial Monitor** at 115200 baud.
5. To run the recording test, select the `xiao_esp32s3_sense_sd_test` environment
   and upload that variant instead.
6. Reset the board. Speak during the ten-second countdown.
7. Wait for `Recording complete. It is safe to remove the microSD card.`
8. Remove the card and play `test.wav` on the computer.

The code tries SD chip-select GPIO 3 first and GPIO 21 second because Seeed Sense
board revisions and examples use both mappings.

If upload cannot find the board, hold **BOOT**, tap **RESET**, release **BOOT**, and
try the upload again.

## Expected recording

`test.wav` should be 320,044 bytes: a 44-byte PCM WAV header followed by 320,000
bytes of audio (16,000 samples/second × 2 bytes/sample × 10 seconds).

## Verification

- Both ESP32 build variants are compiled with PlatformIO.
- Protocol and frame-assembly fixtures live under `test/test_native_protocol`.
- The host fixtures cover short reads, envelope layout, every message type, endian
  boundaries, payload limits, split writes, and stream sequence/sample progression.

### Python HUH1 receiver

`tools/huh_receiver.py` is the dependency-free reference controller for firmware smoke
tests. It connects, validates `HELLO`, issues `START`, renews the capture lease, sends
`STOP`, fetches the finalized WAV, validates offsets and length, and acknowledges receipt.

Record for 30 seconds and fetch a WAV:

```text
python tools/huh_receiver.py 192.0.2.1 --duration 30 --output-wav capture.wav
```

Deliberately stop heartbeats after two seconds and verify the 15-second lease timeout:

```text
python tools/huh_receiver.py 192.0.2.1 --drop-heartbeats-after 2 --read-timeout 60 --output-wav timeout.wav
```

The receiver returns a nonzero exit code for malformed messages, incorrect ownership,
truncation, transfer offsets or lengths, remote errors, missing `STOP`, and failed transfer.
Use `--json` for a machine-readable final result.

Run its host-side unit tests with:

```text
python -m unittest discover -s test -p "test_python_receiver.py"
```
