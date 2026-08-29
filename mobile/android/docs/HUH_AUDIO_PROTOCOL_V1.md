# Huh? Audio Protocol v1

This protocol carries canonical speech audio from any compatible local capture device to
the Huh? Android app. It is intentionally independent of manufacturer and board model.

## Canonical audio

- Signed linear PCM at 16,000 Hz, mono, and 16 bits per sample
- Little-endian sample bytes
- 20 ms / 320 samples / 640 PCM bytes per `AUDIO` message

Unsupported formats are rejected during `HELLO`; v1 does not resample.

## Envelope

All envelope integers use network byte order (big-endian). PCM samples are little-endian.

| Offset | Size | Field |
| --- | ---: | --- |
| 0 | 4 | ASCII magic `HUH1` |
| 4 | 1 | Protocol version (`1`) |
| 5 | 1 | Message type |
| 6 | 2 | Flags, zero in v1 |
| 8 | 4 | Payload length |
| 12 | N | Payload |

Payloads are limited to 65,536 bytes. Receivers reject unknown versions, types, flags,
invalid lengths, truncation, and trailing payload bytes. Message values are `HELLO=1`,
`START=2`, `AUDIO=3`, `HEARTBEAT=4`, `STOP=5`, `ERROR=6`, `ACK=7`, `FETCH=8`,
`FILE_CHUNK=9`, and `FILE_END=10`. Strings are UTF-8 prefixed by an unsigned 16-bit
byte length.

## Payloads

`HELLO`: device ID string, display name, manufacturer, model, firmware version, sample rate
`u32`, channels `u8`, sample width bits `u8`, and samples per frame `u16`.

`START`: a 16-byte capture UUID, most-significant 64 bits first. Android sends this after
`HELLO`; the device echoes it only after capture has started successfully.

`AUDIO`: stream UUID, monotonically increasing sequence `u64`, first-sample index `u64`,
then exactly 640 bytes of little-endian PCM. Duplicates are ignored. A known gap up to
3,200 samples (200 ms) is zero-filled and marked degraded; larger or unaligned gaps
interrupt the stream.

`HEARTBEAT`: capture UUID and a monotonically increasing controller counter `u64`.
Android sends one at least every five seconds while capture is active. Missing the
15-second control lease causes the device to finalize the recording safely.

`STOP`: capture UUID and reason byte: user stop `1`, pause `2`, disconnect `3`, storage
failure `4`, device reboot `5`, capture failure `6`, control lease expired `7`, or
unknown `255`. Android uses it as a command; the device returns it after closing and
finalizing the capture file.

`ERROR`: error code `u16` and a length-prefixed UTF-8 message. It must not contain secrets
or raw audio.

`FETCH`: capture UUID and unsigned byte offset `u32`. Android may reconnect and resume a
partial download by requesting its locally persisted offset.

`FILE_CHUNK`: capture UUID, unsigned offset `u32`, then one or more raw little-endian PCM
bytes. Chunks need not align to a 20 ms audio frame, but their offsets must be contiguous.

`FILE_END`: capture UUID and total PCM byte count `u32`. Android verifies the total and
20 ms frame alignment before exposing the recording to the app's audio pipeline.

`ACK`: capture UUID. Android sends this only after the complete file has been verified and
consumed locally; the device may then delete its microSD copy.

The preferred v1 flow is `HELLO` → Android `START` → device `START` → Android lease
`HEARTBEAT`s → Android/device `STOP` → Android `FETCH` → `FILE_CHUNK`s → `FILE_END` →
Android `ACK`. The legacy live `AUDIO` payload remains decodable during migration, but the
SD-backed transfer prevents phone/network throughput from blocking microphone capture.

## Transport and privacy

The envelope is transport-independent. The first adapter uses local TCP; BLE may handle
association, configuration, and commands. Audio and diagnostics stay between the selected
device and phone. No cloud endpoint is part of this protocol.
