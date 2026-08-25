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
`START=2`, `AUDIO=3`, `HEARTBEAT=4`, `STOP=5`, and `ERROR=6`. Strings are UTF-8 prefixed
by an unsigned 16-bit byte length.

## Payloads

`HELLO`: device ID string, display name, manufacturer, model, firmware version, sample rate
`u32`, channels `u8`, sample width bits `u8`, and samples per frame `u16`.

`START`: a 16-byte UUID, most-significant 64 bits first.

`AUDIO`: stream UUID, monotonically increasing sequence `u64`, first-sample index `u64`,
then exactly 640 bytes of little-endian PCM. Duplicates are ignored. A known gap up to
3,200 samples (200 ms) is zero-filled and marked degraded; larger or unaligned gaps
interrupt the stream.

`HEARTBEAT`: stream UUID and last sequence `u64`. Heartbeats are independent from silence.

`STOP`: stream UUID and reason byte: user stop `1`, pause `2`, disconnect `3`, storage
failure `4`, device reboot `5`, capture failure `6`, or unknown `255`.

`ERROR`: error code `u16` and a length-prefixed UTF-8 message. It must not contain secrets
or raw audio.

## Transport and privacy

The envelope is transport-independent. The first adapter uses local TCP; BLE may handle
association, configuration, and commands. Audio and diagnostics stay between the selected
device and phone. No cloud endpoint is part of this protocol.
