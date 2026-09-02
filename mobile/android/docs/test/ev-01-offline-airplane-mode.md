# EV-01 — Offline and Airplane Mode

**Status:** Not run

## Goal

Prove the phone-only capture, local transcription, diarization, session history, and on-device
Gemma flow work with all radios disabled.

## Method

1. Enable Airplane Mode and confirm Wi-Fi and Bluetooth are off.
2. Launch Huh?, record a 20–30 second spoken sample, stop it, and wait for a transcript.
3. Review timestamps and speaker formatting; run on-device “Make sense of this.”
4. Enable Keep an Ear Out, capture a short spoken conversation, stop it, and verify the saved
   session completes locally.

## Success

All sessions persist, transcribe, and remain usable with no network prompt, network error, or
content leaving the device. LAN-only providers may be unavailable but must not block the on-device
fallback.

## Failure

Any core phone flow requires connectivity, stalls indefinitely, loses a session, or exposes
audio/transcript content outside the device.

## Iteration record

For each run, record build/version, device/OS, exact sample duration, result, relevant local log
excerpt, and one next hypothesis only if it failed.
