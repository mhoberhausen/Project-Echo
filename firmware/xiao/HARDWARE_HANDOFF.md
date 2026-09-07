# Project Echo / “Huh?” Hardware Handoff

## Current hardware

- Seeed Studio XIAO ESP32S3 Sense
- Board footprint: approximately 21 × 17.8 mm
- Approximate height with Sense expansion board: 15 mm
- Integrated ESP32-S3, PDM microphone, microSD, BLE, Wi-Fi, USB-C, battery charging, and user LED
- The Sense board has a microphone but no onboard speaker

## Text wiring diagram

```text
                    PROJECT ECHO / “HUH?” PUCK
                    ===========================

                         3.7 V protected LiPo
                        ┌─────────────────────┐
                        │  BAT+          BAT- │
                        └────┬────────────┬───┘
                             │            │
                             ▼            ▼
                          XIAO BAT        GND
                    ┌────────────────────────────┐
USB-C ─────────────▶│ Seeed XIAO ESP32S3 Sense  │
                    │                            │
                    │ GPIO42 ── PDM clock ───────┼──▶ Sense microphone
                    │ GPIO41 ◀─ PDM data ────────┼─── Sense microphone
                    │                            │
                    │ GPIO7  ── SPI clock ───────┼──▶ microSD SCK
                    │ GPIO8  ◀─ SPI MISO ────────┼─── microSD MISO
                    │ GPIO9  ── SPI MOSI ────────┼──▶ microSD MOSI
                    │ GPIO3* ── SD select ───────┼──▶ microSD CS
                    │                            │
                    │ GPIO21 ── active-low LED ──┼──▶ onboard user LED
                    │                            │
                    │ GPIO2 / D1 ── PWM tone ────┼──▶ optional passive piezo
                    │                            │        │
                    │ GND ───────────────────────┼────────┘
                    └────────────────────────────┘

* Firmware checks GPIO3 and GPIO21 for SD chip-select because Sense revisions
  have used different mappings. If GPIO21 is serving as SD chip-select, the
  firmware disables the LED signal to prevent interference.
```

## Optional piezo wiring

```text
XIAO D1 / GPIO2 ───── passive piezo positive terminal
XIAO GND ──────────── passive piezo negative terminal
```

Use a small passive piezo intended for GPIO-level signaling. Do not connect a
normal low-impedance speaker directly to GPIO2; it requires a transistor or
audio amplifier. The piezo is optional because the onboard LED provides the
default connection-verification signal.

## BLE connection test

The authenticated command is:

```text
PLAY_TEST_SOUND
```

Despite its protocol name, this is a general connection test:

- The onboard LED flashes a recognizable pattern.
- An optional piezo plays a short ascending chime.
- The sequence is non-blocking.
- It is rejected while microphone capture is active.
- A repeated request while the pattern is running returns `BUSY`.

Command payload:

```text
Bytes 0–3:   ASCII "HHC1"
Bytes 4–7:   Request ID, unsigned 32-bit little-endian
Bytes 8–22:  ASCII "PLAY_TEST_SOUND"
```

BLE characteristics:

```text
Service:
7d2e0001-6f9b-4af7-ae8c-5e4f48554831

Public identity — read:
7d2e0002-6f9b-4af7-ae8c-5e4f48554831

Authenticated status — read/notify:
7d2e0003-6f9b-4af7-ae8c-5e4f48554831

Authenticated command — write:
7d2e0004-6f9b-4af7-ae8c-5e4f48554831

Authenticated response — read/notify:
7d2e0005-6f9b-4af7-ae8c-5e4f48554831
```

Expected response:

```text
v1|<request-id>|OK
```

Other possible results are `BUSY`, `AUDIO_OUTPUT_UNAVAILABLE`, and
`INVALID_REQUEST`.

The Android UI can label this action `Test device` or `Find my Huh?`.

## BLE pairing

The public-safe firmware generates a six-digit PIN at boot and reports it over USB serial:

```text
generated at boot
```

It can be changed with `HUH_BLE_STATIC_PASSKEY`. Set
`HUH_BLE_USE_STATIC_PASSKEY=0` to restore a randomly generated PIN printed over
USB serial. A fixed shared PIN should be replaced with per-device setup
credentials before distributing hardware.

## Battery electrical requirements

Use a qualified, protected, single-cell rechargeable lithium-polymer battery:

- Nominal voltage: 3.7 V
- Fully charged voltage: 4.2 V
- Recommended continuous discharge capability: at least 500 mA
- Connect only to the XIAO battery pads after verifying polarity

The XIAO charges the battery through USB-C. The current schematic indicates an
approximately 110 mA charging current, so larger batteries charge slowly.

Approximate minimum charging times before losses and charge tapering:

```text
300 mAh battery  → at least 3 hours
500 mAh battery  → at least 5 hours
1000 mAh battery → at least 10 hours
```

## Suggested battery sizes

Battery model numbers often encode thickness × width × length in tenths of a
millimeter, but vendors are inconsistent. Always verify the supplier drawing.

### Compact prototype

```text
Nominal capacity: 250–350 mAh
Typical envelope: 5 × 20 × 30 mm
Common label:     502030
```

### Recommended first puck enclosure

```text
Nominal capacity: 500–700 mAh
Target envelope:  6 × 30 × 40 mm or smaller
Common examples:  603040, 603035
Reserved cavity:  42 × 32 × 8 mm
```

The reserved cavity includes modest clearance for cell variation, adhesive,
wiring, the protection tab, and swelling tolerance.

### Longer-runtime prototype

```text
Nominal capacity: 800–1000 mAh
Typical envelope: 6–8 × 30 × 40–50 mm
Common examples:  803040, 603450
```

## Recommended mechanical envelope

For the XIAO Sense stack and a 500–700 mAh battery, reserve approximately:

```text
Electronics stack:  23 × 20 × 17 mm
Battery cavity:     42 × 32 × 8 mm
Complete enclosure: approximately 48 × 38 × 22 mm minimum
```

The enclosure should provide:

- A microphone opening above the microphone port
- USB-C access
- microSD access, unless deliberately internal
- Clear space around the XIAO antenna end
- No battery, metal, or wiring directly over the antenna
- Battery-wire strain relief
- Clearance around the LiPo rather than tight compression
- An opening or light pipe for the user LED
- An acoustic opening if an optional piezo is installed

## Safety and design cautions

- Do not place the LiPo against sharp solder joints or microSD socket edges.
- Do not compress, bend, puncture, or tightly clamp the LiPo.
- Verify BAT polarity; connector polarity conventions vary.
- Keep the battery and metal hardware away from the ESP32 antenna region.
- Do not share a sealed acoustic cavity between the microphone and piezo.
- Firmware prevents the test signal during capture, but physical acoustic
  isolation is still useful.
- Treat GPIO21 carefully because it is the user LED and may be SD chip-select
  on some Sense revisions.

## References

- [Seeed XIAO ESP32S3 documentation](https://wiki.seeedstudio.com/xiao_esp32s3_getting_started/)
- [Official XIAO ESP32S3 Sense schematic](https://files.seeedstudio.com/wiki/SeeedStudio-XIAO-ESP32S3/new-res/new-XIAO%20ESP32S3%20Sense_v1.3_SCH_260210%281%29.pdf)
