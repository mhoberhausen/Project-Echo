# EV-02 — Two-minute recording stability

**Status:** Not run

## Goal

Demonstrate a two-minute manual recording completes without crash, ANR, data loss, or unusable
transcript.

## Method

1. Ensure sufficient free device storage and start Listen Now.
2. Speak normally for two uninterrupted minutes; include brief pauses and normal room noise.
3. Stop recording, wait for local processing, then reopen the resulting session.

## Success

The app stays responsive, the recording stops normally, one session appears, transcription reaches
a terminal state, and its duration/transcript are plausible for the sample.

## Failure

Crash, ANR, missing session, stuck processing, corrupted audio, or a transcript that is clearly
truncated without an explained capture interruption.

## Iteration record

Record build/version, free storage before/after, elapsed duration, terminal session status, and any
local error. Change only one suspected cause before repeating.
