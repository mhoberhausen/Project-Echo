# EV-03 — Background and screen-off Active Listening

**Status:** Partially observed

## Goal

Verify user-initiated phone Active Listening remains visible and captures a meaningful conversation
while Huh? is backgrounded and the screen is off.

## Method

1. Enable Keep an Ear Out and confirm its actionable microphone notification is visible.
2. Press Home, lock the phone, then speak a qualifying sample after the configured speech-start
   threshold.
3. Unlock, reopen Huh?, stop Active Listening, and wait for local transcription.

## Success

The microphone notification remains visible while listening, a captured session is persisted and
transcribed, and stopping removes the microphone-listening notification. A separate local
transcription data-sync notification may remain until processing finishes.

## Failure

Silent service stop, absent visible notification while the microphone is in use, lost capture,
unexplained permission error, or failure to recover a persisted session.

## Iteration record

Record timing settings, lock duration, device battery state, notification screenshots, session ID,
and outcome. Do not tune VAD during this test unless the evidence identifies VAD as the cause.
