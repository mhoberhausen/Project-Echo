# Huh Hardware Firmware

Initial firmware bring-up for the Seeed Studio XIAO ESP32S3 Sense.

## Build variants

The default `xiao_esp32s3_sense` build provides the trusted-LAN HUH1 audio server and does
not initialize or require a microSD card.

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

The default build now captures exact 20 ms / 640-byte PCM frames on a dedicated task and
places them in a bounded one-second queue. It reports frame and overrun counters without
logging audio. The transport-independent HUH1 protocol-v1 encoder implements HELLO, START,
AUDIO, HEARTBEAT, STOP, and ERROR with network-order metadata and untouched little-endian
PCM samples.

The default build now accepts one Android TCP client on port `8765`, emits a fresh stream
epoch, and starts microphone capture only after the connection is accepted. It sends a
heartbeat every five seconds and terminates an overrun or failed capture with explicit
HUH1 error/stop messages. This is an unauthenticated trusted-LAN POC, not secure pairing.

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
3. Connect the board and select **PlatformIO: Upload** for the default, SD-free TCP build,
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
